/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.dicomservice;

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
public class TrialConfig {

	static final Logger logger = Logger.getLogger(TrialConfig.class);

	public static String basepath = null;
	public static String trialpath = "trial" + File.separator;
	public static String quarantine = "quarantine";
	public static String updatepath = "update" + File.separator;

	public static final String configFilename = trialpath + "trial.xml";
	public static final String dicomAnonymizerFilename = trialpath + "dicom-anonymizer.properties";
	public static final String lookupTableFilename = trialpath + "lookup-table.properties";
	public static final String xmlAnonymizerFilename = trialpath + "xml-anonymizer.script";
	public static final String templateFilename = trialpath + "template.xml";
	public static final String idtableFilename = trialpath + "idtable.properties";

	public static Document xml = null;

	public static String autostart = null;
	public static String log = null;
	public static String overwrite = null;
	public static final String logDirectory = trialpath + "logs";

	public static String serviceName = null;

	public static final String httpStoreDir = trialpath + "http-store";
	public static final String httpImportDir = trialpath + "http-import";
	public static String[] httpImportIPAddresses = null;
	public static String httpImportAnonymize = null;

	public static final String httpExportDir = trialpath + "http-export";
	public static File[] httpExportDirectoryFiles = null;
	public static String[] httpExportDirectories = null;
	public static String[] httpExportURLs = null;

	public static String dicomExportMode = null;;
	public static final String dicomExportDir = trialpath + "dicom-export";
	public static File[] dicomExportDirectoryFiles = null;
	public static String[] dicomExportDirectories = null;
	public static String[] dicomExportIPAddresses = null;
	public static String[] dicomExportAETitles = null;

	public static final String dicomStoreDir = trialpath + "dicom-store";
	public static final String dicomImportDir = trialpath + "dicom-import";
	public static String dicomStoreAETitle = null;
	public static String dicomStorePort = null;
	public static String dicomImportAnonymize = null;

	public static String databaseExportMode = null;
	public static String databaseExportAnonymize = null;
	public static final String databaseExportDir = trialpath + "database-export";
	public static String databaseExportInterval = null;
	public static String databaseClassName = null;

	public static String preprocessorEnabled = null;
	public static String preprocessorClassName = null;

	public static String keyfile = null;
	public static String basedate = null;
	public static String uidroot = null;
	public static String ptIdPrefix = null;
	public static String ptIdSuffix = null;
	public static int firstPtId = 1;
	public static int ptIdWidth = 4;

