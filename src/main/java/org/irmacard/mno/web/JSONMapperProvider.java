/*
 * JSONMapperProvider.java
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

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

import org.irmacard.mno.common.util.ProtocolCommandDeserializer;
import org.irmacard.mno.common.util.ProtocolCommandSerializer;
import org.irmacard.mno.common.util.ProtocolResponseDeserializer;
import org.irmacard.mno.common.util.ProtocolResponseSerializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import net.sf.scuba.smartcards.ProtocolCommand;
import net.sf.scuba.smartcards.ProtocolResponse;


@Provider
public class JSONMapperProvider implements ContextResolver<ObjectMapper> {
    final ObjectMapper mapper;

    public JSONMapperProvider() {
        mapper = new ObjectMapper();

        // Indent output
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);

        // Improved ENUM handling
        mapper.registerModule(getJSONEnumSerializer());

        // Special purpose data types serialization
        SimpleModule customSerDes = new SimpleModule();

        customSerDes.addSerializer(new ProtocolCommandSerializer(ProtocolCommand.class));
        customSerDes.addDeserializer(ProtocolCommand.class, new ProtocolCommandDeserializer(ProtocolCommand.class));

        customSerDes.addSerializer(new ProtocolResponseSerializer(ProtocolResponse.class));
        customSerDes.addDeserializer(ProtocolResponse.class, new ProtocolResponseDeserializer(ProtocolResponse.class));

        mapper.registerModule(customSerDes);
    }

    @Override
    public ObjectMapper getContext(Class<?> type) {
        return mapper;
    }

    /* Fancy ENUM handling, in particular encode the values as
     * lower case representations of their Java enumeration constants.
     * For example, the array of enums [ONE, TWO, THREE] is encoded
     * as ["one", "two", "three"] in JSON (rather than ["ONE", "TWO",
     * "THREE"]).
     *
     * Thanks to: http://stackoverflow.com/a/24173645
     */
    @SuppressWarnings("rawtypes")
	private SimpleModule getJSONEnumSerializer() {
		SimpleModule module = new SimpleModule();
		module.setDeserializerModifier(new BeanDeserializerModifier() {
			@Override
			public JsonDeserializer<Enum> modifyEnumDeserializer(DeserializationConfig config, final JavaType type,
					BeanDescription beanDesc, final JsonDeserializer<?> deserializer) {
				return new JsonDeserializer<Enum>() {
					@SuppressWarnings("unchecked")
					@Override
					public Enum deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
						Class<? extends Enum> rawClass = (Class<Enum<?>>) type.getRawClass();
						return Enum.valueOf(rawClass, jp.getValueAsString().toUpperCase());
					}
				};
			}
		});
		module.addSerializer(Enum.class, new StdSerializer<Enum>(Enum.class) {
			private static final long serialVersionUID = 1L;

			@Override
			public void serialize(Enum value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
				jgen.writeString(value.name().toLowerCase());
			}
		});
		return module;
    }
}
