/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.exportmanager;

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
import javax.swing.event.*;
import javax.swing.SwingUtilities;
import org.apache.log4j.Logger;
import org.rsna.dicom.DicomSender;
import org.rsna.mircsite.log.Log;
import org.rsna.mircsite.util.FileObject;
import org.rsna.mircsite.util.FileUtil;
import org.rsna.mircsite.util.ProxyServer;
import org.rsna.util.TransferEvent;
import org.rsna.util.TransferListener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The Thread that exports DicomObjects via the HTTP, HTTPS, and DICOM
 * protocols.
 */
public class ExportService extends Thread {

	static final Logger logger = Logger.getLogger(ExportService.class);

	private TrustManager[] trustAllCerts;

	long nextTime;
	File exportDir;
	File expQuarDir;
	EventListenerList listenerList;
	Properties props;
	ProxyServer proxy;

	/**
	 * Class constructor; creates a new instance of the ExportService.
	 * @param props the application properties, containing the
	 * export enabled flag and the destination URL.
	 */
	public ExportService(Properties props, File exportDir, File expQuarDir) {
		this.props = props;
		this.exportDir = exportDir;
		this.expQuarDir = expQuarDir;
		exportDir.mkdirs();
		expQuarDir.mkdirs();
		proxy = new ProxyServer(props);
		listenerList = new EventListenerList();
		trustAllCerts = new TrustManager[] { new AcceptAllX509TrustManager() };
		try {
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null,trustAllCerts, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		}
		catch (Exception e) { }
		nextTime = 0;
	}

	/**
	 * Get the number of files in the exportDir.
	 * @return the number of files in the exportDir.
	 */
	public int getFileCount() {
		if (!exportDir.exists()) return 0;
		File[] files = exportDir.listFiles();
		return files.length;
	}

	/**
	 * Delete all the files in the export directory and the export quarantine.
	 */
	public void deleteAllFiles() {
		deleteAllFiles(exportDir);
		deleteAllFiles(expQuarDir);
	}

	private void deleteAllFiles(File dir) {
		if (!dir.exists()) return;
		File[] files = dir.listFiles();
		for (int i=0; i<files.length; i++)
			files[i].delete();
	}

	/**
	 * The Runnable implementation; starts the thread, polls the
	 * export queue directory and exports files when they appear.
	 */
	public void run() {
		Log.message("Export Service started");
		sendTransferEvent("Export Service started");
		while (!interrupted()) {
			try {
				processFiles();
				sleep(10000);
			}
			catch (Exception e) {
				Log.message("Export Service interrupted");
				sendTransferEvent("Export Service interrupted");
				return;
			}
		}
		Log.message("Export Service: Interrupt received");
		sendTransferEvent("Export Service: Interrupt received");
	}

	//Process all the files in the export directory.
	private void processFiles() {

		//Check that we aren't in the middle of a delay
		long currentTime = System.currentTimeMillis();
		if (currentTime < nextTime) return;

		//No delay; get the files
		File[] files = FileUtil.listSortedFiles(exportDir);

		String result;
		for (int k=0; k<files.length; k++) {
			//If somebody has cleared the queue,
			//just return and try again.
			File file = files[k];
			if (!file.exists()) return;

			//check the properties to see if anything has changed
			String expEnb = props.getProperty("export-enabled");
			boolean exportEnabled = !((expEnb != null) && expEnb.equals("false"));
			//bail out if export has been disabled
			if (!exportEnabled) return;

			//export is enabled; get the latest destination
			String url = (String)props.getProperty("destination");

			//Get the SOPInstanceUID from the object so we can provide a meaningful log entry.
			FileObject fileObject = FileObject.getObject(file);
			String sopiUID = fileObject.getSOPInstanceUID();

			//Transmit the file
			result = export(file,url);
			if (result.equals("OK")) {
				file.delete();
				Log.message(
					"<font color=\"green\">Export successful:<br>"
					+sopiUID+"<br>"
					+file.getName()+"</font>");
				logger.info("Export successful: "+sopiUID+"\n"+file.getName());
				sendTransferEvent("Export successful");
			}

			//Try to interpret error messages and log meaningful results
			else if (result.equals("FailedURLConnection")) {
				//wait 10 minutes and try again if there was no connection
				nextTime = currentTime + 600000;
				Log.message("<font color=\"red\">URL Connection failure to "+url+"</font>");
				sendTransferEvent("URLConnection failure");
				return;
			}
			else {
				//There was an actual problem; report it and then quarantine the object.
				if (result.startsWith("Server:")) {
					Log.message("<font color=\"red\">Export Service: Failure response from "
								+url+": "+result+"<br>"+file.getName()+"</font>");
					logger.warn("Failure response from "+url+": "+result+": "+file.getName());
					sendTransferEvent("Transmission failure");
				}
				else if (result.trim().equals("")) {
					Log.message("<font color=\"red\">No response from "+url
								+ "<br>" + file.getName()+"</font>");
					logger.warn("No response from "+url+": " + file.getName());
					sendTransferEvent("No response from destination");
				}
				else {
					Log.message("<font color=\"red\">Export failure: " + result
								+ "<br>" + sopiUID + "<br>" + file.getName()+"</font>");
					logger.warn("Export failure: " + result + ": " + sopiUID + "\n" + file.getName());
					sendTransferEvent("Export failure");
				}
				Log.message("<font color=\"red\">Object quarantined:<br>"+file.getName()+"</font>");
				logger.warn("Object quarantined: " + file.getName());
				sendTransferEvent("Object quarantined");
				File q = new File(expQuarDir,file.getName());
				if (q.exists()) q.delete();
				file.renameTo(q);
			}
			yield();
		}
	}