	/**
	 * Loads the DICOM Service configuration parameters from the
	 * trial.xml file and the servlet context.
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

			//Get the top-level attributes
			autostart = XmlUtil.getValueViaPath(xml,"clinical-trial@autostart");
			log = XmlUtil.getValueViaPath(xml,"clinical-trial@log");
			overwrite = XmlUtil.getValueViaPath(xml,"clinical-trial@overwrite");

			//Get the preprocessor parameters.
			preprocessorEnabled = XmlUtil.getValueViaPath(xml,"clinical-trial/http-import/preprocessor@enabled");
			preprocessorClassName = XmlUtil.getValueViaPath(xml,"clinical-trial/http-import/preprocessor/preprocessor-class-name");
			//Get the http import anonymizer enabled parameter
			httpImportAnonymize = XmlUtil.getValueViaPath(xml,"clinical-trial/http-import/anonymize");
			//Get the http import IP addresses
			NodeList list = xml.getElementsByTagName("http-import");
			if (list.getLength() == 0) {
				httpImportIPAddresses = new String[0];
			}
			else {
				Element httpImportNode = (Element)list.item(0);
				list = httpImportNode.getElementsByTagName("site");
				httpImportIPAddresses = new String[list.getLength()];
				for (int i=0; i<list.getLength(); i++) {
					httpImportIPAddresses[i] = XmlUtil.getElementValue(list.item(i));
				}
			}

			//Get the http export directories and URLs
			Element httpExportNode = XmlUtil.getElementViaPath(xml,"clinical-trial/http-export");
			if (httpExportNode != null) {
				NodeList siteNodes = httpExportNode.getElementsByTagName("site");
				httpExportDirectories = new String[siteNodes.getLength()];
				httpExportDirectoryFiles = new File[siteNodes.getLength()];
				httpExportURLs = new String[siteNodes.getLength()];
				for (int i=0; i<httpExportDirectories.length; i++) {
					httpExportDirectories[i] = httpExportDir + File.separator +
									((Element)siteNodes.item(i)).getAttribute("directory");
					httpExportDirectoryFiles[i] = new File(basepath + httpExportDirectories[i]);
					httpExportURLs[i] = XmlUtil.getElementValue(siteNodes.item(i));
				}
			}
			else {
				httpExportDirectories = new String[0];
				httpExportURLs = new String[0];
			}

			//Get the dicom export mode, directories, IP addresses and AE Titles
			Element dicomExportNode = XmlUtil.getElementViaPath(xml,"clinical-trial/dicom-export");
			if (dicomExportNode != null) {
				Element storeNode;
				dicomExportMode = dicomExportNode.getAttribute("mode");
				NodeList storeNodes = dicomExportNode.getElementsByTagName("destination-dicom-store");
				dicomExportDirectories = new String[storeNodes.getLength()];
				dicomExportDirectoryFiles = new File[storeNodes.getLength()];
				dicomExportAETitles = new String[storeNodes.getLength()];
				dicomExportIPAddresses = new String[storeNodes.getLength()];
				for (int i=0; i<storeNodes.getLength(); i++) {
					storeNode = (Element)storeNodes.item(i);
					dicomExportDirectories[i] = dicomExportDir + File.separator + storeNode.getAttribute("directory");
					dicomExportDirectoryFiles[i] = new File(basepath + dicomExportDirectories[i]);
					dicomExportAETitles[i] = XmlUtil.getValueViaPath(storeNode,"destination-dicom-store/ae-title");
					dicomExportIPAddresses[i] = XmlUtil.getValueViaPath(storeNode,"destination-dicom-store/ip-address");
				}
			}
			else {
				dicomExportDirectoryFiles = new File[0];
				dicomExportAETitles = new String[0];
				dicomExportIPAddresses = new String[0];
			}

			//Get the dicom store parameters.
			dicomImportAnonymize = XmlUtil.getValueViaPath(xml,"clinical-trial/dicom-store/anonymize");
			//Detect a change in the ae-title or port and notify the author service.
			boolean change = false;
			temp = XmlUtil.getValueViaPath(xml,"clinical-trial/dicom-store/ae-title").trim();
			if ((dicomStoreAETitle != null) && !temp.equals(dicomStoreAETitle)) change = true;
			dicomStoreAETitle = temp;
			temp = XmlUtil.getValueViaPath(xml,"clinical-trial/dicom-store/port").trim();
			if ((dicomStorePort != null) && !temp.equals(dicomStorePort)) change = true;
			dicomStorePort = temp;
			if (change) AdminService.scpParamsChanged();

			//Get the database export parameters.
			databaseExportMode = XmlUtil.getValueViaPath(xml,"clinical-trial/database-export@mode");
			databaseExportAnonymize = XmlUtil.getValueViaPath(xml,"clinical-trial/database-export/anonymize");
			databaseExportInterval = XmlUtil.getValueViaPath(xml,"clinical-trial/database-export/interval");
			databaseClassName = XmlUtil.getValueViaPath(xml,"clinical-trial/database-export/database-class-name");

			//Get the remapper parameters
			keyfile = XmlUtil.getValueViaPath(xml,"clinical-trial/remapper@key-file");
			basedate = XmlUtil.getValueViaPath(xml,"clinical-trial/remapper/base-date");
			uidroot = XmlUtil.getValueViaPath(xml,"clinical-trial/remapper/uid-root");
			ptIdPrefix = XmlUtil.getValueViaPath(xml,"clinical-trial/remapper/patient-id/prefix");
			ptIdSuffix = XmlUtil.getValueViaPath(xml,"clinical-trial/remapper/patient-id/suffix");

			String s = XmlUtil.getValueViaPath(xml,"clinical-trial/remapper/patient-id@first");
			try { firstPtId = Integer.parseInt(s); }
			catch (Exception ex) { firstPtId = 1; }

			s = XmlUtil.getValueViaPath(xml,"clinical-trial/remapper/patient-id@width");
			try { ptIdWidth = Integer.parseInt(s); }
			catch (Exception ex) { ptIdWidth = 4; }

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
	 * Get the autostart attribute of the clinical-trial element
	 * in the trial.xml file.
	 * @return the autostart attribute ("yes" or "no"; "no" if missing).
	 */
	public static String getAutostart() {
		if ((xml == null) || (autostart == null) || !autostart.equals("yes")) return "no";
		return "yes";
	}

