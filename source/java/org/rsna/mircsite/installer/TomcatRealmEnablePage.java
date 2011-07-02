/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.installer;

import java.io.*;
import javax.swing.*;
import java.awt.event.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

/**
 * Installer page to enable the memory realm. This realm is
 * currently required to provide authentication of Tomcat users.
 */
public class TomcatRealmEnablePage extends InstallerHtmlPage {

	/**
	 * Class constructor; create an Enable button.
	 */
	public TomcatRealmEnablePage() {
		super();
		setButtons();
		id = "realm";
	}

	/**
	 * Determine whether the Smart Memory Realm is already enabled. If so, skip
	 * this page. If not, enable it and display the result.
	 */
	public boolean activate() {
		File server = new File(Installer.conf, "server.xml");
		try {
			DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = db.parse(server);
			Element root = doc.getDocumentElement();
			NodeList nodeList = root.getElementsByTagName("Engine");
			if (nodeList.getLength() == 0) {
				this.setText(notOKPage(
						"<p>A place to install the Smart Memory Realm " +
						"could not be found in the Tomcat/conf/server.xml file.</p>"));
				return true;
			}

			//Look for either a smart or dumb realm.
			Element engine = (Element)nodeList.item(0);
			nodeList = engine.getElementsByTagName("Realm");
			Element realm;
			Element dumbRealm = null;
			for (int i=0; i<nodeList.getLength(); i++) {
				realm = (Element)nodeList.item(i);
				String className = realm.getAttribute("className");
				if (className.equals(smart)) {
					System.out.println("the smartmemoryrealm is already installed");
					return false;
				}
				else if (className.equals(dumb)) dumbRealm = realm;
			}

			//We didn't find a smart memory realm.
			//If there was a dumb one, modify it; otherwise,
			//create a new one and put it right before the Host.
			realm = dumbRealm;
			if (realm == null) {
				nodeList = engine.getElementsByTagName("Host");
				if (nodeList.getLength() == 0) {
					this.setText(notOKPage(
							"<p>A place to install the Smart Memory Realm " +
							"could not be found in the Tomcat/conf/server.xml file.</p>"));
					return true;
				}
				Element host = (Element)nodeList.item(0);

				realm = doc.createElement("Realm");
				engine.insertBefore(realm,host);
				Text space = doc.createTextNode("\n      ");
				engine.insertBefore(space,host);
			}
			realm.setAttribute("className",smart);
			boolean result = FileInstaller.setFileText(server,FileInstaller.toString(doc));
			setText(finishedPage());
			return true;
		}
		catch (Exception ex) {
			this.setText(notOKPage(
				"<p>An error occurred while processing the Tomcat/conf/server.xml file.</p>"));
			return true;
		}
	}

	private String dumb = "org.apache.catalina.realm.MemoryRealm";
	private String smart = "org.rsna.tomcat.realm.SmartMemoryRealm";

	//The various HTML pages.

	private String notOKPage(String reason) {
		String page = makeHeader();
		page += "<h3>Enable Tomcat Smart Memory Realm</h3>";
		page += "The installer cannot enable the Tomcat Smart Memory Realm  "
					+ "for the following reason:";
		page += reason;
		page += "<p>You should just click <b>Next</b> and proceed, "
					+ "but you should contact the MIRC project engineers "
					+ "when you are done with the installation to figure "
					+ "what went wrong.</p>";
		return page;
	}

	private String finishedPage() {
		String page = makeHeader();
		page += "<h3>Enable Tomcat Smart Memory Realm</h3>";
		page += "The installer has enabled the Smart Memory Realm.";
		page += "<p>Click <b>Next</b> to continue.</p>";
		return page;
	}

}

