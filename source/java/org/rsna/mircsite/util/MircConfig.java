/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A class to encapsulate the query service configuration file (mirc.xml).
 */
public class MircConfig {

	static File webapps = null;
	static File mirc = null;
	static Document mircXML = null;
	static Element root;
	static Document localServices = null;
	static int timeout = 10;
	static ProxyServer proxy = null;

	protected static MircConfig mircConfigInstance = null;
	static final Logger logger = Logger.getLogger(MircConfig.class);

	/**
	 * Private singleton constructor; this class must be
	 * instantiated using the MircConfig.getInstance(File) method.
	 */
	private MircConfig(File file) {
		//Find the mirc directory
		webapps = new File(file.getAbsolutePath());
		while (!webapps.isDirectory() || !webapps.getName().equals("webapps"))
			webapps = webapps.getParentFile();
		mirc = new File(webapps,"mirc");

		//Find the query service configuration file
		mirc = new File(mirc,"mirc.xml");

		//Set the masthead height entity
		setMastheadHeight(mirc);

		//Load the file and then load the local services.
		loadXML();
		loadLocalServices();
	}

	/**
	 * Get the MircConfig instance.
	 * @param file any file under the webapps tree.
	 */
	public static MircConfig getInstance(File file) {
		if (mircConfigInstance == null)
			mircConfigInstance = new MircConfig(file);
		return mircConfigInstance;
	}

	/**
	 * Reload the MircConfig instance.
	 */
	public MircConfig reload() {
		mircConfigInstance = new MircConfig(mirc);
		return mircConfigInstance;
	}

	//Set the masthead height entity in mirc.xml
	private void setMastheadHeight(File mirc) {
		try {
			String mircText = FileUtil.getFileText(mirc);
			String mh = XmlStringUtil.getEntity(mircText,"masthead");
			File mhFile = new File(mirc.getParentFile(), mh);
			MircImage mhImage = new MircImage(mhFile);
			int mhImageHeight = mhImage.getHeight();
			mircText = XmlStringUtil.setEntity(mircText,"mastheadheight",Integer.toString(mhImageHeight));
			FileUtil.setFileText(mirc, mircText);
		}
		catch (Exception ex) {
			logger.warn("Unable to set the masthead height", ex);
		}
	}

	/**
	 * Get the local address.
	 * @return the URL of Tomcat.
	 */
	public static String getLocalAddress() {
		return root.getAttribute("siteurl");
	}

	/**
	 * Determine whether a URL is local to the MIRC site.
	 * @param url the URL to be tested against the base URL of the Tomcat server.
	 * @return true if the URL is on the same server as the query service.
	 */
	public static boolean isLocal(String url) {
		return url.startsWith(root.getAttribute("siteurl"));
	}

	/**
	 * Get the Document object containing the mirc.xml file.
	 */
	public static Document getMircXML() {
		return mircXML;
	}

	/**
	 * Get the Document object describing the local services.
	 */
	public static Document getLocalServicesXML() {
		return localServices;
	}

	/**
	 * Generate a unique list of servers based on the URL of the server.  If two servers
	 * in mirc.xml have the same DNS, it will come out of here as a single ServerCredential.
	 * Each ServerCredential will have a list of all the servers names that have that same DNS.
	 * Any servers that share the DNS of the query service are not included in this list.
	 */
	public HashSet<ServerCredential> getServersWithCredentials(String username) {

		HashSet<ServerCredential> serverSet = new HashSet<ServerCredential>();

		Passports passports = new Passports(mirc);
		Passport passport = passports.getPassport(username);


		NodeList nodeList = root.getElementsByTagName("server");
		// loop thru the server elements
		for (int i=0; i<nodeList.getLength(); i++) {
			Element serverElement = (Element)nodeList.item(i);
			String url = Visa.getServerURL(serverElement.getAttribute("address"));
			// if an element already exists with this URL in the set, get it,
			// otherwise create a new one.
			ServerCredential serverCredential = null;
			Iterator<ServerCredential> iter = serverSet.iterator();
			while( iter.hasNext()) {
				ServerCredential aServer = iter.next();
				if( aServer.getUrl().equals(url) ) {
					serverCredential = aServer;
				}
			}
			// make a new one, if we didn't find it already
			if( serverCredential == null ) {
				serverCredential = new ServerCredential();
				serverCredential.setUrl(url);
				// if passport or visa is null, they will go in with a null username and password.
				if (passport != null) {
					Visa visa = passport.getVisa(serverCredential.getUrl());
					if (visa != null) {
						serverCredential.setUsername(visa.username);
						serverCredential.setPassword(visa.password);
					}
				}
				serverSet.add(serverCredential);
			} else {
			}
			// add the name in
			String serverName = XmlUtil.getValueViaPath(serverElement,"server");
			serverCredential.getSharedServerNames().add(serverName);
		}
		return serverSet;
	}

