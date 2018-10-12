package org.irmacard.mno.web;

import com.google.gson.JsonSyntaxException;
import io.jsonwebtoken.SignatureAlgorithm;
import org.irmacard.api.common.util.GsonUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

@SuppressWarnings({"MismatchedQueryAndUpdateOfCollection", "FieldCanBeLocal", "unused"})
public class MNOConfiguration {
	private static final String filename = "config.json";
	private static MNOConfiguration instance;

	private String api_server = "";
	private String api_name = "";
	private String jwt_api_key = "api-jwt.der";
	private boolean sign_issue_jwts = true;
	private String jwt_privatekey = "sk.der";

	private transient PrivateKey jwtPrivateKey;
	private transient PublicKey jwtApiKey;

	public MNOConfiguration() {}

	/**
	 * Reloads the configuration from disk so that {@link #getInstance()} returns the updated version
	 */
	public static void load() {
		// TODO: GSon seems to always be lenient (i.e. allow comments in the JSon), even though
		// the documentation states that by default, it is not lenient. Why is this? Could change?
		try {
			String json = new String(getResource(filename));
			instance = GsonUtil.getGson().fromJson(json, MNOConfiguration.class);
		} catch (IOException |JsonSyntaxException e) {
			System.out.println("WARNING: could not load configuration file. Using default values");
			instance = new MNOConfiguration();
		}

		System.out.println("Configuration:");
		System.out.println(instance.toString());
	}

	public static MNOConfiguration getInstance() {
		if (instance == null)
			load();

		return instance;
	}

	private String getApiServerUrl() {
		return api_server;
	}

	public String getApiName() {
		return api_name;
	}

	public boolean shouldSignJwt() {
		return sign_issue_jwts;
	}

	public PrivateKey getJwtPrivateKey() throws KeyManagementException {
		if (jwtPrivateKey == null) {
			try {
				byte[] bytes = MNOConfiguration.getResource(jwt_privatekey);
				if (bytes == null || bytes.length == 0)
					throw new KeyManagementException("Could not read private key");

				PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);

				jwtPrivateKey = KeyFactory.getInstance("RSA").generatePrivate(spec);
			} catch (IOException|NoSuchAlgorithmException|InvalidKeySpecException e) {
				throw new KeyManagementException(e);
			}
		}

		return jwtPrivateKey;
	}

	public SignatureAlgorithm getJwtAlgorithm() {
		return SignatureAlgorithm.RS256;
	}

	public static byte[] getResource(String filename) throws IOException {
		URL url = MNOConfiguration.class.getClassLoader().getResource(filename);
		if (url == null)
			throw new IOException("Could not load file " + filename);

		URLConnection urlCon = url.openConnection();
		urlCon.setUseCaches(false);
		return convertSteamToByteArray(urlCon.getInputStream(), 2048);
	}

	public static byte[] convertSteamToByteArray(InputStream stream, int size) throws IOException {
		byte[] buffer = new byte[size];
		ByteArrayOutputStream os = new ByteArrayOutputStream();

		int line;
		while ((line = stream.read(buffer)) != -1) {
			os.write(buffer, 0, line);
		}
		stream.close();

		os.flush();
		os.close();
		return os.toByteArray();
	}

	@Override
	public String toString() {
		return GsonUtil.getGson().toJson(this);
	}

    public String getApiServerIssueUrl() {
        return getApiServerUrl() + "/issue/";
    }

	public String getApiServerDisclosureUrl() {
		return getApiServerUrl() + "/verification/";
	}

    public Key getApiJwtKey() throws KeyManagementException {
		if (jwtApiKey == null) {
			try {
				byte[] bytes = MNOConfiguration.getResource(jwt_api_key);
				if (bytes == null || bytes.length == 0)
					throw new KeyManagementException("Could not read public key");

				PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);

				jwtApiKey = KeyFactory.getInstance("RSA").generatePublic(spec);
			} catch (IOException|NoSuchAlgorithmException|InvalidKeySpecException e) {
				throw new KeyManagementException(e);
			}
		}

		return jwtApiKey;
    }
}
