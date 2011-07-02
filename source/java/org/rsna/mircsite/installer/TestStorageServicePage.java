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
 * Installer page for the test storage service
 */
public class TestStorageServicePage extends InstallerHtmlPage {

	/**
	 * Class constructor; set up an Install button.
	 */
	public TestStorageServicePage() {
		super();
		actionButton = new JButton("Install");
		actionButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { action(); }
		});
		JButton[] buttons = new JButton[] {actionButton};
		setButtons(buttons);
		id = "ts";
	}

	/**
	 * Figure out whether the test storage service is already installed,
	 * and if so, change the name of the Install button to Remove; then
	 * display the page.
	 */
	public boolean activate() {
		if (Installer.queryService == null) return false;
		String[] names = new String[] {"storage.xml", "MIRCindex.xsl"};
		service = new File(Installer.webapps, serviceName);
		if (service.exists() && service.isDirectory()) {
			this.setText(upgradePage());
			actionButton.setText("Remove");
			install = false;
		}
		else {
			this.setText(installPage());
			install = true;
		}
		return true;
	}

	//Install the or remove the test storage service.
	private void action() {
		if (install) {
			String resource = "/modules/" + serviceName + ".war";
			File zipFile =
					FileInstaller.resourceCopy(
							this,
							resource,
							new File(Installer.webapps, serviceName + ".war"));
			boolean result =
					FileInstaller.unpackZipFile(
							zipFile,
							service,
							null);
			if (result) result = updateConfiguration(true);
			if (result) Installer.nextPage();
			else this.setText(errorPage());
		}
		else {
			updateConfiguration(false);
			Installer.nextPage();
		}
	}

	//Update the query service's mirc.xml configuration file.
	//Add a server element if the test storage service is being installed,
	//or remove the existing server element if the test storage service
	//is being removed.
	private boolean updateConfiguration(boolean addServerElement) {
		File mirc = new File(Installer.webapps, "mirc");
		File mircXMLFile = new File(mirc, "mirc.xml");
		String mircXML = FileInstaller.getFileText(mircXMLFile);
		if (addServerElement) {
			mircXML = Configurator.addStorageServiceElement(
				mircXML, "&siteurl;/" + serviceName + "/", "&sitename; - TEST");
		}
		else {
			mircXML = Configurator.removeTestStorageServiceElement(mircXML);
			File temp = new File(Installer.webapps, serviceName);
			FileInstaller.deleteAll(temp);
			temp = new File(Installer.webapps, serviceName + ".war");
			temp.delete();
		}
		FileInstaller.setFileText(mircXMLFile, mircXML);
		return true;
	}

	private boolean install = true;
	private JButton actionButton;
	String serviceName = "mirctest";
	File service;

	//The various HTML pages.

	private String installPage() {
		String page = makeHeader();
		page += "<h3>Test Storage Service Installation</h3>";
		page += "The installer did not find an instance of the MIRC test storage service.";
		page += "<p>A test storage service is not necessary for the operation of a MIRC "
					+ "site, but it can be useful for verifying an initial installation.</p>";
		page += "<p>If you want to install the test storage service, click <b>Install</b>.</p>";
		page += "<p>If you do not want to install the test storage service, click "
					+ "<b>Next</b>.</p>";
		return page;
	}

	private String upgradePage() {
		String page = makeHeader();
		page += "<h3>Test Storage Service Installation</h3>";
		page += "The installer has found an instance of the MIRC test storage service.";
		page += "<p>If you want to leave the current version in place, click <b>Next</b>.</p>";
		page += "<p>If you want to remove the test storage service, click <b>Remove</b>.</p>";
		return page;
	}

	private String errorPage() {
		String page = makeHeader();
		page += "<h3>Test Storage Service Installation</h3>";
		page += "The installer was unable to install the MIRC test storage service.";
		page += "<p>Click <b>Next</b> to continue.</p>";
		return page;
	}

}

