/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import java.awt.*;
import java.awt.image.*;
import java.awt.Graphics2D;
import java.io.*;
import java.nio.charset.Charset;
import javax.swing.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.geom.AffineTransform;
import org.rsna.dicom.DcmClient;
import com.sun.image.codec.jpeg.JPEGCodec;
import com.sun.image.codec.jpeg.JPEGEncodeParam;
import com.sun.image.codec.jpeg.JPEGImageEncoder;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmObject;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.DcmParser;
import org.dcm4che.data.DcmParserFactory;
import org.dcm4che.data.FileFormat;
import org.dcm4che.data.FileMetaInfo;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.SpecificCharacterSet;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.VRs;
import org.dcm4che.dict.DictionaryFactory;
import org.dcm4che.dict.TagDictionary;
import org.dcm4che.dict.UIDDictionary;
import org.dcm4che.dict.VRs;

/**
 * A class to encapsulate a DICOM image, providing access to
 * the header and the image as well as methods for editing the header
 * and creating JPEG images from the pixels.
 */
public class DicomImage {

	static final DcmParserFactory pFact = DcmParserFactory.getInstance();
	static final DcmObjectFactory oFact = DcmObjectFactory.getInstance();
	static final DictionaryFactory dFact = DictionaryFactory.getInstance();
	static final TagDictionary tagDictionary = dFact.getDefaultTagDictionary();
	static final UIDDictionary uidDictionary = dFact.getDefaultUIDDictionary();

	/** The file containing the DICOM image. */
	public File imageFile;
	/** The dcm4che dataset for the DICOM object. */
	public Dataset dataset;
	/** The transfer syntaxUID. */
	public String transferSyntaxUID;
	/** The BufferedImage created from the DICOM image. */
	public BufferedImage image;
	/** The DICOM Columns element (0028,0012). */
	public int imageWidth;
	/** The DICOM Rows element (0028,0011). */
	public int imageHeight;
	/** The DICOM BitsAllocated element (0028,0100). */
	public int bitsAllocated = 16;
	/** The DICOM BitsStored element (0028,0101). */
	public int bitsStored = 12;
	/** The DICOM HighBit element (0028,0102). */
	public int highBit = 11;
	/** The DICOM WindowCenter element (0028,1050). */
	public int windowCenter = 128;
	/** The DICOM WindowWidth element (0028,1051). */
	public int windowWidth = 256;
	/** The DICOM StudyInstanceUID element (0020,000D). */
	public String siUID = null;
	/** The table of elements tested by the anonymizer scripts that could generate quarantine calls. */
	public Hashtable testedElementsHash = null;

	String imageMessage = null;

	/**
	 * Class constructor; creates a new DicomImage object and
	 * loads the image from the file.
	 * @param imageFile the file containing the DICOM image.
	 * @throws Exception if the file does not contain a parsable DICOM image.
	 * @throws IOException if the file cannot be read.
	 */
	public DicomImage(File imageFile) throws Exception {
		this.imageFile = imageFile;
		if (!imageFile.exists())
			throw new IOException(imageFile.getName() + " could not be found.");

		//Open and parse the image
		BufferedInputStream in = new BufferedInputStream(new FileInputStream(imageFile));

		DcmParser parser = null;
		FileFormat fileFormat = null;
		try {
			parser = pFact.newDcmParser(in);
			fileFormat = parser.detectFileFormat();

			//Bail out if we don't like the format
			if (fileFormat == null)
				throw new Exception("Unrecognized file format in "+imageFile.getName());

			//Get the dataset
			dataset = oFact.newDataset();
			parser.setDcmHandler(dataset.getDcmHandler());
			parser.parseDcmFile(fileFormat, -1);
			in.close();
		}
		catch (Exception ex) {
			if (in != null) in.close();
			throw ex;
		}

		//Get the key parameters
		try {
			FileMetaInfo fmi = dataset.getFileMetaInfo();
			transferSyntaxUID = fmi.getTransferSyntaxUID();
		}
		catch (Exception e) { transferSyntaxUID = ""; }
		try {siUID = dataset.getString(Tags.StudyInstanceUID);}
		catch (Exception e) { siUID = ""; }
		try {imageWidth = dataset.getInteger(Tags.Columns).intValue();}
		catch (Exception e) { };
		try {imageHeight = dataset.getInteger(Tags.Rows).intValue();}
		catch (Exception e) { };
		try {bitsAllocated = dataset.getInteger(Tags.BitsAllocated).intValue();}
		catch (Exception e) { };
		try {bitsStored = dataset.getInteger(Tags.BitsStored).intValue();}
		catch (Exception e) { };
		try {highBit = dataset.getInteger(Tags.HighBit).intValue();}
		catch (Exception e) { };
		windowWidth = getIntFromTagInt(Tags.WindowWidth, 256);
		windowCenter = getIntFromTagInt(Tags.WindowCenter,128);

		//Read the image and leave it null if an error occurs.
		//This lets us actually construct the object, even if
		//it isn't an image.
		try {
			imageMessage = null;
			image = ImageIO.read(imageFile);
		}
		catch (IOException e) {
			imageMessage = e.getMessage();
			image = null;
		}
	}

