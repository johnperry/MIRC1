/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.storageservice;

import com.oreilly.servlet.MultipartRequest;
import java.awt.Dimension;
import java.io.*;
import java.net.*;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.apache.log4j.Logger;
import org.rsna.mircsite.util.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The top-level class of the MIRC Author Service.
 * <p>
 * The Author Service provides on-line authoring tools to
 * allow authorized users to create MIRCdocuments using
 * a browser.
 * <p>
 * This servlet responds to both HTTP GET and POST.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class AuthorService extends HttpServlet {

	static final Logger logger = Logger.getLogger(AuthorService.class);
	static String textext = ".txt";
	static String[] textExtensions = textext.split(",");

	/**
	 * The servlet method that responds to an HTTP GET.
	 * <p>
	 * If called with no query string, it returns the
	 * template selection page to the user in the response text.
	 * <p>
	 * If called with a query string, it interprets the string as
	 * the specification of a document to edit, opens the document and
	 * returns it to the user in a form for editing.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 * @throws ServletException if the servlet cannot handle the request.
	 */
	public void doGet(HttpServletRequest req, HttpServletResponse res)
											throws ServletException {
		//Get the user's name and information
		String userName = req.getRemoteUser();
		//Get the author from the authors.xml file.
		//Send an error page if the user was not authenticated.
		Author author = getAuthor(userName,res);
		//Bail out if the author wasn't authenticated.
		if (author == null) return;

		//See what kind of GET this is by checking the path info.
		//(The path info is the text after the servlet path and before the query string.)
		String pathInfo = req.getPathInfo();

		if ((pathInfo == null) || pathInfo.equals("") || pathInfo.equals("/")) {
			//This is a GET for the template selection page.
			try {
				res.setContentType("text/html; charset=\"UTF-8\"");
				PrintWriter out = res.getWriter();
				out.print(
					XmlUtil.getTransformedText(
						StorageConfig.xml,
						new File(StorageConfig.basepath + "template-selector.xsl"),
						author.getInfoParams() ));
				out.close();
			}
			catch (Exception e) {
				//A serious error has occurred. Possibly storage.xml or template-selector.xsl
				//are missing from the storage service root directory, or they may not parse.
				//In any case, we are dead. Since these are problems with the setup of the
				//server, throw a ServletException at the servlet container.
				throw new ServletException(
					"StorageService exception generating the template selection page:\n" + e.getMessage());
			}
		}
		else if (pathInfo.toLowerCase().equals("/publish")) {
			//This is a GET from the "Publish" link on a MIRCdocument display.
			//Get the name of the document and verify it.
			String docPath = req.getParameter("doc");
			if ((docPath == null) || (docPath.trim().equals(""))) {
				ServletUtil.sendError(res,res.SC_NOT_FOUND);
				return;
			}
			docPath = docPath.trim();
			File docFile = new File(StorageConfig.basepath + docPath);
			if (!docFile.exists()) {
				ServletUtil.sendError(res,res.SC_NOT_FOUND);
				return;
			}
			String dirPath = "/";
			int k = docPath.lastIndexOf("/");
			if (k >= 0) dirPath = "/" + docPath.substring(0,k+1);
			dirPath = req.getContextPath() + dirPath;
			try {
				//Get the document and verify that the user has access to it.
				Document docXML = XmlUtil.getDocument(docFile);
				if (!XMLServer.userIsAuthorizedTo("update",docXML,req) ||
						!req.isUserInRole("publisher")) {
					ServletUtil.sendError(res,res.SC_FORBIDDEN);
					return;
				}
				//Okay, the user can update the document and he is a publisher,
				//publish the document.
				String docString = FileUtil.getFileText(docFile);
				docString = AuthorService.makePublic(docString);
				FileUtil.setFileText(docFile,docString);
				MircIndex.getInstance().insertDocument(docPath);
				//And remove it from the input queue, in case it is there.
				int entry = InputQueue.deleteQueueEntry(docPath);
				ServletUtil.sendPage(
					res,
					HtmlUtil.getPageWithCloseButton(
						"Publication Succeeded",
						"The document was published." +
						((entry==-1) ? "" : "<br>The input queue entry was removed.")));
			}
			catch (Exception e) { sendErrorMessage(res,e.getMessage()); }
		}
		else if (pathInfo.toLowerCase().equals("/update")) {
			//This is a GET from the "Edit" link on a MIRCdocument display.
			//Get the name of the document and verify it.
			String docPath = req.getParameter("doc");
			if ((docPath == null) || (docPath.trim().equals(""))) {
				ServletUtil.sendError(res,res.SC_NOT_FOUND);
				return;
			}
			docPath = docPath.trim();
			File docFile = new File(StorageConfig.basepath + docPath);
			if (!docFile.exists()) {
				ServletUtil.sendError(res,res.SC_NOT_FOUND);
				return;
			}
			try { sendEditForm(userName,author,docPath,docFile,"1",req,res); }
			catch (Exception e) { sendErrorMessage(res,e.getMessage()); }
		}
		else if (pathInfo.toLowerCase().equals("/add")) {
			//This is a GET from the "Add Images" link on a MIRCdocument display.
			//Get the name of the document and verify it.
			String docPath = req.getParameter("doc");
			if ((docPath == null) || (docPath.trim().equals(""))) {
				ServletUtil.sendError(res,res.SC_NOT_FOUND);
				return;
			}
			docPath = docPath.trim();
			File docFile = new File(StorageConfig.basepath + docPath);
			if (!docFile.exists()) {
				ServletUtil.sendError(res,res.SC_NOT_FOUND);
				return;
			}
			try { sendAddFilesForm(docPath,docFile,req,res); }
			catch (Exception e) { sendErrorMessage(res,e.getMessage()); }
		}
		else if (pathInfo.toLowerCase().equals("/delete")) {
			//This is a GET from the "Delete" link on a MIRCdocument display.
			//Get the name of the document and verify it.
			String docPath = req.getParameter("doc");
			if ((docPath == null) || (docPath.trim().equals(""))) {
				ServletUtil.sendError(res,res.SC_NOT_FOUND);
				return;
			}
			docPath = docPath.trim();
			File docFile = new File(StorageConfig.basepath + docPath);
			if (!docFile.exists()) {
				ServletUtil.sendError(res,res.SC_NOT_FOUND);
				return;
			}
			try {
				//Get the document and verify that the user is authorized to delete.
				Document docXML = XmlUtil.getDocument(docFile);
				if (!XMLServer.userIsOwner(docXML,req) &&
						!XMLServer.userIsAdmin(req)) {
					//No, return an error
					ServletUtil.sendError(res,res.SC_FORBIDDEN);
					return;
				}
				//Okay, the user is allowed to delete this document.
				File dirFile = docFile.getParentFile();

				//Remove it from the index.
				//It might not be in the index, but it doesn't hurt to try.
				MircIndex.getInstance().removeDocument(docPath);

				//Remove it from the input queue.
				//Again, it might not be there, but it doesn't hurt to try.
				InputQueue.deleteQueueEntry(docPath);

				//Remove the document. Use the admin method that moves the
				//entire document directory to the deleted-documents folder.
				if (!AdminService.removeDocument(dirFile)) {
					ServletUtil.sendPage(
						res,
						HtmlUtil.getPageWithBackButton(
							"Deletion Failed",
							"The document could not be removed from the server.", -2));
				}
				else {
					//Successful delete
					ServletUtil.sendPage(
						res,
						HtmlUtil.getPageWithBackButton(
							"Deletion Succeeded",
							"The document was removed from the server.", -2));
				}
				return;
			}
			catch (Exception e) { sendErrorMessage(res,e.getMessage()); }
		}
		else {
			//This path is unknown; just return the standard not found message.
			ServletUtil.sendError(res,res.SC_NOT_FOUND);
		}
	}

	/**
	 * The servlet method that responds to an HTTP POST.
	 * <p>
	 * This method interprets the form parameters as a query generated by the
	 * input for the author's name, affiliation, and contact information, and
	 * the choice of the template for a new document. It opens the template and
	 * returns it to the user for authoring.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 * @throws ServletException if the servlet cannot handle the request.
	 */
	public void doPost(HttpServletRequest req, HttpServletResponse res)
											throws ServletException {
		//Get the user's name and information
		String userName = req.getRemoteUser();
		//Get the author from the authors.xml file.
		//Send an error message if the user was not authenticated.
		Author author = getAuthor(userName,res);
		//Bail out if the author wasn't authenticated.
		if (author == null) return;

		//Force the encoding.
		//All author service forms set the accept-charset header to UTF-8.
		try { req.setCharacterEncoding("UTF-8"); }
		catch (Exception ignore) { }

		//Check the content type
		String requestContentType = req.getContentType();
		if (requestContentType == null) requestContentType = "";
		requestContentType = requestContentType.toLowerCase();
		//Get the path info
		String pathInfo = req.getPathInfo();

		//Figure out what kind of POST this is
		if (requestContentType.indexOf("application/x-www-form-urlencoded") >= 0) {

			//It's a POST of a form.
			//See how to handle it by checking the path info.
			if ((pathInfo == null) || pathInfo.equals("") || pathInfo.equals("/")) {
				//This is a POST of the template selection form.
				//Update the user's information
				author.setAuthorInfo(
					getFormInput(req,"name"),
					getFormInput(req,"affiliation"),
					getFormInput(req,"contact"));
				//Get the template file name
				String template = getFormInput(req,"template");
				//Process the template against the editor-form transform file.
				//Pass the userName and the parameters from the Author object.
				try {
					Document templateXML =
						XmlUtil.getDocument(getServletContext().getRealPath(template));
					res.setContentType("text/html; charset=\"UTF-8\"");
					PrintWriter out = res.getWriter();
					Object[] params =
						new Object[] {
							"template", template,
							"edit-owner","yes", //allow editing since the document is new
							"context",	req.getContextPath(),
							"servlet",  req.getContextPath()+req.getServletPath()+"/submit",
							"date",		StringUtil.getDate(),
							"mode",		StorageConfig.getMode(),
							"options",	StorageConfig.getEnumeratedValues(),
							"species",	StorageConfig.getSpeciesValues(),
							"version",	StorageConfig.version,
							"activetab","1"
						};
					params = author.getInfoParams(params);
					out.print(
						XmlUtil.getTransformedText(
							templateXML,
							new File(StorageConfig.basepath + "editor.xsl"),
							params ));
					out.flush();
					out.close();
				}
				catch (Exception e) { sendErrorMessage(res,e.getMessage()); }
			}
			else if (pathInfo.equals("/submit")) {
				//This is a POST of a new document.
				try {
					String docText = req.getParameter("doctext");
					String docName = req.getParameter("docname");
					String activeTab = req.getParameter("activetab");
					if ((docName == null) || docName.trim().equals("")) docName = "MIRCdocument.xml";
					docName = docName.trim().replaceAll("[\\s]+","_");
					docName = docName.replaceAll("[\"&'><]","_");
					docName = docName.replace("[","(");
					docName = docName.replace("]",")");
					if (!docName.toLowerCase().endsWith(".xml")) docName += ".xml";
					docName = makeDirectory() + docName;
					File docFile = new File(docName);
					docName = docName.substring(docName.indexOf("documents"));
					docText = insertFiles(docFile,docText,userName);
					//Make sure the document parses
					Document doc = checkDocument(docFile,docText);
					//Fix the SVG references and create the SVG files
					docText = updateAnnotationFiles(docFile,doc);
					//Okay, install it
					installDocument(req,docFile,docName,docText);
					docName = docName.replaceAll("\\\\","/");
					sendEditForm(userName,author,docName,docFile,activeTab,req,res);
				}
				catch (Exception e) { sendErrorMessage(res,e.getMessage()); }
			}
			else if (pathInfo.equals("/update")) {
				//This is an update of an existing document.
				try {
					String docText = req.getParameter("doctext");
					String docName = req.getParameter("template");
					String activeTab = req.getParameter("activetab");
					File docFile = new File(getServletContext().getRealPath(docName));
					docText = insertFiles(docFile,docText,userName);
					//Make sure the document parses
					Document doc = checkDocument(docFile,docText);
					//Fix the SVG references and create the SVG files
					docText = updateAnnotationFiles(docFile,doc);
					//Okay, install it
					installDocument(req,docFile,docName,docText);
					String preview = req.getParameter("preview");
					if (preview.equals("true"))
						ServletUtil.sendRedirector(res, req.getContextPath()+"/"+docName+"?preview");
					else
						sendEditForm(userName,author,docName,docFile,activeTab,req,res);
				}
				catch (Exception e) { sendErrorMessage(res,e.getMessage()); }
			}
			else {
				//This path is unknown; just return the standard not found message.
				ServletUtil.sendError(res,res.SC_NOT_FOUND);
			}
		}
		else if (requestContentType.indexOf("multipart/form-data") >= 0) {
			if (pathInfo.equals("/add")) {
				try {
					//Make a working directory to receive the submission.
					//This directory is in the storage service's root.
					String tempDirName = StringUtil.makeNameFromDate();
					File tempDirFile = new File(getServletContext().getRealPath(tempDirName));
					tempDirFile.mkdirs();

					//Get the values needed from the storage service configuration
					int maxsize = StorageConfig.getMaxSubmitSize();
					maxsize = Math.max(maxsize,5)*1024*1024;

					//Now get the posted files.
					MultipartRequest mpReq =
						new MultipartRequest(
							req, tempDirFile.getAbsolutePath(), maxsize, "UTF-8");

					//Okay, we have the files, if any, loaded into tempDirFile.
					//Get the list of files. These are the files to be added to the MIRCdocument.
					File[] files = tempDirFile.listFiles();

					//Get the MIRCdocument.
					String docPath = mpReq.getParameter("doc");
					File docFile = new File(getServletContext().getRealPath(docPath));
					MircDocument md = new MircDocument(docFile, docPath);

					//Process the files
					for (int i=0; i<files.length; i++) {
						insertFile(md, files[i], true); //allow unpacking of zip files
					}

					//Delete the temporary directory and all its contents
					FileUtil.deleteAll(tempDirFile);
					ServletUtil.sendRedirector(res, req.getContextPath()+"/"+docPath);
				}
				catch (Exception ex) {
					logger.warn("Add file error",ex);
					sendErrorMessage(res, ex.getMessage());
				}
			}
		}
		else sendMessage(res,"Error","Unsupported Content-Type: "+requestContentType);
	}

	private void insertFile(MircDocument md, File file, boolean unpackZipFiles) {
		if (file.isFile()) {
			FileObject object = FileObject.getObject(file);
			object.setStandardExtension();
			object.filterFilename("%20","_");
			object.filterFilename("\\s+","_");

			if ((object instanceof ZipObject) && unpackZipFiles) {
				ZipObject zObject = (ZipObject)object;
				ZipEntry[] entries = zObject.getEntries();
				for (int i=0; i<entries.length; i++) {
					try {
						//Okay, we have to be a bit careful not to overwrite
						//a file that has already been inserted into the MIRCdocument.
						File zFile = new File(entries[i].getName().replace('/',File.separatorChar));
						zFile = new File(md.docDir, zFile.getName());
						if (zFile.exists()) {
							zFile = File.createTempFile("ALT-","-"+zFile.getName(), md.docDir);
						}
						//Copy the file into the MIRCdocument's directory.
						zObject.extractFile(entries[i], md.docDir, zFile.getName());
						//Insert the file. Only unpack one level deep.
						insertFile(md, zFile, false);
					}
					catch (Exception ignore) { }
				}
			}
			else {
				//Move the file to the directory with the MIRCdocument,
				//changing the name if a file with that name already
				//exists in the directory.
				object.moveToDirectory(md.docDir);

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
					//Insert it into the document as a metadata object.
					md.insert(object,object.getFile().getName());
					if (object.hasMatchingExtension(textExtensions,true)) {
						md.insert(object.getFile());
					}
				}
			}
		}
	}

	//Get an Author element corresponding to the userName.
	//If the user is not authenticated, return an error page.
	private Author getAuthor(String userName, HttpServletResponse res) {
		//Send an error message if the user hasn't been authenticated.
		//This should never happen (if the web.xml file is set up correctly).
		if (userName == null) {
			ServletUtil.sendError(res,res.SC_FORBIDDEN);
			return null;
		}
		//Get the author from the authors.xml file
		return new Author(userName,StorageConfig.basepath + "/authors.xml");
	}

	//Fetch a named element from the form data in the POST
	private String getFormInput(HttpServletRequest req, String name) {
		String values[];
		values = req.getParameterValues(name);
		if (values != null) return values[0].trim();
		return "";
	}

	//Get any files referenced in the user's file cabinet.
	private String insertFiles(File docFile, String docText, String userName) {
		try {
			//Get a File pointing to the directory where the document is stored.
			File parentDir = docFile.getParentFile();

			//Look for attributes pointing to files in the cabinets
			int i = 0;
			int ii;
			int k,kk;
			//Find and insert the images
			String imageStartTag = "<image src=\"[";
			String imageEndTag = "</image>";
			while ((i=docText.indexOf(imageStartTag,i)) != -1) {
				ii = docText.indexOf(imageEndTag,i) + 8;
				k = docText.indexOf("]",i) + 1;
				kk = docText.indexOf("\"",k);
				String path = docText.substring( i+imageStartTag.length(), k-1);
				String inName = docText.substring(k,kk);
				File outFile = null;

				//Get the file.
				if (!path.startsWith("myRSNA|")) {
					//This is a file in a MIRC file cabinet; path points to
					//the cabinet and inName is the name of the file.
					File inFile = getCabinetFile(path, inName, userName);
					String outName = inName.replaceAll("\\s++","_");
					outFile = new File(parentDir, outName);
					FileUtil.copyFile(inFile, outFile);
				}
				else {
					//This is a file in the user's myRSNA files, path
					//is "myRSNA|" followed by the title of the file on
					//the myRSNA site. This is the name that has the proper
					//extension. The inName variable now contains the node
					//id on the myRSNA site.
					String title = path.substring(7);
					//Remove the spaces in the title, if any
					title = title.replaceAll("\\s+", "_");
					outFile = new File(parentDir, title);

					//Get the file from the myRSNA site.
					//Note that the MyRsnaSession.getFile(File, String)
					//method is static. We don't actually require a
					//MyRsnaSession instance because file accesses are
					//not authenticated (which seems odd, but it's true).
					//The getFile method is in MyRsnaSession because it
					//seems to be a nice home for it in case the MyRSNA
					//developers change the authentication requirements
					//some day.
					if (!MyRsnaSession.getFile(outFile, inName)) {
						//The RSNA site is not available, or somebody is doing
						//something funny, skip this image element, even though
						//it may cause problems in viewing the document.
						i++;
						break;
					}
				}

				try {
					MircImage image = new MircImage(outFile);
					int maxWidth = getMaxWidth(docText,i);
					int minWidth = getMinWidth(docText,i);
					String imageElement = insertImage(image,maxWidth,minWidth);
					docText = docText.substring(0,i) + imageElement + docText.substring(ii);
					i += imageElement.length();
				}
				catch (Exception ignore) {
					logger.warn("Exception processing image:",ignore);
					i++; //skip this element so we don't loop forever
				}
			}
			//Fix any other file cabinet references and get the files
			docText = handleCabinetFileRefs(docText, "Shared/Files", parentDir, userName);
			docText = handleCabinetFileRefs(docText, "Personal/Files", parentDir, userName);
		}
		catch (Exception e) { }
		return docText;
	}

	public String handleCabinetFileRefs(String string, String prefix, File parentDir, String username) {
		try {
			Pattern pattern = Pattern.compile("=\"\\["+prefix+"[^\\s\\]*]([^\"]+\")");
			Matcher matcher = pattern.matcher(string);
			StringBuffer sb = new StringBuffer();
			while (matcher.find()) {
				//Get the filenames
				String group = matcher.group();
				String path = group.substring(3, group.indexOf("]")).trim();
				File file = new File(path);
				String filename = file.getName();
				String outName = filename.replaceAll("\\s+","_");

				//Replace the string in the submission with the correct reference
				String repl = matcher.quoteReplacement("=\""+outName+"\"");
				matcher.appendReplacement(sb, repl);

				//Now copy the file
				File inFile = getCabinetFile(path, null, username);
				File outFile = new File(parentDir, outName);
				FileUtil.copyFile(inFile,outFile);
			}
			matcher.appendTail(sb);
			return sb.toString();
		}
		catch (Exception ex) {
			logger.warn(ex);
			return string;
		}
	}

	private File getCabinetFile(String path, String filename, String username) {
		File file = null;
		String shared = "Shared/";
		String personal = "Personal/";
		if (path.startsWith(shared)) {
			path = path.substring(shared.length());
			file = new File(StorageConfig.getFileservice(), "dept");
		}
		else if (path.startsWith(personal)) {
			path = path.substring(personal.length());
			file = new File(StorageConfig.getFileservice(),"users");
			file = new File(file, username);
		}
		if (path.equals("Files") || path.startsWith("Files/")) {
			file = new File(file, path);
			if (filename != null) file = new File(file, filename);
		}
		return file;
	}

	//Create or update SVG files for embedded annotations
	//and create the corresponding annotated JPEGs.
	private String updateAnnotationFiles(File docFile, Document doc) {
		String svgHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
		//Get a File pointing to the directory where the document is stored.
		File parentDir = docFile.getParentFile();
		Element root = doc.getDocumentElement();
		NodeList nodeList = root.getElementsByTagName("svg");
		for (int i=0; i<nodeList.getLength(); i++) {

			//Get the elements
			Element svg = (Element)nodeList.item(i);

			NodeList svgList = svg.getElementsByTagName("image");
			Element svgImage = (Element)svgList.item(0);

			Element altimg = (Element)svg.getParentNode();
			Element image = (Element)altimg.getParentNode();

			//Get the name of the image file on which the annotated
			//image will be based and get a file pointing to it.
			String src = image.getAttribute("src");
			File srcFile = new File(parentDir, src);

			//Get the MircImage for the source
			MircImage srcImage = null;
			try { srcImage = new MircImage(srcFile); }
			catch (Exception quit) { return XmlUtil.toString(doc); }

			//Make sure that the image is not 8-bit
			//to avoid the brightness bug in Java.
			try {
				if (srcImage.getPixelSize() == 8) {
					//8-bit pixels. Convert the image to a 24-bit JPEG
					//and update the SVG and MIRCdocument XML.

					//Get the appropriate name for the converted image
					String name = src;
					int k = name.lastIndexOf(".");
					if (k != -1) name = name.substring(0,k);
					name += "_base.jpeg";
					File convertedFile = new File(parentDir, name);

					//Convert the image
					int width = srcImage.getWidth();
					Dimension d = srcImage.saveAsJPEG(convertedFile,width,width);

					//Update the SVG
					svgImage.setAttribute("xlink:href", name);

					//Update the image element in the MIRCdocument
					image.setAttribute("src", name);
					image.setAttribute("w", Integer.toString(d.width));
					image.setAttribute("h", Integer.toString(d.height));

					//There should be no alternative-image element with
					//the role "original-format", so create one and set
					//it to point to the unconverted image.
					Element ofElement = doc.createElement("alternative-image");
					ofElement.setAttribute("role", "original-format");
					ofElement.setAttribute("src", src);
					image.appendChild(ofElement);
				}
			}
			catch (Exception ignore) { }

			//Get the appropriate name for the annotated file
			int k = src.lastIndexOf(".");
			if (k != -1) src = src.substring(0,k);
			if (src.endsWith("_base")) src = src.substring(0,src.length()-5);
			src += "_an";

			//Save the SVG file
			File svgFile = new File(parentDir,src+".svg");
			String svgText = XmlUtil.toString(svg);
			FileUtil.setFileText(svgFile, FileUtil.utf8, svgHeader + svgText);

			//Fix up the alternative-image that points to the SVG file
			altimg.setAttribute("src",src+".svg");
			altimg.setAttribute("type","svg");
			while (altimg.hasChildNodes()) altimg.removeChild(altimg.getFirstChild());

			//Now make the annotated jpeg.
			//Important note: The XML text generator in editor.js will not return an
			//<alternative-image type="image"> element for the annotated jpeg if it
			//returns an <svg> child of the <alternative-image type="svg"> element.
			//This means that we can create the type="image" element secure in the
			//knowledge that we are not creating a duplicate.
			try {
				SvgUtil.saveAsJPEG(svgFile,parentDir);
				//It worked, create the element to reference it.
				Element jpegElement = doc.createElement("alternative-image");
				jpegElement.setAttribute("role","annotation");
				jpegElement.setAttribute("type","image");
				jpegElement.setAttribute("src",src+".jpg");
				jpegElement.setAttribute("w", Integer.toString(srcImage.getWidth()));
				jpegElement.setAttribute("h", Integer.toString(srcImage.getHeight()));
				image.appendChild(jpegElement);
			}
			catch (Exception ex) {
				logger.warn("Unable to create the annotated JPEG file",ex);
			}
		}
		return XmlUtil.toString(doc);
	}

	//Figure out the maxWidth for a specific image.
	private int getMaxWidth(String text, int i) {
		String string = text.substring(0,i);
		int kImageSection = string.lastIndexOf("<image-section");
		int kSection = string.lastIndexOf("<section");
		if ((kImageSection == -1) && (kSection == -1)) return 512;
		if ((kImageSection == -1) || (kImageSection < kSection))
			//This image must be in a section element.
			return XmlStringUtil.getAttributeInt(text,kSection,"image-width",256);
		else {
			//This image must be in an image-section element.
			//Look for the image-width attribute and use it if it is present.
			//If it is not present, try the image-pane-width attribute and use it.
			//If all else fails, use 700.
			int width = XmlStringUtil.getAttributeInt(text,kImageSection,"image-width",-1);
			if (width >= 0) return width;
			return XmlStringUtil.getAttributeInt(text,kImageSection,"image-pane-width",700);
		}
	}

	//Figure out the minWidth for a specific image.
	private int getMinWidth(String text, int i) {
		String string = text.substring(0,i);
		int kImageSection = string.lastIndexOf("<image-section");
		int kSection = string.lastIndexOf("<section");
		if ((kImageSection == -1) && (kSection == -1)) return 512;
		if ((kImageSection == -1) || (kImageSection < kSection))
			//This image must be in a section element.
			return XmlStringUtil.getAttributeInt(text,kSection,"min-width",0);
		else {
			//This image must be in an image-section element.
			return XmlStringUtil.getAttributeInt(text,kImageSection,"min-width",0);
		}
	}

	//Create the child files for an image and return the XML elements for them.
	private String insertImage(MircImage image, int maxWidth, int minWidth) {
		int imageWidth = image.getColumns();
		int imageHeight = image.getRows();

		//Get a width for the base image.
		if (minWidth > maxWidth) minWidth = maxWidth;
		int width = Math.min(maxWidth,Math.max(imageWidth,minWidth));

		//Make the image element
		String name = image.getFile().getName();
		String nameNoExt = name.substring(0,name.lastIndexOf("."));
		File docDir = image.getFile().getParentFile();
		String insert = "";

		//Make the base image. Use the original if you can; otherwise, make one.
		if (image.isDicomImage() ||
				image.hasNonStandardImageExtension() ||
					(width != imageWidth)) {
			Dimension d_base = image.saveAsJPEG(new File(docDir,nameNoExt+"_base.jpeg"),width,minWidth);
			insert += "<image src=\""+nameNoExt+"_base.jpeg\" width=\""+width+"\" w=\""+d_base.width+"\" h=\""+d_base.height+"\">\n";
		}
		else {
			insert += "<image src=\""+name+"\" width=\""+width+"\" w=\""+imageWidth+"\" h=\""+imageHeight+"\">\n";
		}

		//Make the icons for the author service and the mstf display
		Dimension d_icon = image.saveAsJPEG(new File(docDir,nameNoExt+"_icon.jpeg"),64,0);
		Dimension d_icon96 = image.saveAsJPEG(new File(docDir,nameNoExt+"_icon96.jpeg"),96,0); //for the author service
		insert += "  <alternative-image role=\"icon\" src=\""+nameNoExt+"_icon.jpeg\" w=\""+d_icon.width+"\" h=\""+d_icon.height+"\"/>\n";

		//Make the full image if necessary
		if (imageWidth > maxWidth) {
			if (image.isDicomImage() || image.hasNonStandardImageExtension()) {
				Dimension d_full = image.saveAsJPEG(new File(docDir,nameNoExt+"_full.jpeg"),imageWidth,0);
				insert += "  <alternative-image role=\"original-dimensions\" src=\""+nameNoExt+"_full.jpeg\" w=\""+d_full.width+"\" h=\""+d_full.height+"\"/>\n";
			}
			else {
				insert += "  <alternative-image role=\"original-dimensions\" src=\""+name+"\" w=\""+imageWidth+"\" h=\""+imageHeight+"\"/>\n";
			}
		}

		//Finally, put in the original format image if necessary
		if (image.isDicomImage() || image.hasNonStandardImageExtension())
			insert += "  <alternative-image role=\"original-format\" src=\""+name+"\"/>\n";

		//and add in the end tag
		insert += "</image>\n";
		return insert;
	}

	//Send back an edit form for a document
	private void sendEditForm(String userName, Author author,
							  String docPath, File docFile, String activeTab,
							  HttpServletRequest req, HttpServletResponse res) throws Exception {
		String dirPath = "/";
		int k = docPath.lastIndexOf("/");
		if (k >= 0) dirPath = "/" + docPath.substring(0,k+1);
		dirPath = req.getContextPath() + dirPath;
		//Get the document and verify that the user has access to it.
		Document docXML = XmlUtil.getDocument(docFile);
		if (!XMLServer.userIsAuthorizedTo("update",docXML,req)) {
			ServletUtil.sendError(res,res.SC_FORBIDDEN);
			return;
		}
		//See if the user is the owner or the admin, and if so, allow editing of the owner field.
		boolean editOwner = XMLServer.userIsOwner(docXML,req) ||
							XMLServer.userIsAdmin(req);

		//Okay, the user is allowed to update this document.
		//Use the document as its own template and return an update form to the user.
		Object[] params =
			new Object[] {
				"username", userName,
				"template", docPath,
				"edit-owner",(editOwner ? "yes" : "no"),
				"dirpath",	dirPath,
				"context",	req.getContextPath(),
				"servlet",  req.getContextPath()+req.getServletPath()+"/update",
				"date",		"", //empty because you can't change the original pub date
				"mode",		StorageConfig.getMode(),
				"options",	StorageConfig.getEnumeratedValues(),
				"species",	StorageConfig.getSpeciesValues(),
				"icons",	getIcon96(docFile.getParentFile()),
				"version",	StorageConfig.version,
				"activetab",activeTab
			};
		params = author.getInfoParams(params);
		try {
			ServletUtil.sendPage(
				res,
				XmlUtil.getTransformedText(
					docXML,
					new File(StorageConfig.basepath + "editor.xsl"),
					params),
				false);
		}
		catch (Exception ex) {
			logger.warn("sendEditForm Exception",ex);
			throw ex;
		}
	}

	//Send back a form to add files to a document
	private void sendAddFilesForm(String docPath, File docFile,
					HttpServletRequest req, HttpServletResponse res) throws Exception {
		String dirPath = "/";
		int k = docPath.lastIndexOf("/");
		if (k >= 0) dirPath = "/" + docPath.substring(0,k+1);
		dirPath = req.getContextPath() + dirPath;
		//Get the document and verify that the user has access to it.
		Document docXML = XmlUtil.getDocument(docFile);
		if (!XMLServer.userIsAuthorizedTo("update",docXML,req)) {
			ServletUtil.sendError(res,res.SC_FORBIDDEN);
			return;
		}

		//Okay, the user is allowed to update this document.
		//Use the document as its own template and return an update form to the user.
		Object[] params =
			new Object[] {
				"docpath",	docPath,
				"context",	req.getContextPath(),
				"servlet",  req.getServletPath(),
				"extrapath","/add"
			};
		ServletUtil.sendPage(
			res,
			XmlUtil.getTransformedText(
				docXML,
				new File(StorageConfig.basepath + "add-files.xsl"),
				params),
			false);
	}

	//Find all the files whose names end in the suffix "_icon96.jpeg"
	//in a directory and return an XML Document containing elements
	//with their names (without the suffix)
	static DocumentBuilder db = null;
	private Document getIcon96(File dir) {
		String target = "_icon96.jpeg";
		int targetLength = target.length();

		try {
			//Get the factory if it isn't there
			if (db == null) {
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				db = dbf.newDocumentBuilder();
			}
			//Create a new document to hold the list
			Document document = db.newDocument();

			//Create the root node and add it
			Element root = document.createElement("icons");
			document.appendChild(root);

			//Get the list of Files in the directory
			File[] files = dir.listFiles();

			//Make elements for all the icon96.jpeg files
			String name;
			for (int i=0; i<files.length; i++) {
				if (!files[i].isDirectory() && files[i].getName().endsWith(target)) {
					name = files[i].getName();
					name = name.substring(0,name.length()-targetLength);
					checkForFile(new File(dir,name+".jpeg"),files[i],document,root);
					checkForFile(new File(dir,name+".jpg"),files[i],document,root);
					checkForFile(new File(dir,name+".gif"),files[i],document,root);
					checkForFile(new File(dir,name+".png"),files[i],document,root);
					checkForFile(new File(dir,name+"_base.jpeg"),files[i],document,root);
					checkForFile(new File(dir,name+"_full.jpeg"),files[i],document,root);
					checkForFile(new File(dir,name+"_full.jpg"),files[i],document,root);
					checkForFile(new File(dir,name+"_full.gif"),files[i],document,root);
					checkForFile(new File(dir,name+"_full.png"),files[i],document,root);
				}
			}
			return document;
		}
		catch (Exception e) { }
		return null;
	}

	//Add a file element child if a specific file exists.
	private void checkForFile(File imageFile, File iconFile, Document document, Element element) {
		if (imageFile.exists()) {
			Element child = document.createElement("file");
			element.appendChild(child);
			Attr attr = document.createAttribute("name");
			attr.setValue(imageFile.getName());
			child.setAttributeNode(attr);
			attr = document.createAttribute("icon");
			attr.setValue(iconFile.getName());
			child.setAttributeNode(attr);
		}
	}

	//Send back a page with a message and a close button.
	private void sendMessage(HttpServletResponse res, String title, String message) {
		ServletUtil.sendPage(
			res,
			HtmlUtil.getPageWithBackButton(title,message));
	}

	//Send back an error message
	private void sendErrorMessage(HttpServletResponse res, String error) {
		sendMessage(
			res,
			"Error",
			"An error occurred while attempting to process the document.<br/><br/>" +
			error.replace("<","&lt;").replace(">","&gt;"));
	}

	//Create a directory to receive a document
	private String makeDirectory() throws IOException {
		String reldir = "documents/" + StringUtil.makeNameFromDate();
		String dir = getServletContext().getRealPath(reldir);
		File dirFile = new File(dir);
		if (!dirFile.mkdirs()) {
			throw new IOException("Unable to create a directory to receive the submitted document.");
		}
		return dirFile.getAbsolutePath() + File.separator;
	}

	//Check that a document parses and save it if it doesn't
	private Document checkDocument(File docFile, String docString)  throws Exception {
		try { return XmlUtil.getDocumentFromString(docString); }
		catch (Exception ex) {
			saveNonParsingDocument(docFile,docString);
			throw ex;
		}
	}

	//Save a copy of a non-parsing document in the document's directory
	//to allow author service bugs to be more easily identified
	private void saveNonParsingDocument(File docFile, String docString) {
		try {
			File saveFile = new File(docFile.getParentFile(),"NonParsingDocument.NPxml");
			FileUtil.setFileText(saveFile,docString);
		}
		catch (Exception ignore) { }
	}

	/**
	 * Update and index a new document according to MIRC rules. The rules are:
	 * <ol>
	 * <li> If autoindexing is enabled, the document is accepted.</li>
	 * <li> If the user has the publisher role as defined in web.xml, it is accepted.</li>
	 * <li> If the document is private or restricted, it is accepted.</li>
	 * <li> Otherwise, the document is rendered non-public and placed in the input queue.</li>
	 * </ol>
	 * @param req the servlet request identifying the authenticated user
	 * @param docFile the File pointing to the MIRCdocument XML file
	 * @param docName the relative path from the root of the servlet to the
	 * MIRCdocument XML file (typically documents/???/???.xml).
	 * @param docString the XML text of the MIRCdocument
	 * @throws Exception if it cannot update the publication queue or the storage service index.
	 */
	public static void installDocument(HttpServletRequest req,
								 File docFile,
								 String docName,
								 String docString) throws Exception {
		String publisher = StorageConfig.getPublisherRoleName();
		if (StorageConfig.authorAutoindex() || req.isUserInRole(publisher) || !isPublic(docString)) {
			FileUtil.setFileText(docFile,docString);
			InputQueue.deleteQueueEntry(docName);
		}
		else {
			docString = makeNonPublic(docString);
			FileUtil.setFileText(docFile,docString);
			if (!InputQueue.addQueueEntry(docName,true)) {
				throw new Exception("Unable to update the publication queue.");
			}
		}
		//Index the document
		if (!MircIndex.getInstance().insertDocument(docName)) {
			throw new Exception("Unable to update the storage service index.");
		}
	}

	/**
	 * Determine whether a MIRCdocument is public.
	 * @param docString the XML text of the MIRCdocument.
	 * @return true if the MIRCdocument is public, false otherwise.
	 */
	public static boolean isPublic(String docString) {
		String authorization = getElement(docString,"authorization");
		if (authorization == null) return true;
		String read = getElement(authorization,"read");
		if (read == null) return true;
		if (read.indexOf("*") != -1) return true;
		return false;
	}

	/**
	 * Make a MIRCdocument non-public.
	 * If the authorization/owner element is missing, do not modify
	 * the document, since the document would become invisible to everyone.
	 * @param docString the XML text of the MIRCdocument.
	 * @return the modified MIRCdocument text.
	 */
	public static String makeNonPublic(String docString) {
		String authorization = getElement(docString,"authorization");
		if (authorization == null) return docString;
		if (getElement(authorization,"owner") == null) return docString;
		String read = getElement(authorization,"read");
		if (read == null) {
			authorization += "<read></read>";
		}
		else {
			read = read.replaceAll("\\s+","").replace("*",",").replaceAll("[,]+",",");
			if (read.startsWith(",")) read = read.substring(1);
			if (read.endsWith(",")) read = read.substring(0,read.length()-1);
			authorization = replaceElement(authorization,"read",read);
		}
		docString = replaceElement(docString,"authorization",authorization);
		return docString;
	}

	/**
	 * Make a MIRCdocument public.
	 * If the authorization element or read element
	 * is missing, do nothing because the document
	 * is already public.
	 * @param docString the XML text of the MIRCdocument.
	 * @return the modified MIRCdocument text.
	 */
	public static String makePublic(String docString) {
		String authorization = getElement(docString,"authorization");
		if (authorization == null) return docString;
		String read = getElement(authorization,"read");
		if (read == null) return docString;
		if (read.indexOf("*") != -1) return docString;
		read = read.trim();
		if (read.equals("")) read = "*";
		else read = "*," + read;
		authorization = replaceElement(authorization,"read",read);
		docString = replaceElement(docString,"authorization",authorization);
		return docString;
	}

	//Return the string contents of the first instance of the requested element.
	private static String getElement(String docString, String elementName) {
		int a = docString.indexOf("<" + elementName);
		if (a == -1) return null;
		a = docString.indexOf(">",a) + 1;
		if (a == 0) return null;
		if ((a > 1)  && (docString.charAt(a-2) == '/')) return ""; //handle <name attr="x"/>
		int b = docString.indexOf("</" + elementName + ">",a);
		if (b == -1) return null;
		return docString.substring(a,b);
	}

	//Replace the contents of an element.
	private static String replaceElement(String docString, String elementName, String contents) {
		int a = docString.indexOf("<" + elementName);
		if (a == -1) return docString;
		a = docString.indexOf(">",a) + 1;
		if (a == 0) return docString;
		if ((a > 1)  && (docString.charAt(a-2) == '/')) {
			return docString.substring(0,a-2) + ">" + contents + "</" + elementName + ">";
		}
		int b = docString.indexOf("</" + elementName + ">",a);
		if (b == -1) return docString;
		return docString.substring(0,a) + contents + docString.substring(b);
	}

}
