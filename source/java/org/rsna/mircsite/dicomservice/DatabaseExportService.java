/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.dicomservice;

import java.io.*;
import java.util.Properties;
import org.apache.log4j.Logger;
import org.rsna.mircsite.anonymizer.DicomAnonymizer;
import org.rsna.mircsite.anonymizer.LocalRemapper;
import org.rsna.mircsite.anonymizer.XmlAnonymizer;
import org.rsna.mircsite.log.Log;
import org.rsna.mircsite.util.DatabaseAdapter;
import org.rsna.mircsite.util.DicomObject;
import org.rsna.mircsite.util.FileObject;
import org.rsna.mircsite.util.XmlObject;
import org.rsna.mircsite.util.ZipObject;
import org.rsna.mircsite.util.FileUtil;
import org.rsna.mircsite.util.XmlUtil;

/**
 * The Thread that exports DicomObjects to a database.
 */
public class DatabaseExportService extends Thread {

	boolean running = false;
	static final String serviceName = "DatabaseExportService";
	static final Logger logger = Logger.getLogger(DatabaseExportService.class);

	File lastParent = null;
	String lastURL = "";
	DatabaseAdapter db = null;

	/**
	 * Class constructor; creates a new instance of the DatabaseExportService.
	 */
	public DatabaseExportService() {
	}

	/**
	 * Get the status text for display by the admin service.
	 */
	public String getStatus() {
		return running ? "running" : "not running";
	}

	/**
	 * The Runnable interface implementation. Process the queue directories
	 * and send the files, then sleep for 10 seconds and check again.
	 */
	public void run() {
		running = false;
		if ((db = getDatabase()) == null) {
			Log.message(serviceName+": Not started (database class not loaded)");
			return;
		}
		running = true;
		Log.message(serviceName+": Started");

		while (running && !interrupted()) {
			try {
				processFiles();
				sleep(TrialConfig.getDatabaseExportInterval());
			}
			catch (Exception e) {
				Log.message(serviceName + " exception:<br>" + e.getMessage());
				logger.error(e.getMessage(),e);
				running = false;
			}
		}
		Log.message(serviceName+": exit.");
		running = false;
		if (db != null) db.shutdown();
	}

	/**
	 * Restart the DatabaseExportService.
	 */
	public void restart() {
		if (db != null) {
			db.reset();
			db.shutdown();
			db = null;
			db = getDatabase();
		}
	}

	//Get the DatabaseAdapter. If it is already instantiated, return the
	//current one. If it is not instantiated, create it.
	private DatabaseAdapter getDatabase() {
		if (db != null) return db;
		//Load the Database class specified in the TrialConfig.
		String className = TrialConfig.getDatabaseClassName();
		try {
			Class theClass = Class.forName(className);
			return (DatabaseAdapter)theClass.newInstance();
		}
		catch (Exception ex) {
			logger.warn(
				serviceName + ": Unable to load the Database class: " + className, ex);
		}
		return null;
	}

	//Look through the export directory and send all the files there.
	//Note that the files are actually queue elements (text files
	//containing the absolute file path to the DICOM object to be exported.
	private void processFiles() throws Exception {
		if (db == null) return;
		File directoryFile = TrialConfig.getDatabaseExportDirectoryFile();
		if (directoryFile == null) return;
		directoryFile.mkdirs();
		File[] files = directoryFile.listFiles();
		if (files.length == 0) return;

		File file = null;
		int status;
		ExportQueueElement eqe = null;
		boolean connected = false;
		status = db.connect();
		if (status == DatabaseAdapter.STATUS_OK) connected = true;
		else return; //the connection failed, go back and wait a while

		//We got connected, process the files.
		for (int k=0; k<files.length; k++) {
			eqe = new ExportQueueElement(files[k]);
			file = eqe.getQueuedFile();
			if (file != null) {
				status = processFile(file);
				switch (status) {
					case DatabaseAdapter.STATUS_OK:
						Log.message(serviceName+": Processing complete:<br>"+file.getName()+"</font>");
						eqe.delete();
						break;
					case DatabaseAdapter.STATUS_FAIL:
						Log.message(serviceName+": "+Quarantine.file(eqe,serviceName));
						logger.warn("Object quarantined: " + eqe.getName());
						eqe.delete();
						break;
					case DatabaseAdapter.STATUS_WAIT:
						db.disconnect();
						return;
				}
			}
		}
		db.disconnect();
		System.gc(); //force a garbage collection to try to reduce paging
		yield();
	}

