/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.exportmanager;

import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.SwingUtilities;
import javax.swing.event.*;
import org.apache.log4j.Logger;
import org.rsna.mircsite.anonymizer.DicomAnonymizer;
import org.rsna.mircsite.anonymizer.LocalRemapper;
import org.rsna.mircsite.anonymizer.Remapper;
import org.rsna.mircsite.anonymizer.RemoteRemapper;
import org.rsna.mircsite.log.Log;
import org.rsna.mircsite.util.DicomObject;
import org.rsna.mircsite.util.FileObject;
import org.rsna.util.ApplicationProperties;
import org.rsna.util.FileUtil;
import org.rsna.util.TransferEvent;
import org.rsna.util.TransferListener;

/**
 * The Thread that processes DicomObjects from in the Store,
 * anonymizes them, and queues them for the ExportProcessor.
 */
public class ObjectProcessor extends Thread {

	static final Logger logger = Logger.getLogger(ObjectProcessor.class);

	Store store;
	File anonFile;
	File lkupFile;
	File tempDir;
	File exportDir;
	File anQuarDir;
	File exportQuarantineDir;
	EventListenerList listenerList;
	boolean anonymizerEnabled;
	boolean exportEnabled;
	ApplicationProperties props;
	String scpport;
	String aetitle;
	String httpport;
	String protocol;

	/**
	 * Class constructor; creates a new instance of the ObjectProcessor
	 * and sets itself to run at the lowest possible priority.
	 * @param props the application properties, indicating whether anonymization
	 * and exporting is enabled and carrying the SCP parameters.
	 * @param store the Store object, providing access to the manifests and instances.
	 * @param anonFile the anonymizer properties file, containing all the scripts for anonymization..
	 * @param exportDir the directory into which to store objects to be exported.
	 */
	public ObjectProcessor(	ApplicationProperties props,
							Store store,
							File anonFile,
							File lkupFile,
							File exportDir,
							File anQuarDir) {

		listenerList = new EventListenerList();
		this.props = props;
		this.store = store;
		this.anonFile = anonFile;
		this.lkupFile = lkupFile;
		this.exportDir = exportDir;
		this.anQuarDir = anQuarDir;

		tempDir = new File(exportDir.getAbsolutePath());
		tempDir = new File(tempDir.getParentFile(),"temp");
		tempDir.mkdirs();
		exportDir.mkdirs();
		anQuarDir.mkdirs();

		this.setPriority(Thread.MIN_PRIORITY); //run at the lowest priority
	}

	/**
	 * Delete all the files in the anQuarDir.
	 */
	public void deleteAllFiles() {
		if (!anQuarDir.exists()) return;
		File[] files = anQuarDir.listFiles();
		for (int i=0; i<files.length; i++)
			files[i].delete();
	}

	/**
	 * The Runnable implementation; starts the thread, starts the DicomStorageScp,
	 * and polls the import queue directory, processing files when they appear.
	 */
	public void run() {
		Log.message("ObjectProcessor started");
		sendTransferEvent("ObjectProcessor started");
		while (!interrupted()) {
			try {
				processManifests();
				sleep(10000);
			}
			catch (Exception e) {
				Log.message("ObjectProcessor interrupted");
				sendTransferEvent("ObjectProcessor interrupted");
				return;
			}
		}
		Log.message("ObjectProcessor: Interrupt received");
		sendTransferEvent("ObjectProcessor: Interrupt received");
	}

	//Take the manifests in order and process them.
	private void processManifests() {
		File[] files = store.getQueuedManifests();
		for (int k=0; k<files.length; k++) {
			processManifest(files[k]);
		}
	}

