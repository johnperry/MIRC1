/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.filesender;

import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.log4j.*;
import org.rsna.dicom.DicomSender;

/**
 * The CmdLineSender program provides a command line
 * File Sender that transmits a single file. This
 * program is built in the same package as FileSender and
 * is installed in the same directory with it by the
 * FileSender-installer.jar program. It uses the same
 * library jars as FileSender.
 */
public class CmdLineSender {

	static final Logger logger = Logger.getLogger(CmdLineSender.class);

	/**
	 * The main method to start the program.
	 * <p>
	 * If the args array contains two or more parameters, the program
	 * attempts to send the file identified by the first parameter
	 * to the destination identified by the second parameter. If there
	 * are other parameters, they are interpreted as switches:
	 * <ol>
	 * <li>-d forces the Content-Type to DICOM for HTTP and HTTPS</li>
	 * </ol>
	 * @param args the list of arguments from the command line.
	 */
    public static void main(String args[]) {
		Logger.getRootLogger().addAppender(
				new ConsoleAppender(
					new PatternLayout("%d{HH:mm:ss} %-5p [%c{1}] %m%n")));
		Logger.getRootLogger().setLevel(Level.WARN);
        if (args.length >= 2) {

			//Get the file to be transmitted.
			File file = new File(args[0]);
			if (!file.exists() || !file.isFile()) {
				System.out.println("The file is not a data file.");
				return;
			}

			//Get the destination URL
			String url = args[1];

			//Get the swtiches
			String switches = "";
			for (int i=2; i<args.length; i++) switches += args[i];

			//Transmit the file
			CmdLineSender sender = new CmdLineSender(file,url, (switches.indexOf("-d") != -1) );
		}
		else {
			//Wrong number of arguments, just display some help.
			printHelp();
		}
    }

	//Transmit a file.
	public CmdLineSender(File file, String urlString, boolean forceDicomContentType) {
		String urlLC = urlString.toLowerCase().trim();
		boolean http = (urlLC.indexOf("http://") != -1);
		boolean https = (urlLC.indexOf("https://") != -1);
		boolean dicom = (urlLC.indexOf("dicom://") != -1);

		try {
			if (http || https) {
				sendFileUsingHttp(file,urlString,forceDicomContentType);
			}

			else if (dicom) {
				DicomURL dest = new DicomURL(urlString);
				sendFileUsingDicom(file,dest);
			}
		}
		catch (Exception e) { System.out.println("Exception:\n" + e.getMessage()); }
	}

	//Send one file using HTTP or HTTPS.
	private void sendFileUsingHttp(File file, String urlString, boolean forceDicomContentType) {
		URLConnection conn;
		OutputStream svros;
		FileInputStream fis;
		BufferedReader svrrdr;

		System.out.println("Send " + file.getAbsolutePath() + " to " + urlString);

		//Make the connection
		try {
			URL url = new URL(urlString);
			if (url.getProtocol().toLowerCase().startsWith("https")) {
				TrustManager[] trustAllCerts =
					new TrustManager[] { new AcceptAllX509TrustManager() };
				SSLContext sc = SSLContext.getInstance("SSL");
				sc.init(null,trustAllCerts, new SecureRandom());
				HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
				HttpsURLConnection httpsConn = (HttpsURLConnection)url.openConnection();
				httpsConn.setHostnameVerifier(new AcceptAllHostnameVerifier());
				httpsConn.setUseCaches(false);
				httpsConn.setDefaultUseCaches(false);
				conn = httpsConn;
			}
			else conn = url.openConnection();
			conn.setDoOutput(true);

			//Set the content type
			String contentType = null;
			if (forceDicomContentType || file.getName().matches("[\\d\\.]+"))
				contentType = "application/x-mirc-dicom";
			else {
				try {
					Properties contentTypes;
					InputStream is =
						this.getClass().getResource("/content-types.properties").openStream();
					contentTypes = new Properties();
					contentTypes.load(is);
					String ext = file.getName();
					ext = ext.substring(ext.lastIndexOf(".")+1).toLowerCase();
					contentType = (String)contentTypes.getProperty(ext);
				}
				catch (Exception e) {
					System.out.println(
						"Unable to load the content-types.properties resource:\n" + e.getMessage());
					return;
				}
			}
			if (contentType == null) contentType = "application/default";
			conn.setRequestProperty("Content-Type",contentType);

			//Set the content disposition
			conn.setRequestProperty(
				"Content-Disposition","attachment; filename=\"" + file.getName() + "\"");

			//Make the connection
			conn.connect();
			svros = conn.getOutputStream();
		}
		catch (Exception e) {
			System.out.println("Unable to establish a URLConnection to " + urlString);
			return;
		}

		//Get the stream to read the file
		try { fis = new FileInputStream(file);}
		catch (IOException e) {
			System.out.println(
				"Unable to obtain an input stream to read the file:\n" + e.getMessage());
			return;
		}

		//Send the file to the server
		try {
			int n;
			byte[] bbuf = new byte[1024];
			while ((n=fis.read(bbuf,0,bbuf.length)) > 0) svros.write(bbuf,0,n);
			svros.flush();
			svros.close();
			fis.close();
		}
		catch (IOException e) {
			System.out.println("Error sending the file:\n" + e.getMessage());
			return;
		}

		//Get the response and report it
		try { svrrdr = new BufferedReader(new InputStreamReader(conn.getInputStream())); }
		catch (IOException e) {
			System.out.println(
				"Unable to obtain an input stream to read the response:\n" + e.getMessage());
			return;
		}
		try {
			StringWriter svrsw = new StringWriter();
			int n;
			char[] cbuf = new char[1024];
			while ((n = svrrdr.read(cbuf,0,cbuf.length)) != -1) svrsw.write(cbuf,0,n);
			svrrdr.close();
			String response = svrsw.toString();

			//Try to make a nice response without knowing anything about the receiving application.
			String responseLC = response.toLowerCase();
			if (!forceDicomContentType) {
				//See if we got an html page back
				if (responseLC.indexOf("<html>") != -1) {
					//We did, see if it looks like a successful submit service response
					if (responseLC.indexOf("was received and unpacked successfully") != -1) {
						//It does, just display OK
						System.out.println("OK");
					}
					else if ((responseLC.indexOf("unsupported") != -1) ||
							 (responseLC.indexOf("failed") != -1) ||
							 (responseLC.indexOf("error") != -1)) {
						//This looks like an error
						System.out.println("Error received from the server:\n" + response);
					}
					else {
						//It's not clear what this is; return the whole text without comment.
						System.out.println(response);
					}
				}
				else {
					//It's not an html page; return the whole text without comment.
					System.out.println(response);
				}
			}
			//If it was a forced DICOM content type send, then look for "error"
			else if (responseLC.indexOf("error") != -1) {
				System.out.println("Error received from the server:\n" + response);
			}
			else System.out.println(response);
		}
		catch (IOException e) {
			System.out.println(
				"Unable to obtain the response:\n" + e.getMessage());
		}
	}

