/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.queryservice;

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
import org.rsna.mircsite.util.MyRsnaUsers;
import org.rsna.mircsite.util.Passport;
import org.rsna.mircsite.util.Passports;
import org.rsna.mircsite.util.RadLexIndex;
import org.rsna.mircsite.util.ServerConfig;
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
 * The Query Service Admin servlet.
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
public class AdminService extends HttpServlet {

	static final Logger logger = Logger.getLogger(AdminService.class);

	/**
	 * Initialize the RadLex and the MyRsnaUsers databases on startup.
	 * This method is called when the servlet container parses the webapp's web.xml file.
	 */
	public void init() {
		Runnable radlexIndexer = new Runnable() {
			public void run() {
				RadLexIndex.loadIndex(new File( getServletContext().getRealPath("/") ) );
			};
		};
		radlexIndexer.run();
		String path = getServletContext().getRealPath("/");
		MyRsnaUsers.loadMyRsnaUsers( new File(path) );
	}

	/**
	 * Close down the servlet. This method only closes the MyRsnaUsers database.
	 * This method is called when the servlet container takes the servlet out of service.
	 */
	public void destroy() {
		MyRsnaUsers.close();
	}

	/**
	 * The servlet method that responds to an HTTP GET.
	 * <p>
	 * This method returns an HTML page containing a form for
	 * updating the mirc/mirc.xml file.
	 * file.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doGet(
				HttpServletRequest req,
				HttpServletResponse res
				) throws ServletException {

		File dir = new File(getServletContext().getRealPath("/"));
		MircConfig mc = MircConfig.getInstance(dir);
		sendConfigPage(mc, res);
	}

	/**
	 * The servlet method that responds to an HTTP POST.
	 * <p>
	 * This method uses the posted parameters to update the mirc/mirc.xml file.
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

		try {
			File dir = new File(getServletContext().getRealPath("/"));
			MircConfig mc = MircConfig.getInstance(dir);
			String oldLocalAddress = mc.getLocalAddress();

			//Get the primary configuration parameters
			mc.setPrimarySystemParameters(
				req.getParameter("mode"),
				req.getParameter("sitename"),
				req.getParameter("showsitename"),
				req.getParameter("masthead"),
				req.getParameter("startpage"),
				req.getParameter("showlogin"),
				req.getParameter("showptids"),
				req.getParameter("siteurl"),
				req.getParameter("addresstype"),
				req.getParameter("disclaimerurl"),
				req.getParameter("timeout"),
				req.getParameter("proxyip"),
				req.getParameter("proxyport"),
				req.getParameter("proxyusername"),
				req.getParameter("proxypassword")
			);

			//Get the account table parameters.
			String acctenb = req.getParameter("acctenb");
			String gpenb = req.getParameter("gpenb");
			String defroles = req.getParameter("defroles");
			if (defroles != null) {
				defroles = defroles.replaceAll("\\s+",",").replaceAll("[,]+",",");
				mc.setAccountSystemParameters(
					((acctenb != null) ? acctenb : "no"),
					((gpenb != null) ? gpenb : "no"),
					defroles);
			}

			//Get the server list
			String newLocalAddress = mc.getLocalAddress();
			Server[] servers = getServers(req, newLocalAddress, oldLocalAddress);
			Arrays.sort(servers);
			String serversText = "";
			for (int i=0; i<servers.length; i++) {
				serversText += servers[i].toXMLString();
			}
			mc.setServers(serversText);

			//Reload the configuration so everybody knows what happened.
			mc = mc.reload();

			//Now return the updated configuration page
			sendConfigPage(mc, res);
		}
		catch (Exception ex) {
			ServletUtil.sendPageNoCache(
				res,
				HtmlUtil.getStyledPageWithCloseBox(
					"admin.css",
					"Error",
					"Unable to process the request.",
					""
				)
			);
			logger.warn("Unable to process the request.",ex);
		}
	}

	private void sendConfigPage(MircConfig mc, HttpServletResponse res) {
		try {
			File xslFile =
				new File(getServletContext().getRealPath("admin.xsl"));

			Document mcXML = mc.getXML();
			String page = XmlUtil.getTransformedText(mcXML,xslFile);

			ServletUtil.sendPageNoCache(res,page);
		}
		catch (Exception ex) {
			logger.warn(ex.getMessage(),ex);
			ServletUtil.sendPageNoCache(
				res,
				HtmlUtil.getStyledPageWithCloseBox(
					"admin.css",
					"Error",
					"Unable to create the account management page.",
					""
				)
			);
		}
	}

	private Server[] getServers(HttpServletRequest req, String newLocalAddress, String oldLocalAddress) {
		List<Server> sList = new LinkedList<Server>();
		int i = 0;
		Server server;
		while ((server = getServer(req, i++, newLocalAddress, oldLocalAddress)) != null) {
			if (!server.name.equals("") && !server.address.equals("")) {
				sList.add(server);
			}
		}
		Server[] sArray = new Server[sList.size()];
		return sList.toArray(sArray);
	}

	private Server getServer(HttpServletRequest req, int i, String newLocalAddress, String oldLocalAddress) {
		String enb = req.getParameter("enb"+i);
		String adrs = req.getParameter("adrs"+i);
		String name = req.getParameter("name"+i);
		if ((adrs==null) || (name==null)) return null;
		return new Server(name, adrs, enb, newLocalAddress, oldLocalAddress);
	}

	class Server implements Comparable {
		public String name;
		public String address;
		public String enabled;
		public boolean isLocal = false;

		public Server(String name,
					  String address,
					  String enabled,
					  String newLocalAddress,
					  String oldLocalAddress) {
			this.name = name.trim();
			this.address = address.trim();
			this.enabled = (enabled==null) ? "no" : enabled.trim();
			if (this.address.startsWith(newLocalAddress)) {
				this.address = "&siteurl;" + this.address.substring(newLocalAddress.length());
				isLocal = true;
			}
			else if (this.address.startsWith(oldLocalAddress)) {
				this.address = "&siteurl;" + this.address.substring(oldLocalAddress.length());
				isLocal = true;
			}
		}

		public String toXMLString() {
			return
				"<server address=\"" + address + "\" enabled=\"" + enabled + "\">\n"
			  + "    " + name + "\n"
			  + "</server>\n";
		}

		public int compareTo(Object obj) {
			if (!(obj instanceof Server)) return -1;
			Server s = (Server)obj;
			if (isLocal != s.isLocal) return (isLocal ? -1 : +1);
			return name.compareTo(s.name);
		}
	}

}
