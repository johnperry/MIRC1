/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.dicomeditor;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.EOFException;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Set;

import org.rsna.dicom.DcmClient;

import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmDecodeParam;
import org.dcm4che.data.DcmEncodeParam;
import org.dcm4che.data.DcmParser;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.FileFormat;
import org.dcm4che.data.FileMetaInfo;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.DcmParserFactory;
import org.dcm4che.dict.DictionaryFactory;
import org.dcm4che.dict.TagDictionary;
import org.dcm4che.dict.VRs;

import org.apache.log4j.Logger;

/**
 * A correction tool for DICOM VRs. The corrector fixes the VRs in
 * DICOM objects. These VRs may have been corrupted by early versions
 * of the Anonymizer.
 */
public class Corrector {

	static final Logger logger = Logger.getLogger(Corrector.class);
	static final DcmParserFactory pFact = DcmParserFactory.getInstance();
	static final DcmObjectFactory oFact = DcmObjectFactory.getInstance();
	static final DictionaryFactory dFact = DictionaryFactory.getInstance();
	static final TagDictionary tagDictionary = dFact.getDefaultTagDictionary();

   /**
     * Corrects the VRs in the input file, overwriting it, preserving the syntax.
     * <p>
     * @param inFile the file to correct.
     * @return the list of exceptions
     */
   public static String fixVRs(File inFile) {
	   return fixVRs(inFile,inFile,false);
   }

   /**
     * Corrects the VRs in the input file, overwriting it, forcing the syntax.
     * <p>
     * @param inFile the file to correct.
     * @param forceIVRLE force the transfer syntax to IVRLE if true; leave
     * the syntax unmodified if false.
     * @return the list of exceptions
     */
   public static String fixVRs(File inFile, boolean forceIVRLE) {
	   return fixVRs(inFile,inFile,forceIVRLE);
   }


   /**
     * Corrects the VRs in the input file, writing the result to the output file.
     * @param inFile the file to correct.
     * @param outFile the output file.  It may be same as inFile you if want
     *      to correct in place.
     * @param forceIVRLE force the transfer syntax to IVRLE if true; leave
     * the syntax unmodified if false.
     * @return the list of exceptions
     */
    public static String fixVRs(File inFile, File outFile, boolean forceIVRLE) {
		BufferedInputStream in = null;
		FileOutputStream out = null;
		File tempFile = null;
		String exceptions = "";
		try {
			// Check that this is a known format.
			in = new BufferedInputStream(new FileInputStream(inFile));
			DcmParser parser = pFact.newDcmParser(in);
			FileFormat fileFormat = parser.detectFileFormat();
			if (fileFormat == null) throw new IOException("Unrecognized file format: "+inFile);

			// Get the dataset (excluding pixels) and leave the input stream open
			Dataset dataset = oFact.newDataset();
			parser.setDcmHandler(dataset.getDcmHandler());
			parser.parseDcmFile(fileFormat, Tags.PixelData);

			// Reject objects with encapsulated pixel data.
			if ((parser.getReadTag() == Tags.PixelData) && (parser.getReadLength() < 0))
				return "!error! - encapsulated pixel data";

			exceptions = correctVRs(dataset);
			if (!exceptions.equals("")) logger.error("Corrector exceptions for "+inFile+"\n"+exceptions);

			// Save the dataset.
			// Write to a temporary file in a temporary directory
			// on the same file system root, and rename at the end.
			File tempDir = new File(outFile.getParentFile().getParentFile(),"temp");
			tempDir.mkdirs();
			tempFile = File.createTempFile("DCMtemp-",".anon",tempDir);
            out = new FileOutputStream(tempFile);

			// Set the encoding
        	String prefEncodingUID = UIDs.ImplicitVRLittleEndian;
			FileMetaInfo fmi = dataset.getFileMetaInfo();
            if ((fmi != null) && !forceIVRLE)  prefEncodingUID = fmi.getTransferSyntaxUID();

            // Create and write the metainfo for the encoding we are using
			fmi = oFact.newFileMetaInfo(dataset, prefEncodingUID);
            dataset.setFileMetaInfo(fmi);
            fmi.write(out);

			// write the dataset as far as was parsed
			DcmEncodeParam encoding = DcmDecodeParam.valueOf(prefEncodingUID);
			dataset.writeDataset(out, encoding);
			// write the pixels if the parser actually stopped before pixeldata
			if (parser.getReadTag() == Tags.PixelData) {
				// write (7fe0,0010) attribute header
				dataset.writeHeader(
					out,
					encoding,
					parser.getReadTag(),
					parser.getReadVR(),
					parser.getReadLength());
				// stream pixel data from file
				byte[] buffer = new byte[4096];
				in = new BufferedInputStream(parser.getInputStream());
				int c, remain = parser.getReadLength();
				while (remain > 0) {
					c = in.read(buffer, 0, Math.min(buffer.length, remain));
					if (c == -1) {
						throw new EOFException("EOF during read of pixel data");
					}
					out.write(buffer, 0, c);
					remain -= c;
				}
				// load and transmit any attributes after pixel data
				dataset.clear();
				parser.parseDataset(parser.getDcmDecodeParam(), -1);
				dataset.writeDataset(out, encoding);
			}
			out.flush();
			out.close();
			in.close();
			outFile.delete();
			tempFile.renameTo(outFile);
		}

		catch (Exception e) {
			try {
				//Close the input stream if it actually got opened.
				if (in != null) in.close();
			}
			catch (Exception ignore) { }
			try {
				//Close the output stream if it actually got opened,
				//and delete the tempFile in case it is still there.
				if (out != null) {
					out.close();
					tempFile.delete();
				}
			}
			catch (Exception ignore) { }
			//Now figure out what kind of a response to return.
			String msg = e.getMessage();
			if (msg == null) msg = "!error! - no message";
			else
				logger.error("Error correcting VRs in "+inFile,e);
			return msg;
		}
		return exceptions;
    }


	//Correct the VRs in the dataset.
	private static String correctVRs(Dataset ds) throws Exception {
		String exceptions = "";
		String value;

		//Walk the dataset, element by element and compare each
		//element's VR to the VR in the dictionary for that element.
		for (Iterator it=ds.iterator(); it.hasNext(); ) {
			DcmElement el = (DcmElement)it.next();
			int tag = el.tag();
			int elVR = el.vr();
			int dictVR = getVR(tag,elVR);
			if (elVR != dictVR) {
				value = "";
				try {
					value = ds.getString(tag);
					if ((value == null) || value.equals("")) {
						if (dictVR == VRs.PN) ds.putXX(tag,dictVR," ");
						else ds.putXX(tag,dictVR);
					}
					else ds.putXX(tag,dictVR,value);
				}
				catch (Exception e) {
					logger.warn("VR corrector exception:\n" + e.toString()
								+ "\ntag=" + Integer.toHexString(tag)
								+ ": value= \"" + value + "\"");
					if (!exceptions.equals("")) exceptions += ", ";
					exceptions += Tags.toString(tag);
				}
			}
		}
		return exceptions;
	}

	private static int getVR(int tag, int elVR) {
		TagDictionary.Entry entry = tagDictionary.lookup(tag);
		if (entry == null) return elVR;
		String[] vrs = entry.vr.split(",");
		for (int i=0; i<vrs.length; i++) {
			if (elVR == VRs.valueOf(vrs[i].trim())) return elVR;
		}
		return VRs.valueOf(vrs[0].trim());
	}

}
