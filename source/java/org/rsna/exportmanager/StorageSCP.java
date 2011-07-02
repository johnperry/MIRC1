/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.exportmanager;

import java.io.*;
import java.util.*;
import javax.swing.event.EventListenerList;
import javax.swing.SwingUtilities;
import org.apache.log4j.Logger;
import org.rsna.dicom.Dicom;
import org.rsna.dicom.DicomEvent;
import org.rsna.dicom.DicomEventListener;
import org.rsna.dicom.DicomStorageScp;
import org.rsna.mircsite.log.Log;
import org.rsna.util.ApplicationProperties;
import org.rsna.util.PropertyEvent;
import org.rsna.util.PropertyListener;
import org.rsna.util.TransferEvent;
import org.rsna.util.TransferListener;

/**
 * An encapsulation of a DICOM Storage SCP.
 */
public class StorageSCP implements PropertyListener {

	static final Logger logger = Logger.getLogger(StorageSCP.class);

	File dicomstoreFile;
	DicomStorageScp scp;
	EventListenerList transferListenerList;
	EventListenerList dicomListenerList;
	ApplicationProperties props;
	String scpport;
	String aetitle;

	/**
	 * Class constructor; creates a new instance of the StorageSCP.
	 * @param props the application properties, carrying the SCP parameters.
	 * @param dicomstoreFile the directory to be used by the SCP for
	 * storage of received DICOM objects.
	 */
	public StorageSCP(ApplicationProperties props, File dicomstoreFile) {
		transferListenerList = new EventListenerList();
		dicomListenerList = new EventListenerList();
		this.props = props;
		this.dicomstoreFile = dicomstoreFile;
		dicomstoreFile.mkdirs();
		props.addPropertyListener(this);
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
	 * Stop the SCP if it is running, reinitialize the SCP from the
	 * the application properties object, and restart the SCP.
	 * @return true if the SCP started; false otherwise.
	 */
	public boolean restartSCP() {
		stopSCP();
		scp = initializeDicom(props);
		try { scp.start(); }
		catch (Exception e) {
			Log.message("<font color=\"red\">"+aetitle+" failed to start on port "+scpport
						+"<br>" + e.getMessage()+"</font>");
			sendTransferEvent(aetitle+" failed to start on port "+scpport);
			scp = null;
			return false;
		}
		addDicomListeners(scp);
		Log.message(aetitle+" started on port "+scpport);
		sendTransferEvent(aetitle+" started on port "+scpport);
		return true;
	}

	//Stop the SCP if it is running.
	private void stopSCP() {
		if (scp == null) return;

		//The SCP throws an exception when it stops; ignore it.
		try { scp.stop(); }
		catch (Exception ignore) { }

		//Log the stoppage.
		Log.message(aetitle+" stopped on port "+scpport);
		sendTransferEvent(aetitle+" stopped");
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

	/**
	 * Add a TransferListener to the transfer listener list.
	 * @param listener the TransferListener.
	 */
	public void addTransferListener(TransferListener listener) {
		transferListenerList.add(TransferListener.class, listener);
	}

	/**
	 * Remove a TransferListener from the transfer listener list.
	 * @param listener the TransferListener.
	 */
	public void removeTransferListener(TransferListener listener) {
		transferListenerList.remove(TransferListener.class, listener);
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
		final EventListener[] listeners = transferListenerList.getListeners(TransferListener.class);
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
