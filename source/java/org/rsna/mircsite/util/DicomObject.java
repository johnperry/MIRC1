/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.awt.Image;
import java.awt.image.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.*;
import javax.imageio.ImageIO;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObject;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.DcmParser;
import org.dcm4che.data.DcmParserFactory;
import org.dcm4che.data.FileFormat;
import org.dcm4che.data.FileMetaInfo;
import org.dcm4che.data.SpecificCharacterSet;
import org.dcm4che.dict.DictionaryFactory;
import org.dcm4che.dict.TagDictionary;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDDictionary;
import org.dcm4che.dict.VRs;

/**
  * Class which encapsulates a DICOM object and provides access to its elements.
  */
public class DicomObject extends FileObject {

	static final DcmParserFactory pFact = DcmParserFactory.getInstance();
	static final DcmObjectFactory oFact = DcmObjectFactory.getInstance();
	static final DictionaryFactory dFact = DictionaryFactory.getInstance();
	static final TagDictionary tagDictionary = dFact.getDefaultTagDictionary();
	static final UIDDictionary uidDictionary = dFact.getDefaultUIDDictionary();

	Dataset dataset = null;
	BufferedImage bufferedImage = null;
	int currentFrame = -1;
	boolean isImage = false;
	boolean isManifest = false;
	boolean isAdditionalTFInfo = false;
	SpecificCharacterSet charset = null;

	/**
	 * Class constructor; parses a file to create a new DicomObject.
	 * @param file the file containing the DicomObject.
	 * @throws IOException if the file cannot be read or the file does not parse.
	 */
	public DicomObject(File file) throws Exception {
		super(file);
		BufferedInputStream in = null;
		try {
			in = new BufferedInputStream(new FileInputStream(file));
			DcmParser parser = pFact.newDcmParser(in);
			FileFormat fileFormat = parser.detectFileFormat();
			if (fileFormat == null) {
				throw new IOException("Unrecognized file format: "+file);
			}
			dataset = oFact.newDataset();
			parser.setDcmHandler(dataset.getDcmHandler());
			//Parse the file, but don't get the pixels in order to save heap space
			parser.parseDcmFile(fileFormat, Tags.PixelData);
			//See if this is a real image.
			isImage =  (parser.getReadTag() == Tags.PixelData);
			//See if this is a TCE Manifest
			isManifest = checkManifest();
			//See if this is a TCE Additional Teaching File Info document
			isAdditionalTFInfo = checkAdditionalTFInfo();
			//Get the charset in case we need it for manifest processing.
			charset = dataset.getSpecificCharacterSet();
			in.close();
		}
		catch (Exception exception) {
			if (in != null) in.close();
			throw exception;
		}
	}

	/**
	 * Set the standard extension for a DicomObject (".dcm").
	 * @return the file after modification.
	 */
	public File setStandardExtension() {
		return setExtension(".dcm");
	}

	/**
	 * Get a prefix for a DicomObject ("DCM-").
	 * @return a prefix for a DicomObject.
	 */
	public String getTypePrefix() {
		return "DCM-";
	}

	/**
	 * Determine whether this file has a typical DICOM filename.
	 * Typical DICOM filenames either end in ".dcm" or are a UID.
	 * The test for the extension is case insensitive.
	 * @return true if the file has a typical DICOM filename; false otherwise.
	 */
	public boolean hasTypicalDicomFilename() {
		return hasTypicalDicomFilename(file.getName());
	}

	/**
	 * Determine whether a filename is a typical DICOM filename.
	 * Typical DICOM filenames either end in ".dcm" or are a UID.
	 * The test for the extension is case insensitive.
	 * @return true if the filename is a typical DICOM filename; false otherwise.
	 */
	public static boolean hasTypicalDicomFilename(String name) {
		int k = name.lastIndexOf(".");
		if ((k != -1) && name.substring(k).toLowerCase().equals(".dcm")) return true;
		if (name.matches("[\\d\\.]+")) return true;
		return false;
	}

	/**
	 * Get a MircImage from this DicomObject, only loading the image if it has not
	 * already been loaded. This method ultimately calls getBufferedImage. Note
	 * the processing of overlays provided by that method in order to protect the
	 * JPEG converter.
	 * @return the MircImage.
	 * @throws Exception if the DicomObject does not contain an image.
	 * @throws Exception if the image could not be loaded.
	 */
	public MircImage getMircImage() throws Exception {
		if (!isImage) throw new Exception("Not an image: "+file);
		return new MircImage(this);
	}

