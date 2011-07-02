/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.dicomeditor;

import java.io.*;
import java.util.*;
import org.apache.log4j.*;
import org.rsna.mircsite.anonymizer.DicomAnonymizer;
import org.rsna.mircsite.anonymizer.IdTable;
import org.rsna.mircsite.anonymizer.LocalRemapper;
import org.rsna.util.ApplicationProperties;
import org.rsna.util.FileUtil;
import org.rsna.util.Key;

/**
 * The CmdLineAnonymizer program provides a command line
 * DICOM anonymizer that processes a single file. This
 * program is built in the same package as DicomEditor and
 * is installed in the same directory with it by the
 * DicomEditor-installer.jar program. It uses the same
 * library jars as DicomEditor.
 */
public class CmdLineAnonymizer {

    private ApplicationProperties properties;

    public static String idtablepropfile = "idtable.properties";
    public static String dicomScriptFile = "dicom-anonymizer.properties";
    public static String lookupTableFile = "lookup-table.properties";
	static final Logger logger = Logger.getLogger(CmdLineAnonymizer.class);

	/**
	 * The main method to start the program.
	 * <p>
	 * If the args array contains two or more parameters, the program
	 * attempts to open the second parameter as a DICOM object and anonymize it.
	 * The command line parameters must be:
	 * <ol>
	 * <li>the path to the DicomEditor directory</li>
	 * <li>the path to the DICOM object to be anonymized</li>
	 * <li>(optional) -ivrle if the VR is to be forced to implicit VR little endian</li>
	 * <li>(optional) -rename if the anonymized file is to be renamed as done by DicomEditor</li>
	 * <li>(optional) -sopiuid if the anonymized file is to be renamed with the SOP Instance UID
	 * of the anonymized object (note: for this option to be activated, 'rename must also be
	 * specified</li>
	 * </ol>
	 * <p>Note: the program needs the path to its directory because user.dir might
	 * not point to that directory if the program is started from somewhere else.
	 * @param args the list of arguments from the command line.
	 */
    public static void main(String args[]) {
		Logger.getRootLogger().addAppender(
				new ConsoleAppender(
					new PatternLayout("%d{HH:mm:ss} %-5p [%c{1}] %m%n")));
		Logger.getRootLogger().setLevel(Level.WARN);
        if (args.length >= 2) {
			//Set up the path strings correctly
			//for the properties files.
			//args[0] = the DicomEditor directory
			File programDir = new File(args[0]);
			idtablepropfile = (new File(programDir,idtablepropfile)).getAbsolutePath();
			dicomScriptFile = (new File(programDir,dicomScriptFile)).getAbsolutePath();
			lookupTableFile = (new File(programDir,lookupTableFile)).getAbsolutePath();

			//Get the file to be anonymized and
			//force it to have an absolute path.
			File inFile = new File(args[1]);
			inFile = new File(inFile.getAbsolutePath());

			//Get the switches
			String switches = "";
			for (int i=2; i<args.length; i++) switches += args[i];
			boolean ivrle = (switches.indexOf("-i") != -1);
			boolean rename = (switches.indexOf("-r") != -1);
			boolean sopiuid = (switches.indexOf("-s") != -1);

			//Initialize the IdTable
			if (!IdTable.initialize(idtablepropfile)) {
				System.out.println("The default key does not match the IdTable.");
				System.exit(0);
			}
			//Disable storeLater because we will call storeNow at the end.
			IdTable.setStoreLaterEnable(false);

			//anonymize the file
			anonymize(inFile,ivrle,rename,sopiuid);

			//Save the IdTable
			IdTable.storeNow(false); //only store if the table is dirty
		}
		else {
			//Wrong number of arguments, just display some help.
			printHelp();
		}
    }

	// Anonymize the selected file(s).
	private static void anonymize(File inFile, boolean ivrle, boolean rename, boolean sopiuid) {
		if (!inFile.exists()) {
			System.out.println(inFile.getAbsolutePath()+" does not exist");
			return;
		}
		if (inFile.isFile()) {
			File outFile = inFile;
			if (rename) {
				String name = inFile.getName();
				int k = name.length();
				if (!name.matches("[\\d\\.]+")) {
					k = name.lastIndexOf(".");
					if (k == -1) k = name.length();
					if (name.substring(0,k).endsWith("-no-phi")) return;
				}
				name = name.substring(0,k) + "-no-phi" + name.substring(k);
				outFile = new File(outFile.getParentFile(),name);
			}
			System.out.println("Anonymizing: "+inFile.getAbsolutePath());

			//Get the anonymizer script
			ApplicationProperties dicomScript = new ApplicationProperties(dicomScriptFile);
			if (dicomScript == null) {
				System.out.println("Unable to load "+dicomScriptFile);
				return;
			}

			//Get the local lookup table, if any
			ApplicationProperties lookupTable = new ApplicationProperties(lookupTableFile);

			String result =
				DicomAnonymizer.anonymize(
					inFile,outFile,
					dicomScript, lookupTable,
					new LocalRemapper(),ivrle, rename && sopiuid );

			//Report the results
			if (result.equals(""))
				System.out.println("OK");
			else
				System.out.println("Exceptions:\n" + result);
			return;
		}
		else System.out.println("The file is not a data file.");
	}

	//Some help text
	private static void printHelp() {
		System.out.println(
			"\nUsage:\n" +
			"------\n\n" +
			"java -jar programdir/da.jar programdir dicomfile switches\n\n" +
			"where:\n" +
			"   programdir = the path to the DicomEditor directory\n" +
			"   dicomfile = the path to the file to be anonymized\n" +
			"   switches:\n" +
			"      -ivrle (or just -i) forces the VR to IVRLE\n" +
			"      -rename (or just -r) renames the anonymized file\n" +
			"      -sopiuid (or just -s) uses the SOPInstanceUID as the name\n\n" +
			"Notes:\n" +
			"   If -r is used without -s, \"name.ext\" is anonymized to \"name-no-phi.ext\".\n" +
			"   If -r-s is used, the anonymized file is named SOPInstanceUID.dcm, where\n" +
			"      SOPInstanceUID is the value from the anonymized object.\n" +
			"   To use the -s switch, the -r switch is required.\n" +
			"   Important: this program supports only local remapping.\n\n" +
			"Example:\n" +
			"   java -jar /bin/DicomEditor/da.jar /bin/DicomEditor /path/filename.dcm -i-r-s\n"
		);

	}

}