	//Export one file.
	private String export(File fileToExport, String urlString) {
		if (urlString.trim().toLowerCase().startsWith("dicom"))
			return dicomExport(fileToExport,urlString);
		else
			return httpExport(fileToExport,urlString);
	}

	//Export one file using the http protocol.
	private String httpExport(File fileToExport, String urlString) {
		int n;
		URL url;
		HttpURLConnection conn;
		OutputStream svros;
		FileInputStream fis;
		BufferedReader svrrdr;

		//If the proxy is enabled, set the Java System properties for it.
		//If it is not enabled, remove the Java System properties for it.
		if (proxy.getProxyEnabled()) proxy.setProxyProperties();
		else proxy.clearProxyProperties();

		try {
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
			else return "Illegal protocol: " + protocol;
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type","application/x-mirc");

			//If the proxy is enabled and authentication credentials
			//are available, set them in the request.
			if (proxy.getProxyEnabled() && proxy.authenticate()) {
				conn.setRequestProperty(
					"Proxy-Authorization",
					"Basic "+proxy.getEncodedProxyCredentials());
			}

			conn.connect();
			svros = conn.getOutputStream();
		}
		catch (Exception e) { return "FailedURLConnection"; }
		try { fis = new FileInputStream(fileToExport); }
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

	//Export one file using the DICOM protocol.
	private String dicomExport(File file, String urlString) {
		DicomURL dicomURL = null;
		try { dicomURL = new DicomURL(urlString); }
		catch (Exception e) { return "Illegal protocol: " + urlString; }
		try {
			DicomSender dicomSender =
				new DicomSender(
					dicomURL.host,
					dicomURL.port,
					dicomURL.calledAET,
					dicomURL.callingAET);
			int result = dicomSender.send(file);
			if (result != 0) return "DicomSend Error: " + result;
		}
		catch (Exception e) {
			return
				"DicomSend Exception: " +
				((e.getMessage() != null) ? e.getMessage() : "[no error message]");
		}
		return "OK";
	}

	class DicomURL {
		public String calledAET = "";
		public String callingAET = "";
		public String host = "";
		public int port = -1;
		public DicomURL(String urlString) throws Exception {
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

	//All-accepting X509 Trust Manager
	class AcceptAllX509TrustManager implements X509TrustManager {
		public X509Certificate[] getAcceptedIssuers() { return null; }
		public void checkClientTrusted(X509Certificate[] certs, String authType) { }
		public void checkServerTrusted(X509Certificate[] certs, String authType) { }
	}

	//All-verifying HostnameVerifier
	class AcceptAllHostnameVerifier implements HostnameVerifier {
		public boolean verify(String urlHost, SSLSession ssls) {
			return true;
		}
	}

	/**
	 * Add a TransferListener to the listener list.
	 * @param listener the TransferListener.
	 */
	public void addTransferListener(TransferListener listener) {
		listenerList.add(TransferListener.class, listener);
	}

	/**
	 * Remove a TransferListener from the listener list.
	 * @param listener the TransferListener.
	 */
	public void removeTransferListener(TransferListener listener) {
		listenerList.remove(TransferListener.class, listener);
	}

	//Send a message via a TransferEvent to all TransferListeners.
	private void sendTransferEvent(String message) {
		sendTransferEvent(this,message);
	}

	//Send a TransferEvent to all TransferListeners.
	private void sendTransferEvent(TransferEvent event) {
		sendTransferEvent(this,event.message);
	}

	//Send a TransferEvent to all TransferListeners.
	//The event is sent in the event thread to make it safe for
	//GUI components.
	private void sendTransferEvent(Object object, String message) {
		final TransferEvent event = new TransferEvent(object,message);
		final EventListener[] listeners = listenerList.getListeners(TransferListener.class);
		Runnable fireEvents = new Runnable() {
			public void run() {
				for (int i=0; i<listeners.length; i++) {
					((TransferListener)listeners[i]).attention(event);
				}
			}
		};
		SwingUtilities.invokeLater(fireEvents);
	}

}