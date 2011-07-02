/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.anonymizer;

import java.io.*;
import java.util.*;
import org.apache.log4j.Logger;
import sun.misc.BASE64Encoder;
import sun.misc.BASE64Decoder;

import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;

import java.nio.charset.Charset;

/**
 * A class encapsulating static methods for re-identifying DICOM elements.
 */
public class IdTable {

	static final Logger logger = Logger.getLogger(IdTable.class);

	private static Properties idTable = null;
	private static File propertiesFile = null;
    private static String charsetName = "UTF-8";
	private static BASE64Encoder b64Encoder = null;
	private static Cipher enCipher;
	private static Cipher deCipher;
	private static final String defaultKey = "FveQxzb+JRib1XItpIZVfw==";
	private static final String encryptedDefaultKey = "Kp+Vmuf6/Xuf/HYzyhmAryOX6NnnBYbT9we2eUO528k=";
	private static long lastTime = 0L;
	private static final long delay = 60000L;
	private static boolean storeLaterEnabled = true;
	private static boolean dirty = false;

	/**
	 * Check to see whether the specified file requires the user to be
	 * prompted for a key. The conditions are:
	 * <ul>
	 *  <li>The file doesn't exist.</li>
	 *  <li>The file exists, the key property exists, and it is not the default.</li>
	 *  <li>The file exists, the key property does not exist, and the size &gt; 0.
	 * </ul>
	 * @param filename the path to the IdTable properties file.
	 * @return true if the conditions are met; false otherwise.
	 */
	public static boolean requiresPrompt(String filename) {
		return requiresPrompt(new File(filename));
	}

	/**
	 * Check to see whether the specified file requires the user to be
	 * prompted for a key. The conditions are:
	 * <ul>
	 *  <li>The file doesn't exist.</li>
	 *  <li>The file exists and the key property exists.</li>
	 *  <li>The file exists, the key property does not exist, and the size &gt; 0.
	 * </ul>
	 * @param file the path to the IdTable properties file.
	 * @return true if the conditions are met; false otherwise.
	 */
	public static boolean requiresPrompt(File file) {
		if (!file.exists()) return true;
		Properties props = load(file);
		if (props == null) return true;
		String key = props.getProperty("key");
		if (key != null) return !key.equals(encryptedDefaultKey);
		if (props.size() > 0) return true;
		return false;
	}

	/**
	 * Initialize the re-identification system from a properties file,
	 * using the default encryption key.
	 * @param filename the path to the properties file containing the previous
	 * values used for re-identification.
	 * @return true if the table was loaded, the enCipher was initialized, and the
	 * encryption key matched the table; false otherwise.
	 */
	public static synchronized boolean initialize(String filename) {
		return initialize(new File(filename),defaultKey);
	}

	/**
	 * Initialize the re-identification system from a properties file,
	 * specifying the encryption key.
	 * @param filename the path to the properties file containing the previous
	 * values used for re-identification.
	 * @param encryptionKey the encryption key for this properties file, or null
	 * to use the default key.
	 * @return true if the table was loaded, the enCipher was initialized, and the
	 * encryption key matched the table; false otherwise.
	 */
	public static synchronized boolean initialize(String filename,String encryptionKey) {
		return initialize(new File(filename),encryptionKey);
	}

	/**
	 * Initialize the re-identification system from a properties file,
	 * using the default encryption key.
	 * @param file the file containing the previous values used
	 * for re-identification.
	 * @return true if the table was loaded, the enCipher was initialized, and the
	 * encryption key matched the table; false otherwise.
	 */
	public static synchronized boolean initialize(File file) {
		return initialize(file,defaultKey);
	}

	/**
	 * Initialize the re-identification system from a properties file,
	 * specifying the encryption key.
	 * @param file the file containing the previous values used
	 * for re-identification.
	 * @param encryptionKey the encryption key for this properties file, or null
	 * to use the default key.
	 * @return true if the table was loaded, the enCipher was initialized, and the
	 * encryption key matched the table; false otherwise.
	 */
	public static synchronized boolean initialize(File file, String encryptionKey) {
		propertiesFile = file;
		if (encryptionKey == null) encryptionKey = defaultKey;
		idTable = null;
		if (initializeCipher(encryptionKey) && checkKey(encryptionKey)) return true;
		propertiesFile = null;
		return false;
	}

