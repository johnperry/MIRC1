/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.tceservice;

import java.io.*;
import java.util.*;
import javax.swing.event.EventListenerList;
import org.apache.log4j.Logger;
import org.rsna.dicom.Dicom;
import org.rsna.dicom.DicomEvent;
import org.rsna.dicom.DicomEventListener;
import org.rsna.dicom.DicomStorageScp;
import org.rsna.mircsite.log.Log;

/**
 * An encapsulation of a DICOM Storage SCP.
 */
public class StorageSCP {

	static final Logger logger = Logger.getLogger(StorageSCP.class);

	static final String serviceName = "TCEStorageSCP";

	File dicomstoreFile;
	DicomStorageScp scp;
	EventListenerList dicomListenerList;
	String currentSCPPort;
	String currentAETitle;
	String scpport;
	String aetitle;

	/**
	 * Class constructor; creates a new instance of the StorageSCP.
	 * @param aetitle the AE title to be used by the SCP.
	 * @param scpport the port to be used by the SCP.
	 * @param dicomstoreFile the directory to be used by the SCP for
	 * storage of received DICOM objects.
	 */
	public StorageSCP(String aetitle, String scpport, File dicomstoreFile) {
		dicomListenerList = new EventListenerList();
		this.aetitle = aetitle;
		this.scpport = scpport;
		this.dicomstoreFile = dicomstoreFile;
		dicomstoreFile.mkdirs();
	}

	/**
	 * Reinitialize the StorageSCP and restart it if it is running
	 * and either the SE title or the port have changed.
	 * @param aetitle the AE title to be used by the SCP.
	 * @param scpport the port to be used by the SCP.
	 */
	public void reinitialize(String aetitle, String scpport) {
		if (!aetitle.equals(this.aetitle) || !scpport.equals(this.scpport)) {
			this.aetitle = aetitle;
			this.scpport = scpport;
			if (scp != null) restartSCP();
		}
	}

	/**
	 * Get the number of files in the dicomStoreDir.
	 * @return the number of files in the dicomStoreDir that have not been
	 * moved out by the Store.
	 */
	public int getFileCount() {
		if (!dicomstoreFile.exists()) return 0;
		File[] files = dicomstoreFile.listFiles();
		return files.length;
	}

	/**
	 * Delete all the files in the dicomStoreDir.
	 */
	public void deleteAllFiles() {
		if (!dicomstoreFile.exists()) return;
		File[] files = dicomstoreFile.listFiles();
		for (int i=0; i<files.length; i++)
			files[i].delete();
	}

	/**
	 * Stop the SCP if it is running, then reinitialize and restart it.
	 * @return true if the SCP started; false otherwise.
	 */
	public boolean restartSCP() {
		stopSCP();
		scp = initializeDicom();
		try { scp.start(); }
		catch (Exception e) {
			Log.message("<font color=\"red\">"+serviceName+": "+aetitle
						+" failed to start on port "+scpport
						+"<br>" + e.getMessage()+"</font>");
			scp = null;
			return false;
		}
		addDicomListeners(scp);
		Log.message(serviceName+": "+currentAETitle+" started on port "+currentSCPPort);
		return true;
	}

	//Stop the SCP if it is running.
	private void stopSCP() {
		if (scp == null) return;

		//The SCP throws an exception when it stops; ignore it.
		try { scp.stop(); }
		catch (Exception ignore) { }

		//Log the stoppage.
		Log.message(serviceName+": "+currentAETitle+" stopped on port "+currentSCPPort);
	}

	//Set up the properties that a DicomStorageScp
	//requires and then instantiate a new DicomStorageScp.
	private DicomStorageScp initializeDicom() {
		currentSCPPort = scpport;
		currentAETitle = aetitle;
		Properties p = new Properties();
		p.setProperty("storage-scp-aet",aetitle);
		p.setProperty("port",scpport);
		p.setProperty("dest",dicomstoreFile.getAbsolutePath());
		Dicom.initialize(p);
		return Dicom.getStorageScp();
	}

	//Add the dicomListenerList to the scp.
	private void addDicomListeners(DicomStorageScp scp) {
		EventListener[] listeners = dicomListenerList.getListeners(DicomEventListener.class);
		for (int i=0; i<listeners.length; i++) {
			scp.addDicomEventListener((DicomEventListener)listeners[i]);
		}
	}

	/**
	 * Add a DicomListener to the listener list.
	 * @param listener the DicomEventListener.
	 */
	public void addDicomListener(DicomEventListener listener) {
		dicomListenerList.add(DicomEventListener.class, listener);
		if (scp != null) scp.addDicomEventListener(listener);
	}

	/**
	 * Remove a DicomListener from the listener list.
	 * @param listener the DicomEventListener.
	 */
	public void removeDicomListener(DicomEventListener listener) {
		dicomListenerList.remove(DicomEventListener.class, listener);
		if (scp != null) scp.removeDicomEventListener(listener);
	}

}
