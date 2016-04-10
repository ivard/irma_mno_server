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

import java.security.SecureRandom;
import java.util.*;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.commons.codec.binary.Base64;
import org.irmacard.credentials.info.CredentialDescription;
import org.irmacard.credentials.info.CredentialIdentifier;
import org.irmacard.credentials.info.DescriptionStore;
import org.irmacard.credentials.info.InfoException;
import org.irmacard.mno.common.*;
import org.irmacard.mno.web.exceptions.InputInvalidException;
import org.irmacard.mno.web.exceptions.SessionUnknownException;

abstract public class GenericEnrollmentResource<DocData extends DocumentDataMessage> {
    private SecureRandom rnd;

    @Inject
    protected EnrollmentSessions sessions;

    private static final int SESSION_TOKEN_LENGTH = 33;
    private static final int AA_NONCE_LENGTH = 8;

    public static final String SCHEME_MANAGER = "irma-demo";
    public static final String ISSUER = "MijnOverheid";

    @Inject
    public GenericEnrollmentResource() {
        rnd = new SecureRandom();
    }

    public EnrollmentStartMessage start() {
        String sessionToken = generateSessionToken();
        byte[] nonce = generateAANonce();
        EnrollmentSession session = new EnrollmentSession(sessionToken, nonce);

        sessions.addSession(session);

        return session.getStartMessage();
    }

    /**
     * Verify the document data, compute the resulting attributes, and store them in the session
     */
    public PassportVerificationResultMessage verifyDocument(DocData documentData)
    throws InfoException {
        EnrollmentSession session = getSession(documentData);

        // Verify state of session
        if (session.getState() != EnrollmentSession.State.STARTED) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }

        session.setDocumentData(documentData);

        // Check the passport data
        PassportVerificationResult result = verifyDocumentData(documentData, session.getStartMessage().getNonce());
        PassportVerificationResultMessage msg = new PassportVerificationResultMessage(result);

        if (result != PassportVerificationResult.SUCCESS) {
            // Verification failed, remove session
            sessions.remove(session);
            return msg;
        }

        session.setState(EnrollmentSession.State.PASSPORT_VERIFIED);
        HashMap<CredentialIdentifier, HashMap<String, String>> credentialList = getCredentialList(session);
        session.setCredentialList(credentialList);
        msg.setIssueQr(ApiClient.createIssuingSession(credentialList));

        return msg;
    }

    /**
     * Retrieve the stored session. This method throws runtime errors when the
     * input is malformed, or the session cannot be found.
     *
     * @param message
     *            the BasicClientMessage containing the sessionToken
     * @return the EnrollmentSession
     */
    protected EnrollmentSession getSession(BasicClientMessage message) {
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

    abstract protected HashMap<CredentialIdentifier, HashMap<String, String>> getCredentialList(EnrollmentSession session)
    throws InfoException;

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

    private PassportVerificationResult verifyDocumentData(DocumentDataMessage msg, byte[] nonce) {
        // TODO: query MNO DB
        return msg.verify(nonce);
    }
}