	/**
	 * Get a BufferedImage from this DicomObject, only loading the image if it
	 * has not yet been loaded. This method calls getBufferedImage(0, false).
	 * @return the BufferedImage.
	 * @throws Exception if the image could not be loaded.
	 */
	public BufferedImage getBufferedImage() throws Exception {
		return getBufferedImage(0, false);
	}

	/**
	 * Get a BufferedImage from the DicomObject. This method filters all the pixels for images
	 * with BitsStored < 16, forcing any pixels with overlay bits to the maximum allowed pixel
	 * value (2^BitsStored - 1). This is done to protect the JPEG converter, which throws an
	 * array out of bounds exception on such pixels.
	 * @param frameNumber the frame from which to obtain the BufferedImage.
	 * @param forceReload true if the image is to be reloaded even if it is already loaded;
	 * false if the image is only to be loaded if necessary.
	 * @return the BufferedImage after burning in the overlays.
	 * @throws IOException if the image could not be loaded.
	 */
	public BufferedImage getBufferedImage(int frameNumber, boolean forceReload) throws Exception {
		if (!isImage || (frameNumber < 0) || (frameNumber > getNumberOfFrames())) throw new IOException("Not an image: "+file);
		if (!forceReload && (bufferedImage != null) && (currentFrame == frameNumber)) return bufferedImage;
		bufferedImage = ImageIO.read(file);
		if (bufferedImage == null) throw new IOException("Could not read "+file);
		currentFrame = frameNumber;
		// Burn in the overlays to keep the JPEG converter
		// from throwing an array out of bounds exception
		int bitsStored = getBitsStored();
		if (bitsStored < 16) {
			WritableRaster wr = bufferedImage.getRaster();
			DataBuffer b = wr.getDataBuffer();
			if (b.getDataType() == DataBuffer.TYPE_USHORT) {
				int maxPixelInt = (1 << bitsStored) - 1;
				short maxPixel = (short)(0xffff & maxPixelInt);
				DataBufferUShort bs = (DataBufferUShort)b;
				short[] data = bs.getData();
				for (int i=0; i<data.length; i++) {
					if (data[i] > maxPixel) data[i] = maxPixel;
				}
			}
		}
		return bufferedImage;
	}

	/**
	 * Get the transfer syntax name.
	 * @return the name of the transfer syntax.
	 */
	public String getTransferSyntaxName() {
		String transferSyntaxUID = null;
		try {
			FileMetaInfo fmi = dataset.getFileMetaInfo();
			transferSyntaxUID = fmi.getTransferSyntaxUID();
			return uidDictionary.lookup(transferSyntaxUID).name;
		}
		catch (Exception e) { }
		return "Unknown transfer syntax: " + transferSyntaxUID;
	}

	/**
	 * Get the SOP Class name.
	 * @return the name of the SOP Class.
	 */
	public String getSOPClassName() {
		String sopClassUID = null;
		try {
			sopClassUID = getSOPClassUID();
			return uidDictionary.lookup(sopClassUID).name;
		}
		catch (Exception e) { }
		return "Unknown SOP Class: " + sopClassUID;
	}


	/**
	 * Get the dcm4che name of a DICOM element.
	 * @param tag
	 * @return the dcm4che tag name.
	 */
	public static String getElementName(int tag) {
		TagDictionary.Entry entry = tagDictionary.lookup(tag);
		if (entry == null) return null;
		return entry.name;
	}

	/**
	 * Get the ByteBuffer of a DICOM element in the DicomObject's dataset.
	 * @param tag the group and element number of the element.
	 * @return the value of the element.
	 */
	public ByteBuffer getElementByteBuffer(int tag) {
		return dataset.getByteBuffer(tag);
	}

	/**
	 * Get the contents of a DICOM element in the DicomObject's dataset.
	 * This method returns an empty String if the element does not exist.
	 * @param tagName the dcm4che name of the element.
	 * @return the text of the element, or the empty String if the
	 * element does not exist.
	 */
	public String getElementValue(String tagName) {
		return getElementValue(Tags.forName(tagName),"");
	}

