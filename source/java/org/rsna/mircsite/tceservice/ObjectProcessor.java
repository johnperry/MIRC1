/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.tceservice;

import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.SwingUtilities;
import javax.swing.event.*;
import org.apache.log4j.Logger;
import org.rsna.mircsite.anonymizer.DicomAnonymizer;
import org.rsna.mircsite.log.Log;
import org.rsna.mircsite.storageservice.MircDocument;
import org.rsna.mircsite.storageservice.StorageConfig;
import org.rsna.mircsite.storageservice.Template;
import org.rsna.mircsite.util.DicomObject;
import org.rsna.mircsite.util.FileObject;
import org.rsna.mircsite.util.FileUtil;
import org.rsna.mircsite.util.TomcatUser;
import org.rsna.mircsite.util.TomcatUsers;
import org.rsna.mircsite.util.StringUtil;
import org.rsna.mircsite.util.XmlUtil;
import org.w3c.dom.Document;

/**
 * The Thread that processes DicomObjects from in the Store,
 * anonymizes them, and creates MIRCdocuments for them..
 */
public class ObjectProcessor extends Thread {

	static final Logger logger = Logger.getLogger(ObjectProcessor.class);
	static final String serviceName = "TCEObjectProcessor";

	Store store;
	boolean running = false;

	/**
	 * Class constructor; creates a new instance of the ObjectProcessor
	 * and sets itself to run at the lowest possible priority.
	 * @param store the Store object, providing access to the manifests and instances.
	 */
	public ObjectProcessor(Store store) {

		this.store = store;
		running = false;

		this.setPriority(Thread.MIN_PRIORITY); //run at the lowest priority
	}

	/**
	 * Get the status text for display by the admin service.
	 */
	public String getStatus() {
		if (running) return "running";
		return "not running";
	}

	/**
	 * The Runnable implementation; starts the thread, starts the DicomStorageScp,
	 * and polls the import queue directory, processing files when they appear.
	 */
	public void run() {
		Log.message(serviceName+": started");
		running = true;
		while (!interrupted()) {
			try {
				processManifests();
				sleep(10000);
			}
			catch (Exception e) {
				Log.message(serviceName+": interrupted");
				return;
			}
		}
		Log.message(serviceName+": interrupt received");
	}

	//Take the manifests in order and process them.
	private void processManifests() {
		File[] files = store.getQueuedManifests();
		for (int k=0; k<files.length; k++) {
			processManifest(files[k]);
		}
	}

	//Process one manifest, creating a new MIRCdocument for it
	//and all its referenced instances.
	private void processManifest(File file) {
		try {
			//Get the manifest and its referenced instances
			DicomObject manifest = new DicomObject(file);
			String[] refs = manifest.getInstanceList();

			//Make a directory for this MIRCdocument
			String docref = getDocref();
			String dir = makeDirectory(docref);

			//Put the template in the directory.
			File template = new File(TCEConfig.basepath + TCEConfig.templateFilename);
			File document = new File(dir + "MIRCdocument.xml");
			FileUtil.copyFile(template,document);

			//Make a temp directory child of dir.
			File temp = new File(dir + "temp");
			temp.mkdirs();

			//Get the MIRCdocument
			docref = docref.replace("\\","/") + "/MIRCdocument.xml";
			MircDocument td = new MircDocument(document,docref);

			//Put in the manifest and all the instances and store the updated document.
			//Note: we have to copy the files to a temp directory to protect them
			//from deletion by the insert method. We don't delete instances at all
			//because they may apply to multiple manifests.
			//First do the manifest.
			File ref = new File(temp,file.getName());
			FileUtil.copyFile(file,ref);
			DicomObject dob = new DicomObject(ref);
			td.insert(dob,false,true,null,null);

			//Now do the references.
			for (int i=0; i<refs.length; i++) {
				ref = store.getInstanceFile(refs[i]);
				File tref = new File(temp,ref.getName());
				FileUtil.copyFile(ref,tref);
				dob = new DicomObject(tref);
				td.insert(dob,false,true,null,null);
				td.docText = Template.getText(td.docText, dob);
				td.save();
				yield();
			}

			//Now we can delete the manifest and the temp directory.
			file.delete();
			FileUtil.deleteAll(temp);

			//Now create an account for the owners of the document, if
			//account creation is enabled and the owners don't exist.
			if (TCEConfig.accountAutocreate()) {
				//Creation is enabled.
				try {
					//Get the Tomcat Users.
					TomcatUsers tcUsers = TomcatUsers.getInstance(td.docFile);

					//Get the owners.
					Document xml = XmlUtil.getDocument(td.docFile);
					String ownerElement = XmlUtil.getValueViaPath(xml,"MIRCdocument/authorization/owner");
					String[] owners = ownerElement.split(",");

					//Create accounts for the owners if necessary.
					for (int i=0; i<owners.length; i++) {
						String owner = owners[i].replace("[","").replace("]","").trim();
						if (owner.length() > 0) {
							TomcatUser tcUser = tcUsers.getTomcatUser(owner);
							if (tcUser == null) {
								tcUser =
									new TomcatUser(
											owner,
											TCEConfig.getAccountPassword(),
											TCEConfig.getAccountRoles());
								tcUsers.addTomcatUser(tcUser);
							}
						}
					}

				}
				catch (Exception ex) {
					//Don't create accounts if an error occurs.
				}
			}
		}

		catch (Exception ex) {
			//Something really bad happened; log the event and delete the manifest file.
			Log.message("<font color=\"red\">"+serviceName+
						": Unable to process manifest and instances:<br>"
						+file.getName()+"<br>"
						+ex+"</font>");
			logger.info("Unable to process manifest and instances: "+file.getName(),ex);
			file.delete();
		}
	}

	//Create a directory to receive a document
	private String makeDirectory(String docref) throws IOException {
		String dir = StorageConfig.basepath + docref;
		File dirFile = new File(dir);
		if (!dirFile.mkdirs()) {
			throw new IOException("Unable to create a directory to receive the TCE document.");
		}
		return dirFile.getAbsolutePath() + File.separator;
	}

	private String getDocref() {
		return StorageConfig.documentsDirectory
					+ File.separator
						+ StringUtil.makeNameFromDate();
	}

}