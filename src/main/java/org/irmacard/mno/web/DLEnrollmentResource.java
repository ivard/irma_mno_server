package org.irmacard.mno.web;

import org.irmacard.credentials.info.CredentialIdentifier;
import org.irmacard.credentials.info.InfoException;
import org.irmacard.mno.common.EDLDataMessage;
import org.irmacard.mno.common.EnrollmentStartMessage;
import org.irmacard.mno.common.PassportVerificationResultMessage;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;

public class DLEnrollmentResource extends GenericEnrollmentResource<EDLDataMessage> {
	@GET
	@Path("/start")
	@Produces(MediaType.APPLICATION_JSON)
	@Override
	public EnrollmentStartMessage start() {
		return super.start();
	}


	@POST
	@Path("/verify-dl")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Override
	public PassportVerificationResultMessage verifyDocument(EDLDataMessage documentData)
			throws InfoException {
		return super.verifyDocument(documentData);
	}

	@Override
	protected HashMap<CredentialIdentifier, HashMap<String, String>> getCredentialList(EnrollmentSession session)
	throws InfoException { // TODO
		return null;
	}
}
