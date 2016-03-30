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
import org.irmacard.credentials.info.InfoException;
import org.irmacard.mno.common.*;

import javax.ws.rs.*;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.security.KeyManagementException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

@Path("v2")
public class JsonEnrollmentResource extends PassportEnrollmentResource {
	@GET
	@Path("/start")
	@Produces(MediaType.APPLICATION_JSON)
	@Override
	public EnrollmentStartMessage start() {
		return super.start();
	}


	@POST
	@Path("/verify-passport")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Override
	public PassportVerificationResultMessage verifyDocument(PassportDataMessage documentData)
	throws InfoException {
		// Let super verify the passport message, and compute the resulting credentials
		PassportVerificationResultMessage msg = super.verifyDocument(documentData);

		// Check if super succeeded in verifying the passport
		if (msg.getResult() != PassportVerificationResult.SUCCESS) {
			return msg;
		}

		// Passport was succesfull; create issuing session with the API server
		HashMap<CredentialIdentifier, HashMap<String, String>> creds = getSession(documentData).getCredentialList();
		msg.setIssueQr(ApiClient.createIssuingSession(creds));

		return msg;
	}
}
