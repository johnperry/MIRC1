/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.io.File;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A class to implement the TomcatUsers interface for managing
 * the Tomcat/conf/tomcat-users.xml file and the Tomcat/conf/mirc-groups.xml
 * file.
 */
public class TomcatUsersXmlFileImpl extends TomcatUsers {

	static final Logger logger = Logger.getLogger(TomcatUsersXmlFileImpl.class);

	static String tcUsers = "tomcat-users.xml";
	static String mircGroups = "mirc-groups.xml";

	File dir = null;
	Document tcUsersXML = null;
	Document mircGroupsXML = null;

	/**
	 * Constructor.
	 * @param file any file under the webapps directory.
	 */
	public TomcatUsersXmlFileImpl(File file) {
		//Get a File object that is guaranteed to have an absolute
		//path so we can walk it backwards.
		dir = new File(file.getAbsolutePath());

		//Find the conf directory
		while (!dir.isDirectory() || !dir.getName().equals("webapps"))
			dir = dir.getParentFile();
		dir = new File(dir.getParentFile(),"conf");

		//There has to be a tomcat-users.xml file.
		//Make sure that there is a mirc-groups.xml file.
		File mg = new File(dir,mircGroups);
		if (!mg.exists()) {
			try {
				Document groupsXML = XmlUtil.getDocument();
				Element mgRoot = groupsXML.createElement("mirc-groups");
				groupsXML.appendChild(mgRoot);
				FileUtil.setFileText(mg,XmlUtil.toString(groupsXML));
			}
			catch (Exception ignore) { }
		}
	}

	/**
	 * Get all the TomcatUser objects in a Hashtable indexed by username.
	 * @return the TomcatUser objects or null if unable to get them.
	 */
	public synchronized Hashtable<String,TomcatUser> getTomcatUsers() {
		if (getTomcatUsersXML() == null) return null;
		Hashtable<String,TomcatUser> hashtable = new Hashtable<String,TomcatUser>();
		Element root = tcUsersXML.getDocumentElement();
		NodeList nodeList = root.getElementsByTagName("user");
		for (int i=0; i<nodeList.getLength(); i++) {
			Element user = (Element)nodeList.item(i);
			String username = user.getAttribute("username");
			String password = user.getAttribute("password");
			String roles = user.getAttribute("roles");
			TomcatUser tcUser = new TomcatUser(username,password,roles);
			hashtable.put(username,tcUser);
		}
		return hashtable;
	}

	/**
	 * Get all the MircGroup objects in a Hashtable indexed by groupname.
	 * @return the MircGroup objects or null if unable to get them.
	 */
	public synchronized Hashtable<String,MircGroup> getMircGroups() {
		if (getMircGroupsXML() == null) return null;
		Hashtable<String,MircGroup> hashtable = new Hashtable<String,MircGroup>();
		Element root = mircGroupsXML.getDocumentElement();
		NodeList nodeList = root.getElementsByTagName("group");
		for (int i=0; i<nodeList.getLength(); i++) {
			Element group = (Element)nodeList.item(i);
			String groupname = group.getAttribute("groupname");
			String password = group.getAttribute("password");
			MircGroup mircGroup = new MircGroup(groupname,password);
			hashtable.put(groupname,mircGroup);
		}
		return hashtable;
	}

	/**
	 * Get all the role names in a Hashtable where the roles names are the keys.
	 * @return the Hashtable of role names or null if unable.
	 */
	public synchronized Hashtable<String,String> getRolesHashtable() {
		if (getTomcatUsersXML() == null) return null;
		Hashtable<String,String> hashtable = new Hashtable<String,String>();
		Element root = tcUsersXML.getDocumentElement();
		NodeList nodeList = root.getElementsByTagName("user");
		for (int i=0; i<nodeList.getLength(); i++) {
			Element user = (Element)nodeList.item(i);
			String roles = user.getAttribute("roles");
			String[] roleArray = roles.split(",");
			for (int k=0; k<roleArray.length; k++) {
				hashtable.put(roleArray[k].trim(),"");
			}
		}
		return hashtable;
	}

	/**
	 * Get all the role names in an alphabetized array.
	 * @return the array of role names or a zero-length array if unable.
	 */
	public synchronized String[] getRolesArray() {
		Hashtable<String,String> hashtable = getRolesHashtable();
		if (hashtable == null) return new String[0];
		String[] roleSet = new String[hashtable.size()];
		roleSet = hashtable.keySet().toArray(roleSet);
		Arrays.sort(roleSet);
		return roleSet;
	}