	/**
	 * Indicates whether the DICOM Service is to be started automatically
	 * when the storage service is started.
	 * @return true if the the DICOM Service is to be started automatically; false otherwise.
	 */
	public static boolean autostart() {
		if ((xml == null) || (autostart == null)) return false;
		return autostart.equals("yes");
	}


	/**
	 * Get the log attribute of the clinical-trial element
	 * in the trial.xml file. This attribute indicates whether
	 * identifying elements from received DICOM objects are to
	 * entered into the csv log for the trial.
	 * @return the log attribute ("yes" or "no"; "no" if missing).
	 */
	public static String getLog() {
		if ((xml == null) || (log == null) || !log.equals("yes")) return "no";
		return "yes";
	}

	/**
	 * Indicates whether identifying elements from received
	 * DICOM objects are to entered into the csv log for the trial.
	 * @return true if DICOM object logging is enabled; false otherwise.
	 */
	public static boolean log() {
		if ((xml == null) || (log == null)) return false;
		return log.equals("yes");
	}

	/**
	 * Get the overwrite attribute of the clinical-trial element
	 * in the trial.xml file. This attribute indicates whether
	 * received DICOM objects are to be allowed to overwrite
	 * DICOM objects with the same SOPInstanceUIDs in the MIRCdocument.
	 * @return the overwrite attribute ("yes" or "no"; "yes" if missing).
	 */
	public static String getOverwrite() {
		if ((xml != null) && (overwrite != null) && overwrite.equals("no")) return "no";
		return "yes";
	}

	/**
	 * Indicates whether received DICOM objects are to be allowed to overwrite
	 * DICOM objects with the same SOPInstanceUIDs in the MIRCdocument.
	 * @return true if overwriting is enabled; false otherwise.
	 */
	public static boolean allowOverwrite() {
		if ((xml != null) && (overwrite != null) && overwrite.equals("no")) return false;
		return true;
	}

	/**
	 * Indicate whether an IP address string is listed in the
	 * httpImportIPAddresses array. If the array includes an
	 * entry containing "*", then all IP addresses are accepted;
	 * otherwise, an IP address string must match one of the
	 * array values. This is taken to indicate that an IP address
	 * belongs to one of the participants in the trial.
	 * @param ip the IP address to be checked to see if it belongs
	 * to a clinical trial participant.
	 * @return true if the IP address belongs to a trial participant;
	 * false otherwise.
	 */
	public static boolean hasParticipant(String ip) {
		if (xml == null) return false;
		for (int i=0; i<httpImportIPAddresses.length; i++) {
			if (httpImportIPAddresses[i].equals("*")) return true;
			if (httpImportIPAddresses[i].equals(ip)) return true;
		}
		return false;
	}

	/**
	 * Get the array of HttpImportIPAddresses.
	 * @return the array of HttpImportIPAddresses.
	 */
	public static String[] getHttpImportIPAddresses() {
		if (xml == null) return new String[0];
		return httpImportIPAddresses;
	}

	/**
	 * Return a String indicating whether the anonymizer is enabled for
	 * the HttpImportService on the DICOM service.
	 * @return "yes" if the anonymizer is enabled; "no" otherwise.
	 */
	public static String getHttpImportAnonymize() {
		if ((xml == null) || (httpImportAnonymize == null)) return "no";
		return httpImportAnonymize.equals("yes") ? "yes" : "no";
	}

	/**
	 * Indicate whether the anonymizer is enabled for the
	 * HttpImportService on the DICOM service.
	 * @return true if the anonymizer is enabled; false otherwise.
	 */
	public static boolean httpImportAnonymizerEnabled() {
		if ((xml == null) || (httpImportAnonymize == null)) return false;
		return httpImportAnonymize.equals("yes");
	}

	/**
	 * Get the array of HttpExportDirectories. These are the
	 * directories which serve as queues for sending objects
	 * received by the DicomImportService to other clinical
	 * trial participants.
	 * @return the array of HttpExportDirectories.
	 */
	public static String[] getHttpExportDirectories() {
		if (xml == null) return new String[0];
		return httpExportDirectories;
	}

	/**
	 * Get the array of HttpExportDirectoryFiles. These are the
	 * directories which serve as queues for sending objects
	 * received by the DicomImportService to other clinical
	 * trial participants.
	 * @return the array of HttpExportDirectoryFiles.
	 */
	public static File[] getHttpExportDirectoryFiles() {
		if (xml == null) return new File[0];
		return httpExportDirectoryFiles;
	}