	/**
	 * Get the version of the reidentification database.
	 * @return the version number of the database, or -1 if
	 * the database has not been initialized.
	 */
	public static int getVersion() {
		getIdTable();
		if (idTable == null) return -1;
		BASE64Decoder b64Decoder = new BASE64Decoder();
		Enumeration keys = idTable.keys();
		while (keys.hasMoreElements()) {
			try {
				String key = (String)keys.nextElement();
				byte[] encryptedKey = b64Decoder.decodeBuffer(key);
				byte[] decryptedKey = deCipher.doFinal(encryptedKey);
				String decodedKey = new String(decryptedKey,charsetName);
				if (decodedKey.startsWith("D[") || decodedKey.startsWith("P[")) {
					if (decodedKey.indexOf("][") == -1) return 0;
					else return 1;
				}
			}
			catch (Exception ignore) { }
		}
		return 1;
	}

	/**
	 * Convert the reidentification database to version 1.
	 */
	public static void convert(String siteid) {
		getIdTable();
		if (idTable == null) return;
		siteid = siteid.trim();
		BASE64Decoder b64Decoder = new BASE64Decoder();
		Enumeration keys = idTable.keys();
		while (keys.hasMoreElements()) {
			try {
				String key = (String)keys.nextElement();
				byte[] raw = b64Decoder.decodeBuffer(key);
				byte[] encryptedKey = b64Decoder.decodeBuffer(key);
				byte[] decryptedKey = deCipher.doFinal(encryptedKey);
				String decodedKey = new String(decryptedKey,charsetName);
				if (decodedKey.startsWith("D[") || decodedKey.startsWith("P[")) {
					if (decodedKey.indexOf("][") == -1) {
						String value = getProperty(decodedKey);
						idTable.remove(key);
						decodedKey = decodedKey.substring(0,1) + "["+siteid+"]" + decodedKey.substring(1);
						setProperty(decodedKey,value);
					}
				}
			}
			catch (Exception ignore) { }
		}
	}

	//Initialize the Cipher with the specified key.
	private static boolean initializeCipher(String encryptionKey) {
		try {
			b64Encoder = new BASE64Encoder();
			Provider sunJce = new com.sun.crypto.provider.SunJCE();
			Security.addProvider(sunJce);
			BASE64Decoder b64Decoder = new BASE64Decoder();
			byte[] raw = b64Decoder.decodeBuffer(encryptionKey);
			SecretKeySpec skeySpec = new SecretKeySpec(raw,"Blowfish");

			SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
			byte[] seed = random.generateSeed(8);
			random.setSeed(seed);

			enCipher = Cipher.getInstance("Blowfish");
			enCipher.init(Cipher.ENCRYPT_MODE, skeySpec, random);
			deCipher = Cipher.getInstance("Blowfish");
			deCipher.init(Cipher.DECRYPT_MODE, skeySpec);
		}
		catch (Exception ex) {
			logger.error("Unable to initialize the Cipher using \""+encryptionKey+"\"",ex);
			return false;
		}
		return true;
	}

	//Determine whether the current key is correct for this idTable.
	private static boolean checkKey(String encryptionKey) {
		String encryptedKey;
		getIdTable();
		try { encryptedKey = getKey(encryptionKey); }
		catch (Exception quit) { return false; }
		String storedEncryptedKey = idTable.getProperty("key");
		if (storedEncryptedKey == null) {
			//The idTable doesn't have a key assigned.
			if (idTable.size() == 0) {
				//The table is empty. Enter the current key
				//and tell the user all is well.
				idTable.setProperty("key",encryptedKey);
				storeNow();
				return true;
			}
			else {
				//The table already has entries, but there is no key property.
				//This must be an old table from the dark time before key assignment
				//was possible. Force the key back to the default key but don't
				//store it.
				return initializeCipher(defaultKey);
			}
		}
		else if (idTable.size() == 1) {
			//The table only has the key in it, so we can force it
			//to the encryption key that is now being supplied.
			idTable.setProperty("key",encryptedKey);
			storeNow();
			return true;
		}
		//Okay, we got here because the storedEncryptedKey is not null
		//and there are other entries in the table. Now it matters whether
		//the key is correct for the file, so check to see that the key
		//matches the encryptedKey.
		return encryptedKey.equals(storedEncryptedKey);
	}

