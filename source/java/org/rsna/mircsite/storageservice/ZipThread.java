/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.storageservice;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.log4j.Logger;

import org.rsna.mircsite.anonymizer.*;
import org.rsna.mircsite.dicomservice.TrialConfig;
import org.rsna.mircsite.util.DicomObject;
import org.rsna.mircsite.util.FileObject;
import org.rsna.mircsite.util.FileUtil;
import org.rsna.mircsite.util.MircImage;
import org.rsna.mircsite.util.StringUtil;
import org.rsna.mircsite.util.TypeChecker;
import org.rsna.mircsite.util.XmlStringUtil;
import org.rsna.mircsite.util.XmlUtil;

/**
 * The Thread that processes files submitted to the Zip Service.
 */
public class ZipThread extends Thread {

	static final Logger logger = Logger.getLogger(ZipThread.class);

	String name;
	String affiliation;
	String contact;
	boolean publish;
	File template;
	String username;
	String read;
	String update;
	String export;
	File docsDir;
	File workingDir;
	File submission;
	String textext;
	String skipext;
	String skipprefix;
	String[] textExtensions;
	String[] skipExtensions;
	String[] skipPrefixes;
	boolean overwriteTemplate;
	boolean anonymize;
	TypeChecker checker;

	File root;
	Filter dirsOnly;
	Filter filesOnly;
	String baseDirName;
	int docCount = 0;

	Properties dicomAnonymizerProperties = null;
	Properties lookupTableProperties = null;

	/**
	 * Create a new ZipThread.
	 * @param name the author's name.
	 * @param affiliation the author's affiliation.
	 * @param contact the author's contact information.
	 * @param publish true if the user is a publisher; false otherwise.
	 * @param template the default template file to be used unless overridden by the submission.
	 * @param username the username of the owner to be assigned to the document.
	 * @param read the read privileges to be assigned to the document
	 * @param update the update privileges to be assigned to the document
	 * @param export the export privileges to be assigned to the document
	 * @param docsDir the storage service's documents directory, used as the root of the created documents.
	 * @param workingDir the working directory for unpacking the submission.
	 * @param submission the zip file containing the submission.
	 * @param overwriteTemplate true if supplied parameters are
	 * to overwrite the values in the template; false if the template
	 * parameters are not to be overwritten.
	 * @param anonymize true if DicomObjects are to be anonymized.
	 * @throws Exception if the submission could not be unpacked or the default template file is missing.
	 */
	public ZipThread(
				String name,
				String affiliation,
				String contact,
				boolean publish,
				File template,
				String username,
				String read,
				String update,
				String export,
				File docsDir,
				File workingDir,
				File submission,
				String textext,
				String skipext,
				String skipprefix,
				boolean overwriteTemplate,
				boolean anonymize) throws Exception {

		this.name = fix(name);
		this.affiliation = fix(affiliation);
		this.contact = fix(contact);
		this.publish = publish;
		this.template = template;
		this.username = fix(username);
		this.read = fix(read);
		this.update = fix(update);
		this.export = fix(export);
		this.docsDir = docsDir;
		this.workingDir = workingDir;
		this.submission = submission;
		this.textext = fix(textext);
		this.skipext = fix(skipext);
		this.skipprefix = fix(skipprefix);
		this.overwriteTemplate = overwriteTemplate;
		this.anonymize = anonymize;

		this.textext = this.textext.replaceAll("\\s","");
		textExtensions = trim(this.textext.split(","));
		skipExtensions = trim(this.skipext.split(","));
		skipPrefixes = trim(this.skipprefix.split(","));
		dirsOnly = new Filter(true);
		filesOnly = new Filter(false, skipExtensions);
		if (!template.exists())
			throw new Exception("The Zip Service's default template does not exist.");
		if (!isValidTemplate(template))
			throw new Exception("The Zip Service's default template is not valid.");

		root = new File(workingDir,"root");
		root.mkdirs();

		unpackZipFile(root, submission);

		submission.delete();
		baseDirName = StringUtil.makeNameFromDate();
		checker = new TypeChecker();

		if (anonymize) initializeAnonymizer();
	}

	private String fix(String s) {
		return (s != null) ? s : "";
	}

