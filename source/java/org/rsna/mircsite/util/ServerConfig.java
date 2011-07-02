/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.io.File;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A class to encapsulate the Tomcat configuration file (Tomcat/conf/server.xml).
 */
public class ServerConfig {

	File server;
	Document serverXML;
	Element root;

	/**
	 * Constructor: load the configuration file and parse it.
	 * @param file any file under the webapps tree.
	 */
	public ServerConfig(File file) {
		file = new File(file.getAbsolutePath());
		while (!file.isDirectory() || !file.getName().equals("webapps"))
			file = file.getParentFile();
		server = new File(file.getParentFile(),"conf");
		server = new File(server,"server.xml");
		try {
			serverXML = XmlUtil.getDocument(server);
			root = serverXML.getDocumentElement();
		}
		catch (Exception ex) { serverXML = null; }
	}

	/**
	 * Get the server configuration XML object.
	 * @return the mirc configuration XML DOM object.
	 */
	public Document getXML() {
		return serverXML;
	}

	/**
	 * Get the class name of the memory realm implementation.
	 * @return the fully qualified classname of the realm implementation
	 * on this site (for example, "org.rsna.tomcat.realm.SmartMemoryRealm").
	 * If the memory realm is not implemented, return the empty string.
	 */
	public String getMemoryRealmClassName() {
		if (serverXML != null) {
			Element realm;
			String className;
			NodeList nodeList = root.getElementsByTagName("Realm");
			for (int i=0; i<nodeList.getLength(); i++) {
				realm = (Element)nodeList.item(i);
				className = realm.getAttribute("className");
				if (className.indexOf("MemoryRealm") != -1) return className;
			}
		}
		return "";
	}

	/**
	 * Determine whether the site is running the SmartMemoryRealm.
	 * @return true if the memory realm class is org.rsna.tomcat.realm.SmartMemoryRealm;
	 * false otherwise.
	 */
	public boolean implementsSmartMemoryRealm() {
		return getMemoryRealmClassName().equals("org.rsna.tomcat.realm.SmartMemoryRealm");
	}

}

