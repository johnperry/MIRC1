/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.storageservice;

import java.io.*;
import java.net.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.rsna.mircsite.dicomservice.TrialConfig;
import org.rsna.mircsite.util.*;

/**
 * The Update Service of the MIRC Storage Service.
 * <p>
 * The Update Service provides software and configuration file
 * updates for clinical trial field centers running the
 * FieldCenter application.
 * <p>
 * The servlet responds to both HTTP GET and POST.
 * A GET is used to get the latest version of a file.
 * A POST is used to submit an updated file.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class UpdateService extends HttpServlet {

	static final long delta = 1; //1 millisecond

	/**
	 * The servlet method that responds to an HTTP GET.
	 * <p>
	 * This method expects a filename in the PathInfo. If the filename is
	 * preceded with "sw/", the request is mapped to a single software
	 * directory; otherwise, the request is mapped to a directory whose
	 * name is the name of the user. If there is a "lastModified" query
	 * string parameter, that parameter is compared to the last modified
	 * date of the requested file. If the version on the server is newer by
	 * at least delta milliseconds, the version on the server is returned.
	 * If the file does not exist on the server or the server's version is
	 * not newer than the current version on the requesting site, a
	 * NOT_FOUND error is returned so the requestor knows he's on his own.
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

		//Get the user's name and information
		String username = req.getRemoteUser();
		if (username == null) {
			res.sendError(res.SC_FORBIDDEN);
			return;
		}

		//Get the path to the file
		String requestPath = req.getPathInfo();
		if (requestPath == null) requestPath = "";
		String filename = requestPath.substring(requestPath.lastIndexOf("/")+1);
		String filePath =
			TrialConfig.basepath +
				TrialConfig.trialpath +
					TrialConfig.updatepath +
						((requestPath.startsWith("/sw/")) ? "software" : username) +
							File.separator +
								 filename;

		//See if the requested file exists and is actually a file
		File file = new File(filePath);
		if (!file.exists() || !file.isFile()) {
			res.sendError(res.SC_NOT_FOUND);
			return;
		}

		//Get the last modified date from the request
		String lm = req.getParameter("lastModified");
		if (lm != null) {
			try {
				long lmTime = Long.parseLong(lm);
				if (file.lastModified() < lmTime + delta) {
					res.sendError(res.SC_NOT_FOUND);
					return;
				}
			}
			catch (Exception ignore) { }
		}

		//Okay, the file exists, and either it is substantially later than the
		//current version at the requesting site, or the requesting site didn't
		//specify a valid version time, so return the file.
		ServletUtil.sendBinaryFileContents(
			res,
			"application/x-update",
			file,
			true /*this forces inclusion of the disposition header with the filename*/ );
	}

	/**
	 * The servlet method that responds to an HTTP POST.
	 * <p>
	 * This method receives a posted file submission in the request's input stream.
	 * It stores the file in the user's directory, creating the directory if necessary.
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

		//Get the user's name and information
		String username = req.getRemoteUser();

		if (username == null) {
			res.sendError(res.SC_FORBIDDEN);
			return;
		}

		//Get the path to the user's directory and create it if necessary
		String dirPath =
			TrialConfig.basepath +
				TrialConfig.trialpath +
					TrialConfig.updatepath +
						username;
		File dir = new File(dirPath);
		dir.mkdirs();

		//Get the filename from the disposition header.
		String disposition = req.getHeader("Content-Disposition");
		if (disposition == null) {
			res.sendError(res.SC_FORBIDDEN);
			return;
		}
		String filename = disposition.substring(disposition.lastIndexOf("=")+1).trim();
		filename = filename.trim();
		filename = filename.substring(1,filename.length()-1).trim();
		if (filename.equals("")) {
			res.sendError(res.SC_FORBIDDEN);
			return;
		}
		File file = new File(dir,filename);

		//Get the last modified date from the request
		String lm = req.getParameter("lastModified");
		long lmTime = -1;
		if (lm != null) {
			try { lmTime = Long.parseLong(lm); }
			catch (Exception ex) { lmTime = -1; }
		}

		//Save the file
		try {
			BufferedInputStream sis = new BufferedInputStream(req.getInputStream());
			BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(file));

			byte[] b = new byte[10000];
			int len;
			while ((len=sis.read(b,0,b.length)) != -1) fos.write(b,0,len);
			fos.flush();
			fos.close();

			//If there was a last modified time, set it on the file;
			if (lmTime != -1) file.setLastModified(lmTime);

			res.sendError(res.SC_OK); //Success response
		}
		catch (Exception e) {
			res.sendError(res.SC_NOT_FOUND); //Failure response
		}
	}

}