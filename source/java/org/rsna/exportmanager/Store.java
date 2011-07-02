/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.exportmanager;

import java.io.File;
import java.util.EventListener;
import java.util.Hashtable;
import javax.swing.event.EventListenerList;
import javax.swing.SwingUtilities;
import org.apache.log4j.Logger;
import org.rsna.dicom.DicomEvent;
import org.rsna.dicom.DicomEventListener;
import org.rsna.mircsite.log.Log;
import org.rsna.mircsite.util.DicomObject;
import org.rsna.mircsite.util.FileUtil;
import org.rsna.util.ApplicationProperties;
import org.rsna.util.PropertyEvent;
import org.rsna.util.PropertyListener;
import org.rsna.util.TransferEvent;
import org.rsna.util.TransferListener;

/**
 * A class to encapsulate the storage system for the TFCTE ExportManager.
 * This class manages the received DICOM objects and divides them into
 * two directories, one for manifests and one for other instances. It
 * provides methods for verifying that all the instances required by a
 * manifest are present.
 */
public class Store implements DicomEventListener {

	static final String manifestsName = "manifests";
	static final String instancesName = "instances";
	static final String queueName = "queue";

	static final String dicomImportServiceName = "DicomImportService";

	static final Logger logger = Logger.getLogger(Store.class);

	File store;
	File manifests;
	File instances;
	File queue;
	int currentCount = Integer.MAX_VALUE;
	EventListenerList listenerList;
	ApplicationProperties props;
	GarbageCollector collector;
	String timeout;

	/**
	 * Class constructor; creates a Store and its required subdirectories.
	 * @param store the file pointing to where the Store is to be created.
	 * @throws IOException if the Store cannot be created.
	 */
	public Store(ApplicationProperties props, File store) {
		this.props = props;
		this.store = store;
		listenerList = new EventListenerList();
		manifests = new File(store,manifestsName);
		instances = new File(store,instancesName);
		queue = new File(store,queueName);
		manifests.mkdirs();
		instances.mkdirs();
		queue.mkdirs();
		checkManifests();
		collector = new GarbageCollector();
		collector.start();
	}

	/**
	 * Remove expired files from the store.
	 * @param timeoutMillis the maximum age in milliseconds.
	 */
	public void removeExpiredFiles(long timeoutMillis) {
		boolean deleted = false;

		//Get the earliest time to protect
		long time = System.currentTimeMillis() - timeoutMillis;

		//Remove any expired manifests
		File[] files = manifests.listFiles();
		for (int i=0; i<files.length; i++) {
			File file = files[i];
			if (file.lastModified() < time) deleted |= file.delete();
		}

		//Make a table of instance names that are referenced by
		//manifests that are either already queued or are unexpired.
		Hashtable<String,String> hashtable = new Hashtable<String,String>();
		hashInstances(hashtable,queue);
		hashInstances(hashtable,manifests);

		//Handle any expired instances that can safely be removed.
		files = instances.listFiles();
		for (int i=0; i<files.length; i++) {
			File file = files[i];
			if ((file.lastModified() < time) &&
				(hashtable.get(file.getName()) == null)) deleted |= file.delete();
		}

		//Notify the world if anything was deleted.
		if (deleted) {
			Log.message("GarbageCollector deleted expired files.");
			sendTransferEvent("GarbageCollector deleted expired files");
		}
		//Otherwise, just note that the GarbageCollector ran in the status panel.
		else sendTransferEvent("GarbageCollector found no expired files");
	}

	/**
	 * Get a list of the files in the queue, sorted in
	 * order by last modified time.
	 * @return the list of manifests in the queue in the order
	 * in which they should be processed.
	 */
	public File[] getQueuedManifests() {
		return FileUtil.listSortedFiles(queue);
	}

	/**
	 * Get a File pointing to a named instance.
	 * @param name the name of the file to get.
	 */
	public File getInstanceFile(String name) {
		return new File(instances,name);
	}

	/**
	 * Get the number of manifests in the store.
	 * @return the number of files in the manifests directory.
	 */
	public int getManifestCount() {
		return countFiles(manifests);
	}

	/**
	 * Get the number of manifests in the store.
	 * @return the number of files in the queue directory.
	 */
	public int getQueuedManifestCount() {
		return countFiles(queue);
	}

	/**
	 * Get the number of instances in the store.
	 * @return the number of files in the instances directory.
	 */
	public int getInstanceCount() {
		return countFiles(instances);
	}

	//Count the number of files in a directory.
	private int countFiles(File dir) {
		if (!dir.exists()) return 0;
		File[] files = dir.listFiles();
		return files.length;
	}

	/**
	 * Delete all the files in the store.
	 */
	public void deleteAllFiles() {
		deleteAllFiles(manifests);
		deleteAllFiles(instances);
		deleteAllFiles(queue);
	}

	private void deleteAllFiles(File dir) {
		if (!dir.exists()) return;
		File[] files = dir.listFiles();
		for (int i=0; i<files.length; i++)
			files[i].delete();
	}

