/*
 * EnrollmentResource.java
 *
 * Copyright (c) 2015, Wouter Lueks, Radboud University
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the IRMA project nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.irmacard.mno.web;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.codec.binary.Base64;
import org.irmacard.credentials.Attributes;
import org.irmacard.credentials.CredentialsException;
import org.irmacard.credentials.idemix.descriptions.IdemixCredentialDescription;
import org.irmacard.credentials.idemix.info.IdemixKeyStore;
import org.irmacard.credentials.idemix.irma.IRMAIdemixIssuer;
import org.irmacard.credentials.idemix.messages.IssueCommitmentMessage;
import org.irmacard.credentials.idemix.messages.IssueSignatureMessage;
import org.irmacard.credentials.info.CredentialDescription;
import org.irmacard.credentials.info.DescriptionStore;
import org.irmacard.credentials.info.InfoException;
import org.irmacard.idemix.IdemixSmartcard;
import org.irmacard.idemix.util.CardVersion;
import org.irmacard.mno.common.BasicClientMessage;
import org.irmacard.mno.common.EnrollmentStartMessage;
import org.irmacard.mno.common.PassportDataMessage;
import org.irmacard.mno.common.PassportVerificationResult;
import org.irmacard.mno.common.PassportVerificationResultMessage;
import org.irmacard.mno.common.RequestFinishIssuanceMessage;
import org.irmacard.mno.common.RequestStartIssuanceMessage;
import org.irmacard.mno.web.exceptions.InputInvalidException;
import org.irmacard.mno.web.exceptions.SessionUnknownException;

import net.sf.scuba.smartcards.ProtocolCommands;
import net.sf.scuba.smartcards.ProtocolResponses;

@Path("v1")
public class EnrollmentResource {
    private SecureRandom rnd;

    @Inject
    private EnrollmentSessions sessions;

    private static final int SESSION_TOKEN_LENGTH = 33;
    private static final int AA_NONCE_LENGTH = 8;

    private static final String ISSUER = "MijnOverheid";

    @Inject
    public EnrollmentResource() {
        rnd = new SecureRandom();
    }

    @GET
    @Path("/start")
    @Produces(MediaType.APPLICATION_JSON)
    public EnrollmentStartMessage start() {
        String sessionToken = generateSessionToken();
        byte[] nonce = generateAANonce();
        EnrollmentSession session = new EnrollmentSession(sessionToken, nonce);

        sessions.addSession(session);

        return session.getStartMessage();
    }

    @POST
    @Path("/verify-passport")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public PassportVerificationResultMessage startPassportVerification(PassportDataMessage passportData) {
        EnrollmentSession session = getSession(passportData);

        // Verify state of session
        if (session.getState() != EnrollmentSession.State.STARTED) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }

        session.setPassportData(passportData);

        // Check the passport data
        PassportVerificationResult result = verifyPassportData(passportData, session.getStartMessage().getNonce());

        if (result == PassportVerificationResult.SUCCESS) {
            session.setState(EnrollmentSession.State.PASSPORT_VERIFIED);
        } else {
            // Verification failed, remove session
            sessions.remove(session);
        }

        return new PassportVerificationResultMessage(result);
    }

    @POST
    @Path("/issue/credential-list")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Map<String, String>> getCredentialList(BasicClientMessage startMessage)
            throws InfoException, IOException {
        EnrollmentSession session = getSession(startMessage);

        // Verify state of session
        // TODO Handle state
        if (session.getState() != EnrollmentSession.State.PASSPORT_VERIFIED) {
            // throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }

        Map<String, Map<String, String>> credentialList = getCredentialList(session);

        session.setCredentialList(credentialList);

        return credentialList;
    }

    @POST
    @Path("/issue/{cred}/start")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ProtocolCommands startCredentialIssuing(RequestStartIssuanceMessage startMessage,
            @PathParam("cred") String cred) throws InfoException {
        EnrollmentSession session = getSession(startMessage);

        // Verify state of session
        if (session.getState() != EnrollmentSession.State.PASSPORT_VERIFIED) {
            // throw new WebApplicationException(Response.Status.UNAUTHORIZED);
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
        for (Entry<String, String> entry : attributes.entrySet()) {
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
            // throw new WebApplicationException(Response.Status.UNAUTHORIZED);
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

    /**
     * Retrieve the stored session. This method throws runtime errors when the
     * input is malformed, or the session cannot be found.
     *
     * @param message
     *            the BasicClientMessage containing the sessionToken
     * @return the EnrollmentSession
     */
    private EnrollmentSession getSession(BasicClientMessage message) {
        if (message == null) {
            throw new InputInvalidException("Supply a valid JSON object");
        }

        String sessionToken = message.getSessionToken();
        if (sessionToken == null) {
            throw new InputInvalidException("Specify the sessionToken field");
        }

        final EnrollmentSession session = sessions.getSession(sessionToken);
        if (session == null) {
            throw new SessionUnknownException();
        }

        return session;
    }

    /**
     * A random session token. Returns a base64 encoded string representing the
     * session token. The characters '+' and '/' are removed from this
     * representation.
     *
     * @return the random session token
     */
    private String generateSessionToken() {
        byte[] token = new byte[SESSION_TOKEN_LENGTH];
        rnd.nextBytes(token);
        String strtoken = Base64.encodeBase64String(token);
        return strtoken.replace("+", "").replace("/", "");
    }

    /**
     * A random nonce for the active authentication of the passport
     */
    private byte[] generateAANonce() {
        byte[] nonce = new byte[AA_NONCE_LENGTH];
        rnd.nextBytes(nonce);
        return nonce;
    }

    private Map<String, Map<String, String>> getCredentialList(EnrollmentSession session) throws InfoException {
        HashMap<String, Map<String, String>> credentials = new HashMap<String, Map<String, String>>();

        HashMap<String, String> rootAttributes = new HashMap<String, String>();

        rootAttributes.put("BSN", "123456789");
        credentials.put("root", rootAttributes);

        HashMap<String, String> ageLowerAttributes = new HashMap<String, String>();

        ageLowerAttributes.put("over12", "yes");
        ageLowerAttributes.put("over16", "yes");
        ageLowerAttributes.put("over18", "yes");
        ageLowerAttributes.put("over21", "yes");
        credentials.put("ageLower", ageLowerAttributes);

        return credentials;
    }

    private PassportVerificationResult verifyPassportData(PassportDataMessage msg, byte[] nonce) {
        // TODO: query MNO DB
        if (msg.verify(nonce)) {
            return PassportVerificationResult.SUCCESS;
        } else {
            return PassportVerificationResult.PASSPORT_INVALID;
        }
    }

    private CredentialDescription getCredentialDescription(String cred) throws InfoException {
        return DescriptionStore.getInstance().getCredentialDescriptionByName(ISSUER, cred);
    }
}
