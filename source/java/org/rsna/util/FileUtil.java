/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Encapsulates static methods for working with files and directories.
 */
public class FileUtil {

	public static Charset latin1 = Charset.forName("iso-8859-1");

	/**
	 * Delete a file. If the file is a directory, delete the contents
	 * of the directory and all its child directories, then delete the
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

	/**
	 * Read a text file completely, using the latin-1 character set encoding.
	 * @param filename the path to the file to read.
	 * @return the text of the file, or an empty string if an error occurred.
	 */
	public static String getFileText(String filename) {
		return getFileText(filename,latin1);
	}

	/**
	 * Read a text file completely, using the latin-1 character set encoding.
	 * @param file the file to read.
	 * @return the text of the file, or an empty string if an error occurred.
	 */
	public static String getFileText(File file) {
		return getFileText(file,latin1);
	}

	/**
	 * Read a text file completely, using the specified encoding.
	 * @param filename the path to the file to read.
	 * @param charset the character set to use for the encoding of the file.
	 * @return the text of the file, or an empty string if an error occurred.
	 */
	public static String getFileText(String filename, Charset charset) {
		return getFileText(new File(filename),charset);
	}

	/**
	 * Read a text file completely, using the specified encoding.
	 * @param file the file to read.
	 * @param charset the character set to use for the encoding of the file.
	 * @return the text of the file, or an empty string if an error occurred.
	 */
	public static String getFileText(File file, Charset charset) {
		try {
			BufferedReader br = new BufferedReader(
									new InputStreamReader(
										new FileInputStream(file),charset));
			StringWriter sw = new StringWriter();
			int n;
			char[] cbuf = new char[1024];
			while ((n=br.read(cbuf,0,cbuf.length)) != -1) sw.write(cbuf,0,n);
			br.close();
			return sw.toString();
		}
		catch (Exception e) { return ""; }
	}

	/**
	 * Write a string to a text file, using the latin-1 character set encoding.
	 * @param file the file to write.
	 * @param text the string to write into the file.
	 * @return true if the operation succeeded completely; false otherwise.
	 */
	public static boolean setFileText(File file, String text) {
		return setFileText(file,latin1,text);
	}

	/**
	 * Write a string to a text file, using the specified encoding.
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

	/**
	 * Copy a file.
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