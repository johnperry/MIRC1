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

/**
 * Install the Query Service and the File Service.
 */
public class QueryServicePage extends InstallerHtmlPage {

	/**
	 * Class constructor; set up an Install button.
	 */
	public QueryServicePage() {
		super();
		actionButton = new JButton("Install");
		actionButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { action(); }
		});
		JButton[] buttons = new JButton[] {actionButton};
		setButtons(buttons);
		id = "qs";
	}

	/**
	 * Determine whether a query service already exists. If so,
	 * skip this page. If not, display the page.
	 */
	public boolean activate() {
		String[] names = new String[] {"mirc.xml","mirc.xsl"};
		mirc =	new File(Installer.webapps, "mirc");
		webinf = new File(mirc, "WEB-INF");
		mircXMLFile = new File(mirc, "mirc.xml");
		if (mirc.exists() && FileInstaller.contentsCheck(mirc, names)) return false;
		this.setText(installPage());
		return true;
	}

	//Get the configuration from the user; then install
	//the query service and the file service.
	private void action() {
		if (!getConfiguration()) return;
		String[] excludes = null;
		File zipFile =
			FileInstaller.resourceCopy(
				this,
				"/modules/queryservice.war",
				new File(Installer.webapps, "mirc.war"));
		boolean result = FileInstaller.unpackZipFile(zipFile, mirc, excludes);
		result &= installFS();
		if (result) result = updateConfiguration();
		if (result) {
			Installer.nextPage();
			return;
		}
		setText(errorPage());
	}

	//Get the configuration parameters from the user.
	private boolean getConfiguration() {
		String sitename = JOptionPane.showInputDialog(null,
				"This is a new query service installation.\n\n" +
				"Enter the name of the MIRC site.\n" +
				"This name will appear as the heading of the query page.\n\n",
				"My MIRC Site");
		sitename = sitename.trim();
		if (sitename.equals("")) return false;
		int result =
			JOptionPane.showConfirmDialog(
				null,
				addressTypeMessage,
				"Dynamic IP Address Update",
				JOptionPane.YES_NO_OPTION);
		String port = getPort();
		String addresstype;
		String siteurl;
		if (result == JOptionPane.YES_OPTION) {
			addresstype = "dynamic";
			siteurl = "http://127.0.0.1:" + port;
		}
		else if (result == JOptionPane.NO_OPTION) {
			addresstype = "static";
			siteurl = JOptionPane.showInputDialog(
				null,
				"Enter the URL of the MIRC site, including the port.\n\n" +
				"If you aren't sure what to do, click OK.\n" +
				"The default makes the site URL your local computer.\n\n",
				"127.0.0.1:" + port);
			siteurl = siteurl.trim();
			if (siteurl.equals("")) return false;
			if (!siteurl.startsWith("http://")) siteurl = "http://" + siteurl;
		}
		else return false;
		Installer.queryService = Configurator.hashQueryService(sitename, siteurl, addresstype);
		return true;
	}

	//Update the mirc.xml configuration file.
	private boolean updateConfiguration() {
		mircXML = FileInstaller.getFileText(mircXMLFile);
		mircXML =
			Configurator.updateQueryServiceConfiguration(
				mircXML, Installer.queryService);
		FileInstaller.setFileText(mircXMLFile, mircXML);
		return true;
	}

	//Update the port number for the Tomcat server in the
	//Tomcat/conf/server.xml file.
	private String getPort() {
		String defaultPort = "8080";
		File serverXMLFile = new File(Installer.conf, "server.xml");
		String serverXML;
		if ((serverXML = FileInstaller.getFileText(serverXMLFile)) == null) return defaultPort;
		serverXML = removeComments(serverXML);
		int begin = -1;
		while ((begin = serverXML.indexOf("<Connector",begin+1)) != -1) {
			int end = serverXML.indexOf(">",begin);
			String connector = serverXML.substring(begin,end);
			if ((connector.indexOf("protocol") == -1) &&
				(connector.indexOf("sslProtocol") == -1)) {
				int p = connector.indexOf("port");
				if (p != -1) {
					p = connector.indexOf("\"",p) + 1;
					int q = connector.indexOf("\"",p);
					return connector.substring(p,q);
				}
			}
		}
		return defaultPort;
	}

	//Remove the comments in an XML string.
	private String removeComments(String xml) {
		String x = "";
		int start = 0;
		int end;
		while ((end=xml.indexOf("<!--",start)) >= 0) {
			x += xml.substring(start,end);
			start = xml.indexOf("-->",end) + 3;
			if (start == 2) return x;
		}
		return x + xml.substring(start);
	}

	//Install the file service.
	private boolean installFS() {
		String serviceName = "file";
		File service = new File(Installer.webapps, serviceName);
		File zipFile =
			FileInstaller.resourceCopy(
				this,
				"/modules/fileservice.war",
				new File(Installer.webapps, serviceName + ".war"));
		return FileInstaller.unpackZipFile(zipFile, service, null);
	}

	private JButton actionButton;
	private File	mirc;
	private File	webinf;
	private File 	mircXMLFile;
	private String 	mircXML;
	private int 	versionIndex;
	private boolean	addresstypeExisted = true;

	//The various HTML pages.

	private String addressTypeMessage =
		"Do you want your site's IP address to be dynamic?\n\n" +
		"\"Yes\" means the server will automatically obtain the IP\n" +
		"address from the system when it starts.\n" +
		"\"No\" means the IP address is defined by the configuration file.\n\n" +
		"If you want documents on your site to be accessible from other\n" +
		"computers, you should probably click \"Yes\".\n" +
		"If you are a network administrator serving documents to the\n" +
		"internet from behind a router, you should probably click \"No\".\n" +
		"If your computer has multiple NICs, you should click \"No\" and\n" +
		"find out from your network administrator what IP address to use.\n" +
		"If you just want to specify the IP address yourself, click \"No\".\n" +
		"If you aren't sure what to do, live on the edge and click \"Yes\".\n\n";

	private String installPage() {
		String page = makeHeader();
		page += "<h3>Query Service Installation</h3>";
		page += "The installer did not find an instance of a MIRC query service "
					+ "already installed.";
		page += "<p>If you want to install a query service, click <b>Install</b>.</p>";
		page += "<p>If you do not want to install a query service, click "
					+ "<b>Next</b>.</p>";
		return page + makeFooter();
	}

	private String errorPage() {
		String page = makeHeader();
		page += "<h3>Query Service Installation</h3>";
		page += "The installer was unable to install the MIRC query service or the File Cabinets. ";
		page += "<p>This indicates a serious error. Please submit a note to " +
		        "the RSNA MIRC forum site (forums.rsna.org).</p>";
		page += "<p>Click <b>Next</b> to continue.</p>";
		return page + makeFooter();
	}

}

