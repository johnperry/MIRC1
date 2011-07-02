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
 * Installer page to enable the Tomcat SingleSignOnAuthenticator.
 */
public class TomcatSingleSignOnEnablePage extends InstallerHtmlPage {

	/**
	 * Class constructor; create an Enable button.
	 */
	public TomcatSingleSignOnEnablePage() {
		super();
		setButtons();
		id = "sso";
	}

	/**
	 * Determine whether the Tomcat SingleSignOnAuthenticator is enabled.
	 * If it is, skip this page. If not, display the page.
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
						"<p>A place to install the SingleSignOnAuthenticator " +
						"could not be found in the Tomcat/conf/server.xml file.</p>"));
				return true;
			}

			//Look for the single sign-on authenticator.
			Element engine = (Element)nodeList.item(0);
			nodeList = engine.getElementsByTagName("Valve");
			Element valve;
			for (int i=0; i<nodeList.getLength(); i++) {
				valve = (Element)nodeList.item(i);
				String className = valve.getAttribute("className");
				if (className.equals(authenticator)) {
					System.out.println("the single-signon authenticator is already enabled");
					return false;
				}
			}

			//We didn't find a single sign-on authenticator.
			//Look for the SmartMemoryRealm and put it right after that.
			nodeList = engine.getElementsByTagName("Realm");
			Element realm;
			for (int i=0; i<nodeList.getLength(); i++) {
				realm = (Element)nodeList.item(i);
				String className = realm.getAttribute("className");
				if (className.equals(smartRealm)) {
					valve = doc.createElement("Valve");
					valve.setAttribute("className",authenticator);
					valve.setAttribute("debug","0");
					Node next = realm.getNextSibling();
					Text space = doc.createTextNode("\n      ");
					engine.insertBefore(space,next);
					engine.insertBefore(valve,next);
					FileInstaller.setFileText(server,FileInstaller.toString(doc));
					setText(finishedPage());
					return true;
				}
			}

			//We didn't find the SmartMemoryRealm; display the error.
			setText(noRealmPage());
			return true;

		}
		catch (Exception ex) {
			this.setText(notOKPage(
				"<p>An error occurred while processing the Tomcat/conf/server.xml file.</p>"));
			return true;
		}
	}

	//Enable the Tomcat SingleSignOnAuthenticator and update the server.xml file.
	private void action() {
	}

	private String smartRealm = "org.rsna.tomcat.realm.SmartMemoryRealm";
	private String authenticator = "org.apache.catalina.authenticator.SingleSignOn";

	//The various HTML pages.

	private String notOKPage(String reason) {
		String page = makeHeader();
		page += "<h3>Enable Tomcat Single Sign On Authenticator</h3>";
		page += "The installer cannot enable the Tomcat Single Sign On Authenticator  "
					+ "for the following reason:";
		page += reason;
		page += "<p>You should just click <b>Next</b> and proceed.</p>";
		return page;
	}

	private String noRealmPage() {
		String page = makeHeader();
		page += "<h3>Enable Tomcat Single Sign On Authenticator</h3>";
		page += "The installer cannot enable the Tomcat Single Sign On Authenticator  "
					+ "because the Smart Memory Realm is not enabled";
		page += "<p>Click <b>Next</b>.</p>";
		return page;
	}

	private String finishedPage() {
		String page = makeHeader();
		page += "<h3>Enable Tomcat Single Sign On Authenticator</h3>";
		page += "The installer has enabled the authenticator.";
		page += "<p>Click <b>Next</b> to continue.</p>";
		return page;
	}

}

