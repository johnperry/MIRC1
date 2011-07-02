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
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.rsna.mircsite.util.StringUtil;

/**
  * Encapsulates static methods for logging access to and
  * export of MIRCdocuments.
  */

public class DocAccessLog {

	static Logger accessLog = null;
	static final Logger logger = Logger.getLogger(DocAccessLog.class);
	static final String indent = "\n     ";

    /**
      * If document access logging is enabled in StorageConfig,
      * this method creates a local log entry in CSV format.
      * <p>
      * @param req The request for information.
      */
	public static void makeAccessLogEntry(HttpServletRequest req) {
		if (StorageConfig.docLogEnabled()) {
			try {
				logger.warn("...about to create the log");
				//Create the log if necessary.
				if (accessLog == null) {
					createLocalAccessLog(StorageConfig.basepath + StorageConfig.phiAccessLogDirectory);
				}

				//Log the entry
				logger.warn("...about to write the log entry");
				if (accessLog != null) {
					logger.warn("...writing");
					accessLog.info(StringUtil.getDate() + ","
								 + StringUtil.getTime() + ","
								 + ((req.getParameter("zip") != null) ? "export" : "access") + ","
								 + req.getRemoteUser() + ","
								 + req.getRemoteAddr() + ","
								 + req.getRequestURL().toString());
				}
			}
			catch (Exception ignore) {
				logger.warn("Unable to create an access log entry for a MIRCdocument access.",ignore);
			}
		}
	}

	//Create the local access log with a monthly rolling appender
	private static void createLocalAccessLog(String directory) {
		try {
			accessLog = Logger.getLogger("DocAccess");
			accessLog.setAdditivity(false);
			PatternLayout layout = new PatternLayout("%m%n");
			File logs = new File(directory);
			logs.mkdirs();
			DailyRollingFileAppender appender = new DailyRollingFileAppender(
					layout,
					directory + File.separator + "DocAccessLog.csv",
					"'.'yyyy-MM");
			accessLog.addAppender(appender);
			accessLog.setLevel((Level)Level.ALL);
		}
		catch (Exception e) {
			logger.warn("Unable to instantiate the Document Access logger");
			accessLog = null;
		}
	}
}
