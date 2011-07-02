/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.util.Enumeration;
import java.util.Hashtable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * A class to encapsulate the passport for one Tomcat user.
 */
public class Passport {

	public String username;
	public Hashtable<String,Visa> visas;

	/**
	 * Construct an empty Passport for a username.
	 * @param username the username for this Passport.
	 */
	public Passport(String username) {
		this.username = username;
		this.visas = new Hashtable<String,Visa>();
	}

	/**
	 * Construct a new Passport from an XML element.
	 * @param passport the passport element corresponding to a single
	 * user's Passport.
	 */
	public Passport(Element passport) {
		this.username = passport.getAttribute("username");
		this.visas = new Hashtable<String,Visa>();
		NodeList nodeList = passport.getElementsByTagName("visa");
		for (int i=0; i<nodeList.getLength(); i++) {
			Element v = (Element)nodeList.item(i);
			String url = v.getAttribute("url");
			String name = v.getAttribute("username");
			String password = v.getAttribute("password");
			Visa visa = new Visa(url,name,password);
			visas.put(visa.url,visa);
		}
	}

	/**
	 * Get the visa for a particular URL.
	 * @param url the URL of the destination system for which the
	 * username and password (the visa) is desired.
	 * @return the Visa object for this user on the destination URL.
	 */
	public Visa getVisa(String url) {
		return visas.get(Visa.getServerURL(url));
	}

	/**
	 * Add a Visa to this Passport.
	 * @param visa the Visa to add.
	 */
	public void addVisa(Visa visa) {
		visas.put(visa.url,visa);
	}

	/**
	 * Remove a Visa from this Passport.
	 * @param visa the Visa to remove.
	 */
	public void removeVisa(Visa visa) {
		visas.remove(visa.url);
	}

	/**
	 * Get the XML Element for this Passport.
	 * @return the XML Element containing all the information for this Passport.
	 */
	public Element getXML() {
		try {
			Document doc = XmlUtil.getDocument();
			Element root = doc.createElement("passport");
			root.setAttribute("username",username);
			Enumeration<String> urls = visas.keys();
			while (urls.hasMoreElements()) {
				Visa visa = visas.get(urls.nextElement());
				Element v = doc.createElement("visa");
				v.setAttribute("url",visa.url);
				v.setAttribute("username",visa.username);
				v.setAttribute("password",visa.password);
				root.appendChild(v);
			}
			return root;
		}
		catch (Exception ex) { return null; }
	}

}