	//Get the IdTable Properties object
	private static synchronized Properties getIdTable() {
		if (idTable != null) return idTable;
		if (propertiesFile == null)
			logger.error("Attempt to access IdTable before initialization.");
		else if (!propertiesFile.exists()) {
			idTable = new Properties();
			storeNow();
		}
		else {
			idTable = load(propertiesFile);
			dirty = false;
		}
		return idTable;
	}

	//Load a properties file
	private static Properties load(File file) {
		try {
			Properties props = new Properties();
			FileInputStream fis = new FileInputStream(file);
			props.load(fis);
			fis.close();
			return props;
		}
		catch (Exception e) {
			logger.warn("Unable to load the IdTable: ("+
						file.getAbsolutePath()+"): "+
						e.getMessage());
		}
		return null;
	}

	/**
	 * Set the enable flag for the storeLater method. Programs that
	 * use the IdTable class for anonymization and can catch program
	 * shutdowns should disable storeLater and make a call to storeNow
	 * just before exiting. Programs that cannot guarantee to shut down
	 * gracefully should enable storeLater.
	 * @param enable true if storeLater is enabled; false otherwise.
	 */
	public static void setStoreLaterEnable(boolean enable) {
		storeLaterEnabled = enable;
	}

	/**
	 * If storeLater is enabled, schedule a delayed store of the IdTable
	 * depending on whether it is dirty.
	 * The anonymizer calls storeLater(false) after processing each image.
	 * This method allows all the IdTable updates for one minute to be
	 * accumulated and stored once, thus reducing the number of table stores.
	 * @param forceStore true if the IdTable is to be written
	 * even if it is clean; false if the IdTable is to be written
	 * only if it is dirty.
	 */
	public static void storeLater(boolean forceStore) {
		if (forceStore || dirty) storeLater();
	}

	/**
	 * If storeLater is enabled, schedule a delayed store of the IdTable.
	 */
	public static void storeLater() {
		if (!storeLaterEnabled) return;
		long now = System.currentTimeMillis();
		if ((now - lastTime) > delay) {
			lastTime = now;
			Thread storeTable = new Thread() {
				public void run() {
					try { Thread.sleep(delay); }
					catch (Exception ignore) { }
					storeNow();
				}
			};
			storeTable.start();
		}
	}

	/**
	 * Force an immediate store of the IdTable depending on
	 * whether it is dirty.
	 * @param forceStore true if the IdTable is to be written
	 * even if it is clean; false if the IdTable is to be written
	 * only if it is dirty.
	 */
	public static synchronized void storeNow(boolean forceStore) {
		if (forceStore || dirty) storeNow();
	}

	/**
	 * Force an immediate store of the IdTable.
	 */
	public static synchronized void storeNow() {
		if ((idTable != null) && (propertiesFile != null)) {
			try {
				FileOutputStream fos = new FileOutputStream(propertiesFile);
				idTable.store(fos,"IdTable");
				fos.flush();
				fos.close();
				dirty = false;
			}
			catch (Exception e) {
				logger.warn("Unable to save the IdTable: ("+
							propertiesFile.getAbsolutePath()+"): "+
							e.getMessage());
			}
		}
	}