	//Process one file
	private int processFile(File file) {
		FileObject fileObject;
		String url = getMIRCdocumentURL(file);
		int status;

		//Get the object as whatever type it is.
		fileObject = FileObject.getObject(file);

		//See if it's a DicomObject.
		if (fileObject instanceof DicomObject) {
			try {
				DicomObject dicomObject = (DicomObject)fileObject;
				status = db.process(dicomObject, url);
				if ((status == db.STATUS_OK) && TrialConfig.databaseExportAnonymizerEnabled())
					dicomAnonymize(dicomObject);
				return status;
			}
			catch (Exception e) {
				Log.message(serviceName+": Exception in processFile("+file+"):<br>"+e.getMessage());
				return DatabaseAdapter.STATUS_FAIL;
			}
		}

		//If we get here, see if it's a ZipObject.
		if (fileObject instanceof ZipObject) {
			try {
				ZipObject zipObject = (ZipObject)fileObject;
				status = db.process(zipObject, url);
				//Note: ZipObjects are not anonymized because
				//there is no way to know what they contain.
				return status;
			}
			catch (Exception e) {
				Log.message(serviceName+": Exception in processFile("+file+"):<br>"+e.getMessage());
				return DatabaseAdapter.STATUS_FAIL;
			}
		}

		//If we get here, see if it's an XmlObject.
		if (fileObject instanceof XmlObject) {
			try {
				XmlObject xmlObject = (XmlObject)fileObject;
				status = db.process(xmlObject, url);
				if (TrialConfig.databaseExportAnonymizerEnabled())
					xmlAnonymize(xmlObject);
				return status;
			}
			catch (Exception e) {
				Log.message(serviceName+": Exception in processFile("+file+"):<br>"+e.getMessage());
				return DatabaseAdapter.STATUS_FAIL;
			}
		}

		//If we get here, it's justa plain FileObject.
		if (fileObject != null) {
			try {
				status = db.process(fileObject, url);
				//Note: FileObjects are not anonymized because
				//there is no way to know what they contain.
				return status;
			}
			catch (Exception e) {
				Log.message(serviceName+": Exception in processFile("+file+"):<br>"+e.getMessage());
				return DatabaseAdapter.STATUS_FAIL;
			}
		}
		return DatabaseAdapter.STATUS_FAIL;
	}

	private boolean xmlAnonymize(XmlObject xmlObject) {
		File file = xmlObject.getFile();
		File scriptFile = new File(TrialConfig.basepath + TrialConfig.xmlAnonymizerFilename);
		String anonymizerScript = FileUtil.getFileText(scriptFile);
		if (!XmlAnonymizer.anonymize(file, file, anonymizerScript, new LocalRemapper())) {
			Log.message(serviceName+": Anonymization failure:<br>"+file.getName());
			return false;
		}
		Log.message("<font color=\"blue\">"+
		serviceName+": Anonymization complete<br>"+
		file.getName()+"</font>");
		return true;
	}

	private boolean dicomAnonymize(DicomObject dicomObject) {
		File file = dicomObject.getFile();
		File propFile = new File(TrialConfig.basepath + TrialConfig.dicomAnonymizerFilename);
		Properties anonymizerProperties = loadProperties(propFile);
		if (anonymizerProperties == null) return false;
		File lookupFile = new File(TrialConfig.basepath + TrialConfig.lookupTableFilename);
		Properties lookupTableProperties = loadProperties(lookupFile);
		String exceptions =
			DicomAnonymizer.anonymize(
				file, file,
				anonymizerProperties, lookupTableProperties,
				new LocalRemapper(), false, false);
		if (!exceptions.equals("")) {
			Log.message(serviceName+": Anonymization exceptions: " + exceptions+"<br>"+file.getName());
			return false;
		}
		Log.message("<font color=\"blue\">"+
			serviceName+": Anonymization complete<br>"+
			file.getName()+"</font>");
		return true;
	}

	private Properties loadProperties(File file) {
		Properties props = new Properties();
		try {
			props.load(new FileInputStream(file));
			return props;
		}
		catch (Exception ex) { }
		return null;
	}

	//Get the URL of the MIRCdocument that is in the directory with a file.
	//This algorithm depends on their being only one MIRCdocument file in
	//the directory. It also depends on that directory being in the
	//documents directory tree of the storage service.
	private String getMIRCdocumentURL(File file) {
		File parent = file.getParentFile();
		if (parent.equals(lastParent)) return lastURL;
		File[] docs = parent.listFiles(new Filter());
		if ((docs == null) || (docs.length == 0)) return "";
		String path = docs[0].getAbsolutePath();
		path = path.replace("\\","/");
		int k = path.indexOf("/documents");
		if (k == -1) return "";
		k = path.substring(0,k).lastIndexOf("/");
		if (k == -1) return "";
		lastParent = parent;
		lastURL = path.substring(k);
		return lastURL;
	}

	private class Filter implements FileFilter {
		public boolean accept(File file) {
			try {
				if (file.getName().toLowerCase().endsWith(".xml") &&
					XmlUtil.getDocumentElementName(file).equals("MIRCdocument")) {
						return true;
				}
			}
			catch (Exception ex) { }
			return false;
		}
	}

}
