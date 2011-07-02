/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.anonymizer;

import java.io.*;
import java.util.Hashtable;
import org.apache.log4j.Logger;

/**
 * The MIRC local remapper.
 * This class supports collects requests for remapping PHI and calls the
 * local IdTable to obtain replacement values.
 */
public class LocalRemapper implements Remapper {

	static final Logger logger = Logger.getLogger(LocalRemapper.class);

	Hashtable<String,String> responseTable;
	int count;

	/**
	 * The constructor, which initializes a Remapper for remapping using the local IdTable.
	 */
	public LocalRemapper () {
		//Initialize the responseTable.
		clear();
	}

	/**
	 * Reinitialize the remapper, clearing all accumulated requests.
	 */
	public void clear() {
		responseTable = new Hashtable<String,String>();
		count = 0;
	}

	/**
	 * Get the original date corresponding to a remapped value for a patient and element.
	 * @param seqid an identifier for the remapping request for this value; used
	 * as the key in the results hashtable, so it must be unique.
	 * @param siteid the site ID for the site on which the patient ID was created.
	 * For trials using globally created patient IDs, all the site IDs can be set to
	 * the empty string ("").
	 * @param ptid the original patient ID.
	 * @param tag the DICOM element tag string (gggg,eeee).
	 * @param date the remapped date.
	 * @param base the base date used as the starting date for offsetting dates.
	 */
	public void getOriginalDate(
					int seqid, String siteid, String ptid,
					String tag, String date, String base) {
		String id = Integer.toString(seqid);
		String value = IdTable.getOriginalDate(siteid,ptid,tag,date,base);
		responseTable.put(id,value);
		count++;
	}

	/**
	 * Request a replacement for a date.
	 * @param seqid an identifier for the remapping request for this value; used
	 * as the key in the results hashtable, so it must be unique.
	 * @param siteid the site ID for the site on which the patient ID was created.
	 * For trials using globally created patient IDs, all the site IDs can be set to
	 * the empty string ("").
	 * @param ptid the patient ID.
	 * @param tag the DICOM element tag string (gggg,eeee).
	 * @param date the date value of the element.
	 * @param base the base date to use as the starting date for offsetting dates.
	 */
	public void getOffsetDate(int seqid, String siteid, String ptid,
								String tag, String date, String base) {
		String id = Integer.toString(seqid);
		String value = IdTable.getOffsetDate(siteid,ptid,tag,date,base);
		responseTable.put(id,value);
		count++;
	}

	/**
	 * Request a replacement for a DICOM ID element.
	 * @param seqid an identifier for the remapping request for this value; used
	 * as the key in the results hashtable, so it must be unique.
	 * @param tag the DICOM element tag string (gggg,eeee).
	 * @param gid the ID value to be replaced.
	 */
	public void getGenericID(int seqid, String tag, String gid) {
		String id = Integer.toString(seqid);
		String value = IdTable.getGenericID(tag,gid);
		responseTable.put(id,value);
		count++;
	}

	/**
	 * Request an accession number.
	 * @param seqid an identifier for the remapping request for this value; used
	 * as the key in the results hashtable, so it must be unique.
	 * @param tag the DICOM element tag string (gggg,eeee).
	 * @param gid the value to be replaced.
	 */
	public void getAccessionNumber(int seqid, String tag, String gid) {
		String id = Integer.toString(seqid);
		String value = IdTable.getGenericID(tag,gid);
		responseTable.put(id,value);
		count++;
	}

	/**
	 * Request an integer which has not been returned before.
	 */
	public void getInteger(int seqid) {
		String id = Integer.toString(seqid);
		String value = IdTable.getInteger();
		responseTable.put(id,value);
		count++;
	}

	/**
	 * Request a replacement for a UID.
	 * @param seqid an identifier for the remapping request for this value; used
	 * as the key in the results hashtable, so it must be unique.
	 * @param prefix the UID root to serve as a prefix for the replacement.
	 * @param uid the UID to be replaced.
	 */
	public void getUID(int seqid, String prefix, String uid) {
		String id = Integer.toString(seqid);
		String value = IdTable.getUID(prefix,uid);
		responseTable.put(id,value);
		count++;
	}

	/**
	 * Request a new UID.
	 * @param seqid an identifier for the remapping request; used
	 * as the key in the results hashtable, so it must be unique.
	 * @param prefix the UID root to serve as a prefix for the replacement.
	 */
	public void getUID(int seqid, String prefix) {
		String id = Integer.toString(seqid);
		String value = IdTable.getUID(prefix);
		responseTable.put(id,value);
		count++;
	}

	/**
	 * Get the original UID associated with a remapped UID.
	 * @param seqid an identifier for the  request; used
	 * as the key in the results hashtable, so it must be unique.
	 * @param uid the original UID.
	 */
	public void getOriginalUID(int seqid, String uid) {
		String id = Integer.toString(seqid);
		String value = IdTable.getOriginalUID(uid);
		responseTable.put(id,value);
		count++;
	}

	/**
	 * Get the remapped UID associated with an original UID.
	 * @param seqid an identifier for the  request; used
	 * as the key in the results hashtable, so it must be unique.
	 * @param uid the remapped UID.
	 */
	public void getRemappedUID(int seqid, String uid) {
		String id = Integer.toString(seqid);
		String value = IdTable.getRemappedUID(uid);
		responseTable.put(id,value);
		count++;
	}

	/**
	 * Request a replacement for a DICOM Patient ID.
	 * @param seqid an identifier for the remapping request for this value; used
	 * as the key in the results hashtable, so it must be unique.
	 * @param siteid the site ID for the site on which the patient ID was created.
	 * For trials using globally created patient IDs, all the site IDs can be set to
	 * the empty string ("").
	 * @param ptid the patient ID.
	 * @param prefix the prefix of the new Patient ID.
	 * @param first the starting value of the sequentially increasing integer
	 * used to generate IDs.
	 * @param width the minimum width of the integer part of the Patient ID, with
	 * leading zeroes supplied if the integer does not require the full field width.
	 * @param suffix the suffix of the new Patient ID.
	 */
	public void getPtID(int seqid, String siteid, String ptid,
							String prefix, int first, int width, String suffix) {
		String id = Integer.toString(seqid);
		String value = IdTable.getPtID(siteid,ptid,prefix,first,width,suffix);
		responseTable.put(id,value);
		count++;
	}

	/**
	 * Get the current number of values for which remapping requests have been received.
	 * @return the number of remapping requests.
	 */
	public int getCount() {
		return count;
	}

	/**
	 * Get the results of the remapping request.
	 * @return Hashtable containing the remapped values, indexed by the seqid values
	 * in the requests, or null if an error occurred in the remapping request.
	 */
	public Hashtable getRemappedValues() {
		return responseTable;
	}

}
