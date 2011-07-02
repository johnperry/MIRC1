/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.anonymizer;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.security.*;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Set;
import java.util.regex.*;
import sun.misc.BASE64Encoder;

import org.rsna.dicom.DcmClient;

import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmDecodeParam;
import org.dcm4che.data.DcmEncodeParam;
import org.dcm4che.data.DcmParser;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.FileFormat;
import org.dcm4che.data.FileMetaInfo;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.Dimse;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.DcmParserFactory;
import org.dcm4che.dict.DictionaryFactory;
import org.dcm4che.dict.TagDictionary;
import org.dcm4che.dict.VRs;

import org.apache.log4j.Logger;

import sun.misc.BASE64Encoder;
import sun.misc.BASE64Decoder;

import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * The MIRC DICOM anonymizer. The anonymizer provides de-identification and
 * re-identification of DICOM objects for clinical trials. Each element
 * as well as certain groups of elements are scriptable. The script
 * language is defined in "How to Configure the Anonymizer for MIRC
 * Clinical Trial Services".
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class DicomAnonymizer {

	static final Logger logger = Logger.getLogger(DicomAnonymizer.class);
	static final DcmParserFactory pFact = DcmParserFactory.getInstance();
	static final DcmObjectFactory oFact = DcmObjectFactory.getInstance();
	static final DictionaryFactory dFact = DictionaryFactory.getInstance();
	static final TagDictionary tagDictionary = dFact.getDefaultTagDictionary();

	static BASE64Encoder b64Encoder = new BASE64Encoder();
	static BASE64Decoder b64Decoder = new BASE64Decoder();

   /**
     * Anonymizes the input file, writing the result to the output file.
     * The input and output files are allowed to be the same.
     * The fields to anonymize are scripted in the properties file.
     * The Remapper instance provides a link either to a local or remote
     * remapper for PHI replacement where the remapped elements must maintain
     * their relationships with other remapped elements.
     * <p>
     * Important note: if the script generates a skip() or quarantine()
     * function call, the output file is not written and the input file
     * is unmodified, even if it is the same as the output file.
     * @param inFile the file to anonymize.
     * @param outFile the output file.  It may be same as inFile you if want
     *      to anonymize in place.
     * @param cmds the properties file containing the anonymization commands.
     * @param lkup the properties file containing the local lookup table; null if local
     * lookup is not to be used.
     * @param remapper the Remapper to use for remapping function calls.
     * @param forceIVRLE force the transfer syntax to IVRLE if true; leave
     * the syntax unmodified if false.
     * @param renameToSOPIUID rename the output file to [SOPInstanceUID].dcm, where
     * [SOPInstanceUID] is the value in the anonymized object (in case it is
     * remapped during anonymization.
     * @return the list of exceptions
     */
    public static String anonymize(
			File inFile,
			File outFile,
			Properties cmds,
			Properties lkup,
			Remapper remapper,
			boolean forceIVRLE,
			boolean renameToSOPIUID) {
		String exceptions = "";
		BufferedInputStream in = null;
		FileOutputStream out = null;
		File tempFile = null;
		byte[] buffer = new byte[4096];
		try {
			//Check that this is a known format.
			in = new BufferedInputStream(new FileInputStream(inFile));
			DcmParser parser = pFact.newDcmParser(in);
			FileFormat fileFormat = parser.detectFileFormat();
			if (fileFormat == null) throw new IOException("Unrecognized file format: "+inFile);

			//Get the dataset (excluding pixels) and leave the input stream open
			Dataset dataset = oFact.newDataset();
			parser.setDcmHandler(dataset.getDcmHandler());
			parser.parseDcmFile(fileFormat, Tags.PixelData);

			//Set up the replacements using the cmds properties and the dataset
			Properties theReplacements = setUpReplacements(dataset,cmds,lkup,remapper);
			if (remapper.getCount() > 0) {
				theReplacements =
					insertRemappedValues(
						theReplacements,
						remapper.getRemappedValues());
			}

			// get booleans to handle the global cases
			boolean rpg = (cmds.getProperty("remove.privategroups") != null);
			boolean rue = (cmds.getProperty("remove.unspecifiedelements") != null);
			boolean rol = (cmds.getProperty("remove.overlays") != null);
			int[] keepGroups = getKeepGroups(cmds);

			//Modify the elements according to the commands
			exceptions = doOverwrite(dataset,theReplacements,rpg,rue,rol,keepGroups);
			if (!exceptions.equals(""))
				logger.error("DicomAnonymizer exceptions for "+inFile+"\n"+exceptions);

			//Save the dataset.
			//Write to a temporary file in a temporary directory
			//on the same file system root, and rename at the end.
			File tempDir = new File(outFile.getParentFile().getParentFile(),"anonymizer-temp");
			tempDir.mkdirs();
			tempFile = File.createTempFile("DCMtemp-",".anon",tempDir);
            out = new FileOutputStream(tempFile);

            //Get the SOPInstanceUID in case we need it for the rename.
            String sopiUID = null;
			try { sopiUID = dataset.getString(Tags.SOPInstanceUID); }
			catch (Exception e) { };
			sopiUID = sopiUID.trim();

			//Set the encoding
			DcmDecodeParam fileParam = parser.getDcmDecodeParam();
        	String prefEncodingUID = UIDs.ImplicitVRLittleEndian;
			FileMetaInfo fmi = dataset.getFileMetaInfo();
            if ((fmi != null) && (fileParam.encapsulated || !forceIVRLE))
            	prefEncodingUID = fmi.getTransferSyntaxUID();
			DcmEncodeParam encoding = (DcmEncodeParam)DcmDecodeParam.valueOf(prefEncodingUID);
			boolean swap = fileParam.byteOrder != encoding.byteOrder;

            //Create and write the metainfo for the encoding we are using
			fmi = oFact.newFileMetaInfo(dataset, prefEncodingUID);
            dataset.setFileMetaInfo(fmi);
            fmi.write(out);

			//Write the dataset as far as was parsed
			dataset.writeDataset(out, encoding);
			//Write the pixels if the parser actually stopped before pixeldata
            if (parser.getReadTag() == Tags.PixelData) {
                dataset.writeHeader(
                    out,
                    encoding,
                    parser.getReadTag(),
                    parser.getReadVR(),
                    parser.getReadLength());
                if (encoding.encapsulated) {
                    parser.parseHeader();
                    while (parser.getReadTag() == Tags.Item) {
                        dataset.writeHeader(
                            out,
                            encoding,
                            parser.getReadTag(),
                            parser.getReadVR(),
                            parser.getReadLength());
                        writeValueTo(parser, buffer, out, false);
                        parser.parseHeader();
                    }
                    if (parser.getReadTag() != Tags.SeqDelimitationItem) {
                        throw new Exception(
                            "Unexpected Tag: " + Tags.toString(parser.getReadTag()));
                    }
                    if (parser.getReadLength() != 0) {
                        throw new Exception(
                            "(fffe,e0dd), Length:" + parser.getReadLength());
                    }
                    dataset.writeHeader(
                        out,
                        encoding,
                        Tags.SeqDelimitationItem,
                        VRs.NONE,
                        0);
                } else {
                    writeValueTo(parser, buffer, out, swap && (parser.getReadVR() == VRs.OW));
                }
				parser.parseHeader(); //get ready for the next element
			}
			//Now do any elements after the pixels one at a time.
			//This is done to allow streaming of large raw data elements
			//that occur above Tags.PixelData.
			while (!parser.hasSeenEOF() && parser.getReadTag() != -1) {
				dataset.writeHeader(
					out,
					encoding,
					parser.getReadTag(),
					parser.getReadVR(),
					parser.getReadLength());
				writeValueTo(parser, buffer, out, swap);
				parser.parseHeader();
			}
			out.flush();
			out.close();
			in.close();
			if (renameToSOPIUID) outFile = new File(outFile.getParentFile(),sopiUID+".dcm");
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
			if (msg.indexOf("!skip!") != -1) return "";
			if (msg.indexOf("!quarantine!") != -1)
				logger.info("Quarantine call from "+inFile);
			else
				logger.error("Error Anonymizing "+inFile,e);
			return msg;
		}
		IdTable.storeLater(false); //only store if the table is dirty
		return exceptions;
    }

	private static void writeValueTo(
					DcmParser parser,
					byte[] buffer,
					OutputStream out,
					boolean swap) throws Exception {
		InputStream in = parser.getInputStream();
		int len = parser.getReadLength();
		if (swap && (len & 1) != 0) {
			throw new Exception(
				"Illegal length for swapping value bytes: " + len);
		}
		if (buffer == null) {
			if (swap) {
				int tmp;
				for (int i = 0; i < len; ++i, ++i) {
					tmp = in.read();
					out.write(in.read());
					out.write(tmp);
				}
			} else {
				for (int i = 0; i < len; ++i) {
					out.write(in.read());
				}
			}
		} else {
			byte tmp;
			int c, remain = len;
			while (remain > 0) {
				c = in.read(buffer, 0, Math.min(buffer.length, remain));
				if (c == -1) {
					throw new EOFException("EOF while reading element value");
				}
				if (swap) {
					if ((c & 1) != 0) {
						buffer[c++] = (byte) in.read();
					}
					for (int i = 0; i < c; ++i, ++i) {
						tmp = buffer[i];
						buffer[i] = buffer[i + 1];
						buffer[i + 1] = tmp;
					}
				}
				out.write(buffer, 0, c);
				remain -= c;
			}
		}
		parser.setStreamPosition(parser.getStreamPosition() + len);
	}

    //Create a Properties object that contains all the replacement values defined
    //by the scripts for each element. The value for an element is stored under
    //the key "(gggg,eeee)".
    private static Properties setUpReplacements(
			Dataset ds,
			Properties cmds,
			Properties lkup,
			Remapper remapper) throws Exception {
		Properties props = new Properties();
		for (Enumeration it=cmds.keys(); it.hasMoreElements(); ) {
			String key = (String) it.nextElement();
			if (key.startsWith("set.")) {
				try {
					String replacement = makeReplacement(key,cmds,lkup,ds,remapper);
					props.setProperty(getTagString(key),replacement);
				}
				catch (Exception e) {
					String msg = e.getMessage();
					if (msg == null) msg = "";
					if (msg.indexOf("!skip!") != -1) throw e;
					if (msg.indexOf("!quarantine!") != -1) throw e;
					logger.error("Exception in setUpReplacements:",e);
					throw new Exception(
						"!error! during processing of:\n" + key + "=" + cmds.getProperty(key));
				}
			}
		}
		return props;
	}

	//Get an int[] containing all the keep.groupXXXX
	//elements' group numbers, sorted in order.
	private static int[] getKeepGroups(Properties cmds) {
		LinkedList list = new LinkedList();
		for (Enumeration it=cmds.keys(); it.hasMoreElements(); ) {
			String key = (String)it.nextElement();
			if (key.startsWith("keep.group")) {
				list.add(key.substring("keep.group".length()).trim());
			}
		}
		Iterator iter = list.iterator();
		int[] keepGroups = new int[list.size()];
		for (int i=0; i<keepGroups.length; i++) {
			try { keepGroups[i] = Integer.parseInt((String)iter.next(),16) << 16; }
			catch (Exception ex) { keepGroups[i] = 0; }
		}
		Arrays.sort(keepGroups);
		return keepGroups;
	}

	//Find "[gggg,eeee]" in a String and
	//return a tagString in the form "(gggg,eeee)".
	static final String defaultTagString = "(0000,0000)";
	private static String getTagString(String key) {
		int k = key.indexOf("[");
		if (k < 0) return defaultTagString;
		int kk = key.indexOf("]",k);
		if (kk < 0) return defaultTagString;
		return ("(" + key.substring(k+1,kk) + ")").toLowerCase();
	}

	//Find "(gggg,eeee)" in a String and
	//return an int corresponding to the hex value.
	private static int getTagInt(String key) {
		int k = key.indexOf("(");
		if (k < 0) return 0;
		int kk = key.indexOf(")",k);
		if (kk < 0) return 0;
		key = key.substring(k+1,kk).replaceAll("[^0-9a-fA-F]","");
		try { return Integer.parseInt(key,16); }
		catch (Exception e) { return 0; }
	}

	static final char escapeChar 		= '\\';
	static final char functionChar 		= '@';
	static final char delimiterChar 	= '^';
	static final String ifFn 			= "if";
	static final String requireFn 		= "require";
	static final String contentsFn 		= "contents";
	static final String paramFn 		= "param";
	static final String lookupFn		= "lookup";
	static final String initialsFn 		= "initials";
	static final String scrambleFn 		= "scramble";
	static final String alphabetichashFn= "alphabetichash";
	static final String numerichashFn 	= "numerichash";
	static final String uidFn 			= "uid";
	static final String hashuidFn 		= "hashuid";
	static final String ptidFn 			= "ptid";
	static final String hashFn 			= "hash";
	static final String encryptFn 		= "encrypt";
	static final String hashptidFn 		= "hashptid";
	static final String idFn 			= "id";
	static final String integerFn 		= "integer";
	static final String accessionFn	 	= "accession";
	static final String offsetdateFn 	= "offsetdate";
	static final String incrementdateFn = "incrementdate";
	static final String modifydateFn	= "modifydate";
	static final String roundFn 		= "round";
	static final String timeFn 			= "time";
	static final String dateFn 			= "date";
	static final String quarantineFn 	= "quarantine";
	static final String skipFn	 		= "skip";


	//Create the replacement for one element starting from a key.
	private static String makeReplacement(
			String key,
			Properties cmds,
			Properties lkup,
			Dataset ds,
			Remapper remapper)  throws Exception {
		String cmd = cmds.getProperty(key);
		int thisTag = getTagInt(getTagString(key));
		return makeReplacement(cmd,cmds,lkup,ds,remapper,thisTag);
	}

	//Create the replacement for one element starting from a command.
	private static String makeReplacement(
			String cmd,
			Properties cmds,
			Properties lkup,
			Dataset ds,
			Remapper remapper,
			int thisTag)  throws Exception {
		String out = "";
		char c;
		int i = 0;
		boolean escape = false;
		while (i < cmd.length()) {
			c = cmd.charAt(i++);
			if (escape) {
				out += c;
				escape = false;
			}
			else if (c == escapeChar) escape = true;
			else if (c == functionChar) {
				FnCall fnCall = new FnCall(cmd.substring(i),cmds,lkup,ds,thisTag);
				if (fnCall.length == -1) break;
				i += fnCall.length;
				if (fnCall.name.equals(contentsFn)) 		out += contents(fnCall);
				else if (fnCall.name.equals(paramFn)) 		out += param(fnCall);
				else if (fnCall.name.equals(lookupFn)) 		out += lookup(fnCall);
				else if (fnCall.name.equals(initialsFn)) 	out += initials(fnCall);
				else if (fnCall.name.equals(scrambleFn)) 	out += scramble(fnCall);
				else if (fnCall.name.equals(alphabetichashFn)) out += alphabetichash(fnCall);
				else if (fnCall.name.equals(numerichashFn)) out += numerichash(fnCall);
				else if (fnCall.name.equals(hashuidFn)) 	out += hashuid(fnCall);
				else if (fnCall.name.equals(uidFn)) 		out += uid(fnCall,remapper);
				else if (fnCall.name.equals(ptidFn)) 		out += ptid(fnCall,remapper);
				else if (fnCall.name.equals(hashptidFn))	out += hashptid(fnCall);
				else if (fnCall.name.equals(hashFn))		out += hash(fnCall);
				else if (fnCall.name.equals(encryptFn))		out += encrypt(fnCall);
				else if (fnCall.name.equals(idFn)) 			out += id(fnCall,remapper);
				else if (fnCall.name.equals(integerFn)) 	out += integer(fnCall,remapper);
				else if (fnCall.name.equals(accessionFn))	out += accession(fnCall,remapper);
				else if (fnCall.name.equals(requireFn))		out += require(fnCall);
				else if (fnCall.name.equals(offsetdateFn))	out += offsetdate(fnCall,remapper);
				else if (fnCall.name.equals(incrementdateFn)) out += incrementdate(fnCall);
				else if (fnCall.name.equals(modifydateFn))	out += modifydate(fnCall);
				else if (fnCall.name.equals(roundFn))		out += round(fnCall);
				else if (fnCall.name.equals(timeFn)) 		out += time(fnCall);
				else if (fnCall.name.equals(dateFn)) 		out += date(fnCall);
				else if (fnCall.name.equals(ifFn))			out += iffn(fnCall,remapper);
				else if (fnCall.name.equals(quarantineFn))	throw new Exception("!quarantine!");
				else if (fnCall.name.equals(skipFn))		throw new Exception("!skip!");
				else out += functionChar + fnCall.getCall();
			}
			else out += c;
		}
		return out;
	}

	/**
	 * Encapsulate one function call, providing the parsing for the
	 * function name and the argument list.
	 */
	static class FnCall {

		/** the script. */
		public Properties cmds;
		/** the local lookup table. */
		public Properties lkup;
		/** the DICOM object dataset. */
		public Dataset ds;
		/** the function name. */
		public String name = "";
		/** the current element. */
		public int thisTag = 0;
		/** the decoded list of function arguments. */
		public String[] args = null;
		/** the script to be executed if the function generates true. */
		public String trueCode = "";
		/** the script to be executed if the function generates false. */
		public String falseCode = "";
		/** the length of the script text occupied by this function call. */
		public int length = -1;

	   /**
		 * Constructor. Decodes one function call.
		 * @param call the script of the function call.
		 * @param cmds the complete set of scripts.
		 * @param lkup the local lookup table.
		 * @param ds the DICOM object dataset.
		 */
		public FnCall(String call, Properties cmds, Properties lkup, Dataset ds, int thisTag) {
			this.cmds = cmds;
			this.lkup = lkup;
			this.ds = ds;
			this.thisTag = thisTag;

			//find the function name
			int k = call.indexOf("(");
			if (k == -1) length = -1;
			name = call.substring(0,k).replaceAll("\\s","");

			//now get the arguments
			int kk = k;
			LinkedList arglist = new LinkedList();
			while ((kk = getDelimiter(call, kk+1, ",)")) != -1) {
				arglist.add(call.substring(k+1,kk).trim());
				k = kk;
				if (call.charAt(kk) == ')') break;
			}
			//if there was no clean end to the arguments, bail out
			if (kk == -1) { length = -1; return; }

			//okay, we have arguments; save them
			args = new String[arglist.size()];
			arglist.toArray(args);
			length = kk + 1;

			//if this is an ifFn call, get the conditional code
			if (name.equals(ifFn)) {
				//get the true code
				if ( ((k = call.indexOf("{",kk+1)) == -1) ||
				     ((kk = getDelimiter(call,k+1,"}"))  == -1) ) {
					//this call is not coded correctly; return with
					//a length set to ignore the rest of the line
					length = call.length();
					return;
				}
				//the true code was present, save it
				trueCode = call.substring(k+1,kk);

				//now get the false code
				if ( ((k = call.indexOf("{",kk+1)) == -1) ||
				     ((kk = getDelimiter(call,k+1,"}"))  == -1) ) {
					//either this call is not coded correctly or there
					//is no false code; return with a length set to
					//ignore the rest of the line
					length = call.length();
					return;
				}
				//the false code was present, save it
				falseCode = call.substring(k+1,kk);
				length = kk + 1;
			}
		}

	   /**
		 * Get the tag corresponding to a tag name
		 * allowing for the this keyword.
		 * @param tagName the name of the tag.
		 * @return the tag.
		 */
		public int getTag(String tagName) {
			tagName = (tagName != null) ? tagName.trim() : "";
			if (tagName.equals("") || tagName.equals("this"))
				return thisTag;
			return Tags.forName(tagName);
		}

	   /**
		 * Get a specific argument.
		 * @param index the argument to get, counting from zero.
		 * @return the String value of the argument, or ""
		 * if the argument does not exist.
		 */
		public String getArg(int arg) {
			if (args == null) return "";
			if (arg >= args.length) return "";
			String argString = args[arg].trim();
			if (argString.startsWith("\"") && argString.endsWith("\""))
				argString = argString.substring(1,argString.length()-1);
			return argString;
		}

	   /**
		 * Regenerate the script of this function call, not including
		 * any conditional clauses.
		 * @return the function name and arguments.
		 */
		public String getCall() {
			return name + getArgs();
		}

	   /**
		 * Regenerate the list of arguments in this function call.
		 * @return the function arguments.
		 */
		public String getArgs() {
			String s = "";
			if (args != null) {
				for (int i=0; i<args.length; i++) {
					s += args[i];
					if (i != args.length-1) s += ",";
				}
			}
			return "(" + s + ")";
		}

	   /**
		 * Search a string for a delimiter, handling escape characters
		 * and double-quoted substrings.
		 * Note: this method only works for function parameter lists
		 * and un-nested if clauses. if statements within conditional
		 * clauses are not supported.
		 * @param s the string to search.
		 * @param k the index of the starting point in the string.
		 * @param delims the list of delimiter characters.
		 * @return the index of the delimiter.
		 */
		public int getDelimiter(String s, int k, String delims) {
			boolean inQuote = false;
			boolean inEscape = false;
			while (k < s.length()) {
				char c = s.charAt(k);
				if (inEscape) inEscape = false;
				else if (c == escapeChar) inEscape = true;
				else if (inQuote) {
					if (c == '"') inQuote = false;
				}
				else if (c == '"') inQuote = true;
				else if (delims.indexOf(c) != -1) return k;
				k++;
			}
			return -1;
		}
	}

	//Execute the if function call
	private static String iffn(FnCall fn, Remapper remapper) throws Exception {
		if (testCondition(fn))
			return makeReplacement(fn.trueCode,fn.cmds,fn.lkup,fn.ds,remapper,fn.thisTag);
		return makeReplacement(fn.falseCode,fn.cmds,fn.lkup,fn.ds,remapper,fn.thisTag);
	}

	//Determine whether a condition in an if statement is met
	private static boolean testCondition(FnCall fn) {
		if (fn.args.length < 2) return false;
		String tagName = fn.getArg(0);
		int tag = fn.getTag(tagName);
		String element = contents(fn.ds,tagName,tag);
		if (fn.args[1].equals("isblank")) {
			return (element == null) || element.trim().equals("");
		}
		else if (fn.args[1].equals("matches")) {
			if ((element == null) || (fn.args.length < 3)) return false;
			return element.matches(getArgument(fn.getArg(2)));
		}
		else if (fn.args[1].equals("exists")) {
			return fn.ds.contains(tag);
		}
		return false;
	}

	//Filter a quoted argument, removing the quotes
	private static String getArgument(String arg) {
		arg = arg.trim();
		if (arg.startsWith("\"") && arg.endsWith("\"")) {
			arg = arg.substring(1,arg.length()-1);
		}
		return arg;
	}

	//Execute the contents function call.
	//There are three possible calls:
	//   @contents(ElementName)
	//   @contents(ElementName,"regex")
	//   @contents(ElementName,"regex","replacement")
	private static String contents(FnCall fn) {
		String value = contents(fn.ds,fn.args[0],fn.thisTag);
		if (value == null) return null;
		if (fn.args.length == 1) return value;
		else if (fn.args.length == 2) return value.replaceAll(fn.getArg(1),"");
		else if (fn.args.length == 3) return value.replaceAll(fn.getArg(1),fn.getArg(2));
		return "";
	}

	//Get the contents of a dataset element by tagName
	private static String contents(Dataset ds, String tagName, int defaultTag) {
		String value = "";
		tagName = (tagName != null) ? tagName.trim() : "";
		if (!tagName.equals("")) {
			int tag = tagName.equals("this") ? defaultTag : Tags.forName(tagName);
			try {value = ds.getString(tag);}
			catch (Exception e) { };
		}
		if (value == null) value = "";
		return value;
	}

	//Execute the param function call
	private static String param(FnCall fn) {
		return getParam(fn.cmds,fn.args[0]);
	}

	//Get the value of a parameter identified by a function call argument
	private static String getParam(FnCall fn) {
		return getParam(fn.cmds,fn.args[0]);
	}

	//Get the value of a parameter from the script
	private static String getParam(Properties cmds, String param) {
		param = param.trim();
		if (!param.equals("") && (param.charAt(0) == functionChar)) {
			param = (String)cmds.getProperty("param." + param.substring(1));
		}
		return param;
	}

	//Execute the require function. This function checks whether the
	//specified element is present. If it is, it leaves the element alone;
	//otherwise, it inserts an element with the specified value.
	private static String require(FnCall fn) {
		//Return a @keep() call if the element is present
		if (fn.ds.contains(fn.thisTag)) return "@keep()";

		//The element was not present, return a value for a new element
		//If there are no arguments, return an empty string.
		if (fn.args.length == 0) return "";

		//There are some arguments, get the element tag
		//and see if it is in the dataset.
		String value = null;
		int tag = fn.getTag(fn.args[0]);
		if (fn.ds.contains(tag)) {
			//It is, get the value
			try { value = fn.ds.getString(tag); }
			catch (Exception e) { };
		}
		else {
			//It isn't; get a default value from the arguments
			//or an empty string if there is no default.
			value = (fn.args.length > 1) ? fn.getArg(1) : "";
		}
		//Convert an all-blank value to a blank function call
		//so that the element isn't removed when the replacements
		//are processed.
		if (value.trim().equals("")) value = "@blank("+value.length()+")";
		return value;
	}

	//Execute the lookup function. This function uses the value of an element
	//as an index into a local unencrypted lookup table and returns the result.
	//If the requested element is not present or the value of the element is not
	//a key in the lookup table, throw a quarantine exception.
	//The arguments are: ElementName, keyType
	//where keyType is the name of a specific key, which must appear in the lkup
	//Properties as keyType/value = replacement.
	//Values and replacements are trimmed before use.
	private static String lookup(FnCall fn) throws Exception {
		//Validate the input.
		if (fn.lkup == null) throw new Exception("!quarantine! missing lookup table.");
		if (fn.lkup.size() == 0) throw new Exception("!quarantine! empty lookup table.");
		String key = contents(fn.ds,fn.args[0],fn.thisTag);
		if (key == null) throw new Exception("!quarantine! lookup key missing in DICOM object.");
		key = key.trim();
		key = fn.args[1] + "/" + key;
		String value = fn.lkup.getProperty(key);
		if (value == null) throw new Exception("!quarantine! unable to find key ("+key+") in lookup table.");
		return value.trim();
	}

	//Execute the initials function. This function is typically used
	//to generate the initials of a patient from the contents of the
	//PatientName element.
	private static String initials(FnCall fn) {
		String s = contents(fn.ds,fn.args[0],fn.thisTag);
		if (s == null) return "x";
		s = s.replace(delimiterChar,' ');
		s = s.replaceAll("\\s+"," ").trim();
		if (s.equals("")) return "x";
		String ss = "";
		int i = 0;
		do {
			ss += s.charAt(i);
			i = s.indexOf(" ",i) + 1;
		} while (i != 0);
		//Now move the last name initial to the end
		if (ss.length() > 1) ss = ss.substring(1) + ss.charAt(0);
		return ss.toUpperCase();
	}

	//Execute the scramble function. This function is typically used
	//to generate a scrambled patient name from the contents of the
	//PatientName element.
	private static String scramble(FnCall fn) {
		String s = contents(fn.ds,fn.args[0],fn.thisTag);
		if (s == null) return "x";
		String[] words = s.split("\\^");
		String result = "";
		for (int i=0; i<words.length; i++) {
			if (fn.args.length < 2*i+3) break;
			String skip = getParam(fn.cmds,fn.args[2*i+1]);
			String take = getParam(fn.cmds,fn.args[2*i+2]);
			result += getSubstring(words[i],skip,take);
		}
		if (result.equals("")) return "x";
		return result.toUpperCase();
	}

	private static String getSubstring(String s, String skip, String take) {
		s = s.replaceAll("\\s","");
		skip = skip.replaceAll("\\s","");
		take = take.replaceAll("\\s","");
		int len = s.length();
		int kskip = 0;
		int ktake = 0;
		try {
			kskip = Integer.parseInt(skip);
			ktake = Integer.parseInt(take);
		}
		catch (Exception ex) { return ""; }
		if (kskip < 0) {
			kskip = len + kskip;
			if (kskip < 0) kskip = 0;
		}
		if (kskip + ktake <= len)
			return s.substring(kskip,kskip+ktake);
		else if (ktake <= len)
			return s.substring(len-ktake,len);
		return s;
	}

	//Execute the alphabetichash function. This function is typically used
	//to generate a scrambled patient name from the contents of the
	//PatientName element.
	private static String alphabetichash(FnCall fn) {
		try {
			String string = contents(fn.ds,fn.args[0],fn.thisTag);

			String lengthString = getParam(fn.cmds,fn.args[1]);
			int length;
			try { length = Integer.parseInt(lengthString); }
			catch (Exception ex) { length = 4; }

			if (fn.args.length > 2) {
				int wordCount;
				String wordCountString = getParam(fn.cmds,fn.args[2]);
				try { wordCount = Integer.parseInt(wordCountString); }
				catch (Exception ex) { wordCount = Integer.MAX_VALUE; }
				if (wordCount > 0) {
					String[] words = string.split("\\^");
					string = "";
					for (int i=0; i<words.length && i<wordCount; i++) {
						string += words[i];
					}
				}
			}
			string = string.replaceAll("[\\s,'\\^\\.]","").toUpperCase();
			MessageDigest messageDigest = MessageDigest.getInstance("SHA");
			byte[] hashed = messageDigest.digest(string.getBytes("UTF-8"));
			BASE64Encoder b64Encoder = new BASE64Encoder();
			String result = b64Encoder.encode(hashed);
			result = result.replaceAll("[/+=\\d]","").toUpperCase();
			return result.substring(Math.max(0,result.length()-length),result.length());
		}
		catch (Exception ex) {
			logger.warn("Exception in hash"+fn.getArgs()+": "+ex.getMessage());
			return fn.getArgs();
		}
	}

	//Execute the numerichash function. This function is typically used
	//to generate a numeric value from the contents of the PatientName element.
	private static String numerichash(FnCall fn) {
		try {
			String string = contents(fn.ds,fn.args[0],fn.thisTag);

			String lengthString = getParam(fn.cmds,fn.args[1]);
			int length;
			try { length = Integer.parseInt(lengthString); }
			catch (Exception ex) { length = 4; }

			if (fn.args.length > 2) {
				int wordCount;
				String wordCountString = getParam(fn.cmds,fn.args[2]);
				try { wordCount = Integer.parseInt(wordCountString); }
				catch (Exception ex) { wordCount = Integer.MAX_VALUE; }
				if (wordCount > 0) {
					String[] words = string.split("\\^");
					string = "";
					for (int i=0; i<words.length && i<wordCount; i++) {
						string += words[i];
					}
				}
			}
			string = string.replaceAll("[\\s,'\\^\\.]","").toUpperCase();
			MessageDigest messageDigest = MessageDigest.getInstance("SHA");
			byte[] hashed = messageDigest.digest(string.getBytes("UTF-8"));
			BigInteger bi = new BigInteger(hashed);
			String result = bi.toString();
			return result.substring(Math.max(0,result.length()-length),result.length());
		}
		catch (Exception ex) {
			logger.warn("Exception in numerichash"+fn.getArgs()+": "+ex.getMessage());
			return fn.getArgs();
		}
	}

	//Execute the round function call. This function is used to
	//round an age field into groups of a given size.
	private static String round(FnCall fn) {
		//arg must contain: AgeElementName, groupSize
		try {
			String ageString = contents(fn.ds,fn.args[0],fn.thisTag);
			String sizeString = getParam(fn.cmds,fn.args[1]);
			int size = Integer.parseInt(sizeString);
			if (ageString == null) return "";
			ageString = ageString.trim();
			if (ageString.length() == 0) return "";
			float ageFloat = Float.parseFloat(ageString.replaceAll("\\D",""));
			ageFloat /= (float)size;
			int age = Math.round(ageFloat);
			age *= size;
			String result = age + ageString.replaceAll("\\d","");
			if ((result.length() & 1) != 0) result = "0" + result;
			return result;
		}
		catch (Exception e) {
			logger.warn("Exception caught in id"+fn.getArgs()+": "+e.getMessage());
			return fn.getArgs();
		}
	}

	//Execute the ptid function call. This function is used
	//to re-identify a patient.
	private static String ptid(FnCall fn, Remapper remapper) {
		//args must contain: siteid, elementname, prefix, first, width, suffix
		try {
			//Check the arguments. If too few, just return the arg list.
			//Allow the last argument (the suffix) to be missing.
			if (fn.args.length < 5) return fn.getArgs();
			String siteid = getParam(fn.cmds,fn.args[0]);
			String ptid = contents(fn.ds,fn.args[1],fn.thisTag);
			if (ptid == null) ptid = "null";
			String prefix = getParam(fn.cmds,fn.args[2]);
			int first = getInt(fn.args[3]);
			int width = getInt(fn.args[4]);
			String suffix = "";
			if (fn.args.length >= 6) suffix = getParam(fn.cmds,fn.args[5]);
			int seqid = remapper.getCount();
			remapper.getPtID(seqid,siteid,ptid,prefix,first,width,suffix);
			return "@rv["+seqid+"]";

		}
		catch (Exception e) {
			logger.warn("Exception caught in ptid"+fn.getArgs()+": "+e.getMessage());
			return fn.getArgs();
		}
	}

	//Execute the hashptid function call. This function is used
	//to re-identify a patient without making a call to the IdTable.
	private static String hashptid(FnCall fn) {
		//args must contain: siteid, elementname, prefix, suffix
		try {
			//Check the arguments. If too few, just return the arg list.
			//Allow the last argument (the suffix) to be missing.
			if (fn.args.length < 2) return fn.getArgs();
			String siteid = getParam(fn.cmds,fn.args[0]);
			String ptid = contents(fn.ds,fn.args[1],fn.thisTag);
			if (ptid == null) ptid = "null";
			String prefix = "";
			if (fn.args.length >= 3) prefix = getParam(fn.cmds,fn.args[2]);
			String suffix = "";
			if (fn.args.length >= 4) suffix = getParam(fn.cmds,fn.args[3]);
			return prefix + getUSMD5("[" + siteid + "]" + ptid) + suffix;
		}
		catch (Exception e) {
			logger.warn("Exception caught in hashptid"+fn.getArgs()+": "+e.getMessage());
			return fn.getArgs();
		}
	}

	//Execute the hash function call. This function is used
	//to generate an MD5 hash of any element text, with the
	//result being a base-10 digit string.
	private static String hash(FnCall fn) {
		try {
			String value = contents(fn.ds,fn.args[0],fn.thisTag);
			if (value == null) value = "null";
			return getUSMD5(value);
		}
		catch (Exception e) {
			logger.warn("Exception caught in hash"+fn.getArgs()+": "+e.getMessage());
			return fn.getArgs();
		}
	}

	//Execute the encrypt function call. This function is used
	//to generate an encrypted string from an element text value.
	private static String encrypt(FnCall fn) {
		if (fn.args.length < 2) return fn.getArgs();
		try {
			String value = contents(fn.ds,fn.args[0],fn.thisTag);
			if (value == null) value = "null";
			String key = getParam(fn.cmds,fn.args[1]);
			Cipher enCipher = getCipher(key);
			byte[] encrypted = enCipher.doFinal(value.getBytes("UTF-8"));
			return b64Encoder.encode(encrypted);
		}
		catch (Exception e) {
			logger.warn("Exception caught in encipher"+fn.getArgs()+": "+e.getMessage());
			return fn.getArgs();
		}
	}

	//Get a Cipher initialized with the specified key.
	private static Cipher getCipher(String keyText) {
		try {
			Provider sunJce = new com.sun.crypto.provider.SunJCE();
			Security.addProvider(sunJce);
			byte[] key = getEncryptionKey(keyText,128);
			SecretKeySpec skeySpec = new SecretKeySpec(key,"Blowfish");

			SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
			byte[] seed = random.generateSeed(8);
			random.setSeed(seed);

			Cipher enCipher = Cipher.getInstance("Blowfish");
			enCipher.init(Cipher.ENCRYPT_MODE, skeySpec, random);
			return enCipher;
		}
		catch (Exception ex) {
			logger.error("Unable to initialize the Cipher using \""+keyText+"\"",ex);
			return null;
		}
	}

	static String nonce = "tszyihnnphlyeaglle";
	static String pad = "===";
	private static byte[] getEncryptionKey(String keyText, int size) throws Exception {
		if (keyText == null) keyText = "";
		keyText = keyText.trim();

		//Now make it into a base-64 string encoding the right number of bits.
		keyText = keyText.replaceAll("[^a-zA-Z0-9+/]","");

		//Figure out the number of characters we need.
		int requiredChars = (size + 5) / 6;
		int requiredGroups = (requiredChars + 3) / 4;
		int requiredGroupChars = 4 * requiredGroups;

		//If we didn't get enough characters, then throw some junk on the end.
		while (keyText.length() < requiredChars) keyText += nonce;

		//Take just the right number of characters we need for the size.
		keyText = keyText.substring(0,requiredChars);

		//And return the string padded to a full group.
		keyText = (keyText + pad).substring(0,requiredGroupChars);
		return b64Decoder.decodeBuffer(keyText);
	}

	//Execute the id function call. This function is used to
	//generate a new globally unique value specific to an element.
	//Values are generated starting with 1.
	private static String accession(FnCall fn, Remapper remapper) {
		//arg must contain: elementname
		try {
			int tag = fn.getTag(fn.args[0]);
			String tagString = Tags.toString(tag).toLowerCase();
			String elementValue = contents(fn.ds,fn.args[0],fn.thisTag);
			if (elementValue == null) elementValue = "null";
			int seqid = remapper.getCount();
			remapper.getAccessionNumber(seqid,tagString,elementValue);
			return "@rv["+seqid+"]";
		}
		catch (Exception e) {
			logger.warn("Exception caught in accession"+fn.getArgs()+": "+e.getMessage());
			return fn.getArgs();
		}
	}

	//Execute the integer function call. This function is used to
	//get a unique integer value starting with 1.
	private static String integer(FnCall fn, Remapper remapper) {
		try {
			int seqid = remapper.getCount();
			remapper.getInteger(seqid);
			return "@rv["+seqid+"]";
		}
		catch (Exception e) {
			logger.warn("Exception caught in integer"+fn.getArgs()+": "+e.getMessage());
			return fn.getArgs();
		}
	}

	//Execute the id function call. This function is used to
	//generate a new generic ID specific to the element
	//whose ID is being de-identified. IDs are generated
	//starting with 1.
	private static String id(FnCall fn, Remapper remapper) {
		//arg must contain: elementname
		try {
			int tag = fn.getTag(fn.args[0]);
			String tagString = Tags.toString(tag).toLowerCase();
			String elementValue = contents(fn.ds,fn.args[0],fn.thisTag);
			if (elementValue == null) elementValue = "null";
			int seqid = remapper.getCount();
			remapper.getGenericID(seqid,tagString,elementValue);
			return "@rv["+seqid+"]";
		}
		catch (Exception e) {
			logger.warn("Exception caught in id"+fn.getArgs()+": "+e.getMessage());
			return fn.getArgs();
		}
	}

	private static int getInt(String s) {
		int n = 1;
		try { n = Integer.parseInt(s.trim()); }
		catch (Exception ignore) { }
		return n;
	}

	//Execute the incrementdate function call.
	//Get a new date by adding a constant to an date value.
	private static String incrementdate(FnCall fn) {
		//arg must contain: DateElementName, increment
		//DateElementName is the name of the element containing the date to be incremented.
		//increment specifies the number of days to add to the date (positive generates
		//later dates; negative generates earlier dates.
		String removeDate = "@remove()";
		try {
			String date = contents(fn.ds,fn.args[0],fn.thisTag);
			String incString = getParam(fn.cmds,fn.args[1]);
			long inc = Long.parseLong(incString);
			inc *= 24 * 3600 * 1000;
			GregorianCalendar dateCal = getCal(date);
			dateCal.setTimeInMillis(dateCal.getTimeInMillis() + inc);
			String newDate = intToString(dateCal.get(Calendar.YEAR), 4) +
							 intToString(dateCal.get(Calendar.MONTH) + 1, 2) +
							 intToString(dateCal.get(Calendar.DAY_OF_MONTH), 2);
			return newDate;
		}
		catch (Exception e) {
			logger.warn("Exception caught in incrementdate"+fn.getArgs()+": "+e.getMessage());
			return removeDate;
		}
	}

	//Execute the modifydate function call.
	//Get a new date by modifying the current one.
	private static String modifydate(FnCall fn) {
		//arg must contain: DateElementName, year, month, day
		//DateElementName is the name of the element containing the date to be incremented.
		//year specifies the value of the year (* means to keep the current value).
		//month specifies the value of the month (* means to keep the current value).
		//day specifies the value of the day of the month (* means to keep the current value).
		String removeDate = "@remove()";
		try {
			String date = contents(fn.ds,fn.args[0],fn.thisTag);
			int y = getReplacementValue(getParam(fn.cmds,fn.args[1]).trim());
			int m = getReplacementValue(getParam(fn.cmds,fn.args[2]).trim());
			int d = getReplacementValue(getParam(fn.cmds,fn.args[3]).trim());
			GregorianCalendar dateCal = getCal(date);

			if (y < 0) y = dateCal.get(Calendar.YEAR);

			if (m < 0) m = dateCal.get(Calendar.MONTH);
			else m--;

			if (d < 0) d = dateCal.get(Calendar.DAY_OF_MONTH);

			dateCal.set(y, m, d);

			return  intToString(dateCal.get(Calendar.YEAR), 4) +
					intToString(dateCal.get(Calendar.MONTH) + 1, 2) +
					intToString(dateCal.get(Calendar.DAY_OF_MONTH), 2);
		}
		catch (Exception e) {
			logger.warn("Exception caught in modifydate"+fn.getArgs()+": "+e.getMessage());
			return removeDate;
		}
	}

	private static int getReplacementValue(String s) {
		try { return Integer.parseInt(s); }
		catch (Exception ex) { return -1; }
	}

	//Get a GregorianCalendar for a specific date.
	private static GregorianCalendar getCal(String date) throws Exception {
		//do a little filtering to protect against the most common booboos
		date = date.replaceAll("\\D","");
		if (date.length() != 8) throw new Exception("Illegal date: "+date);
		if (date.startsWith("00")) date = "19" + date.substring(2);
		//now make the calendar
		int year = Integer.parseInt(date.substring(0,4));
		int month = Integer.parseInt(date.substring(4,6));
		int day = Integer.parseInt(date.substring(6,8));
		return new GregorianCalendar(year,month-1,day);
	}

	//Execute the offsetdate function call.
	//Get a new date as an offset from the first date for
	//this patient/element and offset it from a specified base.
	private static String offsetdate(FnCall fn, Remapper remapper) {
		//arg must contain: siteid, DateElementName, basedate
		//DateElementName is the name of the element containing the date to be offset.
		//basedate specifies the base date for the first date for this patient.
		//basedate must be in the form yyyymmdd
		String defaultDate = "20000101";
		String removeDate = "@remove()";
		try {
			if (fn.args.length != 3) {
				logger.warn("DicomAnonymizer offsetdate call with improper arguments "+fn.getArgs()+"");
				return defaultDate;
			}
			String siteid = getParam(fn.cmds,fn.args[0]);
			String ptid = contents(fn.ds,"PatientID",0x00100020);
			String base = getParam(fn.cmds,fn.args[2]);

			int tag = fn.getTag(fn.args[1]);
			String tagString = Tags.toString(tag).toLowerCase();
			String date = contents(fn.ds,fn.args[1],fn.thisTag);

			//if the date is not in the file, send back a
			//remove call so that this element will be removed
			if (date == null) return removeDate;
			//make a basic test of the date and send back a remove call if it's bad
			if ((base.length() != 8) || (base.replaceAll("\\d","").length() != 0)) {
				logger.warn("DicomAnonymizer input data error: Illegal basedate format in offsetdate");
				return defaultDate;
			}
			int seqid = remapper.getCount();
			remapper.getOffsetDate(seqid,siteid,ptid,tagString,date,base);
			return "@rv["+seqid+"]";
		}
		catch (Exception e) {
			logger.warn("Exception caught in offsetdate"+fn.getArgs()+": "+e.getMessage(),e);
			return removeDate;
		}
	}

	//Execute the hashuid function call. Generate a new uid
	//from a prefix and an old uid. The old uid is hashed and
	//appended to the prefix.
	private static String hashuid(FnCall fn) {
		String removeUID = "@remove()";
		try {
			if (fn.args.length != 2) return fn.getArgs();
			String prefix = getParam(fn);
			String uid = contents(fn.ds,fn.args[1],fn.thisTag);
			//If there is no UID in the dataset, then return @remove().
			if (uid == null) return removeUID;
			//Make sure the prefix ends in a period
			if (!prefix.endsWith(".")) prefix += ".";
			//Create the replacement UID
			String hashString = getUSMD5(uid);
			String extra = hashString.startsWith("0") ? "9" : "";
			String hash = prefix + extra + hashString;
			if (hash.length() > 64) hash = hash.substring(0,64);
			return hash;
		}
		catch (Exception e) {
			logger.warn("Exception caught in hashuid"+fn.getArgs()+": "+e.getMessage());
			return fn.getArgs();
		}
	}

	private static String getUSMD5(String string) throws Exception {
		MessageDigest messageDigest = MessageDigest.getInstance("MD5");
		byte[] hashed = messageDigest.digest(string.getBytes("UTF-8"));
		BigInteger bi = new BigInteger(1,hashed);
		return bi.toString();
	}

	//Execute the uid function call. Generate a new uid
	//from a prefix and an old uid. The old uid is looked
	//up to see if it has been seen before. If so, the
	//transformed value is returned. Otherwise, a new one
	//is created from the prefix and an incrementing number.
	private static String uid(FnCall fn, Remapper remapper) {
		String removeUID = "@remove()";
		try {
			if (fn.args.length != 2) return fn.getArgs();
			String prefix = getParam(fn);
			String uid = contents(fn.ds,fn.args[1],fn.thisTag);
			//If there is no UID in the dataset, then return @remove().
			if (uid == null) return removeUID;
			//Make sure the prefix ends in a period
			if (!prefix.endsWith(".")) prefix += ".";
			//Get the uid from the IdTable
			int seqid = remapper.getCount();
			remapper.getUID(seqid,prefix,uid);
			return "@rv["+seqid+"]";
		}
		catch (Exception e) {
			logger.warn("Exception caught in uid"+fn.getArgs()+": "+e.getMessage());
			return fn.getArgs();
		}
	}

	private static String time(FnCall fnCall) {
		Calendar now = Calendar.getInstance();
		return intToString(now.get(Calendar.HOUR_OF_DAY), 2)
				 + fnCall.getArg(0)
				 + intToString(now.get(Calendar.MINUTE), 2)
				 + fnCall.getArg(0)
				 + intToString(now.get(Calendar.SECOND), 2);
	}

	private static String date(FnCall fnCall) {
		Calendar now = Calendar.getInstance();
		return intToString(now.get(Calendar.YEAR), 4)
				 + fnCall.getArg(0)
				 + intToString(now.get(Calendar.MONTH) + 1, 2)
				 + fnCall.getArg(0)
				 + intToString(now.get(Calendar.DAY_OF_MONTH), 2);
	}

	private static String intToString(int n, int digits) {
		String s = Integer.toString(n);
		int k = digits - s.length();
		for (int i=0; i<k; i++) s = "0" + s;
		return s;
	}

	//Insert the values from a remapping hashtable into the replacement
	//hashtable. The places to do the insertions are indicated by
	//"@rv[n]", where n = the id of the value to insert.
	private static Properties insertRemappedValues(
			Properties theReplacements,
			Hashtable<String, String> remappedValues) {
		Pattern pattern = Pattern.compile("@rv\\[\\d+\\]");
		Enumeration keys = theReplacements.keys();
		while (keys.hasMoreElements()) {
			String key = (String)keys.nextElement();
			String value = (String)theReplacements.getProperty(key);
			Matcher matcher = pattern.matcher(value);
			boolean found = false;
			while (matcher.find(0)) {
				String rvIndex = value.substring(matcher.start()+4,matcher.end()-1);
				String rv = remappedValues.get(rvIndex);
				if (rv == null) rv = "ERROR";
				value = matcher.replaceFirst(rv);
				matcher.reset(value);
				found = true;
			}
			if (found) theReplacements.setProperty(key,value);
		}
		return theReplacements;
	}

	//Remove and modify elements in the dataset.
	private static String doOverwrite(
			Dataset ds,
			Properties theReplacements,
				// These global flags deal with groups of elements.
				// If anything appears in theReplacements for an element,
				// it overrides the action of the global flags.
				// A global keep flag overrides a global remove flag.
			boolean rpg,	//remove private groups
			boolean rue,	//remove unspecified elements
			boolean rol,	//remove overlay groups
			int[] keepGroups
			) throws Exception {
		String exceptions = "";
		String name;
		String value;

		//If we are removing anything globally, then go through the dataset
		//and look at each element individually.
		if (rpg || rue || rol) {
			//Make a list of the elements to remove
			LinkedList list = new LinkedList();
			for (Iterator it=ds.iterator(); it.hasNext(); ) {
				DcmElement el = (DcmElement)it.next();
				int tag = el.tag();
				int group = tag & 0xFFFF0000;
				boolean overlay = ((group & 0xFF000000) == 0x60000000);
				if (rpg && ((tag & 0x10000) != 0)) {
					if (theReplacements.getProperty(Tags.toString(tag).toLowerCase()) == null) {
						if (Arrays.binarySearch(keepGroups,group) < 0) {
							list.add(new Integer(tag));
						}
					}
				}
				if (rue) {
					if (theReplacements.getProperty(Tags.toString(tag).toLowerCase()) == null) {
						boolean keep  = (Arrays.binarySearch(keepGroups,group) >= 0) ||
									    (tag == 0x00080016)   || 	//SopClassUID
										(tag == 0x00080018)   || 	//SopInstanceUID
										(tag == 0x0020000D)   ||	//StudyInstanceUID
										(group == 0x00020000) ||	//FMI group
										(group == 0x00280000) ||	//the image description
										(group == 0x7FE00000) ||	//the image
										(overlay && !rol);
						if (!keep) list.add(new Integer(tag));
					}
				}
				if (rol && overlay) list.add(new Integer(tag));
			}
			//Okay, now remove them
			Iterator it = list.iterator();
			while (it.hasNext()) {
				Integer tagInteger = (Integer)it.next();
				int tag = tagInteger.intValue();
				try { ds.remove(tag); }
				catch (Exception ignore) { }
			}
		}

		//Now go through theReplacements and handle the instructions there
		for (Enumeration it = theReplacements.keys(); it.hasMoreElements(); ) {
			String key = (String) it.nextElement();
			int tag = getTagInt(key);
			value = (String)theReplacements.getProperty(key);
			value = (value != null) ? value.trim() : "";
			if (value.equals("") || (value.indexOf("@remove()") != -1)) {
				try { ds.remove(tag); }
				catch (Exception ignore) { }
			}
			else if (value.equals("@keep()")) ; //@keep() leaves the element in place
			else if (value.startsWith("@blank(")) {
				//@blank(n) inserts an element with n blank chars
				String blanks = "                                                       ";
				String nString = value.substring("@blank(".length());
				int paren = nString.indexOf(")");
				int n = 0;
				if (paren != -1) {
					nString = "0" + nString.substring(0,paren).replaceAll("\\D","");
					n = Integer.parseInt(nString);
				}
				if (n > blanks.length()) n = blanks.length();
				try { putXX(ds,tag,getVR(tag),blanks.substring(0,n)); }
				catch (Exception e) {
					logger.warn(key + " exception: " + e.toString());
					if (!exceptions.equals("")) exceptions += ", ";
					exceptions += key;
				}
			}
			else {
				try {
					if (value.equals("@empty()")) value = "";
					putXX(ds,tag,getVR(tag),value);
				}
				catch (Exception e) {
					logger.warn(key + " exception:\n" + e.toString()
								+ "\ntag=" + Integer.toHexString(tag)
								+ ": value= \"" + value + "\"");
					if (!exceptions.equals("")) exceptions += ", ";
					exceptions += key;
				}
			}
		}
		return exceptions;
	}

	private static int getVR(int tag) {
		TagDictionary.Entry entry = tagDictionary.lookup(tag);
		try { return VRs.valueOf(entry.vr); }
		catch (Exception ex) { return VRs.valueOf("SH"); }
	}

	//This method works around the bug in dcm4che which inserts the wrong
	//VR (SH) when storing an empty element of VR = PN. It also handles the
	//problem in older dcm4che versions which threw an exception when an
	//empty DA element was created. And finally, it forces the VR of private
	//elements to UT.
	private static void putXX(Dataset ds, int tag, int vr, String value) throws Exception {
		if ((value == null) || value.equals("")) {
			if (vr == VRs.PN) ds.putXX(tag,vr," ");
			else ds.putXX(tag,vr);
		}
		else if ((tag&0x10000) != 0) ds.putUT(tag,value);
		else ds.putXX(tag,vr,value);
	}
}
