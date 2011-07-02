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
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.rsna.dicom.Dicom;
import org.rsna.dicom.DicomEvent;
import org.rsna.dicom.DicomEventListener;
import org.rsna.dicom.DicomStorageScp;
import org.rsna.mircsite.anonymizer.*;
import org.rsna.mircsite.log.Log;
import org.rsna.mircsite.storageservice.MircDocument;
import org.rsna.mircsite.storageservice.StorageConfig;
import org.rsna.mircsite.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The Thread that receives DicomObjects from the DICOM Storage SCP,
 * anonymizes them, and queues them for the HttpExportProcessor.
 */
public class ObjectProcessor extends Thread implements DicomEventListener {

	static final String processorServiceName = "ObjectProcessor";
	static final String dicomImportServiceName = "DicomImportService";

	static Logger processorLog = null;
	static final Logger logger = Logger.getLogger(ObjectProcessor.class);

	boolean running = false;
	boolean reinitialize = false;
	String storePath;
	String[] httpExportDirectories = null;
	File[] httpExportDirectoryFiles = null;
	String[] dicomExportDirectories = null;
	File[] dicomExportDirectoryFiles = null;
	Properties dicomAnonymizerProperties = null;
	Properties lookupTableProperties = null;

	static DicomStorageScp scp = null;

	/**
	 * Class constructor; creates a new instance of the ObjectProcessor
	 * and sets itself to run at the lowest possible priority.
	 */
	public ObjectProcessor() {
		running = false;
		initialize();
		this.setPriority(Thread.MIN_PRIORITY); //run at the lowest priority
	}

