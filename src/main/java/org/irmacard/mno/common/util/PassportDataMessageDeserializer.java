/*
 * PassportDataMessageDeserializer.java
 *
 * Copyright (c) 2015, Sietse Ringers, Radboud University
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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.bouncycastle.util.encoders.Base64;
import org.irmacard.mno.common.PassportDataMessage;
import org.jmrtd.lds.DG14File;
import org.jmrtd.lds.DG15File;
import org.jmrtd.lds.DG1File;
import org.jmrtd.lds.SODFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class PassportDataMessageDeserializer extends StdDeserializer<PassportDataMessage> {
	public PassportDataMessageDeserializer(Class<PassportDataMessage> type) {
		super(type);
	}

	@Override
	public PassportDataMessage deserialize(JsonParser jparser, DeserializationContext context)
			throws IOException, JsonProcessingException {
		String sessionToken = null;
		String imsi = null;
		SODFile sodFile = null;
		DG1File dg1File = null;
		DG14File dg14File = null;
		DG15File dg15File = null;
		byte[] response = null;

		while (jparser.nextToken() != JsonToken.END_OBJECT) {
			// Move to value
			jparser.nextToken();

			switch (jparser.getCurrentName()) {
				case "sessionToken":
					sessionToken = jparser.getText();
					System.out.println(sessionToken);
					break;
				case "imsi":
					imsi = jparser.getText();
					System.out.println(imsi);
					break;
				case "sodFile":
					sodFile = new SODFile(new ByteArrayInputStream(Base64.decode(jparser.getText())));
					System.out.println(sodFile.toString());
					break;
				case "dg1File":
					dg1File = new DG1File(new ByteArrayInputStream(Base64.decode(jparser.getText())));
					System.out.println(dg1File.toString());
					break;
				case "dg14File":
					dg14File = new DG14File(new ByteArrayInputStream(Base64.decode(jparser.getText())));
					System.out.println(dg14File.toString());
					break;
				case "dg15File":
					dg15File = new DG15File(new ByteArrayInputStream(Base64.decode(jparser.getText())));
					System.out.println(dg15File.toString());
					break;
				case "response":
					response = Base64.decode(jparser.getText());
					System.out.println(response.length);
					break;
				default:
					break;
			}
		}

		jparser.close();

		if (imsi == null || sodFile == null || dg1File == null || dg15File == null || response == null) {
			throw new JsonMappingException("Required field not found");
		}

		PassportDataMessage passportMsg = new PassportDataMessage(sessionToken, imsi);
		passportMsg.setSodFile(sodFile);
		passportMsg.setDg1File(dg1File);
		passportMsg.setDg14File(dg14File);
		passportMsg.setDg15File(dg15File);
		passportMsg.setResponse(response);

		return passportMsg;
	}
}
