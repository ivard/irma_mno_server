package org.irmacard.mno.web.exceptions;

import org.irmacard.api.common.exceptions.ApiErrorMessage;
import org.irmacard.api.common.util.GsonUtil;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

/**
 * Convert an exception to a response for the client of the server
 */
public class MNOExceptionMapper implements ExceptionMapper<Throwable> {
	@Override
	public Response toResponse(Throwable ex) {
		ApiErrorMessage message = new ApiErrorMessage(ex);

		return Response.status(message.getStatus())
				.entity(GsonUtil.getGson().toJson(message))
				.type(MediaType.APPLICATION_JSON)
				.build();
	}
}
