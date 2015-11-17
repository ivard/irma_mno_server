/*
 * Copyright (c) 2015, Wouter Lueks
 * Copyright (c) 2015, Sietse Ringers
 * Copyright (c) 2015, Fabian van den Broek
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the IRMA project nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.irmacard.mno.web;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
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
import org.jmrtd.lds.MRZInfo;

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
    public ProtocolCommands startCredentialIssuing(
            RequestStartIssuanceMessage startMessage, @PathParam("cred") String cred)
                    throws InfoException, CredentialsException {
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
        MRZInfo mrz = session.getPassportDataMessage().getDg1File().getMRZInfo();

        SimpleDateFormat bacDateFormat = new SimpleDateFormat("yyMMdd");
        SimpleDateFormat hrDateFormat = new SimpleDateFormat("MMM d, y"); // Matches Android's default date format
        Date dob;
        Date expiry;

        try {
            dob = bacDateFormat.parse(mrz.getDateOfBirth());
            expiry = bacDateFormat.parse(mrz.getDateOfExpiry());
        }  catch (ParseException e) {
            e.printStackTrace();
            throw new InfoException("Failed to parse MRZ", e);
        }

        int[] lowAges = {12,16,18,21};
        credentials.put("ageLower", ageAttributes(lowAges, dob));

        int[] highAges = {50, 60, 65, 75};
        credentials.put("ageHigher", ageAttributes(highAges, dob));

        HashMap<String,String> nameAttributes = new HashMap<>();
        String[] nameParts = splitFamilyName(mrz.getPrimaryIdentifier());
        String firstnames = toTitleCase(joinStrings(mrz.getSecondaryIdentifierComponents()));
        // The first of the first names is not always the person's usual name ("roepnaam"). In fact, the person's
        // usual name need not even be in his list of first names at all. But given only the MRZ, there is no way of
        // knowing what is his/her usual name... So we can only guess.
        String firstname = toTitleCase(mrz.getSecondaryIdentifierComponents()[0]);

        nameAttributes.put("familyname", toTitleCase(nameParts[1]));
        nameAttributes.put("prefix", nameParts[0]);
        nameAttributes.put("firstnames", firstnames);
        nameAttributes.put("firstname", firstname);

        credentials.put("fullName", nameAttributes);

        HashMap<String, String> idDocumentAttributes = new HashMap<String, String>();
        idDocumentAttributes.put("number", mrz.getDocumentNumber());
        idDocumentAttributes.put("expires", hrDateFormat.format(expiry));
        idDocumentAttributes.put("nationality", mrz.getNationality());
        if (mrz.getDocumentType() == MRZInfo.DOC_TYPE_ID1)
            idDocumentAttributes.put("type", "ID card");
        else if (mrz.getDocumentType() == MRZInfo.DOC_TYPE_ID3)
            idDocumentAttributes.put("type", "Passport");
        else
            idDocumentAttributes.put("type", "unknown");
        credentials.put("idDocument", idDocumentAttributes);

        return credentials;
    }

    /**
     * Try to split the family name in into a prefix and a proper part, using a list of commonly occuring (Dutch)
     * prefixes.
     * @param name The name to split
     * @return An array in which the first element is the prefix, or " " if none found, and the second is the
     * remainder of the name.
     */
    public String[] splitFamilyName(String name) {
        name = name.toLowerCase();
        String[] parts = {" ", name};

        // Taken from https://nl.wikipedia.org/wiki/Tussenvoegsel
        String[] prefixes = {"af", "aan", "bij", "de", "den", "der", "d'", "het", "'t", "in", "onder", "op", "over", "'s", "'t", "te", "ten", "ter", "tot", "uit", "uijt", "van", "vanden", "ver", "voor", "aan de", "aan den", "aan der", "aan het", "aan 't", "bij de", "bij den", "bij het", "bij 't", "boven d'", "de die", "de die le", "de l'", "de la", "de las", "de le", "de van der,", "in de", "in den", "in der", "in het", "in 't", "onder de", "onder den", "onder het", "onder 't", "over de", "over den", "over het", "over 't", "op de", "op den", "op der", "op gen", "op het", "op 't", "op ten", "van de", "van de l'", "van den", "van der", "van gen", "van het", "van la", "van 't", "van ter", "van van de", "uit de", "uit den", "uit het", "uit 't", "uit te de", "uit ten", "uijt de", "uijt den", "uijt het", "uijt 't", "uijt te de", "uijt ten", "voor de", "voor den", "voor in 't"};

        // I'm too lazy to manually sort the list above on string size.
        Arrays.sort(prefixes, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                if (o1.length() < o2.length()) return 1;
                if (o1.length() > o2.length()) return -1;
                return o1.compareTo(o2);
            }
        });

        for (String prefix : prefixes) {
            if (name.startsWith(prefix + " ")) {
                parts[0] = prefix;
                parts[1] = name.substring(prefix.length() + 1); // + 1 to skip the space between the prefix and the name
                return parts;
            }
        }

        return parts;
    }

    public static String toTitleCase(String s) {
        String ACTIONABLE_DELIMITERS = " '-/"; // these cause the character following to be capitalized

        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : s.toCharArray()) {
            c = capitalizeNext ? Character.toUpperCase(c) : Character.toLowerCase(c);
            sb.append(c);
            capitalizeNext = (ACTIONABLE_DELIMITERS.indexOf(c) >= 0);
        }

        return sb.toString();
    }

    public static String joinStrings(String[] parts) {
        if (parts.length == 0)
            return "";

        String glue = " ";

        String s = parts[0];

        for (int i=1; i<parts.length; i++) {
            s += glue + parts[i];
        }

        return s;
    }

    public HashMap<String, String> ageAttributes(int[] ages, Date dob) {
        HashMap<String, String> attrs = new HashMap<>();

        for (int age : ages) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.YEAR, -1 * age);
            Date ageDate = c.getTime();

            String attrValue;
            attrValue = dob.before(ageDate) ? "yes" : "no";
            attrs.put("over" + age, attrValue);
        }

        return attrs;
    }

    private PassportVerificationResult verifyPassportData(PassportDataMessage msg, byte[] nonce) {
        // TODO: query MNO DB
        return msg.verify(nonce);
    }

    private CredentialDescription getCredentialDescription(String cred) throws InfoException {
        return DescriptionStore.getInstance().getCredentialDescriptionByName(ISSUER, cred);
    }
}
