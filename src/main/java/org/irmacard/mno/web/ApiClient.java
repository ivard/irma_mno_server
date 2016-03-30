package org.irmacard.mno.web;

import com.google.gson.JsonParseException;
import io.jsonwebtoken.Jwts;
import org.glassfish.jersey.internal.util.Base64;
import org.irmacard.api.common.ClientQr;
import org.irmacard.api.common.CredentialRequest;
import org.irmacard.api.common.IdentityProviderRequest;
import org.irmacard.api.common.IssuingRequest;
import org.irmacard.api.common.exceptions.ApiErrorMessage;
import org.irmacard.api.common.exceptions.ApiException;
import org.irmacard.api.common.util.GsonUtil;
import org.irmacard.credentials.Attributes;
import org.irmacard.credentials.info.CredentialIdentifier;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.security.KeyManagementException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

/**
 * Issuing client for the irma_api_server. Usage: call {@link #createIssuingSession(HashMap)}.
 */
public class ApiClient {
	/**
	 * Informs the API server of the credentials that we want issued, and returns the resulting session
	 * @param credentialList The credentials and their attributes to issue
	 * @return Serialized version number and URL to the APi server
	 */
	public static ClientQr createIssuingSession(HashMap<CredentialIdentifier, HashMap<String, String>> credentialList) {
		String server = MNOConfiguration.getInstance().getApiServerUrl();
		String jwt = getIssuingJWT(credentialList);

		// Post our JWT
		String qrString = ClientBuilder.newClient().target(server)
				.request(MediaType.APPLICATION_JSON_TYPE)
				.post(Entity.entity(jwt, MediaType.TEXT_PLAIN), String.class);

		// Try to parse the output of the server as a QR
		try {
			ClientQr qr = GsonUtil.getGson().fromJson(qrString, ClientQr.class);
			if (qr.getUrl() == null || qr.getUrl().length() == 0
					|| qr.getVersion() == null || qr.getVersion().length() == 0)
				throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);

			qr.setUrl(server + qr.getUrl()); // Let the token know where to find the server
			return qr;
		} catch (JsonParseException e) {
			try {
				// If it is not a QR then it could be an error message from the API server.
				// Try to deserialize it as such; if it is, then we rethrow it to the token
				ApiErrorMessage apiError = GsonUtil.getGson().fromJson(qrString, ApiErrorMessage.class);
				throw new ApiException(apiError.getError(), "Error from issuing server");
			} catch (Exception parseEx) {
				// Not an ApiErrorMessage
				throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
			}
		}
	}

	private static String getIssuingJWT(HashMap<CredentialIdentifier, HashMap<String, String>> credentialList) {
		return MNOConfiguration.getInstance().shouldSignJwt() ?
				getSignedIssuingJWT(credentialList) :
				getUnsignedIssuingJWT(credentialList);
	}

	private static String getSignedIssuingJWT(HashMap<CredentialIdentifier, HashMap<String, String>> credentialList) {
		try {
			return Jwts.builder()
					.setPayload(getJwtClaims(credentialList))
					.signWith(MNOConfiguration.getInstance().getJwtAlgorithm(),
							MNOConfiguration.getInstance().getJwtPrivateKey())
					.compact();
		} catch (KeyManagementException e) {
			throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
		}
	}

	private static String getUnsignedIssuingJWT(HashMap<CredentialIdentifier, HashMap<String, String>> credentialList) {
		String header = encodeBase64("{\"typ\":\"JWT\",\"alg\":\"none\"}");
		String claims = encodeBase64(getJwtClaims(credentialList));
		return header + "." + claims + ".";
	}

	/**
	 * Serialize the credentials to be issued to the body (claims) of a JWT token
	 */
	private static String getJwtClaims(HashMap<CredentialIdentifier, HashMap<String, String>> credentialList) {
		HashMap<String, Object> claims = new HashMap<>(4);
		claims.put("iprequest", getIdentityProviderRequest(credentialList));
		claims.put("iat", System.currentTimeMillis()/1000);
		claims.put("iss", MNOConfiguration.getInstance().getApiName());
		claims.put("sub", "issue_request");

		return GsonUtil.getGson().toJson(claims);
	}

	/**
	 * Convert the credentials to be issued to an {@link IdentityProviderRequest} for the API server
	 */
	private static IdentityProviderRequest getIdentityProviderRequest(HashMap<CredentialIdentifier, HashMap<String, String>> credentialList) {
		// Calculate expiry date: 6 months from now
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.MONTH, 6);
		long validity = (calendar.getTimeInMillis() / Attributes.EXPIRY_FACTOR) * Attributes.EXPIRY_FACTOR / 1000;

		// Compute credential list for in the issuing request
		ArrayList<CredentialRequest> credentials = new ArrayList<>(credentialList.size());
		for (CredentialIdentifier identifier : credentialList.keySet())
			credentials.add(new CredentialRequest(
					(int) validity, identifier.toString(), credentialList.get(identifier)));

		// Create issuing request, encode as unsigned JWT
		IssuingRequest request = new IssuingRequest(null, null, credentials);
		return new IdentityProviderRequest("", request, 120);
	}

	private static String encodeBase64(String data) {
		return Base64.encodeAsString(data)
				.replace('+', '-')
				.replace('/', '_')
				.replace("=", "")
				.replace("\n", "");
	}
}