	/**
	 * Get a new UID. A new UID is constructed by appending a
	 * sequentially increasing integer to the supplied prefix.
	 * The new UID is placed in the mapping table mapped to itself.
	 * @param prefix the UID root to serve as a prefix for the UID.
	 * @return the new UID.
	 */
	public static synchronized String getUID(String prefix) {
		getIdTable();
		int next = getParam("UN");
		String newUID = prefix.trim() + "." + next;
		newUID = newUID.replaceAll("\\.+","."); //remove multiple sequential dots
		next++;
		setProperty("UN",Integer.toString(next));
		String index = ("U["+newUID+"]");
		setProperty(index,newUID);
		return newUID;
	}

	/**
	 * Get the original UID corresponding to a remapped value.
	 * @param uid the remapped UID.
	 * @return the original UID from the table or "NOT.AVAILABLE".
	 */
	public static synchronized String getOriginalUID(String uid) {
		getIdTable();
		uid = uid.trim();
		BASE64Decoder b64Decoder = new BASE64Decoder();
		Enumeration e = idTable.keys();
		String key;
		String value;
		while (e.hasMoreElements()) {
			key = (String)e.nextElement();
			value = idTable.getProperty(key);
			if (value.equals(uid)) {
				try {
					byte[] encryptedKey = b64Decoder.decodeBuffer(key);
					byte[] decryptedKey = deCipher.doFinal(encryptedKey);
					String decodedKey = new String(decryptedKey,charsetName);
					decodedKey = decodedKey.trim();
					if (decodedKey.startsWith("U[") && decodedKey.endsWith("]")) {
						return decodedKey.substring(2,decodedKey.length() -1);
					}
				}
				catch (Exception skip) { }
			}
		}
		return "NOT.AVAILABLE";
	}

	/**
	 * Get the remapped UID corresponding to an original value.
	 * @param uid the original UID.
	 * @return the remapped UID from the table or "NOT.AVAILABLE".
	 */
	public static synchronized String getRemappedUID(String uid) {
		getIdTable();
		uid = uid.trim();
		String index = ("U["+uid+"]");
		String newUID = getProperty(index);
		if (newUID != null) return newUID;
		return "NOT.AVAILABLE";
	}

	/**
	 * Get a replacement for a UID. If the UID has been seen before,
	 * its replacement value is returned. If it has not been seen before,
	 * a new UID is constructed by appending a sequentially increasing
	 * integer to the supplied prefix.
	 * @param prefix the UID root to serve as a prefix for the replacement.
	 * @param uid the UID to be replaced.
	 * @return the replacement UID.
	 */
	public static synchronized String getUID(String prefix, String uid) {
		getIdTable();
		uid = uid.trim();
		String index = ("U["+uid+"]");
		String newUID = getProperty(index);
		if (newUID != null) return newUID;
		int next = getParam("UN");
		newUID = prefix.trim() + "." + next;
		newUID = newUID.replaceAll("\\.+","."); //remove multiple sequential dots
		next++;
		setProperty("UN",Integer.toString(next));
		setProperty(index,newUID);
		return newUID;
	}

	/**
	 * Get an integer which has not been returned before. This function
	 * returns the next integer after the last one that was returned.
	 * @return the integer.
	 */
	public static synchronized String getInteger() {
		getIdTable();
		String intIndex = ("IN");
		String nextInt = getProperty(intIndex);
		if (nextInt == null) nextInt = "1";
		int next = 1;
		try { next = Integer.parseInt(nextInt); }
		catch (Exception ignore) { }
		next++;
		setProperty(intIndex,Integer.toString(next));
		return nextInt;
	}

	/**
	 * Get a replacement for a DICOM ID element. The IDs generated are
	 * unique to a DICOM element. Note that since DICOM IDs are not
	 * globally unique, the replacement IDs are not globally unique.
	 * If the ID has been seen before for the element,
	 * its previously determined replacement value is returned.
	 * If it has not been seen before, a new ID is constructed from a
	 * sequentially increasing integer.
	 * @param element the DICOM element name (from the dcm4che Tag dictionary).
	 * @param key the ID value to be replaced.
	 * @return the replacement ID.
	 */
	public static synchronized String getGenericID(String element, String key) {
		getIdTable();
		element = element.trim();
		key = key.trim();
		String index = ("G"+element+"["+key+"]");
		String newID = getProperty(index);
		if (newID != null) return newID;
		String nextIndex = ("GN"+element);
		int next = getParam(nextIndex);
		newID = Integer.toString(next);
		next++;
		setProperty(nextIndex,Integer.toString(next));
		setProperty(index,newID);
		return newID;
	}

