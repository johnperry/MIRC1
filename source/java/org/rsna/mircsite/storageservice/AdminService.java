/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.storageservice;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.rsna.dicom.Dicom;
import org.rsna.dicom.DicomStorageScp;
import org.rsna.mircsite.dicomservice.*;
import org.rsna.mircsite.tceservice.*;
import org.rsna.mircsite.util.*;
import org.apache.log4j.Logger;
import org.rsna.mircsite.anonymizer.IdTable;
import org.rsna.mircsite.log.Log;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The top-level class of the MIRC StorageService Admin Service.
 * <p>
 * The Admin Service includes a set of tools allowing the site administrator
 * to modify the configuration parameters of the storage service and to
 * control the functions of the system.
 * <p>
 * This servlet responds to HTTP GET.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class AdminService extends HttpServlet {

	public static HtmlUtil html = new HtmlUtil();
	public static HttpExportService httpExportService = null;
	public static DicomExportService dicomExportService = null;
	public static DatabaseExportService databaseExportService = null;

	public static org.rsna.mircsite.dicomservice.ObjectProcessor dsObjectProcessor = null;
	public static org.rsna.mircsite.tceservice.ObjectProcessor tceObjectProcessor = null;
	public static org.rsna.mircsite.tceservice.StorageSCP tceStorageSCP = null;
	public static org.rsna.mircsite.tceservice.Store tceStore = null;

	public static String key = null;

	StoredDocumentsManager docManager;
	DeletedDocumentsManager ddManager;

	static final Logger logger = Logger.getLogger(AdminService.class);

	/**
	 * Initialize the configuration on startup. This method is called when the
	 * servlet container parses the webapp's web.xml file. It loads all the
	 * static configuration parameters for the storage service, the
	 * DICOM service, and the TCE service. It also starts the deleted documents
	 * garbage collector.
	 */
	public void init() {
		ContentType.load(getServletContext());
		StorageConfig.load(getServletContext());
		TrialConfig.load(getServletContext());
		TCEConfig.load(getServletContext());

		//Initialize the index.
		//This requires the name of the servlet and the port
		//so the index can establish a connection to the database.
		File documentsDir = new File(getServletContext().getRealPath(StorageConfig.documentsDirectory));
		File indexFile = new File(getServletContext().getRealPath("index"));
		try { MircIndex.init(documentsDir, indexFile); }
		catch (Exception ex) {
			logger.error("Unable to initialize the storage service index.", ex);
		}

		//Initialize the central remapping table key.
		String keyfile = TrialConfig.getKeyFile();
		String message;
		if ((keyfile != null) && !keyfile.trim().equals("")) {
			File keyfileFile = new File(keyfile);
			if (!keyfileFile.exists()) {
				message = "The remapping table keyfile does not exist. Stop Tomcat and create or load the keyfile.";
				Log.message(message);
				logger.warn(message);
			}
			else {
				key = FileUtil.getFileText(keyfileFile);
				key = key.trim();
				if (key.equals("")) {
					message = "The remapping key is non-existent or blank. This will cause the default key to be used.";
					Log.message(message);
					logger.warn(message);
				}
				else {
					//Okay, try to initialize the remapping table with the key from the file.
					if (!IdTable.initialize(TrialConfig.basepath + TrialConfig.idtableFilename,key)) {
						message = "Unable to initialize the remapping table with the specified key.";
						Log.message(message);
						logger.warn(message);
					}
					else Log.message("Remapping table initialized.");
				}
			}
		}

		//Start the DICOM Service if necessagy.
		if (TrialConfig.autostart()) {
			//Wait a bit to avoid having multiple SCPs start at once
			try { Thread.sleep(2000); }
			catch (Exception ignore) { }
			startDSAll();
		}

		//Start the TCE Service if necessary.
		if (TCEConfig.autostart()) {
			//Wait a bit to avoid having multiple SCPs start at once
			try { Thread.sleep(2000); }
			catch (Exception ignore) { }
			startTCEAll();
		}

		//Start the garbage collector for deleted documents.
		File dir = new File(getServletContext().getRealPath(StorageConfig.deletedDocuments));
		ddManager = new DeletedDocumentsManager(dir);
		ddManager.start();

		//Start the garbage collector for stored documents.
		dir = new File(getServletContext().getRealPath(StorageConfig.documentsDirectory));
		docManager = new StoredDocumentsManager(dir);
		docManager.start();
	}

	/**
	 * Clean up the servlet as it is being taken out of service.
	 * This method is called when the servlet container is shut down.
	 * It saves the anonymizer remapping table in case it has changed.
	 */
	public void destroy() {
		//Store the IdTable if it has been initialized and if it is dirty.
		IdTable.storeNow(false);
		MircIndex.getInstance().close();
	}

	/**
	 * The servlet method that responds to an HTTP GET.
	 * <p>
	 * If called with no query string, it returns the admin page.
	 * <p>
	 * If called with a query string, it interprets the string as
	 * a command, executes it, and returns a page with the results.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 * @throws IOException if the servlet cannot handle the request.
	 * @throws ServletException if the servlet cannot handle the request.
	 */
	public void doGet(
		HttpServletRequest req,
		HttpServletResponse res
		) throws IOException, ServletException {

		//Force the encoding for the input.
		try { req.setCharacterEncoding("UTF-8"); }
		catch (Exception ignore) { }

		//Get the storage service configuration
		StorageConfig.load(getServletContext());
		String sitename = StorageConfig.getSitename();

		//Get the trial configuration
		TrialConfig.load(getServletContext());

		//Get the TCE configuration
		TCEConfig.load(getServletContext());

		//Generate the admin page.
		String page = "";
		String param;

		//Figure out what to do and do it
		if (req.getParameter("rebuild") != null)
			page = rebuildIndex();

		else if (req.getParameter("listindex") != null)
			page = listIndex(req.getContextPath(), -1, (req.getParameter("log") != null));

		else if ((param=req.getParameter("deleteindexentry")) != null)
			page = deleteIndexEntry(req.getContextPath(),param, getInt(req.getParameter("line")));

		else if (req.getParameter("start") != null) {
			startDSAll();
			page = status(false);
		}

		else if (req.getParameter("tcestart") != null) {
			startTCEAll();
			page = status(false);
		}

		else if (req.getParameter("log") != null)
			page = showLog();

		else if (req.getParameter("clear") != null)
			page = clearLog();

		else page = status( (req.getParameter("save") != null) );

		//Make the full page and return it.
		page = html.html(getHead(sitename) +
						html.body("scroll=\"no\"",
							HtmlUtil.getCloseBox() +
							heading(sitename+" Admin") +
							html.center(getControls(req)) +
							html.div("id=\"results\" class=\"div\"",page)));
		ServletUtil.sendPageNoCache(res,page);
	}

	//Create the status display for the results div.
	private String status(boolean saveIndex) {
		String text = "";
		try {
			String docbase = StorageConfig.getDocbase();
			String aeTitle = TrialConfig.getDicomStoreAETitle();
			String ipAddress = IPUtil.getIPAddress();
			String port = TrialConfig.getDicomStorePort();
			String httpImportIPs[] = TrialConfig.getHttpImportIPAddresses();
			String httpExportDirs[] = TrialConfig.getHttpExportDirectories();
			String dicomExportDirs[] = TrialConfig.getDicomExportDirectories();
			String databaseExportDir = TrialConfig.getDatabaseExportDirectory();
			String indexfilepath = StorageConfig.basepath + "siteindex.xml";

			String tableWidth2 = "width=\"90%\" border=\"1\"";
			text += html.center(html.table(tableWidth2,
					statusRow("Document Base:",docbase) +
					statusRow("basepath:",StorageConfig.getBasepath()) +
					statusRow("Indexed Documents:",MircIndex.getInstance().getIndexSize()) +
					statusRow("PHI Access Logging Enabled:",StorageConfig.getPhiLogEnabled()) +
					statusRow("PHI Access Log Export Enabled:",StorageConfig.getPhiLogExportEnabled()) +
					statusRow("PHI Access Log Export URL:",StorageConfig.getPhiLogExportURL()) ));

			text += "<br/>";

			String tableWidth4 = "width=\"100%\" border=\"1\"";
			text += html.center(html.table(tableWidth4,
					statusRow4("Submit Service enabled:",StorageConfig.getSubmitEnabled(),
										 "Submit Service autoindex:",StorageConfig.getSubmitAutoindex()) +
					statusRow4("Author Service enabled:",StorageConfig.getAuthorEnabled(),
										 "Author Service autoindex:",StorageConfig.getAuthorAutoindex()) +
					statusRow4("DICOM Service enabled:",StorageConfig.getDicomEnabled(),
										 "DICOM Store AE Title:",aeTitle) +
					statusRow4("DICOM Service autostart:",TrialConfig.getAutostart(),
										 "DICOM Store IP Address:",ipAddress) +
					statusRow4("DICOM Service:",
							   ((dsObjectProcessor==null)?"not running":dsObjectProcessor.getStatus()),
										 "DICOM Store Port:",port) +
					statusRow4("TCE Service enabled:",StorageConfig.getTCEEnabled(),
										 "TCE Store AE Title:",TCEConfig.getDicomStoreAETitle()) +
					statusRow4("TCE Service autostart:",TCEConfig.getAutostart(),
										 "TCE Store IP Address:",ipAddress) +
					statusRow4("TCE Service:",
							   ((tceObjectProcessor==null)?"not running":tceObjectProcessor.getStatus()),
										 "TCE Store Port:",TCEConfig.getDicomStorePort())));

			text += "<br/>";

			String queueTableRows = "";
			int dicomCount = FileUtil.getFileCount(new File(TrialConfig.basepath + TrialConfig.dicomImportDir));
			int httpCount = FileUtil.getFileCount(new File(TrialConfig.basepath + TrialConfig.httpImportDir));
			queueTableRows += statusRow("Unprocessed DICOM Objects:",Integer.toString(dicomCount));
			queueTableRows += statusRow("Unprocessed HTTP(S) Objects:",Integer.toString(httpCount));
			int docCount = Quarantine.getFileCount();
			queueTableRows += statusRow("Quarantined DICOM Objects:",Integer.toString(docCount));

			for (int i=0; i<httpExportDirs.length; i++) {
				docCount = FileUtil.getFileCount(new File(TrialConfig.basepath + httpExportDirs[i]));
				queueTableRows += statusRow("Objects queued to "+httpExportDirs[i],Integer.toString(docCount));
			}
			for (int i=0; i<dicomExportDirs.length; i++) {
				docCount = FileUtil.getFileCount(new File(TrialConfig.basepath + dicomExportDirs[i]));
				queueTableRows += statusRow("Objects queued to "+dicomExportDirs[i],Integer.toString(docCount));
			}
			if (TrialConfig.databaseExportEnabled()) {
				docCount = FileUtil.getFileCount(TrialConfig.getDatabaseExportDirectoryFile());
				queueTableRows += statusRow("Objects queued for database export",Integer.toString(docCount));
			}
			text += html.center(html.table(tableWidth2,queueTableRows));

			if (tceStore != null) {
				text += "<br>";
				queueTableRows = "";
				queueTableRows += statusRow("TCE Instances:",Integer.toString(tceStore.getInstanceCount()));
				queueTableRows += statusRow("TCE Manifests:",Integer.toString(tceStore.getManifestCount()));
				queueTableRows += statusRow("Queued TCE Manifests:",Integer.toString(tceStore.getQueuedManifestCount()));
				text += html.center(html.table(tableWidth2,queueTableRows));
			}
			try {
				if (saveIndex) {
					MircIndex.getInstance().logState("saveIndex");
/*
					String indexString = XmlUtil.toString(MircIndex.getIndexXML());
					File savedIndexFile = new File(StorageConfig.basepath + "saved-index-file.xml");
					FileUtil.setFileText(savedIndexFile,indexString);
					text += html.p("Index saved (" + indexString.length() + " characters).");
*/
				}
			}
			catch (Exception ex) { text += html.p("Unable to save the index."); }
			return text;
		}
		catch (Exception e) {
			logger.warn(e.getMessage(),e);
			return "Exception: " + e.getMessage();
		}
	}

	//Start all the DICOM service threads.
	private void startTCEAll() {
		if (StorageConfig.tceEnabled()) {
			if (tceStorageSCP == null) {
				tceStorageSCP =
					new org.rsna.mircsite.tceservice.StorageSCP(
						TCEConfig.getDicomStoreAETitle(),
						TCEConfig.getDicomStorePort(),
						new File(getServletContext().getRealPath(TCEConfig.dicomStoreSCPDir)));
				tceStorageSCP.restartSCP();
			}
			else tceStorageSCP.reinitialize(
						TCEConfig.getDicomStoreAETitle(),
						TCEConfig.getDicomStorePort());

			if (tceStore == null) {
				tceStore =
					new org.rsna.mircsite.tceservice.Store(
						new File(getServletContext().getRealPath(TCEConfig.dicomStoreDir)));
				tceStorageSCP.addDicomListener(tceStore);
			}
			if (tceObjectProcessor == null) {
				tceObjectProcessor =
					new org.rsna.mircsite.tceservice.ObjectProcessor(tceStore);
				tceObjectProcessor.start();
			}
		}
	}

	/**
	 * Handle a change in the DICOM Storage SCP parameters.
	 */
	public static void scpParamsChanged() {
		if (dsObjectProcessor != null)
			dsObjectProcessor.scpParamsChanged(dsObjectProcessor);
	}

	//Start all the DICOM service threads.
	private void startDSAll() {
		if (StorageConfig.dicomEnabled()) {
			if (!IdTable.initialize(TrialConfig.basepath + TrialConfig.idtableFilename,key)) {
				String message = "Unable to initialize the remapping table with the specified key.";
				Log.message(message);
				logger.warn(message);
			}
			startObjectProcessor();
			startHttpExportService();
			startDicomExportService();
			startDatabaseExportService();
		}
	}

	//Start/restart the ObjectProcessor
	//depending on whether it is already instantiated.
	private void startObjectProcessor() {
		if (dsObjectProcessor == null) {
			dsObjectProcessor =
				new org.rsna.mircsite.dicomservice.ObjectProcessor();
			dsObjectProcessor.start();
		}
		else dsObjectProcessor.restart();
	}

	//Start/restart the HttpExportService
	//depending on whether it is already instantiated.
	private void startHttpExportService() {
		if (httpExportService == null) {
			httpExportService = new HttpExportService();
			httpExportService.start();
		}
		else httpExportService.restart();
	}

	//Start/restart the DicomExportService
	//depending on whether it is already instantiated.
	private void startDicomExportService() {
		if (dicomExportService == null) {
			dicomExportService = new DicomExportService();
			dicomExportService.start();
		}
		else dicomExportService.restart();
	}

	//Start/restart the DatabaseExportService
	//depending on whether it is already instantiated.
	private void startDatabaseExportService() {
		if (TrialConfig.databaseExportEnabled()) {
			if (databaseExportService == null) {
				databaseExportService = new DatabaseExportService();
				databaseExportService.start();
			}
			else databaseExportService.restart();
		}
	}

	//Get the contents of the DICOM service rolling log,
	//scrolling the display to the bottom.
	private String showLog() {
		return Log.getLog() + "<p id=\"here\"></p>";
	}

	//Clear the DICOM service rolling log.
	private String clearLog() {
		Log.clearLog();
		return status(false);
	}

	//Display the storage service index, scrolling the
	//display to a specific line number.
	private String listIndex(String contextPath, int entryNumber, boolean log) {
		String linkText;
		int k;
		String narrow = "width=\"40\" align=\"center\"";
		String wide = "width=\"80\" align=\"center\"";
		String remote = "align=\"center\" colspan=\"2\"";
		String here = "id=\"here\" " + narrow;
		String deletePath = "?deleteindexentry=";
		String editPath = contextPath+"/author/update?doc=";
		try {
			String docbase = StorageConfig.getDocbase();
			MircIndex index = MircIndex.getInstance();
			MircIndexEntry[] docs = index.query("");
			index.sortByLMDate(docs);
			StringWriter sw = new StringWriter();
			sw.write("<center><table border=\"1\" cellpadding=\"3\" width=\"100%\">\n");
			String docref;
			String title;
			String pubdate;
			String lmdate;
			if (entryNumber < 0) entryNumber = 0;
			else if (entryNumber >= docs.length) entryNumber = docs.length - 1;
			for (int line=0; line<docs.length; line++) {
				docref = docs[line].md.getAttribute("filename");
				sw.write("<tr>");

				if (line == entryNumber) sw.write(html.td(here,Integer.toString(line+1)));
				else sw.write(html.td(narrow,Integer.toString(line+1)));

				sw.write(html.td(wide, buttonCode("Delete",deletePath+docref+"&line="+line,0)));
				sw.write(html.td(wide, altButtonCode("Edit",editPath+docref,0)));
				title = docs[line].title;
				pubdate = docs[line].pubdate;
				lmdate = StringUtil.getDateTime(docs[line].lmdate).replace("T","&nbsp;");
				sw.write(html.td(makeAnchorTag(docbase + docref, title)));
				sw.write(html.td(
					(pubdate.equals("") ? "" : "PD:&nbsp;"+insertDashes(pubdate)+"<br>") +
					(lmdate.equals("") ? "" : "LM:&nbsp;"+lmdate)
					//+"<br>LM:&nbsp;"+docs[line].lmdate
					));
				sw.write("</tr>\n");
			}
			sw.write("</table>");
			if (docs.length > 0) {
				sw.write("<p>" + docs.length + " document");
				if (docs.length > 1) sw.write("s");
				sw.write(" in the index</p></center>\n");
			}
			else sw.write(html.p("The index is empty.") + "</center>\n");

			if (log) index.logState(contextPath);

			return sw.toString();
		}
		catch (Exception e) {
			logger.warn("Unable to list the index.",e);
			return "Exception: " + e.getMessage();
		}
	}

	private String insertDashes(String s) {
		if (s.length() == 8) {
			return s.substring(0,4) + "-" + s.substring(4,6) + "-" + s.substring(6);
		}
		return s;
	}

	//Delete a storage service index entry and then list the index,
	//scrolling the display to the next line.
	private String deleteIndexEntry(String contextPath, String entry, int line) {
		String text = "";
		try {
			String docbase = StorageConfig.getDocbase();
			String docdir = entry.substring(0,entry.lastIndexOf("/"));
			File docdirFile = new File(getServletContext().getRealPath(docdir));
			if (!docdirFile.isDirectory()) {
				return html.p(html.b(docdir) + " is not a directory.") +
							 html.p("The entry was not deleted.");
			}
			if (countXMLDocs(docdirFile) != 1) {
				return html.p(html.b(docdir)
							+ " contains unknown directories and/or multiple MIRC files.")
							+ html.p("The delete request was rejected.");
			}
			//Okay, it is okay to delete it.
			//Remove it from the input queue in case it is there,
			//then remove it from the index, and move the directory
			//into the deleted documents directory.
			InputQueue.deleteQueueEntry(entry);
			boolean entryFound = MircIndex.getInstance().removeDocument(entry);
			boolean deleted = removeDocument(docdirFile);
			if (entryFound)
				text += html.p(html.b(entry) + " was removed from the index.");
			else
				text += html.p(html.b(entry) + " could not be removed from the index.");
			if (deleted)
				text += html.p("The " + html.b(docdir) +
								 " directory was moved to the deleted documents directory.");
			else
				text += html.p("The " + html.b(docdir) +
								" directory could not be moved to the deleted documents directory.");
			//return an error if we got one
			if (!entryFound || !deleted) return text;

			//OK, if we got here, all the deletes were successful.
			//Return the index listing, scrolled to the last delete.
			return listIndex(contextPath, line, false);
		}
		catch (Exception e) {
			text += html.p("Exception: " + e.getMessage());
			return text;
		}
	}

	/**
	 * Remove a document from the storage service, moving it to the
	 * deleted-documents directory. If a previous version of the document is
	 * already in the deleted-documents directory, delete it before the
	 * new version is moved. Note: this method does not modify the index.
	 * @param dir the directory containing the document and its associated files.
	 * @return true if the document was successfully removed; false otherwise.
	 */
	public static boolean removeDocument(File dir) {
		//Make the necessary directories.
		File file = new File(StorageConfig.basepath + StorageConfig.deletedDocuments);
		file = new File(file,StringUtil.makeNameFromDate());
		file.mkdirs();
		//Make a new target File pointing to where we will move the document's directory.
		file = new File(file,dir.getName());
		//Delete the target just in case.
		FileUtil.deleteAll(file);
		//Update the lastModified parameter for the DeletedDocumentsManager.
		dir.setLastModified(System.currentTimeMillis());
		//Get the parent directory if it is empty.
		File parent = dir.getParentFile();
		//Next move the directory over to the deleted documents directory.
		boolean result = dir.renameTo(file);
		//Finally, delete any empty directories up the tree
		//until we get to the documents directory. The purpose of
		//this action is to remove any subdirectories that may have
		//been created by, for example, the Zip Service, when all their
		//children have been deleted. Note that the File.delete() method
		//fails if the directory is not empty.
		while (!parent.getName().equals(StorageConfig.documentsDirectory)
				&& parent.delete()) {
			parent = parent.getParentFile();
		}
		return result;
	}

	//Determine whether a directory
	//has a standard storage service configuration - with
	//one MIRCdocument XML file and no subdirectories other
	//than the ones for identified and de-identified datasets.
	private int countXMLDocs(File dir) {
		File[] fileList = dir.listFiles();
		int count = 0;
		for (int i=0; i<fileList.length; i++) {
			if (fileList[i].isDirectory() &&
				!fileList[i].getName().equals("phi") &&
				!fileList[i].getName().equals("no-phi")) return -1;
			if (fileList[i].getName().trim().toLowerCase().endsWith(".xml")) {
				String name = XmlUtil.getDocumentElementName(fileList[i]);
				if (name.equals("MIRCdocument")) count++;
			}
		}
		return count;
	}

	//Walk the documents directory tree and create a new index.
	//This can be used to rebuild the index after the administrator does an unnatural
	//act behind the storage system's back, although such acts are not encouraged.
	private String rebuildIndex() {
		Runnable reIndex = new Runnable() {
			public void run() { MircIndex.getInstance().rebuildIndex(); }
		};
		new Thread(reIndex).start();
		return html.p("The background thread to rebuild the index was started.")
				+ html.p("Click the Status button to monitor its progress.")
				+ html.p("The system is usable during the rebuilding process,"
							+ " but some documents many not be visible until the rebuild is complete.");
	}

	//Make the admin page head element, with the title, styles, and scripts.
	private String getHead(String name) {
		String title = html.title("Admin Service: " + name);
		String style =
			  "<style type=\"text/css\">\n"
			+		".div {border-width:thin;border-style:inset;\n"
			+				"padding-left:10px;padding-right:10px;padding-top:10px;padding-bottom:10px;\n"
			+				"overflow:auto}\n"
			+		"body {margin:0; padding:0; background: #c6d8f9;}\n"
			+		"h2 {margin-top:10; margin-left:10; padding-left:10; padding-top:10;}\n"
			+	"</style>\n";
		String script =
			 	"<script>\n"
			+		"function AdjustResultsHeight() {\n"
			+			"var dv = document.getElementById('results');\n"
			+			"var h = getHeight() - dv.offsetTop;\n"
			+		 	"if (h < 50) h = 50;\n"
			+			"dv.style.height = h;\n"
			+		"}\n"
			+		"function getHeight() {\n"
			+			"if (document.all) return document.body.clientHeight;\n"
			+			"return window.innerHeight-10;\n"
			+		"}\n"
			+		"window.onresize = AdjustResultsHeight;\n"
			+		"function scrollResults() {\n"
			+			"var here = document.getElementById(\"here\");\n"
			+			"if (here != null) {\n"
			+				"var results = document.getElementById(\"results\");\n"
			+				"results.scrollTop = here.offsetTop - results.clientHeight/4;\n"
			+			"}\n"
			+		"}\n"
			+		"function onLoad() {\n"
			+			"AdjustResultsHeight();\n"
			+			"scrollResults();\n"
			+		"}\n"
			+		 "window.onload = onLoad;\n"
			+	"</script>\n";
		return html.head(title + style + script);
	}

	//Make the button panel at the top of the admin page.
	private String getControls(HttpServletRequest req) {
		String tableWidth = "width=\"95%\"";
		String w20 = "width=\"25%\"";
		String rowAlign = "align=\"center\"";
		String webapp = req.getContextPath();
		String servlet = webapp + req.getServletPath();

		String controls =
			html.table(tableWidth,
				html.thead(html.tr(rowAlign,
						html.td(w20,"") +
						html.th(w20,"Storage Service") +
						html.th(w20,"TCE") +
						html.th(w20,"DICOM"))) +

				html.tr(rowAlign,
						html.td(buttonCode("Status","?status")) +
						html.td(altButtonCode("Update Configuration", servlet + "/ssconfig")) +
						html.td(altButtonCode("Update Configuration", servlet + "/tceconfig")) +
						html.td(altButtonCode("Update Configuration", servlet + "/tsconfig"))) +

				html.tr(rowAlign,
						html.td("") +
						html.td(buttonCode("List Index","?listindex")) +
						html.td(buttonCode("Start/Restart","?tcestart")) +
						html.td(buttonCode("Start/Restart","?start"))) +

				html.tr(rowAlign,
						html.td("") +
						html.td(
							buttonCode("Rebuild Index","?rebuild")// +
							/*buttonCode("Save Index","?save",45)*/) +
						html.td(
							buttonCode("Show Log","?log",45) +
							buttonCode("Clear Log","?clear",45)) +
						html.td(
							buttonCode("Show Log","?log",45) +
							buttonCode("Clear Log","?clear",45))));
		return controls;
	}

	//Miscellaneous functions to assist in the production of the admin page.
	static final int defaultButtonWidth = 94;

	private String buttonCode(String buttonName, String buttonURL) {
		return buttonCode(buttonName, buttonURL, defaultButtonWidth);
	}

	private String altButtonCode(String buttonName, String buttonURL) {
		return altButtonCode(buttonName, buttonURL, defaultButtonWidth);
	}

	private String buttonCode(String buttonName, String buttonURL, int width) {
		String text = "<input type=\"button\" ";
		if (width>0) text += "style=\"width:" + width + "%\" ";
		text += "value=\"" + buttonName + "\" "
		  +	"onclick=\"document.location.replace('" + buttonURL + "');\"/>\n";
		return text;
	}

	private String altButtonCode(String buttonName, String buttonURL, int width) {
		String text = "<input type=\"button\" ";
		if (width>0) text += "style=\"width:" + width + "%\" ";
		text += "value=\"" + buttonName + "\" "
		  +	"onclick=\"window.open('" + buttonURL + "','_blank');\"/>\n";
		return text;
	}

	private String statusRow(String fieldname, int n) {
		String width = "width=\"50%\"";
		return html.tr(html.td(width,html.b(fieldname)) + html.td(width,Integer.toString(n)));
	}

	private String statusRow(String fieldname, String content) {
		String width = "width=\"50%\"";
		return html.tr(html.td(width,html.b(fieldname)) + html.td(width,content));
	}

	private String statusRow4(String a, String aa, String b, String bb ) {
		String width = "width=\"25%\"";
		return html.tr(html.td(width,html.b(a)) + html.td(width,aa) +
						html.td(width,html.b(b)) + html.td(width,bb));
	}

	private String makeAnchorTag(String url, String linkText) {
		return html.a(url,"_blank",linkText);
	}

	private String heading(String title) {
		return html.h2(title);
	}

	private int getInt(String intString) {
		if (intString == null) return -1;
		try { return Integer.parseInt(intString); }
		catch (Exception ex) { return -1; }
	}
}