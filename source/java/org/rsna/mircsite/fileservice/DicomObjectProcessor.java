/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.fileservice;

import java.awt.AWTEvent;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.event.*;
import org.apache.log4j.Logger;
import org.rsna.dicom.Dicom;
import org.rsna.dicom.DicomEvent;
import org.rsna.dicom.DicomEventListener;
import org.rsna.dicom.DicomStorageScp;
import org.rsna.mircsite.anonymizer.DicomAnonymizer;
import org.rsna.mircsite.anonymizer.LocalRemapper;
import org.rsna.mircsite.util.DicomObject;
import org.rsna.mircsite.util.FileObject;
import org.rsna.mircsite.util.StringUtil;
import org.rsna.mircsite.util.FileUtil;
import org.rsna.mircsite.util.TomcatUser;
import org.rsna.mircsite.util.TomcatUsers;

/**
 * The Thread that receives DicomObjects from a DICOM Storage SCP,
 * anonymizes them, and stores them in the department file cabinet.
 */
public class DicomObjectProcessor extends Thread implements DicomEventListener {

	static final String processorServiceName = "DicomObjectProcessor";
	static final String dicomImportServiceName = "DicomImportService";

	static final Logger logger = Logger.getLogger(DicomObjectProcessor.class);

	File dicomstoreFile;
	File dicomimportFile;
	File quarantineFile;
	DicomStorageScp scp;

	boolean running = false;
	static final String anonymizerFilename = "dicom-anonymizer.properties";

	/**
	 * Class constructor; creates a new instance of the DicomObjectProcessor
	 * and sets itself to run at the lowest possible priority.
	 */
	public DicomObjectProcessor() {
		dicomstoreFile = new File(AdminService.root,"store");
		dicomimportFile = new File(AdminService.root,"import");
		quarantineFile = new File(AdminService.root,"quarantine");
		dicomstoreFile.mkdirs();
		dicomimportFile.mkdirs();
		quarantineFile.mkdirs();
		this.setPriority(Thread.MIN_PRIORITY); //run at the lowest priority
	}

	/**
	 * The Runnable implementation; starts the thread, starts the DicomStorageScp,
	 * and polls the import queue directory, processing files when they appear.
	 */
	public void run() {
		restartSCP();
		while (!interrupted()) {
			try {
				processDicomImportFiles();
				sleep(10000);
			}
			catch (Exception e) { return; }
		}
	}

	/**
	 * Get the current status of the SCP.
	 * @return "running" if the SCP is running; otherwise "not running".
	 */
	public String getStatus() {
		return running ? "running" : "not running";
	}

	/**
	 * Get the current status of the SCP.
	 * @return true if the SCP is running; false otherwise.
	 */
	public boolean isRunning() {
		return running;
	}

	/**
	 * Stop the SCP if it is running.
	 */
	private void stopSCP() {
		if (scp == null) return;
		//The SCP throws an exception when it stops; ignore it.
		try { scp.stop(); }
		catch (Exception ignore) { }
		running = false;
	}

	/**
	 * Reinitialize and restart the SCP from the current
	 * values in the AdminService.
	 */
	public boolean restartSCP() {
		stopSCP();
		scp = initializeDicom();
		try { scp.start(); }
		catch (Exception e) {
			logger.warn("Unable to start the SCP on port "+AdminService.port);
			scp = null;
			return false;
		}
		scp.addDicomEventListener(this);
		running = true;
		return true;
	}

	//Set up the properties that a DicomStorageScp
	//requires and then instantiate a new DicomStorageScp.
	private DicomStorageScp initializeDicom() {
		Properties p = new Properties();
		p.setProperty("storage-scp-aet",AdminService.aeTitle);
		p.setProperty("port",AdminService.port);
		p.setProperty("dest",dicomstoreFile.getAbsolutePath());
		Dicom.initialize(p);
		return Dicom.getStorageScp();
	}

