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

package org.irmacard.mno.web.common.util;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.irmacard.mno.common.util.ProtocolCommandDeserializer;
import org.irmacard.mno.common.util.ProtocolCommandSerializer;
import org.irmacard.mno.common.util.ProtocolResponseDeserializer;
import org.irmacard.mno.common.util.ProtocolResponseSerializer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import net.sf.scuba.smartcards.CommandAPDU;
import net.sf.scuba.smartcards.ProtocolCommand;
import net.sf.scuba.smartcards.ProtocolCommands;
import net.sf.scuba.smartcards.ProtocolResponse;
import net.sf.scuba.smartcards.ResponseAPDU;

public class JsonSerialisationTest {
    private ObjectMapper mapper;

    @Before
    public void setup() {
        mapper = new ObjectMapper();
        SimpleModule customSerDes = new SimpleModule();
        customSerDes.addSerializer(new ProtocolCommandSerializer(ProtocolCommand.class));
        customSerDes.addDeserializer(ProtocolCommand.class, new ProtocolCommandDeserializer(ProtocolCommand.class));
        customSerDes.addSerializer(new ProtocolResponseSerializer(ProtocolResponse.class));
        customSerDes.addDeserializer(ProtocolResponse.class, new ProtocolResponseDeserializer(ProtocolResponse.class));
        mapper.registerModule(customSerDes);
    }

    // Test
    @Test
    public void testProtocolCommandSerialize() throws JsonProcessingException {
        byte[] data = { (byte) 0x80, (byte) 0x1B, (byte) 0x01, (byte) 0x00 };
        CommandAPDU apdu = new CommandAPDU(data);
        ProtocolCommand tst = new ProtocolCommand("test", "Testing", apdu);

        String expected = "{\"key\":\"test\",\"command\":\"801B0100\"}";

        assertEquals(expected, mapper.writeValueAsString(tst));
    }

    @Test
    public void testProtocolCommandDeserialize() throws JsonParseException, JsonMappingException, IOException {
        byte[] data = { (byte) 0x80, (byte) 0x1B, (byte) 0x01, (byte) 0x00 };
        String json = "{\"key\":\"test\",\"command\":\"801B0100\"}";
        ProtocolCommand cmd = mapper.readValue(json, ProtocolCommand.class);

        assertEquals("test", cmd.getKey());
        Assert.assertArrayEquals(data, cmd.getAPDU().getBytes());
    }

    @Test
    public void testProtocolCommandsDeserialize() throws JsonParseException, JsonMappingException, IOException {
        byte[] data = { (byte) 0x80, (byte) 0x1B, (byte) 0x01, (byte) 0x00 };

        String json = "[{\"key\":\"test1\",\"command\":\"801B0100\"}, {\"key\":\"test2\",\"command\":\"801B0100\"}]";
        ProtocolCommands cmds = mapper.readValue(json, ProtocolCommands.class);

        assertEquals(2, cmds.size());

        ProtocolCommand cmd;

        cmd = cmds.get(0);
        assertEquals("test1", cmd.getKey());

        cmd = cmds.get(1);
        assertEquals("test2", cmd.getKey());
    }

    @Test
    public void testProtocolResponseSerialize() throws JsonProcessingException {
        byte[] data = { (byte) 0x80, (byte) 0x1B, (byte) 0x01, (byte) 0x00, (byte) 0x90, 0x00 };
        ResponseAPDU rapdu = new ResponseAPDU(data);
        ProtocolResponse tst = new ProtocolResponse("test", rapdu);

        String expected = "{\"key\":\"test\",\"apdu\":\"801B01009000\"}";

        assertEquals(expected, mapper.writeValueAsString(tst));
    }

    @Test
    public void testProtocolResponseDeserialize() throws JsonParseException, JsonMappingException, IOException {
        byte[] data = { (byte) 0x80, (byte) 0x1B, (byte) 0x01, (byte) 0x00 };
        String json = "{\"key\":\"test\",\"apdu\":\"801B0100\"}";
        ProtocolResponse response = mapper.readValue(json, ProtocolResponse.class);

        assertEquals("test", response.getKey());
        Assert.assertArrayEquals(data, response.getAPDU().getBytes());
    }

}
