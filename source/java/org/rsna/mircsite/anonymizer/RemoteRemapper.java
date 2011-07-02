/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.anonymizer;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.security.cert.X509Certificate;
import java.security.SecureRandom;
import java.util.Hashtable;
import java.util.Properties;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.log4j.Logger;
import org.rsna.mircsite.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

/**
 * The MIRC remote remapper.
 * This class supports collects requests for remapping PHI and calls the
 * central remapper servlet at the principal investigator MIRC site to
 * obtain replacement values.
 */
public class RemoteRemapper implements Remapper {

	static final Logger logger = Logger.getLogger(RemoteRemapper.class);

	ProxyServer proxy;
	TrustManager[] trustAllCerts;

	String centralRemapperURL;
	int count;

	Document request;
	Element root;

	/**
	 * The constructor, which initializes a Remapper for central remapping.
	 * @param centralRemapperURL the URL of the central remapper.
	 */
	public RemoteRemapper (
				String centralRemapperURL,
				Properties proxyProperties) throws Exception {

		this.centralRemapperURL = centralRemapperURL;

		//If the proxy is enabled, set the Java System properties for it.
		//If it is not enabled, remove the Java System properties for it.
		this.proxy = new ProxyServer(proxyProperties);
		if (proxy.getProxyEnabled())
			proxy.setProxyProperties();
		else
			proxy.clearProxyProperties();

		try {
			//Set up for SSL
			this.trustAllCerts = new TrustManager[] { new AcceptAllX509TrustManager() };
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null,trustAllCerts, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			//Initialize the request Document.
			clear();
		}
		catch (Exception ex) {
			logger.error("",ex);
			throw ex;
		}
	}

	/**
	 * Reinitialize the remapper, clearing all accumulated requests.
	 */
	public void clear() throws Exception {
		count = 0;
		DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		request = db.newDocument();
		root = request.createElement("remap");
		request.appendChild(root);
	}

	/**
	 * Get the original date corresponding to a remapped value for a patient and element.
	 * @param seqid an identifier for the remapping request for this value; used
	 * as the key in the results hashtable, so it must be unique.
	 * @param siteid the site ID for the site on which the patient ID was created.
	 * For trials using globally created patient IDs, all the site IDs can be set to
	 * the empty string ("").
	 * @param ptid the original patient ID.
	 * @param tag the DICOM element tag string (gggg,eeee).
	 * @param date the remapped date.
	 * @param base the base date used as the starting date for offsetting dates.
	 */
	public void getOriginalDate(
					int seqid, String siteid, String ptid,
					String tag, String date, String base) {
		Element e = createRequestElement(root,"getOriginalDate",seqid);
		putParam(e,"siteid",siteid);
		putParam(e,"ptid",ptid);
		putParam(e,"tag",tag);
		putParam(e,"date",date);
		putParam(e,"base",base);
	}

	/**
	 * Request a replacement for a date.
	 * @param seqid an identifier for the remapping request for this value; used
	 * as the key in the results hashtable, so it must be unique.
	 * @param siteid the site ID for the site on which the patient ID was created.
	 * For trials using globally created patient IDs, all the site IDs can be set to
	 * the empty string ("").
	 * @param ptid the patient ID.
	 * @param tag the DICOM element tag string (gggg,eeee).
	 * @param date the date value of the element.
	 * @param base the base date to use as the starting date for offsetting dates.
	 */
	public void getOffsetDate(int seqid, String siteid, String ptid,
								String tag, String date, String base) {
		Element e = createRequestElement(root,"getOffsetDate",seqid);
		putParam(e,"siteid",siteid);
		putParam(e,"ptid",ptid);
		putParam(e,"tag",tag);
		putParam(e,"date",date);
		putParam(e,"base",base);
	}

	/**
	 * Request a replacement for a DICOM ID element.
	 * @param seqid an identifier for the remapping request for this value; used
	 * as the key in the results hashtable, so it must be unique.
	 * @param tag the DICOM element tag string (gggg,eeee).
	 * @param gid the ID value to be replaced.
	 */
	public void getGenericID(int seqid, String tag, String gid) {
		Element e = createRequestElement(root,"getGenericID",seqid);
		putParam(e,"tag",tag);
		putParam(e,"gid",gid);
	}

	/**
	 * Request an accession number.
	 * @param seqid an identifier for the remapping request for this value; used
	 * as the key in the results hashtable, so it must be unique.
	 * @param tag the DICOM element tag string (gggg,eeee).
	 * @param gid the value to be replaced.
	 */
	public void getAccessionNumber(int seqid, String tag, String gid) {
		Element e = createRequestElement(root,"getAccessionNumber",seqid);
		putParam(e,"tag",tag);
		putParam(e,"gid",gid);
	}

	/**
	 * Request an integer that has not been returned before.
	 */
	public void getInteger(int seqid) {
		Element e = createRequestElement(root,"getInteger",seqid);
	}

	/**
	 * Request a replacement for a UID.
	 * @param seqid an identifier for the remapping request for this value; used
	 * as the key in the results hashtable, so it must be unique.
	 * @param prefix the UID root to serve as a prefix for the replacement.
	 * @param uid the UID to be replaced.
	 */
	public void getUID(int seqid, String prefix, String uid) {
		Element e = createRequestElement(root,"getUID",seqid);
		//Note: In central remapping, the local UID root is not used.
		putParam(e,"uid",uid);
	}

	/**
	 * Request a new UID.
	 * @param seqid an identifier for the remapping request; used
	 * as the key in the results hashtable, so it must be unique.
	 * @param prefix the UID root to serve as a prefix for the replacement.
	 */
	public void getUID(int seqid, String prefix) {
		Element e = createRequestElement(root,"getUID",seqid);
		//Note: In central remapping, the local UID root is not used,
		//so in this case, there is no parameter put in the request.
	}

