/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.database;

import org.rsna.mircsite.log.Log;
import org.rsna.mircsite.util.DatabaseAdapter;
import org.rsna.mircsite.util.DicomObject;
import org.rsna.mircsite.util.XmlObject;
import org.rsna.mircsite.util.ZipObject;
import org.rsna.mircsite.util.FileObject;

/**
 * A dummy implementation of a Database for testing the DatabaseExportService.
 * This class does nothing but log method calls to verify dataflow.
 */
public class TestDatabase extends DatabaseAdapter {

	static final String name = "TestDatabase";

	public TestDatabase() {
		super();
	}

	/**
	 * Reset the database interface.
	 */
	public int reset() {
		Log.message(name + ": reset method call");
		return STATUS_OK;
	}

	/**
	 * Connect to the database.
	 */
	public int connect() {
		Log.message(name + ": connect method call");
		return STATUS_OK;
	}

	/**
	 * Disconnect from the database.
	 */
	public int disconnect() {
		Log.message(name + ": disconnect method call");
		return STATUS_OK;
	}

	/**
	 * Stop the database interface.
	 */
	public int shutdown() {
		Log.message(name + ": shutdown method call");
		return STATUS_OK;
	}

	/**
	 * Process a DICOM object.
	 */
	public int process(DicomObject object, String url) {
		Log.message(
			name + ": process method call<br/>" +
			"DicomObject = " + object.getFile().getName() + "<br/>" +
			"URL = " + splitURL(url));
		return STATUS_OK;
	}

	/**
	 * Process an XML object.
	 */
	public int process(XmlObject object, String url) {
		Log.message(
			name + ": process method call<br/>" +
			"XmlObject = " + object.getFile().getName() + "<br/>" +
			"URL = " + splitURL(url));
		return STATUS_OK;
	}

	/**
	 * Process a Zip object.
	 */
	public int process(ZipObject object, String url) {
		Log.message(
			name + ": process method call<br/>" +
			"ZipObject = " + object.getFile().getName() + "<br/>" +
			"URL = " + splitURL(url));
		return STATUS_OK;
	}

	/**
	 * Process a File object.
	 */
	public int process(FileObject object, String url) {
		Log.message(
			name + ": process method call<br/>" +
			"FileObject = " + object.getFile().getName() + "<br/>" +
			"URL = " + splitURL(url));
		return STATUS_OK;
	}

	//Split up a URL so long names don't make the log unreadable.
	private String splitURL(String url) {
		return url.replace("/","/ ");
	}

}
