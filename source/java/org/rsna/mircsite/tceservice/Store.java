/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.tceservice;

import java.io.File;
import java.util.EventListener;
import java.util.Hashtable;
import javax.swing.event.EventListenerList;
import org.apache.log4j.Logger;
import org.rsna.dicom.DicomEvent;
import org.rsna.dicom.DicomEventListener;
import org.rsna.mircsite.log.Log;
import org.rsna.mircsite.util.DicomObject;
import org.rsna.mircsite.util.FileUtil;

/**
 * A class to encapsulate the storage system for the TCE Service.
 * This class manages the received DICOM objects and divides them into
 * two directories, one for manifests and one for other instances. It
 * provides methods for verifying that all the instances required by a
 * manifest are present.
 */
public class Store implements DicomEventListener {

	static final String manifestsName = "manifests";
	static final String instancesName = "instances";
	static final String queueName = "queue";

	static final String serviceName = "TCEImportService";

	static final Logger logger = Logger.getLogger(Store.class);

	File store;
	File manifests;
	File instances;
	File queue;
	int currentCount = Integer.MAX_VALUE;
	EventListenerList listenerList;
	GarbageCollector collector;
	String timeout;

	/**
	 * Class constructor; creates a Store and its required subdirectories.
	 * @param store the file pointing to where the Store is to be created.
	 * @throws IOException if the Store cannot be created.
	 */
	public Store(File store) {
		this.store = store;
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
			Log.message("TCE GarbageCollector deleted expired files.");
		}
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
					Log.message(
						serviceName+": Manifest received from \""+
						e.getCallingAET()+"\":<br>" + file.getName());

					//Put the file in the manifests directory
					dicomObject.moveToDirectory(manifests,true);

					//Check all the manifests, queue any completed ones, and set the currentCount.
					checkManifests();
				}
				else {
					//Log the file reception.
					Log.message(
						serviceName+": Instance received from \""+
						e.getCallingAET()+"\":<br>" + file.getName());

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
					"<font color=\"red\">"+serviceName+": Object received from \""+
					e.getCallingAET()+"\"<br>" +
					"Object failed to parse:<br>" + file.getName()+"</font>");
				logger.warn(
					serviceName+": Object failed to parse: " + file.getName());
			}
		}
		else if (e.getStatus() != 0xff00) {
			Log.message(
				"<font color=\"red\">"+serviceName+": Unexpected DICOM status: "+
				e.toStringNoPath()+"</font>");
			logger.info(
				serviceName+": Unexpected DICOM status: "+e.toStringNoPath());
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
		int min = Integer.MAX_VALUE;
		File[] manifestList = FileUtil.listSortedFiles(manifests);
		for (int i=0; i<manifestList.length; i++) {
			try {
				DicomObject manifest = new DicomObject(manifestList[i]);
				String[] refs = manifest.getInstanceList();
				if (refs != null) {
					int count = countMissingInstances(refs);
					if (count == 0) {
						queueManifest(manifest);
					}
					else if (count < min) min = count;
				}
			}
			catch (Exception ignore) { }
		}
		currentCount = min;
	}

	//Queue a manifest.
	private void queueManifest(DicomObject manifest) {
		manifest.touch();
		manifest.moveToDirectory(queue,true);
		Log.message(
			serviceName+": Manifest completed and queued:<br>"
			+ manifest.getFile().getName());
	}

	//Count the number of files in a list that are missing
	//from the instances directory.
	private int countMissingInstances(String[] files) {
		File file;
		int count = 0;
		for (int i=0; i<files.length; i++) {
			file = new File(instances,files[i]);
			if (!file.exists()) count++;
		}
		return count;
	}

	//A garbage collector Thread to remove all expired
	//files from the store after a 60-minute timeout.
	class GarbageCollector extends Thread {
		long timeout = 60L * 60L * 1000L;
		public GarbageCollector() {
			this.setPriority(Thread.MIN_PRIORITY);
		}
		public void run() {
			while (true) {
				try {
					//Sleep first, then remove the files.
					sleep(timeout);
					removeExpiredFiles(timeout);
				}
				catch (Exception e) { }
			}
		}
	}

}