	/**
	 * Get the contents of a DICOM element in the DicomObject's dataset.
	 * The value of the tagName argument can be a dcm4che element name
	 * (e.g., SOPInstanceUID), or the tag itself, coded either as (0008,0018)
	 * or [0008,0018]. This method returns the defaultString argument if
	 * the element does not exist.
	 * @param tagName the dcm4che name of the element.
	 * @param defaultString the String to return if the element does not exist.
	 * @return the text of the element, or defaultString if the element does not exist.
	 */
	public String getElementValue(String tagName, String defaultString) {
		int tag;
		if (tagName.startsWith("[") && tagName.endsWith("]"))
			tagName = tagName.replace("[","(").replace("]",")");
		if (tagName.startsWith("(") && tagName.endsWith(")"))
			tag = Tags.valueOf(tagName);
		else
			tag = Tags.forName(tagName);
		return getElementValue(tag,defaultString);
	}

	/**
	 * Get the contents of a DICOM element in the DicomObject's dataset.
	 * This method returns an empty String if the element does not exist.
	 * @param tag the tag specifying the element (in the form 0xggggeeee).
	 * @return the text of the element, or the empty String if the
	 * element does not exist.
	 */
	public String getElementValue(int tag) {
		return getElementValue(tag,"");
	}

	/**
	 * Get the contents of a DICOM element in the DicomObject's dataset.
	 * This method returns the defaultString argument if the element does not exist.
	 * @param tag the tag specifying the element (in the form 0xggggeeee).
	 * @param defaultString the String to return if the element does not exist.
	 * @return the text of the element, or defaultString if the element does not exist.
	 */
	public String getElementValue(int tag, String defaultString) {
		String value = null;
		try { value = dataset.getString(tag); }
		catch (Exception e) { }
		if (value == null) value = defaultString;
		return value;
	}

	/**
	 * Convenience method to get the contents of the PatientName element.
	 * @return the text of the element or the empty String if the
	 * element does not exist.
	 */
	public String getPatientName() {
		return getElementValue(Tags.PatientName);
	}

	/**
	 * Convenience method to get the contents of the PatientID element.
	 * @return the text of the element or the empty String if the
	 * element does not exist.
	 */
	public String getPatientID() {
		return getElementValue(Tags.PatientID);
	}

	/**
	 * Convenience method to get the contents of the Modality element.
	 * @return the text of the element or the empty String if the
	 * element does not exist.
	 */
	public String getModality() {
		return getElementValue(Tags.Modality);
	}

	/**
	 * Convenience method to get the contents of the SeriesNumber element.
	 * @return the text of the element or the empty String if the
	 * element does not exist.
	 */
	public String getSeriesNumber() {
		return getElementValue(Tags.SeriesNumber);
	}

	/**
	 * Convenience method to get the contents of the AcquisitionNumber element.
	 * @return the text of the element or the empty String if the
	 * element does not exist.
	 */
	public String getAcquisitionNumber() {
		return getElementValue(Tags.AcquisitionNumber);
	}

	/**
	 * Convenience method to get the contents of the InstanceNumber element.
	 * @return the text of the element or the empty String if the
	 * element does not exist.
	 */
	public String getInstanceNumber() {
		return getElementValue(Tags.InstanceNumber);
	}

	/**
	 * Convenience method to get the contents of the SOPClassUID element.
	 * @return the text of the element or null if the element does not exist.
	 */
	public String getSOPClassUID() {
		return getElementValue(Tags.SOPClassUID,null);
	}

	/**
	 * Convenience method to get the contents of the SOPInstanceUID element.
	 * @return the text of the element or null if the element does not exist.
	 */
	public String getSOPInstanceUID() {
		return getElementValue(Tags.SOPInstanceUID,null);
	}

	/**
	 * Convenience method to get the contents of the SOPInstanceUID element.
	 * Included for compatibility with other FileObjects.
	 * @return the text of the element or null if the element does not exist.
	 */
	public String getUID() {
		return getElementValue(Tags.SOPInstanceUID,null);
	}

	/**
	 * Convenience method to get the contents of the StudyInstanceUID element.
	 * @return the text of the element or null if the element does not exist.
	 */
	public String getStudyInstanceUID() {
		return getElementValue(Tags.StudyInstanceUID,null);
	}

	/**
	 * Convenience method to get the contents of the StudyInstanceUID element.
	 * Included for compatibility with other FileObjects.
	 * @return the text of the element or null if the element does not exist.
	 */
	public String getStudyUID() {
		return getElementValue(Tags.StudyInstanceUID,null);
	}