	/**
	 * Get an accession number. This method is included only for backward
	 * compatibility. It simply calls getGenericID(element,key).
	 * @param element the DICOM element name (from the dcm4che Tag dictionary).
	 * @param key the value to be replaced.
	 * @return the next accession number.
	 */
	public static synchronized String getAccessionNumber(String element, String key) {
		return getGenericID(element,key);
	}

	/**
	 * Get a replacement for a DICOM Patient ID.
	 * If the Patient ID has been seen before,its previously determined
	 * replacement value is returned. If it has not been seen before,
	 * a new Patient ID is constructed from the supplied prefix, a sequentially
	 * increasing integer, and the supplied suffix.
	 * @param siteid the site ID for the site on which the patient ID was created.
	 * For trials using globally created patient IDs, all the site IDs can be set to
	 * the empty string ("").
	 * @param ptid the patient ID.
	 * @param prefix the prefix of the new Patient ID.
	 * @param first the starting value of the sequentially increasing integer
	 * used to generate IDs.
	 * @param fieldwidth the minimum width of the integer part of the Patient ID, with
	 * leading zeroes supplied if the integer does not require the full field width.
	 * @param suffix the suffix of the new Patient ID.
	 * @return the replacement Patient ID.
	 */
	public static synchronized String getPtID(
			String siteid, String ptid, String prefix, int first, int fieldwidth, String suffix) {
		getIdTable();
		siteid = siteid.trim();
		ptid = ptid.trim();
		String index = ("P["+siteid+"]["+ptid+"]");
		String newPtID = getProperty(index);
		if (newPtID != null) return newPtID;
		int next = getParam("PN",first);
		newPtID = Integer.toString(next);
		while (newPtID.length() < fieldwidth) newPtID = "0" + newPtID;
		newPtID = prefix + newPtID + suffix;
		next++;
		setProperty("PN",Integer.toString(next));
		setProperty(index,newPtID);
		return newPtID;
	}

	/**
	 * Get the original date corresponding to a remapped value for a patient and element.
	 * @param siteid the site ID for the site on which the patient ID was created.
	 * For trials using globally created patient IDs, all the site IDs can be set to
	 * the empty string ("").
	 * @param ptid the original patient ID.
	 * @param element the DICOM element tag (from the dcm4che Tag dictionary).
	 * @param date the remapped date.
	 * @param base the base date used as the starting date for offsetting dates.
	 * @return the original date computed from the table or "NOT.AVAILABLE".
	 */
	public static synchronized String getOriginalDate(
					String siteid,
					String ptid,
					String element,
					String date,
					String base) {
		getIdTable();
		siteid = siteid.trim();
		ptid = ptid.trim();
		element = element.trim();
		String index = ("D["+siteid+"]["+ptid+"]"+element);
		String first = getProperty(index);
		if (first == null) return "NOT.AVAILABLE";
		GregorianCalendar firstCal = new GregorianCalendar();
		firstCal.setTimeInMillis(Long.parseLong(first.trim()));
		GregorianCalendar dateCal = getCal(date);
		GregorianCalendar baseCal = getCal(base);
		long firstMillis = firstCal.getTimeInMillis();
		long dateMillis = dateCal.getTimeInMillis();
		long baseMillis = baseCal.getTimeInMillis();
		long offsetMillis = dateMillis + firstMillis - baseMillis;
		dateCal.setTimeInMillis(offsetMillis);
		String origDate = intToString(dateCal.get(Calendar.YEAR), 4) +
						  intToString(dateCal.get(Calendar.MONTH) + 1, 2) +
						  intToString(dateCal.get(Calendar.DAY_OF_MONTH), 2);
		return origDate;
	}

