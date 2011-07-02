/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.storageservice;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.zip.ZipEntry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.rsna.mircsite.util.FileObject;
import org.rsna.mircsite.util.FileUtil;
import org.rsna.mircsite.util.HtmlUtil;
import org.rsna.mircsite.util.MircImage;
import org.rsna.mircsite.util.ServletUtil;
import org.rsna.mircsite.util.StringUtil;
import org.rsna.mircsite.util.TomcatUser;
import org.rsna.mircsite.util.TomcatUsers;
import org.rsna.mircsite.util.TypeChecker;
import org.rsna.mircsite.util.XmlUtil;
import org.rsna.mircsite.util.ZipObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.oreilly.servlet.MultipartRequest;

/**
 * The NewDoc Service of the MIRC Storage Service.
 * <p>
 * The NewDoc Service accepts multipart/form-data submissions of
 * forms containing document elements and files for inclusion in
 * new MIRCdocuments.
 * <p>
 * The servlet responds to both HTTP GET and POST.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class NewDocService extends HttpServlet {

	private static final long serialVersionUID = 123123l;
	static final Logger logger = Logger.getLogger(NewDocService.class);
	TypeChecker checker = new TypeChecker();
	static String textext = ".txt";
	static String[] textExtensions = textext.split(",");

	/**
	 * The servlet method that responds to an HTTP GET.
	 * <p>
	 * It returns a web page containing a submission form
	 * to the user in the response text.
	 * <p>
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 * @throws ServletException if the servlet cannot handle the request.
	 * @throws IOException if any IO error occurs.
	 */
	public void doGet(
		HttpServletRequest req,
		HttpServletResponse res
		) throws IOException, ServletException {

		res.setContentType("text/html; charset=\"UTF-8\"");
		PrintWriter out = res.getWriter();

		//Get the sitename for the submission page
		String sitename = StorageConfig.getSitename();

		//Make sure authoring is enabled
		if (!StorageConfig.authorEnabled()) {
			out.print(
				responseMessage(
					sitename,
					"<p>New document creation is not enabled on this site.</p>") );
			return;
		}

		//Find the directories and files. The strategy is to preload
		//the author's information from the author object and to use
		//the template to obtain the list of section titles.
		File root = new File(getServletContext().getRealPath("/"));
		File storage = new File(root,"storage.xml");
		File newdoc = new File(root,"newdoc");
		File xsl = new File(newdoc,"newdoc-service.xsl");
		File template = new File(newdoc,"template.xml");

		//Get the user's name and information
		String username = req.getRemoteUser();
		Author author = new Author(username,StorageConfig.basepath + "/authors.xml");

		String page;
		try {
			//Get an XML document containing the list of groups for this user.
			TomcatUsers tcUsers = TomcatUsers.getInstance(root);
			TomcatUser tcUser = tcUsers.getTomcatUser(username);
			String[] groups = tcUser.getMircGroupsForUser(tcUsers);
			Document gpsXML = XmlUtil.getDocument();
			Element gpsElement = gpsXML.createElement("groups");
			gpsXML.appendChild(gpsElement);
			for (int i=0; i<groups.length; i++) {
				Element gpElement = gpsXML.createElement("group");
				gpsElement.appendChild(gpElement);
				gpElement.appendChild(gpsXML.createTextNode(groups[i]));
			}

			//Get the template
			Document templateXML = XmlUtil.getDocument(template);

			//Generate the submission page.
			Object[] params = {
				"name",			author.name,
				"affiliation",	author.affiliation,
				"contact",		author.contact,
				"username",		author.username,
				"groups",		gpsXML,
				"textext",		".txt",
				"template",		templateXML
			};
			page = XmlUtil.getTransformedText(storage,xsl,params);
		}
		catch (Exception ex) {
			page = responseMessage(
						sitename,
						"<p>Error creating the submission page.</p>" +
						"<p>"+ex.getMessage()+"</p>");
		}
		out.print(page);
		out.flush();
		out.close();
	}

	/**
	 * The servlet method that responds to an HTTP POST.
	 * <p>
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 * @throws ServletException if the servlet cannot handle the request.
	 * @throws IOException if any IO error occurs.
	 */
	public void doPost(
		HttpServletRequest req,
		HttpServletResponse res
		) throws IOException, ServletException {

		try { req.setCharacterEncoding("UTF-8"); }
		catch (Exception ignore) { }
		res.setContentType("text/html; charset=\"UTF-8\"");
		PrintWriter out = res.getWriter();

		//Get the values needed from the storage service configuration
		String sitename = StorageConfig.getSitename();
		int maxsize = StorageConfig.getMaxSubmitSize();
		maxsize = Math.max(maxsize,5)*1024*1024;

		//Make sure authoring is enabled
		if (!StorageConfig.authorEnabled()) {
			out.print(
				responseMessage(
					sitename,
					"<p>New document creation is not enabled on this site.</p>") );
			return;
		}

		//Check the Content-Type
		String contentType = req.getContentType().toLowerCase();
		if (contentType.indexOf("multipart/form-data") < 0 ) {
			out.print(
				responseError(
					sitename,
					"<p>Unsupported Content-Type: " + req.getContentType() + "</p>") );
			return;
		}

		//Make a working directory to receive the submission.
		//This directory is in the newdoc subfolder of the storage service's root.
		String baseDirName = StringUtil.makeNameFromDate();
		String relDirName = "newdoc/" + baseDirName;
		String mdDirName = getServletContext().getRealPath(relDirName);
		File mdDirFile = new File(mdDirName);
		if (!mdDirFile.mkdirs()) {
			out.print(
				responseError(
					sitename,
					"<p>Unable to create a directory to process the submission:</p>"
					+ "<p>" + mdDirName + "</p>") );
			return;
		}

		//Now get the posted files.
		MultipartRequest mpReq = new MultipartRequest(req, mdDirName, maxsize, "UTF-8");

		//Update the Author's information
		//Get the user's name and information
		String userName = req.getRemoteUser();
		//Get the author from the authors.xml file.
		//Send an error message if the user was not authenticated.
		if (userName == null) {
			ServletUtil.sendError(res,res.SC_FORBIDDEN);
			return;
		}
		//Get the author from the authors.xml file
		Author author = new Author(userName, StorageConfig.basepath + "/authors.xml");
		author.setAuthorInfo(
			mpReq.getParameter("name"),
			mpReq.getParameter("affiliation"),
			mpReq.getParameter("contact"));

		//Okay, we have the files, if any, loaded into dir. Get the list of files
		//before we copy in the template. These are the files to be added to the MIRCdocument.
		File[] files = mdDirFile.listFiles();

		//Make sure that there is not an illegal file type in the submission.
		if( !filesAreLegal(files) ) {
			out.print(
				responseError(
					sitename,
					"<p>An unsafe file was detected in the submission.</p>"));
			return;
		}

		//Make a File that points to the MIRCdocument.xml file to be created,
		//and copy the template into that file.
		File mdFile = new File(mdDirFile,"MIRCdocument.xml");
		File template = new File(getServletContext().getRealPath("newdoc/template.xml"));
		FileUtil.copyFile(template, mdFile);

		//Create the index entry.
		String indexEntry = "documents/" + baseDirName + "/MIRCdocument.xml";

		try {
			//Parse the document and insert the form parameters.
			Document mdXML = XmlUtil.getDocument(mdFile);
			Element root = mdXML.getDocumentElement();

			//Get the section parameters
			Hashtable<String,String> params = getParams(mpReq);

			//Insert the form parameters.
			setElement(root,"title",mpReq.getParameter("title"));
			setElement(root,"author/name",mpReq.getParameter("name"));
			setElement(root,"author/affiliation",mpReq.getParameter("affiliation"));
			setElement(root,"author/contact",mpReq.getParameter("contact"));
			setElement(root,"abstract",mpReq.getParameter("abstext"));
			setElement(root,"authorization/owner",mpReq.getParameter("username"));

			//Set the read and update privileges.
			//Note that in this service, when autoindexing is not
			//enabled, if a non-publisher submits a public document,
			//read and update privileges are granted to the publisher role.
			//Note also that the asterisk, if present, is left in place
			//here so that it will trigger entry of the document into
			//the input queue during the call to AuthorService.installDocument
			//later.
			String read = mpReq.getParameter("read");
			String publisher = StorageConfig.getPublisherRoleName();
			if (StorageConfig.authorAutoindex()
					|| req.isUserInRole(publisher)
							|| (read.indexOf("*")==-1)) {
				setElement(root,"authorization/read",read);
			}
			else {
				setElement(root,"authorization/read",read+","+publisher);
				setElement(root,"authorization/update",publisher);
			}

			//Insert the section elements
			int n = 1;
			String secname;
			while ((secname = params.get("secname"+n)) != null) {
				Element section = getSectionElement(root,secname);
				if (section != null) setElement(section,params.get("sectext"+n));
				n++;
			}

			//Force a publication-date element containing today's date.
			insertPublicationDate(root);

			//Save the file and then insert all the files.
			FileUtil.setFileText(mdFile,XmlUtil.toString(mdXML));
			MircDocument md = insertFiles(mdFile,indexEntry,files);

			//Make sure the document parses.
			mdXML = XmlUtil.getDocument(md.docFile);

			//Now move the directory into the documents tree.
			File docsFile = new File(getServletContext().getRealPath("documents"));
			docsFile.mkdirs();
			File dirFile = new File(docsFile,baseDirName);
			if (!mdDirFile.renameTo(dirFile))
				throw new Exception(
					"Unable to rename the document directory.<br/>"
					+"source: "+mdDirFile+"<br/>"
					+"destination: "+dirFile
				);

			//Now set the permissions for the document, index it,
			//and add it to the input queue if necessary.
			AuthorService.installDocument(
								req,
								new File(dirFile,"MIRCdocument.xml"),
								indexEntry,
								md.docText);

			//It looks like we made it; send a redirector to the document.
			out.print(HtmlUtil.getRedirector(req.getContextPath() + "/" + indexEntry));
		}
		catch (Exception ex) {
			logger.warn("Exception while processing the document.",ex);
			String response =
				responseMessage(
						sitename,
						"<p>Message: " + ex.getMessage() + "</p>"
						+ deleteResponse(FileUtil.deleteAll(mdDirFile)));
			out.print(response);
			MircIndex.getInstance().removeDocument(indexEntry);
		}
		out.close();
	}

	//Remove all publication-date elements and insert one with today's date.
	private void insertPublicationDate(Element root) {
		NodeList nl = root.getElementsByTagName("publication-date");
		for (int i=0; i<nl.getLength(); i++) {
			Node pd = nl.item(i);
			pd.getParentNode().removeChild(pd);
		}
		Document doc = root.getOwnerDocument();
		Element pd = doc.createElement("publication-date");
		pd.appendChild(doc.createTextNode(StringUtil.getDate()));
		root.appendChild(pd);
	}

	//Find a section element with a given heading.
	private Element getSectionElement(Element root, String secname) {
		Node child = root.getFirstChild();
		while (child != null) {
			if ((child.getNodeType() == Node.ELEMENT_NODE) &&
				child.getNodeName().equals("section") &&
				((Element)child).getAttribute("heading").equals(secname)) {
					return (Element)child;
			}
			child = child.getNextSibling();
		}
		return null;
	}

	//Replace the contents of an element with XML text.
	private void setElement(Element root, String path, String text) {
		Element target = XmlUtil.getElementViaPath(root,"MIRCdocument/"+path);
		if (target != null) setElement(target,text);
	}

	private void setElement(Element target, String text) {
		try {
			//First remove all the target's children.
			while (target.hasChildNodes()) {
				target.removeChild(target.getFirstChild());
			}
			//Next create a temporary document for parsing the text.
			Document temp =
				XmlUtil.getDocumentFromString(
					"<root>"+text+"</root>");
			//Finally, import all the child nodes of the root
			//of the temporary document into the target.
			Element root = temp.getDocumentElement();
			while (root.hasChildNodes()) {
				Node child = root.getFirstChild();
				target.appendChild(target.getOwnerDocument().importNode(child,true));
				root.removeChild(child);
			}
		}
		catch (Exception ignore) { }
	}

	private Hashtable<String,String> getParams(MultipartRequest mpReq) {
		Hashtable<String,String> ht = new Hashtable<String,String>();
		Enumeration en = mpReq.getParameterNames();
		while (en.hasMoreElements()) {
			String name = (String)en.nextElement();
			if (name.startsWith("sectext") || name.startsWith("secname")) {
				ht.put(name,mpReq.getParameter(name));
			}
		}
		return ht;
	}

	private MircDocument insertFiles(
				File mdFile,
				String indexEntry,
				File[] files) throws Exception {
		//Instantiate the MircDocument so we can add the files to it.
		MircDocument md = new MircDocument(mdFile, indexEntry);

		//Now add in all the files.
		for (int i=0; i<files.length; i++) {
			insertFile(md,files[i],true);
		}
		//Save the document and return it.
		md.save();
		return md;
	}

	//Note: this method assumes that all the files are already in the
	//directory with the MIRCdocument. It only deletes a file if it is
	//a ZipObject that is unpacked.
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
				zObject.getFile().delete();
			}
			else {
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

	//Produce a string for the submission response page indicating whether the
	//submission could be deleted after processing.
	private String deleteResponse(boolean b) {
		if (b) return "<p>The submission was deleted.</p>";
		return "<p>There was a problem deleting the submission.</p>";
	}

	//Miscellaneous functions used in producing HTML reponse pages.

	private String responseMessage(String name, String text) {
		return responseHead(name) + "<h2>" + name + "</h2>" + text + responseEnd();
	}

	private String responseError(String name, String text) {
		return responseMessage(
					name,
					"<p>Unable to process the submitted document.</p>"
					+ text);
	}

	private String responseHead(String name) {
		return "<html>\n<head>\n<title>New Document Service: " + name + "</title>\n</head>\n<body>\n";
	}

	private String responseEnd() {
		return "</body>\n</html>";
	}
	private boolean filesAreLegal(File[] files) {
		FileUtil fileUtil = new FileUtil();
		for (int i = 0; i < files.length; i++) {
			if (!checker.isFileAllowed(files[i]) ) {
				return false;
			}
		}
		return true;
	}

}