/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.io.File;
import java.util.Enumeration;
import java.util.Hashtable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * A class to encapsulate the mirc-passports.xml file.
 */
public class Passports {

	static String mircPassports = "mirc-passports.xml";

	Document mircPassportsXML;
	File dir;

	/**
	 * Construct a new Passports object from the
	 * Tomcat/conf/mirc-passports.xml file.
	 * @param file any file in the tree under Tomcat/webapps.
	 */
	public Passports(File file) {
		//Get a File object that is guaranteed to have an absolute
		//path so we can walk it backwards.
		dir = new File(file.getAbsolutePath());

		//Find the conf directory
		while (!dir.isDirectory() || !dir.getName().equals("webapps"))
			dir = dir.getParentFile();
		dir = new File(dir.getParentFile(),"conf");

		//Make sure that there is a mirc-passports.xml file.
		File mp = new File(dir,mircPassports);
		if (!mp.exists()) {
			try {
				Document passportsXML = XmlUtil.getDocument();
				Element root = passportsXML.createElement("passports");
				passportsXML.appendChild(root);
				FileUtil.setFileText(mp,XmlUtil.toString(passportsXML));
			}
			catch (Exception ignore) { }
		}
	}

	/**
	 * Get the Passport object for a specific user.
	 * @param theUsername the name of the user on this Tomcat instance.
	 */
	public Passport getPassport(String theUsername) {
		Element passport = getPassportElement(theUsername);
		if (passport == null) return null;
		return new Passport(passport);
	}

	/**
	 * Add a Passport for a specific user, or update the Passport if one
	 * already exists for the user.
	 * @param thePassport the Passport to add.
	 */
	public void addPassport(Passport thePassport) {
		//Do nothing if we can't get the mircPasswordsXML.
		if (getMircPassportsXML() == null) return;
		Element root = mircPassportsXML.getDocumentElement();
		try {
			//See if the user already has a Passport, and if so, remove it.
			Element passport = getPassportElement(mircPassportsXML, thePassport.username);
			if (passport != null) root.removeChild(passport);
			//Now append the new passport.
			passport = (Element)mircPassportsXML.importNode(thePassport.getXML(),true);
			root.appendChild(passport);
			File mp = new File(dir,mircPassports);
			FileUtil.setFileText(mp,XmlUtil.toString(mircPassportsXML));
		}
		catch (Exception ignore) { }
	}

	//Get the XML Element corresponding to a specific user's passport.
	private Element getPassportElement(String theUsername) {
		if (getMircPassportsXML() == null) return null;
		return getPassportElement(mircPassportsXML, theUsername);
	}

	private Element getPassportElement(
			Document passportsXML,
			String theUsername) {
		Element root = passportsXML.getDocumentElement();
		NodeList nodeList = root.getElementsByTagName("passport");
		for (int i=0; i<nodeList.getLength(); i++) {
			Element passport = (Element)nodeList.item(i);
			String username = passport.getAttribute("username");
			if (username.equals(theUsername)) return passport;
		}
		return null;
	}

	//Get mirc-passports.xml as a XML DOM Document.
	private Document getMircPassportsXML() {
		if (mircPassportsXML != null) return mircPassportsXML;
		mircPassportsXML = getDocument(new File(dir,mircPassports));
		return mircPassportsXML;
	}

	//Get an XML Document and return null if unable
	private Document getDocument(File file) {
		try { return XmlUtil.getDocument(file); }
		catch (Exception ex) { return null; }
	}

}

