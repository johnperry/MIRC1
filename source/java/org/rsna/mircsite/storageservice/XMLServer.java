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
import java.util.zip.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.apache.log4j.Logger;
import org.rsna.mircsite.dicomservice.*;
import org.rsna.mircsite.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The XML Server servlet. This servlet acts as a server for XML files
 * and the objects they reference. It verifies authorization for all
 * MIRCdocument accesses and provides transformation, zip export, and
 * PHI access logging.
 * <p>
 * This servlet responds to HTTP GET.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class XMLServer extends HttpServlet {

	static final Logger logger = Logger.getLogger(XMLServer.class);

	/**
	 * The servlet method that responds to an HTTP GET. All accesses
	 * to MIRCdocument XML objects are tested for authorization. If
	 * an access requires authentication, the browser is redirected to
	 * the Login servlet with a return path that re-initiates the
	 * access after authentication. For compatibillity with previous
	 * if the path starts with "/secure", the browser is redirected to
	 * versions, the Login servlet with a return path that does not
	 * include the "/secure" path element.
	 * <p>
	 * If called with no query string, it returns the requested file,
	 * after transformation if it is required.
	 * <p>
	 * If the request is for an XML file and the request includes a query
	 * string of ?xsl=name, it uses the name as the name of the transformation
	 * file; otherwise, it uses the name of the root element as the name of
	 * the transformation file.
	 * <p>
	 * If the file is a MIRCdocument and the document indicates that it
	 * contains PHI, it logs the access as a PHI access.
	 * <p>
	 * If called with a query string of ?dicom for a file whose name ends in "dcm",
	 * it returns a dump of the DICOM file's dataset formatted into an HTML page.
	 * <p>
	 * If called with a query string of ?zip (no value), it returns a zip file
	 * containing the MIRCdocument and all its local references. If the
	 * query string also includes an ext parameter, the value of the
	 * parameter is used as the extension of the zip file; otherwise,
	 * the zip file has the extension ".zip".
	 * <p>
	 * If called with a query string of ?zip=name, it returns a zip file
	 * containing the contents of the name subdirectory. If the name
	 * directory is NOT "no-phi" it logs the access as a PHI access.
	 * <p>
	 * If called with a query string of ?dicom-export, it queues all the
	 * *.dcm files in the document's root directory for the DicomExportService.
	 * This does not queue the subdirectories.
	 * <p>
	 * If called with a query string of ?database-export, it queues all the
	 * *.dcm files in the document's root directory for the DatabaseExportService.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 * @throws ServletException if the servlet cannot handle the request.
	 */
	public void doGet(
			HttpServletRequest req,
			HttpServletResponse res
			) throws IOException, ServletException {

		//If the path ends in ".xml" or ".dcm", then requests come here without authentication.
		String contextPath = req.getContextPath();
		String requestPath = req.getServletPath();
		String pathInfo = req.getPathInfo();
		if (pathInfo != null) requestPath += pathInfo;
		String redirectPath = contextPath + requestPath;

		//Redirect if this is a "/secure" access
		if (requestPath.startsWith("/secure")) {
			ServletUtil.authenticate(req,res,contextPath + requestPath.substring(7));
			return;
		}

		if (requestPath.startsWith("/"))
			requestPath = requestPath.substring(1);
		String requestFilename = requestPath.substring(requestPath.lastIndexOf("/")+1);
		String requestDirectory = requestPath.substring(0,requestPath.lastIndexOf("/")+1);
		String requestPathLC = requestPath.toLowerCase();

		//See if this is a request for storage.xml or trial/idtable.properties.
		//Refuse to serve these files so nobody can see any secrets in there.
		if (requestPathLC.equals("")
				|| requestPathLC.equals("storage.xml")
				|| requestPathLC.equals("trial/idtable.properties")) {
			logger.info("Request for secret file: IP = "+req.getRemoteHost());
			res.sendError(res.SC_NOT_FOUND);
			return;
		}

		//See if the requested file exists
		File file = new File(getServletContext().getRealPath(requestPath));
		if (!file.exists()) {
			res.sendError(res.SC_NOT_FOUND);
			return;
		}

		//Handle special requests for DICOM objects here.
		if (requestPathLC.endsWith(".dcm")
                        || requestPathLC.endsWith(".svs")) {
//CHANGED THIS TO ADD SVS SUPPORT
			if (req.getParameter("dicom") != null) {
				try {
					DicomObject dicomObject = new DicomObject(file);
					ServletUtil.sendPage(
						res,
						HtmlUtil.getPage(
							file.getName(),
							dicomObject.getElementTable()),
						false);
				}
				catch (Exception ex) { res.sendError(res.SC_NO_CONTENT); }
				return;
			}
			else if (req.getParameter("viewer") != null) {
				String reqURL = req.getRequestURL().toString();
				int k = reqURL.indexOf("://") + 3;	//get past the protocol
				k = reqURL.indexOf("/",k) + 1;	//get to the beginning of the servlet name
				k = reqURL.indexOf("/",k) + 1;	//get to the end of the servlet name, plus the slash

                            //get path for MIRC document associated with this image
                            //need to do this to determine update permissions
                            int l = reqURL.lastIndexOf("/");
                            String mircPath = reqURL.substring(0, l+1)+"MIRCdocument.xml";
                            String dicomFileName = reqURL.substring(l+1);
                            int m = mircPath.indexOf("://") + 3;
                            m = mircPath.indexOf("/", m);
                            mircPath = "webapps/"+mircPath.substring(m+1); //not sure if this path will work for all tomcat versions

                            Document xmlDocument;
                            boolean canEditBool = false;
                            try {
                                xmlDocument = XmlUtil.getDocument(mircPath);
                                canEditBool = userIsAuthorizedTo("update", xmlDocument, req);
                            } catch (Exception e) {
                                //The xml file doesn't parse.
                                canEditBool = false;
                                //continue, can still load viewer, but can't edit
                            }
                            String canEditStr = new String("false");
                            if (canEditBool == true) {
                                canEditStr = "true";
                            }

                            String codebase = reqURL.substring(0,k) + "dicomviewer/imagej";
                            StringBuffer applet = new StringBuffer();
                            
                            applet.append("<HTML><BODY>");
                            applet.append("<APPLET ");
                            applet.append("CODEBASE=\""+codebase+"\" "); //should probably put this in the MIRC directory...so that they do not get overwritten
                            applet.append("ID=\"viewerApplet\" ");
                            applet.append("ARCHIVE=\"ij.jar\" ");
                            applet.append("CODE=\"ij.ImageJApplet.class\" "); //always ends in .class
                            applet.append("security=\"all-permissions\" " );
                            applet.append("NAME=\"Viewer.java\" ");
                            applet.append("WIDTH=\"100% \" ");
                            applet.append("HEIGHT=\"100%\" ");
                            applet.append("HSPACE=\"0\" ");
                            applet.append("VSPACE=\"0\" ");
                            applet.append("ALIGN=\"top\" ");
                            applet.append("MAYSCRIPT=\"mayscript\">");
                            //the heap size argument will only work with recent versions of the java plugin
                            //increasing the heap size is essential for viewing / saving large images
                            //applet.append("<PARAM NAME=\"MAYSCRIPT\" value=\"true\">");
                            applet.append("<PARAM name=\"java_arguments\" value=\"-Xmx512m\">");
                            applet.append("<PARAM name=\"cache_option\" value=\"Browser\">"); //may need to change this to plugin
                            applet.append("<PARAM name=\"cache_archive\" value=\"ij.jar\">");
                            applet.append("<PARAM NAME=url1 VALUE=\""+reqURL+"\">");
                            //these parameters are sent to imageja and used to save edited image back to mirc server
                            applet.append("<PARAM NAME=var1 VALUE=\"host="+req.getHeader("host")+"\">");
                            applet.append("<PARAM NAME=var2 VALUE=\"authorization="+req.getHeader("authorization")+"\">");
                            applet.append("<PARAM NAME=var3 VALUE=\"useragent="+req.getHeader("user-agent")+"\">");
                            applet.append("<PARAM NAME=var4 VALUE=\"accept="+req.getHeader("accept")+"\">");
                            applet.append("<PARAM NAME=var5 VALUE=\"keepalive="+req.getHeader("keep-alive")+"\">");
                            applet.append("<PARAM NAME=var6 VALUE=\"referer="+req.getHeader("referer")+"\">");
                            applet.append("<PARAM NAME=var7 VALUE=\"cookie="+req.getHeader("cookie")+"\">");
                            applet.append("<PARAM NAME=var8 VALUE=\"canedit="+canEditStr+"\">");
                            applet.append("<PARAM NAME=var9 VALUE=\"filename="+dicomFileName+"\">");
                            applet.append("</APPLET>");
                            applet.append("</BODY></HTML>");

				ServletUtil.sendPage(res, applet.toString(), false);
				return;
			}
		}

		//Handle requests to serve anything else here.
		//Most requests are handled by the Tomcat
		//Coyote connector, but some need to be caught here.
		if (!requestPathLC.endsWith(".xml")) {
			String contentType = ContentType.getContentType(requestPath);
			boolean result = ServletUtil.sendBinaryFileContents(
				res,
				contentType,
				file,
				requestPathLC.endsWith(".dcm"));
			return;
		}

		//See if the document parses
		Document xmlDocument;
		try { xmlDocument = XmlUtil.getDocument(file); }
		catch (Exception e) {
			//The xml file doesn't parse.
			//Send the error message back as html.
			returnExceptionMessage(res,"Parse Exception",e.getMessage());
			return;
		}

		//It parses, see if this is a MIRCdocument
		String rootName = xmlDocument.getDocumentElement().getTagName();
		if (!rootName.equals("MIRCdocument")) {
			//No, process it here.
			File xslFile =
				getTransformationFile(
					rootName, true, req.getParameter("xsl"), requestDirectory);
			if (xslFile == null) {
				//Suppress transformation; just return the file.
				ServletUtil.sendTextFileContents(res,"text/xml; charset=\"UTF-8\"",file);
				return;
			}
			//We have a transformation file, transform the document and return the result.
			String page = "";
			try { page = XmlUtil.getTransformedText(xmlDocument,xslFile); }
			catch (Exception e) {
				//We got an error during the transformation.
				//Send the error message back as html.
				returnExceptionMessage(res,"Transform Exception",e.getMessage());
				return;
			}
			//The transformation succeeded, return the page.
			//Choose the content type based on the page contents.
			res.setContentType(getContentType(page) + "; charset=\"UTF-8\"");
			PrintWriter out = res.getWriter();
			out.print(page);
			return;
		}

		//This is a MIRCdocument, see if this is a zip export
		String zipParameter = req.getParameter("zip");
		String myrsnaParameter = req.getParameter("myrsna");
		if (zipParameter != null) {
			//Check whether export is authorized.
			if (userIsAuthorizedTo("export", xmlDocument, req)) {
				//Export is authorized; make the filename for the zip file
				String extension = ".zip";
				String extParameter = req.getParameter("ext");
				if ((extParameter != null) && !extParameter.trim().equals(""))
					extension = "." + extParameter.trim();
				String zipFilename = getZipFilename(requestPath,extension);
				File zipFile = new File(getServletContext().getRealPath(requestDirectory + zipFilename));
				String zipResult = "";
				//See what kind of zip this is.
				zipParameter = zipParameter.trim();
				if (zipParameter.equals("")) {
					//This is a zip of the case.
					//First insert the path attribute.
					setPathAttribute(xmlDocument,requestPath,file);
					//Now zip the case.
					String[] filenames = getFilenames(xmlDocument, requestFilename);
					zipResult = zipfiles(filenames ,getServletContext(), requestDirectory, zipFilename);
				}
				else {
					//This is a zip of a (dataset) directory.
					File dir = new File(getServletContext().getRealPath(requestDirectory + zipParameter));
					if (!dir.exists()) {
						res.sendError(res.SC_NOT_FOUND);
						return;
					}
					zipResult = zipdirectory(dir,zipFile);
				}
				if (zipResult.equals("OK")) {
					if (myrsnaParameter == null) {
						ServletUtil.sendBinaryFileContents(res,"application/zip",zipFile,true);
					}
					else {
						if (exportToMyRsna(req.getRemoteUser(), /*getTitle(xmlDocument)*/ null, zipFile)) {
							ServletUtil.sendText(res, "text/plain", "The zip file was stored successfully.", false);
						}
						else {
							ServletUtil.sendText(res, "text/plain", "The zip file could not be stored.", false);
						}
					}
					//Make the access log entry
					if (!zipParameter.equals("no-phi")) AccessLog.makeAccessLogEntry(req,xmlDocument);
				}
				else returnExceptionMessage(res,"Zip Exception",zipResult);
				zipFile.delete();
			}
			else ServletUtil.authenticate(req,res,redirectPath);	//Export is not authorized.
			return;
		}

		//No, see if this is a DICOM export
		if (req.getParameter("dicom-export") != null) {
			//Determine whether to allow the DICOM export.
			//The user must be the admin user.
			if (userIsAdmin(req)) {
				int count = 0;
				File[] exportDirs = TrialConfig.getDicomExportDirectoryFiles();
				if (exportDirs.length > 0) {
				File dir = file.getParentFile();
				File[] files = dir.listFiles();
					for (int i=0; i<files.length; i++) {
						if (DicomObject.hasTypicalDicomFilename(files[i].getName())) {
							String name = files[i].getName();
							name = name + ".qe";
							File qeFile = new File(dir,name);
							for (int k=0; k<exportDirs.length; k++) {
								ExportQueueElement eqe = new ExportQueueElement(qeFile,files[i]);
								try {
									eqe.queue(exportDirs[k]);
									count++;
								}
								catch (Exception ignore) { }
							}
						}
					}
				}
				ServletUtil.sendPageNoCache(
					res,
					HtmlUtil.getPageWithCloseButton(
						"DICOM Export",
						"Number of DICOM objects queued for DICOM export: " + count));
			}
			else ServletUtil.authenticate(req,res,redirectPath);	//Export is not authorized.
			return;
		}

		//No, see if this is a database export
		boolean documentIsResearchDataset =
			XmlUtil.getValueViaPath(xmlDocument,"MIRCdocument/document-type")
				.toLowerCase().equals("research dataset");
		if (req.getParameter("database-export") != null) {
			if (userIsAdmin(req) && documentIsResearchDataset) {
				File dir = file.getParentFile();
				int dcmCount = 0;
				dcmCount += queueFilesForDatabase(dir,xmlDocument,".dcm","alternative-image","src");
				dcmCount += queueFilesForDatabase(dir,xmlDocument,".dcm","image","src");
				dcmCount += queueFilesForDatabase(dir,xmlDocument,".dcm","image","href");
				int mdCount = queueFilesForDatabase(dir,xmlDocument,null,"metadata","href");

				ServletUtil.sendPageNoCache(
					res,
					HtmlUtil.getPageWithCloseButton(
						"Database Export",
						"Number of DICOM objects queued for database export: " + dcmCount +
						"<br/>Number of metadata objects queued for database export: " + mdCount));
			}
			else ServletUtil.authenticate(req,res,redirectPath);	//Export is not authorized.
			return;
		}

		//Handle it as a normal XML file service.
		//Check whether read is authorized.
			if (!userIsAuthorizedTo("read", xmlDocument, req)) {
				//Read is not authorized.
				ServletUtil.authenticate(req,res,redirectPath);
				return;
			}

		//Read is authorized. Get the transformation file.
		//Allow users who are authorized to export the opportunity to specify
		//the XSL transformation file to be used.
		File xslFile =
			getTransformationFile(
				rootName,
				userIsAuthorizedTo("export", xmlDocument, req),
				req.getParameter("xsl"),
				requestDirectory);

		//If the transformation is suppressed, return the file.
		if (xslFile == null) {
			ServletUtil.sendTextFileContents(res,"text/xml; charset=\"UTF-8\"",file);
			return;
		}

		//Okay, we have an xsl transform file.
		//Set up the parameters.
		String username = req.getRemoteUser();
		String editurl = "";
		String addurl = "";
		if (userIsAuthorizedTo("update", xmlDocument, req)) {
			editurl = contextPath + "/author/update?doc=" + requestPath;
			addurl = contextPath + "/author/add?doc=" + requestPath;
		}
		String publishurl = "";
		if (userIsAuthorizedTo("update", xmlDocument, req) && req.isUserInRole("publisher"))
			publishurl = contextPath + "/author/publish?doc=" + requestPath;
		String deleteurl = "";
		if (userIsAuthorizedTo("delete", xmlDocument, req))
			deleteurl = contextPath + "/author/delete?doc=" + requestPath;
		String exporturl = "";
		if (userIsAuthorizedTo("export", xmlDocument, req))
			exporturl = contextPath + "/" + requestPath + "?zip";
		String filecabineturl = "";
		if ((username != null) && !exporturl.equals(""))
			filecabineturl = "/file/service/save";
		String dicomexporturl = "";
		if (userIsAdmin(req))
			dicomexporturl = contextPath + "/" + requestPath + "?dicom-export";
		String databaseexporturl = "";
		if (userIsAdmin(req) && documentIsResearchDataset)
			databaseexporturl = contextPath + "/" + requestPath + "?database-export";
		String unknown = req.getParameter("unknown");
		if (unknown == null) unknown = "no";
		String serverURL = req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort();
		String dirPath = "/" + requestPath.substring(0,requestPath.lastIndexOf("/")+1);
		String preview = req.getParameter("preview");
		if ((preview != null) && (preview.equals("yes") || preview.trim().equals(""))) preview = "yes";
		else preview = "no";
		String bgcolor = req.getParameter("bgcolor");
		bgcolor = ((bgcolor != null) && !bgcolor.trim().equals("")) ?
							bgcolor.trim() :
							xmlDocument.getDocumentElement().getAttribute("background");
		String display = req.getParameter("display");
		display = ((display != null) && !display.trim().equals("")) ?
							display.trim() :
							xmlDocument.getDocumentElement().getAttribute("display");
		String icons = req.getParameter("icons");
		icons = (icons != null) ? icons.trim() : "";

		//Okay, now set up the params for the transformation.
		String[] params = new String[] {
			"today",			StringUtil.getDate().replaceAll("-",""),
			"preview",			preview,
			"bgcolor",			bgcolor,
			"icons",			icons,
			"display",			display,
			"unknown",			unknown,
			"context-path",		contextPath,
			"dir-path",			dirPath,
			"doc-path",			contextPath + "/" + requestPath,
			"export-url",		exporturl,
			"edit-url",			editurl,
			"add-url",			addurl,
			"publish-url",		publishurl,
			"delete-url",		deleteurl,
			"filecabinet-url",	filecabineturl,
			"user-is-authenticated", (userIsAuthenticated(req) ? "yes" : "no"),
			"user-is-owner",	(userIsOwner(xmlDocument, req) ? "yes" : "no"),
			"user-is-admin",	(userIsAdmin(req) ? "yes" : "no"),
			"user-has-myrsna-acct",	(userHasMyRsnaAcct(req) ? "yes" : "no"),
			"server-url",		serverURL,
			"doc-url",			req.getRequestURL().toString(),
			"dicom-export-url",	dicomexporturl,
			"database-export-url",databaseexporturl
		};

		//Now we're ready; let's do it.
		String page = "";
		try { page = XmlUtil.getTransformedText(xmlDocument,xslFile,params); }
		catch (Exception e) {
			//We got an error during the transformation.
			//Send the error message back as html.
			returnExceptionMessage(res,"Transform Exception",e.getMessage());
			return;
		}
		//The transformation succeeded, return the page with an appropriate content type.
		res.setContentType(getContentType(page) + "; charset=\"UTF-8\"");
		PrintWriter out = res.getWriter();
		out.print(page);
		AccessLog.makeAccessLogEntry(req, xmlDocument);
		DocAccessLog.makeAccessLogEntry(req);
	}

	//Get a name for a zip file from the path.
	//The path looks like documents/xxx[/yyy]/something.xml.
	//Make the name as xxx[_yyy].ext.
	//Note: the ext argument must start with a period.
	private String getZipFilename(String path, String ext) {
		int k = path.indexOf("/");
		if (k != -1) path = path.substring(k+1);
		k = path.lastIndexOf("/");
		if (k != -1) path = path.substring(0,k);
		path = path.replace("/","_");
		path += ext;
		return path;
	}

	//Get a content type for a transformed page based on its beginning text.
	//If it starts with "<?xml", return "text/xml".
	//If it starts with "<html", return "text/html".
	//Otherwise, return "text/plain".
	private String getContentType(String page) {
		String bt = page.substring(0,Math.min(100,page.length())).trim();
		if (bt.startsWith("<?xml")) return "text/xml";
		if (bt.startsWith("<html")) return "text/html";
		return "text/plain";
	}

	//Get the transformation file for a request.
	private File getTransformationFile(
			String rootName, //the root element name
			boolean allowXSL, //true if xslParam is allowed; false otherwise
			String xslParam, //the xsl parameter from the request
			String requestDirectory	//the directory in which the xml file is located
			) {
		String xsl = rootName;
		if (allowXSL && (xslParam != null)) xsl = xslParam;
		xsl = xsl.trim();
		File xslFile = null;
		if (!xsl.equals("")) {
			if (!xsl.endsWith(".xsl")) xsl += ".xsl";
			//See if the transformation file is in the directory with the xml file.
			xslFile = new File(getServletContext().getRealPath(requestDirectory + xsl));
			if (!xslFile.exists()) {
				//Not there, look in the root directory.
				xslFile = new File(getServletContext().getRealPath(xsl));
			}
			if (!xslFile.exists()) xslFile = null;
		}
		return xslFile;
	}

	//Set the path attribute in a MIRCdocument.
	//If any error occurs, leave the document alone.
	//If the document is not a MIRCdocument, leave the document alone.
	private void setPathAttribute(Document doc, String value, File file) {
		try {
			Element root = doc.getDocumentElement();
			if (!root.getTagName().equals("MIRCdocument")) return;
			String currentValue = root.getAttribute("path");
			if ((currentValue != null) && currentValue.equals(value)) return;
			root.setAttribute("path",value);
			FileUtil.setFileText(file,XmlUtil.toString(doc));
		}
		catch (Exception ex) { }
	}

	//Find files and queue them for the DatabaseExportService.
	private int queueFilesForDatabase(
					File dir,
					Document xmlDocument,
					String extension,
					String nodeName,
					String attrName) {

		String[] files = XmlUtil.getAttributeValues(xmlDocument,nodeName,attrName);
		File exportDir = TrialConfig.getDatabaseExportDirectoryFile();
		int count = 0;
		for (int i=0; i<files.length; i++) {
			if ((extension == null) || files[i].endsWith(extension)) {
				String name = files[i];
				try {
					File file = new File(dir,name);
					if (file.exists()) {
						ExportQueueElement eqe = ExportQueueElement.createEQE(file);
						eqe.queue(exportDir);
						count++;
					}
				}
				catch (Exception ignore) { }
			}
		}
		return count;
	}

	//Send an HTML page containing an error message
	private void returnExceptionMessage(
						HttpServletResponse res,
						String title,
						String message) throws IOException {
		res.setContentType("text/html; charset=\"UTF-8\"");
		PrintWriter out = res.getWriter();
		out.print( "<html><head><title>" + title + "</title></head>"
			+ "<body><h3>XML Server Exception</h3><p>" + message + "</p></body></html>" );
		out.flush();
	}

	//Get the local references for an XML document
	private String[] getFilenames(Document mircDocument, String filename) {
		LinkedList names = new LinkedList();
		names.add(filename);
		Node root = mircDocument.getDocumentElement();
		getAttributeFilenames(names,root);
		return (String[])names.toArray(new String[names.size()]);
	}

	private void getAttributeFilenames(LinkedList names, Node node) {
		if (node.getNodeType() == Node.ELEMENT_NODE) {
			NamedNodeMap attrMap = node.getAttributes();
			int attrlen = attrMap.getLength();
			for (int i=0; i<attrlen; i++) {
				String name = attrMap.item(i).getNodeName();
				if (name.equals("docref") || name.equals("href") || name.equals("src")) {
					String value = attrMap.item(i).getNodeValue();
					if ((value.indexOf('/') == -1) &&	(value.indexOf(':') == -1)) names.add(value);
				}
			}
			NodeList children = node.getChildNodes();
			if (children != null) {
				for (int i=0; i<children.getLength(); i++) {
					getAttributeFilenames(names,children.item(i));
				}
			}
		}
	}

	//Zip a collection of files.
	//All the input files and the output zip file are in the same directory.
	private static synchronized String zipfiles(
				String[] filelist,
				ServletContext sc,
				String directory,
				String zipfilename) {

		File f = new File(sc.getRealPath(directory + zipfilename));

		try {

			//Get the various streams and buffers
			FileOutputStream fout = new FileOutputStream(f);
			ZipOutputStream zout = new ZipOutputStream(fout);
			File in;
			FileInputStream fin;
			ZipEntry ze;
			byte[] buffer = new byte[10000];
			int bytesread;

			//Sort the list so we can more easily find duplicates
			Arrays.sort(filelist);

			//Add all the files to the zip file
			for (int i=0; i < filelist.length; i++) {

				if ((i==0) || !filelist[i].equals(filelist[i-1])) {

					//Not a duplicate of a previously added file, try to add it
					ze = new ZipEntry(filelist[i]);
					in = new File (sc.getRealPath(directory + filelist[i]));
					if (in.exists()) {

						//The file exists - oh happy day
						fin = new FileInputStream(in);
						zout.putNextEntry(ze);
						while ((bytesread = fin.read(buffer)) > 0) {
							zout.write(buffer,0,bytesread);
						}
						zout.closeEntry();
						fin.close();
					}
				}
			}
			zout.close();
		}

		catch (Exception ex) {
			//No joy - return the exception message
			return "Error: " + ex.getMessage();
		}

		//Everything worked, return OK
		return "OK";
	}

	//Zip a directory and its subdirectories.
	private static synchronized String zipdirectory(File dir, File zipFile) {
		try {
			//Get the parent and find out how long it is
			File parent = dir.getParentFile();
			int rootLength = parent.getAbsolutePath().length() + 1;

			//Get the various streams and buffers
			FileOutputStream fout = new FileOutputStream(zipFile);
			ZipOutputStream zout = new ZipOutputStream(fout);
			zipdirectory(zout,dir,rootLength);
			zout.close();
		}
		catch (Exception ex) {
			//No joy - return the exception message
			return "Error: " + ex.getMessage();
		}
		//Everything worked, return OK
		return "OK";
	}

	//Zip a directory and its subdirectories
	//into a ZipOutputStream, setting the root of the zip package to be the parent
	//directory of the originally requested directory.
	private static synchronized void zipdirectory(ZipOutputStream zout, File dir, int rootLength)
												throws Exception {
		if (dir.isDirectory()) {
			File[] files = dir.listFiles();
			for (int i=0; i<files.length; i++) {
				if (files[i].isDirectory()) zipdirectory(zout,files[i],rootLength);
				else zipfile(zout,files[i],rootLength);
			}
		}
	}

	//Zip a file into a ZipOutputStream, setting the
	//root of the zip package to be the parent directory
	//of the originally requested directory.
	private static synchronized void zipfile(ZipOutputStream zout, File file, int rootLength)
												throws Exception {
		FileInputStream fin;
		ZipEntry ze;
		byte[] buffer = new byte[10000];
		int bytesread;
		String entryname = file.getAbsolutePath().substring(rootLength);
		ze = new ZipEntry(entryname);
		if (file.exists()) {
			fin = new FileInputStream(file);
			zout.putNextEntry(ze);
			while ((bytesread = fin.read(buffer)) > 0) zout.write(buffer,0,bytesread);
			zout.closeEntry();
			fin.close();
		}
	}

    /**
      * Determine whether a remote user is the owner of a MIRCdocument.
      * @param docXML the MIRCdocument DOM object.
      * @param req the servlet request identifying the remote user.
      */
	public static boolean userIsOwner(Node docXML, HttpServletRequest req) {
		try {
			String username = req.getRemoteUser();
			if (username != null) {
				String owner = XmlUtil.getValueViaPath(docXML,"MIRCdocument/authorization/owner").trim();
				if (owner.equals("")) return false;
				//The owner field can be a comma-separated list of usernames.
				//Make a list and check it against the username.
				//Note: this method filters out square brackets so that usernames
				//can be either without brackets or with them (as in the role-based
				//elements - read, update, export).
				String[] owners = owner.replaceAll("[\\[\\],\\s]+",",").split(",");
				for (int i=0; i<owners.length; i++) {
					if (owners[i].equals(username)) return true;
				}
			}
		}
		catch (Exception e) { }
		return false;
	}

	private boolean userIsAuthenticated(HttpServletRequest req) {
		return (req.getRemoteUser() != null);
	}

    /**
      * Determine whether a remote user has the administrator role.
      * @param req the servlet request identifying the remote user.
      */
	public static boolean userIsAdmin(HttpServletRequest req) {
		String adminRoleName = StorageConfig.getAdminRoleName().trim();
		if (req.isUserInRole(adminRoleName)) return true;
		return false;
	}

    /**
      * Determine whether a remote user is authorized for a specified
      * action on a MIRCdocument. Possible actions are read, update, export,
      * and delete. An administrator and a document owner are authorized
      * to take any action. Other users are subject to the constraints
      * imposed in the authorization element.
      * @param action the requested action.
      * @param docXML the MIRCdocument DOM object.
      * @param req the servlet request identifying the remote user.
      */
	public static boolean userIsAuthorizedTo(String action, Node docXML, HttpServletRequest req) {
		try {
			//The owner is authorized to do anything.
			if (userIsOwner(docXML,req)) return true;

			//The admin user is allowed to do anything
			if (userIsAdmin(req)) return true;

			//For the delete action, only the owner or admin is ever authorized.
			//Therefore, if the action is delete, return false now.
			if (action.equals("delete")) return false;

			//For non-owners or non-authenticated users, the rule is that if an action
			//authorization does not exist in the document, read and export actions are
			//authorized, but update actions are not.

			//See if the action authorization exists in the document.
			Node roleListNode = XmlUtil.getElementViaPath(docXML,"MIRCdocument/authorization/"+action);
			if (roleListNode == null) return !action.equals("update");

			//OK, the action authorization exists. Now the rule is that the user must
			//be authenticated and must have a role that is explicitly authorized.

			//Get the list of roles
			String roleList = XmlUtil.getElementValue(roleListNode).trim();

			//See if the list is blank, which authorizes nobody.
			if (roleList.equals("")) return false;

			//See if the list includes an asterisk, which authorizes anybody.
			if (roleList.indexOf("*") != -1) return true;

			//It is not a blanket authorization; check the roles individually.
			//The list can be separated by commas or whitespace.
			//If a specific user is included, it must be placed in [...].
			String user = req.getRemoteUser();
			String[] roles = roleList.replaceAll("[,\\s]+",",").split(",");
			for (int i=0; i<roles.length; i++) {
				String role = roles[i].trim();
				if (role.startsWith("[") && role.endsWith("]")) {
					//It's a username, see if it is the current user.
					role = role.substring(1,role.length()-1).trim();
					if ((user != null) && role.equals(user)) return true;
				}
				else if (req.isUserInRole(role)) return true;
			}
		}
		catch (Exception e) { }
		return false;
	}

	private boolean userHasMyRsnaAcct(HttpServletRequest req) {
		try {
			String mircUsername = req.getRemoteUser();
			if (mircUsername == null) return false;
			MyRsnaUsers mrus = MyRsnaUsers.getInstance();
			if (mrus == null) return false;
			MyRsnaUser mru = mrus.getMyRsnaUser(mircUsername);
			if (mru == null) return false;
			return true;
		}
		catch (Exception ex) { return false; }
	}

	private boolean exportToMyRsna(String mircUsername, String title, File zipFile) {
		try {
			if (mircUsername == null) return false;
			MyRsnaSession mrs = MyRsnaSessions.getInstance().getMyRsnaSession(mircUsername);
			if (mrs == null) return false;
			boolean result = mrs.postFile(zipFile, title);
			return result;
		}
		catch (Exception ex) { return false; }
	}

	private String getTitle(Document xmlDoc) {
		Element root = xmlDoc.getDocumentElement();
		Node child = root.getFirstChild();
		while (child != null) {
			if ((child.getNodeType() == Node.ELEMENT_NODE) &&
				child.getNodeName().equals("title"))
					return child.getTextContent();
			child = child.getNextSibling();
		}
		return null;
	}

}
