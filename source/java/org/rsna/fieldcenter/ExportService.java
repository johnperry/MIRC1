/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.fieldcenter;

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
import org.rsna.mircsite.util.ProxyServer;
import org.rsna.util.FileUtil;
import org.rsna.util.GeneralFileFilter;
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

	static final String serviceName = "ExportService";
	static final Logger logger = Logger.getLogger(ExportService.class);

	private TrustManager[] trustAllCerts;

	long nextTime;
	File exportFile;
	File quarantineFile;
	File transmittedlogFile;
	EventListenerList listenerList;
	Properties props;
	ProxyServer proxy;

	/**
	 * Class constructor; creates a new instance of the ExportService.
	 * @param props the application properties, containing the
	 * export enabled flag and the destination URL.
	 */
	public ExportService(Properties props) {
		this.props = props;
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
		exportFile = new File(FieldCenter.exportFilename);
		quarantineFile = new File(FieldCenter.exportQuarFilename);
		File objectlogFile = new File(FieldCenter.objectlogFilename);
		transmittedlogFile = new File(objectlogFile,"transmitted");
		transmittedlogFile.mkdirs();
	}

	/**
	 * The Runnable implementation; starts the thread, polls the
	 * export queue directory and exports files when they appear.
	 */
	public void run() {
		Log.message(serviceName+": Started");
		sendTransferEvent(serviceName+": Started");
		while (!interrupted()) {
			try {
				processFiles();
				sleep(10000);
			}
			catch (Exception e) {
				Log.message(serviceName+": Interrupted");
				sendTransferEvent(serviceName+": Interrupted");
				return;
			}
		}
		Log.message(serviceName+": Interrupt received");
		sendTransferEvent(serviceName+": Interrupt received");
	}

	//Process all the files in the export directory.
	private void processFiles() {
		GeneralFileFilter filter = new GeneralFileFilter();
		filter.setExtensions("*");
		filter.setMaxCount(100);
		File[] files = exportFile.listFiles(filter);
		String result;
		long currentTime;
		for (int k=0; (k<files.length) && ((currentTime = System.currentTimeMillis()) > nextTime); k++) {
			File file = files[k];
			//check the properties to see if anything has changed
			String expEnb = props.getProperty("export-enabled");
			boolean exportEnabled = !((expEnb != null) && expEnb.equals("false"));
			//bail out if export has been disabled
			if (!exportEnabled) return;
			//export is enabled; get the latest destination
			String url = (String)props.getProperty("destination");
			result = export(file,url);
			if (result.equals("OK")) {
				saveObject(file);
				Log.message("<font color=\"green\">"+serviceName+
							": Export successful:<br>"+file.getName()+"</font>");
				logger.info(serviceName+": Export successful: "+file.getName());
				sendTransferEvent("Export successful");
			}
			else if (result.equals("FailedURLConnection") ||
					 result.startsWith("CommunicationFailure") ||
					 result.trim().equals("")) {
				//wait 10 minutes and try again
				nextTime = currentTime + 600000;
				Log.message("<font color=\"red\">"+serviceName+": URL Connection failure to "+url);
				sendTransferEvent("URLConnection failure");
			}
			else {
				if (result.startsWith("Server:")) {
					Log.message("<font color=\"red\">"+serviceName+": Failure response from "
								+url+": "+result+"<br>"+file.getName()+"</font>");
					logger.warn(serviceName+": Failure response from "+url+": "+result+": "+file.getName());
					sendTransferEvent("Transmission failure");
				}
				else {
					Log.message("<font color=\"red\">"+serviceName+": Export failure: "
								+ result + "<br>" + file.getName()+"</font>");
					logger.warn(serviceName+": Export failure: " + result + ": " + file.getName());
					sendTransferEvent("Export failure");
				}
				Log.message("<font color=\"red\">"+serviceName+": Object quarantined: "
							+ file.getName()+"</font>");
				logger.warn("Object quarantined: " + file.getName());
				sendTransferEvent("Object quarantined");
				File q = new File(quarantineFile,file.getName());
				if (q.exists()) q.delete();
				file.renameTo(q);
			}
			yield();
		}
	}

	//Save an object in the transmitted log directory.
	private void saveObject(File file) {
		String enb = props.getProperty("save-transmitted-objects");
		if ((enb != null) && enb.equals("true")) {
			File destination = new File(transmittedlogFile,file.getName());
			file.renameTo(destination);
		}
		if (file.exists()) file.delete();
	}

	//Export one file.
	private String export(File fileToExport, String urlString) {
		if (urlString.trim().toLowerCase().startsWith("dicom"))
			return dicomExport(fileToExport,urlString);
		else
			return httpExport(fileToExport,urlString);
	}

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
		try {
			//Send the file to the server
			fis = new FileInputStream(fileToExport);
			byte[] bbuf = new byte[1024];
			while ((n=fis.read(bbuf,0,bbuf.length)) > 0) svros.write(bbuf,0,n);
			svros.flush();
			svros.close();
			fis.close();
			InputStream is;
			//Get the response
			is = conn.getInputStream();
			InputStreamReader isr = new InputStreamReader(is);
			svrrdr = new BufferedReader(isr);
			StringWriter svrsw = new StringWriter();
			char[] cbuf = new char[1024];
			while ((n = svrrdr.read(cbuf,0,cbuf.length)) != -1) svrsw.write(cbuf,0,n);
			svrrdr.close();
			return svrsw.toString();
		}
		catch (IOException e) { return "CommunicationFailure: "+e.getMessage(); }
	}

	//Export one file using DICOM.
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