/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.installer;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * A class for checking whether a Tomcat instance is running.
 */
public class TomcatChecker {

	int port;

	/**
	 * Class constructor; acquires the necessary information to connect
	 * to a Tomcat instance.
	 * @param server pointer to the Tomcat/conf/server.xml file
	 * @throws Exception
	 */
	public TomcatChecker(File server) throws Exception {
		port = getPort(server);
	}

	/**
	 * Try to make a connection.
	 * @return response from the connection.
	 * @throws Exception if the connection fails.
	 */
	public String connect() throws Exception {
		URL url = new URL("http://127.0.0.1:"+port);
		HttpURLConnection conn = (HttpURLConnection)url.openConnection();
		conn.setRequestMethod("GET");
		conn.connect();
		return getContent(conn);
	}

	//Get the text returned in the connection.
	String getContent(URLConnection conn) throws Exception {
		int length = conn.getContentLength();
		StringBuffer text = new StringBuffer();
		InputStream is = conn.getInputStream();
		InputStreamReader isr = new InputStreamReader(is);
		int size = 256;
		char[] buf = new char[size];
		int len;
		while ((len=isr.read(buf,0,size)) != -1) text.append(buf,0,len);
		return text.toString();
	}

	//Get the port on which the server is running.
	int getPort(File server) throws Exception {
		DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		Document doc = db.parse(server);
		Element root = doc.getDocumentElement();
		NodeList nodeList = root.getElementsByTagName("Connector");
		for (int i=0; i<nodeList.getLength(); i++) {
			Element connector = (Element)nodeList.item(i);
			if ((connector.getAttributeNode("secure") == null) &&
				(connector.getAttributeNode("secure") == null)) {
				return Integer.parseInt(connector.getAttribute("port"));
			}
		}
		throw new Exception("Unable to get the server port");
	}

}
