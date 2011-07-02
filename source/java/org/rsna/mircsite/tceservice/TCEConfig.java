/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.tceservice;

import java.io.*;
import java.net.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.http.HttpServlet;
import org.apache.log4j.Logger;
import org.rsna.mircsite.storageservice.AdminService;
import org.rsna.mircsite.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Encapsulates the configuration parameters for a MIRC Dicom Service.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class TCEConfig {

	static final Logger logger = Logger.getLogger(TCEConfig.class);

	public static String basepath = null;
	public static String tcepath = "tce" + File.separator;
	public static String quarantine = "quarantine";
	public static String updatepath = "update" + File.separator;

	public static final String configFilename = tcepath + "tce.xml";
	public static final String templateFilename = tcepath + "template.xml";

	public static Document xml = null;

	public static String autostart = null;

	public static String serviceName = null;

	public static final String dicomStoreSCPDir = tcepath + "dicom-store-scp";
	public static final String dicomStoreDir = tcepath + "dicom-store";
	public static String dicomStoreAETitle = null;
	public static String dicomStorePort = null;
	public static String accountAutocreate = null;
	public static String accountPassword = null;
	public static String accountRoles = null;

	/**
	 * Loads the TCE Service configuration parameters from the
	 * configuration file and the servlet context.
	 * @param servletContext
	 * @return true if the parameters could be loaded, else false.
	 */
	public static synchronized boolean load(ServletContext servletContext) {
		String temp;
		try {
			//Get the trial.xml config file
			xml = XmlUtil.getDocument(servletContext.getRealPath(configFilename));

			//Get the basepath to the root directory
			basepath = servletContext.getRealPath(File.separator).trim();
			if (!basepath.endsWith(File.separator)) basepath += File.separator;

			//Get the root directory name
			temp = basepath.substring(0,basepath.length()-1);
			serviceName = temp.substring(temp.lastIndexOf(File.separator)+1);

			//Get the autostart attribute
			autostart = XmlUtil.getValueViaPath(xml,"tce-service@autostart");

			//Get the account creation parameters
			accountAutocreate = XmlUtil.getValueViaPath(xml,"tce-service@autocreate");
			accountPassword = XmlUtil.getValueViaPath(xml,"tce-service@password");
			accountRoles = XmlUtil.getValueViaPath(xml,"tce-service@roles");

			//Get the dicom store parameters.
			dicomStoreAETitle = XmlUtil.getValueViaPath(xml,"tce-service/dicom-store/ae-title").trim();
			dicomStorePort = XmlUtil.getValueViaPath(xml,"tce-service/dicom-store/port").trim();

			//We made it.
			return true;
		}
		catch (Exception e) {
			xml = null;
			logger.warn("Unable to load the configuration: ["+e.getMessage()+"]");
		}
		return false;
	}

	/**
	 * Returns a String containing the file system path to the root of
	 * the storage service, including a separator character at the end.
	 * @return the basepath.
	 */
	public static String getBasepath() {
		return basepath;
	}

	/**
	 * Returns a String containing the name of the storage service.
	 * @return the storage service name (which is the same as the name of
	 * the root directory of the storage service).
	 */
	public static String getServiceName() {
		return serviceName;
	}

	/**
	 * Get the autostart attribute of the tce-service element
	 * in the configuration file.
	 * @return the autostart attribute ("yes" or "no"; "no" if missing).
	 */
	public static String getAutostart() {
		if ((xml == null) || (autostart == null) || !autostart.equals("yes")) return "no";
		return "yes";
	}

	/**
	 * Indicates whether the TCE Service is to be started automatically
	 * when the storage service is started.
	 * @return true if the the TCE Service is to be started automatically; false otherwise.
	 */
	public static boolean autostart() {
		if ((xml == null) || (autostart == null)) return false;
		return autostart.equals("yes");
	}

	/**
	 * Get the Application Entity Title of the DICOM Storage SCP
	 * for the TCE service.
	 * @return the DICOM Storage SCP AE Title, or the empty string if missing.
	 */
	public static String getDicomStoreAETitle() {
		if (xml == null) return "";
		return dicomStoreAETitle;
	}

	/**
	 * Get the port of the DICOM Storage SCP for the TCE service.
	 * @return the DICOM Storage SCP port, or the empty string if missing.
	 */
	public static String getDicomStorePort() {
		if (xml == null) return "";
		return dicomStorePort;
	}

	/**
	 * Indicates whether the TCE service is to create accounts
	 * automatically when a document is received for an owner who does
	 * not already have an account.
	 * @return true if tceAccountAutocreate is "yes"
	 */
	public static boolean accountAutocreate() {
		return getAccountAutocreate().equals("yes");
	}

	/**
	 * Returns the account autocreate attribute of the tce-service element
	 * from the storage.xml file.
	 * @return the autocreate attribute ("yes" or "no"; "no" if missing).
	 */
	public static String getAccountAutocreate() {
		if ((xml == null) || (accountAutocreate == null)) return "no";
		if (accountAutocreate.equals("yes")) return "yes";
		return "no";
	}

	/**
	 * Returns the account default password attribute of the tce-service element
	 * from the storage.xml file.
	 * @return the account password attribute ("password" if missing).
	 */
	public static String getAccountPassword() {
		if ((xml == null) || (accountPassword == null)) return "password";
		if (accountPassword.equals("")) return "password";
		return accountPassword;
	}

	/**
	 * Returns the account roles attribute of the tce-service element
	 * from the storage.xml file.
	 * @return the account roles attribute ("" if missing).
	 */
	public static String getAccountRoles() {
		if ((xml == null) || (accountPassword == null)) return "";
		return accountRoles;
	}
}