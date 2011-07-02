/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.dicomservice;

import java.io.*;
import org.rsna.mircsite.util.*;

/**
 * The storage service quarantine for queue elements
 * pointing to DICOM objects that cannot be sent to their
 * destinations.
 */
public class Quarantine {

	/**
	 * Add a queue element file to the quarantine. The queue
	 * element file is renamed into the quarantine directory,
	 * removing it from the transmission queue.
	 * @param file the queue element file to be quarantined.
	 * @return true if the quarantine was successful; false otherwise.
	 */
	public static boolean file(File file) {
		//Make a clone pointing to the original File object.
		//This is done to avoid modifying the original File object.
		File clone = new File(file.getAbsolutePath());

		//Make a File pointing to the quarantine and create
		//the quarantine if it doesn't exist.
		File q = new File(TrialConfig.basepath
							+ TrialConfig.trialpath
								+ TrialConfig.quarantine);
		if (!q.exists()) q.mkdirs();

		//Make a name for the quarantined file that will be unique if
		//queue elements for the same object going to other destinations
		//are also quarantined.
		q = new File(TrialConfig.basepath
						+ TrialConfig.trialpath
							+ TrialConfig.quarantine
								+ File.separator
									+ StringUtil.makeNameFromDate()
										+ "-" + file.getName());

		//If the queue element is not already in the quarantine, quarantine it
		if (!q.exists()) return clone.renameTo(q);

		//It was already there; just delete the element from the directory
		//so it doesn't hang the queue handling process.
		return file.delete();
	}

	/**
	 * Add a queue element file to the quarantine and return a
	 * string for logging. The file is renamed into the quarantine
	 * directory, removing it from the transmission queue.
	 * @param file the queue element file to be quarantined.
	 * @param service the service making the quarantine call; used to provide a
	 * name in the returned log string.
	 * @return a string to log the result.
	 */
	public static String file(File file, String service) {
		if (file(file))
			return service+": Quarantine succeeded: "+file.getName();
		else
			return service+": Quarantine failed: "+file.getName();
	}

	/**
	 * Count the number of quarantined files.
	 * @return the number of files in the quarantine.
	 */
	public static int getFileCount() {
		return FileUtil.getFileCount(
			new File(TrialConfig.basepath + TrialConfig.trialpath + TrialConfig.quarantine));
	}

}