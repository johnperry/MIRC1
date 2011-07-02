/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.dicomservice;

import java.io.*;
import java.net.*;
import java.util.*;
import org.apache.log4j.Logger;
import org.rsna.dicom.DicomSender;
import org.rsna.mircsite.log.Log;
import org.rsna.mircsite.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The Thread that exports DicomObjects via the DICOM protocol.
 */
public class DicomExportService extends Thread {

	boolean running = false;
	boolean reinitialize = false;
	String thisAeTitle = "DUMMY";
	File[] directoryFiles = null;
	String[] aeTitles = null;
	String[] ipAddresses = null;
	long[] nextTimes = null;
	static final String serviceName = "DicomExportService";
	static final Logger logger = Logger.getLogger(DicomExportService.class);

	/**
	 * Class constructor; creates a new instance of the DicomExportService.
	 */
	public DicomExportService() {
		initialize();
	}

	//Initialize the DicomExportService.
	//Get the parameters for exporting, including the export directories,
	//the AE titles, IP addresses and ports for all the destinations.
	//Reset all the timeouts to zero. This method is called whenever the
	//object is created or restarted.
	private void initialize() {
		reinitialize = false;
		thisAeTitle = TrialConfig.getDicomStoreAETitle();
		directoryFiles = TrialConfig.getDicomExportDirectoryFiles();
		aeTitles = TrialConfig.getDicomExportAETitles();
		ipAddresses = TrialConfig.getDicomExportIPAddresses();
		nextTimes = new long[ipAddresses.length];
		for (int i=0; i<nextTimes.length; i++) {
			nextTimes[i] = 0;
		}
	}

	/**
	 * Get the status text for display by the admin service.
	 */
	public String getStatus() {
		if (running) return "running";
		return "not running";
	}

	/**
	 * The Runnable interface implementation. Process the queue directories
	 * and send the files, then sleep for 10 seconds and check again.
	 */
	public void run() {
		Log.message(serviceName+": Started");
		running = true;
		while (!interrupted()) {
			try {
				processFiles();
				sleep(10000);
			}
			catch (Exception e) {
				running = false;
				return;
			}
			if (reinitialize) {
				initialize();
				Log.message(serviceName+": Reinitialized");
			}
		}
		Log.message(serviceName+": Interrupt received");
	}

	/**
	 * Restart the DicomExportService. Set a flag to cause the service to
	 * reinitialize after the next pass through the export directories.
	 */
	public void restart() {
		reinitialize = true;
	}

	//Look through the export directories and
	//send all the files there, oldest first. Note that the
	//files are actually queue elements (text files containing
	//the absolute file path to the DICOM object to be exported.
	private void processFiles() {
		String result;
		String resultLC;
		long currentTime;
		if (directoryFiles == null) return;
		if (directoryFiles.length != ipAddresses.length) return;

		for (int i=0; i<ipAddresses.length; i++) {
			//protect against a new destination that is
			//serviced before it is created by the queuing
			//of an object.
			if (!directoryFiles[i].exists()) directoryFiles[i].mkdirs();
			File[] files = directoryFiles[i].listFiles();

			for (int k=0; (k<files.length)
							&& ((currentTime = System.currentTimeMillis()) > nextTimes[i]);
									k++) {
				File file = files[k];
				result = export(file,aeTitles[i],ipAddresses[i]);
				resultLC = result.toLowerCase();
				if (result.equals("OK")) {
					file.delete();
					Log.message(serviceName+": Export successful: "+file.getName());
				}
				else if (resultLC.indexOf("d000") != -1) {
					//Assume this means the exported object's UID already existed on the receiver.
					//Note that the event occurred, but treat it as a success.
					String dicomFilePath = FileUtil.getFileText(file);
					Log.message(
						serviceName+": Export result: 0xD000 [duplicate UID?]"+
						"<br>Destination: "+directoryFiles[i].getName()+
						"<br>Queue element: "+file.getName()+
						"<br>DicomObject: "+dicomFilePath+
						"<br>"+((file.delete()) ?
							"The queue element was deleted." :
							"The queue element could not be deleted."));
				}
				else if (resultLC.indexOf("c001") != -1) {
					//Assume that this means the receiver's resources were not available.
					//Note that this occurred, and then force a wait for 5 seconds.
					Log.message(
						serviceName+": Export result: 0xC001 [resource unavailable?]"+
						"<br>The object was requeued.");
					nextTimes[i] = currentTime + 5000;
				}
				else if (result.equals("Rejected association")) {
					//wait 10 minutes if there was no connection
					nextTimes[i] = currentTime + 600000;
					Log.message(serviceName+": Rejected association to "+ipAddresses[i]);
				}
				else if ((resultLC.indexOf("timeout") != -1) ||
						 (resultLC.indexOf("timed out") != -1)) {
					//wait 10 seconds and try again if there was a timeout
					nextTimes[i] = currentTime + 10000;
					Log.message(serviceName+": " + result + ": " + file.getName());
				}
				else {
					Log.message(serviceName+": Export failure: "+ result + ": " + file.getName());
					logger.warn("Export failure: "+ result + ": " + file.getName());
					Log.message(Quarantine.file(file,serviceName));
				}
				yield();
			}
		}
	}

	//Export one file.
	private String export(File file, String aeTitle, String ipAddress) {
		ExportQueueElement eqe;
		File fileToExport;
		//Use the file input argument to create a queue element
		//and then use it to get the queued file.
		eqe = new ExportQueueElement(file);
		fileToExport = eqe.getQueuedFile();
		if (fileToExport == null) return "Error: Null fileToExport";
		int k = ipAddress.indexOf(":");
		if (k < 0) return ("Error: IP Address with no port specified");
		String host = ipAddress.substring(0,k);
		int port;
		try { port = Integer.parseInt(ipAddress.substring(k+1)); }
		catch (Exception e) { return "Error: IP Address with unparseable port"; }
		try {
			DicomSender dicomSender = new DicomSender(host,port,aeTitle,thisAeTitle);
			int result = dicomSender.send(fileToExport);
			if (result != 0) {
				logger.error("DicomSend Error: result = " + result + "["+Integer.toHexString(result)+"]");
				return "DicomSend Error; result = " + result + "["+Integer.toHexString(result)+"]";
			}
		}
		catch (Exception e) {
			logger.error(serviceName+" Export Exception for "+file,e);
			Log.message(serviceName+" Export Exception for "+file+"<br>Exception message: "+e.getMessage());
			return e.getMessage();
		}
		return "OK";
	}

}