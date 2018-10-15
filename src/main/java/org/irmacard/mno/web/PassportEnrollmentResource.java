package org.irmacard.mno.web;

import com.google.gson.Gson;
import org.irmacard.api.common.AttributeDisjunction;
import org.irmacard.api.common.AttributeDisjunctionList;
import org.irmacard.api.common.ClientQr;
import org.irmacard.credentials.info.AttributeIdentifier;
import org.irmacard.credentials.info.CredentialIdentifier;
import org.irmacard.credentials.info.InfoException;
import org.irmacard.mno.common.*;
import org.irmacard.mno.common.util.GsonUtil;
import org.jmrtd.lds.icao.MRZInfo;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.security.KeyManagementException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.util.stream.Collectors.joining;

@Path("v2/passport")
public class PassportEnrollmentResource extends GenericEnrollmentResource<PassportDataMessage> {
	protected static Gson gson = GsonUtil.getGson();

	@GET
	@Path("/start")
	@Produces(MediaType.APPLICATION_JSON)
	@Override
	public EnrollmentStartMessage start() {
		return super.start();
	}

	@GET
	@Path("/surfverify")
	@Produces(MediaType.APPLICATION_JSON)
	public DisclosureSessionMessage verifySurf() {
		//TODO: change to post, to first verify sessionnumber?
		AttributeDisjunctionList list = new AttributeDisjunctionList(4);
		list.add(new AttributeDisjunction("First name", getAttributeIdentifier("firstname")));
		list.add(new AttributeDisjunction("Last name", getAttributeIdentifier("familyname")));
		list.add(new AttributeDisjunction("Radboud number", getAttributeIdentifier("id")));
		list.add(new AttributeDisjunction("E-mail address", getAttributeIdentifier("email")));
		try {
			ClientQr qr = ApiClient.createDisclosureSession(
					list,
					"testsp",
					"testsp",
					MNOConfiguration.getInstance().getJwtAlgorithm(),
					MNOConfiguration.getInstance().getJwtPrivateKey());
			DisclosureSessionMessage msg = new DisclosureSessionMessage(qr,
					qr.getUrl().replace("http","ws").replace("verification","status"),
					qr.getUrl().concat("/getproof"));
			return msg;
		} catch (KeyManagementException e) {
			System.out.println("ERROR: getJWTPrivateKey failed");
			e.printStackTrace();
			throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
		}
	}

	private AttributeIdentifier getAttributeIdentifier(String attributeName) {
		return new AttributeIdentifier(
				new CredentialIdentifier(
						"pbdf",
						"pbdf",
						"surfnet"),
				attributeName
		);
	}

	@POST
	@Path("/verify-documents")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public ValidationResultMessage verifyDocuments(Map<Integer,String> dataMap){
		List<AbstractDocumentData> data = retreiveDataFromJSON(dataMap);
		for (AbstractDocumentData datum: data){
			ValidationResult result = datum.validate();
			if (!result.isValid()){
				return new ValidationResultMessage(result);
			}
		}
		//if we get here, then all data has been validated, so we start issuing
		return retreiveIssuingJWT(data);
	//	return new ValidationResultMessage(new ValidationResult(ValidationResult.Result.VALID));
	}

	private ValidationResultMessage retreiveIssuingJWT(List<AbstractDocumentData> data){
		ValidationResultMessage msg = new ValidationResultMessage(new ValidationResult(ValidationResult.Result.VALID));
		HashMap<CredentialIdentifier, HashMap<String,String>> toIssue = new HashMap<>();
		for (AbstractDocumentData datum: data){
			toIssue.put(datum.getCredentialIdentifier(),datum.getIssuingJWT());
		}
		System.out.println("Created issuing list: " + toIssue.toString());
		try {
			msg.setIssueQr(ApiClient.createIssuingSession(toIssue,
					MNOConfiguration.getInstance().getApiName(),
					MNOConfiguration.getInstance().getJwtAlgorithm(),
					MNOConfiguration.getInstance().getJwtPrivateKey()));
			System.out.println("set the QR?");
		} catch (KeyManagementException e) {
			System.out.println("ERROR: getJWTPrivateKey failed");
			e.printStackTrace();
			throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
		}
		System.out.println("Msg to send: "+ gson.toJson(msg));
		return msg;
	}

