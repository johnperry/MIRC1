/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

/**
 * An adapter for accessing an external database.
 * Classes extending this class can be accessed by the
 * MIRC DatabaseExportService, which passes objects received
 * from external sources to it for processing.
 */
public class DatabaseAdapter {

	/**
	 * Return status success: the operation succeeded.
	 */
	public static final int STATUS_OK = 0;

	/**
	 * Return status failure: the operation failed in
	 * such a way that trying again will also fail.
	 */
	public static final int STATUS_FAIL = -1;

	/**
	 * Return status wait: the operation failed in
	 * such a way that trying again may not fail.
	 */
	public static final int STATUS_WAIT = -2;

	/**
	 * Empty DatabaseAdapter constructor.
	 */
	public void DatabaseAdapter() { }

	/**
	 * Reset the database interface. This method is called by
	 * the DatabaseExportService if it is restarted.
	 */
	public int reset() {
		return STATUS_OK;
	}

	/**
	 * Establish a connection to the database. This method is called by
	 * the DatabaseExportService when it is about to call the database
	 * interface to process one or more objects. This call can be used
	 * by the database interface to, for example, connect to a relational
	 * database.
	 */
	public int connect() {
		return STATUS_OK;
	}

	/**
	 * Disconnect from the database. This method is called by
	 * the DatabaseExportService when it is temporarily finished
	 * processing objects. This call can be used by the database
	 * interface to, for example, disconnect from a relational
	 * database.
	 */
	public int disconnect() {
		return STATUS_OK;
	}

	/**
	 * Stop the database interface. This method is called by
	 * the DatabaseExportService when it is about to shut down.
	 * This call notifies the database interface that no further
	 * accesses will occur. The database interface should not
	 * rely on this call for anything critical since external
	 * conditions may prevent the call from occurring.
	 */
	public int shutdown() {
		return STATUS_OK;
	}

	/**
	 * Process a DICOM object. This method is called by
	 * the DatabaseExportService when it receives a DICOM file.
	 * @param dicomObject The DicomObject to be processed.
	 * @param url the URL of the DicomObject as stored in the storageservice.
	 */
	public int process(DicomObject dicomObject, String url) {
		return STATUS_OK;
	}

	/**
	 * Process an XML object. This method is called by
	 * the DatabaseExportService when it receives an XML file.
	 * @param xmlObject The XmlObject to be processed.
	 * @param url the URL of the XmlObject as stored in the storageservice.
	 */
	public int process(XmlObject xmlObject, String url) {
		return STATUS_OK;
	}

	/**
	 * Process a Zip object. This method is called by
	 * the DatabaseExportService when it receives a Zip file.
	 * @param zipObject The ZipObject to be processed.
	 * @param url the URL of the ZipObject as stored in the storageservice.
	 */
	public int process(ZipObject zipObject, String url) {
		return STATUS_OK;
	}

	/**
	 * Process a file object. This method is called by
	 * the DatabaseExportService when it receives a file that
	 * it cannot parse as any other known type.
	 * @param fileObject The FileObject to be processed.
	 * @param url the URL of the FileObject as stored in the storageservice.
	 */
	public int process(FileObject fileObject, String url) {
		return STATUS_OK;
	}
}