	/**
	 * Get the transfer syntax name
	 * @return the name of the transfer syntax.
	 */
	public String getTransferSyntaxName() {
		try { return uidDictionary.lookup(transferSyntaxUID).name; }
		catch (Exception e) { }
		return "unknown transfer syntax";
	}

	/**
	 * Get the SOP class name
	 * @return the name of the SOP class.
	 */
	public String getSOPClassName() {
		try {
			String scUID = dataset.getString(Tags.SOPClassUID);
			return uidDictionary.lookup(scUID).name;
		}
		catch (Exception e) { }
		return "unknown SOP Class";
	}

	//Get an int from the dataset, supplying a
	//default value if the element is missing or incorrect.
	private int getIntFromTagInt(int tag, int defaultValue) {
		String s;
		try {
			s = dataset.getString(tag);
			return Integer.parseInt(s);
		}
		catch (Exception e) { return defaultValue; }
	}

	/**
	 * Save the dataset as a file.
	 * @param file the file in which to write the dataset.
	 */
	public boolean saveDicomImage(File file) {
		try { DcmClient.Utils.writeDataset(dataset,file); }
		catch (Exception e) { return false; }
		return true;
	}

	/**
	 * Save the dataset, overwriting the original imageFile.
	 */
	public boolean saveDicomImage() {
		return saveDicomImage(imageFile);
	}

	/**
	 * Get a BufferedImage of a specific maximum width from the DICOM image.
	 * The size of the buffered image is determined by the imageWidth parameter
	 * (Columns) of the DICOM image and the maxWidth parameter of the method call.
	 * The aspect ratio of the image is preserved.
	 * @param maxWidth the maximum width of the returned BufferedImage.
	 * @return a BufferedImage with a width equal to the minimum of the width of
	 * the original image and the maxWidth parameter.
	 * @throws Exception if the process fails.
	 */
	public BufferedImage getBufferedImage(int maxWidth) throws Exception {
		// If the image is null, then we couldn't have loaded it.
		// If so, return the message we got when the object was created.
		if (image == null) throw new Exception(imageMessage);
		// Set the scale
		double scale = (maxWidth < imageWidth) ? (double)maxWidth/(double)imageWidth : 1.0D;

		// Set up the transform
		AffineTransformOp op =
			new AffineTransformOp(
				AffineTransform.getScaleInstance(scale,scale),
				AffineTransformOp.TYPE_NEAREST_NEIGHBOR);

		// Transform the image
		BufferedImage scaledImage =
			op.filter(
				image,
				op.createCompatibleDestImage(image,image.getColorModel() ));
		return scaledImage;
	}

