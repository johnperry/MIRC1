/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.storageservice;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.rsna.mircsite.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
  * A class to access an author's name, affiliation, and contact information
  * so that an author doesn't have to enter it in every document he creates.
  */
public class Author {

	String filename = "";

	/** the author's username */
	public String username;
	/** the author's name as it is to appear in a document. */
	public String name;
	/** the author's affiliation as it is to appear in a document. */
	public String affiliation;
	/** the author's contact information as it is to appear in a document. */
	public String contact;

	/**
	 * Class constructor; creates an Author object to
	 * store the author's name, affiliation, and contact
	 * information.
	 * @param username the author's Tomcat username.
	 * @param filename the path to the authors.xml file
	 * containing all the information for authors known
	 * to this author service.
	 */
	public Author(String username, String filename) {
		this.username = username;
		this.filename = filename;
		getAuthorInfo();
	}

	/**
	 * Get the Object array of parameters for an XSL
	 * transformation for this author. The parameters
	 * supplied are:
	 * <ul>
	 * <li>username</li>
	 * <li>name</li>
	 * <li>affiliation</li>
	 * <li>contact</li>
	 * </ul>
	 * @return an array containing the parameters for this
	 * author.
	 */
	public Object[] getInfoParams() {
		return getInfoParams(new Object[] {});
	}

	/**
	 * Add this author's information to an Object array of
	 * parameters for an XSL transformation. The parameters
	 * added are:
	 * <ul>
	 * <li>username</li>
	 * <li>name</li>
	 * <li>affiliation</li>
	 * <li>contact</li>
	 * </ul>
	 * @param inParams the array which is to be extended with the
	 * author's information.
	 * @return the array containing the input parameters plus the
	 * parameters for this author.
	 */
	public Object[] getInfoParams(Object[] inParams) {
		String[] params = new String[] {
			"username",		username,
			"name",			name,
			"affiliation",	affiliation,
			"contact",		contact
		};
		if (inParams.length == 0) return params;
		Object[] combinedParams = new Object[params.length + inParams.length];
		int j = 0;
		for (int i=0; i<params.length; i++) combinedParams[j++] = params[i];
		for (int i=0; i<inParams.length; i++) combinedParams[j++] = inParams[i];
		return combinedParams;
	}

	//Get the name, affiliation, and contact information for this author
	//from the authors.xml file and populate the public fields.
	private void getAuthorInfo() {
		name = "";
		contact = "";
		affiliation = "";
		Element author = getAuthorElement();
		if (author == null) return;
		try {
			name = XmlUtil.getValueViaPath(author,"author/name").trim();
			affiliation = XmlUtil.getValueViaPath(author,"author/affiliation").trim();
			contact = XmlUtil.getValueViaPath(author,"author/contact").trim();
		}
		catch (Exception e) { }
	}

	/**
	 * Update the author's name, affiliation and contact information.
	 * @param name the author's name as it is to appear in a document.
	 * @param affiliation the author's affiliation as it is to appear in a document.
	 * @param contact the author's contact information as it is to appear in a document.
	 */
	public void setAuthorInfo(String name, String affiliation, String contact) {
		this.name = name.trim();
		this.affiliation = affiliation.trim();
		this.contact = contact.trim();
		setAuthorInfo();
	}

	//Update the authors.xml file with this author's information.
	public synchronized void setAuthorInfo() {
		String xml =
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<author user=\"" + username + "\">\n" +
			"  <name>" + name + "</name>\n" +
			"  <affiliation>" + affiliation + "</affiliation>\n" +
			"  <contact>" + contact + "</contact>\n" +
			"</author>";
		try {
			Document doc = XmlUtil.getDocumentFromString(xml);
			Element newAuthor = doc.getDocumentElement();
			Document authors = getAuthors();
			Element authorsRoot = authors.getDocumentElement();
			Element author = getAuthorElement(authorsRoot);
			newAuthor = (Element)authors.importNode(newAuthor,true);
			if (author != null) authorsRoot.replaceChild(newAuthor,author);
			else authorsRoot.appendChild(newAuthor);
			File file = new File(filename);
			FileUtil.setFileText(file, FileUtil.utf8, XmlUtil.toString(authors));
		}
		catch (Exception e) { }
	}

	//Get the authors.xml file and parse it.
	private synchronized Document getAuthors() {
		File file = new File(filename);
		//If the file doesn't exist, then create it
		if (!file.exists()) makeEmptyAuthorsFile(file);
		//Now parse it
		Document authors = null;
		try { authors = XmlUtil.getDocument(file); }
		catch (Exception e) { }
		return authors;
	}

	//Get the root element of the authors.xml file.
	private synchronized Element getAuthorsRoot() {
		Document authors = getAuthors();
		if (authors != null) return authors.getDocumentElement();
		return null;
	}

	//Get the author child element corresponding to this author.
	private synchronized Element getAuthorElement() {
		Element authorsRoot = getAuthorsRoot();
		return getAuthorElement(authorsRoot);
	}

	//Get the author child element corresponding to this author.
	private synchronized Element getAuthorElement(Element authorsRoot) {
		if (authorsRoot == null) return null;
		NodeList authorsList = authorsRoot.getElementsByTagName("author");
		Element author;
		for (int i=0; i<authorsList.getLength(); i++) {
			author = (Element)authorsList.item(i);
			if (author.getAttribute("user").equals(username)) return author;
		}
		return null;
	}

	//Create an empty authors.xml file
	private static void makeEmptyAuthorsFile(File file) {
		String content =
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<authors>\n" +
			"</authors>\n";
		FileUtil.setFileText(file, FileUtil.utf8, content);
	}

}
