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

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.glassfish.jersey.test.jetty.JettyTestContainerFactory;
import org.irmacard.credentials.Attributes;
import org.irmacard.credentials.CredentialsException;
import org.irmacard.credentials.idemix.IdemixCredentials;
import org.irmacard.credentials.idemix.descriptions.IdemixVerificationDescription;
import org.irmacard.credentials.idemix.info.IdemixKeyStore;
import org.irmacard.credentials.idemix.smartcard.IRMACard;
import org.irmacard.credentials.idemix.smartcard.SmartCardEmulatorService;
import org.irmacard.credentials.info.DescriptionStore;
import org.irmacard.credentials.info.InfoException;
import org.irmacard.idemix.IdemixService;
import org.irmacard.idemix.IdemixSmartcard;
import org.irmacard.mno.common.BasicClientMessage;
import org.irmacard.mno.common.EnrollmentStartMessage;
import org.irmacard.mno.common.RequestFinishIssuanceMessage;
import org.irmacard.mno.common.RequestStartIssuanceMessage;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import net.sf.scuba.smartcards.CardServiceException;
import net.sf.scuba.smartcards.ProtocolCommands;
import net.sf.scuba.smartcards.ProtocolResponse;
import net.sf.scuba.smartcards.ProtocolResponses;

public class EnrollmentTest extends JerseyTest {
    @BeforeClass
    public static void initializeInformation() throws InfoException {
        URI core = new File(System.getProperty("user.dir")).toURI().resolve("src/main/resources/irma_configuration/");
        DescriptionStore.setCoreLocation(core);
        DescriptionStore.getInstance();
        IdemixKeyStore.setCoreLocation(core);
        IdemixKeyStore.getInstance();

    }

    private SmartCardEmulatorService emulatedCardService;
    byte[] DEFAULT_CRED_PIN = "0000".getBytes();

    @Before
    public void setupCard() {
        IRMACard card = new IRMACard();
        emulatedCardService = new SmartCardEmulatorService(card);
    }

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
        EnrollmentStartMessage session1 = target("/v1/start").request(MediaType.APPLICATION_JSON)
                .get(EnrollmentStartMessage.class);

        EnrollmentStartMessage session2 = target("/v1/start").request(MediaType.APPLICATION_JSON)
                .get(EnrollmentStartMessage.class);

        // The length con vary a little bit, the probability that they are less
        // than the minimum length is extremely low ~ 2^-80
        assert(session1.getSessionToken().length() > 20);
        assert(session2.getSessionToken().length() > 20);

        // Session tokens should be unique
        assert(!session1.getSessionToken().equals(session2.getSessionToken()));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void issuanceTest() throws CredentialsException, CardServiceException, InfoException {
        // Create a new session
        EnrollmentStartMessage session = target("/v1/start").request(MediaType.APPLICATION_JSON)
                .get(EnrollmentStartMessage.class);

        // Request list of credentials that can be issued
        BasicClientMessage credentialListMsg = new BasicClientMessage(session.getSessionToken());
        HashMap<String, Map<String, byte[]>> credentialList = new HashMap<String, Map<String, byte[]>>();
        credentialList = target("/v1/issue/credential-list")
.request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(credentialListMsg, MediaType.APPLICATION_JSON), credentialList.getClass());

        assert(credentialList.containsKey("root"));

        // Before we start issuing the root credential, we connect to the card
        // and send the pin.
        IdemixService service = new IdemixService(emulatedCardService);
        IdemixCredentials ic = new IdemixCredentials(service);
        ic.connect();
        service.sendPin(DEFAULT_CRED_PIN);

        // Select applet
        ProtocolResponse select_response = service.execute(IdemixSmartcard.selectApplicationCommand);

        // Get first set of issuance messages
        RequestStartIssuanceMessage startIssuanceMsg = new RequestStartIssuanceMessage(session.getSessionToken(),
                select_response.getData());
        ProtocolCommands commands = target("/v1/issue/root/start").request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(startIssuanceMsg, MediaType.APPLICATION_JSON), ProtocolCommands.class);

        // Send the commands to the card
        ProtocolResponses responses = service.execute(commands);

        // Post responses to the server to get final signature
        RequestFinishIssuanceMessage finishIssuanceMsg = new RequestFinishIssuanceMessage(session.getSessionToken(),
                responses);
        commands = target("/v1/issue/root/finish").request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(finishIssuanceMsg, MediaType.APPLICATION_JSON), ProtocolCommands.class);

        // Send final set of commands to the card
        responses = service.execute(commands);

        // Verify the issued credential
        IdemixVerificationDescription ivd = new IdemixVerificationDescription("MijnOverheid", "rootAll");
        Attributes attributes = ic.verify(ivd);

        assert(attributes != null);
    }
}