	private String[] trim(String[] s) {
		if (s != null) {
			for (int i=0; i<s.length; i++) s[i] = s[i].trim();
		}
		return s;
	}

	public void run() {
		processDirectory(root, template, "", "");
		FileUtil.deleteAll(workingDir);
	}

	private void processDirectory(File dir, File template, String title, String keywords) {

		//Make sure this directory should be processed
		if (skipDirectory(dir)) return;

		//Make sure the title is legal XML
		title = XmlStringUtil.makeFilteredString(title);
		if (title.trim().equals("")) title = "Untitled";

		//If there is a valid template, use it for this directory and
		//the rest of the directories on this branch.
		File newTemplate = new File(dir,"template.xml");
		if (newTemplate.exists() && isValidTemplate(newTemplate))
			template = newTemplate;

		//Make a MIRCdocument out of any other files in this directory.
		File[] files = dir.listFiles(filesOnly);
		Arrays.sort(files);

		if (files.length > 0) createMircDocument(files,template,title,keywords);

		//Process any child directories
		keywords += " " + title;
		files = dir.listFiles(dirsOnly);
		for (int i=0; i<files.length; i++) {
			processDirectory(files[i],template,files[i].getName(),keywords);
		}
	}

	private boolean skipDirectory(File dir) {
		if ((skipPrefixes == null) || (skipPrefixes.length == 0)) return false;
		String name = dir.getName();
		for (int i=0; i<skipPrefixes.length; i++) {
			if (!skipPrefixes[i].equals("") && name.startsWith(skipPrefixes[i])) {
				return true;
			}
		}
		return false;
	}

	private void createMircDocument(File[] files, File template, String title, String keywords) {

		//Don't create MIRCdocuments for empty file lists.
		if (files.length == 0) return;

		//Put the MIRCdocument in a subdirectory
		//of the main submission directory (baseDirName).
		docCount++;
		String dirName = baseDirName + File.separator + docCount;
		File mdDir = new File(docsDir,dirName);
		mdDir.mkdirs();

		//Make the File that points to the MIRCdocument.xml file to be created.
		File mdFile = new File(mdDir,"MIRCdocument.xml");

		//While we're at it, create the index entry.
		String indexEntry = "documents/" + baseDirName + "/" + docCount + "/MIRCdocument.xml";

		//Copy the template into the directory.
		FileUtil.copyFile(template, mdFile);

		//Instantiate the MircDocument so we can add objects into it.
		MircDocument md;
		try { md = new MircDocument(mdFile, indexEntry); }
		catch (Exception crash) { return; }

		//Set the title, author, abstract, and keywords.
		//Use the keywords as the abstract since there is
		//no way to get a real abstract.
		md.insert(
			title,
			name,
			affiliation,
			contact,
			keywords,
			keywords,
			username,
			read,
			update,
			export,
			overwriteTemplate);

		//Now add in all the files.
		for (int i=0; i<files.length; i++) {
			if (checker.isFileAllowed(files[i])) {
				//This file is allowed in the MIRCdocument.
				//Move the object to the MIRCdocument's directory.
				FileObject object = FileObject.getObject(files[i]);
				object.setStandardExtension();
				object.moveToDirectory(mdDir,object.getFile().getName());

				//If the object is a DicomObject and anonymize is set,
				//anonymize it using the anonymization script for the
				//storage service.
				if (anonymize
					&& (object instanceof DicomObject)
						&& (dicomAnonymizerProperties != null)) {

					File file = object.getFile();
					String exceptions =
						DicomAnonymizer.anonymize(
							file, file,
							dicomAnonymizerProperties, lookupTableProperties,
							new LocalRemapper(), false, false);
					//We don't have to re-parse the object because none
					//of the rest of this loop depends on the parsed data.
				}

				//See if we can instantiate it as a MircImage.
				//If successful, add it in as an image.
				try {
					MircImage image = new MircImage(object.getFile());
					if (image.isDicomImage()) {
						md.insertDicomElements(image.getDicomObject());
					}
					md.insert(image);
				}
				catch (Exception notImage) {
					//The file is not an image that can be inserted,
					//parse it as a subclass of FileObject and insert
					//it into the document as a metadata object.
					FileObject fileObject = FileObject.getObject(object.getFile());
					md.insert(fileObject,fileObject.getFile().getName());
					if (fileObject.hasMatchingExtension(textExtensions,true)) {
						md.insert(fileObject.getFile());
					}
				}
			}
		}

		//Make sure the document parses, and delete it if it doesn't.
		try { XmlUtil.getDocument(md.docFile); }
		catch (Exception failed) {
			MircIndex.getInstance().removeDocument(indexEntry);
			FileUtil.deleteAll(mdDir);
			return;
		}

		//Index the document.
		if (!publish && AuthorService.isPublic(md.docText)) {
			md.docText = AuthorService.makeNonPublic(md.docText);
			FileUtil.setFileText(md.docFile,md.docText);
			InputQueue.addQueueEntry(indexEntry,true);
		}
		MircIndex.getInstance().insertDocument(indexEntry);
	}