	/**
	 * Get a BufferedImage of a specific maximum width from the DICOM image.
	 * The size of the buffered image is determined by the imageWidth parameter
	 * (Columns) of the DICOM image and the maxWidth parameter of the method call.
	 * The aspect ratio of the image is preserved. The pixel bits above the
	 * BitsStored position are masked to zero. This method provides a BufferedImage
	 * that will not cause the JPEG converter to fail.
	 * @param maxWidth the maximum width of the returned BufferedImage.
	 * @return a BufferedImage with a width equal to the minimum of the width of
	 * the original image and the maxWidth parameter, with unused bits masked to zero.
	 * @throws Exception if the process fails.
	 */
	public BufferedImage getMaskedBufferedImage(int maxWidth) throws Exception {
		BufferedImage bufferedImage = getBufferedImage(maxWidth);
		WritableRaster wr = bufferedImage.getRaster();

		DataBuffer b = wr.getDataBuffer();
		if (b.getDataType() == DataBuffer.TYPE_USHORT) {
			DataBufferUShort bs = (DataBufferUShort)b;
			short[] data = bs.getData();
			if (bitsStored < 16) {
				int maxPixelInt = (1 << bitsStored) - 1;
				short maxPixel = (short)(0xffff & maxPixelInt);
				for (int i=0; i<data.length; i++) {
					if (data[i] > maxPixel) data[i] = maxPixel;
				}
			}
		}
		bufferedImage.setData(wr);
		return bufferedImage;
	}

	/**
	 * Save the DICOM image as a JPEG with a specific maximum width and the default quality.
	 * The size of the saved image is determined by the imageWidth parameter
	 * (Columns) of the DICOM image and the maxWidth parameter of the method call.
	 * The aspect ratio of the image is preserved. This method uses the
	 * Sun JPEGImageEncoder.encode method.
	 * @param maxWidth the maximum width of the returned BufferedImage.
	 * @param file the file in which to save the image.
	 * @throws Exception if the process fails.
	 */
	public void saveAsJPEG(int maxWidth, File file) throws Exception {
		saveAsJPEG(maxWidth, file, -1);
	}

	/**
	 * Save the DICOM image as a JPEG with a specific maximum width and quality.
	 * The size of the saved image is determined by the imageWidth parameter
	 * (Columns) of the DICOM image and the maxWidth parameter of the method call.
	 * The aspect ratio of the image is preserved. This method uses the
	 * Sun JPEGImageEncoder.encode method.
	 * @param maxWidth the maximum width of the returned BufferedImage.
	 * @param file the file in which to save the image.
	 * @throws Exception if the process fails.
	 */
	public void saveAsJPEG(int maxWidth, File file, int quality) throws Exception {
		// Get the image
		BufferedImage scaledImage = getMaskedBufferedImage(maxWidth);

		// Get a stream pointing to the destination image location
		OutputStream out = new BufferedOutputStream(new FileOutputStream(file));

		// JPEG-encode the image and write to file.
		JPEGImageEncoder encoder = JPEGCodec.createJPEGEncoder(out);
		if (quality >= 0) {
			quality = Math.min(quality,100);
			float fQuality = ((float)quality) / 100.0F;
			JPEGEncodeParam p = encoder.getDefaultJPEGEncodeParam(scaledImage);
			p.setQuality(fQuality,true);
			encoder.setJPEGEncodeParam(p);
		}
		encoder.encode(scaledImage);
		out.close();
	}

	/**
	 * Save the DICOM image as a JPEG with a specific maximum width.
	 * The size of the saved image is determined by the imageWidth parameter
	 * (Columns) of the DICOM image and the maxWidth parameter of the method call.
	 * The aspect ratio of the image is preserved. This method uses the ImageIO.write
	 * method.
	 * @param maxWidth the maximum width of the returned BufferedImage.
	 * @param file the file in which to save the image.
	 * @throws Exception if the process fails.
	 */
	public void writeAsJPEG(int maxWidth, File file) throws Exception {
		// Get the image
		BufferedImage scaledImage = getMaskedBufferedImage(maxWidth);

		// Write the file
		if (!ImageIO.write(scaledImage,"jpeg",file))
			throw new Exception("ImageIO.write returned false");
	}