	/**
	 * Get the number of server elements in the MIRC configuration XML object.
	 * This is the number of storage services known to the MIRC site.
	 * @return number of server elements in the MIRC configuration XML object.
	 */
	public int getNumberOfServers() {
		return root.getElementsByTagName("server").getLength();
	}

	/**
	 * Get the MIRC configuration XML object.
	 * @return the MIRC configuration XML DOM object.
	 */
	public Document getXML() {
		return mircXML;
	}

	/**
	 * Get the TomcatUsers implementation class name.
	 * @return the fully qualified classname of the implementation of TomcatUsers
	 * for this MIRC site (for example "org.rsna.mircsite.util.TomcatUsersXmlFileImpl").
	 */
	public String getTomcatUsersClassName() {
		if (mircXML != null) {
			return XmlUtil.getValueViaPath(mircXML,"mirc/accounts@tcusersclass");
		}
		return "no.TomcatUsers.class";
	}

	/**
	 * Get the name of the MIRC site from the MIRC configuration XML DOM object.
	 */
	public String getSiteName() {
		if (mircXML != null) {
			return XmlUtil.getValueViaPath(mircXML,"mirc@sitename");
		}
		return "";
	}

	/**
	 * Get the query timeout in seconds.
	 */
	public int getQueryTimeout() {
		return timeout;
	}

	/**
	 * Get the proxy server object.
	 * @return the current proxy server object.
	 */
	public ProxyServer getProxyServer() {
		return proxy;
	}

	/**
	 * Get the flag indicating whether password changes are allowed or not
	 */
	public boolean isPasswordChangeAllowed() {
		if (mircXML != null) {
			String enb = XmlUtil.getValueViaPath(mircXML,"mirc/accounts@allowpasswordchange");
			return (enb != null) && enb.equals("yes");
		}
		return false;
	}

	/**
	 * Get the flag indicating whether account creation is enabled.
	 * @return true if account creation is enabled; false otherwise.
	 */
	public boolean getAcctCreationEnabled() {
		if (mircXML != null) {
			String enb = XmlUtil.getValueViaPath(mircXML,"mirc/accounts@enabled");
			return (enb != null) && enb.equals("yes");
		}
		return false;
	}

	/**
	 * Set the flag indicating whether account creation is enabled.
	 * @param enabled true if group creation is to be enabled; false otherwise.
	 */
	public void setAcctCreationEnabled(boolean enabled) {
		setEntity("acctenb", (enabled ? "yes" : "no"));
	}

	/**
	 * Get the flag indicating whether group creation is enabled.
	 * @return true if group creation is enabled; false otherwise.
	 */
	public boolean getGroupCreationEnabled() {
		if (mircXML != null) {
			String enb = XmlUtil.getValueViaPath(mircXML,"mirc/accounts/groups@enabled");
			return (enb != null) && enb.equals("yes");
		}
		return false;
	}

	/**
	 * Set the flag indicating whether group creation is enabled.
	 * @param enabled true if group creation is to be enabled; false otherwise.
	 */
	public void setGroupCreationEnabled(boolean enabled) {
		setEntity("gpenb", (enabled ? "yes" : "no"));
	}

	/**
	 * Get the default roles for new accounts.
	 * @return the default roles for new accounts.
	 */
	public String getDefaultRoles() {
		if (mircXML != null) {
			return XmlUtil.getValueViaPath(mircXML,"mirc/accounts@roles");
		}
		return "";
	}

	/**
	 * Set the default roles for new accounts.
	 * @param defroles the default roles for new accounts.
	 */
	public void setDefaultRoles(String defroles) {
		setEntity("defroles",defroles);
	}

