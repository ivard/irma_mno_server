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

import java.net.URI;
import java.net.URISyntaxException;

import javax.ws.rs.ApplicationPath;

import org.glassfish.jersey.server.ResourceConfig;
import org.irmacard.credentials.idemix.info.IdemixKeyStore;
import org.irmacard.credentials.idemix.info.IdemixKeyStoreDeserializer;
import org.irmacard.credentials.info.DescriptionStore;
import org.irmacard.credentials.info.DescriptionStoreDeserializer;
import org.irmacard.credentials.info.InfoException;
import org.irmacard.mno.web.exceptions.MNOExceptionMapper;

@ApplicationPath("/")
public class MNOApplication extends ResourceConfig {
    public MNOApplication() {
        // register Gson
        register(GsonJerseyProvider.class);

        // register exception handler, for converting and then returning exceptions as JSON output
        register(MNOExceptionMapper.class);

        // register enrollment applications
        register(APDUEnrollmentResource.class);
        register(JsonEnrollmentResource.class);

        // register session state
        register(new EnrollmentSessionsBinder());

        // Setup Core location for IRMA
        try {
            if (!DescriptionStore.isInitialized() || !IdemixKeyStore.isInitialized()) {
                URI CORE_LOCATION = MNOApplication.class.getClassLoader().getResource("/irma_configuration/").toURI();
                DescriptionStore.initialize(new DescriptionStoreDeserializer(CORE_LOCATION));
                IdemixKeyStore.initialize(new IdemixKeyStoreDeserializer(CORE_LOCATION));
            }
        } catch (URISyntaxException|InfoException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