	//Process one manifest, taking care to send the manifest
	//and the instances in the requested order.
	private void processManifest(File file) {

		//Figure out when to send the manifest.
		String firstString = props.getProperty("send-manifest-first");
		boolean first = (firstString == null) || !firstString.equals(false);

		try {
			//Get the manifest and its referenced instances
			DicomObject manifest = new DicomObject(file);
			String[] refs = manifest.getInstanceList();

			//If we are sending the manifest first, send it but don't delete it.
			if (first) processFile(file,false);

			//Now send the instances, but don't delete them.
			//There may be multiple manifests which reference the same
			//instance, so we don't delete instances and instead rely
			//on the timeout mechanism to flush them out of the store.
			for (int i=0; i<refs.length; i++)
				processFile(store.getInstanceFile(refs[i]),false);

			//If we are sending the manifest last, send it but don't delete it.
			if (!first) processFile(file,false);

			//Now delete the manifest. This method ensures that the manifest
			//stays in the store until everything is done, protecting any
			//instances that are about to time out.
			file.delete();
		}
		catch (Exception ex) {
			//Something really bad happened; log the event and quarantine the file.
			Log.message("<font color=\"red\">Manifest quarantined:<br>"
						+file.getName()+"<br>"
						+ex+"</font>");
			File q = new File(anQuarDir,file.getName());
			if (q.exists()) q.delete();
			file.renameTo(q);
			sendTransferEvent("Manifest object quarantined");
		}
	}

	//Anonymize one file and pass it to the export directory.
	private void processFile(File file, boolean delete) {

		File tempFile = null;
		try { tempFile = File.createTempFile("AN-",".tmp",tempDir); }
		catch (Exception ex) {
			//We can't create the temporary file.
			//Don't log it because we will flood the log.
			//Just send a TransferEvent to try to draw
			//attention to the problem.
			sendTransferEvent("Unable to create a temporary file for anonymization.");
			return;
		}

		//Get the configuration
		String anonEnb = props.getProperty("anonymizer-enabled");
		boolean anonymizerEnabled = !((anonEnb != null) && anonEnb.equals("false"));

		if (anonymizerEnabled) {
			String ivrle = props.getProperty("forceIVRLE");
			boolean forceIVRLE = !((ivrle != null) && ivrle.equals("false"));

			//Reload the properties every time so we always have the latest.
			Properties anprops = loadProperties(anonFile);
			Properties lkprops = loadProperties(lkupFile);

			//Now anonymize the file into a temp file
			String exceptions = "";
			try {
				exceptions =
					DicomAnonymizer.anonymize(
						file, tempFile,
						anprops, lkprops,
						getRemapper(props), forceIVRLE, false);
			}
			catch (Exception ex) { exceptions = ex.toString(); }

			//Check the exceptions
			if (exceptions.equals("")) {

				//Everything is okay, log it
				Log.message("<font color=\"blue\">Anonymization complete"+"<br>"+file.getName()+"</font>");
				logger.info("Anonymization complete"+": "+file.getName());
				sendTransferEvent("Anonymization complete");
			}

			else {
				//Something bad happened; log it and force a quarantine.
				//Note: the anonymizer logs the exception list to Log4J,
				//so we just have to log it to the displayed log.
				if (exceptions.indexOf("!quarantine!") != -1) {
					Log.message("<font color=\"red\">DicomAnonymizer quarantine call:<br>"
								+file.getName()+"</font>");
				}
				else if (exceptions.indexOf("!error!") != -1) {
					Log.message("<font color=\"red\">DicomAnonymizer error call: "+exceptions+"<br>"
								+file.getName()+"</font>");
				}
				else {
					Log.message("DicomAnonymization exceptions: "+ exceptions+"<br>"+file.getName());
				}
				sendTransferEvent("DICOM anonymization exceptions");

				//Now delete the temp file and quarantine the original file
				tempFile.delete();
				File q = new File(anQuarDir,file.getName());
				if (q.exists()) q.delete();
				file.renameTo(q);
				sendTransferEvent("DICOM object quarantined");
				return;
			}
		}

		else FileUtil.copyFile(file,tempFile);

		//Export the temp file
		FileObject exportFile = new FileObject(tempFile);
		exportFile.moveToDirectory(exportDir);

		//And delete the original if required.
		if (delete) file.delete();

		System.gc(); //force a garbage collection to try to reduce paging
		yield();
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
	//is small compared to loading a DICOM object itself, and it
	//does decouple everything.
	private Properties loadProperties(File propFile) {
		Properties props = new Properties();
		try { props.load(new FileInputStream(propFile)); }
		catch (Exception ignore) { }
		return props;
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