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

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.irmacard.credentials.Attributes;
import org.irmacard.credentials.info.CredentialIdentifier;
import org.irmacard.idemix.util.CardVersion;
import org.irmacard.mno.common.DocumentDataMessage;
import org.irmacard.mno.common.EDLDataMessage;
import org.irmacard.mno.common.EnrollmentStartMessage;
import org.irmacard.mno.common.PassportDataMessage;

public class EnrollmentSession {
    public enum State {
        STARTED, PASSPORT_VERIFIED, ISSUING
    }

    private String sessionToken;
    private byte[] nonce;
    private State state;
    private DocumentDataMessage documentData;
    private HashMap<CredentialIdentifier, HashMap<String, String>> credentialList;
    private Map<CredentialIdentifier, Attributes> attributesList;
    private Map<CredentialIdentifier, BigInteger> nonceList;
    private CardVersion cardVersion;

    public EnrollmentSession(String sessionToken, byte[] nonce) {
        this.sessionToken = sessionToken;
        this.nonce = nonce;
        this.state = State.STARTED;

        attributesList = new HashMap<CredentialIdentifier, Attributes>();
        nonceList = new HashMap<CredentialIdentifier, BigInteger>();
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public EnrollmentStartMessage getStartMessage() {
        return new EnrollmentStartMessage(sessionToken, nonce);
    }

    public void setDocumentData(DocumentDataMessage documentData) {
        this.documentData = documentData;
    }

    public PassportDataMessage getPassportDataMessage() {
        try {
            return (PassportDataMessage) documentData;
        } catch (ClassCastException e) {
            return null;
        }
    }

    public EDLDataMessage getEDLDataMessage() {
        try {
            return (EDLDataMessage) documentData;
        } catch (ClassCastException e) {
            return null;
        }
    }

    public void setCredentialList(HashMap<CredentialIdentifier, HashMap<String, String>> credentialList) {
        this.credentialList = credentialList;
    }

    public HashMap<CredentialIdentifier, HashMap<String, String>> getCredentialList() {
        return credentialList;
    }

    public void setRawAttributes(CredentialIdentifier cred, Attributes rawAttributes) {
        attributesList.put(cred, rawAttributes);
    }

    public Attributes getRawAttributes(CredentialIdentifier cred) {
        return attributesList.get(cred);
    }

    public void setNonce(CredentialIdentifier cred, BigInteger nonce1) {
        nonceList.put(cred, nonce1);
    }

    public BigInteger getNonce(CredentialIdentifier cred) {
        return nonceList.get(cred);
    }

    public void setCardVersion(CardVersion cardVersion) {
        this.cardVersion = cardVersion;
    }

    public CardVersion getCardVersion() {
        return cardVersion;
    }
}