	/**
	 * Set all the primary system parameters.
	 * @param mode the query mode for race/sex/species/breed (rad/vet).
	 * @param sitename the name of the site for display in the masthead.
	 * @param showsitename whether to display the site in the masthead (yes/no).
	 * @param masthead the name of the image to be used for the masthead.
	 * @param startpage the name of the div to be used on startup (maincontent or altcontent).
	 * @param siteurl the URL of Tomcat (http://ip:port).
	 * @param addresstype whether to obtain the IP address from the OS on startup (dynamic/static).
	 * @param disclaimerurl the URL of the disclaimer page (if blank, no disclaimer is presented).
	 * @param timeout the query timeout in seconds.
	 * @param proxyip the IP address of the proxy server, or "" if no proxy server is in use.
	 * @param proxyport the port of the proxy server.
	 * @param proxyusername the username required by the proxy server, or "" if none is required.
	 * @param proxypassword the password required by the proxy server, or "" if none is required.
	 */
	public void setPrimarySystemParameters(
			String mode,
			String sitename,
			String showsitename,
			String masthead,
			String startpage,
			String showlogin,
			String showptids,
			String siteurl,
			String addresstype,
			String disclaimerurl,
			String timeout,
			String proxyip,
			String proxyport,
			String proxyusername,
			String proxypassword
			) {
		String text = FileUtil.getFileText(mirc);
		text = XmlStringUtil.setEntity(text,"mode",mode);
		text = XmlStringUtil.setEntity(text,"sitename",sitename);
		text = XmlStringUtil.setEntity(text,"showsitename",showsitename);
		text = XmlStringUtil.setEntity(text,"masthead",masthead);
		text = XmlStringUtil.setEntity(text,"startpage",startpage);
		text = XmlStringUtil.setEntity(text,"showlogin",showlogin);
		text = XmlStringUtil.setEntity(text,"showptids",showptids);
		text = XmlStringUtil.setEntity(text,"siteurl",siteurl);
		text = XmlStringUtil.setEntity(text,"addresstype",addresstype);
		text = XmlStringUtil.setEntity(text,"disclaimerurl",disclaimerurl);
		text = XmlStringUtil.setEntity(text,"timeout",timeout);
		text = XmlStringUtil.setEntity(text,"proxyip",proxyip);
		text = XmlStringUtil.setEntity(text,"proxyport",proxyport);
		text = XmlStringUtil.setEntity(text,"proxyusername",proxyusername);
		text = XmlStringUtil.setEntity(text,"proxypassword",proxypassword);
		FileUtil.setFileText(mirc,text);
		loadXML();
	}

	/**
	 * Set all the account system parameters.
	 * @param acctenb true if account creation is to be enabled; false otherwise.
	 * @param gpenb true if group creation is to be enabled; false otherwise.
	 * @param defroles the default roles for new accounts.
	 */
	public void setAccountSystemParameters(
			String acctenb,
			String gpenb,
			String defroles) {
		String text = FileUtil.getFileText(mirc);
		text = XmlStringUtil.setEntity(text,"acctenb",acctenb);
		text = XmlStringUtil.setEntity(text,"gpenb",gpenb);
		text = XmlStringUtil.setEntity(text,"defroles",defroles);
		FileUtil.setFileText(mirc,text);
		loadXML();
	}

	/**
	 * Replace all the server elements with the supplied text.
	 * @param servers the XML text for the servers.
	 */
	public void setServers(String servers) {
		String text = FileUtil.getFileText(mirc);
		int start = text.indexOf("<server");
		if (start == -1) return;
		int end = text.lastIndexOf("</server>");
		if (end == -1) return;
		start = StringUtil.lineStart(text, start);
		end = StringUtil.nextLine(text, end);
		text = text.substring(0,start) + servers + text.substring(end);
		FileUtil.setFileText(mirc,text);
		loadXML();
	}

	//Set an entity in the mirc.xml file and parse the result.
	private static void setEntity(String name, String value) {
		String text = FileUtil.getFileText(mirc);
		text = XmlStringUtil.setEntity(text,name,value);
		FileUtil.setFileText(mirc,text);
		loadXML();
	}

	//Load the mirc.xml XML document
	private static void loadXML() {
		try {
			mircXML = XmlUtil.getDocument(mirc);
			root = mircXML.getDocumentElement();

			//Get the timeout
			try { timeout = Integer.parseInt(XmlUtil.getValueViaPath(mircXML,"mirc@timeout")); }
			catch (Exception useDefault) { }
			timeout = ((timeout>1) && (timeout<200)) ? timeout : 10;

			//Get the proxy server parameters and load the ProxyServer object.
			String proxyIP = XmlUtil.getValueViaPath(mircXML,"mirc/proxyserver@ip").trim();
			String proxyPort = XmlUtil.getValueViaPath(mircXML,"mirc/proxyserver@port").trim();
			String proxyUsername = XmlUtil.getValueViaPath(mircXML,"mirc/proxyserver@username").trim();
			String proxyPassword = XmlUtil.getValueViaPath(mircXML,"mirc/proxyserver@password").trim();
			boolean enabled = !proxyIP.equals("") && !proxyPort.equals("");
			proxy = new ProxyServer(enabled, proxyIP, proxyPort, proxyUsername, proxyPassword);
		}
		catch (Exception ex) {
			mircXML = null;
			proxy = new ProxyServer(false, "", "", "", "");
		}
	}

