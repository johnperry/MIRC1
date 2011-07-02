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
 * Page to install a new storage service.
 */
public class StorageServicePage extends InstallerHtmlPage {

	/**
	 * Class constructor; set up an Install button.
	 */
	public StorageServicePage() {
		super();
		actionButton = new JButton("Install");
		actionButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { action(); }
		});
		JButton[] buttons = new JButton[] {actionButton};
		setButtons(buttons);
		id = "ss";
	}

	/**
	 * Display the page.
	 */
	public boolean activate() {
		if (Installer.queryService == null) return false;
		actionButton.setEnabled(true);
		setText(newStorageServicePage());
		return true;
	}

	//Install a new storage service.
	private void action() {
		serviceName = JOptionPane.showInputDialog(null,
				"Enter a one-word name for the storage service servlet.",
				"Storage Service Servlet Name",JOptionPane.QUESTION_MESSAGE);
		serviceName = serviceName.trim().replaceAll("\\W","");
		service = new File(Installer.webapps, serviceName);
		if (!service.exists()) {
			if (getConfiguration(serviceName)) {
				File zipFile =
						FileInstaller.resourceCopy(
								this,
								resource,
								new File(Installer.webapps, serviceName + ".war"));
				boolean result = FileInstaller.unpackZipFile(zipFile, service, null);
				if (result) result = updateConfiguration();
				if (result)
					this.setText(
						finishedPage(
							"The installer successfully installed the <b>"
							+ serviceName + "</b> storage service."));
				else
					this.setText(
						finishedPage(
							"The installer failed to install the <b>"
							+ serviceName + "</b> storage service."));
			}
		}
		else this.setText(
			finishedPage(
				"A service with the name \"<b>"
				+ serviceName + "</b>\" already exists."));
	}

	//Get the configuration desired for this storage service installation.
	private boolean getConfiguration(String servletname) {
		siteName = JOptionPane.showInputDialog(null,
				"Enter the name of the storage service. This name will\n" +
				"appear in the list of storage services on the query page.",
				"A MIRC Storage Service");
		siteName = siteName.trim();
		if (siteName.equals("")) return false;
		rolePrefix = JOptionPane.showInputDialog(null,
				"Enter a two- or three-character designator for the user,\n" +
				"author, and admin roles on this storage service.\n\n" +
				"If you want to control access to this storage service\n" +
				"separately from access to other storage services, choose\n" +
				"a unique designator.\n\n" +
				"The most flexible approach is to choose a unique designator,\n" +
				"but if you don't want to be bothered with such subtleties and\n" +
				"you want all users to be able to access all storage services,\n" +
				"use the same designator for all storage services. In such\n" +
				"cases, the standard designator is SS.\n\n",
				"");
		rolePrefix = rolePrefix.replaceAll("[\\s+\\-]","").toUpperCase();
		if (rolePrefix.equals("")) rolePrefix = "SS";
		String tagline = JOptionPane.showInputDialog(null,
				"Enter a one-line site description to be shown on the query results\n" +
				"page under the site name and above the query results from your site.\n" +
				"If you don't want a description to be shown, leave the line blank.",
				"");
		tagline = tagline.trim();
		int result = JOptionPane.showConfirmDialog(null,
				"Do you want documents submitted to this site to be indexed automatically?\n\n" +
				"If you answer \"yes\", they will be indexed and made available on your\n" +
				"site immediately upon receipt.\n\n" +
				"If you answer \"no\", they will be inserted into the input queue for \n" +
				"consideration by the site administrator before they will be available\n" +
				"on your site.\n",
				"Automatic Indexing for Submitted Documents",
				JOptionPane.YES_NO_OPTION);
		String autoindex;
		if (result == JOptionPane.YES_OPTION) autoindex = "yes";
		else if (result == JOptionPane.NO_OPTION) autoindex = "no";
		else return false;
		storageService = Configurator.hashStorageService(siteName, servletname, tagline, autoindex);
		String siteurl = Installer.queryService.get("siteurl");
		if (siteurl != null) storageService.put("siteurl", siteurl);
		return true;
	}

	//Update the configuration files for the storage service and the query service.
	private boolean updateConfiguration() {

		//Configure the storage.xml file for the storage service.
		File storageXMLFile = new File(service, "storage.xml");
		String storageXML = FileInstaller.getFileText(storageXMLFile);
		storageXML = Configurator.updateStorageServiceConfiguration(storageXML, storageService);
		FileInstaller.setFileText(storageXMLFile, storageXML);

		//Configure the web.xml file for the storage service.
		File webinf = new File(service, "WEB-INF");
		File webXMLFile = new File(webinf, "web.xml");
		String webXML = FileInstaller.getFileText(webXMLFile);
		webXML = Configurator.setRolePrefix(webXML, rolePrefix);
		FileInstaller.setFileText(webXMLFile, webXML);

		//Configure the mirc.xml file for the query service.
		File mirc = new File(Installer.webapps, "mirc");
		File mircXMLFile = new File(mirc, "mirc.xml");
		String mircXML = FileInstaller.getFileText(mircXMLFile);
		mircXML = Configurator.addStorageServiceElement(
			mircXML, "&siteurl;/"+serviceName+"/service", siteName);
		FileInstaller.setFileText(mircXMLFile, mircXML);

		return true;
	}

	private String resource = "/modules/storageservice.war";
	private JButton actionButton;
	private File service;
	private String serviceName;
	private String siteName;
	private String rolePrefix;
	private boolean security;
	private Hashtable<String,String> storageService;

	//The various HTML pages.

	private String newStorageServicePage() {
		String page = makeHeader();
		page += "<h3>Storage Service Installation</h3>";
		page += "Storage Services store and distribute your MIRC documents. " +
						"Each site needs at least one storage service. Most sites need " +
						"only one.";
		page += "<p>Note: As of Release T21, the standard storage service and the " +
						"clinical trial service have been combined. All storage services now " +
						"include DICOM services. " +
						"If you want to install a storage service for a clinical trial, use " +
						"this page.</p>";
		page += "<p>If this is an initial installation or if you just want to add another " +
						"storage service to your site, click <b>Install</b>.</p>";
		page += "<p>If you do not want to add another storage service, click <b>Next</b>.</p>";
		return page;
	}

	private String finishedPage(String result) {
		String page = makeHeader();
		page += "<h3>Install New Storage Services</h3>";
		page += result;
		page += "<p>If you want to add another storage service to your site, click "
					+ "<b>Install</b></p>";
		page += "<p>If you do not want to install additional storage services, click "
					+ "<b>Next</b>.</p>";
		return page;
	}

}

