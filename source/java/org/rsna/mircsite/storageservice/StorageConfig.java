/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.storageservice;

import java.io.*;
import java.net.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.http.HttpServlet;
import org.apache.log4j.Logger;
import org.rsna.mircsite.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Encapsulates the configuration parameters for a MIRC Storage Service.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class StorageConfig {

	static final Logger logger = Logger.getLogger(StorageConfig.class);

	public static final String configFilename = "storage.xml";
	public static final String templatepath = "templates" + File.separator;
	public static final String phiAccessLogDirectory = "access-logs";

	public static Document xml = null;

	public static String basepath = null;
	public static final String indexDoc = "index-doc.xsl";
	public static final String deletedDocuments = "deleted-documents";
	public static final String inputqueueFilename = "inputqueue.xml";
	public static final String siteindexFilename = "siteindex.xml";
	public static final String documentsDirectory = "documents";
	public static final String bullpenDirectory = "bullpen";

	public static File indexDocFile = null;
	public static File fileservice = null;
	public static File queryservice = null;
	public static Document enumeratedValues = null;
	public static Document speciesValues = null;
	public static String mode = "rad";
	public static String version = "";

	public static String querymode = null;
	public static String orderBy = null;
	public static String docbase = null;
	public static String tagline = null;
	public static String sitename = null;
	public static String docLogEnabled = null;
	public static String phiLogEnabled = null;
	public static String phiLogExportEnabled = null;
	public static String phiLogExportURL = null;
	public static String submitEnabled = null;
	public static String maxSubmitSize = null;
	public static String submitAutoindex = null;
	public static String zipEnabled = null;
	public static String maxZipSize = null;
	public static String zipAutoindex = null;
	public static String authorEnabled = null;
	public static String authorAutoindex = null;
	public static String publisherRoleName = null;
	public static String adminRoleName = null;
	public static String userRoleName = null;
	public static String tomcatRoleName = null;
	public static String dicomEnabled = null;
	public static String tceEnabled = null;

	public static int 	 doctimeout = 0;
	public static int 	 ddtimeout = 0;
	public static int    jpegquality = -1;

	/**
	 * Loads the Storage Service configuration parameters from the
	 * storage.xml file, the web.xml file, and the servlet context.
	 * @param servletContext
	 * @return true if the parameters could be loaded, else false.
	 */
	public static synchronized boolean load(ServletContext servletContext) {
		try {
			//Get the trial.xml config file
			xml = XmlUtil.getDocument(servletContext.getRealPath(configFilename));

			//Get the basepath to the root directory
			basepath = servletContext.getRealPath("/").trim();
			if (!basepath.endsWith(File.separator)) basepath += File.separator;

			//Find the fileservice
			File webapps = new File(basepath);
			while (!webapps.getName().equals("webapps")) webapps = webapps.getParentFile();
			fileservice = new File(webapps,"file");

			//Find the queryservice and load the parameters
			queryservice = new File(webapps,"mirc");
			Document mircxml = XmlUtil.getDocument(new File(queryservice,"mirc.xml"));
			mode = XmlUtil.getValueViaPath(mircxml,"mirc@mode");
			if ((mode == null) || mode.equals("")) mode = "rad";
			enumeratedValues = XmlUtil.getDocument(new File(queryservice,"enumerated-values.xml"));
			speciesValues = XmlUtil.getDocument(new File(queryservice,"species-values.xml"));

			//Get the storage service parameters
			docbase = XmlUtil.getValueViaPath(xml,"storage/service@docbase");
			version = XmlUtil.getValueViaPath(xml,"storage/service@version");
			tagline = XmlUtil.getValueViaPath(xml,"storage/tagline");
			sitename = XmlUtil.getValueViaPath(xml,"storage/sitename");
			querymode = XmlUtil.getValueViaPath(xml,"storage/service@querymode");
			orderBy = XmlUtil.getValueViaPath(xml,"storage/service@orderby");

			String temp = XmlUtil.getValueViaPath(xml,"storage/service@doctimeout");
			doctimeout = 0;
			try { doctimeout = Integer.parseInt(temp); }
			catch (Exception ignore) { }

			temp = XmlUtil.getValueViaPath(xml,"storage/service@ddtimeout");
			ddtimeout = 0;
			try { ddtimeout = Integer.parseInt(temp); }
			catch (Exception ignore) { }

			temp = XmlUtil.getValueViaPath(xml,"storage/service@jpegquality");
			jpegquality = -1;
			try { jpegquality = Integer.parseInt(temp); }
			catch (Exception ignore) { }

			//Get the document logging parameters
			docLogEnabled = XmlUtil.getValueViaPath(xml,"storage/doc-access-log@enabled");

			//Get the PHI logging parameters
			phiLogEnabled = XmlUtil.getValueViaPath(xml,"storage/phi-access-log@enabled");
			phiLogExportEnabled = XmlUtil.getValueViaPath(xml,"storage/phi-access-log@export");
			phiLogExportURL = XmlUtil.getValueViaPath(xml,"storage/phi-access-log@url");

			//Get the submission parameters
			submitEnabled = XmlUtil.getValueViaPath(xml,"storage/submit-service/doc@enabled");
			maxSubmitSize = XmlUtil.getValueViaPath(xml,"storage/submit-service/doc@maxsize");
			submitAutoindex = XmlUtil.getValueViaPath(xml,"storage/submit-service/doc@autoindex");

			//Get the zip service parameters
			zipEnabled = XmlUtil.getValueViaPath(xml,"storage/submit-service/zip@enabled");
			maxZipSize = XmlUtil.getValueViaPath(xml,"storage/submit-service/zip@maxsize");
			zipAutoindex = XmlUtil.getValueViaPath(xml,"storage/submit-service/zip@autoindex");

			//Get the author service parameters
			authorEnabled = XmlUtil.getValueViaPath(xml,"storage/author-service@enabled");
			authorAutoindex = XmlUtil.getValueViaPath(xml,"storage/author-service@autoindex");

			//Get the web.xml file so we can get the role names.
			File webxmlFile = new File(basepath + "WEB-INF" + File.separator + "web.xml");
			String webxml = FileUtil.getFileText(webxmlFile);
			publisherRoleName = XmlStringUtil.getEntity(webxml,"publisher");
			adminRoleName = XmlStringUtil.getEntity(webxml,"admin");
			tomcatRoleName = XmlStringUtil.getEntity(webxml,"tomcat");
			userRoleName = XmlStringUtil.getEntity(webxml,"user");

			//Get the dicom service enable
			dicomEnabled = XmlUtil.getValueViaPath(xml,"storage/dicom-service@enabled");

			//Get the tce service enable
			tceEnabled = XmlUtil.getValueViaPath(xml,"storage/tce-service@enabled");

			//Make the indexDocFile
			indexDocFile = new File(servletContext.getRealPath(indexDoc));

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
	 * Returns a File pointing to the bullpen directory.
	 * @return the bullpen directory File.
	 */
	public static File getBullpenFile() {
		return new File(basepath + bullpenDirectory);
	}

	/**
	 * Returns a File pointing to the root of the file service
	 * running on the same Tomcat instance as the storage service.
	 * @return the root directory of the file service.
	 */
	public static File getFileservice() {
		return fileservice;
	}

	/**
	 * Returns a String containing the mode in which this MIRC
	 * site is running. The mode indicates the scientific discipline
	 * supported by the site. Valid values are "rad" and "vet".
	 * @return the mode of the site.
	 */
	public static String getMode() {
		return mode;
	}

	/**
	 * Returns a String containing the flag indicating how to order query results
	 * from this site.
	 * @return the query result order for the site.
	 */
	public static String getOrderBy() {
		return orderBy;
	}

	/**
	 * Returns the XML DOM object containing the enumerated values
	 * used by the query service and the author service.
	 * @return the enumerated values of the site.
	 */
	public static Document getEnumeratedValues() {
		return enumeratedValues;
	}

	/**
	 * Returns the XML DOM object containing the species and breeds
	 * used by the query service and the author service.
	 * @return the species of the site.
	 */
	public static Document getSpeciesValues() {
		return speciesValues;
	}

	/**
	 * Returns a File pointing to the root of the query service
	 * running on the same Tomcat instance as the storage service.
	 * @return the root directory of the query service.
	 */
	public static File getQueryservice() {
		return queryservice;
	}

	/**
	 * Returns the querymode attribute from the storage.xml file.
	 * Querymode indicates whether the storage service is to return
	 * all results to all users ("open") or only results that point
	 * to documents that users are allowed to see ("restricted").
	 * @return query mode.
	 */
	public static String getQueryMode() {
		if ((xml == null) || (querymode == null)) return "open";
		return querymode;
	}

	/**
	 * Returns the deleted documents timeout attribute from the
	 * storage.xml file. ddtimeout is the minimum time in days
	 * which a deleted document can remain in the deleted-documents
	 * directory before being removed by the garbage collector.
	 * @return the time in days for documents to live in the
	 * deleted-documents directory before final removal.
	 */
	public static int getDDTimeout() {
		if (xml == null) return 0;
		return ddtimeout;
	}

	/**
	 * Returns the documents timeout attribute from the
	 * storage.xml file. doctimeout is the minimum time in days
	 * which a document can remain in the documents
	 * directory before being removed. This feature should
	 * only be used on systems which are intended to store
	 * transient documents only.
	 * @return the time in days for documents to live in the
	 * documents directory before final removal.
	 */
	public static int getDocTimeout() {
		if (xml == null) return 0;
		return doctimeout;
	}

	/**
	 * Returns the jpegquality attribute from the
	 * storage.xml file. jpegquality is quality setting
	 * supplied to the MircImage.saveAsJPEG method. The range
	 * of valid values is 0 to 100. Negative values cause
	 * the method to use the default parameter supplied by
	 * the SUN JPEGImageEncoder.
	 * @return the JPEG quality parameter to be used in the
	 * creation of JPEG images.
	 */
	public static int getJPEGQuality() {
		if (xml == null) return -1;
		return jpegquality;
	}

	/**
	 * Returns the docbase attribute from the storage.xml file.
	 * Docbase is the path from the root of the servlet to the
	 * top of the directory tree containing the MIRCdocuments.
	 * @return the base directory of the documents tree.
	 */
	public static String getDocbase() {
		if ((xml == null) || (docbase == null)) return "";
		return docbase;
	}

	/**
	 * Returns the tagline element from the storage.xml file.
	 * @return the tagline, or an empty String if tagline is missing.
	 */
	public static String getTagline() {
		if ((xml == null) || (tagline == null)) return "";
		return tagline;
	}

	/**
	 * Returns the sitename element from the storage.xml file.
	 * @return the name of the site, or an empty String if sitename is missing.
	 */
	public static String getSitename() {
		if ((xml == null) || (sitename == null)) return "";
		return sitename;
	}

	/**
	 * Indicates whether document access logging is enabled on the
	 * storage service. Document access logging always generates local
	 * logging in the storage service's access-logs directory.
	 * @return true if docLogEnabled is "yes"; false otherwise.
	 */
	public static boolean docLogEnabled() {
		if ((xml == null) || (docLogEnabled == null)) return false;
		return docLogEnabled.equals("yes");
	}

	/**
	 * Returns the enabled attribute of the doc-access-log element
	 * from the storage.xml file.
	 * @return the enabled attribute ("yes" or "no"; "no" if missing).
	 */
	public static String getDocLogEnabled() {
		if ((xml == null) || (docLogEnabled == null)) return "no";
		return docLogEnabled;
	}

	/**
	 * Indicates whether PHI access logging is enabled on the
	 * storage service. PHI access logging always generates local
	 * logging in the storage service's access-logs directory.
	 * Exporting access log entries is controlled by the
	 * phiLogExportEnabled parameter.
	 * @return true if phiLogEnabled is "yes"; false otherwise.
	 */
	public static boolean phiLogEnabled() {
		if ((xml == null) || (phiLogEnabled == null)) return false;
		return phiLogEnabled.equals("yes");
	}

	/**
	 * Returns the enabled attribute of the phi-access-log element
	 * from the storage.xml file.
	 * @return the enabled attribute ("yes" or "no"; "no" if missing).
	 */
	public static String getPhiLogEnabled() {
		if ((xml == null) || (phiLogEnabled == null)) return "no";
		return phiLogEnabled;
	}

	/**
	 * Indicates whether PHI access log entries are to be exported.
	 * To generate exported access log entries, the phiLogEnabled
	 * parameter must be true and the phiLogExportURL must not be
	 * blank.
	 * @return phiLogExportEnabled
	 */
	public static boolean phiLogExportEnabled() {
		if ((xml == null) || (phiLogExportEnabled == null)) return false;
		return phiLogExportEnabled.equals("yes");
	}

	/**
	 * Returns the export attribute of the phi-access-log element
	 * from the storage.xml file.
	 * @return the export attribute ("yes" or "no"; "no" if missing).
	 */
	public static String getPhiLogExportEnabled() {
		if ((xml == null) || (phiLogExportEnabled == null)) return "no";
		return phiLogExportEnabled;
	}

	/**
	 * Returns the url attribute of the phi-access-log element
	 * from the storage.xml file. This attribute must not be blank
	 * if exporting is enabled.
	 * @return the url attribute, or an empty string if missing.
	 */
	public static String getPhiLogExportURL() {
		if ((xml == null) || (phiLogExportURL == null)) return "";
		return phiLogExportURL;
	}

	/**
	 * Indicates whether the submit service is enabled on the storage service.
	 * @return submitEnabled
	 */
	public static boolean submitEnabled() {
		if ((xml == null) || (submitEnabled == null)) return false;
		return submitEnabled.equals("yes");
	}

	/**
	 * Returns the enabled attribute of the postdoc element
	 * from the storage.xml file.
	 * @return the enabled attribute ("yes" or "no"; "no" if missing).
	 */
	public static String getSubmitEnabled() {
		if ((xml == null) || (submitEnabled == null)) return "no";
		return submitEnabled;
	}

	/**
	 * Returns the maxsize attribute of the postdoc element from the storage.xml
	 * file. This is the maximum size in megabytes enabled for reception by
	 * the submit service. The default is 10 MB. Any value less than 5 MB is
	 * replaced by 5 MB.
	 * @return maxSubmitSize
	 */
	public static int getMaxSubmitSize() {
		if ((xml == null) || (maxSubmitSize == null)) return 10;
		int maxSize = StringUtil.getInt(maxSubmitSize);
		if (maxSize < 5) maxSize = 5;
		return maxSize;
	}

	/**
	 * Indicates whether automatic indexing for public documents
	 * is enabled on the submit service.
	 * @return submitAutoindex
	 */
	public static boolean submitAutoindex() {
		if ((xml == null) || (submitAutoindex == null)) return false;
		return submitAutoindex.equals("yes");
	}

	/**
	 * Returns the autoindex attribute of the postdoc element from the
	 * storage.xml file.
	 * @return the autoindex attribute ("yes" or "no"; "no" if missing).
	 */
	public static String getSubmitAutoindex() {
		if ((xml == null) || (submitAutoindex == null)) return "no";
		return submitAutoindex;
	}

	/**
	 * Indicates whether the zip service is enabled on the storage service.
	 * @return zipEnabled
	 */
	public static boolean zipEnabled() {
		if ((xml == null) || (zipEnabled == null)) return false;
		return zipEnabled.equals("yes");
	}

	/**
	 * Returns the enabled attribute of the zip element
	 * from the storage.xml file.
	 * @return the enabled attribute ("yes" or "no"; "no" if missing).
	 */
	public static String getZipEnabled() {
		if ((xml == null) || (zipEnabled == null)) return "no";
		return zipEnabled;
	}

	/**
	 * Returns the maxsize attribute of the zip element from the storage.xml
	 * file. This is the maximum size in megabytes enabled for reception by
	 * the zip service. The default is 30 MB. Any value less than 5 MB is
	 * replaced by 5 MB.
	 * @return maxSubmitSize
	 */
	public static int getMaxZipSize() {
		if ((xml == null) || (maxZipSize == null)) return 30;
		int maxSize = StringUtil.getInt(maxZipSize);
		if (maxSize < 5) maxSize = 5;
		return maxSize;
	}

	/**
	 * Indicates whether automatic indexing for public documents
	 * is enabled on the submit service.
	 * @return submitAutoindex
	 */
	public static boolean zipAutoindex() {
		if ((xml == null) || (zipAutoindex == null)) return false;
		return zipAutoindex.equals("yes");
	}

	/**
	 * Returns the autoindex attribute of the zip element from the
	 * storage.xml file.
	 * @return the autoindex attribute ("yes" or "no"; "no" if missing).
	 */
	public static String getZipAutoindex() {
		if ((xml == null) || (zipAutoindex == null)) return "no";
		return zipAutoindex;
	}

	/**
	 * Indicates whether the author service is enabled on the storage service.
	 * @return authorEnabled
	 */
	public static boolean authorEnabled() {
		if ((xml == null) || (authorEnabled == null)) return false;
		return authorEnabled.equals("yes");
	}

	/**
	 * Returns the enabled attribute of the author-service element
	 * from the storage.xml file.
	 * @return the enabled attribute ("yes" or "no"; "no" if missing).
	 */
	public static String getAuthorEnabled() {
		if ((xml == null) || (authorEnabled == null)) return "no";
		return authorEnabled;
	}

	/**
	 * Indicates whether automatic indexing for public documents
	 * is enabled on the author service.
	 * @return authorAutoindex
	 */
	public static boolean authorAutoindex() {
		if ((xml == null) || (authorAutoindex == null)) return false;
		return authorAutoindex.equals("yes");
	}

	/**
	 * Returns the autoindex attribute of the author-service element from the
	 * storage.xml file.
	 * @return the autoindex attribute ("yes" or "no"; "no" if missing).
	 */
	public static String getAuthorAutoindex() {
		if ((xml == null) || (authorAutoindex == null)) return "no";
		return authorAutoindex;
	}

	/**
	 * Returns the value of the publisher entity in the storage service's
	 * web.xml file. This is the role name that a user must have to allow
	 * the user to publish public documents.
	 * @return the name of the publisher role on the storage service.
	 */
	public static String getPublisherRoleName() {
		if ((xml == null) || (publisherRoleName == null)) return "";
		return publisherRoleName;
	}

	/**
	 * Returns the value of the user entity in the storage service's
	 * web.xml file. This is the role name that a user must have to allow
	 * the user to access the storage service.
	 * @return the name of the user role on the storage service.
	 */
	public static String getUserRoleName() {
		if ((xml == null) || (userRoleName == null)) return "";
		return userRoleName;
	}

	/**
	 * Returns the value of the admin entity in the storage service's
	 * web.xml file. This is the role name that a user must have to allow
	 * the user to access the admin service.
	 * @return the name of the admin role on the storage service.
	 */
	public static String getAdminRoleName() {
		if ((xml == null) || (adminRoleName == null)) return "";
		return adminRoleName;
	}

	/**
	 * Returns the value of the tomcat entity in the storage service's
	 * web.xml file. This is the role name that a user must have to allow
	 * the user to access the services associated with Tomcat itself (including
	 * the User Role Manager, the Log Viewer, etc.).
	 * @return the name of the tomcat role on the storage service.
	 */
	public static String getTomcatRoleName() {
		if ((xml == null) || (tomcatRoleName == null)) return "";
		return tomcatRoleName;
	}

	/**
	 * Indicates whether the DICOM service is enabled on the storage service.
	 * @return dicomEnabled
	 */
	public static boolean dicomEnabled() {
		if ((xml == null) || (dicomEnabled == null)) return false;
		return dicomEnabled.equals("yes");
	}

	/**
	 * Returns the enabled attribute of the dicom-service element
	 * from the storage.xml file.
	 * @return the enabled attribute ("yes" or "no"; "no" if missing).
	 */
	public static String getDicomEnabled() {
		if ((xml == null) || (dicomEnabled == null)) return "no";
		return dicomEnabled;
	}

	/**
	 * Indicates whether the TCE service is enabled on the storage service.
	 * @return tceEnabled
	 */
	public static boolean tceEnabled() {
		if ((xml == null) || (tceEnabled == null)) return false;
		return tceEnabled.equals("yes");
	}

	/**
	 * Returns the enabled attribute of the tce-service element
	 * from the storage.xml file.
	 * @return the enabled attribute ("yes" or "no"; "no" if missing).
	 */
	public static String getTCEEnabled() {
		if ((xml == null) || (tceEnabled == null)) return "no";
		return tceEnabled;
	}

}