	/**
	 * Get the maximum pixel value in the DICOM image.
	 * @return the maximum pixel value in the DICOM image.
	 * @throws Exception if the process fails.
	 */
	public int getMaxPixel() throws Exception {
		BufferedImage fullImage = getBufferedImage(imageWidth);
		WritableRaster wr = fullImage.getRaster();
		int w = wr.getWidth();
		int h = wr.getHeight();
		int minX = wr.getMinX();
		int minY = wr.getMinY();
		int numBands = wr.getNumBands();
		if (numBands != 1) throw new Exception("NumBands = " + numBands);
		int[] pixels = new int[w*h];
		pixels = wr.getPixels(minX, minY, w, h, pixels);
		int maxPixel = 0;
		for (int i=0; i<pixels.length; i++) {
			if (maxPixel < pixels[i]) maxPixel = pixels[i];
		}
		return maxPixel;
	}

	/**
	 * Get a String containing an HTML table element describing the
	 * key parameters of the DICOM image.
	 * @return the HTML text describing the DICOM image
	 * @throws Exception if the process fails.
	 */
	public String getColorString() throws Exception {
		int pixelSize = image.getColorModel().getPixelSize();
		int colorSpaceType = image.getColorModel().getColorSpace().getType();
		int numBands = image.getData().getNumBands();
		int maxPixel = getMaxPixel();

		String colorString =
			"<table>\n"
		  + "<tr><td>Original Image Width</td><td>"+imageWidth+"</td></tr>\n"
		  + "<tr><td>Original Image Height</td><td>"+imageHeight+"</td></tr>\n"
		  + "<tr><td>Buffered Image Type</td><td>"+image.getType()+"</td></tr>\n"
		  + "<tr><td>ColorModel PixelSize</td><td>"+pixelSize+"</td></tr>\n"
		  + "<tr><td>ColorSpaceType</td><td>"+colorSpaceType+"</td></tr>\n"
		  + "<tr><td>numBands</td><td>"+numBands+"</td></tr>\n"
		  + "<tr><td>maxPixel</td><td>"+maxPixel+"</td></tr>\n"
		  + "<tr><td>windowCenter</td><td>"+windowCenter+"</td></tr>\n"
		  + "<tr><td>windowWidth</td><td>"+windowWidth+"</td></tr>\n"
		  + "</table>\n";
		return colorString;
	}

	/**
	 * Get a String containing an HTML table element listing all the
	 * elements in the DICOM image and their values.
	 * @return the HTML text listing all the elements in the DICOM image.
	 * @throws Exception if the process fails.
	 */
	public String getElementList() {
		SpecificCharacterSet cs = dataset.getSpecificCharacterSet();
		StringBuffer table = new StringBuffer("<h3>"+imageFile.getName());
		table.append("<br>"+getTransferSyntaxName());
		table.append("<br>"+getSOPClassName()+"</h3>\n");
		table.append("<table>\n");
		table.append("<b><tr><th>Element</th><td><b>Name</b></td><th>VR</th>" +
						"<th>VM</th><th>Length</th><td><b>Data</b></td></tr></b>\n");
		dumpDcmObject(dataset.getFileMetaInfo(),cs,table);
		dumpDcmObject(dataset,cs,table);
		table.append("</table>\n");
		return table.toString();
	}

