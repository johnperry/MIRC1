/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.storageservice;

import java.io.*;
import org.apache.log4j.Logger;
import org.rsna.mircsite.util.*;

/**
 * The Thread that automatically removes documents from
 * the deleted-documents directory after they time out.
 */
public class DeletedDocumentsManager extends Thread {

	static final Logger logger = Logger.getLogger(DeletedDocumentsManager.class);

	File dir;
	static long anHour = 60 * 60 * 1000;
	static long aDay = 24 * anHour;

	/**
	 * Create a new DeletedDocumentsManager to remove files
	 * from the deleted-documents directory after they time out.
	 * param dir the deleted-documents directory.
	 */
	public DeletedDocumentsManager(File dir) {
		this.dir = dir;
		this.setPriority(Thread.MIN_PRIORITY);
	}

	/**
	 * Start the thread. Check for timed out documents
	 * once when the Thread starts and then every day.
	 */
	public void run() {
		try {
			while (true) {
				checkFiles();
				sleep(aDay);
			}
		}
		catch (Exception ex) { }
	}

	//Remove timed out files.
	private void checkFiles() {
		long maxAge = StorageConfig.getDDTimeout() * aDay;
		if (maxAge == 0) return;
		long timeNow = System.currentTimeMillis();
		long earliestAllowed = timeNow - maxAge;

		//Check the directories in dir.
		File[] docs = dir.listFiles();
		for (int i=0; i<docs.length; i++) {
			long lm = docs[i].lastModified();
			if (lm < earliestAllowed) {
				FileUtil.deleteAll(docs[i]);
			}
		}
	}

}
