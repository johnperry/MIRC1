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
import org.rsna.mircsite.util.FileUtil;
import org.rsna.mircsite.util.HtmlUtil;
import org.rsna.mircsite.util.ServletUtil;
import org.rsna.mircsite.util.ServerConfig;
import org.rsna.mircsite.util.TomcatUser;
import org.rsna.mircsite.util.TomcatUsers;
import org.rsna.mircsite.util.TomcatUsersXmlFileImpl;

/**
 * The User Role Manager servlet.
 * <p>
 * This servlet provides a browser-accessible user interface for
 * editing the Tomcat/conf/tomcat-users.xml file used by the
 * memory realm for managing users and roles. This servlet will
 * be replaced when the database realm is implemented.
 * <p>
 * This servlet responds to both HTTP GET and POST.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class UserRoleManager extends HttpServlet {

	/**
	 * The servlet method that responds to an HTTP GET.
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

		//Get a File pointing to the root of the servlet
		File rootDir = new File(getServletContext().getRealPath("/"));

		//Get the TomcatUsers object.
		TomcatUsers tcUsers = TomcatUsers.getInstance(rootDir);

		//Make sure that this system is using the XML implementation.
		if (!(tcUsers instanceof TomcatUsersXmlFileImpl)) {
			ServletUtil.sendPageNoCache(
				res,
				HtmlUtil.getPageWithCloseButton(
					"Not XML Implementation",
					"This site does not use the Tomcat XML implementation for users.<br/>"
					+"The User Role Manager is not available on this site."
				)
			);
			return;
		}

		//Make the page and return it.
		TomcatUsersXmlFileImpl tcUsersXmlFileImpl = (TomcatUsersXmlFileImpl)tcUsers;
		ServletUtil.sendPageNoCache(res,getPage(tcUsersXmlFileImpl, rootDir));
	}

	/**
	 * The servlet method that responds to an HTTP POST.
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

		//Get a File pointing to the root of the servlet
		File rootDir = new File(getServletContext().getRealPath("/"));

		//Get the TomcatUsers object.
		TomcatUsers tcUsers = TomcatUsers.getInstance(rootDir);

		//Make sure that this system is using the XML implementation.
		if (!(tcUsers instanceof TomcatUsersXmlFileImpl)) return;

		TomcatUsersXmlFileImpl tcUsersXmlFileImpl = (TomcatUsersXmlFileImpl)tcUsers;

		//Get the number of parameters passed
		Enumeration en = req.getParameterNames();
		int nParams = 0;
		while (en.hasMoreElements()) {
			nParams++;
			en.nextElement();
		}

		//Get the parameter names and values
		en = req.getParameterNames();
		String[] params = new String[nParams];
		String[] values = new String[nParams];
		for (int i=0; i<nParams; i++) {
			params[i] = ((String)en.nextElement()).trim();
			values[i] = req.getParameter(params[i]);
		}

		//Get the number of users and the number of roles
		int nUsers = getMaxIndex(params,"u") + 1;
		int nRoles = getMaxIndex(params,"r") + 1;

		//Get the names in a convenient array.
		String[] roleNames = new String[nRoles];
		for (int i=0; i<nRoles; i++) {
			roleNames[i] = getValue(params,values,"r",i);
		}

		//Create the users.
		//Since we are only managing the admin roles, we need to
		//have access to the current users in order to get their groups.
		Hashtable<String,TomcatUser> oldUserTable = tcUsersXmlFileImpl.getTomcatUsers();

		//Make a new table to store the users we are now creating.
		Hashtable<String,TomcatUser> newUserTable = new Hashtable<String,TomcatUser>();

		//Process all the input.
		for (int i=0; i<nUsers; i++) {
			String username = getValue(params,values,"u",i);
			if (!username.equals("")) {
				//Get the old user or create a new one if the old one doesn't exist.
				//Note: if the username changes, all the old groups are lost.
				//This allows re-using obsolete usernames, but it means that you
				//can't change a username and have it refer to the same user.
				TomcatUser user = tcUsersXmlFileImpl.getTomcatUser(username);
				if (user == null) user = new TomcatUser(username,"","");

				//Now update the password and roles.
				user.password = getValue(params,values,"p",i);
				boolean roleFound = false;
				for (int j=0; j<nRoles; j++) {
					String role = getValue(params,values,"cb",i,j);
					if (!role.equals("")) {
						user.addRole(roleNames[j]);
						roleFound = true;
					}
					else user.removeRole(roleNames[j]);
				}
				if (roleFound) newUserTable.put(username,user);
			}
		}

		//Reset the users database from the hashtable.
		tcUsersXmlFileImpl.resetUsers(newUserTable);

		//Make a new page from the new data and return it.
		ServletUtil.sendPageNoCache(res,getPage(tcUsersXmlFileImpl, rootDir));
	}

	//Get the value of named parameter [i]
	private String getValue(String[] params, String[] values, String prefix, int i) {
		String name = prefix+i;
		return getValueFromName(params,values,name);
	}

	//Get the value of named parameter [i,j]
	private String getValue(String[] params, String[] values, String prefix, int i, int j) {
		String name = prefix + "u" + i + "r" + j;
		return getValueFromName(params,values,name);
	}

	//Get the value of the named parameter
	private String getValueFromName(String[] params, String[] values, String name) {
		for (int i=0; i<params.length; i++) {
			if (params[i].equals(name)) {
				String value = values[i];
				if (value == null) return "";
				return value.trim();
			}
		}
		return "";
	}

	//Find the maximum index value of a named parameter
	private int getMaxIndex(String[] params, String prefix) {
		int max = 0;
		int v;
		for (int i=0; i<params.length; i++) {
			if (params[i].startsWith(prefix)) {
				try {
					String rest = params[i].substring(prefix.length());
					v = Integer.parseInt(rest);
					if (v > max) max = v;
				}
				catch (Exception e) { }
			}
		}
		return max;
	}

	//Create an HTML page containing the form for managing
	//the users and roles.
	private String getPage(TomcatUsersXmlFileImpl tcUsers, File rootDir) {
		TomcatUser[] users = getUsers(tcUsers);
		String[] roles = getRoles(rootDir.getParentFile());

		StringBuffer sb = new StringBuffer();
		responseHead(sb);
		makeTableHeader(sb, roles);
		makeTableRows(sb, users, roles);
		responseTail(sb);

		return sb.toString();
	}

	private void makeTableHeader(StringBuffer sb, String[] roles) {
		sb.append( "<thead>\n"
					+ " <tr>\n"
					+ "  <th/>\n" );
		for (int i=0; i<roles.length; i++) {
			sb.append( "  <th class=\"thv\"><nobr>"+roles[i]+"</nobr>"
				  	+  "<input name=\"r"+i+"\" type=\"hidden\" value=\""+roles[i]+"\"/></th>\n" );
		}
		sb.append( " </tr>\n" );
		sb.append( "</thead>\n" );
	}

	private void makeTableRows(StringBuffer sb, TomcatUser[] users, String[] roles) {
		for (int i=0; i<users.length; i++) {
			sb.append( "<tr>\n" );
			sb.append( " <td class=\"tdl\">"
					 	+  "<input name=\"u"+i+"\" value=\""+users[i].username+"\"/>"
					 	+  "</td>\n" );
			for (int j=0; j<roles.length; j++) {
				sb.append( "<td><input name=\"cbu"+i+"r"+j+"\" type=\"checkbox\"" );
				if ((users[i].hasRole(roles[j]))) sb.append( " checked=\"true\"" );
				sb.append( "/></td>\n" );
			}
			sb.append( " <td class=\"tdl\">"
					 +  "<input name=\"p"+i+"\" type=\"password\" value=\""+users[i].password+"\"/>"
					 +  "</td>\n" );
			sb.append( " </tr>\n" );
		}
		sb.append( "<tr>\n" );
		sb.append( "<td class=\"tdl\"><input name=\"u"+users.length+"\"/></td>\n" );
		for (int j=0; j<roles.length; j++) {
			sb.append( "<td><input name=\"cbu"+users.length+"r"+j+"\" type=\"checkbox\"/></td>\n" );
		}
		sb.append( " <td class=\"tdl\"><input name=\"p"+users.length+"\"/></td>\n" );
		sb.append( " </tr>\n" );
	}

	//Get a sorted array of all the TomcatUser objects corresponding
	//to user elements in the tomcat-users.xml file.
	private TomcatUser[] getUsers(TomcatUsersXmlFileImpl tcUsers) {
		Hashtable<String,TomcatUser> hashtable = tcUsers.getTomcatUsers();
		TomcatUser[] u = new TomcatUser[hashtable.size()];
		u = hashtable.values().toArray(u);
		Arrays.sort(u);
		return u;
	}

	//Create a sorted list of admin roles, combining all the roles
	//declared in web.xml files.
	private String[] getRoles(File webapps) {
		Hashtable<String,String> h = new Hashtable<String,String>();
		addEntities(webapps,h);
		String[] roles = new String[h.size()];
		roles = h.keySet().toArray(roles);
		Arrays.sort(roles);
		return roles;
	}

	//Find all the roles defined in entity definitions in
	//web.xml files for storage services and the file service and add
	//them to a hashtable.
	private void addEntities(File webapps, Hashtable<String,String> h) {
		File[] list = webapps.listFiles();
		if (list == null) return;
		for (int i=0; i<list.length; i++) {
			if (list[i].isDirectory()) {
				addDirEntities(list[i],h);
			}
		}
	}

	//Find all the roles defined in entity definitions in
	//one web.xml file and add them to a hashtable. The dir File
	//points to the root of the webapp, so it is necessary to find
	//the web.xml file relative to it.
	private void addDirEntities(File dir, Hashtable<String,String> h) {
		File webxml = new File(dir,"WEB-INF/web.xml");
		if (!webxml.exists()) return;
		String webxmlText = FileUtil.getFileText(webxml);
		insertEntities(webxmlText,h);
	}

	//Add all the roles defined in entity definitions
	//in a String and add them to a hashtable.
	private void insertEntities(String s, Hashtable<String,String> h) {
		int begin = -1;
		int end;
		String name, value;
		while ((begin = s.indexOf("!ENTITY",begin+1)) != -1) {
			begin = s.indexOf("\"",begin) + 1;
			end = s.indexOf("\"",begin);
			value = s.substring(begin,end);
			h.put(value,"y");
		}
	}

	private void responseHead(StringBuffer sb) {
		sb.append(
				"<html>\n"
			+	" <head>\n"
			+	"  <title>User Role Manager</title>\n"
			+	"   <style>\n"
			+	"    body {background-color:#c6d8f9; margin:0; padding:0;}\n"
			+	"    h1 {padding-top:10;}\n"
			+	"    .thv {layout-flow:vertical-ideographic; padding:5}\n"
			+	"    .thl {text-align:left; padding:5; glyph-orientation}\n"
			+	"    .tdl {text-align:left; padding 5}\n"
			+	"    td {text-align:center; padding:5}\n"
			+	"   </style>\n"
			+	" </head>\n"
			+	" <body>\n"
			+	HtmlUtil.getCloseBox()
			+	"  <center>\n"
			+	"   <h1>User Role Manager</h1>\n"
			+	"   <form method=\"post\" accept-charset=\"UTF-8\" action=\"\">\n"
			+	"    <table border=\"1\">\n"
		);
	}

	private void responseTail(StringBuffer sb) {
		sb.append(
				"    </table>\n"
			+	"    <br/>\n"
			+	"    <input type=\"submit\" value=\"Update tomcat-users.xml\">\n"
			+	"   </form>\n"
			+	"   <p>Changes will become effective in 60 seconds.</p>\n"
			+	"  </center>\n"
			+	" </body>\n"
			+	"</html>\n"
		);
	}

}











