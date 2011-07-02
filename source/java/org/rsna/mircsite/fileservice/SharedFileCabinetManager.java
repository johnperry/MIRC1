/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.fileservice;

import java.io.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.log4j.Logger;
import org.rsna.mircsite.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The Thread that automatically removes files from
 * the shared file cabinet after they time out.
 */
public class SharedFileCabinetManager extends Thread {

	static final Logger logger = Logger.getLogger(SharedFileCabinetManager.class);

	File root;
	File filesDir;
	File iconsDir;

	static long anHour = 60 * 60 * 1000;

	/**
	 * Create a new SharedFileCabinetManager to remove files
	 * from the shared file cabinet after they time out.
	 */
	public SharedFileCabinetManager(File root) {
		this.root = root;
		File dept = new File(root, "dept");
		this.filesDir = new File(dept, "Files");
		this.iconsDir = new File(dept, "Icons");
	}

	/**
	 * Start the thread. Check for timed out files every hour.
	 */
	public void run() {
		try {
			while (true) {
				checkFiles();
				sleep(anHour);
			}
		}
		catch (Exception ex) { }
	}

	//Remove timed out files.
	private void checkFiles() {
		long maxAge = AdminService.timeout * anHour;
		if (maxAge == 0) return;
		long timeNow = System.currentTimeMillis();
		long earliestAllowed = timeNow - maxAge;

		//Set up the Files
		removeOldFiles(filesDir, earliestAllowed);
		removeOldFiles(iconsDir, earliestAllowed);
	}

	private void removeOldFiles(File dir, long minLM) {
		if (!dir.exists() || !dir.isDirectory()) return;
		File[] files = dir.listFiles();
		for (int i=0; i<files.length; i++) {
			if (files[i].isDirectory()) {
				removeOldFiles(files[i], minLM);
			}
			else {
				long lm = files[i].lastModified();
				if (lm < minLM) files[i].delete();
			}
		}
		if (!dir.equals(filesDir) && !dir.equals(iconsDir)) {
			files = dir.listFiles();
			if (files.length == 0) dir.delete();
		}
	}
}
