/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encapsulates static methods for working with files and directories.
 */
public class FileUtil {


	/**
	 * Counts the files in a directory, including any directory files.
	 * @param dir the directory.
	 * @return the number of files in the directory, or zero if not a directory.
	 */
	public static int getFileCount(File dir) {
		return getFileCount(dir,null);
	}

	/**
	 * Counts the files in a directory that match a FileFilter.
	 * @param dir the directory.
	 * @param filter the filter for selecting files to count. If null,
	 * no filtering is applied and all files are counted
	 * @return the number of files in the directory that match the filter,
	 * or zero if not a directory.
	 */
	public static int getFileCount(File dir, FileFilter filter) {
		File[] fileList = dir.listFiles(filter);
		return (fileList != null) ? fileList.length : 0;
	}

	/**
	 * Deletes a file. If the file is a directory, deletes the contents
	 * of the directory and all its child directories, then deletes the
	 * directory itself.
	 * @param file the file to delete.
	 * @return true if the operation succeeded completely; false otherwise.
	 */
	public static boolean deleteAll(File file) {
		boolean b = true;
		if (file.exists()) {
			if (file.isDirectory()) {
				try {
					File[] files = file.listFiles();
					for (int i=0; i<files.length; i++) b &= deleteAll(files[i]);
				}
				catch (Exception e) { return false; }
			}
			b &= file.delete();
		}
		return b;
	}

	public static Charset latin1 = Charset.forName("ISO-8859-1");
	public static Charset utf8 = Charset.forName("UTF-8");

	/**
	 * Reads a text file completely, trying to obtain the charset from the
	 * file itself, and defaulting to UTF-8 if it fails.
	 * @param file the file to read.
	 * @return the text of the file, or an empty string if an error occurred.
	 */
	public static String getFileText(File file) {
		String text = getFileText(file,utf8);
		Charset charset = getEncoding(text);
		if (charset.name().equals(utf8.name())) return text;
		return getFileText(file,charset);
	}

	/**
	 * Reads a text file completely, using the specified encoding, or
	 * UTF-8 if the specified encoding is not supported.
	 * @param file the file to read.
	 * @param encoding the name of the charset to use.
	 * @return the text of the file, or an empty string if an error occurred.
	 */
	public static String getFileText(File file, String encoding) {
		Charset charset;
		try { charset = Charset.forName(encoding); }
		catch (Exception ex) { charset = utf8; }
		return getFileText(file,charset);
	}

	/**
	 * Reads a text file completely, using the specified encoding.
	 * @param file the file to read.
	 * @param charset the character set to use for the encoding of the file.
	 * @return the text of the file, or an empty string if an error occurred.
	 */
	public static String getFileText(File file, Charset charset) {
		BufferedReader br = null;
		try {
			if (!file.exists()) return "";
			br = new BufferedReader(
					new InputStreamReader(
						new FileInputStream(file),charset));
			StringWriter sw = new StringWriter();
			int n;
			char[] cbuf = new char[1024];
			while ((n=br.read(cbuf,0,cbuf.length)) != -1) sw.write(cbuf,0,n);
			br.close();
			return sw.toString();
		}
		catch (Exception e) {
			if (br != null) {
				try { br.close(); }
				catch (Exception ignore) { }
			}
			return "";
		}
	}

	/**
	 * Writes a string to a text file, trying to determine the desired encoding
	 * from the text itself and using the UTF-8 encoding as a default.
	 * @param file the file to write.
	 * @param text the string to write into the file.
	 * @return true if the operation succeeded completely; false otherwise.
	 */
	public static boolean setFileText(File file, String text) {
		Charset charset = getEncoding(text);
		return setFileText(file,charset,text);
	}

	/**
	 * Writes a string to a text file, using the specified encoding, or
	 * UTF-8 if the specified encoding is not supported.
	 * @param file the file to write.
	 * @param encoding the name of the charset to use.
	 * @param text the string to write into the file.
	 * @return true if the operation succeeded completely; false otherwise.
	 */
	public static boolean setFileText(File file, String encoding, String text) {
		Charset charset;
		try { charset = Charset.forName(encoding); }
		catch (Exception ex) { charset = utf8; }
		return setFileText(file,charset,text);
	}

	/**
	 * Writes a string to a text file, using the specified encoding.
	 * @param file the file to write.
	 * @param charset the character set to use for the encoding of the file.
	 * @param text the string to write into the file.
	 * @return true if the operation succeeded completely; false otherwise.
	 */
	public static boolean setFileText(File file, Charset charset, String text) {
		BufferedWriter bw = null;
		boolean result = true;
		try {
			bw = new BufferedWriter(
					new OutputStreamWriter(
						new FileOutputStream(file),charset));
			bw.write(text,0,text.length());
		}
		catch (Exception e) { result = false; }
		finally {
			try { bw.flush(); bw.close(); }
			catch (Exception ignore) { }
		}
		return result;
	}