	class Filter implements FileFilter {
		boolean directories;
		String[] skip;
		public Filter(boolean directories) {
			this.directories = directories;
			this.skip = new String[0];
		}
		public Filter(boolean directories, String[] skip) {
			this.directories = directories;
			this.skip = skip;
			for (int i=0; i<skip.length; i++)
				skip[i] = skip[i].toLowerCase().trim();
		}
		public boolean accept(File file) {
			if (directories) return file.isDirectory();
			if (file.isDirectory()) return false;
			String name = file.getName().toLowerCase();
			if (name.equals("template.xml")) return false;
			for (int i=0; i<skip.length; i++) {
				if (!skip[i].equals("") && name.endsWith(skip[i])) return false;
			}
			return true;
		}
	}

	private boolean isValidTemplate(File template) {
		String root = XmlUtil.getDocumentElementName(template);
		return (root != null) && root.equals("MIRCdocument");
	}

	/**
	 * Unpack a zip file into a root directory, preserving the directory structure of the zip file.
	 * @param root the directory into which to unpack the zip file.
	 * @param inFile the zip file to unpack.
	 * @throws Exception if anything goes wrong.
	 */
	private static void unpackZipFile(File root, File inFile) throws Exception {
		if (!inFile.exists()) throw new Exception("Zip file does not exist.");
		ZipFile zipFile = new ZipFile(inFile);
		Enumeration zipEntries = zipFile.entries();
		while (zipEntries.hasMoreElements()) {
			ZipEntry entry = (ZipEntry)zipEntries.nextElement();
			String name = entry.getName().replace('/',File.separatorChar);
			//Make sure that the directory is present
			File outFile = new File(root,name);
			outFile.getParentFile().mkdirs();
			if (!entry.isDirectory()) {
				//Clean up any file names that might cause a problem in a URL.
				name = outFile
						.getName()
							.trim()
								.replaceAll("[\\s]+","_")
									.replaceAll("[\"&'><#;:@/?=]","_");
				outFile = new File(outFile.getParentFile(),name);

				//Now write the file with the corrected name.
				//Note: the name change only happens for files.
				BufferedOutputStream out =
					new BufferedOutputStream(new FileOutputStream(outFile));
				BufferedInputStream in =
					new BufferedInputStream(zipFile.getInputStream(entry));
				int size = 4096;
				int n = 0;
				byte[] b = new byte[size];
				while ((n = in.read(b,0,size)) != -1) out.write(b,0,n);
				in.close();
				out.close();
			}
		}
		zipFile.close();
	}

	private void initializeAnonymizer() {
		try {
			File propFile = new File(TrialConfig.basepath + TrialConfig.dicomAnonymizerFilename);
			dicomAnonymizerProperties = new Properties();
			dicomAnonymizerProperties.load(new FileInputStream(propFile));
			File lkupFile = new File(TrialConfig.basepath + TrialConfig.lookupTableFilename);
			lookupTableProperties = new Properties();
			try { lookupTableProperties.load(new FileInputStream(lkupFile)); }
			catch (Exception ex) { lookupTableProperties = null; }
		}
		catch (Exception e) {
			logger.warn("Unable to load the DicomAnonymizer script.");
		}
	}



}