	private List<AbstractDocumentData> retreiveDataFromJSON(Map<Integer,String> dataMap){
		List<AbstractDocumentData> data = new ArrayList<>();
		for (Map.Entry<Integer,String> documentData : dataMap.entrySet()){
			System.out.println("Object found: "+documentData.getValue());
			switch (documentData.getKey()){
				case AbstractDocumentData.RADBOUD:
					RadboudData rd = gson.fromJson(documentData.getValue(), RadboudData.class);
					try {
						rd.setJwtSigningKey(MNOConfiguration.getInstance().getApiJwtKey());
					} catch (KeyManagementException e){
						System.out.println("unable to set API JWT key for Raboud credential");
					}
					data.add(rd);
					break;
				case AbstractDocumentData.PASSPORT:
					PassportData pd = gson.fromJson(documentData.getValue(), PassportData.class);
					data.add(pd);
					break;
				case AbstractDocumentData.EDL:
					EDlData edd = gson.fromJson(documentData.getValue(),EDlData.class);
					data.add(edd);
					break;
				default:
					System.out.println("Error, encountered unknown object, type: "+documentData.getKey()+" objString: "+documentData.getValue());
					break;
			}
		}
		return data;
	}

	@POST
	@Path("/verify-document")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Override
	public PassportVerificationResultMessage verifyDocument(PassportDataMessage documentData)
			throws InfoException {
		return super.verifyDocument(documentData);
	}

	@Override
	protected HashMap<CredentialIdentifier, HashMap<String, String>> getCredentialList(EnrollmentSession session)
	throws InfoException {
		HashMap<CredentialIdentifier, HashMap<String, String>> credentials = new HashMap<>();
		MRZInfo mrz;
		try {
			mrz = session.getPassportDataMessage().getDg1File().getMRZInfo();
		} catch (NullPointerException e) {
			throw new InfoException("Cannot retrieve MRZ info");
		}

		SimpleDateFormat bacDateFormat = new SimpleDateFormat("yyMMdd");
		SimpleDateFormat hrDateFormat = new SimpleDateFormat("MMM d, y"); // Matches Android's default date format
		Date dob;
		Date expiry;

		try {
			dob = bacDateFormat.parse(mrz.getDateOfBirth());
			expiry = bacDateFormat.parse(mrz.getDateOfExpiry());
		}  catch (ParseException e) {
			e.printStackTrace();
			throw new InfoException("Failed to parse MRZ", e);
		}

		int[] lowAges = {12,16,18,21};
		credentials.put(new CredentialIdentifier(SCHEME_MANAGER, ISSUER, "ageLower"), ageAttributes(lowAges, dob));

		int[] highAges = {50, 60, 65, 75};
		credentials.put(new CredentialIdentifier(SCHEME_MANAGER, ISSUER, "ageHigher"), ageAttributes(highAges, dob));

		HashMap<String,String> nameAttributes = new HashMap<>();
		String[] nameParts = splitFamilyName(mrz.getPrimaryIdentifier());
		String firstnames = toTitleCase(joinStrings(mrz.getSecondaryIdentifierComponents()));
		// The first of the first names is not always the person's usual name ("roepnaam"). In fact, the person's
		// usual name need not even be in his list of first names at all. But given only the MRZ, there is no way of
		// knowing what is his/her usual name... So we can only guess.
		String firstname = toTitleCase(mrz.getSecondaryIdentifierComponents()[0]);

		nameAttributes.put("familyname", toTitleCase(nameParts[1]));
		nameAttributes.put("prefix", nameParts[0]);
		nameAttributes.put("firstnames", firstnames);
		nameAttributes.put("firstname", firstname);

		credentials.put(new CredentialIdentifier(SCHEME_MANAGER, ISSUER, "fullName"), nameAttributes);

		HashMap<String, String> idDocumentAttributes = new HashMap<String, String>();
		idDocumentAttributes.put("number", mrz.getDocumentNumber());
		idDocumentAttributes.put("expires", hrDateFormat.format(expiry));
		idDocumentAttributes.put("nationality", mrz.getNationality());
		if (mrz.getDocumentType() == MRZInfo.DOC_TYPE_ID1)
			idDocumentAttributes.put("type", "ID card");
		else if (mrz.getDocumentType() == MRZInfo.DOC_TYPE_ID3)
			idDocumentAttributes.put("type", "Passport");
		else
			idDocumentAttributes.put("type", "unknown");
		credentials.put(new CredentialIdentifier(SCHEME_MANAGER, ISSUER, "idDocument"), idDocumentAttributes);

		return credentials;
	}

	@POST
	@Path("/image-converter")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.TEXT_PLAIN)
	public String convertToBmp(String otherImageType) {
		try {
			// GraphicsMagick (gm) tool needed to convert images
			ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", "echo " + otherImageType + " | base64 --decode | gm convert - bmp:- | base64");
			Process p = pb.start();
			BufferedReader ir = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String bmp =  ir.lines().collect(joining());
			if (bmp.length() > 1000) {
				// Check whether result is large enough to be an image
				return bmp;
			}
		}
		catch(IOException e) {
			e.printStackTrace();
			throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
		}
		throw new WebApplicationException("Image manipulation of given image type is not supported", Response.Status.NOT_IMPLEMENTED);
	}
}
