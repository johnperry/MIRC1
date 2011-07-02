/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import javax.servlet.ServletContext;

/**
 * A class to translate filename extensions into HTTP
 * content types.
 */
public class ContentType {

	private static Properties contentTypes;
	private static String filename = "content-types.properties";
	private static String defaultContentType = "application/default";

	/**
	 * Load the content types properties file.
	 */
	public static void load(ServletContext servletContext) {
		File file = new File(servletContext.getRealPath(filename));
		contentTypes = new Properties();
		try {
			FileInputStream fis = new FileInputStream(file);
			contentTypes.load(fis);
		}
		catch (Exception e) { }
	}

	/**
	 * Get the content type corresponding to a file.
	 * If the content type cannot be determined,
	 * "application/default" is returned.
	 * @param file the File object.
	 * @return the corresponding HTTP content type.
	 */
	public static String getContentType(File file) {
		return getContentType(file.getName());
	}

	/**
	 * Get the content type corresponding to a filename.
	 * If the content type cannot be determined,
	 * "application/default" is returned.
	 * @param filename the file name String.
	 * @return the corresponding HTTP content type.
	 */
	public static String getContentType(String filename) {
		String extension = "";
		int k = filename.lastIndexOf(".") + 1;
		if (k>0) extension = filename.substring(k).trim().toLowerCase();
		if (extension.equals("")) extension = "default";
		return getContentTypeForExtension(extension);
	}

	/**
	 * Get the content type corresponding to a file extension.
	 * If the content type cannot be determined,
	 * "application/default" is returned.
	 * @param extension the file extension, not including the leading period.
	 * @return the corresponding HTTP content type.
	 */
	public static String getContentTypeForExtension(String extension) {
		if (contentTypes == null) return defaultContentType;
		String ct = contentTypes.getProperty(extension.toLowerCase());
		if (ct == null) return defaultContentType;
		ct = ct.trim();
		if (ct.equals("")) return defaultContentType;
		return ct;
	}

}