	/**
	 * Convenience method to get the contents of the SeriesInstanceUID element.
	 * @return the text of the element or null if the element does not exist.
	 */
	public String getSeriesInstanceUID() {
		return getElementValue(Tags.SeriesInstanceUID,null);
	}

	/**
	 * Convenience method to get the contents of the SeriesDescription element.
	 * @return the text of the element or null if the element does not exist.
	 */
	public String getSeriesDescription() {
		return getElementValue(Tags.SeriesDescription,null);
	}

	/**
	 * Convenience method to get the integer value of the Columns element.
	 * @return the integer value of the Columns element or -1 if the element does not exist.
	 */
	public int getColumns() {
		int columns = -1;
		try {columns = dataset.getInteger(Tags.Columns).intValue();}
		catch (Exception e) { };
		return columns;
	}

	/**
	 * Convenience method to get the integer value of the Rows element.
	 * @return the integer value of the Rows element or -1 if the element does not exist.
	 */
	public int getRows() {
		int rows = -1;
		try {rows = dataset.getInteger(Tags.Rows).intValue();}
		catch (Exception e) { };
		return rows;
	}

	/**
	 * Convenience method to get the integer value of the BitsStored element.
	 * @return the integer value of the Columns element or 12 if the element does not exist.
	 */
	public int getBitsStored() {
		int bitsStored = 12;
		try {bitsStored = dataset.getInteger(Tags.BitsStored).intValue();}
		catch (Exception e) { };
		return bitsStored;
	}

	/**
	 * Convenience method to get the integer value of the NumberOfFrames element.
	 * @return the integer value of the NumberOfFrames element or 0 if the
	 * value is not available.
	 */
	public int getNumberOfFrames() {
		int nof = 0;
		try {nof = dataset.getInteger(Tags.NumberOfFrames).intValue();}
		catch (Exception e) { };
		return nof;
	}

	/**
	 * Tests whether the DicomObject actually contained a image.
	 * The test is done by verifying that the PixelData element is present.
	 * @return true if the object contains an image; false otherwise.
	 */
	public boolean isImage() {
		return isImage;
	}

	/**
	 * Tests whether the DicomObject corresponds to a raw data object.
	 * The test is done by comparing the SOPClassUID to the value of
	 * the UIDs entry for SiemensCSANonImageStorage (1.3.12.2.1107.5.9.1).
	 * @return true if the object is a raw data set; false otherwise.
	 */
	public boolean isRawData() {
		return SopClass.isRawData(getSOPClassUID());
	}

	/**
	 * Tests whether the DicomObject corresponds to a supported image.
	 * The test is done by comparing the SOPClassUID to known image SOPClassUIDs.
	 * @return true if the object corresponds to a supported image; false otherwise.
	 */
	public boolean isSupportedImage() {
		return SopClass.isImage(getSOPClassUID());
	}

	/**
	 * Tests whether the DicomObject corresponds to an SR.
	 * The test is done by comparing the SOPClassUID to known SR SOPClassUIDs.
	 * @return true if the object corresponds to an SR; false otherwise.
	 */
	public boolean isSR() {
		return SopClass.isSR(getSOPClassUID());
	}

	/**
	 * Tests whether the DicomObject corresponds to a KIN.
	 * The test is done by comparing the SOPClassUID to the KIN SOPClassUID.
	 * @return true if the object corresponds to a KIN; false otherwise.
	 */
	public boolean isKIN() {
		return SopClass.isKIN(getSOPClassUID());
	}

	/**
	 * Tests whether the DicomObject corresponds to a KIN that is an IHE TFCTE manifest.
	 * @return true if the object corresponds to a TCE Manifest; false otherwise.
	 */
	public boolean isManifest() {
		return isManifest;
	}

	//Check whether this object is a TCE Manifest
	private boolean checkManifest() {
		if (!isKIN()) return false;
		try {
			DcmElement cncsElement = dataset.get(Tags.ConceptNameCodeSeq);
			Dataset sq = cncsElement.getItem(0);
			String codeValue = sq.getString(Tags.CodeValue).trim();
			if (codeValue.equals("TCE001")) return true;
			if (codeValue.equals("TCE002")) return true;
			if (codeValue.equals("TCE007")) return true;
		}
		catch (Exception e) { };
		return false;
	}

