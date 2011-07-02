/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.mircadmin;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.apache.log4j.Logger;
import org.rsna.mircsite.util.FileUtil;
import org.rsna.mircsite.util.HtmlUtil;
import org.rsna.mircsite.util.ServletUtil;
import org.rsna.mircsite.util.MircConfig;
import org.rsna.mircsite.util.MircGroup;
import org.rsna.mircsite.util.MyRsnaUser;
import org.rsna.mircsite.util.MyRsnaUsers;
import org.rsna.mircsite.util.Passport;
import org.rsna.mircsite.util.Passports;
import org.rsna.mircsite.util.TomcatUser;
import org.rsna.mircsite.util.TomcatUsers;
import org.rsna.mircsite.util.Visa;
import org.rsna.mircsite.util.XmlStringUtil;
import org.rsna.mircsite.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The User Account Manager servlet.
 * <p>
 * This servlet provides a browser-accessible user interface allowing
 * a user to change his account parameters in the Tomcat/conf/tomcat-users.xml
 * file used by the memory realm for managing users and roles. This servlet will
 * be replaced when the database realm is implemented.
 * <p>
 * This servlet responds to both HTTP GET and POST.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class UserAccountManager extends HttpServlet {

	static final Logger logger = Logger.getLogger(UserAccountManager.class);

	/**
	 * The servlet method that responds to an HTTP GET.
	 * <p>
	 * This method returns an HTML page containing a form for
	 * adding, removing, and changing users, roles and their
	 * relationships. The initial contents of the form are
	 * constructed from the contents of the Tomcat/conf/tomcat-users.xml
	 * file.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doGet(
				HttpServletRequest req,
				HttpServletResponse res
				) throws ServletException {
		loadPage(req, res, "");
	}

	/**
	 * The servlet method that responds to an HTTP POST.
	 * <p>
	 * This method interprets the posted parameters as a new set
	 * of users and roles and constructs a new Tomcat/conf/tomcat-users.xml
	 * file. It then returns an HTML page containing a new form
	 * constructed from the new contents of the file.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doPost(
				HttpServletRequest req,
				HttpServletResponse res
				) throws ServletException {

		//Force the encoding for the input.
		try { req.setCharacterEncoding("UTF-8"); }
		catch (Exception ignore) { }

		String message = "";

		try {
			File dir = new File(getServletContext().getRealPath("/"));
			TomcatUsers tcUsers = TomcatUsers.getInstance(dir);
			String username = req.getRemoteUser();
			MircConfig mc = MircConfig.getInstance(dir);

			if ((username == null) && mc.getAcctCreationEnabled()) {
				//Get the new account parameters from the form.
				String newusername = req.getParameter("newusername");
				username = (username != null) ? username.replaceAll("\\s+","") : "";
				String newpassword1 = req.getParameter("newpassword1");
				newpassword1 = (newpassword1 != null) ? newpassword1.trim() : "";
				String newpassword2 = req.getParameter("newpassword2");
				newpassword2 = (newpassword2 != null) ? newpassword2.trim() : "";

				//Confirm the new account parameters
				if (!newusername.equals("") && !newpassword1.equals("")) {
					if (!newpassword1.equals(newpassword2)) {
						message = "The passwords did not match.|The account was not created.";
						loadPage(req, res, message);
						return;
					}

					//Make sure that we don't overwrite an existing account.
					TomcatUser tcUser = tcUsers.getTomcatUser(newusername);
					if (tcUser != null) {
						message = "A user account for the name \""+newusername+"\" "
								+ "already exists.|"
								+ "The request was not processed.";
						loadPage(req, res, message);
						return;
					}
					String defaultRoles = mc.getDefaultRoles();
					tcUser = new TomcatUser(newusername,newpassword1,defaultRoles);
					tcUsers.addTomcatUser(tcUser);
					message = "A new account was created for \""+newusername+"\".|"
							+ "To use the account, you must close this window and|"
							+ "log in on the query page. New accounts take about a minute|"
							+ "to become active.";
					loadPage(req, res, message);
					return;
				}
				//If there were no new account parameters, do nothing.
				loadPage(req, res, "");
				return;
			}

			//If we get here, the user is authenticated.
			//First see if the user is changing his password.
			//Get the passwords from the form.
			String password1 = req.getParameter("password1").trim();
			String password2 = req.getParameter("password2").trim();
			//Confirm the passwords
			if (!password1.equals("")) {
				if (!password1.equals(password2)) {
					message = "The passwords did not match.|"
							+ "The account for user \""+username+"\" was not modified.";
					loadPage(req, res, message);
					return;
				}
				//Update the account
				TomcatUser tcUser = tcUsers.getTomcatUser(username);
				tcUser.password = password1;
				tcUsers.addTomcatUser(tcUser);
				message += "The password for \""+username+"\" was updated.|";
			}

			//Now update the user's passport.
			//First see if the user already has a passport.
			Passports passports = new Passports(dir);
			Passport passport = passports.getPassport(username);
			if (passport == null) passport = new Passport(username);
			int k = 1;
			String ppurl;
			boolean change = false;
			while ((ppurl = req.getParameter("ppurl"+k)) != null) {
				String ppname = req.getParameter("ppname"+k);
				ppname = (ppname != null) ? ppname.trim() : "";
				String ppword = req.getParameter("ppword"+k);
				ppword = (ppword != null) ? ppword.trim() : "";

				//Fix the home visa password if
				//the user is changing his password.
				if ((k==1) && !password1.equals("")) ppword = password1;

				//Get the visa for this URL
				Visa visa = passport.getVisa(ppurl);

				//If the name and password are both non-blank,
				//create a visa or update the current one.
				if (!ppname.equals("") && !ppword.equals("")) {
					if (visa == null) {
						visa = new Visa(ppurl, ppname, ppword);
						passport.addVisa(visa);
						message += "The visa for \""+ppurl+"\" was created.|";
						change = true;
					}
					else if (!ppname.equals(visa.username) || !ppword.equals(visa.password)) {
						visa.username = ppname;
						visa.password = ppword;
						passport.addVisa(visa);
						message += "The visa for \""+ppurl+"\" was updated.|";
						change = true;
					}
				}
				else if (visa != null) {
					//The name or password is blank; remove the visa.
					passport.removeVisa(visa);
					message += "The visa for \""+ppurl+"\" was removed.|";
					change = true;
				}
				k++;
			}
			if (change) passports.addPassport(passport);

			//Next see if he wants to resign from any groups.
			TomcatUser tcUser = tcUsers.getTomcatUser(username);
			Enumeration params = req.getParameterNames();
			boolean changed = false;
			while (params.hasMoreElements()) {
				String param = (String)params.nextElement();
				if (param.startsWith("resign-")) {
					String role = param.substring(7);
					tcUser.removeRole(role);
					changed = true;
					message +=
						"User \""+username+"\" resigned from the \""+role+"\" group.|";
				}
			}
			if (changed) tcUsers.addTomcatUser(tcUser);

			//Next see if he wants to join a group.
			//Get the group name and password from the form.
			String groupname = req.getParameter("groupname");
			groupname = (groupname != null) ? groupname.trim() : "";
			String grouppassword = req.getParameter("grouppassword");
			grouppassword = (grouppassword != null) ? grouppassword.trim() : "";
			if (!groupname.equals("") && !grouppassword.equals("")) {
				MircGroup mircGroup = tcUsers.getMircGroup(groupname);
				if (mircGroup == null)
					message += "The group \""+groupname+"\" does not exist.|";
				else if (!mircGroup.password.equals(grouppassword))
					message += "The group \""+groupname+"\" did not accept the password.|";
				else {
					tcUser = tcUsers.getTomcatUser(username);
					tcUser.addRole(groupname);
					tcUsers.addTomcatUser(tcUser);
					message +=
						"User \""+username+"\" joined group \""+groupname+"\".|";
				}
			}

			//Next see if he wants to create a group.
			//Get the new groupname and password from the form.
			String newgroupname = req.getParameter("newgroupname");
			newgroupname = (newgroupname != null) ? newgroupname.trim() : "";
			String newgrouppassword1 = req.getParameter("newgrouppassword1");
			newgrouppassword1 = (newgrouppassword1 != null) ? newgrouppassword1.trim() : "";
			String newgrouppassword2 = req.getParameter("newgrouppassword2");
			newgrouppassword2 = (newgrouppassword2 != null) ? newgrouppassword2.trim() : "";
			//Validate the data.
			if (!newgroupname.equals("") && mc.getGroupCreationEnabled()) {
				if (!newgrouppassword1.equals(newgrouppassword2)) {
					message += "The passwords for \""+newgroupname+"\" did not match.|"
							+  "The group was not created.|";
				}
				else {
					MircGroup mircGroup = tcUsers.getMircGroup(newgroupname);
					if (mircGroup == null) {
						//The group does not exist.
						//Make sure it is not a management role name.
						if (tcUsers.hasRole(newgroupname)) {
							//The group name is a management role name; do not allow it.
							message += "\""+newgroupname+"\" is a management role name.|"
									+  "The group could not be created.|";
						}
						else {
							//It is not a management role name; create the group.
							mircGroup = new MircGroup(newgroupname,newgrouppassword1);
							tcUsers.addMircGroup(mircGroup);
							//Now add the user to the group.
							tcUser = tcUsers.getTomcatUser(username);
							tcUser.addRole(newgroupname);
							tcUsers.addTomcatUser(tcUser);
							message += "The group \""+newgroupname+"\" was created.|"
									+ "User \""+username+"\" was added to the group.|";
						}
					}
					else {
						//The group exists. Update the password if the current
						//user is a member of the group.
						message += "The group \""+newgroupname+"\" already exists.|";
						if (req.isUserInRole(newgroupname)) {
							mircGroup.password = newgrouppassword1;
							tcUsers.addMircGroup(mircGroup);
							message += "The password was updated.|";
						}
						else message += "You are not authorized to change its password.|";
					}
				}
			}

			//Next see if he wants to create or change his MyRsna account information
			String myrsnaUsername = req.getParameter("myrsnaname");
			String myrsnaPassword = req.getParameter("myrsnapw");
			if ((myrsnaUsername != null) && (myrsnaPassword != null)) {
				myrsnaUsername = myrsnaUsername.trim();
				myrsnaPassword = myrsnaPassword.trim();
				if (!myrsnaUsername.equals("") && !myrsnaPassword.equals("")) {
					MyRsnaUser mru = new MyRsnaUser(myrsnaUsername, myrsnaPassword);
					MyRsnaUsers mrus = MyRsnaUsers.getInstance();
					if (mrus != null) mrus.addMyRsnaUser( username, mru);
				}
			}

			//And finally, display the page again.
			loadPage(req, res, message);
		}
		catch (Exception ex) {
			ServletUtil.sendPageNoCache(
				res,
				HtmlUtil.getStyledPageWithCloseBox(
					"user-account-manager.css",
					"Error",
					"Unable to process the request.",
					""
				)
			);
			logger.warn("Unable to process the request.",ex);
		}
	}

	//Get the admin role name for the mircadmin servlets
	private String getAdminRoleName() {
		File webxmlFile = new File(getServletContext().getRealPath("/WEB-INF/web.xml"));
		String webxml = FileUtil.getFileText(webxmlFile);
		return XmlStringUtil.getEntity(webxml,"tomcat");
	}

	//Add the username and password attributes for this user to the
	//server elements in the MircConfig XML Document.
	private void setPassport(String username, Document mcXML) {
		File file = new File(getServletContext().getRealPath("/"));
		Passports passports = new Passports(file);
		Passport passport = passports.getPassport(username);
		if (passport == null) return;
		Element root = mcXML.getDocumentElement();
		NodeList nodeList = root.getElementsByTagName("server");
		for (int i=0; i<nodeList.getLength(); i++) {
			Element server = (Element)nodeList.item(i);
			String url = server.getAttribute("address");
			Visa visa = passport.getVisa(url);
			if (visa != null) {
				server.setAttribute("username",visa.username);
				server.setAttribute("password",visa.password);
			}
		}
	}

	//Create a servers document with one server element for each
	//server, including the user's credentials on that server.
	//Each server element contains one service element for
	//each storage service to specify its name.
	private Document getServersXML(String username, String password, Document mcXML) {
		Document serversXML = null;
		try {
			//First make a document to return
			serversXML = XmlUtil.getDocument();
			Element serversRoot = serversXML.createElement("servers");
			serversXML.appendChild(serversRoot);

			//Next get the user's passport.
			File file = new File(getServletContext().getRealPath("/"));
			Passports passports = new Passports(file);
			Passport passport = passports.getPassport(username);

			//Next get the URL of the query service
			Element mcRoot = mcXML.getDocumentElement();
			NodeList nodeList = mcRoot.getElementsByTagName("queryservice");
			String qsURL = "";
			if (nodeList.getLength() != 0) {
				Element qs = (Element)nodeList.item(0);
				qsURL = Visa.getServerURL(qs.getAttribute("address"));
			}

			//Now collect the server elements from mcXML.
			nodeList = mcRoot.getElementsByTagName("server");
			Hashtable<String,Element> servers = new Hashtable<String,Element>();

			for (int i=0; i<nodeList.getLength(); i++) {
				Element server = (Element)nodeList.item(i);
				String url = Visa.getServerURL(server.getAttribute("address"));
				String serviceName = XmlUtil.getValueViaPath(server,"server");

				//See if we already have a server element
				//for this server in the hashtable.
				Element xServer = servers.get(url);
				if (xServer == null) {
					xServer = serversXML.createElement("server");
					xServer.setAttribute("url",url);
					xServer.setAttribute("order",""+i);
					if (url.equals(qsURL)) {
						xServer.setAttribute("username",username);
						xServer.setAttribute("password",password);
					}
					else if (passport != null) {
						Visa visa = passport.getVisa(url);
						if (visa != null) {
							xServer.setAttribute("username",visa.username);
							xServer.setAttribute("password",visa.password);
						}
					}
					serversRoot.appendChild(xServer);
				}

				//Put in the service name
				Element xService = serversXML.createElement("service");
				xService.appendChild(serversXML.createTextNode(serviceName));
				xServer.appendChild(xService);
				servers.put(url,xServer);
			}
		}
		catch (Exception ex) { }
		return serversXML;
	}

	//Get an XML Document listing the groups to which the user belongs.
	private Document getGroupsXML(String username) {
		File dir = new File(getServletContext().getRealPath("/"));
		TomcatUsers tcUsers = TomcatUsers.getInstance(dir);
		TomcatUser tcUser = tcUsers.getTomcatUser(username);
		try {
			Document groupsXML = XmlUtil.getDocument();
			Element root = groupsXML.createElement("groups");
			groupsXML.appendChild(root);
			if (tcUser != null) {
				String[] groupArray = tcUser.getMircGroupsForUser(tcUsers);
				for (int i=0; i<groupArray.length; i++) {
					Element group = groupsXML.createElement("group");
					group.appendChild(groupsXML.createTextNode(groupArray[i]));
					root.appendChild(group);
				}
			}
			return groupsXML;
		}
		catch (Exception error) { return null; }
	}

	private void loadPage(
				HttpServletRequest req,
				HttpServletResponse res,
				String message
				) throws ServletException {
		try {
			File dir = new File(getServletContext().getRealPath("/"));
			MircConfig mc = MircConfig.getInstance(dir);

			String username = req.getRemoteUser();
			String password = "";
			String myrsnaUsername = "";
			String myrsnaPassword = "";

			String userIsAuthenticated = "yes";
			if (username != null) {
				TomcatUsers tcUsers = TomcatUsers.getInstance(dir);
				TomcatUser tcUser = tcUsers.getTomcatUser(username);
				if (tcUser != null) password = tcUser.password;

				MyRsnaUsers mrus = MyRsnaUsers.getInstance();
				if (mrus != null) {
					MyRsnaUser mru = mrus.getMyRsnaUser(username);
					if (mru != null) {
						myrsnaUsername = mru.username;
						myrsnaPassword = mru.password;
					}
				}
			}
			else {
				username = "";
				userIsAuthenticated = "no";
			}

			String adminRoleName = getAdminRoleName();
			String userIsAdmin = ((req.isUserInRole(adminRoleName)) ? "yes" : "no");

			File xslFile =
				new File(getServletContext().getRealPath("/user-account-manager.xsl"));

			Document mcXML = mc.getXML();
			Object[] params = new Object[] {
				"message",				message,
				"username",				username,
				"user-is-authenticated",userIsAuthenticated,
				"user-is-admin",		userIsAdmin,
				"groups",				getGroupsXML(username),
				"servers",				getServersXML(username,password,mcXML),
				"myrsna-username",		myrsnaUsername,
				"myrsna-password",		myrsnaPassword
			};
			String page = XmlUtil.getTransformedText(mcXML,xslFile,params);

			ServletUtil.sendPageNoCache(res,page);
		}
		catch (Exception ex) {
			logger.warn(ex.getMessage(),ex);
			ServletUtil.sendPageNoCache(
				res,
				HtmlUtil.getStyledPageWithCloseBox(
					"user-account-manager.css",
					"Error",
					"Unable to create the account management page.",
					""
				)
			);
		}
	}


}
