/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.util.ArrayList;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * A class to encapsulate one Tomcat user.
 */
public class TomcatUser implements Comparable {

	public String username;
	public String password;
	public String roles;
	public boolean isUser;
	public boolean isAdmin;

	/**
	 * Construct a new TomcatUser.
	 */
	public TomcatUser(String username, String password, String roles) {
		this.username = username;
		this.password = password;
		this.roles = roles.replaceAll("\\s","");
		this.isUser = false;
		this.isAdmin = false;
	}

	/**
	 * Construct a new TomcatUser.
	 */
	public TomcatUser(TomcatUser user) {
		this.username = user.username;
		this.password = user.password;
		this.roles = user.roles;
	}

	/**
	 * Add a role to this user.
	 * @param role the role to add.
	 */
	public void addRole(String role) {
		if (!hasRole(role)) {
			if (!roles.equals("")) roles += ",";
			roles += role;
		}
	}

	/**
	 * Remove a role from this user.
	 * @param role the role to remove.
	 */
	public void removeRole(String role) {
		String s = "," + roles + ",";
		int k = s.indexOf("," + role + ",");
		if (k != -1) {
			s = s.substring(0,k) + s.substring(k+role.length()+1);
			if (s.startsWith(",")) s = s.substring(1);
			if (s.endsWith(",")) s = s.substring(0,s.length()-1);
			roles = s;
		}
	}

	/**
	 * Check whether this user has a specific role.
	 * @param role the role to check.
	 * @return true if the user has the role; false otherwise.
	 */
	public boolean hasRole(String role) {
		String s = "," + roles + ",";
		return (s.indexOf("," + role + ",") != -1);
	}

	/**
	 * Get the MIRC groups for this user.
	 * @return the array of group names to which the user belongs.
	 * This is the list of the user's roles that are also group names;
	 * thus, administrative roles are not included.
	 * @param tcUsers the TomcatUsers object which provides access to the MircGroups.
	 * @return the list of the MircGroups to which the user belongs.
	 */
	public String[] getMircGroupsForUser(TomcatUsers tcUsers) {
		String[] rolesArray = roles.split(",");
		ArrayList<String> groups = new ArrayList<String>();
		for (int i=0; i<rolesArray.length; i++) {
			if (tcUsers.hasGroup(rolesArray[i])) {
				groups.add(rolesArray[i]);
			}
		}
		String[] groupNames = new String[groups.size()];
		groupNames = groups.toArray(groupNames);
		return groupNames;
	}

	/**
	 * Compare this user to another user to determine the sorting order.
	 * @param otherUser the object representing the other user.
	 * @return the sorting order: -1 if this user precedes the other user;
	 * 0 if the names are identical; +1 if this user follows the other user.
	 */
	public int compareTo(Object otherUser) {
		return username.compareTo(((TomcatUser)otherUser).username);
	}

	/**
	 * Make a String for this user.
	 * @return the user's parameters.
	 */
	public String toString() {
		return username + ":" + password + ";" + roles;
	}

	/**
	 * Make an XML object for the user.
	 * @return an XML document containing the username, password, and role elements.
	 */
	public Document getUserXML() {
		try {
			Document doc = XmlUtil.getDocument();
			Element user = doc.createElement("user");
			doc.appendChild(user);
			user.setAttribute("username",username);
			user.setAttribute("password",password);
			String[] rolesArray = roles.split(",");
			for (int i=0; i<rolesArray.length; i++) {
				Element role = doc.createElement("role");
				role.appendChild(doc.createTextNode(rolesArray[i]));
				user.appendChild(role);
			}
			return doc;
		}
		catch (Exception ex) { return null; }
	}

}

