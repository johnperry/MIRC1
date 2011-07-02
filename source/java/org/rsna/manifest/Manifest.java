/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.manifest;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmDecodeParam;
import org.dcm4che.data.DcmEncodeParam;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.DcmParser;
import org.dcm4che.data.DcmParserFactory;
import org.dcm4che.data.FileFormat;
import org.dcm4che.data.FileMetaInfo;
import org.dcm4che.dict.DictionaryFactory;
import org.dcm4che.dict.TagDictionary;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDDictionary;
import org.dcm4che.dict.UIDs;
import org.dcm4che.dict.VRs;
import org.rsna.mircsite.util.DicomObject;
import org.rsna.mircsite.util.StringUtil;

/**
  * Class which creates and stores an IHE TFCTE.manifest.
  */
public class Manifest {

	static final DcmParserFactory pFact = DcmParserFactory.getInstance();
	static final DcmObjectFactory oFact = DcmObjectFactory.getInstance();
	static final DictionaryFactory dFact = DictionaryFactory.getInstance();
	static final TagDictionary tagDictionary = dFact.getDefaultTagDictionary();
	static final UIDDictionary uidDictionary = dFact.getDefaultUIDDictionary();

	String authorName;
	String text;
	File[] instances;

	Dataset dataset = null;
	Charset charset = null;
	String uid;
	String studyDate;
	String studyTime;
	String contentDate;
	String contentTime;
	String ptName;
	String ptID;
	String ptBirthDate;
	String ptSex;
	String accessionNumber = "0";
	String manufacturer;
	String modality = "KO";
	String refPhysName;

	/**
	 * Class constructor; creates a new Manifest from a set of input parameters.
	 */
	public Manifest(
				String uid,
				String authorName,
				String accessionNumber,
				String manufacturer,
				String refPhysName,
				String text,
				File[] instances) throws Exception {
		this.uid = uid.replaceAll("\\s","");
		this.authorName = authorName;
		this.accessionNumber = accessionNumber;
		this.manufacturer = manufacturer;
		this.refPhysName = refPhysName;
		this.text = text;
		this.instances = instances;
		initializeParams();
		unpackFirstInstance();
		createManifest();
	}

	//Initialize the parameters that apply to this manifest.
	private void initializeParams() {
		studyDate = StringUtil.getDate().replace("-","");
		studyTime = StringUtil.getTime().replace(":","");
		contentDate = StringUtil.getDate().replace("-","");
		contentTime = StringUtil.getTime().replace(":","");
	}

	//Get the key parameters necessary for the manifest from the first
	//instance in the list. Throw an Exception if there are no selected
	//instances.
	private void unpackFirstInstance() throws Exception {
		if (instances.length == 0)
			throw new Exception(
				"There were no selected instances\n" +
				"from which to obtain the required\n" +
				"elements for the manifest.");
		DicomObject dicomObject = new DicomObject(instances[0]);
		ptName = dicomObject.getPatientName();
		ptID = dicomObject.getPatientID();
		ptBirthDate = dicomObject.getElementValue(Tags.PatientBirthDate);
		ptSex = dicomObject.getElementValue(Tags.PatientSex);
	}

	//Create a manifest from the parameters that were supplied to the constructor.
	private void createManifest() throws Exception {
		dataset = oFact.newDataset();
		dataset.putUI(Tags.SOPClassUID, UIDs.KeyObjectSelectionDocument);
		dataset.putUI(Tags.SOPInstanceUID, uid);
		dataset.putDA(Tags.StudyDate, studyDate);
		dataset.putDA(Tags.ContentDate, contentDate);
		dataset.putTM(Tags.StudyTime, studyTime);
		dataset.putTM(Tags.ContentTime, contentTime);
		dataset.putSH(Tags.AccessionNumber, accessionNumber);
		dataset.putSH(Tags.Modality, modality);
		dataset.putLO(Tags.Manufacturer, manufacturer);
		dataset.putPN(Tags.ReferringPhysicianName, refPhysName);
		dataset.putPN(Tags.PatientName, ptName);
		dataset.putPN(Tags.PatientID, ptID);
		dataset.putPN(Tags.PatientBirthDate, ptBirthDate);
		dataset.putPN(Tags.PatientSex, ptSex);
		//put in some more stuff later

		dataset.putCS(Tags.ValueType, "CONTAINER");

		DcmElement cncs = dataset.putSQ(Tags.ConceptNameCodeSeq);
		Dataset item = cncs.addNewItem();
		item.putSH(Tags.CodeValue, "TCE001");
		item.putSH(Tags.CodingSchemeDesignator, "IHERADTF");
		item.putLO(Tags.CodeMeaning, "For Teaching File Export");

		dataset.putCS(Tags.ContinuityOfContent, "SEPARATE");

		//put in the instance references
		DcmElement crpes = dataset.putSQ(Tags.CurrentRequestedProcedureEvidenceSeq);
		item = crpes.addNewItem();
		DcmElement refSeriesSeq = item.putSQ(Tags.RefSeriesSeq);
		Dataset subItem = refSeriesSeq.addNewItem();
		DcmElement refSOPSeq = subItem.putSQ(Tags.RefSOPSeq);

		for (int i=0; i<instances.length; i++) {
			DicomObject dicomObject;
			try {
				dicomObject = new DicomObject(instances[i]);
				String sopClassUID = dicomObject.getSOPClassUID();
				String sopInstanceUID = dicomObject.getSOPInstanceUID();
				Dataset ds = refSOPSeq.addNewItem();
				ds.putUI(Tags.RefSOPClassUID, sopClassUID);
				ds.putUI(Tags.RefSOPInstanceUID, sopInstanceUID);
			}
			catch (Exception ignore) { }
		}

		//put in the template identification
		DcmElement contentTemplateSeq = dataset.putSQ(Tags.ContentTemplateSeq);
		item = contentTemplateSeq.addNewItem();
		item.putCS(Tags.MappingResource, "DCMR");
		item.putCS(Tags.TemplateIdentifier, "2010");

		//put in the content
		DcmElement contentSeq = dataset.putSQ(Tags.ContentSeq);
		item = contentSeq.addNewItem();
		item.putPN(Tags.PersonName, authorName);
		item = contentSeq.addNewItem();
		item.putUT(Tags.TextValue, text);
		DcmElement conceptNameCodeSeq = item.putSQ(Tags.ConceptNameCodeSeq);
		item = conceptNameCodeSeq.addNewItem();
		item.putSH(Tags.CodeValue,"113012");
	}

	/**
	 * Save the manifest in a file whose name is the uid of the manifest
	 * and whose extension is "kos".
	 * @param dir the directory in which to store the manifest.
	 */
	public File save(File dir) throws Exception {
		return save(dir, uid+".kos");
	}

	/**
	 * Save the manifest in a file.
	 * @param file the File designating where to save the manifest.
	 * @param name the name to give to the file.
	 */
	public File save(File file, String name) throws Exception {
		file = new File(file,name);
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(file);

			// Select the encoding
			String prefEncodingUID = UIDs.ImplicitVRLittleEndian;

			// Create and write the metainfo for the encoding we are using
			FileMetaInfo fmi = oFact.newFileMetaInfo(dataset, prefEncodingUID);
			dataset.setFileMetaInfo(fmi);
			fmi.write(out);

			// write the dataset
			DcmEncodeParam encoding = DcmDecodeParam.valueOf(prefEncodingUID);
			dataset.writeDataset(out, encoding);
			out.flush();
			out.close();
			return file;
		}

		catch (Exception ex) {
			try { if (out != null) out.close(); }
			catch (Exception ignore) { }
			throw ex;
		}
	}

}
