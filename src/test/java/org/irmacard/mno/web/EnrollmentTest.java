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

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.glassfish.jersey.test.jetty.JettyTestContainerFactory;
import org.irmacard.credentials.info.CredentialIdentifier;
import org.irmacard.mno.common.EDLDataMessage;
import org.irmacard.mno.common.EnrollmentStartMessage;
import org.irmacard.mno.common.PassportVerificationResult;
import org.irmacard.mno.common.PassportVerificationResultMessage;
import org.irmacard.mno.common.util.GsonUtil;
import org.junit.Test;

import javax.inject.Inject;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;

public class EnrollmentTest extends JerseyTest {
    @Inject
    protected EnrollmentSessions sessions;

    public EnrollmentTest() {
        super(new JettyTestContainerFactory());
    }

    @Override
    protected Application configure() {
        enable(TestProperties.LOG_TRAFFIC);
        enable(TestProperties.DUMP_ENTITY);

        return new MNOApplication();
    }

    @Override
    protected void configureClient(ClientConfig config) {
        config.register(GsonJerseyProvider.class);
    }

    @Test
    public void getSessionTest() {
        EnrollmentStartMessage session1 = target("/v2/dl/start").request(MediaType.APPLICATION_JSON)
                .get(EnrollmentStartMessage.class);

        EnrollmentStartMessage session2 = target("/v2/dl/start").request(MediaType.APPLICATION_JSON)
                .get(EnrollmentStartMessage.class);

        // The length con vary a little bit, the probability that they are less
        // than the minimum length is extremely low ~ 2^-80
        assert(session1.getSessionToken().length() > 20);
        assert(session2.getSessionToken().length() > 20);

        // Session tokens should be unique
        assert(!session1.getSessionToken().equals(session2.getSessionToken()));
    }

    @Test
    public void EDLTest() {
        EnrollmentStartMessage startMsg = target("/v2/dl/start")
                .request(MediaType.APPLICATION_JSON)
                .get(EnrollmentStartMessage.class);

        EnrollmentSession session = EnrollmentSessions.getSessions().getSession(startMsg.getSessionToken());
        session.setAANonce(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

        PassportVerificationResultMessage resultMsg = target("/v2/dl/verify-document")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(getEDLDataMessage(startMsg.getSessionToken()), MediaType.APPLICATION_JSON),
                        PassportVerificationResultMessage.class);

        assert(resultMsg.getResult() == PassportVerificationResult.SUCCESS);

        CredentialIdentifier cred = new CredentialIdentifier("irma-demo.MijnOverheid.fullName");
        assert(session.getCredentialList().get(cred).get("familyname").equals("Zeilemaker"));
    }

    private EDLDataMessage getEDLDataMessage(String sessionToken) {
        return GsonUtil.getGson().fromJson("{\"sessionToken\":\"" + sessionToken + "\",\"imsi\":\"\",\"docNr\":\"1509496211\",\"sodFile\":\"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\",\"dg1File\":\"YYIBTF8BDWU0LURMMDAgMDAwMDFfAl1fAwNOTERfBApaZWlsZW1ha2VyXwUIU3ZlbiBSIFZfBgQCERl0XwcHVmxldXRlbl8KBBURIBRfCwQVESAkXwwQR2VtZWVudGUgVmVlbmRhbV8OCjUwOTQ5NjIxMTF/Y4HYAgERhw9BTTsBAhmTOxURICQ7OzuHD0ExOykEIBQ7FREgJDs7O4cPQTI7KQQgFDsVESAkOzs7hw5COwECGZM7FREgJDs7O4cbRDE7IgEgEDsVESAZOzk1OzsxNS4xMS4yMDE5hwxEMTs7OzAxLjA2OzuHGkQ7IgEgEDsVESAZOzk1OzsxNS4xMS4yMDE5hwtEOzs7MDEuMDY7O4cPQkU7IAIgBjsVESAkOzs7hxxEMUU7FREgFDsVESAZOzk1OzsxNS4xMS4yMDE5hw1EMUU7OzswMS4wNjs7\",\"dg15File\":\"b4IBBzCCAQMwDQYJKoZIhvcNAQEBBQADgfEAMIHtAoHlALgRawjGsQdF9j9DLg37fPC5VBjFDbPCh+NdzqHWf+S1QprZ6JWDHcGVOuDnFwM9MK8hm1UwdbQzkJzKEaQPsrKXE/8YUhUfVr3E+zdDLLFLpNLbNj2OiMDcq7N20YfDul8166WO2EeuMjMJszcaAxPuCWiiP57nevbEHNi3YaOnEjaTXocoyLBQrseRJSAcbUHrkOHEBxXAuwLtWAqOdLDFOzPlgghvNq5V/Tk0uE4DkRGQEbw8ykCnE1/70gBGWx4c8jIdAQCdpO2xcMGKAO4UomuwQwm2bRnf6eP9hwUeGED2PQIDAQAB\",\"response\":\"Uefz4sTvb8BqJX0jXlDYHYSEPmVXT4hdsu92rN8smWH8L0v18r2BGWxmtNqqHjRgXnhBFnUqWcKpvy4N4Fld6SqNBOJVYmMv3X1civi8yjHp906LN+xl7isYSZ5Oiibd+4VS9SHLjUhQKBfqE5GoLiqsdhGzjCvncT12MqSO+GXwV1b/bevxrCglvDfW/dxY1GHH+84ViUa2X7yDVz5+K5rifZn+KFI54Rvt1a9tI5uv8baQR3Quf16bcfOwUofILx6n16eGyeCGMjsmRouvijoqmx4dP5oJSggJcdpLCsjz2mzp\"}", EDLDataMessage.class);
    }
}