	/**
	 * Reset the database of Tomcat users.
	 * @param users the table of users to put in the database.
	 */
	public synchronized void resetUsers(
			Hashtable<String,TomcatUser> users) {
		try {
			Document usersXML = XmlUtil.getDocument();
			Element tcRoot = usersXML.createElement("tomcat-users");
			usersXML.appendChild(tcRoot);
			Hashtable<String,String> rolesTable = new Hashtable<String,String>();
			Enumeration<String> keys = users.keys();
			while (keys.hasMoreElements()) {
				String key = keys.nextElement();
				TomcatUser user = users.get(key);
				addTomcatUser(usersXML, rolesTable, user);
			}
			tcUsersXML = usersXML;
			FileUtil.setFileText(
				new File(dir,tcUsers),getTCUsersString(tcUsersXML));
		}
		catch (Exception oops) {
			logger.warn("Error resetting the users database.",oops);
		}
	}

	/**
	 * Reset the database of MIRC groups.
	 * @param groups the table of groups to put in the database.
	 */
	public synchronized void resetGroups(
			Hashtable<String,MircGroup> groups) {
		try {
			Document groupsXML = XmlUtil.getDocument();
			Element mgRoot = groupsXML.createElement("mirc-groups");
			groupsXML.appendChild(mgRoot);
			Enumeration<String> keys = groups.keys();
			while (keys.hasMoreElements()) {
				String key = keys.nextElement();
				MircGroup group = groups.get(key);
				addMircGroup(groupsXML, group);
			}
			mircGroupsXML = groupsXML;
			FileUtil.setFileText(
				new File(dir,mircGroups),XmlUtil.toString(mircGroupsXML));
		}
		catch (Exception oops) {
			logger.warn("Error resetting the groups database.",oops);
		}
	}

	/**
	 * Determine whether a specific role exists.
	 * @param theRolename the rolename to test.
	 * @return true if the role exists; false otherwise.
	 */
	public synchronized boolean hasRole(String theRolename) {
		if (getTomcatUsersXML() == null) return false;
		Element root = tcUsersXML.getDocumentElement();
		NodeList nodeList = root.getElementsByTagName("role");
		for (int i=0; i<nodeList.getLength(); i++) {
			Element role = (Element)nodeList.item(i);
			String rolename = role.getAttribute("rolename");
			if (rolename.equals(theRolename)) return true;
		}
		return false;
	}

	/**
	 * Determine whether a specific group exists.
	 * @return true if the group exists; false otherwise.
	 */
	public synchronized boolean hasGroup(String theGroupname) {
		return (getMircGroup(theGroupname) != null);
	}

	/**
	 * Get a specific Tomcat user.
	 * @return the user or null if unable.
	 */
	public synchronized TomcatUser getTomcatUser(String theUsername) {
		Element user = getUserElement(theUsername);
		if (user == null) return null;
		String password = user.getAttribute("password");
		String roles = user.getAttribute("roles");
		TomcatUser tcUser = new TomcatUser(theUsername,password,roles);
		return tcUser;
	}

	/**
	 * Get a specific MIRC group.
	 * @return the group or null if unable.
	 */
	public synchronized MircGroup getMircGroup(String theGroupname) {
		Element group = getGroupElement(theGroupname);
		if (group == null) return null;
		String password = group.getAttribute("password");
		MircGroup mircGroup = new MircGroup(theGroupname,password);
		return mircGroup;
	}

	/**
	 * Add a Tomcat user to the database or update the user if it exists.
	 */
	public synchronized void addTomcatUser(TomcatUser theUser) {
		//Do nothing if we can't load the users database.
		if (getTomcatUsersXML() == null) return;
		Hashtable<String,String> rolesTable = getRolesHashtable();
		addTomcatUser(tcUsersXML, rolesTable, theUser);
		//And save the users database
		FileUtil.setFileText(
			new File(dir,tcUsers),
			getTCUsersString(tcUsersXML));
	}

	/**
	 * Add a MIRC group to the database or update the group if it exists.
	 */
	public synchronized void addMircGroup(MircGroup theGroup) {
		//Do nothing if we can't load the groups database.
		if (getMircGroupsXML() == null) return;
		addMircGroup(mircGroupsXML, theGroup);
		//And save the groups database
		FileUtil.setFileText(
			new File(dir,mircGroups),
			XmlUtil.toString(mircGroupsXML));
	}

