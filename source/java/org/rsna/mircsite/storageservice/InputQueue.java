/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.storageservice;

import java.io.*;
import java.nio.charset.Charset;
import javax.servlet.*;
import javax.servlet.http.*;
import org.rsna.mircsite.util.*;
import org.w3c.dom.Document;

/**
 * Encapsulates static methods for managing the list of
 * documents submitted to a storage service.
 * <p>
 * The input queue is an XML file containing references to documents
 * that are to be either added to the index or made public.
 */
public class InputQueue {

	/**
	 * Parses the input queue XML file.
	 * @return the parsed XML DOM object created from the input queue XML file.
	 */
	public static synchronized Document getInputQueueXML()
			throws Exception {
		File inFile = new File(StorageConfig.basepath + StorageConfig.inputqueueFilename);
		if (!inFile.exists()) makeEmptyInputQueue(inFile);
		return XmlUtil.getDocument(StorageConfig.basepath + StorageConfig.inputqueueFilename);
	}

	/**
	 * Adds an element to the input queue with no request to make the document public.
	 * @param entry the relative path from the root of the servlet to the
	 * MIRCdocument XML file.
	 * @return true if the document was entered into the queue; false otherwise.
	 */
	public static synchronized boolean addQueueEntry(String entry) {
		return addQueueEntry(entry, false);
	}

	/**
	 * Adds an element to the input queue.
	 * @param entry the relative path from the root of the servlet to the
	 * MIRCdocument XML file.
	 * @param publish true if the document is to be made public; false otherwise.
	 * @return true if the document was entered into the queue; false otherwise.
	 */
	public static synchronized boolean addQueueEntry(String entry, boolean publish) {
		try {
			File queueFile = new File(StorageConfig.basepath + StorageConfig.inputqueueFilename);
			if (!queueFile.exists()) makeEmptyInputQueue(queueFile);
			String queue = FileUtil.getFileText(queueFile);
			entry = entry.replaceAll("\\\\","/").trim();
			int a = queue.indexOf(entry);
			if (a != -1) {
				//The element exists, make sure it has a publish attribute if necessary
				if (!publish) return true;
				String line = StringUtil.getLine(queue,a);
				if (line.indexOf("publish=\"yes\"") != -1) return true;
				line = line.substring(0,line.indexOf("<doc")) + "<doc publish=\"yes\""
				 + line.substring(line.indexOf(">"));
				queue = StringUtil.replaceLine(queue,a,line);
			}
			else {
				//The element doesn't exist, create one and add it at the end of the queue
				a = queue.indexOf("</inputqueue>");
				if (a == -1) return false;
				a = StringUtil.lineStart(queue,a);
				queue = queue.substring(0,a)
						+ "  <doc" + (publish? " publish=\"yes\"" : "") + ">"
						+ entry
						+ "</doc>\n" + queue.substring(a);
			}
			FileUtil.setFileText(queueFile,queue);
			return true;
		}
		catch (Exception e) { return false; }
	}

	/**
	 * Deletes an entry from the input queue.
	 * @param entry the relative path from the root of the servlet to the
	 * MIRCdocument XML file.
	 * @return the number of the document in the list of queue elements or -1 if
	 * the entry did not appear in the input queue. This value is used
	 * by the Admin Service to scroll the list of queue elements to the next queue element.
	 */
	public static synchronized int deleteQueueEntry(String entry) {
		File queueFile = new File(StorageConfig.basepath + "inputqueue.xml");
		if (!queueFile.exists()) makeEmptyInputQueue(queueFile);
		String queue = FileUtil.getFileText(queueFile);
		entry = entry.replaceAll("\\\\","/").trim();
		int a = queue.indexOf(entry);
		if (a == -1) return -1;
		int b = StringUtil.nextLine(queue,a);
		a = StringUtil.lineStart(queue,a);
		queue = queue.substring(0,a) + queue.substring(b);
		FileUtil.setFileText(queueFile,queue);
		int k = 0;
		int i = 0;
		while (((i=queue.indexOf("<doc",i)) != -1) && (i < a)) { k++; i++; }
		return k;
	}

	//Make an Input Queue XML file containing no queue elements.
	private static void makeEmptyInputQueue(File indexFile) {
		String index = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
						+ "<inputqueue>\n"
						+ "</inputqueue>\n";
		FileUtil.setFileText(indexFile, FileUtil.utf8, index);
	}

}
