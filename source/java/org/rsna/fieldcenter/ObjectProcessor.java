/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.fieldcenter;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.SwingUtilities;
import javax.swing.event.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.rsna.dicom.Dicom;
import org.rsna.dicom.DicomEvent;
import org.rsna.dicom.DicomEventListener;
import org.rsna.dicom.DicomStorageScp;
import org.rsna.mircsite.anonymizer.DicomAnonymizer;
import org.rsna.mircsite.anonymizer.IdTable;
import org.rsna.mircsite.anonymizer.LocalRemapper;
import org.rsna.mircsite.anonymizer.Remapper;
import org.rsna.mircsite.anonymizer.RemoteRemapper;
import org.rsna.mircsite.anonymizer.XmlAnonymizer;
import org.rsna.mircsite.log.Log;
import org.rsna.util.ApplicationProperties;
import org.rsna.util.FileUtil;
import org.rsna.util.GeneralFileFilter;
import org.rsna.util.PropertyEvent;
import org.rsna.util.PropertyListener;
import org.rsna.util.TransferEvent;
import org.rsna.util.TransferListener;
import org.rsna.util.UpdateUtil;
import org.w3c.dom.Document;

/**
 * The Thread that receives DicomObjects from the DICOM Storage SCP
 * and metadata files from the HttpReceiver, anonymizes them,
 * and queues them for the HttpExportProcessor.
 */