	/**
	 * Tests whether the DicomObject is an IHE TCE ATFI SR document.
	 * @return true if the object corresponds to a TCE; false otherwise.
	 */
	public boolean isAdditionalTFInfo() {
		return isAdditionalTFInfo;
	}

	//Check whether this object is a TCE ATFI Object
	private boolean checkAdditionalTFInfo() {
		if (!isSR()) return false;
		try {
			DcmElement cncsElement = dataset.get(Tags.ConceptNameCodeSeq);
			Dataset sq = cncsElement.getItem(0);
			String codeValue = sq.getString(Tags.CodeValue).trim();
			if (codeValue.equals("TCE006")) return true;
		}
		catch (Exception e) { };
		return false;
	}

	/**
	 * Get the IHE TFCTE additional teaching file info in an
	 * extension of a Hashtable object.
	 * @return the additional teaching file info Hashtable, or null if
	 * this object is not an IHE Additional Teaching File Info object.
	 */
	public Hashtable getAdditionalTFInfo() {
		if (!isAdditionalTFInfo()) return null;
		return new ATFI(dataset,charset);
	}

	/**
	 * Gets a brief description of this DicomObject.
	 * @return "TCE Manifest" or SOP Class Name.
	 */
	public String getDescription() {
		if (isManifest()) return "TCE Manifest";
		return getSOPClassName();
	}

	/**
	 * Get an array of SOPInstanceUIDs for all the instances listed in the
	 * Current Requested Procedure Evidence Sequence element in a manifest.
	 * @return the array if this object is a manifest; null otherwise.
	 */
	public String[] getInstanceList() {
		return getList(Tags.CurrentRequestedProcedureEvidenceSeq, Tags.RefSOPInstanceUID);
	}

	/**
	 * Get an array of PersonNames from the Content Sequence element in a manifest
	 * @return the array if this object is a manifest; null otherwise.
	 */
	public String[] getObserverList() {
		return getList(Tags.ContentSeq, Tags.PersonName);
	}

	/**
	 * Get the text of the Key Object Description from the Content Sequence of
	 * an IHE TFCTE Manifest.
	 * @return the Key Object Description text if this object is a manifest; null otherwise.
	 */
	public String getKeyObjectDescription() {
		if (!isManifest()) return null;
		DcmElement cs = dataset.get(Tags.ContentSeq);
		Dataset csItem;
		int i = 0;
		while ((csItem = cs.getItem(i)) != null) {
			i++;
			DcmElement cncs = csItem.get(Tags.ConceptNameCodeSeq);
			if (cncs != null) {
				Dataset cncsItem = cncs.getItem(0);
				if (cncsItem != null) {
					DcmElement cv = cncsItem.get(Tags.CodeValue);
					try {
						if ((cv != null) && cv.getString(charset).equals("113012")) {
							DcmElement tv = csItem.get(Tags.TextValue);
							if (tv != null) return tv.getString(charset);
						}
					}
					catch (Exception ignore) { }
				}
			}
		}
		return null;
	}

	/**
	 * Get a Hashtable containing entries obtained by parsing the supplied
	 * text. The hashtable contains entries for names designated in the text
	 * by "mirc:name=" at the beginning of a line.
	 * All the text following the equal sign and before the next name designation
	 * is assigned to the key. Any name can appear only once (or the last value
	 * is stored in the table).
	 * @param kodText the text to parse.
	 * @return the parsed text, or null if the supplied string is null.
	 */
	public Hashtable getParsedText(String kodText) {
		if (kodText == null) return null;
		Hashtable<String,String> kodTable = new Hashtable<String,String>();
		Pattern pattern = Pattern.compile("mirc:[^\\s]+=");
		Matcher matcher = pattern.matcher(kodText);
		String name = "";
		int lastEnd = 0;
		while (matcher.find()) {
			if (!name.equals("")) kodTable.put(name,kodText.substring(lastEnd,matcher.start()));
			lastEnd = matcher.end();
			name = matcher.group();
			name = name.substring(5,name.length()-1);
		}
		if (!name.equals("")) kodTable.put(name,kodText.substring(lastEnd));
		return kodTable;
	}

