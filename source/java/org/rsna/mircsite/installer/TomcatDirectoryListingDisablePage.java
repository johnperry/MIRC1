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
 * Installer page to disable Tomcat directory listings.
 * This minimizes the chance that a hacker could find
 * something by roaming around the site looking at
 * the contents of the directories.
 */
public class TomcatDirectoryListingDisablePage extends InstallerHtmlPage {

	/**
	 * Class constructor; create a Disable button.
	 */
	public TomcatDirectoryListingDisablePage() {
		super();
		actionButton = new JButton("Disable");
		actionButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { action(); }
		});
		JButton[] buttons = new JButton[] {actionButton};
		setButtons(buttons);
		id = "list";
	}

	/**
	 * Determine whether directory listings are already disabled. If so,
	 * skip this page. If not, display the page.
	 */
	public boolean activate() {
		webXMLFile = new File(Installer.conf, "web.xml");
		if ((webXML = FileInstaller.getFileText(webXMLFile)).equals("")) {
			setText(notOKPage());
			actionButton.setEnabled(false);
			return true;
		}
		String target1 = "<param-name>listings</param-name>";
		String target2 = "<param-value>";
		if ( ( (begin = webXML.indexOf(target1)) < 0 ) ||
				 ( (begin = webXML.indexOf(target2,begin)) < 0 )    ) {
			setText(notOKPage());
			actionButton.setEnabled(false);
			return true;
		}
		begin = webXML.indexOf(">",begin) + 1;
		end = webXML.indexOf("<",begin);
		String value = webXML.substring(begin,end).trim();
		if (value.equals("false")) {
			actionButton.setEnabled(false);
			return false;
		}
		setText(enabledPage());
		return true;
	}

	//Update the web.xml file to disable directory listings.
	//Note that all the real work was done by the activate()
	//function; all that remains now is to change the value
	//of the parameter to false.
	private void action() {
		webXML = webXML.substring(0,begin) + "false" + webXML.substring(end);
		FileInstaller.setFileText(webXMLFile, webXML);
		setText(finishedPage());
	}

	private JButton actionButton;
	private String webXML;
	private File webXMLFile;
	private int begin, end;

	//The various HTML pages.

	private String notOKPage() {
		String page = makeHeader();
		page += "<h3>Disable Tomcat Directory Listings</h3>";
		page += "The installer could not find the directory listing enable value in the "
					+ "server's web.xml configuration file.";
		page += "<p>This indicates a problem in either the installer or "
					+ "the Tomcat/conf/web.xml file.</p>";
		page += "<p>You should just click <b>Next</b> and proceed.</p>";
		return page;
	}

	private String enabledPage() {
		String page = makeHeader();
		page += "<h3>Disable Tomcat Directory Listings</h3>";
		page += "The installer has determined that directory listings are enabled in "
					+ "the Tomcat server.";
		page += "<p>Directory listings allow users to probe your site to see "
					+ "contents that might not be visible through the web pages on the site. "
					+ "If your site contains private information, you should definitely disable "
					+ "directory listings.</p>";
		page += "<p>If your site does not contain private information and if you fancy "
					+	"yourself a hot-dog computer jock, you may find "
					+ "it more convenient to leave directory listings enabled.</p>";
		page += "<p>If you want to disable directory listings, click <b>Disable</b></p>";
		page += "<p>If you do not want to disable directory listings, click <b>Next</b>.</p>";
		return page;
	}

	private String finishedPage() {
		String page = makeHeader();
		page += "<h3>Disable Tomcat Directory Listings</h3>";
		page += "The installer has disabled directory listings.";
		page += "<p>Click <b>Next</b> to continue.</p>";
		return page;
	}

}

