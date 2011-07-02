/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.dicomservice;

import java.io.*;
import org.rsna.mircsite.util.FileObject;
import org.rsna.mircsite.util.DicomObject;
import org.rsna.mircsite.util.XmlObject;
import org.rsna.mircsite.util.ZipObject;
import org.rsna.mircsite.util.*;

/**
  * A class to encapsulate an export queue element File for clinical trials.
  * A queue element is a File pointing to a disk file (the queue element file)
  * containing the absolute path string of the actual file to be exported.
  */
public class ExportQueueElement extends File {

	/**
	 * Class constructor; creates a new ExportQueueElement object
	 * from a File pointing to the queue element file.
	 * It does not write anything to the queue element file.
	 * @param file File pointing to the queue element file.
	 */
	public ExportQueueElement(File file) {
		super(file.getAbsolutePath());
	}

	/**
	 * Class constructor; creates a new ExportQueueElement object from
	 * a path string pointing to the queue element file.
	 * It does not write anything to the queue element file.
	 * @param absolutePath the path to the queue element file.
	 */
	public ExportQueueElement(String absolutePath) {
		super(absolutePath);
	}

	/**
	 * Class constructor; creates a new ExportQueueElement object from a File
	 * pointing to the queue element file and a File pointing to the file to
	 * be exported. It creates the queue element file and writes into it the
	 * path string pointing to the file to be exported.
	 * @param queueElementFile pointing to the queue element file.
	 * @param queuedFile pointing to the file to be exported.
	 */
	public ExportQueueElement(File queueElementFile, File queuedFile) {
		this(queueElementFile);
		FileUtil.setFileText(this,queuedFile.getAbsolutePath());
	}

	/**
	 * Class constructor; creates a new ExportQueueElement object from a
	 * DicomObject. It creates the queue element file in the directory
	 * with the DicomObject and points it at the DicomObject. The queue
	 * element file contains the path string pointing to the DicomObject
	 * data file to be exported.
	 * @param dicomObject the object whose data file is to be exported.
	 */
	public ExportQueueElement(DicomObject dicomObject) {
		this(
			new File(
				dicomObject.getFile().getParentFile(),
				dicomObject.getSOPInstanceUID()+".qe"
			)
		);
		FileUtil.setFileText(this,dicomObject.getFile().getAbsolutePath());
	}

	/**
	 * Make an ExportQueueElement object from a File.
	 * It creates the queue element file in the directory
	 * with the File and points it at the File.
	 * The queue element is named to be unique in the directory.
	 * The queue element file contains the path string pointing
	 * to the data file to be exported.
	 * @param file the file is to be exported.
	 */
	public static ExportQueueElement createEQE(File file) {
		return createEQE(new FileObject(file));
	}

	/**
	 * Make an ExportQueueElement object from a FileObject.
	 * It creates the queue element file in the directory
	 * with the FileObject and points it at the FileObject.
	 * The queue element is named to be unique in the directory.
	 * The queue element file contains the path string pointing
	 * to the data file to be exported.
	 * @param fileObject the object whose data file is to be exported.
	 */
	public static ExportQueueElement createEQE(FileObject fileObject) {

		File dir = fileObject.getFile().getParentFile();

		//make a name that is unique in the directory.
		//set a prefix that indicates what the file is.
		String prefix = fileObject.getTypePrefix();
		try {
			ExportQueueElement eqe =
				new ExportQueueElement(dir.createTempFile(prefix,".qe",dir));
			FileUtil.setFileText(eqe,fileObject.getFile().getAbsolutePath());
			return eqe;
		}
		catch (Exception ex) { return null; }
	}

	/**
	 * Rename the queue element file into an export queue directory,
	 * creating the directory if necessary.
	 * @param directory the export queue directory.
	 * @throws Exception if the operation failed.
	 */
	public void queue(File directory) throws Exception {
		if (!directory.exists()) directory.mkdirs();
		File destination = new File(directory,this.getName());
		if (destination.exists()) {
			destination = File.createTempFile("ALT-","-"+this.getName(),directory);
		}
		if (!this.renameTo(destination)) throw new Exception("Queue operation failed");
	}

	/**
	 * Get a File pointing to the actual file to be exported.
	 * @return File pointing to the file to be exported.
	 */
	public File getQueuedFile() {
		File file = null;
		String path = FileUtil.getFileText(this).trim();
		if (!path.equals("")) {
			try { file = new File(path); }
			catch (Exception e) { file = null; }
		}
		return file;
	}

}
