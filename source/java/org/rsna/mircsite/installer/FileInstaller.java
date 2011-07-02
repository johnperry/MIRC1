/*---------------------------------------------------------------
*  Copyright 2009 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.installer;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.*;
import javax.swing.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Class to encapsulate static methods for installing files.
 */
public class FileInstaller {

	/**
	 * Backup a file, creating the directory for the backup if necessary.
	 * @param file the file to be backed up.
	 * @param backup the backup.
	 */
	public static void backup(File file, File backup) {
		File parent = backup.getParentFile();
		if ((parent != null) && !parent.exists()) parent.mkdirs();
		if (file.exists() && !backup.exists()) {
			if (fileCopy(file, backup) != null) {
				System.out.println("   "+file+"\n      backed up to "+backup);
			}
			else JOptionPane.showMessageDialog(null, "Unable to backup\n" + file);
		}
	}

	/**
	 * Delete a file; if the file is a directory, delete all the file in the directory
	 * and in any child directory of the directory.
	 * @param f the file to be deleted.
	 * @return true if successful; false otherwise.
	 */
	public static boolean deleteAll(File f) {
		boolean b = true;
		if (f.exists()) {
			if (f.isDirectory()) {
				try {
					File[] files = f.listFiles();
					for (int i=0; i<files.length; i++) b &= deleteAll(files[i]);
				}
				catch (Exception e) { return false; }
			}
			b &= f.delete();
		}
		return b;
	}

	/**
	 * Copy a file.
	 * @param inFile the file to be copied.
	 * @param outPath the path to the copy.
	 * @return the absolute path to the copy.
	 */
	public static String fileCopy(File inFile, String outPath) {
		String path = outPath.trim();
		if (!path.endsWith(File.separator)) path += File.separator;
		File outFile = new File(path + inFile.getName());
		try {
			if (copy(new FileInputStream(inFile),outFile))
				return outFile.getAbsolutePath();
		} catch (Exception e) { }
		return null;
	}

	/**
	 * Copy a file.
	 * @param inFile the file to be copied.
	 * @param outFile the copy.
	 * @return the absolute path to the copy.
	 */
	public static String fileCopy(File inFile, File outFile) {
		try {
			if (copy(new FileInputStream(inFile),outFile))
				return outFile.getAbsolutePath();
		} catch (Exception e) { }
		return null;
	}

	/**
	 * Copy a resource to a file.
	 * @param object an object to use to get a Class, enabling us to get
	 * the resource as a stream.
	 * @param resource the path to the resource (in the JAR).
	 * @param outPath the path to the copy.
	 * @return the absolute path to the copy.
	 */
	public static File resourceCopy(Object object, String resource, String outPath) {
		String path = outPath.trim();
		if (!path.endsWith(File.separator)) path += File.separator;
		int i = resource.lastIndexOf("/") + 1;
		File outFile = new File(path + resource.substring(i));
		BufferedInputStream in = null;
		BufferedOutputStream out = null;
		if (copy(object.getClass().getResourceAsStream(resource), outFile))
			return outFile;
		else return null;
	}

	/**
	 * Copy a resource to a file.
	 * @param object an object to use to get a Class, enabling us to get
	 * the resource as a stream.
	 * @param resource the path to the resource (in the JAR).
	 * @param outFile the copy.
	 * @return the absolute path to the copy.
	 */
	public static File resourceCopy(Object object, String resource, File outFile) {
		BufferedInputStream in = null;
		BufferedOutputStream out = null;
		if (copy(object.getClass().getResourceAsStream(resource),outFile))
			return outFile;
		else return null;
	}

	/**
	 * Check whether a resource is available.
	 * @param object an object to use to get a Class, enabling us to get
	 * the resource.
	 * @param resource the path to the resource (in the JAR).
	 * @return true if the resource is available; false otherwise.
	 */
	public static boolean resourceIsAvailable(Object object, String resource) {
		return (object.getClass().getResource(resource) != null);
	}

	/**
	 * Copy an InputStream to a File.
	 * @param inputStream the InputStream to be copied.
	 * @param outFile the copy.
	 * @return true if the operation succeeded; false otherwise.
	 */
	public static boolean copy(InputStream inputStream, File outFile) {
		BufferedInputStream in = null;
		BufferedOutputStream out = null;
		boolean result = true;
		try {
			in = new BufferedInputStream(inputStream);
			out = new BufferedOutputStream(new FileOutputStream(outFile));
			byte[] buffer = new byte[4096];
			int n;
			while ( (n = in.read(buffer,0,4096)) != -1) out.write(buffer,0,n);
		}
		catch (Exception e) {
			result = false;
			JOptionPane.showMessageDialog(null,
						"Error copying " + outFile.getName() + "\n" + e.getMessage());
		}
		finally {
			try { out.flush(); out.close(); in.close(); }
			catch (Exception e) { }
		}
		return result;
	}