	/**
	 * The DicomEventListener implementation; listens for DICOM objects appearing in
	 * the dicom-store directory and moves them to the dicom-import directory to queue
	 * them for the anonymizer.
	 * @param event the event which occurred.
	 */
	public void dicomEventOccurred(DicomEvent event) {
		if ((event.getStatus() == 0) &&
			event.serviceAsString(event.getService()).equals("C_STORE_RQ")) {

			File inFile = new File(event.getFilename());
			logger.debug("C_STORE_RQ received for "+inFile);

			//Store the new file in the import directory,
			//adding the calledAET as a suffix in square brackets
			String calledAET = event.getCalledAET();
			String outName = inFile.getName() + "[" + calledAET + "]";
			File outFile = new File(dicomimportFile, outName);
			boolean renameResult = inFile.renameTo(outFile);
			if (!renameResult)
				logger.warn(
					"Unable to transfer the received DICOM object to the import directory:\n"
					+"Received file: "+inFile+"\n"
					+"Output file:   "+outFile);
		}
	}

	//Function to anonymize files in the import directory
	//and insert them in the specified file cabinet. If
	//a destination AE Title is available and it corresponds
	//to a username, then put the file in the personal file
	//cabinet of the user, under Personal/Files/DICOM/StudyInstanceUID;
	//otherwise, put it in Shared\/Files/DICOM/StudyInstanceUID.
	private void processDicomImportFiles() {
		File[] importFiles = dicomimportFile.listFiles();
		for (int k=0; k<importFiles.length; k++) {
			File next = importFiles[k];
			try {
				boolean okay = true;
				//If enabled, anonymize in place
				if (AdminService.anonymizerEnabled) {
					//Reload the properties every time so we always have the latest.
					Properties anprops = loadProperties(anonymizerFilename);
					String exceptions =
						DicomAnonymizer.anonymize(
							next, next,
							anprops, null,
							new LocalRemapper(), false, false);
					okay =
						(exceptions.indexOf("!quarantine!") == -1) &&
						(exceptions.indexOf("!error!") == -1);
					if (!okay)
						logger.debug(
							"Quarantine or error call on "+next);
				}
				if (okay) {
					DicomObject dob = new DicomObject(next);
					String siUID = dob.getStudyInstanceUID();
					String sopiUID = dob.getSOPInstanceUID();

					//See if we can use the personal files.
					//Note: the calledAET is a suffix to the filename.
					//It is taken to be the username of the user who
					//owns the object.
					String username = dob.getFile().getName();
					int k1 = username.lastIndexOf("[");
					int k2 = username.lastIndexOf("]");
					if ((k1 != -1) && (k2 > k1)) {
						username = username.substring(k1+1, k2);
					}
					else username = "";
					File base = new File(AdminService.root, "dept");;
					if (!username.equals("")) {
						TomcatUsers users = TomcatUsers.getInstance(base); //(any old file under the webapps tree)
						TomcatUser user = users.getTomcatUser(username);
						if (user != null) {
							base = new File(AdminService.root, "users");
							base = new File(base, username);
						}
					}
					File files = new File(base,"Files");
					File dicom = new File(files,"DICOM");
					File dir = new File(dicom, siUID);
					File icons = new File(base, "Icons");
					File iconsDICOM = new File(icons,"DICOM");
					File iconDir = new File(iconsDICOM, siUID);
					dir.mkdirs();
					iconDir.mkdirs();
					File outFile = new File(dir, sopiUID+".dcm");
					FileUtil.copyFile(next, outFile);
					FileService.makeIcon(outFile, iconDir);
				}
			}
			catch (Exception ignore) { }
			//If the file is still in the import directory,
			//quarantine it to prevent an infinite loop.
			if (next.exists()) {
				File q = new File(quarantineFile,next.getName());
				if (q.exists()) q.delete();
				next.renameTo(q);
			}
			System.gc(); //force a garbage collection to try to reduce paging
			yield();
		}
	}

	//Load a properties file.
	private Properties loadProperties(String filename) {
		File propFile = new File(AdminService.root,filename);
		Properties props = new Properties();
		try { props.load(new FileInputStream(propFile)); }
		catch (Exception ignore) { }
		return props;
	}

}