	/**
	 * The DicomEventListener implementation; listens for DICOM objects,
	 * parses them to determine whether they are instances or manifests,
	 * and moves them to the appropriate store directory.
	 */
	public void dicomEventOccurred(DicomEvent e) {
		if ((e.getStatus() == 0) && e.serviceAsString(e.getService()).equals("C_STORE_RQ")) {
			File file = new File(e.getFilename());

			//Parse the file and store it in the appropriate directory.
			try {
				DicomObject dicomObject = new DicomObject(file);
				if (dicomObject.isManifest()) {
					//Log the file reception.
					Log.message("Manifest received from \""+e.getCallingAET()+"\":<br>" + file.getName());
					logger.info("Manifest received: " + file.getName());
					sendTransferEvent("Manifest received");

					//Put the file in the manifests directory
					boolean ok = dicomObject.moveToDirectory(manifests,true);

					//Check all the manifests, queue any completed ones, and set the currentCount.
					checkManifests();
				}
				else {
					//Log the file reception.
					Log.message("Instance received from \""+e.getCallingAET()+"\":<br>" + file.getName());
					logger.info("Instance received: " + file.getName());
					sendTransferEvent("Instance received");

					//Put the file in the instances directory
					dicomObject.moveToDirectory(instances,true);

					//Count the instance and, if it is possible that a manifest has been fulfilled,
					//check the manifests, queue any completed ones, and set a new currentCount value.
					currentCount--;
					if (currentCount <= 0) checkManifests();
				}
			}
			catch (Exception ex) {
				Log.message(
					"<font color=\"red\">Object received from \""+e.getCallingAET()+"\"<br>" +
					"Object failed to parse:<br>" + file.getName()+"</font>");
				logger.info("Object failed to parse: " + file.getName());
				sendTransferEvent("Object failed to parse");
			}
		}
		else if (e.getStatus() != 0xff00) {
			Log.message("<font color=\"red\">Unexpected DICOM status: "+e.toStringNoPath()+"</font>");
			logger.info("Unexpected DICOM status: "+e.toStringNoPath());
			sendTransferEvent("Unexpected DICOM status: "+e.toStringNoPath());
		}
	}

	//Find all the instance names referenced by manifests
	//in a directory and insert them into a Hashtable.
	private void hashInstances(Hashtable<String,String> h, File dir) {
		File files[] = dir.listFiles();
		for (int i=0; i<files.length; i++) {
			try {
				DicomObject manifest = new DicomObject(files[i]);
				String[] refs = manifest.getInstanceList();
				for (int j=0; j<refs.length; j++) {
					h.put(refs[i],"");
				}
			}
			catch (Exception ignore) { }
		}
	}

	//Check the manifests, queue any complete manifests, and set the currentCount.
	private void checkManifests() {

/**/	logger.debug("\n\nEntering checkManifests:\n");

		int min = Integer.MAX_VALUE;
		File[] manifestList = FileUtil.listSortedFiles(manifests);

/**/	logger.debug("There are " + manifestList.length + " manifests to check.");

		for (int i=0; i<manifestList.length; i++) {
			try {
				DicomObject manifest = new DicomObject(manifestList[i]);

/**/			logger.debug("Checking manifest["+i+"]: "+ manifest.getSOPInstanceUID());

				String[] refs = manifest.getInstanceList();
				if (refs != null) {
					int count = countMissingInstances(refs);

/**/				logger.debug("Number of missing instances = " + count);

					if (count == 0) queue(manifest);
					else if (count < min) min = count;
				}
			}
			catch (Exception ignore) { }
		}
		currentCount = min;

/**/	logger.debug("\n\nLeaving checkManifests with currentCount = " + currentCount + "\n");

	}

	//Queue a manifest.
	private void queue(DicomObject manifest) {
		manifest.touch();
		manifest.moveToDirectory(queue,true);
		Log.message("Manifest completed and queued:<br>" + manifest.getFile().getName());
		logger.info("Manifest completed and queued: " + manifest.getFile().getName());
		sendTransferEvent("Manifest completed and queued");
	}

	//Count the number of files in a list that are missing
	//from the instances directory.
	private int countMissingInstances(String[] files) {
		File file;
		boolean found;
		int count = 0;
		for (int i=0; i<files.length; i++) {
			file = new File(instances,files[i]);
			found = file.exists();
			if (!found) count++;

/**/		logger.debug("     instance " + (found ? "" : "not ") + "found: " + files[i]);

		}
		return count;
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

	//A garbage collector Thread to remove all expired files from the store.
	//The specified timeout is in minutes.
	class GarbageCollector extends Thread implements PropertyListener {
		String timeout;
		public GarbageCollector() {
			 this.setPriority(Thread.MIN_PRIORITY);
			 timeout = props.getProperty("timeout");
			 props.addPropertyListener(this);
		}
		public void run() {
			while (true) {
				try {
					Long time = 60L; //default timeout
					timeout = props.getProperty("timeout");
					if (timeout != null) {
						try { time = Long.parseLong(timeout); }
						catch (Exception ignore) { }
					}
					time *= 60 * 1000;
					//Sleep first, then remove the files.
					sleep(time);
					removeExpiredFiles(time);
				}
				catch (Exception e) { }
			}
		}
		public void propertyChanged(PropertyEvent event) {
			String newTimeout = props.getProperty("timeout");
			if (timeout == null) return;
			if (newTimeout == null) return;
			if (!timeout.equals(newTimeout)) {
				this.interrupt();
			}
		}
	}

}