	//Load the anonymizer.properties file.
	//This function is called whenever the Clinical Trial Service is restarted,
	//allowing new anonymization scripts to be loaded.
	private void initialize() {
		reinitialize = false;
		try {
			File propFile = new File(TrialConfig.basepath + TrialConfig.dicomAnonymizerFilename);
			dicomAnonymizerProperties = new Properties();
			dicomAnonymizerProperties.load(new FileInputStream(propFile));
			File lkupFile = new File(TrialConfig.basepath + TrialConfig.lookupTableFilename);
			lookupTableProperties = new Properties();
			try { lookupTableProperties.load(new FileInputStream(lkupFile)); }
			catch (Exception ex) { lookupTableProperties = null; }
			if (TrialConfig.dicomImportAnonymizerEnabled())
				Log.message(processorServiceName+": DicomAnonymizer initialized");
			else Log.message(processorServiceName+": DicomAnonymizer not enabled");
		}
		catch (Exception e) {
			Log.message(processorServiceName+": Unable to configure the DicomAnonymizer");
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
	 * Restart the ObjectProcessor. Set a flag to cause the service to
	 * reinitialize after the next pass through the import directories.
	 */
	public void restart() {
		reinitialize = true;
	}

	/**
	 * The Runnable interface implementation. Process the import directories
	 * and send the files, then sleep for 10 seconds and check again.
	 */
	public void run() {
		startSCP(this); //Start the SCP.
		running = true;
		Log.message(processorServiceName+": Started");
		while (!interrupted()) {
			try {
				processDicomImportFiles();
				processHttpImportFiles();
				sleep(10000);
			}
			catch (Exception e) {
				running = false;
				Log.message(processorServiceName+": Interrupted");
				return;
			}
			if (reinitialize) initialize();
		}
		Log.message(processorServiceName+": Interrupt received");
	}

	/**
	 * React to a change in the Storage SCP AE Title or port.
	 * If the SCP isn't instantiated, do nothing; otherwise, stop
	 * it; create a new one; and register as a listener.
	 */
	 public static void scpParamsChanged(ObjectProcessor dop) {
		//If the store isn't instantiated, don't do anything.
		if (scp == null) return;
		startSCP(dop);
	}

	//Initialize and start the SCP
	private static void startSCP(ObjectProcessor op) {
		if (scp != null) {
			//There is already a storage SCP; stop it.
			//This throws an exception, but ignore it.
			try { scp.stop(); }
			catch (Exception ignore) { }
			scp = null;
		}

		//Now get a new SCP with the new params.
		scp = getDicomStore();

		//Start listening.
		scp.addDicomEventListener(op);

		//And start the scp.
		try {
			scp.start();
			Log.message(
				dicomImportServiceName+": "+TrialConfig.getDicomStoreAETitle()+
				" Storage SCP started on port " + TrialConfig.getDicomStorePort());
		}
		catch (Exception e) {
			Log.message(dicomImportServiceName+": SCP failed to start<br>" + e.getMessage());
		}
	 }

	//Get and initialize the DICOM Storage SCP
	private static DicomStorageScp getDicomStore() {
		String aetitle, port;
		Properties p = new Properties();
		aetitle = TrialConfig.getDicomStoreAETitle();
		port = TrialConfig.getDicomStorePort();
		p.setProperty("storage-scp-aet",aetitle);
		p.setProperty("port",port);
		p.setProperty("dest",TrialConfig.basepath + TrialConfig.dicomStoreDir);
		Dicom.initialize(p);
		return Dicom.getStorageScp();
	}

	/**
	 * The DicomEventListener implementation. Listen for an event from
	 * the SCP, log the file, and move it to the dicom-import directory for
	 * anonymization and export.
	 * @param event the event identifying the file that was received.
	 */
	public void dicomEventOccurred(DicomEvent event) {
		if ((event.getStatus() == 0) && event.serviceAsString(event.getService()).equals("C_STORE_RQ")) {
			File inFile = new File(event.getFilename());
			Log.message(dicomImportServiceName+": Image received: " + inFile.getName());
			//Make the output directory in case it doesn't exist.
			File outDir = new File(TrialConfig.basepath + TrialConfig.dicomImportDir);
			outDir.mkdirs();
			//Put the new file in it, using the overwrite attribute of the trial to determine
			//whether duplicate SOPInstanceUIDs are to be renamed so as not to lose them.
			FileObject fileObject = new FileObject(inFile);
			fileObject.moveToDirectory(outDir,TrialConfig.allowOverwrite());
		}
		else if (event.getStatus() != 0xff00)
			Log.message(dicomImportServiceName+": unexpected status: "+event.toStringNoPath());
	}

	//Look through the dicom-import directory and process
	//all the files there. Note that these are actual files,
	//not queue elements.
	private void processDicomImportFiles() {
		File importDirFile = new File(TrialConfig.basepath + TrialConfig.dicomImportDir);
		if (!importDirFile.exists()) return;
		File[] files = importDirFile.listFiles();
		for (int k=0; k<files.length; k++) {
			File next = files[k];
			if (files[k].canRead() && files[k].canWrite()) {
				try {
					boolean forceQuarantine = false;
					httpExportDirectories = TrialConfig.getHttpExportDirectories();
					httpExportDirectoryFiles = TrialConfig.getHttpExportDirectoryFiles();
					//If enabled, anonymize.
					//Note: in normal clinical trials, the anonymizer is enabled and de-identified
					//images are transmitted by the HttpExportProcessor.
					//For normal research applications, images are not anonymized and the document
					//creates directories of identified and de-identified images. In this situation,
					//the anonymizer is disabled and the document takes care of de-identifying the
					//images.
					//Note: in research applications, the document takes its elements from the
					//identified (e.g., containing PHI) image, so such documents contain PHI.
					if (TrialConfig.dicomImportAnonymizerEnabled()) {
						String exceptions =
							DicomAnonymizer.anonymize(
								next, next,
								dicomAnonymizerProperties, lookupTableProperties,
								new LocalRemapper(), false, false);
						if (!exceptions.equals("")) {
							if (exceptions.indexOf("!quarantine!") != -1) {
								Log.message("<font color=\"red\">DicomAnonymizer quarantine call:<br>"
											+next.getName()+"</font>");
								forceQuarantine = true;
							}
							else if (exceptions.indexOf("!error!") != -1) {
								Log.message("<font color=\"red\">DicomAnonymizer error call: "+exceptions+"<br>"
											+next.getName()+"</font>");
								forceQuarantine = true;
							}
							else {
								//Note: the anonymizer logs the exception list to Log4J,
								//so we just have to log it to the displayed log.
								Log.message("Anonymization exceptions: " + exceptions+"<br>"+next.getName());
							}
						}
						else {
							Log.message("<font color=\"blue\">Anonymization complete"+"<br>"+next.getName()+"</font>");
						}
					}
					if (!forceQuarantine) {
						//get the document for this study or create it if necessary
						DicomObject nextObject = new DicomObject(next);
						MircDocument td = new MircDocument(nextObject);
						//put in the object and store the updated document
						td.insert(
							nextObject,
							TrialConfig.allowOverwrite(),
							TrialConfig.dicomImportAnonymizerEnabled(),
							dicomAnonymizerProperties, lookupTableProperties);

						//export the object
						if (httpExportDirectoryFiles != null) {
							for (int i=0; i<httpExportDirectoryFiles.length; i++) {
								try {
									ExportQueueElement eqe =
										ExportQueueElement.createEQE(nextObject);
									eqe.queue(httpExportDirectoryFiles[i]);
								}
								catch (Exception e) {
									Log.message(processorServiceName+": " + httpExportDirectories[i]
												+ " export failed:" + next.getName());
									logger.warn(httpExportDirectories[i]
												+ " export failed:" + next.getName());
								}
							}
						}
						if (!queueForDatabase(nextObject))
							Log.message(Quarantine.file(next,processorServiceName));
						else
							Log.message(processorServiceName+": Processing complete: "+next.getName());

						//log the event if logging is enabled
						makeTrialLogEntry("dicom-import",nextObject);
					}

					//if the file still exists, then either the object was
					//set for quarantining or there is a bug somewhere;
					//log it and quarantine the file so we don't
					//fall into an infinite loop.
					if (next.exists()) {
						logger.warn("Forced quarantine: "+next);
						Log.message(Quarantine.file(next,processorServiceName));
					}
				}
				catch (Exception e) {
					Log.message(processorServiceName+": Error during processing: "
								+ next.getName() + "  - " + e.getMessage());
					logger.warn("Error during processing: object quarantined: "
								+ next.getName(),e);
					Log.message(Quarantine.file(next,processorServiceName));
				}
				yield();
			}
		}
	}

	//Look through the http-import directory and process all
	//the files there, oldest first. Note that these are actual
	//files, not queue elements.
	private void processHttpImportFiles() {
		File importDirFile = new File(TrialConfig.basepath + TrialConfig.httpImportDir);
		if (!importDirFile.exists()) return;
		File[] files = importDirFile.listFiles();
		for (int k=0; k<files.length; k++) {
			File next = files[k];
			if (next.canRead() && next.canWrite()) {
				FileObject fileObject = FileObject.getObject(next);
				if (preprocess(fileObject)) {
					process(fileObject);
					if (!queueForDatabase(fileObject))
						Log.message(Quarantine.file(next,processorServiceName));
					else
						Log.message(processorServiceName+": Processing complete: "+next.getName());
				}

				//If the file still exists, then there must be a bug
				//somewhere; log it and quarantine the file so we don't
				//fall into an infinite loop.
				if (next.exists()) {
					logger.warn(
						"File still in queue after processing:\n"+next+
						"The file will be quarantined.");
					Log.message(Quarantine.file(next,processorServiceName));
				}
				Thread.currentThread().yield();
			}
		}
	}

	//Preprocess a FileObject.
	private boolean preprocess(FileObject fileObject) {
		if (TrialConfig.getPreprocessorEnabled().equals("yes")) {
			//Load the preprocessor class specified in the TrialConfig.
			//Reload each time in case the class changes behind our back.
			PreprocessorAdapter ppa = null;
			String className = TrialConfig.getPreprocessorClassName();
			try {
				Class theClass = Class.forName(className);
				ppa = (PreprocessorAdapter)theClass.newInstance();
				if (fileObject instanceof DicomObject) return ppa.process((DicomObject)fileObject);
				if (fileObject instanceof XmlObject) return ppa.process((XmlObject)fileObject);
				if (fileObject instanceof ZipObject) return ppa.process((ZipObject)fileObject);
				return ppa.process(fileObject);
			}
			catch (Exception ex) {
				Log.message("Exception while loading or running the preprocessor: " + className);
				logger.warn("Exception while loading or running the preprocessor: " + className);
				return false;
			}
		}
		return true;
	}

	//Process a FileObject.
	private void process(FileObject fileObject) {
		if (fileObject instanceof DicomObject) process((DicomObject)fileObject);
		else if (fileObject instanceof XmlObject) process((XmlObject)fileObject);
		else if (fileObject instanceof ZipObject) process((ZipObject)fileObject);
		else {
			try {
				//This is a file that can't be put into a MIRCdocument directory.
				//Put the file in the bullpen to get it out of the input queue
				//and to place it somewhere for the DatabaseExportService to find.
				fileObject.moveToDirectory(StorageConfig.getBullpenFile());
			}
			catch (Exception ex) { }
		}
	}

	//Process a DICOM file.
	private void process(DicomObject dicomObject) {
		try {
			dicomObject.setExtension(".dcm");
			//get the document for this study or create it if necessary
			MircDocument td = new MircDocument(dicomObject);
			//Put in the object and store the updated document.
			//Note that since all http imports should have been
			//anonymized before transmission over the internet,
			//we assume that is the case. There is no way to tell.
			td.insert(dicomObject,TrialConfig.allowOverwrite(),true,null,null);
			//export the image via DICOM if in auto mode
			dicomExportDirectories = TrialConfig.getDicomExportDirectories();
			dicomExportDirectoryFiles = TrialConfig.getDicomExportDirectoryFiles();
			if (TrialConfig.getDicomExportMode().equals("auto") && (dicomExportDirectoryFiles != null)) {
				for (int i=0; i<dicomExportDirectoryFiles.length; i++) {
					try {
						ExportQueueElement eqe = ExportQueueElement.createEQE(dicomObject);
						eqe.queue(dicomExportDirectoryFiles[i]);
					}
					catch (Exception e) {
						String name = dicomObject.getFile().getName();
						Log.message(processorServiceName+": " + dicomExportDirectories[i]
									+ " DICOM export failed:" + name);
						logger.warn(dicomExportDirectories[i]
									+ " DICOM export failed:" + name);
					}
				}
			}
			//log the object if logging is enabled
			makeTrialLogEntry("http-import",dicomObject);
		}
		catch (Exception notDicom) { }
	}

	//Process an XML file.
	private void process(XmlObject xmlObject) {
		try {
			String uid = xmlObject.getUID();
			if (uid.equals("")) {
				uid = xmlObject.getFile().getName().replaceAll("\\s","");
				if (uid.endsWith(".md")) uid = uid.substring(0,uid.length()-3);
			}
			uid += ".xml";
			String studyUID = xmlObject.getStudyUID();
			if (!studyUID.equals("")) {
				MircDocument td = new MircDocument(xmlObject);
				td.insert(xmlObject,uid);
			}
		}
		catch (Exception ex) { }
	}

	//Process a zip file.
	private void process(ZipObject zipObject) {
		try {
			String uid = zipObject.getUID();
			if (uid.equals("")) {
				uid = zipObject.getFile().getName().replaceAll("\\s","");
				if (uid.endsWith(".md")) uid = uid.substring(0,uid.length()-3);
			}
			uid += ".zip";
			String studyUID = zipObject.getStudyUID();
			if (!studyUID.equals("")) {
				MircDocument td = new MircDocument(zipObject);
				td.insert(zipObject,uid);
			}
		}
		catch (Exception ex) { }
	}

	//Queue a file for the DatabaseExportService.
	private boolean queueForDatabase(FileObject fileObject) {
		if (TrialConfig.getDatabaseExportMode().equals("auto")) {
			File databaseExportDirectoryFile = TrialConfig.getDatabaseExportDirectoryFile();
			try {
				ExportQueueElement eqe = ExportQueueElement.createEQE(fileObject);
				eqe.queue(databaseExportDirectoryFile);
			}
			catch (Exception e) {
				Log.message(processorServiceName+": Unable to queue " + fileObject.getFile().getName()
							+ " for database export");
				logger.warn("Unable to queue " + fileObject.getFile().getName() + " for database export");
			}
		}
		return true;
	}

	//Create a Log4J log file with a monthly rolling appender and populate
	//it with information from the dataset in a csv format so the log file
	//can be opened and processed with a spreadsheet.
	private void makeTrialLogEntry(String service, DicomObject dicomObject) {
		if (!TrialConfig.log()) return;
		if (processorLog == null) {
			try {
				processorLog = Logger.getLogger("trial");
				processorLog.setAdditivity(false);
				PatternLayout layout = new PatternLayout("%d{yyyy-MM-dd},%d{HH:mm:ss},%m%n");
				File logs = new File(TrialConfig.basepath + TrialConfig.logDirectory);
				logs.mkdirs();
				DailyRollingFileAppender appender =
					new DailyRollingFileAppender(
						layout,
						TrialConfig.basepath + TrialConfig.logDirectory
									+ File.separator + TrialConfig.serviceName + ".csv",
						"'.'yyyy-MM");
				processorLog.addAppender(appender);
				processorLog.setLevel((Level)Level.ALL);
			}
			catch (Exception e) {
				logger.warn("Unable to instantiate a trial logger");
				processorLog = null;
				return;
			}
		}
		processorLog.info( service  + "," + dicomObject.getPatientName()
									+ "," + dicomObject.getPatientID()
									+ "," + dicomObject.getModality()
									+ "," + dicomObject.getSeriesNumber()
									+ "," + dicomObject.getAcquisitionNumber()
									+ "," + dicomObject.getInstanceNumber());
	}

}