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

/**
 * HTML page offering the user the option to create
 * or update the roles for the admin user.
 */
public class AdminUserPage extends InstallerHtmlPage {

	/**
	 * Class constructor; create the HTML page with a table of
	 * the current users and roles and buttons for updating it.
	 */
	public AdminUserPage() {
		super();
		actionButton = new JButton("Create");
		actionButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { action(); }
		});
		JButton[] buttons = new JButton[] {actionButton};
		setButtons(buttons);
		id = "admin";
	}

	/**
	 * Determine whether all the roles currently defined in the web.xml
	 * files are possessed by at least one user. If so, skip this page.
	 * If not, create the page.
	 */
	public boolean activate() {
		usersXMLFile = new File(Installer.conf, "tomcat-users.xml");
		if ((usersXML = FileInstaller.getFileText(usersXMLFile)).equals("")) {
			this.setText(notOKPage());
			actionButton.setEnabled(false);
			return true;
		}
		//Change any user name attributes to username attributes to fix the changes
		//in the tomcat-users.xml file in Tomcat 5.5.
		usersXML = usersXML.replaceAll("<user\\s+name\\s*=\\s*\"","<user username=\"");
		//Now get the MIRC roles.
		roleEntities =Configurator.getRoleEntities(Installer.webapps);
		boolean found = true;
		for (int i=0; i<roleEntities.length; i++) {
			found &= searchForRole(usersXML, roleEntities[i]);
		}
		if (found) {
			System.out.println("an admin user already exists with all roles");
			return false;
		}
		else {
			table = getUserTable(usersXML);
			this.setText(nonexistingRolePage(table));
			actionButton.setEnabled(true);
		}
		return true;
	}

	//Create a table of the users and roles given a String
	//containing the contents of the tomcat-users.xml file.
	private String getUserTable(String s) {
		int i = -1;
		table = "<br><center><table border=1>\n<td><b>Username</b></td>"
									+ "<td><b>Roles</b></td></th>\n";
		String name, roles;
		while ((i=s.indexOf("<user",i+1)) != -1) {
			name = getAttribute(s,i,"username");
			roles = getAttribute(s,i,"roles");
			roles = highlight(roles);
			table += "<tr><td>" + name + "</td><td>" + roles + "</td></tr>\n";
		}
		table += "</table></center>";
		return table;
	}

	//Get an attribute value out of an xml string starting at an index point.
	private String getAttribute(String s, int i, String name) {
		int begin = s.indexOf(name + "=",i);
		if (begin < 0) return "";
		begin = s.indexOf("\"",begin) + 1;
		int end = s.indexOf("\"",begin);
		return s.substring(begin,end);
	}

	//Highlight the key roles.
	private String highlight(String s) {
		s = s.replaceAll(","," ").replaceAll("\\s+"," ").trim().replaceAll("\\s",", ");
		for (int i=0; i<roleEntities.length; i++)
			s = highlight(s,roleEntities[i]);
		return s;
	}

	//Highlight (in red) a role.
	private String highlight(String s, String r) {
		int k = -1;
		int kk;
		int rlen = r.length();
		String temp1;
		String temp2;
		while ((k < s.length()) && (k=s.indexOf(r, k+1)) >= 0) {
			kk = s.length() - rlen;
			if ( ((k==0) || (s.charAt(k-1) == ' ')) &&
			     ((k==kk) || (s.charAt(k+rlen) == ',')) ) {
				temp1 = s.substring(0,k) + "<font color=red>" + r + "</font>";
				temp2 = s.substring(k+rlen);
				k = temp1.length();
				s = temp1 + temp2;
			}
		}
		return s;
	}

	//Called when the user clicks the Create button.
	//This function allows the user to create a new user
	//or add all the roles to an existing user.
	private void action() {
		String name = JOptionPane.showInputDialog(
				null,
				"Enter a user name to be assigned the administrator roles.\n"
				+ "If the user already exists, the roles will be added \n"
				+ "to the user's other roles.\n"
				+ "If the user does not exist, it will be created.","username");
		if (name.equals("")) {
			this.setText(problemPage("No user name was supplied.",table));
			return;
		}
		String roles = getRoles(usersXML,name);
		if (roles == null) {
			//Create the user.
			String password = JOptionPane.showInputDialog(null,
					"The \"" + name + "\" user does not exist.\n"
				+	"Enter a password for the user.","password");
			String password2 = JOptionPane.showInputDialog(null,
					"Enter the password again, please.","password");
			if (password.equals(password2))
				usersXML = addUser(usersXML,name,password,"");
			else {
				this.setText(problemPage("The passwords did not match.",table));
				return;
			}
		}
		//Now update the roles for the user.
		for (int i=0; i<roleEntities.length; i++)
			usersXML = updateUser(usersXML,name,roleEntities[i]);
		table = getUserTable(usersXML);
		usersXML = usersXML.replaceAll("[cC][pP]1252", "utf-8");
		FileInstaller.setFileText(usersXMLFile,usersXML);
		setText(finishedPage(table));
	}

	//Add a user to the tomcat-users.xml text.
	private String addUser(String s, String name, String password, String roles) {
		int k = s.indexOf("</tomcat-users>");
		if (k < 0) return s;
		String user =
			"  <user username=\"" + name + "\" password=\"" + password +
					"\" roles=\"" + roles + "\"/>\n";
		return s.substring(0,k) + user + s.substring(k);
	}

	//Update a user in the tomcat-users.xml text, giving the user all the MIRC roles.
	private String updateUser(String s, String name, String role) {
		int k = -1;
		String username;
		String oldRoles, newRoles;
		while ((k=s.indexOf("<user",k+1)) != -1) {
			username = getAttribute(s,k,"username");
			if (username.equals(name)) {
				oldRoles = getAttribute(s,k,"roles");
				if ((","+oldRoles+",").replaceAll("\\s","").indexOf(","+role.trim()+",") < 0) {
					newRoles = oldRoles + "," + role;
					newRoles = newRoles.replaceAll("\\s","");
					k = s.indexOf("roles=",k);
					k = s.indexOf("\"",k) + 1;
					int kk = s.indexOf("\"",k);
					return s.substring(0,k) + newRoles + s.substring(kk);
				}
				else return s;
			}
		}
		return s;
	}

	//Get all the roles for a user.
	private String getRoles(String s, String name) {
		int k = -1;
		while ((k=s.indexOf("<user", k+1)) >= 0) {
			if (getAttribute(s,k,"username").equals(name)) return getAttribute(s,k,"roles");
		}
		return null;
	}

	//See if a user has a specific role.
	private boolean searchForRole(String s, String role) {
		int k = -1;
		role = "," + role.trim() + "," ;
		String roles;
		while ((k=s.indexOf("<user", k+1)) >= 0) {
			roles = "," + getAttribute(s,k,"roles") + ",";
			roles = roles.replaceAll("\\s","");
			if (roles.indexOf(role) >= 0) return true;
		}
		return false;
	}

	JButton	actionButton;
	String	usersXML;
	File	usersXMLFile;
	String	table;
	String[] roleEntities;

	//The various HTML pages.

	private String notOKPage() {
		String page = makeHeader();
		page += "<h3>Create Administrator User</h3>";
		page += "The installer could not find the tomcat-users.xml file.";
		page += "<p>This indicates a problem in either the installer or "
					+ "the Tomcat/conf/tomcat-users.xml file.</p>";
		page += "<p>You should just click <b>Next</b> and proceed.</p>";
		page += makeFooter();
		return page;
	}

	private String nonexistingRolePage(String table) {
		String page = makeHeader();
		page += "<h3>Create Administrator User</h3>";
		page += "The installer has determined that not all the MIRC administrator "
					+ "roles have been assigned to users. ";
		page += "Only users assigned the administrator roles can manage  "
					+ "the services on the MIRC site.";
		page += "<p>To create a new user or to add the administrator roles to an "
					+ "existing user, click <b>Create</b>.</p>";
		page += "<p>If you do not want to modify the list of users, click <b>Next</b>.</p>";
		page += table;
		page += makeFooter();
		return page;
	}

	private String problemPage(String result, String table) {
		String page = makeHeader();
		page += "<h3>Create Administrator User</h3>";
		page += result;
		page += "<p>If you want to try again, click <b>Create</b>.</p>";
		page += "<p>If you do not want to create a user, click <b>Next</b>.</p>";
		page += table;
		return page;
	}

	private String finishedPage(String table) {
		String page = makeHeader();
		page += "<h3>Create Administrator User</h3>";
		page += "The installer has updated the tomcat-users file.";
		page += "<p>Click <b>Next</b> to continue.</p>";
		page += table;
		page += makeFooter();
		return page;
	}

}

