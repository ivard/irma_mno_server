package org.irmacard.mno.web;

import com.google.gson.JsonParseException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.glassfish.jersey.internal.util.Base64;
import org.irmacard.api.common.*;
import org.irmacard.api.common.issuing.*;
import org.irmacard.api.common.disclosure.*;
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
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

/**
 * Issuing client for the irma_api_server. Usage: call {@link #createIssuingSession(HashMap, String, SignatureAlgorithm, PrivateKey)}.
 */
public class ApiClient {
	/**
	 * Informs the API server of the credentials that we want issued, and returns the resulting session
	 * @param credentialList The credentials and their attributes to issue
	 * @param sigAlg The signature algorithm for signing the JWT
	 * @param privKey The private key for signing the JWT
	 * @return Serialized version number and URL to the APi server
	 */
	public static ClientQr createIssuingSession(HashMap<CredentialIdentifier, HashMap<String, String>> credentialList, String iss, SignatureAlgorithm sigAlg, PrivateKey privKey) {
		String server = MNOConfiguration.getInstance().getApiServerIssueUrl();
		String jwt = getIssuingJWT(credentialList, iss, sigAlg, privKey);
		System.out.println("created JWT: " + jwt);
		return createSession(jwt,server);

	}

	public static ClientQr createDisclosureSession(AttributeDisjunctionList list, String iss, String keyId, SignatureAlgorithm sigAlg, PrivateKey privKey){
		String server = MNOConfiguration.getInstance().getApiServerDisclosureUrl();
		String jwt = getDisclosureJWT(list, keyId, iss, sigAlg, privKey);
		System.out.println("created JWT: " + jwt);

		return createSession(jwt,server);
	}

	private static ClientQr createSession(String jwt, String server){
		// Post our JWT
		System.out.println("requesting discl QR to " + server);
		String qrString = ClientBuilder.newClient().target(server)
				.request(MediaType.APPLICATION_JSON_TYPE)
				.post(Entity.entity(jwt, MediaType.TEXT_PLAIN), String.class);
		System.out.println("Received qrString:" + qrString);

		// Try to parse the output of the server as a QR
		try {
			ClientQr qr = GsonUtil.getGson().fromJson(qrString, ClientQr.class);
			if (qr.getUrl() == null || qr.getUrl().length() == 0
					|| qr.getVersion() == null || qr.getVersion().length() == 0) {
				System.out.println("QR niet oke");
				throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
			}
			qr.setUrl(server + qr.getUrl()); // Let the token know where to find the server
			return qr;
		} catch (JsonParseException e) {
			e.printStackTrace();
			try {
				// If it is not a QR then it could be an error message from the API server.
				// Try to deserialize it as such; if it is, then we rethrow it to the token
				ApiErrorMessage apiError = GsonUtil.getGson().fromJson(qrString, ApiErrorMessage.class);
				System.out.println("ApiError:" + apiError.getMessage());
				throw new ApiException(apiError.getError(), "Error from issuing server");
			} catch (Exception parseEx) {
				// Not an ApiErrorMessage
				throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
			}
		}
	}

		//Copied from IRMA api common to quickly get disclosure working
	//TODO: should replace this whole ApiClient with api_common version
	public static String getDisclosureJWT(AttributeDisjunctionList list, String iss, SignatureAlgorithm sigAlg, PrivateKey privKey) {
		return getDisclosureJWT(list, null, iss, sigAlg, privKey);
	}

	public static String getDisclosureJWT(AttributeDisjunctionList list, String keyID, String iss, SignatureAlgorithm sigAlg, PrivateKey privKey) {
		DisclosureProofRequest request = new DisclosureProofRequest(null, null, list);
		ServiceProviderRequest spRequest = new ServiceProviderRequest("", request, 120);
		return buildJwt(keyID, sigAlg, privKey, getJwtClaims(spRequest, "sprequest", "verification_request", iss));
	}

	/**
	 * Serialize the credentials to be issued to the body (claims) of a JWT token
	 */
	private static String getJwtClaims(ClientRequest request,
									   String type,
									   String subject,
									   String iss) {
		HashMap<String, Object> claims = new HashMap<>(4);
		claims.put(type, request);
		claims.put("iat", System.currentTimeMillis() / 1000);
		claims.put("iss", iss);
		claims.put("sub", subject);

		return GsonUtil.getGson().toJson(claims);
	}

	private static String buildJwt(String keyID, SignatureAlgorithm sigAlg, PrivateKey privKey, String request) {
		JwtBuilder builder = Jwts.builder();
		if (keyID != null)
			builder.setHeaderParam("kid", keyID);

		return builder
				.setPayload(request)
				.signWith(sigAlg, privKey)
				.compact();
	}

	private static String getIssuingJWT(HashMap<CredentialIdentifier, HashMap<String, String>> credentialList, String iss, SignatureAlgorithm sigAlg, PrivateKey privKey) {
		return MNOConfiguration.getInstance().shouldSignJwt() ?
				getSignedIssuingJWT(credentialList,iss,sigAlg, privKey) :
				getUnsignedIssuingJWT(credentialList,iss);
	}

	private static String getSignedIssuingJWT(HashMap<CredentialIdentifier, HashMap<String, String>> credentialList, String iss, SignatureAlgorithm sigAlg, PrivateKey privKey) {
		return Jwts.builder()
				.setPayload(getJwtClaims(credentialList,iss))
				.signWith(sigAlg,//MNOConfiguration.getInstance().getJwtAlgorithm(),
						privKey)//MNOConfiguration.getInstance().getJwtPrivateKey())
				.compact();
	}

	private static String getUnsignedIssuingJWT(HashMap<CredentialIdentifier, HashMap<String, String>> credentialList, String iss) {
		String header = encodeBase64("{\"typ\":\"JWT\",\"alg\":\"none\"}");
		String claims = encodeBase64(getJwtClaims(credentialList, iss));
		return header + "." + claims + ".";
	}

	/**
	 * Serialize the credentials to be issued to the body (claims) of a JWT token
	 */
	private static String getJwtClaims(HashMap<CredentialIdentifier, HashMap<String, String>> credentialList, String iss) {
		HashMap<String, Object> claims = new HashMap<>(4);
		claims.put("iprequest", getIdentityProviderRequest(credentialList));
		claims.put("iat", System.currentTimeMillis()/1000);
		claims.put("iss", iss);
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
			credentials.add(new CredentialRequest((int) validity, identifier, credentialList.get(identifier)));

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