	/**
	 * Get the array of HttpExportURLs corresponding
	 * to the HttpExportDirectories.
	 * @return the array of HttpExportURLs.
	 */
	public static String[] getHttpExportURLs() {
		if (xml == null) return new String[0];
		return httpExportURLs;
	}

	/**
	 * Get the dicomExportMode, which determines whether
	 * objects are to be automatically or manually queued
	 * for export to DICOM Storage SCPs. The default is
	 * "auto".
	 * @return the dicomExportMode.
	 */
	public static String getDicomExportMode() {
		if (xml == null) return "auto";
		if (dicomExportMode == null) return "auto";
		if (dicomExportMode.equals("QC")) return "QC";
		return "auto";
	}

	/**
	 * Get the array of DicomExportDirectories. These are the
	 * directories which serve as queues for sending objects
	 * received by the HttpImportService to local DICOM devices.
	 * @return the array of DicomExportDirectories.
	 */
	public static String[] getDicomExportDirectories() {
		if (xml == null) return new String[0];
		return dicomExportDirectories;
	}

	/**
	 * Get the array of DicomExportDirectoryFiles. These are the
	 * directories which serve as queues for sending objects
	 * received by the HttpImportService to local DICOM devices.
	 * @return the array of DicomExportDirectoryFiles.
	 */
	public static File[] getDicomExportDirectoryFiles() {
		if (xml == null) return new File[0];
		return dicomExportDirectoryFiles;
	}

	/**
	 * Get the array of DicomExportAETitles corresponding
	 * to the DicomExportDirectories.
	 * @return the array of DicomExportAETitles.
	 */
	public static String[] getDicomExportAETitles() {
		if (xml == null) return new String[0];
		return dicomExportAETitles;
	}

	/**
	 * Get the array of DicomExportIPAddresses corresponding
	 * to the DicomExportDirectories.
	 * @return the array of DicomExportIPAddresses.
	 */
	public static String[] getDicomExportIPAddresses() {
		if (xml == null) return new String[0];
		return dicomExportIPAddresses;
	}

	/**
	 * Get the Application Entity Title of the DICOM Storage SCP
	 * for the DICOM service.
	 * @return the DICOM Storage SCP AE Title, or the empty string if missing.
	 */
	public static String getDicomStoreAETitle() {
		if (xml == null) return "";
		return dicomStoreAETitle;
	}

	/**
	 * Get the port of the DICOM Storage SCP for the DICOM service.
	 * @return the DICOM Storage SCP port, or the empty string if missing.
	 */
	public static String getDicomStorePort() {
		if (xml == null) return "";
		return dicomStorePort;
	}

	/**
	 * Return a String indicating whether the anonymizer is enabled for
	 * the DicomImportService on the DICOM service.
	 * @return "yes" if the anonymizer is enabled; "no" otherwise.
	 */
	public static String getDicomImportAnonymize() {
		if ((xml == null) || (dicomImportAnonymize == null)) return "no";
		return dicomImportAnonymize.equals("yes") ? "yes" : "no";
	}

	/**
	 * Indicate whether the anonymizer is enabled for the
	 * DicomImportService on the DICOM service.
	 * @return true if the anonymizer is enabled; false otherwise.
	 */
	public static boolean dicomImportAnonymizerEnabled() {
		if ((xml == null) || (dicomImportAnonymize == null)) return false;
		return dicomImportAnonymize.equals("yes");
	}

	/**
	 * Return a String indicating whether database export is enabled
	 * for the DICOM service.
	 * @return "yes" if database export is enabled; "no" otherwise.
	 */
	public static String getDatabaseExportMode() {
		if ((xml == null) || (databaseExportMode == null)) return "disabled";
		return databaseExportMode;
	}

	/**
	 * Indicate whether database export is enabled for the
	 * DICOM service.
	 * @return true if database export is enabled (either in auto or QC mode); false otherwise.
	 */
	public static boolean databaseExportEnabled() {
		if ((xml == null) || (databaseExportMode == null)) return false;
		return !databaseExportMode.equals("disabled");
	}

	/**
	 * Get the DatabaseExportDirectory. This directory serves
	 * as a queue for objects being inserted into a relational
	 * database.
	 * @return the absolute pate to the DatabaseExportDirectory.
	 */
	public static String getDatabaseExportDirectory() {
		if (xml == null) return "";
		return databaseExportDir;
	}

