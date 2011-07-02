/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import javax.swing.*;

import java.io.*;
import java.net.*;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.security.SecureRandom;
import java.util.*;
import org.rsna.mircsite.util.ProxyServer;

import org.rsna.mircsite.log.Log;

/**
 * Encapsulates static methods for communicating with the MIRC Update Service.
 * Thess methods allow an application to perform an upload to or a download.
 */
public class UpdateUtil {

	/**
	 * GET a file from site-specific respository on the Update Service.
	 * If the file exists on the calling system, the lastModified
	 * time is passed to the Update Service, and the file will be
	 * downloaded only if the lastModified time on the server is
	 * greater than that on the calling system.
	 * @param file the file to get, pointing to the place on the calling
	 * system where the file is to be stored.
	 * @param props the properties object containing the parameters required
	 * for the connection, including:
	 * <ul>
	 * <li>update-url: the url of the Update Service</li>
	 * <li>update-username: the username on the server for the calling system</li>
	 * <li>update-password: the password on the server for the calling system</li>
	 * <li>proxy-enabled: the flag indicating whether to use a proxy server for communication</li>
	 * <li>proxy-ipaddress: the Proxy Server IP address</li>
	 * <li>proxy-port: the Proxy Server port</li>
	 * <li>proxy-username: the Proxy Server username</li>
	 * <li>proxy-password: the Proxy Server password</li>
	 * </ul>
	 * @return true if the file was downloaded and stored; false otherwise.
	 */
	public static boolean getFile(File file, Properties props) {
		return getFile(file,false,props);
	}