	//Get an XML Document and return null if unable
	private Document getDocument(File file) {
		try { return XmlUtil.getDocument(file); }
		catch (Exception ex) { return null; }
	}

	/**
	 * Get the TomcatUsers XML DOM document.
	 * @return the TomcatUsers XML DOM document or null if unable.
	 */
	public Document getTomcatUsersXML() {
		if (tcUsersXML != null) return tcUsersXML;
		tcUsersXML = getDocument(new File(dir,tcUsers));
		return tcUsersXML;
	}

	/**
	 * Get the MircGroups XML DOM document.
	 * @return the MircGroups XML DOM document or null if unable.
	 */
	public Document getMircGroupsXML() {
		if (mircGroupsXML != null) return mircGroupsXML;
		mircGroupsXML = getDocument(new File(dir,mircGroups));
		return mircGroupsXML;
	}

	//Get the XML Element corresponding to a specific username
	private Element getUserElement(String theUsername) {
		if (getTomcatUsersXML() == null) return null;
		return getUserElement(tcUsersXML, theUsername);
	}

	private Element getUserElement(
			Document usersXML,
			String theUsername) {
		Element root = usersXML.getDocumentElement();
		NodeList nodeList = root.getElementsByTagName("user");
		for (int i=0; i<nodeList.getLength(); i++) {
			Element user = (Element)nodeList.item(i);
			String username = user.getAttribute("username");
			if (username.equals(theUsername)) return user;
		}
		return null;
	}

	//Get the XML Element corresponding to a specific groupname
	private Element getGroupElement(String theGroupname) {
		if (getMircGroupsXML() == null) return null;
		return getGroupElement(mircGroupsXML, theGroupname);
	}

	private Element getGroupElement(
			Document groupsXML,
			String theGroupname) {
		Element root = groupsXML.getDocumentElement();
		NodeList nodeList = root.getElementsByTagName("group");
		for (int i=0; i<nodeList.getLength(); i++) {
			Element group = (Element)nodeList.item(i);
			String groupname = group.getAttribute("groupname");
			if (groupname.equals(theGroupname)) return group;
		}
		return null;
	}

	//Add a user into an XML document
	private void addTomcatUser(
			Document usersXML,
			Hashtable<String,String> rolesTable,
			TomcatUser theUser) {
		Element root = usersXML.getDocumentElement();
		Element user = getUserElement(usersXML, theUser.username);
		if (user == null) {
			//Create a new user
			user = usersXML.createElement("user");
			root.appendChild(user);
		}
		//Set the user's attributes
		user.setAttribute("username",theUser.username);
		user.setAttribute("password",theUser.password);
		user.setAttribute("roles",theUser.roles);
		//Make sure that all the roles are present
		String[] roles = theUser.roles.split(",");
		for (int i=0; i<roles.length; i++) {
			String rolename = roles[i].trim();
			if (rolesTable.get(rolename) == null) {
				//The role must be new, create it.
				Element role = usersXML.createElement("role");
				role.setAttribute("rolename",rolename);
				root.appendChild(role);
				//and update the hashtable
				rolesTable.put(rolename,"");
			}
		}
	}

	//Add a group into an XML document
	private void addMircGroup(
			Document groupsXML,
			MircGroup theGroup) {
		Element root = groupsXML.getDocumentElement();
		Element group = getGroupElement(groupsXML, theGroup.groupname);
		if (group == null) {
			//Create a new group
			group = groupsXML.createElement("group");
			root.appendChild(group);
		}
		//Set the group's attributes
		group.setAttribute("groupname",theGroup.groupname);
		group.setAttribute("password",theGroup.password);
	}

	private String getTCUsersString(Document tcUsersXML) {
		StringBuffer sb = new StringBuffer();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<tomcat-users>\n");
		Element root = tcUsersXML.getDocumentElement();
		NodeList nl = root.getElementsByTagName("role");
		addElements(sb,nl);
		nl = root.getElementsByTagName("user");
		addElements(sb,nl);
		sb.append("</tomcat-users>\n");
		return sb.toString();
	}

	private void addElements(StringBuffer sb, NodeList nl) {
		for (int i=0; i<nl.getLength(); i++) {
			Element el = (Element)nl.item(i);
			sb.append("  <" + el.getTagName());
			NamedNodeMap attributes = el.getAttributes();
			for (int k=0; k<attributes.getLength(); k++) {
				Node n = attributes.item(k);
				sb.append(" " + n.getNodeName() + "=\"" + n.getNodeValue() + "\"");
			}
			sb.append("/>\n");
		}
	}

}