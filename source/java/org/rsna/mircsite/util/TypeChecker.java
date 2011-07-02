/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.PropertyResourceBundle;

/**
 * A class to determine whether a file is of a type that is allowed on a MIRC site.
 * File types are stored in a properties file called FileTypes.properties.
 * Individual extensions are specified by a period followed by the extension text.
 */
public class TypeChecker {

	ArrayList<String> theList = null;

	public TypeChecker() {
		try {
			PropertyResourceBundle bundle = null;
			InputStream stream = this.getClass().getClassLoader().getResourceAsStream("FileTypes.properties");
			if (stream == null) return;
			bundle = new PropertyResourceBundle(stream);
			// put all the keys into the ArrayList
			theList = new ArrayList<String>();
			Enumeration<String> en = bundle.getKeys();
			while (en.hasMoreElements()) {
				theList.add(en.nextElement().trim().toLowerCase());
			}
		}
		catch (Exception ex ) {
			theList = null;
		}
	}

	/**
	 * Determine whether the extension of a file exists in the FileTypes.properties file.
	 * @param file the file whose type is to be tested.
	 * @return true if the list does not exist or if the file's extension exists in the list; false otherwise.
	 */
	public boolean isFileAllowed(File file) {
		if (theList == null) return true;
		FileObject fo = new FileObject(file);
		String ext = fo.getExtension().toLowerCase();
		return theList.contains(ext);
	}

}
