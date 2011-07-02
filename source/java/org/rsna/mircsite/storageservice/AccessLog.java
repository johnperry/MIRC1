/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.storageservice;

import java.io.*;
import java.net.*;
import java.util.Calendar;
import javax.servlet.*;
import javax.servlet.http.*;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.rsna.mircsite.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
  * Encapsulates static methods for logging access to and
  * export of objects containing PHI.
  */

public class AccessLog {

	static Logger accessLog = null;
	static final Logger logger = Logger.getLogger(AccessLog.class);
	static final String indent = "\n     ";

    /**
      * If access logging is enabled in StorageConfig, this method
      * creates a local log entry in a simplified format.
      * <p>
      * If access log exporting is also enabled in StorageConfig,
      * this method also creates a log entry in the format
      * defined by the IHE ATNA integration profile.
      * <p>
      * The export mechanism is not that defined by ATNA. Instead,
      * this method makes a URLConnection to a URL defined in
      * StorageConfig and writes the XML string. The server receiving
      * the string is responsible for processing it or passing it to
      * an official audit repository.
      * @param req The request for information.
      * @param xmlDocument The MIRCdocument that was was accessed. If the
      * document contains no PHI, no access log entry is created.
      */
	public static void makeAccessLogEntry(HttpServletRequest req, Document xmlDocument) {
		if (StorageConfig.phiLogEnabled()) {
			try {
				//See if the MIRCdocument has PHI
				Node siuidNode = XmlUtil.getElementViaPath(xmlDocument,"MIRCdocument/phi/study/si-uid");
				if (siuidNode == null) return;

				//There is PHI in the document, get the identifying parameters
				String siuid = XmlUtil.getElementValue(siuidNode);
				String ptid = XmlUtil.getValueViaPath(xmlDocument,"MIRCdocument/phi/study/pt-id");
				String ptname = XmlUtil.getValueViaPath(xmlDocument,"MIRCdocument/phi/study/pt-name");

				//Get the servlet parameters
				String sscontext = req.getContextPath();
				String ssip = getIPAddress();

				//Get the user parameters
				String username = req.getRemoteUser();
				if (username == null) username = "User NOT authenticated!";
				String userip = req.getRemoteAddr();

				//Get the event
				String event = "Access";
				if (req.getParameter("zip") != null) event = "Export";

				//Log the entry
				makeAccessLogEntry(event,username,userip,sscontext,ssip,siuid,ptid,ptname);
			}
			catch (Exception ignore) {
				logger.warn("Unable to create an access log entry for a PHI access.",ignore);
			}
		}
	}

    /**
      * If access logging is enabled in StorageConfig, this method
      * creates a local log entry in a simplified format.
      * <p>
      * If access log exporting is also enabled in StorageConfig,
      * this method also creates a log entry in the format
      * defined by the IHE ATNA integration profile.
      * <p>
      * The export mechanism is not that defined by ATNA. Instead,
      * this method makes a URLConnection to a URL defined in
      * StorageConfig and writes the XML string. The server receiving
      * the string is responsible for processing it or passing it to
      * an official audit repository.
      * @param event The name of the access event that has occurred.
      * It can have the value "Access" or "Export".
      * @param username The username of the user on the storage service
      * on which the PHI access has occurred.
      * @param userip The IP address of the user's computer.
      * @param sscontext The name of the storage service on which the access
      * has occurred.
      * @param ssip The IP address of the storage service.
      * @param siuid The Study Instance UID of the PHI that was accessed.
      * @param ptid The ID of the patient whose PHI was accessed.
      * @param ptname The name of the patient whose PHI was accessed.
      */
	public static void makeAccessLogEntry(String event,
								   String username,
								   String userip,
								   String sscontext,
								   String ssip,
								   String siuid,
								   String ptid,
								   String ptname)  {

		if (StorageConfig.phiLogEnabled()) {
			if (accessLog == null) createLocalAccessLog(StorageConfig.basepath + StorageConfig.phiAccessLogDirectory);
			String datetime = getDateTime();
			makeLocalAccessLogEntry(datetime,event,username,userip,siuid,ptid,ptname);
			if (StorageConfig.phiLogExportEnabled() && !StorageConfig.getPhiLogExportURL().equals("")) {
				File phiAccessLogDir = new File (StorageConfig.basepath + StorageConfig.phiAccessLogDirectory);
				ExportableAccessLogEntry entry =
					new ExportableAccessLogEntry(phiAccessLogDir,datetime,event,username,
												 userip,sscontext,ssip,siuid,ptid,ptname);
				entry.export(StorageConfig.getPhiLogExportURL());
			}
		}
	}

	//Create the local access log with a monthly rolling appender
	private static void createLocalAccessLog(String directory) {
		try {
			accessLog = Logger.getLogger("PHIAccess");
			accessLog.setAdditivity(false);
			PatternLayout layout = new PatternLayout("%m%n");
			File logs = new File(directory);
			logs.mkdirs();
			DailyRollingFileAppender appender = new DailyRollingFileAppender(
					layout,
					directory + File.separator + "AccessLog.txt",
					"'.'yyyy-MM");
			accessLog.addAppender(appender);
			accessLog.setLevel((Level)Level.ALL);
		}
		catch (Exception e) {
			logger.warn("Unable to instantiate the PHI Access logger");
			accessLog = null;
		}
	}

	//Make an entry in the local access log.
	private static void makeLocalAccessLogEntry(String datetime,
										 String event,
										 String username,
										 String userip,
										 String siuid,
										 String ptid,
										 String ptname) {
		if (accessLog != null)
			accessLog.info(datetime + " - " + event + " by " + username + " @" + userip
							+ indent + "SIUID: " + siuid
							+ indent + "Pt ID: " + ptid
							+ indent + "Name:  " + ptname);
	}

	//Create a datetime string.
	//The datetime is in local time.
	private static String getDateTime() {
		Calendar now = Calendar.getInstance();
		int hour = now.get(Calendar.HOUR_OF_DAY);
		int minute = now.get(Calendar.MINUTE);
		int second = now.get(Calendar.SECOND);
		int year = now.get(Calendar.YEAR);
		int month = now.get(Calendar.MONTH) + 1;
		int day = now.get(Calendar.DAY_OF_MONTH);
		return intToString(year,4) + "-" + intToString(month,2) + "-" + intToString(day,2) + "T"
				+ intToString(hour,2) + ":" + intToString(minute,2) + ":" + intToString(second,2);
	}

	// Convert the int n to a String with at least digits
	// places, padded with leading zeroes if necessary.
	private static String intToString(int n, int digits) {
		String s = "" + n;
		int k = digits - s.length();
		for (int i=0; i<k; i++) s = "0" + s;
		return s;
	}

	// Obtain the local IP address
	private static String getIPAddress() {
		InetAddress localHost;
		try { localHost = InetAddress.getLocalHost(); }
		catch (Exception e) { return "unknown"; }
		return localHost.getHostAddress();
	}

}