public class ObjectProcessor extends Thread
						implements DicomEventListener, PropertyListener, HttpFileEventListener {

	static final String processorServiceName = "ObjectProcessor";
	static final String dicomImportServiceName = "DicomImportService";
	static final String httpImportServiceName = "HttpImportService";

	static final Logger processorLog = Logger.getLogger("trial");
	static final Logger logger = Logger.getLogger(ObjectProcessor.class);

	File dicomstoreFile;
	File dicomimportFile;
	File httpstoreFile;
	File httpimportFile;
	File httpexportFile;
	File receivedlogFile;
	File anonQuarantineFile;
	File exportQuarantineFile;
	DicomStorageScp scp;
	HttpReceiver receiver;
	EventListenerList listenerList;
	boolean anonymizerEnabled;
	boolean exportEnabled;
	ApplicationProperties props;
	String scpport;
	String aetitle;
	String httpport;
	String protocol;
	boolean dailySaveDone = false;

	/**
	 * Class constructor; creates a new instance of the ObjectProcessor
	 * and sets itself to run at the lowest possible priority.
	 * @param props the application properties, indicating whether anonymization
	 * and exporting is enabled and carrying the SCP parameters.
	 */
	public ObjectProcessor(ApplicationProperties props) {
		listenerList = new EventListenerList();
		this.props = props;
		props.addPropertyListener(this);
		dicomstoreFile = new File(FieldCenter.dicomstoreFilename);
		dicomimportFile = new File(FieldCenter.dicomimportFilename);
		httpexportFile = new File(FieldCenter.exportFilename);
		anonQuarantineFile = new File(FieldCenter.anonQuarFilename);
		exportQuarantineFile = new File(FieldCenter.exportQuarFilename);
		httpstoreFile = new File(FieldCenter.httpstoreFilename);
		httpimportFile = new File(FieldCenter.httpimportFilename);
		File objectlogFile = new File(FieldCenter.objectlogFilename);
		receivedlogFile = new File(objectlogFile,"received");
		dicomstoreFile.mkdirs();
		dicomimportFile.mkdirs();
		httpstoreFile.mkdirs();
		httpimportFile.mkdirs();
		httpexportFile.mkdirs();
		anonQuarantineFile.mkdirs();
		exportQuarantineFile.mkdirs();
		receivedlogFile.mkdirs();
		this.setPriority(Thread.MIN_PRIORITY); //run at the lowest priority
	}

	/**
	 * The Runnable implementation; starts the thread, starts the DicomStorageScp,
	 * and polls the import queue directory, processing files when they appear.
	 */
	public void run() {
		restartSCP();
		restartHTTP();
		Log.message(processorServiceName+": Started");
		sendTransferEvent(processorServiceName+": Started");
		while (!interrupted()) {
			try {
				processDicomImportFiles();
				processHttpImportFiles();
				saveIdTable();
				sleep(10000);
			}
			catch (Exception e) {
				Log.message(processorServiceName+": Interrupted");
				sendTransferEvent(processorServiceName+": Interrupted");
				return;
			}
		}
		Log.message(processorServiceName+": Interrupt received");
		sendTransferEvent(processorServiceName+": Interrupt received");
	}

	//Save the IdTable if it is 0400 and it has changed.
	private void saveIdTable() {
		Calendar calendar = new GregorianCalendar();
		int hour = calendar.get(Calendar.HOUR_OF_DAY);
		if (hour == 4) {
			if (!dailySaveDone) {
				IdTable.storeNow(false);
				dailySaveDone = true;
			}
		}
		else dailySaveDone = false;
	}

	/**
	 * The PropertyListener implementation; listens for a change in
	 * the application properties object.
	 * @param event the event indicating that the properties have changed.
	 */
	public void propertyChanged(PropertyEvent event) {
		//See if the SCP AE Title or port have changed,
		//and if so, restart the SCP.
		if (scp != null) {
			if (!props.getProperty("scp-aetitle").trim().equals(aetitle) ||
				!props.getProperty("scp-port").trim().equals(scpport)) {
				restartSCP();
			}
		}
		//See if the HTTP protocol or port have changed,
		//and if so, restart the receiver.
		if (receiver != null) {
			if (!props.getProperty("http-protocol").trim().equals(protocol) ||
				!props.getProperty("http-port").trim().equals(httpport)) {
				restartHTTP();
			}
		}
	}

	//Stop the SCP if it is running.
	private void stopSCP() {
		if (scp == null) return;
		//The SCP throws an exception when it stops; ignore it.
		try { scp.stop(); }
		catch (Exception ignore) { }
		//Log the stoppage.
		Log.message(dicomImportServiceName+": "+aetitle+" stopped on port "+scpport);
		sendTransferEvent(dicomImportServiceName+": Stopped");
	}

	//Reinitialize and restart the SCP from the
	//current values in the application properties.
	private boolean restartSCP() {
		stopSCP();
		scp = initializeDicom(props);
		try { scp.start(); }
		catch (Exception e) {
			Log.message("<font color=\"red\">"+processorServiceName
						+": SCP failed to start<br>" + e.getMessage());
			sendTransferEvent(processorServiceName+": SCP failed to start");
			scp = null;
			return false;
		}
		scp.addDicomEventListener(this);
		Log.message(dicomImportServiceName+": "+aetitle+" started on port "+scpport);
		sendTransferEvent(dicomImportServiceName+": Started");
		return true;
	}

	//Set up the properties that a DicomStorageScp
	//requires and then instantiate a new DicomStorageScp.
	private DicomStorageScp initializeDicom(Properties props) {
		Properties p = new Properties();
		aetitle = props.getProperty("scp-aetitle").trim();
		scpport = props.getProperty("scp-port").trim();
		p.setProperty("storage-scp-aet",aetitle);
		p.setProperty("port",scpport);
		p.setProperty("dest",dicomstoreFile.getName());
		Dicom.initialize(p);
		return Dicom.getStorageScp();
	}

	//Stop the HttpReceiver if it is running.
	private void stopHTTP() {
		if (receiver == null) return;
		receiver.stopReceiver();
		//Log the stoppage.
		Log.message(
			httpImportServiceName + ": " +
			receiver.getProtocol() +
			" stopped on port " +
			receiver.getPort());
		receiver = null;
		sendTransferEvent(httpImportServiceName+": Stopped");
	}

	//Reinitialize and restart the HttpReceiver from the
	//current values in the application properties.
	private boolean restartHTTP() {
		stopHTTP();
		protocol = props.getProperty("http-protocol").trim();
		httpport = props.getProperty("http-port").trim();
		int port = 8444;
		try { port = Integer.parseInt(httpport); }
		catch (Exception ignore) { }
		try {
			receiver = new HttpReceiver(httpstoreFile,"http",port,(File)null,(String)null);
			receiver.start();
		}
		catch (Exception e) {
			Log.message("<font color=\"red\">"+processorServiceName
						+": HttpReceiver failed to start<br>" + e.getMessage());
			sendTransferEvent(processorServiceName+": HttpReceiver failed to start");
			receiver = null;
			return false;
		}
		receiver.addHttpFileEventListener(this);
		Log.message(
				httpImportServiceName + ": " +
				receiver.getProtocol() +
				" started on port " +
				receiver.getPort());
		sendTransferEvent(httpImportServiceName+": Started");
		return true;
	}

	/**
	 * The DicomEventListener implementation; listens for DICOM objects appearing in
	 * the dicom-store directory and moves them to the dicom-import directory to queue
	 * them for the anonymizer and/or HttpExportProcessor.
	 */
	public void dicomEventOccurred(DicomEvent e) {
		if ((e.getStatus() == 0) && e.serviceAsString(e.getService()).equals("C_STORE_RQ")) {
			File inFile = new File(e.getFilename());
			Log.message(dicomImportServiceName+": Image received:<br>" + inFile.getName());
			logger.info(dicomImportServiceName+": Image received: " + inFile.getName());
			sendTransferEvent(dicomImportServiceName+": Image received");
			//Store the new file in the dicom-import directory
			File outFile = new File(dicomimportFile,inFile.getName());
			inFile.renameTo(outFile);
		}
		else if (e.getStatus() != 0xff00) {
			Log.message(dicomImportServiceName+": handleDicomEvent: "+e.toStringNoPath());
			sendTransferEvent(dicomImportServiceName+": handleDicomEvent: "+e.toStringNoPath());
		}
	}

	/**
	 * The HttpFileEventListener implementation; listens for files appearing in
	 * the http-store directory and moves them to the http-import directory to queue
	 * them for the anonymizer and/or HttpExportProcessor.
	 */
	public void httpFileEventOccurred(HttpFileEvent e) {
		if (e.status == HttpFileEvent.RECEIVED) {
			File inFile = e.file;
			Log.message(httpImportServiceName+": File received:<br>" + inFile.getName());
			logger.info(httpImportServiceName+": File received: " + inFile.getName());
			sendTransferEvent(httpImportServiceName+": File received");
			//Store the new file in the http-import directory
			File outFile = new File(httpimportFile,inFile.getName());
			inFile.renameTo(outFile);
		}
		else {
			Log.message(httpImportServiceName+": handleHttpFileEvent: "+e.message);
			sendTransferEvent(httpImportServiceName+": handleHttpFileEvent: "+e.message);
		}
	}

	//Anonymize files in the dicom-import directory and pass them to the
	//http-export directory or to the quarantine if export is disabled.
	private void processDicomImportFiles() {
		GeneralFileFilter filter = new GeneralFileFilter();
		filter.setExtensions("*");
		filter.setMaxCount(100);
		File[] files = dicomimportFile.listFiles(filter);
		for (int k=0; k<files.length; k++) {
			File next = files[k];
			if (next.canRead() && next.canWrite()) {
				try {
					saveObject(next);
					boolean forceQuarantine = false;
					String anonEnb = props.getProperty("anonymizer-enabled");
					boolean anonymizerEnabled = !((anonEnb != null) && anonEnb.equals("false"));
					String ivrle = props.getProperty("forceIVRLE");
					boolean forceIVRLE = !((ivrle != null) && ivrle.equals("false"));
					//If enabled, anonymize in place
					if (anonymizerEnabled) {
						//Reload the properties every time so we always have the latest.
						//First, fetch the latest version from the update server, if enabled.
						UpdateUtil.getFile(new File(FieldCenter.dicomAnonymizerFilename),props);
						//Now load it from the local disk
						Properties anprops = loadProperties(FieldCenter.dicomAnonymizerFilename);
						Properties lkprops = loadProperties(FieldCenter.lookupTableFilename);
						Remapper remapper = getRemapper(props);
						String exceptions =
							DicomAnonymizer.anonymize(
								next, next,
								anprops, lkprops,
								remapper, forceIVRLE, false);
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
								Log.message("DicomAnonymization exceptions: "+ exceptions+"<br>"+next.getName());
							}
							sendTransferEvent("DICOM anonymization exceptions");
						}
						else {
							Log.message("<font color=\"blue\">DicomAnonymization complete"+"<br>"+next.getName()+"</font>");
							logger.info("DICOM anonymization complete"+": "+next.getName());
							sendTransferEvent("DICOM anonymization complete");
						}
					}
					String expEnb = props.getProperty("export-enabled");
					boolean exportEnabled = !((expEnb != null) && expEnb.equals("false"));
					if (exportEnabled && !forceQuarantine) {
						//Export the image
						File outFile = new File(httpexportFile,next.getName());
						next.renameTo(outFile);
						makeTrialLogEntry("dicom-import",outFile);
					}
					else if (forceQuarantine) {
						//Put the file in the anonymizer quarantine
						File q = new File(anonQuarantineFile,next.getName());
						if (q.exists()) q.delete();
						next.renameTo(q);
						sendTransferEvent("DICOM object quarantined");
					}
					else if (!exportEnabled) {
						//Put the file in the export quarantine
						File q = new File(exportQuarantineFile,next.getName());
						if (q.exists()) q.delete();
						next.renameTo(q);
						sendTransferEvent("DICOM object quarantined");
					}
				}
				catch (Exception e) {
					//This is an exception that shouldn't occur.
					//Put the file in the anonymizer quarantine.
					Log.message(processorServiceName+": <font color=\"red\">DICOM object quarantined:<br>"
								+ next.getName() + "<br>  -- " + e.getMessage()+"</font>");
					logger.warn("DICOM object quarantined: " + next.getName() + "  -" + e.getMessage(),e);
					sendTransferEvent("DICOM object quarantined");
					File q = new File(anonQuarantineFile,next.getName());
					if (q.exists()) q.delete();
					next.renameTo(q);
				}
				System.gc(); //force a garbage collection to try to reduce paging
				yield();
			}
		}
	}

	//Anonymize files in the http-import directory and pass them to the
	//http-export directory or to the quarantine if export is disabled.
	private void processHttpImportFiles() {
		GeneralFileFilter filter = new GeneralFileFilter();
		filter.setExtensions("*");
		filter.setMaxCount(100);
		File[] files = httpimportFile.listFiles(filter);
		for (int k=0; k<files.length; k++) {
			File next = files[k];
			if (next.canRead() && next.canWrite()) {
				boolean isXmlFile = parseFile(next);
				boolean isExportable = true;
				try {
					saveObject(next);
					String anonEnb = props.getProperty("anonymizer-enabled");
					boolean anonymizerEnabled = !((anonEnb != null) && anonEnb.equals("false"));
					//If enabled, anonymize in place
					if (anonymizerEnabled && isXmlFile) {
						//Only anonymize XML files. Others just export.
						//Reload the properties every time so we always have the latest.
						//First, fetch the latest version from the update server, if enabled.
						File scriptFile = new File(FieldCenter.xmlAnonymizerFilename);
						UpdateUtil.getFile(scriptFile,props);
						//Now load it from the local disk
						String script = FileUtil.getFileText(scriptFile);
						//Get a Remapper based on the current properties.
						Remapper remapper = getRemapper(props);
						//And now anonymize the file in place, setting the isExportable flag for later use.
						if (isExportable =
								XmlAnonymizer.anonymize(
									next, next, script, remapper)) {
							Log.message(
								"<font color=\"blue\">HTTP anonymization complete"+
								"<br>"+next.getName()+"</font>");
							logger.info("HTTP anonymization complete"+": "+next.getName());
							sendTransferEvent("HTTP anonymization complete");
						}
					}
					String expEnb = props.getProperty("export-enabled");
					boolean exportEnabled = !((expEnb != null) && expEnb.equals("false"));
					if (isExportable && exportEnabled) {
						//Export the object
						File outFile = new File(httpexportFile,next.getName());
						next.renameTo(outFile);
					}
					else if (!isExportable) {
						//Put the file in the  anonymizer quarantine
						File q = new File(anonQuarantineFile,next.getName());
						if (q.exists()) q.delete();
						next.renameTo(q);
						sendTransferEvent("HTTP object quarantined");
					}
					else if (!exportEnabled) {
						//Put the file in the  export quarantine
						File q = new File(exportQuarantineFile,next.getName());
						if (q.exists()) q.delete();
						next.renameTo(q);
						sendTransferEvent("HTTP object quarantined");
					}
				}
				catch (Exception e) {
					//This should not occur.
					//Put the file in the anonymizer quarantine.
					Log.message(processorServiceName+": <font color=\"red\">HTTP object quarantined:<br>"
								+ next.getName() + "<br>  -- " + e.getMessage()+"</font>");
					logger.warn("HTTP object quarantined: " + next.getName() + "  -" + e.getMessage(),e);
					sendTransferEvent("HTTP object quarantined");
					File q = new File(anonQuarantineFile,next.getName());
					if (q.exists()) q.delete();
					next.renameTo(q);
				}
				System.gc(); //force a garbage collection to try to reduce paging
				yield();
			}
		}
	}

	//Parse a file to see if it is XML
	private boolean parseFile(File file) {
		try {
			DocumentBuilder db =
				DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = db.parse(file);
			return true;
		}
		catch (Exception ex) { return false; }
	}

	//Get a remapper based on the current properties.
	private Remapper getRemapper(Properties props) {
		String enabled = props.getProperty("remapper-enabled");
		if ((enabled != null) && enabled.trim().equals("true")) {
			String url = props.getProperty("remapper-url");
			if ((url != null) && !url.trim().equals("")) {
				try { return new RemoteRemapper(url.trim(),props); }
				catch (Exception useLocalRemapperInstead) { }
			}
			Log.message("<font color=\"red\">Unable to create a remote remapper.</font>");
		}
		return new LocalRemapper();
	}

	//Load a properties file. Used to load the DICOM anonymizer
	//properties for each file processed. It's an inefficient way to
	//ensure that we always have the latest version, but the overhead
	//is small compared to loading the DICOM object itself, and it
	//does decouple everything.
	private Properties loadProperties(String filename) {
		File propFile = new File(filename);
		Properties props = new Properties();
		try { props.load(new FileInputStream(propFile)); }
		catch (Exception ex) { props = null; }
		return props;
	}

	//Save an object in the received log directory.
	private void saveObject(File file) {
		String enb = props.getProperty("save-received-objects");
		if ((enb != null) && enb.equals("true")) {
			File out = new File(receivedlogFile,file.getName());
			FileUtil.copyFile(file,out);
		}
	}

	//Log the key DicomObject element values of objects
	//that have been exported.
	private void makeTrialLogEntry(String service, File file) {
		processorLog.setAdditivity(false);
		try {
			DicomObject td = new DicomObject(file);
			processorLog.info(td.getPatientName()
					  + "," + td.getPatientID()
					  + "," + td.getModality()
					  + "," + td.getSeriesNumber()
					  + "," + td.getAcquisitionNumber()
					  + "," + td.getInstanceNumber());
		}
		catch (Exception e) {
			logger.warn("Unable to make a trial log entry for "+file.getName());
			Log.message("<font color=\"red\">Unable to make a trial log entry for<br>"+file.getName()+"<\font>");
			sendTransferEvent("Trial log entry failure");
		}
	}

	/**
	 * Add a TransferListener to the listener list.
	 * @param listener the TransferListener.
	 */
	public void addTransferListener(TransferListener listener) {
		listenerList.add(TransferListener.class, listener);
	}

	/**
	 * Remove a TransferListener from the listener list.
	 * @param listener the TransferListener.
	 */
	public void removeTransferListener(TransferListener listener) {
		listenerList.remove(TransferListener.class, listener);
	}

	//Send a message via a TransferEvent to all TransferListeners.
	private void sendTransferEvent(String message) {
		sendTransferEvent(this,message);
	}

	//Send a TransferEvent to all TransferListeners.
	private void sendTransferEvent(TransferEvent event) {
		sendTransferEvent(this,event.message);
	}

	//Send a TransferEvent to all TransferListeners.
	//The event is sent in the event thread to make it safe for
	//GUI components.
	private void sendTransferEvent(Object object, String message) {
		final TransferEvent event = new TransferEvent(object,message);
		final EventListener[] listeners = listenerList.getListeners(TransferListener.class);
		Runnable fireEvents = new Runnable() {
			public void run() {
				for (int i=0; i<listeners.length; i++) {
					((TransferListener)listeners[i]).attention(event);
				}
			}
		};
		SwingUtilities.invokeLater(fireEvents);
	}

}