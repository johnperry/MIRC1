/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.fieldcenter;

import java.io.*;
import javax.imageio.ImageIO;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.DcmParser;
import org.dcm4che.data.DcmParserFactory;
import org.dcm4che.data.FileFormat;
import org.dcm4che.dict.Tags;

/**
 * A generic DICOM object, providing parsing and access to the
 * dataset as well as methods providing direct access to certain
 * common elements.
 */
public class DicomObject {

	static final DcmParserFactory pFact = DcmParserFactory.getInstance();
	static final DcmObjectFactory oFact = DcmObjectFactory.getInstance();

	/** The file which contains the DICOM object. */
	public File file = null;
	/** The dataset obtained when the DICOM object was parsed. */
	public Dataset dataset = null;

	/**
	 * Class constructor; opens and parses a DICOM file.
	 * @param file the file containing the DICOM object.
	 * @throws IOException if the file format was not recognized.
	 */
	public DicomObject(File file) throws IOException {
		this.file = file;
		BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
		DcmParser parser = pFact.newDcmParser(in);
		FileFormat fileFormat = parser.detectFileFormat();
		if (fileFormat == null) {
			throw new IOException("Unrecognized file format of file "+file);
		}
		dataset = oFact.newDataset();
		parser.setDcmHandler(dataset.getDcmHandler());
		parser.parseDcmFile(fileFormat, 0x00280000);
		in.close();
	}

	/**
	 * Get the PatientName element from the dataset.
	 * @return the PatientName element.
	 */
	public String getPatientName() {
		return getTag(Tags.PatientName);
	}

	/**
	 * Get the PatientID element from the dataset.
	 * @return the PatientID element.
	 */
	public String getPatientID() {
		return getTag(Tags.PatientID);
	}

	/**
	 * Get the Modality element from the dataset.
	 * @return the Modality element.
	 */
	public String getModality() {
		return getTag(Tags.Modality);
	}

	/**
	 * Get the SeriesNumber element from the dataset.
	 * @return the SeriesNumber element.
	 */
	public String getSeriesNumber() {
		return getTag(Tags.SeriesNumber);
	}

	/**
	 * Get the AcquisitionNumber element from the dataset.
	 * @return the AcquisitionNumber element.
	 */
	public String getAcquisitionNumber() {
		return getTag(Tags.AcquisitionNumber);
	}

	/**
	 * Get the InstanceNumber element from the dataset.
	 * @return the InstanceNumber element.
	 */
	public String getInstanceNumber() {
		return getTag(Tags.InstanceNumber);
	}

	/**
	 * Get an element from the dataset.
	 * @param tag the element number to retrieve, in the format 0xggggeeee.
	 * @return the String value of the element, or the empty String if 
	 * the element is not in the dataset.
	 */
	public String getTag(int tag) {
		String tagString = "";
		try { tagString = dataset.getString(tag); }
		catch (Exception e) { };
		return tagString;
	}

}