	/**
	 * GET a file from the Update Service. If the file exists on the calling system,
	 * the lastModified time is passed to the Update Service, and the file will be
	 * downloaded only if the lastModified time on the server is greater than that
	 * on the calling system.
	 * @param file the file to get, pointing to the place on the calling system where
	 * the file is to be stored.
	 * @param swupdate true if the file is to be obtained from the software repository,
	 * false if the file is to be obtained from the site-specific repository.
	 * @param props the properties object containing the parameters required
	 * for the connection, including:
	 * <ul>
	 * <li>update-url: the url of the Update Service</li>
	 * <li>update-username: the username on the server for the calling system</li>
	 * <li>update-password: the password on the server for the calling system</li>
	 * <li>proxy-enabled: the flag indicating whether to use a proxy server for communication</li>
	 * <li>proxy-ipaddress: the Proxy Server IP address</li>
	 * <li>proxy-port: the Proxy Server port</li>
	 * <li>proxy-username: the Proxy Server username</li>
	 * <li>proxy-password: the Proxy Server password</li>
	 * </ul>
	 * @return true if the file was downloaded and stored; false otherwise.
	 */
	public static boolean getFile(File file, boolean swupdate, Properties props) {
		String enabled = props.getProperty("update-enabled");
		if ((enabled == null) || enabled.equals("false")) return false;
		String updateURL = props.getProperty("update-url");
		if ((updateURL == null) || updateURL.equals("")) return false;
		updateURL += (updateURL.endsWith("/") ? "" : "/")
			+ (swupdate ? "sw/" : "") + file.getName();
		if (file.exists()) updateURL += "?lastModified=" + Long.toString(file.lastModified());

		try {
			HttpURLConnection conn = getConnection(updateURL, props);
			conn.setDoOutput(false);
			conn.setDoInput(true);
			conn.setRequestMethod("GET");

			//Make the connection
			conn.connect();
			//See if we get a response code
			int code = conn.getResponseCode();
			if (code != HttpURLConnection.HTTP_OK) return false;

			//Get the input and output streams and save the file
			BufferedInputStream svris = new BufferedInputStream(conn.getInputStream());
			BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(file));
			byte[] buffer = new byte[4096];
			int count;
			while ((count=svris.read(buffer,0,buffer.length)) != -1)
				fos.write(buffer,0,count);
			svris.close();
			fos.flush();
			fos.close();
			return true;
		}
		catch (Exception failure) { return false; }
	}

	/**
	 * POST a file to the site-specific repository on the Update Service.
	 * The filename is passed in the Content-Disposition header. The file's
	 * lastModified time is passed to the Update Service so it can be saved with the file.
	 * @param file the file to post, pointing to the place on the calling system where
	 * the file is located.
	 * @param props the properties object containing the parameters required
	 * for the connection, including:
	 * <ul>
	 * <li>update-url: the url of the Update Service</li>
	 * <li>update-username: the username on the server for the calling system</li>
	 * <li>update-password: the password on the server for the calling system</li>
	 * <li>proxy-enabled: the flag indicating whether to use a proxy server for communication</li>
	 * <li>proxy-ipaddress: the Proxy Server IP address</li>
	 * <li>proxy-port: the Proxy Server port</li>
	 * <li>proxy-username: the Proxy Server username</li>
	 * <li>proxy-password: the Proxy Server password</li>
	 * </ul>
	 * @return true if the file was uploaded and stored; false otherwise.
	 */
	public static boolean postFile(File file, Properties props) {
		String enabled = props.getProperty("update-enabled");
		if ((enabled == null) || enabled.equals("false")) return false;
		String updateURL = props.getProperty("update-url");
		if ((updateURL == null) || updateURL.equals("")) return false;
		updateURL += (updateURL.endsWith("/") ? "" : "/") + file.getName();
		if (file.exists())
			updateURL += "?lastModified=" + Long.toString(file.lastModified());
		else
			return false;

		try {
			HttpURLConnection conn = getConnection(updateURL, props);
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setRequestMethod("POST");
			String disposition = "attachment; filename=\"" + file.getName() + "\"";
			conn.setRequestProperty("Content-Disposition",disposition);
			conn.setRequestProperty("Content-Type","application/x-update");

			//Make the connection
			conn.connect();

			//Get the input and output streams and send the file
			BufferedOutputStream svros = new BufferedOutputStream(conn.getOutputStream());
			BufferedInputStream fis = new BufferedInputStream(new FileInputStream(file));
			byte[] buffer = new byte[4096];
			int totalcount = 0;
			int count;
			while ((count=fis.read(buffer,0,buffer.length)) != -1) {
				svros.write(buffer,0,count);
				totalcount += count;
			}
			svros.flush();
			svros.close();
			fis.close();

			return (conn.getResponseCode() == HttpURLConnection.HTTP_OK);
		}
		catch (Exception failure) { return false; }
	}

	//Local method to get the connection
	static HttpURLConnection getConnection(String updateURL, Properties props) throws Exception {
		HttpURLConnection conn;

		//Set the authenticator
		String updateUsername = props.getProperty("update-username");
		if (updateUsername == null) return null;
		String updatePassword = props.getProperty("update-password");
		if (updatePassword == null) return null;
		Authenticator.setDefault(
			new UpdateAuthenticator(updateUsername,updatePassword));

		//If the proxy is enabled, set the Java System Properties for it.
		//If it is not enabled, remove the Java System Properties for it.
		ProxyServer proxy = new ProxyServer(props);
		if (proxy.getProxyEnabled()) proxy.setProxyProperties();
		else proxy.clearProxyProperties();

		//Get the connection. Handle http and https.
		URL url = new URL(updateURL);
		String protocol = url.getProtocol().toLowerCase();
		if (protocol.startsWith("https")) {
			//Set up for SSL
			TrustManager[] trustAllCerts = new TrustManager[] { new AcceptAllX509TrustManager() };
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null,trustAllCerts, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			HttpsURLConnection httpsConn = (HttpsURLConnection)url.openConnection();
			httpsConn.setHostnameVerifier(new AcceptAllHostnameVerifier());
			httpsConn.setUseCaches(false);
			httpsConn.setDefaultUseCaches(false);
			conn = httpsConn;
		}
		else if (protocol.startsWith("http")) {
			//It's not SSL, set up for straight HTTP
			conn = (HttpURLConnection)url.openConnection();
		}
		else return null;

		//If the proxy is enabled and authentication credentials
		//are available, set them in the request.
		if (proxy.getProxyEnabled() && proxy.authenticate()) {
			conn.setRequestProperty(
				"Proxy-Authorization",
				"Basic "+proxy.getEncodedProxyCredentials());
		}
		return conn;
	}

	//Simple authenticator to pass the credentials
	static class UpdateAuthenticator extends Authenticator {
		String username = null;
		String password = null;
		/**
		 * Constructs the authenticator and saves the username and password.
		 * @param username the username of the account on the update service.
		 * @param password the password of the account on the update service.
		 */
		public UpdateAuthenticator(String username, String password) {
			super();
			this.username = username;
			this.password = password;
		}
		/**
		 * Provides the credentials during the authentication challenge.
		 * @return the credentials.
		 */
		protected PasswordAuthentication getPasswordAuthentication() {
			return new PasswordAuthentication(username,password.toCharArray());
		}
	}

	//All-accepting X509 Trust Manager
	static class AcceptAllX509TrustManager implements X509TrustManager {
		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}
		public void checkClientTrusted(X509Certificate[] certs, String authType) {
		}
		public void checkServerTrusted(X509Certificate[] certs, String authType) {
		}
	}

	//All-verifying HostnameVerifier
	static class AcceptAllHostnameVerifier implements HostnameVerifier {
		public boolean verify(String urlHost, SSLSession ssls) {
			return true;
		}
	}

}