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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.rsna.mircsite.util.FileUtil;
import org.rsna.mircsite.util.StringUtil;
import org.rsna.mircsite.util.TypeChecker;
import org.rsna.mircsite.util.XmlUtil;
import org.w3c.dom.Document;

import com.oreilly.servlet.MultipartRequest;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The Submit Service of the MIRC Storage Service.
 * <p>
 * The Submit Service accepts multipart/form-data submissions of
 * zip files containing a MIRCdocument and the files it references
 * in its own directory. This is the interface used by browsers.
 * <p>
 * This Submit Service also accepts application/x-zip-compressed
 * submissions of zip files. This is the interface used by client-side
 * authoring tools.
 * <p>
 * The servlet responds to both HTTP GET and POST.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class SubmitService extends HttpServlet {

	private static final long serialVersionUID = 12312321l;

	static final Logger logger = Logger.getLogger(SubmitService.class);

	/**
	 * The servlet method that responds to an HTTP GET.
	 * <p>
	 * It returns a web page containing a submission for
	 * to the user in the response text.
	 * <p>
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 * @throws ServletException if the servlet cannot handle the request.
	 * @throws IOException if any IO error occurs.
	 */
	public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException {


		//Get the sitename for the submission page
		String sitename = StorageConfig.getSitename();

//		//Make sure document submission is enabled
//		if (!StorageConfig.submitEnabled()) {
//			PrintWriter out = res.getWriter();
//			out.print(returnMessage(sitename,"<p>Document submission is not enabled on this site.</p>") );
//			out.close();
//			return;
//		}

		req.setAttribute("siteName", sitename);
		String forward = "/submit-service/Submit.jsp";
		RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(forward);
		try {
			dispatcher.forward(req,res);
		} catch( Exception ex) {
			throw new ServletException(ex);
		}
	}

	/**
	 * The servlet method that responds to an HTTP POST.
	 * <p>
	 * This method receives a document submission in the form of a zip file.
	 * It uses the content type to determine how to receive and process the submission.
	 * <p>
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 * @throws ServletException if the servlet cannot handle the request.
	 * @throws IOException if any IO error occurs.
	 */
	public void doPost(HttpServletRequest req, HttpServletResponse res ) throws IOException, ServletException {

		String response = "";

		//Get the values needed from the storage service configuration
		String docbase = StorageConfig.getDocbase();
		String sitename = StorageConfig.getSitename();
		int maxsize = StorageConfig.getMaxSubmitSize();
		maxsize = Math.max(maxsize,5)*1024*1024;

		//Make sure document submission is enabled
		if (!StorageConfig.submitEnabled()) {
			PrintWriter out = res.getWriter();
			out.print(returnMessage(sitename,"<p>Document submission is not enabled on this site.</p>") );
			out.close();
			return;
		}

		//Check the Content-Type
		String contentType = req.getContentType().toLowerCase();
		if (!contentType.contains("multipart/form-data") && !contentType.contains("application/x-zip-compressed")) {
			PrintWriter out = res.getWriter();
			out.print(returnError(sitename,"<p>Unsupported Content-Type: " + req.getContentType() + "</p>") );
			out.close();
			return;
		}

		//Get the suppress query string parameter. This parameter can be used
		//by client-side applications to suppress the submission form on
		//results pages. If the parameter is present, the form is suppressed.
		String suppressString = req.getParameter("suppress");
		boolean suppress = (suppressString != null);

		//Get the subfolders query string parameter. This parameter can be used
		//by client-side applications to tell the service to accept zip files containing
		//subfolders. If subfolders are accepted, all the path information in the zip
		//file is ignored.
		String subfoldersString = req.getParameter("subfolders");
		boolean subfolders = (subfoldersString != null);

		//Get the ppt query string parameter. This parameter is a convenience
		//for setting both the suppress and subfolders parameters.
		String pptString = req.getParameter("ppt");
		if (pptString != null) {
			suppress = true;
			subfolders = true;
		}

		//Make a directory to receive the document
		String reldir = "documents/" + StringUtil.makeNameFromDate();
		String dir = getServletContext().getRealPath(reldir);
		File dirFile = new File(dir);
		if (!dirFile.mkdirs()) {
			PrintWriter out = res.getWriter();
			out.print(
				returnError(
					sitename,
					"<p>Unable to create a directory to receive the submitted document:</p>"
					+ "<p>" + dir + "</p>") );
			out.close();
			return;
		}

		//Now get the posted file and the path parameter, if present
		File file;
		String path = null;
		if (contentType.indexOf("multipart/form-data") >= 0 ) {
			MultipartRequest multipartRequest = new MultipartRequest(req, dir, maxsize, "UTF-8");
			file = getMultipartFormFile(multipartRequest);
			path = multipartRequest.getParameter("path");
		}
		else {
			file = getPostedFile(req,maxsize,dir);
			path = req.getParameter("path");
		}
		if (path != null) path = path.trim();

		//See what we got
		if (file == null) {
			response += "<p>It appears that no file was posted.</p>";
			response += deleteResponse(FileUtil.deleteAll(dirFile));
			finish(req, res, response, sitename, suppress);
			return;
		}
		File mainFile = null;
		try { mainFile = unpackZipFile(file, subfolders); }
		catch (Exception ex) {
			response += "<p>There was a problem unpacking the posted file.</p>"
					 +  deleteResponse(FileUtil.deleteAll(dirFile));
			finish(req, res, response, sitename, suppress);
			return;
		}
		if (mainFile == null) {
			response += "<p>The zip file was unpacked successfully.</p>"
					 +  "<p>It did not contain a MIRCdocument file to index.</p>"
					 +   deleteResponse(FileUtil.deleteAll(dirFile));
			finish(req, res, response, sitename, suppress);
			return;
		}
		else {
			//Okay, we have an acceptable submission and it is in
			//the directory identified by the String reldir and the
			//File dirFile. The doc query parameter, if any, is in path;
			//this is the path to the XML file of the MIRCdocument to
			//be updated. If path is null or empty, then just make a
			//new document.
			file.delete(); //delete the zip file
			String relPath = reldir + "/" + mainFile.getName();

			boolean isDocumentUpdate = (path != null)  && !path.equals("");
			if (isDocumentUpdate) {
				//This is an update of an existing MIRCdocument.
				//Remove the old version and put the new one in
				//its place.
/**/			logger.debug("Interpreting the submission as an attempted update...");
				try {
					File oldFile = new File(getServletContext().getRealPath(path));
					Document docXML = XmlUtil.getDocument(oldFile);
					//First parse the old document and see if the user
					//is authorized to change it.
					boolean canUpdate = XMLServer.userIsAuthorizedTo("update",docXML,req);
/**/				logger.debug("Authenticated username: "+req.getRemoteUser());
/**/				logger.debug("User authorization to update: "+canUpdate);
					if (canUpdate) {
						//Okay, remove the old document.
						File oldDirFile = oldFile.getParentFile();
						MircIndex.getInstance().removeDocument(path);
						InputQueue.deleteQueueEntry(path);
						AdminService.removeDocument(oldDirFile);
						//Now rename the new directory to the old name so the URL stays the same
						if (dirFile.renameTo(oldDirFile)) {
							//Okay, everything is ready, change relPath to point to the old
							//directory but with the new file name for the MIRCdocument, in
							//case it has changed.
							relPath = path.substring(0,path.lastIndexOf("/")) + "/" + mainFile.getName();
							response += "<p>The document has been updated.</p>";
						}
					}
				}
				catch (Exception continueOnward) { }
			}
			response +=
				"<p>The zip file was received and unpacked successfully:</p>";
			String fullPath = docbase + relPath;
			response += "<p>" + anchorTag(fullPath) + "</p>";

			String username = req.getRemoteUser();
			String publisher = StorageConfig.getPublisherRoleName();
			boolean isAutoindex = StorageConfig.submitAutoindex();
			boolean isPublisher = req.isUserInRole(publisher);
			setAuthorization(mainFile, username, isAutoindex, isPublisher, isDocumentUpdate);

			//Now index the document.
			if (isAutoindex || isPublisher) {
				if (MircIndex.getInstance().insertDocument(relPath))
					response += "<p>The site index has been updated.</p>";
				else
					response += "<p>The attempt to update the site index failed.</p>";
			}
			else {
				if (InputQueue.addQueueEntry(relPath))
					response += "<p>The document has been added to the input queue.</p>";
				else
					response += "<p>The attempt to update the input queue failed.</p>";
			}
		}
		finish(req, res, response, sitename, suppress);
	}

	private void setAuthorization(
						File docFile,
						String owner,
						boolean isAutoindex,
						boolean isPublisher,
						boolean isDocumentUpdate) {
		Document doc;
		try { doc = XmlUtil.getDocument(docFile); }
		catch (Exception ex) { return; }
		Element authElement = XmlUtil.getElementViaPath(doc,"MIRCdocument/authorization");
		if (authElement == null) {
			//The document does not have an authorization element; insert it.
			Element root = doc.getDocumentElement();
			authElement = (Element)root.appendChild(doc.createElement("authorization"));
		}

		//Now remove any existing owner element and add one containing only
		//the username of the submitting user. NOTE: this is ONLY done if
		//the submission is NOT an update to an existing document.
		if (!isDocumentUpdate) {
			Element ownerElement = XmlUtil.getElementViaPath(authElement,"authorization/owner");
			if (ownerElement != null) authElement.removeChild(ownerElement);
			ownerElement = doc.createElement("owner");
			ownerElement.appendChild(doc.createTextNode(owner));
			authElement.appendChild(ownerElement);
		}

		//Now set up the read element, if necessary.
		//The rules are:
		//  If autoindexing is enabled, the document is accepted.
		//  If the user has the publisher role as defined in web.xml, it is accepted.
		//  If the document is private or restricted, it is accepted.
		//  Otherwise, the document is made non-public.
		Element readElement = XmlUtil.getElementViaPath(authElement,"authorization/read");
		if (readElement == null) {
			//The element is missing, add an empty one so that the document will be private by default.
			readElement = (Element)authElement.appendChild(doc.createElement("read"));
		}
		else {
			String read = XmlUtil.getElementValue(readElement);
			boolean isPublic = (read.indexOf("*") != -1);
			if (!isAutoindex && !isPublisher && isPublic) {
				read = read.replaceAll("\\s+","").replace("*",",").replaceAll("[,]+",",");
				if (read.startsWith(",")) read = read.substring(1);
				if (read.endsWith(",")) read = read.substring(0,read.length()-1);
				while (readElement.hasChildNodes()) readElement.removeChild(readElement.getFirstChild());
				readElement.appendChild(doc.createTextNode(read));
			}
		}

		//Now save the document to the original file.
		FileUtil.setFileText(docFile,XmlUtil.toString(doc));
	}

	private void finish(HttpServletRequest req, HttpServletResponse res, String response, String sitename, boolean suppress)
				throws IOException, ServletException {
		if( suppress ) {
			PrintWriter out = res.getWriter();
			out.print(response);
			out.close();
			return;
		} else {
			req.setAttribute("message", response);
			req.setAttribute("siteName", sitename);
			String forward = "/submit-service/Submit.jsp";
			RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(forward);
			try {
				dispatcher.forward(req,res);
			} catch( Exception ex) {
				throw new ServletException(ex);
			}
			return;
		}
	}

	//Receive a multipartform and get the first file it contains.
	//Note: since browsers do not send more than one file in a multipartform submission,
	//only the first file is processed. It is assumed to be a zip file containing the
	//entire submission.
	private File getMultipartFormFile(MultipartRequest multipartRequest)
			throws IOException {
		Enumeration files = multipartRequest.getFileNames();
		File file = null;
		if (files.hasMoreElements()) {
			String name = (String)files.nextElement();
			file = multipartRequest.getFile(name);
		}
		return file;
	}

	//Receive a file sent via HTTP as content type application/x-zip-compressed.
	//Note: since an empty directory has just been created for the document, it is not necessary
	//to protect against the temp file name colliding with an existing file.
	private File getPostedFile(HttpServletRequest req, int maxsize, String dir)
			throws IOException {
		File file = new File(dir + "/tempMIRCatSubmission.zip");
		try {
			ServletInputStream sis = req.getInputStream();
			FileOutputStream fos = new FileOutputStream(file);
			byte[] b = new byte[10000];
			int len;
			while ((len=sis.read(b,0,b.length)) >= 0) fos.write(b,0,len);
			fos.flush();
			fos.close();
		}
		catch (Exception e) { file = null; }
		return file;
	}

	//Produce a string for the submission response page indicating whether the
	//submission could be deleted after processing.
	private String deleteResponse(boolean b) {
		if (b) return "<p>The submission was deleted.</p>";
		return "<p>There was a problem deleting the submission.</p>";
	}

	//Unpack the submitted zip file.
	//A MIRCdocument and its local references are stored in their own directory.
	//If the subfolders parameter is true, path information is ignored. If
	//the parameter is false, zip files containing path information are rejected.
	//
	//Note that with the advent of research datasets, which are created by template elements
	//in the trial/template.xml file in response to the receipt of DICOM objects by the
	//DICOM service, some document directories may contain "phi" and "no-phi" subdirectories.
	//These subdirectories are not exported when documents are exported, so the zip files
	//still meet the requirements of the submit service. If we decide to include the
	//research datasets in exported documents, then we have to change this function to
	//allow the datasets to be unpacked and stored in the directory with the MIRCdocument.
	private File unpackZipFile(File file, boolean subfolders) throws Exception {
		if (!file.exists()) return null;
		String path = file.getAbsolutePath();
		String p = path.toLowerCase();
		if (!p.endsWith(".zip") && !p.endsWith(".jar") && !p.endsWith(".war"))
			throw new Exception("The file was not a zip, jar, or war file.");
		path = path.substring(0,path.lastIndexOf(File.separatorChar)+1);
		File xmlFile = null;
		TypeChecker checker = new TypeChecker();
		ZipFile zipFile = new ZipFile(file);
		Enumeration zipEntries = zipFile.entries();
		while (zipEntries.hasMoreElements()) {
			ZipEntry entry = (ZipEntry)zipEntries.nextElement();
			String name = entry.getName().replace("\\", "/");
			if (entry.isDirectory() || ((name.indexOf('/') >= 0) && !subfolders)) {
				if (!subfolders) {
					zipFile.close();
					throw new Exception("The zip file contains a directory.");
				}
			}
			else {
				//Eliminate the path information.
				name = name.substring(name.lastIndexOf("/")+1);
				//Make a File to store the entry;
				File outFile = new File(path + name);
				// Only accept legal files types
				if (checker.isFileAllowed(outFile)) {
					BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outFile));
					BufferedInputStream in = new BufferedInputStream(zipFile.getInputStream(entry));
					int size = 1024;
					int n = 0;
					byte[] b = new byte[size];
					while ((n = in.read(b,0,size)) != -1) out.write(b,0,n);
					in.close();
					out.close();
					if (name.toLowerCase().endsWith(".xml")) {
						String rootElementName = XmlUtil.getDocumentElementName(path + name);
						if (rootElementName == null) {
							zipFile.close();
							throw new Exception("The zip file contains an XML file that does not parse.");
						}
						if (rootElementName.equals("MIRCdocument") && (xmlFile == null)) {
							xmlFile = checkFilename(path,name);
						}
					}
				}
			}
		}
		zipFile.close();
		return xmlFile;
	}

	//Check a filename to see if it contains characters that would
	//cause problems when they appear in the docref attribute and
	//rename the file to an acceptable name if necessary.
	private File checkFilename(String path, String name) {
		File oldFile = new File(path,name);
		// Do not allow asterisks, apostrophes, quotes and angle brackets in filenames
		String newName = name.trim().replaceAll("[\\s]+","_").replaceAll("[\"&'><#;:@/?=]","_");
		if (newName.equals(name)) return oldFile;
		File newFile = new File(path,newName);
		oldFile.renameTo(newFile);
		return newFile;
	}

	//Miscellaneous functions used in producing HTML reponse pages.

	private String returnMessage(String name, String text) {
		return responseHead(name) + "<h2>" + name + "</h2>" + text + responseEnd();
	}

	private String returnError(String name, String text) {
		return responseHead(name) + "<h2>" + name + "</h2>" +
						"<p>Unable to process the submitted document.</p>" +
						text + responseEnd();
	}

	private String responseHead(String name) {
		return "<html>\n<head>\n<title>Submit Service: " + name + "</title>\n</head>\n<body>\n";
	}

	private String responseEnd() {
		return "</body>\n</html>";
	}

	private String anchorTag(String url) {
		return "<a href=\"" + url + "\" target=\"_blank\">" + url + "</a></p>";
	}

}