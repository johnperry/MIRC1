/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.dicomservice;

import java.io.*;
import java.net.*;
import java.security.cert.X509Certificate;
import java.security.SecureRandom;
import java.util.*;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.log4j.Logger;
import org.rsna.mircsite.log.Log;
import org.rsna.mircsite.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The Thread that exports DicomObjects via the HTTP and HTTPS
 * protocols.
 */
public class HttpExportService extends Thread {

	static final String serviceName = "HttpExportService";
	static final Logger logger = Logger.getLogger(HttpExportService.class);

	private TrustManager[] trustAllCerts;

	boolean running = false;
	boolean reinitialize = false;
	File[] directoryFiles = null;
	String[] urls = null;
	long[] nextTimes = null;

	/**
	 * Class constructor; creates a new instance of the HttpExportService.
	 * This class supports both HTTP and HTTPS. When using HTTPS, this class
	 * accepts all certificates from the remote server, so it only uses the
	 * encryption part of TLS, not the authentication part.
	 */
	public HttpExportService() {
		System.getProperties().remove("java.protocol.handler.pkgs");
		trustAllCerts = new TrustManager[] { new AcceptAllX509TrustManager() };
		try {
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null,trustAllCerts, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		}
		catch (Exception e) { }
		initialize();
	}

	//Get the arrays of export directories, URLs and timeouts.
	//This function is called when the service is created and whenever the
	//service is restarted.
	private void initialize() {
		reinitialize = false;
		directoryFiles = TrialConfig.getHttpExportDirectoryFiles();
		urls = TrialConfig.getHttpExportURLs();
		nextTimes = new long[urls.length];
		for (int i=0; i<nextTimes.length; i++) {
			nextTimes[i] = 0;
		}
	}

	/**
	 * Get the status text for display by the admin service.
	 */
	public String getStatus() {
		if (running) return "running";
		return "not running";
	}

	/**
	 * The Runnable interface implementation. Process the queue directories
	 * and send the files, then sleep for 10 seconds and check again.
	 */
	public void run() {
		Log.message(serviceName+": Started");
		running = true;
		while (!interrupted()) {
			try {
				processFiles();
				sleep(10000);
			}
			catch (Exception e) {
				running = false;
				return;
			}
			if (reinitialize) {
				initialize();
				Log.message(serviceName+": Reinitialized");
			}
		}
		Log.message(serviceName+": Interrupt received");
	}

	/**
	 * Restart the HttpExportService. Set a flag to cause the service to
	 * reinitialize after the next pass through the export directories.
	 */
	public void restart() {
		reinitialize = true;
	}

	//Look through the export directories and
	//send all the files there, oldest first. Note that in the
	//MIRCsite implementation, the files are actually queue
	//elements (text files containing the absolute file path
	//to the DICOM object to be exported. This is different from
	//the HttpExportService in the FieldCenter implementation
	//because the MIRCsite can handle multiple destinations
	//whereas the FieldCenter application is designed for a
	//single destination.
	private void processFiles() {
		String result;
		long currentTime;
		for (int i=0; i<urls.length; i++) {
			File[] files = directoryFiles[i].listFiles();
			for (int k=0; (k<files.length) && ((currentTime = System.currentTimeMillis()) > nextTimes[i]); k++) {
				File file = files[k];
				result = export(file,urls[i]);
				if (result.equals("OK")) {
					file.delete();
					Log.message(serviceName+": Export successful: "+file.getName());
				}
				else if (result.equals("FailedURLConnection")) {
					//wait 10 minutes if there was no connection
					nextTimes[i] = currentTime + 600000;
					Log.message(serviceName+": URL Connection failure to "+urls[i]);
				}
				else {
					if (result.startsWith("Server:")) {
						Log.message(serviceName+": Failure response from "+urls[i]+":<br>"
										+ result + ": " + file.getName());
						logger.info(serviceName+": Failure response from "+urls[i]+":<br>"
										+ result + ": " + file.getName());
					}
					else if (result.trim().equals("")) {
						Log.message(serviceName+": No response from "+urls[i]+":<br>" + file.getName());
						logger.info(serviceName+": No response from "+urls[i]+":<br>" + file.getName());
					}
					else {
						Log.message(serviceName+": Export failure: " + result + ": " + file.getName());
						logger.info(serviceName+": Export failure: " + result + ": " + file.getName());
					}
					Log.message(Quarantine.file(file,serviceName));
				}
				yield();
			}
		}
	}

	//Export one file.
	private String export(File file, String urlString) {
		int n;
		URL url;
		HttpURLConnection conn;
		OutputStream svros;
		FileInputStream fis;
		BufferedReader svrrdr;
		ExportQueueElement eqe;
		File fileToExport;
		//Use the file input argument to create a queue element
		//and then use it to get the queued file.
		eqe = new ExportQueueElement(file);
		fileToExport = eqe.getQueuedFile();
		if (fileToExport == null) return "Error: Null fileToExport";
		try {
			//Make the connection
			url = new URL(urlString);
			String protocol = url.getProtocol().toLowerCase();
			if (protocol.startsWith("https")) {
				HttpsURLConnection httpsConn = (HttpsURLConnection)url.openConnection();
				httpsConn.setHostnameVerifier(new AcceptAllHostnameVerifier());
				httpsConn.setUseCaches(false);
				httpsConn.setDefaultUseCaches(false);
				conn = httpsConn;
			}
			else if (protocol.startsWith("http")) {
				conn = (HttpURLConnection)url.openConnection();
			}
			else {
				logger.info("Illegal protocol: " + protocol);
				return "Illegal protocol: " + protocol;
			}
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setRequestMethod("POST");
			//Set the content type to a special one for
			//MIRC HTTP transmission of DICOM objects.
			conn.setRequestProperty("Content-Type","application/x-mirc-dicom");
			conn.connect();
			svros = conn.getOutputStream();
		}
		catch (Exception e) {
			logger.info("FailedURLConnection: "+e.getMessage());
			return "FailedURLConnection";
		}
		try {
			fis = new FileInputStream(fileToExport);
		}
		catch (IOException e) { return "Local[1]: "+e.getMessage(); }
		try {
			//Send the file to the server
			byte[] bbuf = new byte[1024];
			while ((n=fis.read(bbuf,0,bbuf.length)) > 0) svros.write(bbuf,0,n);
			svros.flush();
			svros.close();
			fis.close();
		}
		catch (IOException e) { return "Local[2]: "+e.getMessage(); }
		InputStream is;
		//Get the response
		try { is = conn.getInputStream(); }
		catch (IOException e) { return "Local[3]: (exception from getInputStream) "+e.getMessage(); }
		InputStreamReader isr = new InputStreamReader(is);
		svrrdr = new BufferedReader(isr);
		try {
			StringWriter svrsw = new StringWriter();
			char[] cbuf = new char[1024];
			while ((n = svrrdr.read(cbuf,0,cbuf.length)) != -1) svrsw.write(cbuf,0,n);
			svrrdr.close();
			return svrsw.toString();
		}
		catch (IOException e) {
			return "Local[4]:"+e.getMessage();
		}
	}

	//All-accepting X509 Trust Manager
	//
	class AcceptAllX509TrustManager implements X509TrustManager {
		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}
		public void checkClientTrusted(X509Certificate[] certs, String authType) {
		}
		public void checkServerTrusted(X509Certificate[] certs, String authType) {
		}
	}

	//All-verifying HostnameVerifier
	//
	class AcceptAllHostnameVerifier implements HostnameVerifier {
		public boolean verify(String urlHost, SSLSession ssls) {
			return true;
		}
	}

}