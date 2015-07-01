/*
 * ProtocolCommandDeserializer.java
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

package org.irmacard.mno.common.util;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import net.sf.scuba.smartcards.CommandAPDU;
import net.sf.scuba.smartcards.ProtocolCommand;
import net.sf.scuba.util.Hex;

@SuppressWarnings("serial")
public class ProtocolCommandDeserializer extends StdDeserializer<ProtocolCommand> {

    public ProtocolCommandDeserializer(Class<ProtocolCommand> type) {
        super(type);
    }

    @Override
    public ProtocolCommand deserialize(JsonParser jparser, DeserializationContext context)
            throws IOException, JsonProcessingException {
        JsonToken token = jparser.nextValue();
        String key = null;
        String command = null;
        // TODO: this miraculously works, by stopping when we hit
        // END_OBJECT. Find out if this is the correct way to do it.
        // If you don't scan for END_OBJECT you'll consume the entire input.
        while (token != null && token != JsonToken.END_OBJECT) {
            switch (token) {
            case VALUE_STRING:
                switch (jparser.getCurrentName()) {
                case "key":
                    key = jparser.getText();
                    break;
                case "command":
                    command = jparser.getText();
                    break;
                default:
                    break;
                }
                break;
            default:
                break;
            }
            token = jparser.nextValue();
        }

        // TODO: include some sanity checks
        CommandAPDU apdu = new CommandAPDU(Hex.hexStringToBytes(command));
        ProtocolCommand cmd = new ProtocolCommand(key, "", apdu);
        return cmd;
    }

}