	//Try to figure out the encoding in a text string based on the xml or html declaration.
	private static Charset getEncoding(String text) {

		//See if this is an xml document with an encoding declaration.
		Pattern xml = Pattern.compile(
			"^\\s*<\\?xml\\s+[^>]*\\s*encoding\\s*=\\s*(\"[^\"]*\")",
			Pattern.DOTALL | Pattern.MULTILINE);
		Matcher xmlMatcher = xml.matcher(text);
		if (xmlMatcher.find()) return getEncoding(xmlMatcher);

		//See if this is an html document with a charset declaration.
		Pattern html = Pattern.compile(
			"^\\s*<(html|HTML).*<(meta|META)\\s+[^>]*\\s*(charset|CHARSET)\\s*=\\s*(\"[^\"]*\"|[^\"\\s]*)",
			Pattern.DOTALL | Pattern.MULTILINE);
		Matcher htmlMatcher = html.matcher(text);
		if (htmlMatcher.find()) return getEncoding(htmlMatcher);

		//We don't recognize this document declaration; use UTF-8.
		//Maybe this should actually be ISO-8859-1 since
		//that is the web default encoding, but it is probably
		//better to default to UTF-8 because that will be better
		//for sites in the Far East, and the pain for the Europeans
		//will be minimal.
		return utf8;
	}

	//Get the Charset corresponding to a match in a Matcher.
	private static Charset getEncoding(Matcher matcher) {
		int groups = matcher.groupCount();
		String name = matcher.group(groups);
		if (name.startsWith("\"")) name = name.substring(1);
		if (name.endsWith("\"")) name = name.substring(0,name.length()-1);
		try { return Charset.forName(name); }
		catch (Exception ex) { return utf8; }
	}

	/**
	 * Copies a file.
	 * @param inFile the file to copy.
	 * @param outFile the copy.
	 * @return true if the operation succeeded completely; false otherwise.
	 */
	public static boolean copyFile(File inFile, File outFile) {
		BufferedInputStream in = null;
		BufferedOutputStream out = null;
		boolean result = true;
		try {
			in = new BufferedInputStream(new FileInputStream(inFile));
			out = new BufferedOutputStream(new FileOutputStream(outFile));
			byte[] buffer = new byte[4096];
			int n;
			while ( (n = in.read(buffer,0,4096)) != -1) out.write(buffer,0,n);
		}
		catch (Exception e) { result = false; }
		finally {
			try { out.flush(); out.close(); in.close(); }
			catch (Exception ignore) { }
		}
		return result;
	}

	/**
	 * Get an array of files from a directory and sort it by last-modified-date.
	 * This method ignores child directories.
	 * @param theDir the directory.
	 * @return the list of files in the directory, sorted by date last modified.
	 */
	public static File[] listSortedFiles(File theDir) {
		if (!theDir.isDirectory()) return new File[0];
		File[] files = theDir.listFiles();
		ArrayList<SortableFile> list = new ArrayList<SortableFile>();
		for (int i=0; i<files.length; i++) {
			if (files[i].isFile()) {
				list.add(new SortableFile(files[i]));
			}
		}
		SortableFile[] sortableFiles = new SortableFile[list.size()];
		sortableFiles = (SortableFile[])list.toArray(sortableFiles);
		Arrays.sort(sortableFiles);
		return sortableFiles;
	}

	private static class SortableFile extends File {
		private static final long serialVersionUID = 1231234l;
		long date;
		public SortableFile(File file) {
			super(file.getAbsolutePath());
			date = file.lastModified();
		}
		public int compareTo(File object) {
			long diff = date - ((SortableFile)object).date;
			return (diff > 0) ? 1 : ((diff < 0) ? -1 : 0);
		}
	}

	/**
	 * Finds the oldest file in a directory.
	 * @param dir the directory.
	 * @return the oldest file in the directory, or null if not a directory.
	 */
	public static File findOldestFile(File dir) {
		if (!dir.exists()) return null;
		if (!dir.isDirectory()) return null;
		File[] files = dir.listFiles();
		if (files.length == 0) return null;
		File oldest = files[0];
		for (int i=1; i<files.length; i++) {
			if (files[i].lastModified() < oldest.lastModified()) {
				oldest = files[i];
			}
		}
		return oldest;
	}

}