	//Build the configuration of all the local services.
	//This is done to provide a DOM object for use by the
	//query service client to determine which menu options
	//can be enabled for a user, given the selection of a
	//service.
	private static void loadLocalServices() {
		try {
			localServices = XmlUtil.getDocument();
			Element lsRoot = localServices.createElement("local-services");
			localServices.appendChild(lsRoot);
			//Set the params for the server itself
			setServerParams(lsRoot);
			//Set the singleton services (query and file)
			setQueryServiceParams(lsRoot);
			setFileServiceParams(lsRoot);
			//Set the local storage services
			NodeList nodeList = root.getElementsByTagName("server");
			for (int i=0; i<nodeList.getLength(); i++) {
				Element serverElement = (Element)nodeList.item(i);
				String url = Visa.getServerURL(serverElement.getAttribute("address"));
				if (isLocal(url) && !serverElement.getAttribute("address").endsWith("/mirctest/"))
					setStorageServiceParams(lsRoot, serverElement);
			}
		}
		catch (Exception ex) {
			localServices = null;
		}
	}

	//Set the parameters for the server
	private static void setServerParams(Element lsRoot) {
		lsRoot.setAttribute("address",root.getAttribute("siteurl"));
		lsRoot.setAttribute("admin","tomcat");
	}

	//Set the parameters for the Query Service
	private static void setQueryServiceParams(Element lsRoot) {
		Element qs = lsRoot.getOwnerDocument().createElement("query");
		lsRoot.appendChild(qs);
		String text = FileUtil.getFileText(new File(webapps,"mirc/WEB-INF/web.xml"));
		String admin = XmlStringUtil.getEntity(text, "admin");
		qs.setAttribute("admin", admin);
		String user = XmlStringUtil.getEntity(text, "user");
		qs.setAttribute("user", user);
	}

	//Set the parameters for the File Service
	private static void setFileServiceParams(Element lsRoot) {
		Element fs = lsRoot.getOwnerDocument().createElement("file");
		lsRoot.appendChild(fs);
		String text = FileUtil.getFileText(new File(webapps,"file/WEB-INF/web.xml"));
		String admin = XmlStringUtil.getEntity(text, "admin");
		fs.setAttribute("admin", admin);
		String user = XmlStringUtil.getEntity(text, "user");
		fs.setAttribute("user", user);
	}

	//Set the parameters for a Storage Service
	private static void setStorageServiceParams(Element lsRoot, Element service) {
		Document ownerDoc = lsRoot.getOwnerDocument();
		Element ss = ownerDoc.createElement("storage");
		lsRoot.appendChild(ss);

		//Get the context from the address attribute of the service element
		String adrs = lsRoot.getAttribute("address");
		String svcadrs = service.getAttribute("address").substring(adrs.length());
		int k = svcadrs.indexOf("/",1);
		String context = (k != -1) ? svcadrs.substring(0,k) : "";
		ss.setAttribute("context", context);

		//Now get the roles
		String text = FileUtil.getFileText(new File(webapps,context+"/WEB-INF/web.xml"));
		String admin = XmlStringUtil.getEntity(text, "admin");
		ss.setAttribute("admin", admin);
		String author = XmlStringUtil.getEntity(text, "author");
		ss.setAttribute("author", author);
		String publisher = XmlStringUtil.getEntity(text, "publisher");
		ss.setAttribute("publisher", publisher);
		String update = XmlStringUtil.getEntity(text, "update");
		ss.setAttribute("update", update);
		String user = XmlStringUtil.getEntity(text, "user");
		ss.setAttribute("user", user);

		//Finally, put in the service name.
		Node node = service.getFirstChild();
		while (node != null) {
			Node importedNode = ownerDoc.importNode(node,true);
			ss.appendChild(importedNode);
			node = node.getNextSibling();
		}
	}

}

