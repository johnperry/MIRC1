/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import org.apache.log4j.Logger;

/**
 * An adapter for external preprocessors.
 * Classes extending this class can be accessed by the
 * MIRC ObjectProcessor, which passes objects received
 * from external sources to it for preprocessing.
 */
public class PreprocessorAdapter {

	static final Logger logger = Logger.getLogger(PreprocessorAdapter.class);

	/**
	 * Empty PreprocessorAdapter constructor.
	 */
	public void PreprocessorAdapter() { }

	/**
	 * Process a DICOM object. This method is called by
	 * the ObjectProcessor when it receives a DICOM file.
	 * @param dicomObject The DicomObject to be processed.
	 * @return true if the object is valid; false if
	 * the object is not valid and must be quarantined
	 * without further processing.
	 */
	public boolean process(DicomObject dicomObject) {
		return true;
	}

	/**
	 * Process an XML object. This method is called by
	 * the ObjectProcessor when it receives an XML file.
	 * @param xmlObject The XmlObject to be processed.
	 * @return true if the object is valid; false if
	 * the object is not valid and must be quarantined
	 * without further processing.
	 */
	public boolean process(XmlObject xmlObject) {
		return true;
	}

	/**
	 * Process a Zip object. This method is called by
	 * the ObjectProcessor when it receives a Zip file.
	 * @param zipObject The ZipObject to be processed.
	 * @return true if the object is valid; false if
	 * the object is not valid and must be quarantined
	 * without further processing.
	 */
	public boolean process(ZipObject zipObject) {
		return true;
	}

	/**
	 * Process a file object. This method is called by
	 * the ObjectProcessor when it receives a file that
	 * it cannot parse as any other known type.
	 * @param fileObject The FileObject to be processed.
	 * @return true if the object is valid; false if
	 * the object is not valid and must be quarantined
	 * without further processing.
	 */
	public boolean process(FileObject fileObject) {
		return true;
	}
}