	/**
	 * Unpack a zip file, preserving the directory structure of the zip file.
	 * @param inZipFile the zip file.
	 * @param outDir the directory into which to unpack the zip file.
	 * @param excludes a list of filenames not to copy, or null if all files are to
	 * be copied.
	 * @return true if the operation succeeded; false otherwise.
	 */
	public static boolean unpackZipFile(File inZipFile,
										File outDir,
										String[] excludes) {
		if (!inZipFile.exists()) return false;
		try {
			ZipFile zipFile = new ZipFile(inZipFile);
			Enumeration zipEntries = zipFile.entries();
			while (zipEntries.hasMoreElements()) {
				ZipEntry entry = (ZipEntry)zipEntries.nextElement();
				String name = entry.getName().replace('/',File.separatorChar);
				if (!skip(name,excludes)) {
					File outFile = new File(outDir, name);
					(new File(outFile.getAbsolutePath())).getParentFile().mkdirs();
					if (!entry.isDirectory()) {
						BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outFile));
						BufferedInputStream in = new BufferedInputStream(zipFile.getInputStream(entry));
						int size = 1024;
						int n = 0;
						byte[] b = new byte[size];
						while ((n = in.read(b,0,size)) != -1) out.write(b,0,n);
						in.close();
						out.close();
					}
				}
			}
			zipFile.close();
			return true;
		}
		catch (Exception e) {
			JOptionPane.showMessageDialog(
				null,
				"Exception occurred in unpackZipFile\n" +
				"inZipFile = "+inZipFile.getName()+"\n" +
				"outDir = "+outDir.getAbsolutePath()+"\n" +
				e.toString());
			return false;
		}
	}

	//See if a string is included in an array of strings.
	private static boolean skip(String name, String[] excludes) {
		if (excludes == null) return false;
		for (int i=0; i<excludes.length; i++) {
			if (name.equals(excludes[i])) return true;
		}
		return false;
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
			"^\\s*<\\?xml\\s+[^>]*\\s*encoding\\s*=\\s*\"",
			Pattern.DOTALL | Pattern.MULTILINE);
		Matcher xmlMatcher = xml.matcher(text);
		if (xmlMatcher.find()) {
			int start = xmlMatcher.end();
			int end = text.indexOf("\"",start);
			if (end == -1) return utf8;
			String name = text.substring(start,end);
			try { return Charset.forName(name); }
			catch (Exception ex) { return utf8; }
		}

		//See if this is an html document with a charset declaration.
		Pattern html = Pattern.compile(
			"^\\s*<(html|HTML).*<(meta|META)\\s+[^>]*\\s*(charset|CHARSET)\\s*=\\s*\"",
			Pattern.DOTALL | Pattern.MULTILINE);
		Matcher htmlMatcher = html.matcher(text);
		if (htmlMatcher.find()) {
			int start = htmlMatcher.end();
			int end = text.indexOf("\"",start);
			if (end == -1) return utf8;
			String name = text.substring(start,end);
			try { return Charset.forName(name); }
			catch (Exception ex) { return utf8; }
		}

		//We don't recognize this document, use UTF-8.
		//Maybe this should actually be ISO-8859-1, since
		//that is the web default encoding, but it is probably
		//better to default to UTF-8 because that will be better
		//for sites in the Far East, and the pain for the Europeans
		//will be minimal.
		return utf8;
	}

	/** An empty array of Strings. */
	static final String[] empty = new String[] {};

	/**
	 * Check the contents of a directory to see if all
	 * the files in an array are present.
	 * @param dir the directory.
	 * @param include the list of filenames
	 * @return true if all the filenames are included in the
	 * directory; false otherwise.
	 */
	public static boolean contentsCheck(File dir, String[] include) {
		return contentsCheck(dir, include, empty);
	}

	/**
	 * Check the contents of a directory to see if all
	 * the files in one array are present and all the files
	 * in another array are not.
	 * @param dir the directory.
	 * @param include the list of filenames that must be present.
	 * @param exclude the list of filenames that must be absent.
	 * @return true if the directory contents meet the requirements;
	 * false otherwise.
	 */
	public static boolean contentsCheck(File dir, String[] include, String[] exclude) {
		if (!dir.isDirectory()) return false;
		if (include != null) {
			for (int i=0; i<include.length; i++) {
				File file = new File(dir, include[i]);
				if (!file.exists()) {
					System.out.println("   "+file + " does not exist");
					return false;
				}
			}
		}
		if (exclude != null) {
			for (int i=0; i<exclude.length; i++) {
				File file = new File(dir, exclude[i]);
				if (file.exists()) {
					System.out.println("   "+file + " exists");
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Determine whether the current installer is a lite version.
	 * The lite installer only includes the MIRC libraries, not the
	 * general libraries like dcm4che, xerces, etc. This method looks for
	 * the dcm4che.jar file. If it is missing, this is a lite installer.
	 * @return true if this is a lite installer; false otherwise.
	 */
	public static boolean isLite() {
		String resource = "/libraries/dcm4che.jar";
		return !resourceExists(resource);
	}

	/**
	 * Determine whether a resource exists in the jar file.
	 * @return true if the resource is in the jar; false otherwise.
	 */
	public static boolean resourceExists(String resource) {
		return (resource.getClass().getResource(resource) != null);
	}

	/**
	 * Copy the MIRC libraries from the jar to the Tomcat/shared/lib directory.
	 * @param object an object to be used to get access to the jar.
	 */
	public static boolean installLibraries(Object object) {
		System.out.println("install the libraries");
		String[] libNames = new String[] {"cos.jar","dcm4che.jar","getopt.jar",
										  "log4j.jar","dicom.jar","gif.jar",
										  "mircservlets.jar", "jdbm.jar",
										  "mircutil.jar","serializer.jar",
										  "xalan.jar", "xercesImpl.jar",
										  "xml-apis.jar", "xsltc.jar",
										  "batik-rasterizer-partial.jar",
										  "commons-pool-1.4.jar",
										  "xmldb.jar", "xmlrpc-client-3.1.1.jar",
										  "xmlrpc-common-3.1.1.jar", "ws-commons-util-1.0.2.jar"};
		String resource;
		boolean result = true;
		//Delete the mircacq.jar file if it exists
		//mircacq.jar has been replaced by dicom.jar
		File mircacq = new File(Installer.lib, "mircacq.jar");
		if (mircacq.exists()) mircacq.delete();
		//Delete the old xmlrpc if it exists
		//xmlrpc-1.2-patched.jar has been replaced by xmlrpc-client-3.1.1.jar.
		File oldXmlrpc = new File(Installer.lib, "xmlrpc-1.2-patched.jar");
		if (oldXmlrpc.exists()) oldXmlrpc.delete();
		//Now try to install everything in the list.
		for (int i=0; i<libNames.length; i++) {
			resource = "/libraries/" + libNames[i];
			if (resourceExists(resource)) {
				File dest = new File(Installer.lib, libNames[i]);
				boolean copy = (resourceCopy(object, resource, dest) != null);
				result &= copy;
				if (copy) System.out.println("   "+resource + " copied to "+dest);
				else System.out.println("   could not copy "+resource+" to "+dest);
			}
			else System.out.println("   could not find "+resource);
		}
		//Install the Smart Memory Realm
		resource = "/files/smartmemoryrealm.jar";
		if (resourceExists(resource)) {
			File dest = new File(Installer.lib, "smartmemoryrealm.jar");
			boolean copy = (resourceCopy(object, resource, dest) != null);
			result &= copy;
			if (copy) System.out.println("   "+resource + " copied to "+dest);
			else System.out.println("   could not copy "+resource+" to "+dest);
		}
		else System.out.println("   could not find "+resource);
		System.out.println("finished installing the libraries " + (result ? "[OK]" : "[FAILED]"));
		return result;
	}

	/**
	 * Make a String from an XML DOM Node.
	 * @param node the node at the top of the tree.
	 */
	public static String toString(Node node) {
		StringBuffer sb = new StringBuffer();
		renderNode(sb,node);
		return sb.toString();
	}

	//Recursively walk the tree and write the nodes to a StringBuffer.
	private static void renderNode(StringBuffer sb, Node node) {
		switch (node.getNodeType()) {

			case Node.DOCUMENT_NODE:
				//sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
				NodeList nodes = node.getChildNodes();
				if (nodes != null) {
					for (int i=0; i<nodes.getLength(); i++) {
						renderNode(sb,nodes.item(i));
					}
				}
				break;

			case Node.ELEMENT_NODE:
				String name = node.getNodeName();
				NamedNodeMap attributes = node.getAttributes();
				if (attributes.getLength() == 0) {
					sb.append("<" + name + ">");
				}
				else {
					sb.append("<" + name + " ");
					int attrlen = attributes.getLength();
					for (int i=0; i<attrlen; i++) {
						Node current = attributes.item(i);
						sb.append(current.getNodeName() + "=\"" +
							escapeChars(current.getNodeValue()));
						if (i < attrlen-1)
							sb.append("\" ");
						else
							sb.append("\">");
					}
				}
				NodeList children = node.getChildNodes();
				if (children != null) {
					for (int i=0; i<children.getLength(); i++) {
						renderNode(sb,children.item(i));
					}
				}
				sb.append("</" + name + ">");
				break;

			case Node.TEXT_NODE:
			case Node.CDATA_SECTION_NODE:
				sb.append(escapeChars(node.getNodeValue()));
				break;

			case Node.PROCESSING_INSTRUCTION_NODE:
				sb.append("<?" + node.getNodeName() + " " +
					escapeChars(node.getNodeValue()) + "?>");
				break;

			case Node.ENTITY_REFERENCE_NODE:
				sb.append("&" + node.getNodeName() + ";");
				break;

			case Node.DOCUMENT_TYPE_NODE:
				// Ignore document type nodes
				break;

			case Node.COMMENT_NODE:
				sb.append("<!--" + node.getNodeValue() + "-->");
				break;
		}
		return;
	}

	private static String escapeChars(String theString) {
		return theString.replace("&","&amp;")
						.replace(">","&gt;")
						.replace("<","&lt;")
						.replace("\"","&quot;")
						.replace("'","&apos;");
	}

}