	//Get a list of element values by walking the tree below a starting
	//element in the dataset, and finding all instances of a specific tag.
	private String[] getList(int startingTag, int tagToFind) {
		if (!isManifest()) return null;
		try {
			ArrayList<String> list = new ArrayList();
			DcmElement el = dataset.get(startingTag);
			getList(list, el, tagToFind);
			String[] strings = new String[list.size()];
			strings = list.toArray(strings);
			return strings;
		}
		catch (Exception ex) { return null; }
	}

	//Walk a tree of elements to find any that match a tag
	//and add their values to a list.
	private void getList(ArrayList<String> list, DcmElement el, int tag) {
		//If this is the element we want, then get its value
		try {
			if (el.vr() != VRs.SQ) {
				//It's not a sequence; see if it's a tag match.
				if (el.tag() == tag) {
					list.add(el.getString(charset));
				}
				return;
			}
			else {
				//It's a sequence; walk the item tree looking for matches.
				int i = 0;
				Dataset ds;
				while ((ds=el.getItem(i++)) != null) {
					for (Iterator it=ds.iterator(); it.hasNext(); ) {
						DcmElement e = (DcmElement)it.next();
						getList(list,e,tag);
					}
				}
			}
		}
		catch (Exception ex) { return; }
	}

	/**
	 * Get a String containing an HTML table element listing all the
	 * elements in the DICOM object and their values.
	 * @return the HTML text listing all the elements in the DICOM object.
	 * @throws Exception if the process fails.
	 */
	public String getElementTable() {
		SpecificCharacterSet cs = dataset.getSpecificCharacterSet();
		StringBuffer table = new StringBuffer();
		table.append("<h3>"+file.getName());
		table.append("<br>"+getTransferSyntaxName());
		table.append("<br>"+getSOPClassName());
		table.append("</h3>\n");
		table.append("<table>\n");
		table.append("<b><tr><th>Element</th><td><b>Name</b></td><th>VR</th>" +
						"<th>VM</th><th>Length</th><td><b>Data</b></td></tr></b>\n");
		walkDataset(dataset.getFileMetaInfo(),cs,table,"");
		walkDataset(dataset,cs,table,"");
		table.append("</table>\n");
		return table.toString();
	}

	private void walkDataset(DcmObject dataset,
							 SpecificCharacterSet cs,
							 StringBuffer table,
							 String prefix) {
		int maxLength = 80;
		DcmElement el;
		String tagString;
		String tagName;
		String vrString;
		String valueString;
		String valueLength;
		int vr;
		int vm;
		if (dataset == null) return;
		for (Iterator it=dataset.iterator(); it.hasNext(); ) {
			table.append("<tr>");
			el = (DcmElement)it.next();
			int tag = el.tag();
			tagString = checkForNull(Tags.toString(tag));

			try { tagName = checkForNull(tagDictionary.lookup(tag).name); }
			catch (Exception e) { tagName = ""; }

			vr = el.vr();
			vrString = VRs.toString(vr);
			if (vrString.equals("")) vrString = "["+Integer.toHexString(vr)+"]";

			vm = el.vm(cs);

			table.append("<td>"+prefix+tagString+"</td>");
			table.append("<td><font color=\"blue\">"+tagName+"</font></td>");
			table.append("<td align=center>"+vrString+"</td>");
			table.append("<td align=center>"+vm+"</td>");
			table.append("<td align=center>"+el.length()+"</td>");

			if (!vrString.toLowerCase().startsWith("sq")) {
				valueString = getElementValueString(cs,el);
				if (valueString == null)
					table.append("<td>"+nullValue+"</td>");
				else if (valueString.length() < maxLength)
					table.append(
						"<td>\""
						+ valueString.replaceAll("\\s","&nbsp;")
						+ "\"</td>");
				else
					table.append(
						"<td>\""
						+ valueString.substring(0,maxLength).replaceAll("\\s","&nbsp;")
						+ "...\"</td>");
				table.append("</tr>\n");
			}
			else {
				table.append("</tr>\n");
				int i = 0;
				Dataset sq;
				while ((sq=el.getItem(i++)) != null) {
					walkDataset(sq,cs,table,prefix+i+">");
				}
			}
		}
	}