	//Send one file using DICOM.
	private void sendFileUsingDicom(File file, DicomURL dest) {
		System.out.println(
			"Sending " +file.getAbsolutePath() + " to " + dest.urlString);
		try {
			DicomSender dicomSender =
				new DicomSender(
					dest.host, dest.port, dest.calledAET, dest.callingAET);
			int result = dicomSender.send(file);
			if (result != 0) {
				System.out.println("DicomSend Error: [result=" + result + "]");
				return;
			}
		}
		catch (Exception e) {
			String message = "DicomSend Exception:";
			if (e.getMessage() != null) message += e.getMessage() + "\n";
			else message += " [no error message]\n";
			System.out.println(message);
			return;
		}
		System.out.println("OK");
	}

	//All-accepting X509 Trust Manager
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
	class AcceptAllHostnameVerifier implements HostnameVerifier {
		public boolean verify(String urlHost, SSLSession ssls) {
			return true;
		}
	}

	//Decode a DICOM URL into its component pieces and encapsulate them.
	class DicomURL {
		public String calledAET;
		public String callingAET;
		public String host;
		public int port;
		public String urlString;
		public DicomURL(String urlString) throws Exception {
			this.urlString = urlString;
			int k = urlString.indexOf("://") + 3;
			int kk = urlString.indexOf(":",k);
			if (kk == -1) throw new Exception("Missing separator [:] for AE Titles");
			calledAET = urlString.substring(k,kk).trim();
			k = ++kk;
			kk = urlString.indexOf("@",k);
			if (kk == -1) throw new Exception("Missing terminator [@] for CallingAET");
			callingAET = urlString.substring(k,kk).trim();
			k = ++kk;
			kk = urlString.indexOf(":",k);
			if (kk == -1) throw new Exception("Missing separator [:] for Host and Port");
			host = urlString.substring(k,kk).trim();
			k = ++kk;
			String portString = urlString.substring(k).trim();
			try { port = Integer.parseInt(portString); }
			catch (Exception e) { throw new Exception("Unparseable port number ["+portString+"]"); }
		}
	}

	//Some help text
	private static void printHelp() {
		System.out.println(
			"\nUsage:\n" +
			"------\n\n" +
			"java -jar programdir/fs.jar path/file url switches\n\n" +
			"where:\n" +
			"   path/file = the path to the file to be transmitted\n" +
			"   url = the URL of the destination:\n" +
			"      http://host:port/trial/import/doc\n" +
			"      https://host:port/trial/import/doc\n" +
			"      dicom://destinationAET:senderAET@host:port\n" +
			"   switches:\n" +
			"      -dicom (or just -d) forces the Content-Type\n\n" +
			"Notes:\n" +
			"   The senderAET is optional in a DICOM transmission.\n\n" +
			"   The -dicom switch only applies to the http or https protocols.\n" +
			"      It can be used to force the Content-Type when the file being\n" +
			"      transmitted is a DICOM file but the extension is not \".dcm\".\n" +
			"Example:\n" +
			"   java -jar /bin/FileSender/fs.jar /path/filename.dcm url -d\n"
		);

	}

}