	//Display the elements in a DcmObject
	private void dumpDcmObject(DcmObject dataset, SpecificCharacterSet cs, StringBuffer table) {
		int maxLength = 60;
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

			table.append("<td>"+tagString+"</td>");

			if (testedElementsHash.get(tagString) == null) table.append("<td>"+tagName+"</td>");
			else table.append("<td><font color=\"red\">"+tagName+"</font></td>");

			table.append("<td align=center>"+vrString+"</td>");
			table.append("<td align=center>"+vm+"</td>");
			table.append("<td align=center>"+el.length()+"</td>");

			if (!vrString.toLowerCase().startsWith("sq")) {
				valueString = getElementValueString(cs,el);
				if (valueString == null)
					table.append("<td>"+nullValue()+"</td>");
				else if (valueString.length() < maxLength)
					table.append("<td>\""+valueString+"\"</td>");
				else
					table.append("<td>\""+valueString.substring(0,maxLength)+"...\"</td>");
			}
			table.append("</tr>\n");
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
		return nullValue();
	}

	//Return an HTML string indicating a null value (in red).
	private String nullValue() {
		return "<font color=red>null</font>";
	}

	/**
	 * Edit a DICOM element value through a JOptionPane.
	 * @param parent the parent component, used to center the JOptionPane.
	 * @param tag the DICOM tag (0xggggeeee) of the element to be edited.
	 * @return the object encapsulating the change.
	 */
	public EditChange edit(Component parent, int tag) {
		try {
			SpecificCharacterSet cs = dataset.getSpecificCharacterSet();
			DcmElement element = dataset.get(tag);
			TagDictionary.Entry entry = tagDictionary.lookup(tag);
			if (entry == null) return null;
			String vrString = entry.vr;
			int vr = VRs.valueOf(vrString);
			if ((element != null) && (VRs.isStringValue(vr))) {
				String name = tagDictionary.lookup(tag).name;
				String value = getElementValueString(cs,element);
				String newValue =
					JOptionPane.showInputDialog(
							parent,
							"Edit DICOM Element:\n"
							 + Tags.toString(tag) + "  "+name+"\n\n",
							 value);
				if ((newValue != null) && (!newValue.equals(value))) {
					if ((newValue == null) || newValue.equals("")) {
						if (vr == VRs.PN) dataset.putXX(tag,vr," ");
						else dataset.putXX(tag,vr);
					}
					else dataset.putXX(tag,vr,newValue);
					return new EditChange(tag,value,newValue,siUID);
				}
			}
		}
		catch (Exception e) { }
		return null;
	}

	/**
	 * Apply a LinkedList of EditChanges to the DICOM image.
	 * @param list the list of EditChanges.
	 */
	public void applyChanges(LinkedList list) {
		TagDictionary.Entry entry;
		String vrString;
		int vr;
		EditChange change;
		for (Iterator it=list.iterator(); it.hasNext(); ) {
			change = (EditChange)it.next();
			if (siUID.equals(change.siUID)) {
				entry = tagDictionary.lookup(change.tag);
				if (entry != null) {
					vrString = entry.vr;
					vr = VRs.valueOf(vrString);
					if ((change.newValue == null) || change.newValue.equals("")) {
						if (vr == VRs.PN) dataset.putXX(change.tag,vr," ");
						else dataset.putXX(change.tag,vr);
					}
					else dataset.putXX(change.tag,vr,change.newValue);
				}
			}
		}
	}

	/**
	 * Save a list of DICOM elements that could have generated
	 * a quarantine call. This is used to determine whether an
	 * element name should be shown in red in the HTML table
	 * returned by getElementList.
	 * @param testedElements the array of DICOM element names
	 * as used in the dcm4che TagDictionary.
	 */
	public void setTestedElements(String[] testedElements) {
		testedElementsHash = new Hashtable();
		if (testedElements == null) return;
		for (int i=0; i<testedElements.length; i++) {
			try {
				int tag = Tags.forName(testedElements[i]);

				//Index the tag name so we can make it red
				String tagString = Tags.toString(tag);
				testedElementsHash.put(tagString,"");

				//Create an element if necessary
				String value = null;
				try { value = dataset.getString(tag); }
				catch (Exception ignore) { }
				if (value == null) {
					//Okay, the tag name is valid
					//but the element is missing.
					//Create an empty element so it
					//can be edited if necessary
					dataset.putXX(tag,"");
				};
			}
			catch (Exception ignore) { }
		}
	}

}
