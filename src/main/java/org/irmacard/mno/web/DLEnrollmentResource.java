package org.irmacard.mno.web;

import org.irmacard.credentials.info.CredentialIdentifier;
import org.irmacard.credentials.info.InfoException;
import org.irmacard.mno.common.DriverDemographicInfo;
import org.irmacard.mno.common.EDLDataMessage;
import org.irmacard.mno.common.EnrollmentStartMessage;
import org.irmacard.mno.common.PassportVerificationResultMessage;
import org.jmrtd.lds.MRZInfo;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

@Path("v2/dl")
public class DLEnrollmentResource extends GenericEnrollmentResource<EDLDataMessage> {
	@GET
	@Path("/start")
	@Produces(MediaType.APPLICATION_JSON)
	@Override
	public EnrollmentStartMessage start() {
		return super.start();
	}

	@POST
	@Path("/verify-document")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Override
	public PassportVerificationResultMessage verifyDocument(EDLDataMessage documentData)
			throws InfoException {
		return super.verifyDocument(documentData);
	}

	@Override
	protected HashMap<CredentialIdentifier, HashMap<String, String>> getCredentialList(EnrollmentSession session)
	throws InfoException { // TODO
		HashMap<CredentialIdentifier, HashMap<String, String>> credentials = new HashMap<>();
		EDLDataMessage data = session.getEDLDataMessage();
		DriverDemographicInfo driver = data.getDriverDemographicInfo();

		SimpleDateFormat bacDateFormat = new SimpleDateFormat("yyMMdd");
		SimpleDateFormat hrDateFormat = new SimpleDateFormat("MMM d, y"); // Matches Android's default date format
		Date dob;
		Date expiry;

		try {
			dob = bacDateFormat.parse(driver.getDob());
			expiry = bacDateFormat.parse(driver.getDoe());
		}  catch (ParseException e) {
			e.printStackTrace();
			throw new InfoException("Failed to parse dates", e);
		}

		int[] lowAges = {12,16,18,21};
		credentials.put(new CredentialIdentifier(SCHEME_MANAGER, ISSUER, "ageLower"), ageAttributes(lowAges, dob));

		int[] highAges = {50, 60, 65, 75};
		credentials.put(new CredentialIdentifier(SCHEME_MANAGER, ISSUER, "ageHigher"), ageAttributes(highAges, dob));

		return null;
	}
}
