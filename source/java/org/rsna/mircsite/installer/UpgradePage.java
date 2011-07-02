/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.installer;

import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

/**
 * Installer page to perform an upgrade. This page also installs the
 * library jars even if this installation is not an upgrade. This
 * ensures that the jars are refreshed no matter what.
 */
public class UpgradePage extends InstallerHtmlPage {

	/**
	 * Class constructor; create an Enable button.
	 */
	public UpgradePage() {
		super();
		actionButton = new JButton("Upgrade");
		actionButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { action(); }
		});
		JButton[] buttons = new JButton[] {actionButton};
		setButtons(buttons);
		id = "upgrade";
	}

	/**
	 * First, check whether Tomcat is running and abort if it is.
	 * Next, install the libraries and expand the database war file
	 * if appropriate. Then determine whether there is a MIRC
	 * site already installed. If not, skip this page. If so, get the key
	 * parameters from the existing configuration and display the page.
	 */
	public boolean activate() {
		checkTomcat();
		String[] names = new String[] {"mirc.xml","mirc.xsl"};
		File mirc = new File(Installer.webapps, "mirc");
		File mircXMLFile = new File(mirc, "mirc.xml");
		boolean result = false;
		if (mirc.exists() && FileInstaller.contentsCheck(mirc,names)) {
			mircXML = FileInstaller.getFileText(mircXMLFile);
			Installer.installedVersion = Configurator.getQueryServiceVersion(mircXML);
			boolean pageElements = Configurator.checkForRSNASite(mirc) || Configurator.since("T30");
			Installer.queryService = Configurator.hashQueryService(mircXML,pageElements);
			//Get the addresstype entity.
			String addresstype = (String)Installer.queryService.get("addresstype");
			if ((addresstype == null) || (!addresstype.equals("dynamic") && !addresstype.equals("static"))) {
				addresstype = "static";
			}
			Installer.queryService.put("addresstype",addresstype);
			setText(upgradePage(Installer.installedVersion));
			deleteRadLexIndex();
			result = true;
		}
		FileInstaller.installLibraries(this); //Install the libraries no matter what,
		upgradeMA(); 						  //and install the mircadmin war no matter what.
		return result;
	}

	//Check that Tomcat is not running.
	//Display a message and exit if it is.
	private void checkTomcat() {
		File server = new File(Installer.conf, "server.xml");
		try {
			TomcatChecker checker = new TomcatChecker(server);
			String response = checker.connect();
			JOptionPane.showMessageDialog(
				this,
				"Tomcat is running.\n" +
				"Stop Tomcat and run the installer again.");
			System.exit(0);
		}
		catch (Exception ex) { }
	}

	//Upgrade all the webapps on the site and display the results.
	private void action() {
		System.out.println("upgrading the services.");
		boolean qsResult = upgradeQS();
		boolean fsResult = upgradeFS();
		boolean ssResult = upgradeSS();
		boolean tsResult = upgradeTS();
		boolean maResult = upgradeMA();
		boolean result = qsResult && fsResult && ssResult && tsResult && maResult;
		System.out.println("finished upgrading the services " + (result ? "[OK]" : "[FAILED]"));
		if (result) Installer.nextPage();
		setText(errorPage(qsResult,fsResult,ssResult,tsResult,maResult));
	}

	private JButton actionButton;
	private File 	mircXMLFile;
	private String 	mircXML;

	//The various HTML pages

	private String upgradePage(String oldVersion) {
		String page = makeHeader();
		page += "<h3>MIRC Site Upgrade</h3>";
		page += "The installer has found an existing MIRC site "
					+ "(Release " + oldVersion + ") on this Tomcat instance.";
		page += "<p>If you want to upgrade this version, click <b>Upgrade</b>.</p>";
		page += "<p>If you want to leave the current version in place, click "
					+ "<b>Next</b>.</p>";
		return page + makeFooter();
	}

	private String errorPage(boolean qsResult, boolean fsResult, boolean ssResult, boolean tsResult, boolean lvResult) {
		String page = makeHeader();
		page += "<h3>MIRC Site Upgrade</h3>";
		page += "The installer encountered errors upgrading the MIRC site.";
		if (qsResult) page += "<p>The query service upgrade was successful.</p>";
		else page += "<p>The query service upgrade failed.</p>";
		if (fsResult) page += "<p>The file service upgrade was successful.</p>";
		else page += "<p>The file service upgrade failed.</p>";
		if (ssResult) page += "<p>All of the storage service upgrades were successful.</p>";
		else page += "<p>At least one storage service upgrade failed.</p>";
		if (tsResult) page += "<p>The test service upgrade was successful.</p>";
		else page += "<p>The test service upgrade failed.</p>";
		if (lvResult) page += "<p>The MIRC Admin upgrade was successful.</p>";
		else page += "<p>The MIRC Admin upgrade failed.</p>";
		page += "<p>To continue, click <b>Next</b>.</p>";
		return page + makeFooter();
	}

	//Upgrade the query service
	private boolean upgradeQS() {
		File mirc = new File(Installer.webapps, "mirc");
		File webinf = new File(mirc, "WEB-INF");
		String[] excludes = new String[] {
				"MIRCheaderbackground.jpg",
				"news" + File.separator + "news.rss"};
		for (int i=0; i<excludes.length; i++) {
			File file = new File(mirc, excludes[i]);
			if (!file.exists()) excludes[i] = "";
		}
		if (!excludes[0].equals("") && Configurator.since("T30")) excludes[0] = "";
		FileInstaller.backup(
			new File(mirc, "mirc.xml"),
			new File(Installer.backupDirectory, "mirc-mirc.xml"));
		FileInstaller.backup(
			new File(mirc, "enumerated-values.xml"),
			new File(Installer.backupDirectory, "mirc-enumerated-values.xml"));
		FileInstaller.backup(
			new File(webinf, "web.xml"),
			new File(Installer.backupDirectory, "mirc-web.xml"));
		//Make sure all the old classes and libraries are removed.
		FileInstaller.deleteAll(webinf);
		deleteRadLexIndex();
		File zipFile =
			FileInstaller.resourceCopy(
				this,
				"/modules/queryservice.war",
				new File(Installer.webapps, "mirc.war"));
		boolean result = FileInstaller.unpackZipFile(zipFile, mirc, excludes);
		if (result) {
			File mircXMLFile = new File(mirc, "mirc.xml");
			String mircXML = FileInstaller.getFileText(mircXMLFile);
			mircXML = Configurator.updateQueryServiceConfiguration(mircXML, Installer.queryService);
			FileInstaller.setFileText(mircXMLFile, mircXML);
		}
		return result;
	}

	//Delete the RadLexIndex database
	private void deleteRadLexIndex() {
		File mirc = new File(Installer.webapps, "mirc");
		(new File(mirc, "RadLexIndex.db")).delete();
		(new File(mirc, "RadLexIndex.lg")).delete();
	}

	//Upgrade the file service.
	private boolean upgradeFS() {
		String serviceName = "file";
		File service = new File(Installer.webapps, serviceName);
		//Back up the files to be overwritten
		FileInstaller.backup(new File(service, "fileservice.xml"),
							 new File(Installer.backupDirectory, serviceName + "-fileservice.xml"));
		FileInstaller.backup(new File(service, "dicom-anonymizer.properties"),
							 new File(Installer.backupDirectory, serviceName + "-dicom-anonymizer.properties"));
		FileInstaller.backup(new File(service, "xml-anonymizer.script"),
							 new File(Installer.backupDirectory, serviceName + "-xml-anonymizer.script"));
		//Get the war file
		File zipFile =
			FileInstaller.resourceCopy(
				this,
				"/modules/fileservice.war",
				new File(Installer.webapps, serviceName + ".war"));
		//Install it
		String[] excludes =
			new String[] {
				"fileservice.xml",
				"dicom-anonymizer.properties"};
		for (int k=0; k<excludes.length; k++) {
			File check = new File(service, excludes[k]);
			if (!check.exists()) excludes[k] = "";
		}
		return FileInstaller.unpackZipFile(zipFile, service, excludes);
	}

	//Upgrade the test storage service if it is present.
	private boolean upgradeTS() {
		String serviceName = "mirctest";
		File service = new File(Installer.webapps, serviceName);
		//If the service isn't present return true.
		if (!service.exists() || !service.isDirectory()) return true;
		File webinf = new File(service, "WEB-INF");
		//Make sure all the old classes and libraries are removed.
		FileInstaller.deleteAll(webinf);
		String resource = "/modules/" + serviceName + ".war";
		File zipFile =
			FileInstaller.resourceCopy(
					this,
					resource,
					new File(Installer.webapps, serviceName + ".war"));
		String[] excludes = null;
		boolean result = FileInstaller.unpackZipFile(zipFile, service, excludes);
		if (result) {
			File mirc = new File(Installer.webapps, "mirc");
			File mircXMLFile = new File(mirc, "mirc.xml");
			String mircXML = FileInstaller.getFileText(mircXMLFile);
			//Remove the old test storage service element.
			//This will remove the old version of the URL
			mircXML = Configurator.removeTestStorageServiceElement(mircXML);
			//Now add the test storage service element,
			//with the new URL compatible with Tomcat 5.
			mircXML = Configurator.addStorageServiceElement(mircXML, "&siteurl;/mirctest/", "&sitename;-TEST");
			FileInstaller.setFileText(mircXMLFile, mircXML);
		}
		return result;
	}

	//Upgrade the mircadmin service.
	private boolean upgradeMA() {
		String serviceName = "mircadmin";
		File service = new File(Installer.webapps, serviceName);
		File webinf = new File(service, "WEB-INF");
		//Make sure all the old classes and libraries are removed.
		FileInstaller.deleteAll(webinf);
		String resource = "/modules/" + serviceName + ".war";
		File zipFile =
			FileInstaller.resourceCopy(
				this,
				resource,
				new File(Installer.webapps, serviceName + ".war"));
		String[] excludes = null;
		boolean result = FileInstaller.unpackZipFile(zipFile, service, excludes);
		return result;
	}

	//Upgrade all the storage services.
	private boolean upgradeSS() {
		if (Installer.queryService == null) return true;
		String[] includeNames = new String[] {"storage.xml","MIRCdocument.xsl"};
		File[] files = Installer.webapps.listFiles();
		boolean result = true;
		for (int i=0; i<files.length; i++) {
			if (files[i].isDirectory() && FileInstaller.contentsCheck(files[i],includeNames)) {
				result &= upgradeSS(files[i]);
			}
		}
		return result;
	}

	//Upgrade a single storage service.
	private boolean upgradeSS(File service) {
		String serviceName = service.getName();
		File trial = new File(service, "trial");
		File tce = new File(service, "tce");
		File storageXMLFile = new File(service, "storage.xml");
		String storageXML = FileInstaller.getFileText(storageXMLFile);
		Hashtable<String,String>storageService = Configurator.hashStorageService(storageXML);

		checkStorageServiceForDamage(serviceName, storageService);

		String siteurl = (String)Installer.queryService.get("siteurl");
		if (siteurl != null) storageService.put("siteurl",siteurl);

		FileInstaller.backup(new File(service, "storage.xml"),
							 new File(Installer.backupDirectory, serviceName + "-storage.xml"));
		FileInstaller.backup(new File(trial,  "trial.xml"),
							 new File(Installer.backupDirectory, serviceName + "-trial.xml"));
		FileInstaller.backup(new File(trial,  "template.xml"),
							 new File(Installer.backupDirectory, serviceName + "-trial-template.xml"));
		FileInstaller.backup(new File(trial,  "dicom-anonymizer.properties"),
							 new File(Installer.backupDirectory, serviceName + "-dicom-anonymizer.properties"));
		FileInstaller.backup(new File(trial,  "xml-anonymizer.properties"),
							 new File(Installer.backupDirectory, serviceName + "-xml-anonymizer.properties"));
		FileInstaller.backup(new File(tce,  "tce.xml"),
							 new File(Installer.backupDirectory, serviceName + "-tce.xml"));
		FileInstaller.backup(new File(tce,  "template.xml"),
							 new File(Installer.backupDirectory, serviceName + "-tce-template.xml"));

		File webinf = new File(service, "WEB-INF");
		File webXMLFile = new File(webinf, "web.xml");
		String webXML = FileInstaller.getFileText(webXMLFile);
		Hashtable<String,String>webXMLHashtable = Configurator.hashWebXML(webXML);
		FileInstaller.backup(webXMLFile,
							 new File(Installer.backupDirectory, serviceName + "-web.xml"));

		//Make sure all the old classes and libraries are removed.
		FileInstaller.deleteAll(webinf);

		//And remove the DICOM viewer
		FileInstaller.deleteAll(new File(service, "dicomviewer"));

		//Get rid of the old GIF buttons
		File buttonsDir = new File(service, "buttons");
		if (buttonsDir.exists()) {
			File[] buttons = buttonsDir.listFiles();
			for (int i=0; i<buttons.length; i++) {
				if (buttons[i].getName().toLowerCase().endsWith(".gif"))
					buttons[i].delete();
			}
		}

		//Get rid of the old Util.js file so the new util.js file will get the right name.
		File utiljs = new File(service, "Util.js");
		utiljs.delete();

		//Set up the excludes
		String[] excludes;
		excludes = new String[] {
				"trial" + File.separator + "trial.xml",
				"trial" + File.separator + "template.xml",
				"tce" + File.separator + "tce.xml",
				"tce" + File.separator + "template.xml",
				"trial" + File.separator + "dicom-anonymizer.properties"};
		File check;
		for (int k=0; k<excludes.length; k++) {
			check = new File(service, excludes[k]);
			if (!check.exists()) excludes[k] = "";
		}
		String resource = "/modules/storageservice.war";
		File zipFile =
			FileInstaller.resourceCopy(
				this,
				resource,
				new File(Installer.webapps, serviceName + ".war"));
		boolean result = FileInstaller.unpackZipFile(zipFile, service, excludes);
		if (result) {
			webXML = FileInstaller.getFileText(webXMLFile);
			webXML = Configurator.updateWebXML(webXML, webXMLHashtable);
			FileInstaller.setFileText(webXMLFile, webXML);
			storageXML = FileInstaller.getFileText(storageXMLFile);
			storageXML = Configurator.updateStorageServiceConfiguration(storageXML, storageService);
			FileInstaller.setFileText(storageXMLFile, storageXML);
		}
		return result;
	}

	//See if the storage service was damaged by a previous
	//version of the installer in an upgrade, and if so, fix it.
	private void checkStorageServiceForDamage(String serviceName, Hashtable<String,String>storageService) {
		String servletname = (String)storageService.get("servletname");
		if (!servletname.equals(serviceName)) {
			storageService.put("servletname",serviceName);
			String sitename = (String)storageService.get("sitename");
			sitename = JOptionPane.showInputDialog(null,
					"The " + serviceName + " storage service appears to have \n" +
					"been damaged during a previous upgrade. The installer will \n" +
					"now correct the problem.\n\n" +
					"Enter the name of the storage service. This name will\n" +
					"appear in the list of storage services on the query page.\n\n",
					sitename);
			sitename = sitename.trim();
			storageService.put("sitename",sitename);
			String tagline = (String)storageService.get("tagline");
			tagline = JOptionPane.showInputDialog(null,
					"Enter a one-line site description to be shown on the query results\n" +
					"page under the site name and above the query results from your site.\n\n",
					tagline);
			tagline = tagline.trim();
			storageService.put("tagline",tagline);
			int result = JOptionPane.showConfirmDialog(null,
					"Do you want documents submitted to this site to be indexed automatically?\n\n" +
					"If you answer \"yes\", they will be indexed and made available on your\n" +
					"site immediately upon receipt.\n\n" +
					"If you answer \"no\", they will be inserted into the input queue for \n" +
					"consideration by the site administrator before they will be available\n" +
					"on your site.\n\n",
					"Automatic Indexing for Submitted Documents",
					JOptionPane.YES_NO_OPTION);
			String autoindex;
			if (result == JOptionPane.YES_OPTION) autoindex = "yes";
			else autoindex = "no";
			storageService.put("autoindex",autoindex);
		}
	}

}
