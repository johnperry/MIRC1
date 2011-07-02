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
import org.apache.log4j.Logger;
import org.rsna.mircsite.util.FileUtil;

/**
  * A class to create an access log entry in the IHE ATNA
  * format and to export it via HTTP. This class requires
  * an access-log-template.txt file to be in the storage
  * service's access-logs directory. The format of the
  * access-log-template.txt file is that of the log entry,
  * with certain attribute values (denoted by "@name")
  * indicating where substitutions are to be made.
  * <p>
  * The export method is not that defined by ATNA. It is
  * intended that a server receive the transmission and
  * forward it to the institution's audit log repository.
  * An example server is available.
  */

public class ExportableAccessLogEntry {

	static final Logger logger = Logger.getLogger(ExportableAccessLogEntry.class);

	static final String accessLogTemplateName = "access-log-template.txt";

	//These values are taken from DICOM Supplement 95:
	static final String nameAccess = "Patient Record";
	static final String nameExport = "Export";
	static final String codeAccess = "110110";
	static final String codeExport = "110106";
	static final String lifecycleAccess = "6";
	static final String lifecycleExport = "10";

	String entry = "";
	File accessLogDir = null;

    /**
      * This class encapsulates a log entry in the format
      * specified by the IHE ATNA integration profile.
      * The format is taken from DICOM Supplement 95.
      * @param datetime the datetime string identifying the time the event occurred.
      * @param eventname the name of the access event that has occurred.
      * It can have the value "Access" or "Export".
      * @param username the username of the user on the storage service
      * on which the PHI access has occurred.
      * @param userip the IP address of the user's computer.
      * @param sscontext the name of the storage service on which the access
      * has occurred.
      * @param ssip the IP address of the storage service.
      * @param siuid the Study Instance UID of the PHI that was accessed.
      * @param ptid the ID of the patient whose PHI was accessed.
      * @param ptname the name of the patient whose PHI was accessed.
      */
	public ExportableAccessLogEntry(File accessLogDir,
									String datetime,
									String eventname,
									String username,
									String userip,
									String sscontext,
									String ssip,
									String siuid,
									String ptid,
									String ptname)  {

		this.accessLogDir = accessLogDir;

		//Get the log entry template file
		File accessLogTemplateFile = new File(accessLogDir,accessLogTemplateName);
		entry = FileUtil.getFileText(accessLogTemplateFile);
		if (entry.equals("")) {
			logger.warn("Unable to read the exportable access log template");
			return;
		}

		//Put in all the values
		entry = entry.replaceAll("@datetime",datetime);
		if (eventname.equals("Export")) {
			entry = entry.replace("@eventcode",codeExport);
			entry = entry.replace("@eventname",nameExport);
			entry = entry.replace("@lifecycle",lifecycleExport);
		}
		else if (eventname.equals("Access")) {
			entry = entry.replace("@eventcode",codeAccess);
			entry = entry.replace("@eventname",nameAccess);
			entry = entry.replace("@lifecycle",lifecycleAccess);
		}
		else logger.warn("Unknown audit log entry event type");
		entry = entry.replace("@username",username);
		entry = entry.replace("@userip",userip);
		entry = entry.replace("@sscontext",sscontext);
		entry = entry.replace("@ssip",ssip);
		entry = entry.replace("@ptname",ptname);
		entry = entry.replace("@ptid",ptid);
		entry = entry.replace("@siuid",siuid);
	}

	/**
	  * Export the log entry. The export mechanism is not that
	  * defined by ATNA. Instead, this method makes a direct connection
	  * to a URL defined in StorageConfig and writes the XML string.
	  * The server receiving the string is responsible
	  * for processing it or passing it to an official audit repository.
      * @param urlString the URL of the server to receive the exported log entry.
      */
	public void export(String urlString) {
		try {
			URL url = new URL(urlString);
			Socket socket = new Socket(url.getHost(),url.getPort());
			PrintWriter svrpw = new PrintWriter(socket.getOutputStream());
			svrpw.println(entry);
			svrpw.flush();
			svrpw.close();
		}
		catch (Exception ex) {
			logger.warn(ex.toString());
		}
	}

	/**
      * Write the log entry String to a file.
      * @param file the File in which to write the log entry.
      */
	public void saveAs(File file) {
		FileUtil.setFileText(file,entry);
	}

	/**
      * Write the log entry to a file.
      * @return the log entry String.
      */
	public String toString() {
		return entry;
	}

}