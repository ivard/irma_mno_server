package org.irmacard.mno.common.util;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.irmacard.api.common.ClientQr;

import java.io.IOException;

public class ClientQrSerializer extends StdSerializer<ClientQr>{
	private static final long serialVersionUID = 7779089836235090923L;

	public ClientQrSerializer(Class<ClientQr> type) {
		super(type);
	}

	@Override
	public void serialize(ClientQr qr, JsonGenerator jgen, SerializerProvider provider)
			throws IOException, JsonGenerationException {
		jgen.writeStartObject();
		jgen.writeStringField("u", qr.getUrl());
		jgen.writeObjectField("v", qr.getVersion());
		jgen.writeEndObject();
	}
}
