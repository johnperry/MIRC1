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
 * Page to install a redirector in the ROOT of Tomcat.
 */
public class RedirectorPage extends InstallerHtmlPage {

	/**
	 * Class constructor; creates a page allowing the user to
	 * install a redirector in the ROOT of Tomcat to point to the MIRC
	 * site so the user doesn't have to include the servlet path in the URL.
	 */
	public RedirectorPage() {
		super();
		actionButton = new JButton("Install");
		actionButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { action(); }
		});
		JButton[] buttons = new JButton[] {actionButton};
		setButtons(buttons);
		id = "redirector";
	}

	/**
	 * Determine whether the redirector has already installed and if so,
	 * skip the page. If not, display the page and see if the user wants
	 * to install the redirector.
	 */
	public boolean activate() {
		serverRoot = new File(Installer.webapps, "ROOT");
		indexJSP = new File(serverRoot, "index.jsp");
		indexHTML = new File(serverRoot, "index.html");
		if (indexHTML.exists() && isRedirector(indexHTML)) {
			System.out.println("a redirector already exists");
			return false;
		}
		setText(notInstalledPage());
		return true;
	}

	//Determine whether a file is a redirector.
	private boolean isRedirector(File f) {
		String html = FileInstaller.getFileText(f);
		String target = "<META HTTP-EQUIV=\"Refresh\" CONTENT=\"0;url=/mirc/query\">";
		if (html.indexOf(target) >= 0) return true;
		return false;
	}

	//Backup any index files that already exist in the ROOT and install the redirector.
	private void action() {
		if (indexJSP.exists()) {
			FileInstaller.backup(indexJSP,
								 new File(Installer.backupDirectory, "ROOT-index.jsp"));
			indexJSP.delete();
		}
		if (indexHTML.exists()) {
			FileInstaller.backup(indexHTML,
								 new File(Installer.backupDirectory, "ROOT-index.html"));
			indexHTML.delete();
		}
		serverRoot.mkdirs();
		FileInstaller.resourceCopy(this, "/files/index.html", indexHTML);
		Installer.nextPage();
	}

	private JButton actionButton;
	private File serverRoot;
	private File indexJSP;
	private File indexHTML;

	//The HTML page.
	private String notInstalledPage() {
		String page = makeHeader();
		page += "<h3>Install Query Service Redirector</h3>";
		page += "The installer has determined that the welcome page (index.html) "
					+ "in the root of the server is not a redirector pointing to the "
					+ "query service. ";
		page += "The installer can install a redirector in the root of the server "
					+ "so visitors to the site will be able to use a simpler URL.";
		String siteurl = (String)Installer.queryService.get("siteurl");
		page += "<p>With a redirector, the URL will be: <b>" + siteurl + "</b>.<br>";
		page += "Without a redirector, the URL will be: <b>" + siteurl
					+ "/mirc/query</b>.</p>";
		page += "<p>If your site is just used for MIRC, it is safe to install a "
					+ "redirector. If you use the root of the server as a welcome "
					+ "page for other applications, you should not install a redirector.</p>";
		page += "<p>If you want to install a redirector, click <b>Install</b>.<br>";
		page += "If you do not want to install a redirector, click <b>Next</b>.</p>";
		return page;
	}

}

