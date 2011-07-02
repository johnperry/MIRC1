/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.fileservice;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import org.rsna.mircsite.anonymizer.DicomAnonymizer;
import org.rsna.mircsite.anonymizer.LocalRemapper;
import org.rsna.mircsite.util.AgendaItem;
import org.rsna.mircsite.util.Conference;
import org.rsna.mircsite.util.Conferences;
import org.rsna.mircsite.util.ContentType;
import org.rsna.mircsite.util.DicomObject;
import org.rsna.mircsite.util.FileUtil;
import org.rsna.mircsite.util.GeneralFileFilter;
import org.rsna.mircsite.util.HtmlUtil;
import org.rsna.mircsite.util.MircImage;
import org.rsna.mircsite.util.MyRsnaSession;
import org.rsna.mircsite.util.MyRsnaSessions;
import org.rsna.mircsite.util.ServletUtil;
import org.rsna.mircsite.util.StringUtil;
import org.rsna.mircsite.util.TypeChecker;
import org.rsna.mircsite.util.XmlStringUtil;
import org.rsna.mircsite.util.XmlUtil;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.oreilly.servlet.MultipartRequest;

/**
 * The top-level class of the MIRC File Cabinet servlet.
 * <p>
 * The File Cabinet stores and manages files for users.
 * <p>
 * This servlet responds to both HTTP GET and POST.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class FileService extends HttpServlet {

	private static final long serialVersionUID = 123123213l;

	static final Logger logger = Logger.getLogger(FileService.class);

	String requestContentType;
	String pathInfo;
	String pathInfoLC;
	String deptpath;
	String username;
	String usernameLC;
	String userpath;
	File root;
	File dept;
	File user;
	File commonDir;

	FileFilter dirsOnly = new GeneralFileFilter();

	/**
	 * The servlet method that responds to an HTTP GET.
	 * It returns a page displaying token images for all the files in
	 * the user's file cabinet, along with controls to add or delete files.
	 * @param req the HttpServletRequest provided by the servlet container.
	 * @param res the HttpServletResponse provided by the servlet container.
	 * @throws ServletException if the servlet cannot handle the request.
	 */
	public void doGet(
			HttpServletRequest req,
			HttpServletResponse res)
				throws ServletException {

		//Check the user and set up all the fields.
		//On a GET, first redirect the user to the Login
		//Servlet if the user is not authenticated.
		if (req.getRemoteUser() == null) {
			ServletUtil.authenticate(req,res,req.getRequestURL().toString());
			return;
		}
		//Okay, the user was authenticated, now set up all the variables.
		if (!setup(req,res)) return;

		//See what kind of GET this is by checking the path info.
		//(The path info is the text after the servlet path and before the query string.)
		if (pathInfoLC.equals("") || pathInfoLC.equals("/")) {
			try {
				//This is a GET for the main file cabinet page.
				String page = FileUtil.getFileText(new File(root, "file-service.html"));
				Properties props = new Properties();
				props.setProperty("username", username);
				String openpath = req.getParameter("openpath");
				if (openpath == null) openpath = "";
				props.setProperty("openpath", openpath);
				page = StringUtil.replace(page, props);
				ServletUtil.sendPageNoCache(res, page);
			}
			catch (Exception e) { sendErrorMessage(res,e.getMessage()); }
			return;
		}

		else if (pathInfoLC.equals("/tree")) {
			try {
				//This is a GET for the tree structure for the left pane.
				String filesParam = req.getParameter("files");
				boolean includeFiles = (filesParam == null) || !filesParam.equals("no");
				String conferencesParam = req.getParameter("conferences");
				boolean includeConferences = (conferencesParam == null) || !conferencesParam.equals("no");
				String myrsnaParam = req.getParameter("myrsna");
				boolean includeMyRSNA = (myrsnaParam == null) || !myrsnaParam.equals("no");

				Conferences confs = Conferences.getInstance();
				Document doc = XmlUtil.getDocument();
				Element root = doc.createElement("tree");
				doc.appendChild(root);
				Element shared = appendCategory(root, "Shared");
				if (includeFiles) appendDir(shared, new File(dept, "Files"), "Files");
				if (includeConferences) appendConferences(shared, confs.getRootConference(null));
				Element personal = appendCategory(root, "Personal");
				if (includeFiles) appendDir(personal, new File(user, "Files"), "Files");
				if (includeConferences) appendConferences(personal, confs.getRootConference(username));
				if (includeMyRSNA) appendMyRsnaFiles(root, "MyRSNA");
				ServletUtil.sendText(res, "text/xml", XmlUtil.toString(root), false);
			}
			catch (Exception ex) { ServletUtil.sendError(res, 404); }
			return;
		}

		else if (pathInfoLC.startsWith("/mirc/")) {
			//This is a GET for the contents of a single MIRC file cabinet directory.
			String path = pathInfo.substring("/mirc/".length());
			Path p = new Path(path);
			try  {
				Document doc = getDirContentsDoc(p);
				ServletUtil.sendText(res, "text/xml", XmlUtil.toString(doc.getDocumentElement()), false);
			}
			catch (Exception ex) { ServletUtil.sendError(res, 404); }
			return;
		}

		else if (pathInfoLC.startsWith("/myrsna/folder/")) {
			//This is a GET for the contents of a single myRSNA folder
			String nodeID = pathInfo.substring("/myrsna/folder/".length());
			try  {
				Document doc = getMyRsnaDirContentsDoc(nodeID);
				ServletUtil.sendText(res, "text/xml", XmlUtil.toString(doc.getDocumentElement()), false);
			}
			catch (Exception ex) { ServletUtil.sendError(res, 404); }
			return;
		}

		else if (pathInfoLC.startsWith("/createfolder/")) {
			//This a request to create a directory and return the tree of its parent
			try {
				String path = pathInfo.substring("/createFolder/".length());
				path = path.trim();
				if ((path.length() == 0) || path.equals("/")) throw new Exception("");
				Path p = new Path(path);
				p.dir.mkdirs();
				p.iconDir.mkdirs();
				Document doc = XmlUtil.getDocument();
				Element root = doc.createElement("tree");
				doc.appendChild(root);
				File parentDir = p.dir.getParentFile();
				appendDir(root, parentDir, parentDir.getName());
				ServletUtil.sendText(res, "text/xml", XmlUtil.toString(root), false);
			}
			catch (Exception ex) {
				ServletUtil.sendError(res, 404);
			}
			return;
		}

		else if (pathInfoLC.startsWith("/deletefolder/")) {
			//This a request to delete a directory and return the tree of its parent
			String path = pathInfo.substring("/deleteFolder/".length());
			Path p = new Path(path);
			try {
				File parentDir = p.dir.getParentFile();
				//if (p.dir.listFiles().length ==0) {
					FileUtil.deleteAll(p.dir);
					FileUtil.deleteAll(p.iconDir);
				//}
				Document doc = XmlUtil.getDocument();
				Element root = doc.createElement("tree");
				doc.appendChild(root);
				appendDir(root, parentDir, parentDir.getName());
				ServletUtil.sendText(res, "text/xml", XmlUtil.toString(root), false);
			}
			catch (Exception ex) {
				ServletUtil.sendError(res, 404);
			}
			return;
		}

		else if (pathInfoLC.startsWith("/renamefolder/")) {
			try {
				//This a request to rename a directory
				String path = pathInfo.substring("/renameFolder/".length());
				Path p = new Path(path);
				String newname = req.getParameter("newname");
				File newDir = new File(p.dir.getParentFile(), newname);
				File newIconDir = new File(p.iconDir.getParentFile(), newname);
				if (newDir.exists() || newIconDir.exists()) {
					ServletUtil.sendText(res, "text/xml", "<notok/>", false);
				}
				else {
					boolean dirResult = p.dir.renameTo(newDir);
					boolean iconResult = p.iconDir.renameTo(newIconDir);
					ServletUtil.sendText(res,
						"text/xml",
						"<ok>\n"
						+"  <dirResult dir=\""+p.dir+"\">"+dirResult+"</dirResult>\n"
						+"  <iconResult dir=\""+p.iconDir+"\">"+iconResult+"</iconResult>\n</ok>",
						false);
				}
			}
			catch (Exception ex) {
				ServletUtil.sendError(res, 404);
			}
			return;
		}

		else if (pathInfoLC.startsWith("/deletefiles/")) {
			//This a request to delete a list of files
			//from a directory. Note: only an error code
			//is returned; the client will make another
			//call to get the contents.
			String path = pathInfo.substring("/deleteFiles/".length());
			Path p = new Path(path);
			try {
				deleteFiles(req, p);
				ServletUtil.sendError(res, 200);
			}
			catch (Exception ex) {
				ServletUtil.sendError(res, 404);
			}
			return;
		}

		else if (pathInfoLC.startsWith("/renamefile/")) {
			//This a request to remame a single file.
			String path = pathInfo.substring("/renameFile/".length());
			Path p = new Path(path);
			try {
				renameFile(req, p);
				ServletUtil.sendError(res, 200);
			}
			catch (Exception ex) {
				ServletUtil.sendError(res, 404);
			}
			return;
		}

		else if (pathInfoLC.startsWith("/copyfiles")) {
			//This a request to copy a list of files
			//from one directory to another. As in the
			//case of deleteFiles, the client will make
			//another call to display whatever directory
			//it wants. Unlike deleteFiles, however, in
			//this case the source and destination path
			//information is passed in query parameters,
			//along with the list of files.
			try {
				copyFiles(req);
				ServletUtil.sendError(res, 200);
			}
			catch (Exception ex) {
				ServletUtil.sendError(res, 404);
			}
			return;
		}

		else if (pathInfoLC.startsWith("/exportfiles/")) {
			//This is a request to export a list of files
			//from a directory as a zip file.
			String path = pathInfo.substring("/exportFiles/".length());
			Path p = new Path(path);
			exportFiles(req, res, p);
			return;
		}

		else if (pathInfoLC.equals("/save")) {
			//This is a request from a MIRCdocument to save its images.
			//Put them in a subdirectory of the user's Personal/Files
			//directory.
			String path = req.getParameter("path");
			File dir = new File(path).getParentFile();
			//Okay, here is a kludge to try to avoid saving MIRCdocuments
			//on top of one another. If a MIRCdocument was created by the
			//ZipService, it will be in a directory called documents/{datetime}/n.
			//If we just get the parent of the file in the path, then we just get n.
			//Let's try to do a little better. If the parent has a short name and
			//the grandparent has a long name (for example, "200901011234132/1"),
			//then get the grandparent and parent together.
			String parentName = dir.getName();
			String grandparentName = dir.getParentFile().getName();
			if ((parentName.length() < 17) && (grandparentName.length() == 17)) {
				//Note: datetime names are ALWAYS 17 chars.
				parentName = grandparentName + "/" + parentName;
			}
			String targetDirPath = "Personal/Files/"+parentName;
			Path p = new Path(targetDirPath);
			saveImages(path, p);
			ServletUtil.sendRedirector(res, "/file/service?openpath="+targetDirPath);
		}

		else if (pathInfoLC.startsWith("/createconference")) {
			//This a request to create a conference and return the tree of its parent
			String id = req.getParameter("id"); //the id of the parent of the created conference
			String name = req.getParameter("name");
			try {
				Conferences confs = Conferences.getInstance();
				Conference conf = confs.createConference(username, name, id);
				if (conf != null) {
					Document doc = XmlUtil.getDocument();
					Element root = doc.createElement("tree");
					doc.appendChild(root);
					appendConferences(root, confs.getConference(id));
					ServletUtil.sendText(res, "text/xml", XmlUtil.toString(root), false);
					return;
				}
			}
			catch (Exception ex) { }
			ServletUtil.sendError(res, 404);
			return;
		}

		else if (pathInfoLC.startsWith("/deleteconference")) {
			//This a request to delete a conference and return the tree of its parent.
			String id = req.getParameter("id");
			try {
				Conferences confs = Conferences.getInstance();
				Conference conf = confs.getConference(id);
				String parentID = conf.pid;
				confs.deleteConference(id);
				Document doc = XmlUtil.getDocument();
				Element root = doc.createElement("tree");
				doc.appendChild(root);
				appendConferences(root, confs.getConference(parentID));
				ServletUtil.sendText(res, "text/xml", XmlUtil.toString(root), false);
				return;
			}
			catch (Exception ex) { logger.warn("unable to delete conference; id="+id, ex); }
			ServletUtil.sendError(res, 404);
			return;
		}

		else if (pathInfoLC.startsWith("/renameconference")) {
			String id = req.getParameter("id");
			String name = req.getParameter("name");
			try {
				//This a request to rename a conference
				Conferences confs = Conferences.getInstance();
				Conference conf = confs.getConference(id);
				conf.title = name;
				String result = confs.setConference(conf) ? "<ok/>" : "<notok/>";
				ServletUtil.sendText(res, "text/xml", result, false);
			}
			catch (Exception ex) { ServletUtil.sendError(res, 404); }
			return;
		}

		else if (pathInfoLC.startsWith("/appendagendaitem")) {
			String nodeID = req.getParameter("nodeID");
			String url = req.getParameter("url");
			String title = req.getParameter("title");
			String alturl = req.getParameter("alturl");
			String alttitle = req.getParameter("alttitle");
			String subtitle = req.getParameter("subtitle");
			try {
				//This a request to append a new agenda item to a conference
				Conferences confs = Conferences.getInstance();
				Conference conf = confs.getConference(nodeID);
				AgendaItem item = new AgendaItem(url, title, alturl, alttitle, subtitle);
				conf.appendAgendaItem(item);
				confs.setConference(conf);
				String result = confs.setConference(conf) ? "<ok/>" : "<notok/>";
				ServletUtil.sendText(res, "text/xml", result, false);
			}
			catch (Exception ex) { ServletUtil.sendError(res, 404); }
			return;
		}

		else if (pathInfoLC.startsWith("/moveagendaitem")) {
			String nodeID = req.getParameter("nodeID");
			String sourceURL = req.getParameter("sourceURL");
			String targetURL = req.getParameter("targetURL");
			try {
				//This a request to move an agenda item within a conference
				Conferences confs = Conferences.getInstance();
				Conference conf = confs.getConference(nodeID);
				conf.moveAgendaItem(sourceURL, targetURL);
				confs.setConference(conf);
				String result = confs.setConference(conf) ? "<ok/>" : "<notok/>";
				ServletUtil.sendText(res, "text/xml", result, false);
			}
			catch (Exception ex) { ServletUtil.sendError(res, 404); }
			return;
		}

		else if (pathInfoLC.startsWith("/transferagendaitem")) {
			String sourceID = req.getParameter("sourceID");
			String targetID = req.getParameter("targetID");
			String url = req.getParameter("url");
			try {
				//This a request to move an agenda item from one conference to another
				Conferences confs = Conferences.getInstance();
				Conference conf = confs.getConference(sourceID);
				AgendaItem aItem = conf.removeAgendaItem(url);
				confs.setConference(conf);
				String result = "<notok/>";
				if (aItem != null) {
					conf = confs.getConference(targetID);
					conf.appendAgendaItem(aItem);
					result = confs.setConference(conf) ? "<ok/>" : "<notok/>";
				}
				ServletUtil.sendText(res, "text/xml", result, false);
			}
			catch (Exception ex) { ServletUtil.sendError(res, 404); }
			return;
		}

		else if (pathInfoLC.startsWith("/deleteagendaitems")) {
			//This a request to delete a list of agenda items
			//from a conference. Note: only an error code is
			//returned; the client will make another call to
			//get the modified contents of the conference.
			try {
				String nodeID = req.getParameter("nodeID");
				Conferences confs = Conferences.getInstance();
				Conference conf = confs.getConference(nodeID);

				//Get the list of agenda items.
				String list = req.getParameter("list");
				String[] urls = list.split("\\|");
				//Do everything we can, and ignore errors.
				for (int i=0; i<urls.length; i++) {
					conf.removeAgendaItem(urls[i].trim());
				}
				confs.setConference(conf);
				String result = confs.setConference(conf) ? "<ok/>" : "<notok/>";
				ServletUtil.sendText(res, "text/xml", result, false);
			}
			catch (Exception ex) { ServletUtil.sendError(res, 404); }
			return;
		}

		else if (pathInfoLC.startsWith("/getagenda")) {
			//This is a request for an XML structure containing
			//the agenda items of the specified conference
			String nodeID = req.getParameter("nodeID");
			try {
				Conferences confs = Conferences.getInstance();
				Conference conf = confs.getConference(nodeID);

				Document doc = XmlUtil.getDocument();
				Element root = doc.createElement("agenda");
				doc.appendChild(root);
				Element shared = appendCategory(root, "Shared");
				Iterator<AgendaItem> it = conf.agenda.iterator();
				while (it.hasNext()) {
					AgendaItem item = it.next();
					Element el = doc.createElement("item");
					el.setAttribute("url", item.url);
					el.setAttribute("title", item.title);
					el.setAttribute("alturl", item.alturl);
					el.setAttribute("alttitle", item.alttitle);
					el.setAttribute("subtitle", item.subtitle);
					root.appendChild(el);
				}
				ServletUtil.sendText(res, "text/xml", XmlUtil.toString(root), false);
			}
			catch (Exception ex) { logger.warn("error in getAgenda",ex); ServletUtil.sendError(res, 404); }
			return;
		}

		else if (pathInfoLC.startsWith("/personal/files")) {
			String path = pathInfo.substring("/Personal/Files".length());
			path = path.replace("../","");
			File file = new File(user, "files");
			file = new File(file, path);
			boolean isDicomFile = file.getName().toLowerCase().endsWith(".dcm");
			if (isDicomFile && (req.getParameter("list") != null)) dicom(res, file);
			else {
				String contentType = ContentType.getContentType(file);
				ServletUtil.sendBinaryFileContents(res,contentType,file,isDicomFile);
			}
			return;
		}

		else if (pathInfoLC.startsWith("/personal/icons")) {
			String path = pathInfo.substring("/Personal/Icons".length());
			path = path.replace("../","");
			File file = new File(user, "icons");
			file = new File(file, path);
			String contentType = ContentType.getContentType(file);
			ServletUtil.sendBinaryFileContents(res,contentType,file,false);
			return;
		}

		else if (pathInfoLC.startsWith("/shared/files")) {
			String path = pathInfo.substring("/Shared/Files".length());
			path = path.replace("../","");
			File file = new File(dept, "files");
			file = new File(file, path);
			boolean isDicomFile = file.getName().toLowerCase().endsWith(".dcm");
			if (isDicomFile && (req.getParameter("list") != null)) dicom(res, file);
			else {
				String contentType = ContentType.getContentType(file);
				ServletUtil.sendBinaryFileContents(res,contentType,file,isDicomFile);
			}
			return;
		}

		else if (pathInfoLC.startsWith("/shared/icons")) {
			String path = pathInfo.substring("/Shared/Icons".length());
			path = path.replace("../","");
			File file = new File(dept, "icons");
			file = new File(file, path);
			String contentType = ContentType.getContentType(file);
			ServletUtil.sendBinaryFileContents(res,contentType,file,false);
			return;
		}

		else {
			//This path is not allowed; just return the standard not_found message.
			ServletUtil.sendError(res,HttpServletResponse.SC_NOT_FOUND);
		}
	}

	/**
	 * The servlet method that responds to an HTTP POST.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 * @throws ServletException if the servlet cannot handle the request.
	 */
	public void doPost(
			HttpServletRequest req,
			HttpServletResponse res)
				throws ServletException {

		//Check the user and set up all the fields.
		if (!setup(req,res)) return;

		//Figure out what kind of POST this is
		if (requestContentType.indexOf("multipart/form-data") >= 0) {
			//This is a post of a multipart form.
			if (pathInfoLC.startsWith("/uploadfile/")) {
				//This a request to upload a file and return a file cabinet page.
				String path = pathInfo.substring("/uploadFile/".length());
				Path p = new Path(path);
				try {
					uploadFile(req, res, p);
					ServletUtil.sendRedirector(res, "/file/service?openpath="+path);
				}
				catch (Exception e) { logger.warn("failed",e); sendErrorMessage(res,e.getMessage()); }
				return;
			}

			else {
				//This path is unknown; return the standard not found message.
				ServletUtil.sendError(res,HttpServletResponse.SC_NOT_FOUND);
				return;
			}
		}

		else sendMessage(res,"Error","Unsupported Content-Type: "+requestContentType);
	}

	//A helper class to find a directory from a path
	class Path {
		public File dir = null;
		public File iconDir = null;
		public String fileURL = null;
		public String iconURL = null;
		public String dirTitle = null;
		public String path = null;

		public Path(String path) {
			path = path.replace("../","");
			this.path = path;
			if (path.startsWith("Shared/Files")) {
				path = path.substring("Shared/Files".length());
				if (path.startsWith("/")) path = path.substring(1);
				if ((path.length()>1) && !path.endsWith("/")) path = path + "/";
				dir = new File(dept, "Files");
				iconDir = new File(dept, "Icons");
				dir = new File(dir, path);
				iconDir = new File(iconDir, path);
				dirTitle = "Shared/Files/" + path;
				fileURL = "Shared/Files/" + path;
				iconURL = "Shared/Icons/" + path;
			}
			else if (path.startsWith("Personal/Files")) {
				path = path.substring("Personal/Files".length());
				if (path.startsWith("/")) path = path.substring(1);
				if ((path.length()>1) && !path.endsWith("/")) path = path + "/";
				dir = new File(user, "Files");
				iconDir = new File(user, "Icons");
				dir = new File(dir, path);
				iconDir = new File(iconDir, path);
				dirTitle = "Personal/Files/" + path;
				fileURL = "Personal/Files/" + path;
				iconURL = "Personal/Icons/" + path;
			}
		}
		public boolean isShared() {
			return path.startsWith("Shared/Files");
		}
		public boolean isUser() {
			return path.startsWith("Personal/Files");
		}
	}

	private Document getDirContentsDoc(Path p) throws Exception {
		File[] files = p.dir.listFiles();
		Document doc = XmlUtil.getDocument();
		Element root = doc.createElement("dir");
		root.setAttribute("title", p.dirTitle);
		doc.appendChild(root);
		for (int i=0; i<files.length; i++) {
			if (files[i].isFile()) {
				Element el = doc.createElement("file");
				String name = files[i].getName();

				//Figure out whether the icon is a gif or a jpg
				String iconName = "";
				File iconFile = new File(p.iconDir, name+".gif");
				if (!iconFile.exists())
					iconFile = new File(p.iconDir, name+".jpg");
				if (iconFile.exists()) iconName = iconFile.getName();

				el.setAttribute("fileURL", p.fileURL+name);
				el.setAttribute("iconURL", p.iconURL+iconName);
				el.setAttribute("title", name);
				root.appendChild(el);
			}
		}
		return doc;
	}

	//Check that the user is authenticated. If not, send back a 403 and return false.
	//Otherwise, make all the files the GET and POST methods need and create the
	//directories if necessary. Finally, get the PathInfo, and return true.
	private boolean setup(HttpServletRequest req, HttpServletResponse res) {

		//Get the user's name
		username = req.getRemoteUser();

		//Bail out if the user was not authenticated.
		//This should never happen, but just to be safe...
		if (username == null) {
			logger.warn("A non-authenticated user accessed the file cabinet.");
			ServletUtil.sendError(res,HttpServletResponse.SC_FORBIDDEN);
			return false;
		}
		usernameLC = username.toLowerCase();

		//Set up the Files
		root = new File(getServletContext().getRealPath("/"));
		dept = new File(root,"dept");
		File deptFilesDir = new File(dept,"Files");
		File deptIconsDir = new File(dept,"Icons");
		File users = new File(root,"users");
		user = new File(users,username);
		File userFilesDir = new File(user,"Files");
		File userIconsDir = new File(user,"Icons");
		commonDir = new File(root,"common");

		//Fix the case of the root folder names, just in case
		fixCase(dept, "files", "Files");
		fixCase(dept, "icons", "Icons");
		fixCase(user, "files", "Files");
		fixCase(user, "icons", "Icons");

		//Make sure everything exists, and if not, create it.
		if (!deptFilesDir.exists()) deptFilesDir.mkdirs();
		if (!deptIconsDir.exists()) deptIconsDir.mkdirs();
		if (!userFilesDir.exists()) userFilesDir.mkdirs();
		if (!userIconsDir.exists()) userIconsDir.mkdirs();

		//Set up the paths to the user and the department
		userpath = "users/"+username+"/";
		deptpath = "dept/";

		//Get the PathInfo and create an all-lowercase version that
		//is guaranteed not to be null.
		pathInfo = req.getPathInfo();
		pathInfo = (pathInfo != null) ? pathInfo.trim() : "";
		pathInfoLC = pathInfo.toLowerCase();

		//Get the content type and make it not null.
		requestContentType = req.getContentType();
		requestContentType = (requestContentType != null) ? requestContentType.toLowerCase() : "";

		return true;
	}

	private void fixCase(File root, String oldName, String newName) {
		File oldFile = new File(root, oldName);
		File newFile = new File(root, newName);
		oldFile.renameTo(newFile);
	}

	//Add a category node to a tree
	private Element appendCategory(Node parent, String title) throws Exception {
		Element el = parent.getOwnerDocument().createElement("node");
		el.setAttribute("name", title);
		parent.appendChild(el);
		return el;
	}

	//Add a directory and its child directories to a tree
	private void appendDir(Node parent, File dir, String title) throws Exception {
		Element el = parent.getOwnerDocument().createElement("node");
		el.setAttribute("name", title);
		el.setAttribute("sclickHandler", "showFileDirContents");
		parent.appendChild(el);
		File[] files = dir.listFiles(dirsOnly);
		for (int i=0; i<files.length; i++) {
			appendDir(el, files[i], files[i].getName());
		}
	}

	//Add a conference and its child conferences to a tree
	private void appendConferences(Node parent, Conference conf) {
		if (conf == null) logger.warn("call to appendConferences(null); ["+((Element)parent).getAttribute("name")+"]");
		if (conf == null) return;
		Element el = parent.getOwnerDocument().createElement("node");
		el.setAttribute("name", conf.title);
		el.setAttribute("nodeID", conf.id);
		el.setAttribute("sclickHandler", "showConferenceContents");
		parent.appendChild(el);
		Iterator<String> it = conf.children.iterator();
		while (it.hasNext()) {
			String id = it.next();
			Conference child = Conferences.getInstance().getConference(id);
			appendConferences(el, child);
		}
	}

	//Add the user's MyRSNA folders to a tree
	private void appendMyRsnaFiles(Element parent, String title) {
		MyRsnaSession mrs = MyRsnaSessions.getInstance().getMyRsnaSession(username);
		if (mrs != null) {
			try {
				Element folders = mrs.getMyRSNAFolders();
				//Got it, first put in the category title
				Element el = parent.getOwnerDocument().createElement("node");
				el.setAttribute("name", title);
				parent.appendChild(el);
				appendMyRsnaFolderDir(el, folders);
			}
			catch (Exception skipMyRsna) { }
		}
	}

	//Walk a tree of MyRsna folders
	private void appendMyRsnaFolderDir(Node parent, Node folders) {
		if (folders == null) return;
		Node child = folders.getFirstChild();
		while (child != null) {
			if ((child.getNodeType() == Node.ELEMENT_NODE) && child.getNodeName().equals("folder")) {
				Element folder = (Element)child;
				Node name = getFirstNamedChild(folder, "name");
				if (name != null) {
					Element el = parent.getOwnerDocument().createElement("node");
					el.setAttribute("name", name.getTextContent().trim());
					el.setAttribute("nodeID", folder.getAttribute("id"));
					el.setAttribute("sclickHandler", "showMyRsnaDirContents");
					parent.appendChild(el);
					Node subfolders = getFirstNamedChild(folder, "folders");
					if (subfolders != null) appendMyRsnaFolderDir(el, subfolders);
				}
			}
			child = child.getNextSibling();
		}
	}

	//Get the contents of a myRSNA folder
	private Document getMyRsnaDirContentsDoc(String nodeID) throws Exception {
		//Make the return document
		Document doc = XmlUtil.getDocument();
		Element root = doc.createElement("dir");
		doc.appendChild(root);

		//Get the session
		MyRsnaSession mrs = MyRsnaSessions.getInstance().getMyRsnaSession(username);
		if (mrs != null) {
			try {
				Element result = mrs.getMyRSNAFiles(null);
				//Get the requested folder
				Node folder = findMyRsnaFolder(result, nodeID);
				if (folder != null) {
					//Got it, get the files child
					Node files = getFirstNamedChild(folder, "files");
					if (files != null) {
						//OK, we're there, now put in all the file children
						Node child = files.getFirstChild();
						while (child != null) {
							if ((child.getNodeType() == Node.ELEMENT_NODE) && child.getNodeName().equals("file")) {
								appendMyRsnaFile(root, child);
							}
							child = child.getNextSibling();
						}
					}
				}
			}
			catch (Exception quit) { }
		}
		return doc;
	}

	private void appendMyRsnaFile(Node parent, Node file) {
		Element el = parent.getOwnerDocument().createElement("file");
		Element eFile = (Element)file;
		el.setAttribute("id",      eFile.getAttribute("id"));
		el.setAttribute("title",   getFirstNamedChild(file, "name")     .getTextContent().trim());
		el.setAttribute("fileURL", getFirstNamedChild(file, "original") .getTextContent().trim());
		el.setAttribute("iconURL", getFirstNamedChild(file, "thumbnail").getTextContent().trim());
		parent.appendChild(el);
	}

	private Node findMyRsnaFolder(Node node, String id) {
		if (node == null) return null;
		if ((node.getNodeType() == Node.ELEMENT_NODE)
				&& node.getNodeName().equals("folder")
					&& ((Element)node).getAttribute("id").equals(id)) return node;
		//If we get here, the current node is not the one we want; look at its children
		Node result;
		Node child = node.getFirstChild();
		while (child != null) {
			if ((result=findMyRsnaFolder(child, id)) != null) return result;
			child = child.getNextSibling();
		}
		return null;
	}

	private Node getFirstNamedChild(Node node, String name) {
		Node child = node.getFirstChild();
		while (child != null) {
			if ((child.getNodeType() == Node.ELEMENT_NODE) && child.getNodeName().equals(name)) return child;
			child = child.getNextSibling();
		}
		return null;
	}

	// Delete files from the specified directory.
	private void deleteFiles(HttpServletRequest req, Path p) throws Exception {
		//If the delete request is for the shared directory,
		//determine whether the user has the file service
		//admin role and abort if he doesn't.
		if (p.isShared() && !userIsFSAdmin(req)) return;

		//Get the list of document names and verify it.
		//Do everything we can, and ignore errors.
		String list = req.getParameter("list");
		if ((list == null) || (list.trim().equals(""))) return;
		String[] docs = list.split("\\|");
		for (int i=0; i<docs.length; i++) {
			docs[i] = docs[i].replace("../","").trim();
			File file = new File(p.dir, docs[i]);
			File jpgIcon = new File(p.iconDir, docs[i]+".jpg");
			File gifIcon = new File(p.iconDir, docs[i]+".gif");
			file.delete();
			jpgIcon.delete();
			gifIcon.delete();
		}
	}

	// Rename a file in the specified directory.
	private void renameFile(HttpServletRequest req, Path p) throws Exception {
		//If the rename request is for the shared directory,
		//determine whether the user has the file service
		//admin role and abort if he doesn't.
		if (p.isShared() && !userIsFSAdmin(req)) return;

		String oldName = req.getParameter("oldname");
		String newName = req.getParameter("newname");
		if ((oldName == null) || (oldName.trim().equals(""))) return;
		if ((newName == null) || (newName.trim().equals(""))) return;
		newName = fixExtension(newName, oldName);
		File oldFile = new File(p.dir, oldName);
		File oldJpgIcon = new File(p.iconDir, oldName+".jpg");
		File oldGifIcon = new File(p.iconDir, oldName+".gif");
		File newFile = new File(p.dir, newName);
		File newJpgIcon = new File(p.iconDir, newName+".jpg");
		File newGifIcon = new File(p.iconDir, newName+".gif");
		oldFile.renameTo(newFile);
		oldJpgIcon.renameTo(newJpgIcon);
		oldGifIcon.renameTo(newGifIcon);
	}

	private String fixExtension(String newName, String oldName) {
		//Get the extension on the old filename
		String ext = "";
		int k = oldName.lastIndexOf(".");
		if (k != -1) ext = oldName.substring(k);

		//See if the new filename is the same
		if (newName.endsWith(ext)) return newName;

		//It doesn't. Append the extension to the new filename.
		if (newName.endsWith(".")) newName = newName.substring(0, newName.length() - 1);
		return newName + ext;
	}

	// Copy files from one directory to another.
	private void copyFiles(HttpServletRequest req) throws Exception {
		//Get the path
		String sourcePath = req.getParameter("sourcePath");
		if (sourcePath == null) return;
		String destPath = req.getParameter("destPath");
		if (destPath == null) return;
		String list = req.getParameter("files");
		Path src = new Path(sourcePath);
		if (!src.isShared() && !src.isUser()) return;
		Path dest = new Path(destPath);
		if (!dest.isShared() && !dest.isUser()) return;

		//Get the list of document names and verify it.
		//Do everything we can, and ignore errors.
		if ((list == null) || (list.trim().equals(""))) return;
		String[] files = list.split("\\|");
		for (int i=0; i<files.length; i++) {
			files[i] = files[i].replace("../","").trim();
			copyFile(src, dest, files[i]);
		}
	}

	//Copy one file
	private void copyFile(Path src, Path dest, String file) {
		File destFile = new File(dest.dir, file);
		File destJpgIcon = new File(dest.iconDir, file+".jpg");
		File destGifIcon = new File(dest.iconDir, file+".gif");
		destFile.delete();
		destJpgIcon.delete();
		destGifIcon.delete();
		File srcFile = new File(src.dir, file);
		File srcJpgIcon = new File(src.iconDir, file+".jpg");
		File srcGifIcon = new File(src.iconDir, file+".gif");
		FileUtil.copyFile(srcFile, destFile);
		if (srcJpgIcon.exists()) FileUtil.copyFile(srcJpgIcon, destJpgIcon);
		if (srcGifIcon.exists()) FileUtil.copyFile(srcGifIcon, destGifIcon);
	}

	// Export the files listed in the request from the specified directory.
	private void exportFiles(HttpServletRequest req, HttpServletResponse res, Path p) {
		//Get the list of document names and verify it. The list is in the
		//"list" parameter, and the files are separated by the "|" character.
		String list = req.getParameter("list");
		if ((list == null) || (list.trim().equals(""))) return;
		String[] docs = list.split("\\|");
		if (docs.length > 0) {
			try {
				File outDir = getTempDir(user);
				File outFile = new File(outDir, "Export.zip");
				if (zipFiles(docs, p.dir, outFile))
					ServletUtil.sendBinaryFileContents(res, "application/zip", outFile, true);
				else sendMessage(res,"Export Failure","The requested file(s) could not be exported.");
				FileUtil.deleteAll(outDir);
			}
			catch (Exception ignore) { }
		}
	}

	// Return a dump of the header of a DICOM file/
	private void dicom(HttpServletResponse res, File file) {
		try {
			if ((file != null) && file.exists()) {
				DicomObject dicomObject = new DicomObject(file);
				ServletUtil.sendPage(
					res,
					HtmlUtil.getPage(
						file.getName(),
						dicomObject.getElementTable()),
					false);
			}
		}
		catch (Exception ex) {
			try { res.sendError(HttpServletResponse.SC_NO_CONTENT); }
			catch (Exception ignore) { }
		}
	}

	// Get a file from a multipart form, store it, and add it to the specified directory
	private void uploadFile(HttpServletRequest req, HttpServletResponse res, Path p) throws Exception {
		//Get the submission
		File dir = getTempDir(user);
		String dirPath = dir.getAbsolutePath();
		int maxsize = AdminService.maxsize * 1024 * 1024;
		MultipartRequest multipartRequest = new MultipartRequest(req, dirPath, maxsize, "UTF-8");
		Enumeration files = multipartRequest.getFileNames();

		boolean anonymize = (multipartRequest.getParameter("anonymize") != null);
		TypeChecker checker = new TypeChecker();

		//Note: we only get the first submitted file (which may be a zip
		//file, so it is possible that the submission will result in multiple
		//files being stored because the zip file will be automatically
		//unpacked by the saveFile method.
		if (files.hasMoreElements()) {
			String name = (String)files.nextElement();
			String filename = multipartRequest.getFilesystemName(name);
			File dataFile = multipartRequest.getFile(name);
			if( !checker.isFileAllowed(dataFile) ) {
				FileUtil.deleteAll(dir);
				throw new Exception("The uploaded file has an illegal extension.");
			}
			//Okay, try to store the file(s)
			if (!saveFile(p, dataFile, filename, anonymize)) {
				FileUtil.deleteAll(dir);
				throw new Exception("The received file could not be stored.");
			}
		}
		FileUtil.deleteAll(dir);
	}

	// Put all the images from a MIRCdocument into the file cabinet.
	private void saveImages(String path, Path p) {
		//Get a File pointing to the MIRCdocument
		if (path.startsWith("/")) path = path.substring(1);
		path = path.replace('/',File.separatorChar);
		File docFile = new File(root.getParentFile(), path);
		File docParent = docFile.getParentFile();
		p.dir.mkdirs();
		p.iconDir.mkdirs();
		try {
			Document doc = XmlUtil.getDocument(docFile);
			Element rootElement = doc.getDocumentElement();
			NodeList nodeList = rootElement.getElementsByTagName("image");
			for (int i=0; i<nodeList.getLength(); i++) {
				File inFile = getBestImage(docParent, (Element)nodeList.item(i));
				if ((inFile != null) && inFile.exists()) {
					String filename = inFile.getName();
					File target = new File(p.dir, filename);
					File targetJPG = new File(p.iconDir, filename+".jpg");
					File targetGIF = new File(p.iconDir, filename+".gif");
					//Delete the target if it exists.
					target.delete();
					targetJPG.delete();
					targetGIF.delete();
					//Now copy the inFile into the target
					if (FileUtil.copyFile(inFile, target)) makeIcon(target, p.iconDir);
				}
			}
		}
		catch (Exception ex) {
			logger.warn("Error while processing saveImages request.",ex);
		}
	}

	// Find the best image identified by an image element.
	private File getBestImage(File docParent, Element image) {
		NodeList nodeList = image.getElementsByTagName("alternative-image");
		Element e = getImageForRole(nodeList,"original-format");
		if (e == null) e = getImageForRole(nodeList,"original-dimensions");
		if (e == null) e = image;
		String src = e.getAttribute("src");
		if ((src == null) || src.trim().equals("") || (src.indexOf("/") != -1))
			return null;
		return new File(docParent,src);
	}

	// Find an element with a specific role attribute.
	private Element getImageForRole(NodeList nodeList, String role) {
		for (int i=0; i<nodeList.getLength(); i++) {
			Element e = (Element)nodeList.item(i);
			if ((e != null) && e.getAttribute("role").equals(role))
				return e;
		}
		return null;
	}

	// Make an output File corresponding to an input File, eliminating the
	// appendages put on such files by MIRCdocument generators.
	private File getTargetFile(File targetDir, File file) {
		String name = file.getName();
		int k = name.lastIndexOf(".");
		if (k == -1) return new File(targetDir, name);
		String nameNoExt = name.substring(0,k);
		if (nameNoExt.endsWith("_base") ||
			nameNoExt.endsWith("_icon") ||
			nameNoExt.endsWith("_full")) {
				name = nameNoExt.substring(0,nameNoExt.length()-5) + name.substring(k);
		}
		return new File(targetDir,name);
	}

	// Determine whether the request comes from a user with the file service admin role.
	private boolean userIsFSAdmin(HttpServletRequest req) {
		File webxmlFile = new File(root,"WEB-INF" + File.separator + "web.xml");
		String webxml = FileUtil.getFileText(webxmlFile);
		String adminRoleName = XmlStringUtil.getEntity(webxml,"admin");
		return req.isUserInRole(adminRoleName);
	}

	// Create a temp directory inside a designated directory.
	// The Java class library doesn't provide a File method to do
	// that, so we will use the createTempFile method to get a File.
	// That method creates the file, however, so after getting it, we
	// have to delete the file and then use the File to create the directory.
	private File getTempDir(File parent) throws Exception {
		File dir = File.createTempFile("temp","",parent);
		dir.delete();
		dir.mkdir();
		return dir;
	}

	/**
	 * Store a file in the file cabinet.
	 * @param p the object identifying the directories to be modified.
	 * @param dataFile the file to store.
	 * @param filename the name under which the file is to be stored.
	 * @param anonymize true if DICOM files are to be anonymized.
	 * @return true if the operation succeeded completely; false otherwise.
	 */
	private boolean saveFile(
			Path p,
			File dataFile,
			String filename,
			boolean anonymize) {

		if (filename.toLowerCase().endsWith(".zip")) {
			boolean ok = unzipFile(p, dataFile, anonymize);
			dataFile.delete();
			return ok;
		}
		else {
			File target = new File(p.dir, filename);
			File targetJPG = new File(p.iconDir, filename+".jpg");
			File targetGIF = new File(p.iconDir, filename+".gif");
			//Delete the target if it exists.
			target.delete();
			targetJPG.delete();
			targetGIF.delete();
			//Now rename the dataFile into the target
			if (dataFile.renameTo(target)) {
				target = handleDicomObject(target, anonymize);
				makeIcon(target, p.iconDir);
				return true;
			}
		}
		return false;
	}

	// Unpack a zip submission. Note: this function ignores
	// any path information and puts all files in the outDir.
	private boolean unzipFile(
			Path p,
			File inZipFile,
			boolean anonymize) {

		if (!inZipFile.exists()) return false;
		try {
			ZipFile zipFile = new ZipFile(inZipFile);
			Enumeration zipEntries = zipFile.entries();
			TypeChecker checker = new TypeChecker();
			while (zipEntries.hasMoreElements()) {
				ZipEntry entry = (ZipEntry)zipEntries.nextElement();
				String name = entry.getName().replace('/',File.separatorChar);
				//name = name.substring(name.lastIndexOf(File.separator)+1); //remove the path information
				if (!entry.isDirectory()) {
					File outFile = new File(p.dir, name);
					File iconDir = (new File(p.iconDir, name)).getParentFile();
					if (!checker.isFileAllowed(outFile) ) {
						zipFile.close();
						return false;
					}
					outFile.getParentFile().mkdirs();
					BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outFile));
					BufferedInputStream in = new BufferedInputStream(zipFile.getInputStream(entry));
					int size = 1024;
					int n;
					byte[] b = new byte[size];
					while ((n = in.read(b,0,size)) != -1) out.write(b,0,n);
					in.close();
					out.close();
					outFile = handleDicomObject(outFile,anonymize);
					makeIcon(outFile, iconDir);
				}
			}
			zipFile.close();
			return true;
		}
		catch (Exception e) { return false; }
	}

	//Check whether a file is a DicomObject, and if so, set its extension
	//and handle anonymization if requested.
	private static File handleDicomObject(File file, boolean anonymize) {
		try {
			DicomObject dob = new DicomObject(file);
			if (anonymize) {
				File quarantineFile = new File(AdminService.root,"quarantine");
				quarantineFile.mkdirs();
				Properties anprops = loadProperties(DicomObjectProcessor.anonymizerFilename);
				String exceptions =
					DicomAnonymizer.anonymize(
						file, file,
						anprops, null,
						new LocalRemapper(), false, false);
			}
			file = dob.setStandardExtension();
		}
		catch (Exception ex) { }
		return file;
	}

	//Load a properties file.
	private static Properties loadProperties(String filename) {
		File propFile = new File(AdminService.root,filename);
		Properties props = new Properties();
		try { props.load(new FileInputStream(propFile)); }
		catch (Exception ignore) { }
		return props;
	}

	// Zip a list of files in a single directory.
	private static boolean zipFiles(String[] list, File inDir, File outFile) {
		try {
			//Get the various streams and buffers
			ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(outFile));
			File in;
			FileInputStream fin;
			ZipEntry ze;
			byte[] buffer = new byte[10000];
			int bytesread;

			//Add all the files to the zip file
			for (int i=0; i<list.length; i++) {
				list[i] = list[i].replace("../","");
				ze = new ZipEntry(list[i]);
				in = new File(inDir, list[i]);
				if ((in != null) && in.exists()) {
					fin = new FileInputStream(in);
					zout.putNextEntry(ze);
					while ((bytesread = fin.read(buffer)) > 0) {
						zout.write(buffer,0,bytesread);
					}
					zout.closeEntry();
					fin.close();
				}
			}
			zout.close();
		}
		catch (Exception ex) { return false; }
		return true;
	}

	// Try to load the file as an image.
	// If it loads, create a 96-pixel-wide jpeg icon.
	// If not, determine whether the file has an icon stored
	// in the common directory and use it.
	// If no special icon exists for the file extension,
	// create one from the default icon.
	// For non-image files, write the name of the target
	// near the bottom of the icon
	public static File makeIcon(File target, File iconDir) {
		iconDir.mkdirs();
		String name = target.getName();
		int k = name.lastIndexOf(".") + 1;
		String ext = (k>0) ? name.substring(k).toLowerCase() : "";
		try {
			MircImage image = new MircImage(target);
			File token = new File(iconDir, name+".jpg");
			image.saveAsJPEG(token,96,0);
			return token;
		}
		catch (Exception e) {
			//It's not an image.
			//See if there is an icon for this file
			File commonDir = new File(AdminService.root, "common");
			String inIconName = ext+".gif";
			File inIcon = new File(commonDir, inIconName);
			if (!inIcon.exists()) {
				//No icon, use the default
				inIconName = "default.gif";
				inIcon = new File(commonDir, inIconName);
			}
			try {
				//Now create an icon specifically for this file
				MircImage icon = new MircImage(inIcon);
				String outIconName = name+".gif";
				File outIcon = new File(iconDir, outIconName);
				icon.saveAsIconGIF(outIcon,96,target.getName());
				return outIcon;
			}
			catch (Exception ex) { }
		}
		return null;
	}

	//Send back a page with a message and a return button.
	private void sendMessage(HttpServletResponse res, String title, String message) {
		ServletUtil.sendPage(
			res,
			getPageWithReturnButton(title,message));
	}

	//Generate an HTML page that presents a message with a return button.
	private String getPageWithReturnButton(String title, String message) {
		return HtmlUtil.getPage(title,message,"window.open('/file/service','_self');","Return");
	}

	//Send back an error message
	private void sendErrorMessage(HttpServletResponse res, String error) {
		sendMessage(res,"Error",
			"An error occurred while attempting to access the file cabinet.<br/><br/>" + error);
	}

}