	/**
	 * Get the original UID associated with a remapped UID.
	 * @param seqid an identifier for the  request; used
	 * as the key in the results hashtable, so it must be unique.
	 * @param uid the original UID.
	 */
	public void getOriginalUID(int seqid, String uid) {
		Element e = createRequestElement(root,"getOriginalUID",seqid);
		putParam(e,"uid",uid);
	}

	/**
	 * Get the remapped UID associated with an original UID.
	 * @param seqid an identifier for the  request; used
	 * as the key in the results hashtable, so it must be unique.
	 * @param uid the remapped UID.
	 */
	public void getRemappedUID(int seqid, String uid) {
		Element e = createRequestElement(root,"getRemappedUID",seqid);
		putParam(e,"uid",uid);
	}

	/**
	 * Request a replacement for a DICOM Patient ID.
	 * @param seqid an identifier for the remapping request for this value; used
	 * as the key in the results hashtable, so it must be unique.
	 * @param siteid the site ID for the site on which the patient ID was created.
	 * For trials using globally created patient IDs, all the site IDs can be set to
	 * the empty string ("").
	 * @param ptid the patient ID.
	 * @param prefix the prefix of the new Patient ID.
	 * @param first the starting value of the sequentially increasing integer
	 * used to generate IDs.
	 * @param width the minimum width of the integer part of the Patient ID, with
	 * leading zeroes supplied if the integer does not require the full field width.
	 * @param suffix the suffix of the new Patient ID.
	 */
	public void getPtID(int seqid, String siteid, String ptid,
						String prefix, int first, int width, String suffix) {
		Element e = createRequestElement(root,"getPtID",seqid);
		putParam(e,"siteid",siteid);
		putParam(e,"ptid",ptid);
		putParam(e,"prefix",prefix);
		putParam(e,"first",Integer.toString(first));
		putParam(e,"width",Integer.toString(width));
		putParam(e,"suffix",suffix);
	}

	/**
	 * Get the current number of values for which remapping requests have been received.
	 * @return the number of remapping requests.
	 */
	public int getCount() {
		return count;
	}

	/**
	 * Get the results of the remapping request.
	 * @return Hashtable containing the remapped values, indexed by the seqid values
	 * in the requests, or null if an error occurred in the remapping request.
	 * @throws Exception if unable to contact the server.
	 */
	public Hashtable getRemappedValues() throws Exception {
		Document response = getResponseDocument(centralRemapperURL, request);
		if (response == null) return null;
		Hashtable<String,String> responseTable = new Hashtable<String,String>();
		Element responseRoot = response.getDocumentElement();
		Node child = responseRoot.getFirstChild();
		while (child != null) {
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				String id = ((Element)child).getAttribute("id");
				String value = getTextValue(child);
				responseTable.put(id,value);
			}
			child = child.getNextSibling();
		}
		return responseTable;
	}

	private Element createRequestElement(Element requestRoot, String tagName, int id) {
		Element e = requestRoot.getOwnerDocument().createElement(tagName);
		e.setAttribute("id",Integer.toString(id));
		requestRoot.appendChild(e);
		count++;
		return e;
	}

	private void putParam(Element e, String paramName, String value) {
		Element child = e.getOwnerDocument().createElement(paramName);
		Text text = child.getOwnerDocument().createTextNode(value);
		child.appendChild(text);
		e.appendChild(child);
	}

	private String getTextValue(Node node) {
		StringBuffer value = new StringBuffer();
		Node child = node.getFirstChild();
		while (child != null) {
			if (child.getNodeType() == Node.TEXT_NODE) {
				value.append(child.getNodeValue());
			}
			child = child.getNextSibling();
		}
		return value.toString();
	}

	//Get the response as an XML Document.
	//Return null if the document doesn't parse.
	//Throw an exception if unable to get the string from the server.
	private Document getResponseDocument(String url, Document request) throws Exception {
		String responseString = getResponseString(url, XmlUtil.toString(request));
		try { return XmlUtil.getDocumentFromString(responseString); }
		catch (Exception ex) { }
		return null;
	}

	private String getResponseString(String urlString, String request) throws Exception {
		URL url;
		HttpURLConnection conn = null;
		OutputStreamWriter writer = null;
		BufferedReader reader = null;

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
			else conn = (HttpURLConnection)url.openConnection();

			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type","text/xml");

			//If the proxy is enabled and authentication credentials
			//are available, set them in the request.
			if (proxy.getProxyEnabled() && proxy.authenticate()) {
				conn.setRequestProperty(
					"Proxy-Authorization",
					"Basic "+proxy.getEncodedProxyCredentials());
			}

			//Make the connection and send the request string encoded as UTF-8.
			conn.connect();
			Charset utf8 = Charset.forName("UTF-8");
			writer = new OutputStreamWriter(conn.getOutputStream(),utf8);
			writer.write(request);
			writer.flush();
			writer.close();

			//Get the response, decoding as UTF-8.
			reader =
				new BufferedReader(
					new InputStreamReader(conn.getInputStream(),utf8));
			StringWriter sw = new StringWriter();
			int n;
			char[] cbuf = new char[1024];
			while ((n = reader.read(cbuf,0,cbuf.length)) != -1) sw.write(cbuf,0,n);
			reader.close();
			return sw.toString();
		}
		catch (Exception ex) {
			if (reader != null) {
				try { reader.close(); }
				catch (Exception ignore) { }
			}
			if (writer != null) {
				try { writer.close(); }
				catch (Exception ignore) { }
			}
			throw ex;
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

}
