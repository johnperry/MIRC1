/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.storageservice;

import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.rsna.mircsite.util.FileUtil;
import org.rsna.mircsite.util.HtmlUtil;
import org.rsna.mircsite.util.StringUtil;
import org.rsna.mircsite.util.XmlUtil;

import com.oreilly.servlet.MultipartRequest;

/**
 * The Zip Service of the MIRC Storage Service.
 * <p>
 * The Zip Service accepts multipart/form-data submissions of
 * zip files containing trees of files. It walks the tree,
 * creating MIRCdocuments from the files in each leaf directory.
 * <p>
 * The servlet responds to both HTTP GET and POST.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class ZipService extends HttpServlet {

	private static final long serialVersionUID = 12312312l;

	static final Logger logger = Logger.getLogger(ZipService.class);

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

		//Make sure document submission is enabled
		if (!checkEnabled(out)) return;

		//Get the user's name and information
		String userName = req.getRemoteUser();
		Author author = new Author(userName,StorageConfig.basepath + "/authors.xml");

		//Generate the submission page.
		String[] params = {
			"name",			author.name,
			"affiliation",	author.affiliation,
			"contact",		author.contact,
			"username",		author.username,
			"read",			"",
			"update",		"",
			"export",		"",
			"textext",		".txt",
			"skipext",		".dba",
			"skipprefix",	"__",
			"result",		""
		};
		File storage = new File(getServletContext().getRealPath("storage.xml"));
		File xsl = new File(getServletContext().getRealPath("zip/zip-service.xsl"));
		try {
			out.print(XmlUtil.getTransformedText(storage,xsl,params));
			out.flush();
			out.close();
		}
		catch (Exception ex) {
			logger.warn("Error creating the submission page.",ex);
			sendNoPageMessage(out);
		}
	}

	/**
	 * The servlet method that responds to an HTTP POST.
	 * <p>
	 * This method receives a zip file submission and starts a thread to process it.
	 * It uses the content type to determine how to receive and process the submission.
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
		int maxsize = StorageConfig.getMaxZipSize();
		maxsize = Math.max(maxsize,5)*1024*1024;

		//Make sure document submission is enabled
		if (!checkEnabled(out)) return;

		//Make a working directory to receive the submission.
		//This directory is in the submit subfolder of the storage service's root.
		String reldir = "zip/" + StringUtil.makeNameFromDate();
		String dir = getServletContext().getRealPath(reldir);
		File dirFile = new File(dir);
		dirFile.mkdirs();

		//Also make a File pointing to the storage service's documents directory.
		File docsFile = new File(getServletContext().getRealPath("documents"));

		//The result message
		String result = "";

		String name					= null;
		String affiliation			= null;
		String contact				= null;
		String username				= null;
		String read					= null;
		String update				= null;
		String export				= null;
		String textext				= null;
		String skipext				= null;
		String skipprefix			= null;
		String otString				= null;
		boolean overwriteTemplate	= false;
		String anString				= null;
		boolean anonymize			= false;
		File file 					= null;

		//Now get the data based on the content type
		String contentType = req.getContentType().toLowerCase();
		if (contentType.contains("multipart/form-data")) {

			//Get the parameters and the file from the multipart request
			MultipartRequest multipartRequest = new MultipartRequest(req, dir, maxsize, "UTF-8");

			name 			= multipartRequest.getParameter("name");
			affiliation 	= multipartRequest.getParameter("affiliation");
			contact 		= multipartRequest.getParameter("contact");
			username 		= multipartRequest.getParameter("username");
			read 			= multipartRequest.getParameter("read");
			update 			= multipartRequest.getParameter("update");
			export 			= multipartRequest.getParameter("export");
			textext 		= multipartRequest.getParameter("textext");
			skipext 		= multipartRequest.getParameter("skipext");
			skipprefix		= multipartRequest.getParameter("skipprefix");
			otString		= multipartRequest.getParameter("overwrite");
			overwriteTemplate = (otString != null) && otString.equals("overwrite");
			anString		= multipartRequest.getParameter("anonymize");
			anonymize		= (anString != null) && anString.equals("anonymize");

			file = getMultipartFormFile(multipartRequest);
		}
		else if (contentType.contains("application/x-zip-compressed")
					||
				 contentType.contains("application/x-mirc")) {

			//Get the parameters from the HttpServletRequest and then
			//get the file from the input stream
			name 			= fix(req.getParameter("name"));
			affiliation 	= fix(req.getParameter("affiliation"));
			contact 		= fix(req.getParameter("contact"));
			username 		= fix(req.getParameter("username"));
			read 			= fix(req.getParameter("read"));
			update 			= fix(req.getParameter("update"));
			export 			= fix(req.getParameter("export"));
			textext 		= fix(req.getParameter("textext"));
			skipext 		= fix(req.getParameter("skipext"));
			skipprefix		= fix(req.getParameter("skipprefix"));
			otString		= req.getParameter("overwrite");
			overwriteTemplate = (otString != null) && otString.equals("overwrite");
			anString		= req.getParameter("anonymize");
			anonymize		= (anString != null) && anString.equals("anonymize");

			file = getFileFromStream(req, dirFile);
		}
		if (file == null) result += "It appears that no file was posted.|";

		else {
			//Create a thread to process the file and kick it off.
			//This thread will unpack the file (which must be a zip file)
			//and create MIRCdocuments for the files it contains.
			String publisher = StorageConfig.getPublisherRoleName();
			boolean publish = StorageConfig.zipAutoindex() || req.isUserInRole(publisher);
			try {
				File template = new File(getServletContext().getRealPath("/zip/template.xml"));
				ZipThread zipThread =
					new ZipThread(
							name,
							affiliation,
							contact,
							publish,
							template,
							username,
							read,
							update,
							export,
							docsFile,
							dirFile,
							file,
							textext,
							skipext,
							skipprefix,
							overwriteTemplate,
							anonymize);
				zipThread.start();
				result += "The file was received and queued for processing.";
			}
			catch (Exception ex) {
				logger.warn("Exception while creating the ZipThread.",ex);
				result += "Unable to create the processing thread for the submission.|";
				FileUtil.deleteAll(dirFile);
			}
		}

		//If the submission was multipart/form-data,
		//generate the submission + results page.
		if (contentType.contains("multipart/form-data")) {

			String[] params = {
				"sitename",		sitename,
				"name",			name,
				"affiliation",	affiliation,
				"contact",		contact,
				"username",		username,
				"read",			read,
				"update",		update,
				"export",		export,
				"textext",		textext,
				"skipext",		skipext,
				"skipprefix",	skipprefix,
				"result",		result
			};
			File storage = new File(getServletContext().getRealPath("storage.xml"));
			File xsl = new File(getServletContext().getRealPath("zip/zip-service.xsl"));
			try {
				out.print(XmlUtil.getTransformedText(storage,xsl,params));
				out.flush();
				out.close();
			}
			catch (Exception ex) {
				logger.warn("Error creating the submission page.",ex);
				sendNoPageMessage(out);
			}
		}
		else {
			out.print(result);
			out.flush();
			out.close();
		}
	}

	private String fix(String s) {
		return (s != null) ? s : "";
	}

	//Receive a multipart form and get the first file it contains.
	//Note: only the first file is returned. It is assumed to be a
	//zip file containing the entire submission.
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

	//Receive a file from the InputStream of the request.
	private File getFileFromStream(HttpServletRequest req, File dir)
			throws IOException {
		File file = null;
		try {
			file = File.createTempFile("zip-", ".zip", dir);
			BufferedInputStream bis = new BufferedInputStream(req.getInputStream());
			FileOutputStream fos = new FileOutputStream(file);
			byte[] b = new byte[10000];
			int len;
			while ((len=bis.read(b,0,b.length)) >= 0) {
				fos.write(b,0,len);
			}
			fos.flush();
			fos.close();
		}
		catch (Exception e) {
			file = null;
			logger.info("Exception caught while receiving a file.");
		}
		return file;
	}

	//Check that the zip service is enabled and return a page if it is not.
	private boolean checkEnabled(PrintWriter out) {
		if (StorageConfig.zipEnabled()) return true;
		//It's not enabled; display a page
		send(out,"Not Enabled","Zip submission is not enabled on this site.");
		return false;
	}

	//Send a page saying the submission page could not be created.
	private void sendNoPageMessage(PrintWriter out) {
		send(out, "Unable", "Unable to create the submission page.");
	}

	//Send a page with a closebox

	private void send(PrintWriter out, String title, String message) {
		out.print(HtmlUtil.getStyledPageWithCloseBox("service.css", title, message, "error"));
		out.flush();
		out.close();
	}

}