/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import java.io.File;

/**
 * Encapsulates static methods for obtaining an encryption key.
 */
public class Key {

	static String nonce = "tszyihnnphlyeaglle";
	static String pad = "===";

	/**
	 * Get a 128-bit encryption key.
	 * @param parent the JFrame of the caller, used to center the dialog.
	 * @param prompt true if the user is to be prompted for the key; false
	 * if the result is to be null, indicating the default key.
	 * @return the key.
	 */
	public static String getEncryptionKey(JFrame parent, boolean prompt) {
		return getEncryptionKey(parent,prompt,128);
	}

	/**
	 * Return an encryption key of the specified size specified by the user.
	 * @param parent the JFrame of the caller, used to center the dialog.
	 * @param prompt true if the user is to be prompted for the key; false
	 * if the result is to be null, indicating the default key.
	 * @return the requested key length.
	 */
	public static int getEncryptionKeySize(JFrame parent, boolean prompt) {
		int len = 128;
		if (!prompt) return len;
		String text = JOptionPane.showInputDialog(
			parent,
			  "Enter the required key length.\n"
			+ "Click \"Cancel\" to use the default length ("+len+").\n\n");
		if (text == null) return len;
		text = text.trim();
		if (text.equals("")) return len;
		try { len = Integer.parseInt(text); }
		catch (Exception useDefault) { }
		return len;
	}

	/**
	 * Return an encryption key of the specified size.
	 * @param parent the JFrame of the caller, used to center the dialog.
	 * @param prompt true if the user is to be prompted for the key; false
	 * if the result is to be null, indicating the default key.
	 * @param size the length of the key.
	 * @return the key of the specified length.
	 */
	public static String getEncryptionKey(JFrame parent, boolean prompt, int size) {

		//If the parent is planning to use the default key, return null.
		if (!prompt) return null;

		//Ask the user for a string.
		String text = JOptionPane.showInputDialog(
			parent,
			  "Enter the encryption key.\n"
			+ "To read a keyfile, enter the path, starting with '@'.\n"
			+ "Click \"Cancel\" to use the default key.\n\n");

		if (text == null) return null;
		text = text.trim();
		if (text.equals("")) return null;

		//If it's a keyfile, get the text, and do not modify it.
		if (text.startsWith("@")) {
			File keyfile = new File(text.substring(1));
			if (!keyfile.exists()) {
				JOptionPane.showMessageDialog(parent,"Key file not found.");
				return null;
			}
			text = FileUtil.getFileText(keyfile);
			if (text.trim().equals("")) {
				JOptionPane.showMessageDialog(parent,"The key file was empty.");
				return null;
			}
			return text;
		}

		//Now make it into a base-64 string encoding the right number of bits.
		text = text.replaceAll("[^a-zA-Z0-9+/]","");

		//Figure out the number of characters we need.
		int requiredChars = (size + 5) / 6;
		int requiredGroups = (requiredChars + 3) / 4;
		int requiredGroupChars = 4 * requiredGroups;

		//If we didn't get enough characters, then throw some junk on the end.
		while (text.length() < requiredChars) text += nonce;

		//Take just the right number of characters we need for the size.
		text = text.substring(0,requiredChars);

		//And return the string padded to a full group.
		return (text + pad).substring(0,requiredGroupChars);
	}
}