	/**
	 * Get a replacement for a date. If the element has not been previously seen,
	 * the supplied date is inserted in the database and the base date is
	 * returned. If the supplied element has been previously seen, the offset
	 * from the database value is computed for the supplied date, added to the
	 * base date, and returned as the offset date. This has the effect of keeping
	 * all dates for this element in proper chronological relationship while
	 * offsetting them to start at the base date. Note that this approach uses
	 * a single base date for each element but not for each patient/element.
	 * @param siteid the site ID for the site on which the patient ID was created.
	 * For trials using globally created patient IDs, all the site IDs can be set to
	 * the empty string ("").
	 * @param ptid the patient ID.
	 * @param element the DICOM element tag (from the dcm4che Tag dictionary).
	 * @param date the date value of the element.
	 * @param base the base date to use as the starting date for offsetting dates.
	 * @return the replacement date.
	 */
	public static synchronized String getOffsetDate(
					String siteid,
					String ptid,
					String element,
					String date,
					String base) {
		getIdTable();
		siteid = siteid.trim();
		ptid = ptid.trim();
		element = element.trim();
		String index = ("D["+siteid+"]["+ptid+"]"+element);
		String first = getProperty(index);
		if (first == null) {
			GregorianCalendar firstCal = getCal(date);
			setProperty(index,Long.toString(firstCal.getTimeInMillis()));
			return base;
		}
		GregorianCalendar firstCal = new GregorianCalendar();
		firstCal.setTimeInMillis(Long.parseLong(first.trim()));
		GregorianCalendar dateCal = getCal(date);
		GregorianCalendar baseCal = getCal(base);
		long firstMillis = firstCal.getTimeInMillis();
		long dateMillis = dateCal.getTimeInMillis();
		long baseMillis = baseCal.getTimeInMillis();
		long offsetMillis = dateMillis - firstMillis + baseMillis;
		dateCal.setTimeInMillis(offsetMillis);
		String newDate = intToString(dateCal.get(Calendar.YEAR), 4) +
						 intToString(dateCal.get(Calendar.MONTH) + 1, 2) +
						 intToString(dateCal.get(Calendar.DAY_OF_MONTH), 2);
		return newDate;
	}

	//Get a GregorianCalendar for a specific date.
	private static GregorianCalendar getCal(String date) {
		//do a little filtering to protect against the most common booboos
		date = date.replaceAll("\\D","");
		if (date.length() != 8) {
			return new GregorianCalendar(2000,0,1);
		}
		if (date.startsWith("00")) date = "19" + date.substring(2);
		//now make the calendar
		int year = Integer.parseInt(date.substring(0,4));
		int month = Integer.parseInt(date.substring(4,6));
		int day = Integer.parseInt(date.substring(6,8));
		return new GregorianCalendar(year,month-1,day);
	}

	//Convert an int to a specific width string with leading zeroes.
	private static String intToString(int n, int digits) {
		String s = Integer.toString(n);
		int k = digits - s.length();
		for (int i=0; i<k; i++) s = "0" + s;
		return s;
	}

	//Get a property from the database, encrypting the key.
	private static String getProperty(String key) {
		try { return idTable.getProperty(getKey(key)); }
		catch (Exception ex) { return null; }
	}

	//Put a property into the database, encrypting the key.
	private static void setProperty(String key, String prop) {
		try {
			idTable.setProperty(getKey(key),prop);
			dirty = true;
		}
		catch (Exception ignore) { }
	}

	//Get an integer property from the database, with a default value of 1.
	private static int getParam(String key) {
		return getParam(key,1);
	}

	//Get an integer property from the database, with a default value.
	private static int getParam(String key, int defaultValue) {
		try { return Integer.parseInt(getProperty(key)); }
		catch (Exception ignore) { }
		return defaultValue;
	}

	//Encrypt a key.
	private static String getKey(String s) throws Exception {
		try {
			byte[] encrypted = enCipher.doFinal(s.replaceAll("\\s","").getBytes(charsetName));
			return b64Encoder.encode(encrypted);
		}
		catch (Exception ex) {
			logger.warn("Exception in getKey: " + ex.toString());
			return null;
		}
	}

}

