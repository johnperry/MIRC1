/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.fieldcenter;

import java.awt.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import org.rsna.util.GeneralFileFilter;
import org.rsna.util.FileEvent;
import org.rsna.util.FileUtil;
import org.rsna.util.FileListener;
import org.rsna.mircsite.util.FileObject;
import org.rsna.mircsite.util.DicomObject;
import org.rsna.mircsite.util.XmlObject;
import org.rsna.mircsite.util.ZipObject;

/**
 * A Thread for selecting files and queuing them for processing.
 */
public class Selector extends Thread {

	Component parent;
	EventListenerList listenerList;
	GeneralFileFilter filter;
	File file;
	boolean subdirectories;
	boolean unpackZip;
	File dicomImportDir;
	File httpImportDir;
	File exportDir;
	File selectorTemp;
	boolean queueDicomObjects;
	boolean queueXmlObjects;
	boolean queueZipFiles;
	boolean unpackZipFiles;
	boolean queueZipObjects;
	boolean unpackZipObjects;
	boolean queueFileObjects;

	/**
	 * Class constructor; creating an instance of the Selector.
	 * @param filter the file filter for selecting files to send.
	 * @param file the file to select, or if it is a directory, the
	 * directory whose files are to be sent if they match the filter.
	 * @param subdirectories true if all files in the directory and
	 * its subdirectories are to be sent; false if only files in
	 * the directory itself are to be sent; ignored if file is not
	 * a directory.
	 */
	public Selector(GeneralFileFilter filter,
					File file,
					boolean subdirectories,
					boolean queueDicomObjects,
					boolean queueXmlObjects,
					boolean queueZipFiles,
					boolean unpackZipFiles,
					boolean queueZipObjects,
					boolean unpackZipObjects,
					boolean queueFileObjects) throws Exception {
		super();
		this.filter = filter;
		this.file = file;
		this.subdirectories = subdirectories;
		this.queueDicomObjects = queueDicomObjects;
		this.queueXmlObjects = queueXmlObjects;
		this.queueZipFiles = queueZipFiles;
		this.unpackZipFiles = unpackZipFiles;
		this.queueZipObjects = queueZipObjects;
		this.unpackZipObjects = unpackZipObjects;
		this.queueFileObjects = queueFileObjects;
		this.setPriority(Thread.MIN_PRIORITY);
		listenerList = new EventListenerList();
		dicomImportDir = new File(FieldCenter.dicomimportFilename);
		httpImportDir = new File(FieldCenter.httpimportFilename);
		exportDir = new File(FieldCenter.exportFilename);
		selectorTemp = new File(dicomImportDir.getParentFile(), "selector-temp");
		selectorTemp.mkdirs();
	}

	/**
	 * Start the Thread.
	 */
	public void run() {
		check(file, subdirectories);
		fireFileEvent(new FileEvent(this,FileEvent.NO_MORE_FILES));
	}

	// Queue a file if it is not a directory and it parses as an
	// acceptable type. If the file is a directory, queue the files
	// in the directory that match the filter. If subdirectories == true,
	// queue the matching contents of any subdirectories as well.
	private void check(File file, boolean subdirectories) {
		if (interrupted()) return;

		if (!file.isDirectory()) {
			//Handle normal files here
			//The strategy is:
			//  DicomObjects are queued in the dicomImportDir;
			//  XmlObjects are queued in the httpImportDir;
			//  other objects are just queued for export without processing.
			FileObject fo = FileObject.getObject(file);
			if (queueDicomObjects && (fo instanceof DicomObject)) {
				queue(file, dicomImportDir, ".dcm");
			}
			else if (queueXmlObjects && (fo instanceof XmlObject)) {
				queue(file, httpImportDir, ".xml");
			}
			else if ((queueZipFiles || queueZipObjects) && (fo instanceof ZipObject)) {
				//Determine how to handle a zip file.
				//If the file contains a manifest, unpackZipObjects controls;
				//otherwise, unpackZipFiles controls.
				ZipObject zo = (ZipObject)fo;
				boolean manifest = zo.hasManifest();
				if ((queueZipFiles && !manifest && unpackZipFiles) ||
					(queueZipObjects && manifest && unpackZipObjects)) {
					//Okay, we are required to unpack the zip file.
					//Create a temporary directory into which to unpack it.
					File dir = null;
					try {
						dir = File.createTempFile("ZIPTEMP","",selectorTemp);
						dir.delete();
						dir.mkdirs();
					}
					catch (Exception unable) {
						System.out.println("Unable to create temp directory to unpack zip file.");
						return;
					}
					zo.extractAll(dir);
					//Now queue all the files in the temp directory,
					//including subdirectories, whether subdirectories
					//were specified in the top-level call or not.
					check(dir,true);
					//Finally, delete the temp directory.
					FileUtil.deleteAll(dir);
				}
				else if ((queueZipFiles && !manifest) ||
						 (queueZipObjects && manifest)) queue(file, exportDir, ".zip");
			}
			else if (queueFileObjects) {
				queue(file, exportDir, ".unk");
			}
			fo = null;
		}
		else {
			//Handle directories here
			File[] files = file.listFiles(filter);
			for (int i=0; i<files.length && !interrupted(); i++) {
				if (!files[i].isDirectory() || subdirectories) check(files[i], subdirectories);
			}
		}
	}

	private void queue(File file, File destDir, String ext) {

		try {
			//Copy a file to the selector-temp directory.
			//This will ensure that the rename will work if
			//the source medium is another file system.
			File copy = File.createTempFile("SEL-",ext,selectorTemp);
			FileUtil.copyFile(file,copy);
			//Now rename it into the destination directory.
			//This ensures that the movement is atomic, preventing
			//the ObjectProcessor or ExportService from jumping on
			//the file before it is all there.
			File dest = new File(destDir, copy.getName());
			copy.renameTo(dest);
			//Notify the listeners
			fireFileEvent(new FileEvent(this,FileEvent.MOVE,file,copy));
			yield();
		}
		catch (Exception ex) {
			System.out.println("Unable to queue a file in the Selector.");
		}
	}

	// The rest of this code is for handling event listeners and for sending events.
	/**
	* Register a FileListener.
	* @param listener The listener to register.
	*/
	public void addFileListener(FileListener listener) {
		listenerList.add(FileListener.class, listener);
	}

	/**
	* Remove a FileListener.
	* @param listener The listener to remove.
	*/
	public void removeSenderListener(FileListener listener) {
		listenerList.remove(FileListener.class, listener);
	}

	/**
	* Fire a FileEvent. The fileEventOccurred method calls are made
	* in the event dispatch thread, making it safe for GUI updates.
	*/
	private void fireFileEvent(FileEvent fe) {
		final FileEvent event = fe;
		final EventListener[] listeners = listenerList.getListeners(FileListener.class);
		Runnable fireEvent = new Runnable() {
			public void run() {
				for (int i=0; i<listeners.length; i++) {
					((FileListener)listeners[i]).fileEventOccurred(event);
				}
			}
		};
		SwingUtilities.invokeLater(fireEvent);
	}

}
