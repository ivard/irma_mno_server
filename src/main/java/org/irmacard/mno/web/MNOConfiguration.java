package org.irmacard.mno.web;

import com.google.gson.JsonSyntaxException;
import org.irmacard.api.common.util.GsonUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

@SuppressWarnings({"MismatchedQueryAndUpdateOfCollection", "FieldCanBeLocal", "unused"})
public class MNOConfiguration {
	private static final String filename = "config.json";
	private static MNOConfiguration instance;

	private String api_server = "";
	private String api_name = "";

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

	public String getApiServerUrl() {
		return api_server;
	}

	public String getApiName() {
		return api_name;
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
}
