/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.storageservice;

import java.io.*;
import java.util.*;
import org.apache.log4j.Logger;
import org.rsna.mircsite.util.*;
import org.w3c.dom.Element;

/**
 * The Thread that automatically removes documents from
 * the documents directory after they time out.
 */
public class StoredDocumentsManager extends Thread {

	static final Logger logger = Logger.getLogger(DeletedDocumentsManager.class);

	File dir;
	static long anHour = 60 * 60 * 1000;
	static long aDay = 24 * anHour;

	/**
	 * Create a new StoredDocumentsManager to remove files
	 * from the documents directory after they time out.
	 * param dir the documents directory.
	 */
	public StoredDocumentsManager(File dir) {
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
		long maxAge = StorageConfig.getDocTimeout() * aDay;
		if (maxAge == 0) return;
		long timeNow = System.currentTimeMillis();
		long earliestAllowed = timeNow - maxAge;

		MircIndexEntry[] docs = MircIndex.getInstance().query("");
		for (MircIndexEntry doc : docs) {
			String docPath = doc.md.getAttribute("filename").trim();
			if (!docPath.equals("")) {
				File docFile = new File(dir, docPath);
				if (docFile.lastModified() < earliestAllowed) {
					//Okay, delete this document.
					File dirFile = docFile.getParentFile();

					//Remove it from the index.
					//It might not be in the index, but it doesn't hurt to try.
					MircIndex.getInstance().removeDocument(docPath);

					//Remove it from the input queue.
					//Again, it might not be there, but it doesn't hurt to try.
					InputQueue.deleteQueueEntry(docPath);

					//Remove the document. Use the admin method that moves the
					//entire document directory to the deleted-documents folder.
					AdminService.removeDocument(dirFile);
				}
			}
		}
	}

}