	/**
	 * Get the DatabaseExportDirectoryFile. This directory serves
	 * as a queue for objects being inserted into a relational
	 * database.
	 * @return the File object pointing to the DatabaseExportDirectory.
	 */
	public static File getDatabaseExportDirectoryFile() {
		if (xml == null) return null;
		return new File(basepath + databaseExportDir);
	}

	/**
	 * Return a String indicating whether the anonymizer is enabled for
	 * the DicomImportService on the DICOM service.
	 * @return "yes" if the anonymizer is enabled; "no" otherwise.
	 */
	public static String getDatabaseExportAnonymize() {
		if ((xml == null) || (databaseExportAnonymize == null)) return "no";
		return databaseExportAnonymize.equals("yes") ? "yes" : "no";
	}

	/**
	 * Indicate whether the anonymizer is enabled for the
	 * DicomImportService on the DICOM service.
	 * @return true if the anonymizer is enabled; false otherwise.
	 */
	public static boolean databaseExportAnonymizerEnabled() {
		if ((xml == null) || (databaseExportAnonymize == null)) return false;
		return databaseExportAnonymize.equals("yes");
	}

	/**
	 * Get the DatabaseExportInterval. This defines the time in
	 * milliseconds between checks of the database export directory.
	 * @return the interval in milliseconds, or 10000 if the interval
	 * cannot be parsed as an integer, the interval is less than 5000,
	 * or the interval is greater than 1000000.
	 */
	public static int getDatabaseExportInterval() {
		int def = 10000;
		if (xml == null) return def;
		int t;
		try { t = Integer.parseInt(databaseExportInterval); }
		catch (Exception ex) { return def; }
		if ((t < 5000) || (t > 1000000)) return def;
		return t;
	}

	/**
	 * Get the name of the Database class which is to be loaded by the
	 * DatabaseExportService. This class serves as the interface to the
	 * external database for clinical trial data.
	 * @return the name of the class implementing the Database interface, or
	 * null if the class name is not defined.
	 */
	public static String getDatabaseClassName() {
		if (xml == null) return null;
		return databaseClassName;
	}

	/**
	 * Return a String indicating whether the preprocessor is enabled.
	 * @return "yes" if the preprocessor is enabled; "no" otherwise.
	 */
	public static String getPreprocessorEnabled() {
		if ((xml == null) || (preprocessorEnabled == null)) return "no";
		return preprocessorEnabled.equals("yes") ? "yes" : "no";
	}

	/**
	 * Get the name of the Preprocessor class which is to be loaded by the
	 * ObjectProcessor. This class can provide external verification of clinical trial data.
	 * @return the name of the class implementing the preprocessor, or
	 * null if the class name is not defined.
	 */
	public static String getPreprocessorClassName() {
		if (xml == null) return null;
		return preprocessorClassName;
	}

	/**
	 * Get the remapper external key file.
	 * @return the path to the external key file, or
	 * null if the key file is not defined.
	 */
	public static String getKeyFile() {
		if (xml == null) return null;
		return keyfile;
	}

	/**
	 * Get the remapper UID roote.
	 * @return the remapper UID root, or
	 * null if the UID root is not defined.
	 */
	public static String getUIDRoot() {
		if (xml == null) return null;
		return uidroot;
	}

	/**
	 * Get the remapper base date.
	 * @return the remapper base date, or
	 * null if the base date is not defined.
	 */
	public static String getBaseDate() {
		if (xml == null) return null;
		return basedate;
	}

	/**
	 * Get the remapper patient ID prefix.
	 * @return the patient ID prefix, or
	 * null if the patient ID prefix is not defined.
	 */
	public static String getPtIdPrefix() {
		if (xml == null) return null;
		return ptIdPrefix;
	}

	/**
	 * Get the remapper patient ID suffix.
	 * @return the patient ID suffix, or
	 * null if the patient ID suffix is not defined.
	 */
	public static String getPtIdSuffix() {
		if (xml == null) return null;
		return ptIdSuffix;
	}

	/**
	 * Get the remapper patient ID first value.
	 * @return the patient ID first value, or
	 * null if the patient ID first value is not defined.
	 */
	public static int getFirstPtId() {
		if (xml == null) return 1;
		return firstPtId;
	}

	/**
	 * Get the remapper patient ID width value.
	 * @return the patient ID width value, or
	 * null if the patient ID width value is not defined.
	 */
	public static int getPtIdWidth() {
		if (xml == null) return 4;
		return ptIdWidth;
	}

}