	//Make a displayable text value for an element, handling
	//cases where the element is multivalued and where the element value
	//is too long to be reasonably displayed.
	private String getElementValueString(SpecificCharacterSet cs, DcmElement el) {
		int tag = el.tag();
		if ((tag & 0xffff0000) >= 0x60000000) return "...";
		String valueString;
		String[] s;
		try { s = el.getStrings(cs); }
		catch (Exception e) { s = null; }
		if (s == null) valueString = null;
		else {
			valueString = "";
			for (int i=0; i<s.length; i++) {
				valueString += s[i];
				if (i != s.length-1) valueString += "\\";
			}
		}
		return valueString;
	}

	//Handle null element values (e.g. missing elements).
	private String checkForNull(String s) {
		if (s != null) return s;
		return nullValue;
	}

	//An HTML string indicating a null value (in red).
	String nullValue = "<font color=red>null</font>";

	//A class to encapsulate the information in a
	//TCE Additional Teaching File Info object.
	class ATFI extends Hashtable<String,String> {
		public ATFI(Dataset dataset, SpecificCharacterSet charset) {
			super();
			Hashtable<String,String> codes = getCodes();
			try {
				DcmElement csElement = dataset.get(Tags.ContentSeq);
				int i = 0;
				Dataset ds;
				while ((ds = csElement.getItem(i++)) != null) {
					putItem(ds,charset,codes);
				}
			}
			catch (Exception e) { }
		}
		private void putItem(Dataset ds,
							 SpecificCharacterSet charset,
							 Hashtable<String,String> codes) {
			try {
				//Make sure this is a container
				DcmElement rt = ds.get(Tags.RelationshipType);
				DcmElement vt = ds.get(Tags.ValueType);
				if ((rt == null) || (vt == null) ||
					(!rt.getString(charset).equals("CONTAINS"))) return;
				//Get the code of the container type
				DcmElement cncsElement = ds.get(Tags.ConceptNameCodeSeq);
				Dataset item = cncsElement.getItem(0);
				DcmElement cvElement = item.get(Tags.CodeValue);
				String cvString = cvElement.getString(charset);
				//Get the value
				String valueString = null;
				if (vt.getString(charset).equals("TEXT")) {
					//Handle text values here
					DcmElement tvElement = ds.get(Tags.TextValue);
					valueString = tvElement.getString(charset);
				}
				else if (vt.getString(charset).equals("CODE")) {
					//Handle code values here
					DcmElement ccsElement = ds.get(Tags.ConceptCodeSeq);
					Dataset ccsItem = ccsElement.getItem(0);
					DcmElement ccscvElement = ccsItem.get(Tags.CodeValue);
					String codeString = ccscvElement.getString(charset);
					valueString = codes.get(codeString);
					//If the code is not in the table, then use the
					//code itself. This is necessary to handle all the
					//modalities without having to put them in the table.
					if (valueString == null) valueString = codeString;
				}
				if (valueString != null) {
					String name = codes.get(cvString);
					if (name != null) {
						String current = this.get(name);
						if (current != null) valueString = current + "; " + valueString;
						this.put(name,valueString);
					}
				}
			}
			catch (Exception e) { };
		}
		private Hashtable<String,String> getCodes() {
			Hashtable<String,String> codes = new Hashtable<String,String>();
			codes.put("TCE101","author/name");
			codes.put("TCE102","author/affiliation");
			codes.put("TCE103","author/contact");
			codes.put("TCE104","abstract");
			codes.put("TCE105","keywords");
			codes.put("121060","history");
			codes.put("121071","findings");
			codes.put("TCE106","discussion");
			codes.put("111023","differential-diagnosis");
			codes.put("TCE107","diagnosis");
			codes.put("112005","anatomy");
			codes.put("111042","pathology");
			codes.put("TCE108","organ-system");
			codes.put("121139","modality");
			codes.put("TCE109","category");
			codes.put("TCE110","level");

			codes.put("TCE201","Primary");
			codes.put("TCE202","Intermediate");
			codes.put("TCE203","Advanced");

			codes.put("TCE301","Musculoskeletal;");
			codes.put("TCE302","Pulmonary");
			codes.put("TCE303","Cardiovascular");
			codes.put("TCE304","Gastrointestinal");
			codes.put("TCE305","Genitourinary");
			codes.put("TCE306","Neuro");
			codes.put("TCE307","Vascular and Interventional");
			codes.put("TCE308","Nuclear");
			codes.put("TCE309","Ultrasound");
			codes.put("TCE310","Pediatric");
			codes.put("TCE311","Breast");
			return codes;
		}
	}
}
