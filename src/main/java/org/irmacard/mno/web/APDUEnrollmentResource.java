package org.irmacard.mno.web;

import net.sf.scuba.smartcards.ProtocolCommands;
import net.sf.scuba.smartcards.ProtocolResponses;
import org.irmacard.credentials.Attributes;
import org.irmacard.credentials.CredentialsException;
import org.irmacard.credentials.idemix.descriptions.IdemixCredentialDescription;
import org.irmacard.credentials.idemix.info.IdemixKeyStore;
import org.irmacard.credentials.idemix.irma.IRMAIdemixIssuer;
import org.irmacard.credentials.idemix.messages.IssueCommitmentMessage;
import org.irmacard.credentials.idemix.messages.IssueSignatureMessage;
import org.irmacard.credentials.info.CredentialDescription;
import org.irmacard.credentials.info.InfoException;
import org.irmacard.idemix.IdemixSmartcard;
import org.irmacard.idemix.util.CardVersion;
import org.irmacard.mno.common.*;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

@Path("v1")
public class APDUEnrollmentResource extends EnrollmentResource {
	@GET
	@Path("/start")
	@Produces(MediaType.APPLICATION_JSON)
	public EnrollmentStartMessage start() {
		return super.start();
	}


	@POST
	@Path("/verify-passport")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public PassportVerificationResultMessage startPassportVerification(PassportDataMessage passportData)
	throws InfoException {
		return super.startPassportVerification(passportData);
	}

	@POST
	@Path("/issue/credential-list")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public HashMap<String, HashMap<String, String>> getCredentialList(BasicClientMessage startMessage)
			throws InfoException, IOException {
		EnrollmentSession session = getSession(startMessage);

		// Verify state of session
		// TODO Handle state
		if (session.getState() != EnrollmentSession.State.PASSPORT_VERIFIED) {
			// throw new WebApplicationException(Response.Status.UNAUTHORIZED);
		}

		return session.getCredentialList();
	}

	@POST
	@Path("/issue/{cred}/start")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public ProtocolCommands startCredentialIssuing(
			RequestStartIssuanceMessage startMessage, @PathParam("cred") String cred)
			throws InfoException, CredentialsException {
		EnrollmentSession session = getSession(startMessage);

		// Verify state of session
		if (session.getState() != EnrollmentSession.State.PASSPORT_VERIFIED) {
			throw new WebApplicationException(Response.Status.UNAUTHORIZED);
		}

		// Check if we can issue this credential
		if (!session.getCredentialList().containsKey(cred)) {
			throw new WebApplicationException(Response.Status.UNAUTHORIZED);
		}
		Map<String, String> attributes = session.getCredentialList().get(cred);

		// Setup raw attributes
		Attributes rawAttributes = new Attributes();
		CredentialDescription cd = getCredentialDescription(cred);
		rawAttributes.setCredentialID(cd.getId());
		for (Map.Entry<String, String> entry : attributes.entrySet()) {
			rawAttributes.add(entry.getKey(), entry.getValue().getBytes());
		}
		session.setRawAttributes(cred, rawAttributes);

		IdemixCredentialDescription icd = new IdemixCredentialDescription(cd);
		CardVersion cv = new CardVersion(startMessage.getCardVersion());
		session.setCardVersion(cv);

		BigInteger nonce1 = icd.generateNonce();
		session.setNonce(cred, nonce1);

		ProtocolCommands commands = IdemixSmartcard.requestIssueCommitmentCommands(cv, icd, rawAttributes, nonce1);

		return commands;
	}

	@POST
	@Path("/issue/{cred}/finish")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public ProtocolCommands finishCredentialIssuing(RequestFinishIssuanceMessage finishMessage,
	                                                @PathParam("cred") String cred) throws InfoException, CredentialsException {
		EnrollmentSession session = getSession(finishMessage);
		ProtocolResponses responses = finishMessage.getResponses();

		// Verify state of session
		if (session.getState() != EnrollmentSession.State.PASSPORT_VERIFIED) {
			throw new WebApplicationException(Response.Status.UNAUTHORIZED);
		}

		// Check if we can issue this credential
		if (!session.getCredentialList().containsKey(cred)) {
			throw new WebApplicationException(Response.Status.UNAUTHORIZED);
		}

		// Retrieve issue-state
		CredentialDescription cd = getCredentialDescription(cred);
		IdemixCredentialDescription icd = new IdemixCredentialDescription(cd);
		Attributes rawAttributes = session.getRawAttributes(cred);
		BigInteger nonce1 = session.getNonce(cred);
		CardVersion cv = session.getCardVersion();

		// Initialize the issuer
		IRMAIdemixIssuer issuer = new IRMAIdemixIssuer(icd.getPublicKey(),
				IdemixKeyStore.getInstance().getSecretKey(cd), icd.getContext());

		// TODO: check throws of issuer, can we relay this?
		IssueCommitmentMessage commit_msg = IdemixSmartcard.processIssueCommitmentCommands(cv, responses);
		IssueSignatureMessage signature_msg = issuer.issueSignature(commit_msg, icd, rawAttributes, nonce1);

		ProtocolCommands commands = IdemixSmartcard.requestIssueSignatureCommands(cv, icd, signature_msg);
		return commands;
	}
}
