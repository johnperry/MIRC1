/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.dicomservice;

import java.io.*;
import java.net.*;
import java.util.Calendar;
import javax.servlet.*;
import javax.servlet.http.*;
import org.apache.log4j.Logger;
import org.rsna.mircsite.log.Log;
import org.rsna.mircsite.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The HttpImportService of the MIRC DICOM Service.
 * <p>
 * The Submit Service accepts application/x-mirc-dicom submissions of
 * DICOM objects and queues them for processing by the ObjectProcessor.
 * <p>
 * The servlet responds only to HTTP POST.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class HttpImportService extends HttpServlet {

	static final String serviceName = "HttpImportService";
	static final Logger logger = Logger.getLogger(HttpImportService.class);

	/**
	 * The servlet method that responds to an HTTP POST.
	 * <p>
	 * This method receives an object submission and queues
	 * it for processing by the ObjectProcessor.
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

		res.setContentType("text/html; charset=\"UTF-8\"");
		PrintWriter out = res.getWriter();

		//Check the extra path info to see what is requested.
		//The options are a /doc or /ping.
		String pathInfo = req.getPathInfo();
		if (pathInfo == null) return;
		pathInfo = pathInfo.toLowerCase();

		//For now, don't protect the ping response by checking
		//whether the connection is from a participant because this
		//function is to be used during setup of a new Field Center
		//and the Principal Investigator site may not yet have been
		//configured for it.
		if (pathInfo.equals("/ping")) {
			out.println("OK");
			out.println("Received Content-Type = " + req.getContentType());
			out.println("Received Content-Length = " + req.getContentLength());
			out.flush();
			out.close();
			return;
		}

		//If it's not a /ping and it's not a /doc, ignore the
		//request on the theory that somebody is fooling around.
		if (!pathInfo.equals("/doc")) return;

		//See if this is a connection from a participating site
		//and return with no response if it is not.
		//Note: if the participant does not have a public IP address, this
		//method probably doesn't work, in which case the workaround is
		//to accept everybody.
		if (!TrialConfig.hasParticipant(req.getRemoteAddr())) {
			Log.message(serviceName+": Message from non-participant received and ignored: "
						+ req.getRemoteAddr());
			logger.warn("Message from non-participant received and ignored: "
						+ req.getRemoteAddr());
			return;
		}

		//Check the Content-Type
		String contentType = req.getContentType().toLowerCase();
		if (contentType.indexOf("application/x-mirc") < 0 ) {
			out.print("Server: Unsupported Content-Type: " + req.getContentType());
			out.close();
			return;
		}

		//The content-type was application/x-mirc.
		//This is a DICOM import from another clinical trial service.
		//Make sure the http-store directory exists to receive the document.
		File dir = new File(getServletContext().getRealPath(TrialConfig.httpStoreDir));
		dir.mkdirs();

		//Now get the posted file
		File file = getPostedFile(req,dir);

		//See what we got
		if (file == null) {
			out.print("Server: Error receiving the file");
			out.close();
			return;
		}

		//Rename it to the http-import directory so the
		//ObjectProcessor will pick it up.
		File outFile = new File(getServletContext().getRealPath(TrialConfig.httpImportDir));
		outFile.mkdirs();
		outFile = new File(outFile,file.getName());
		if (file.renameTo(outFile)) Log.message(serviceName+": Object received from "
												+ req.getRemoteAddr() + ":"+file.getName());
		else {
			Log.message(serviceName+": Unable to move object to http-import: [" +
						req.getRemoteAddr() + ": "+file.getName() + "]");
			logger.warn("Unable to move object to http-import: [" +
						req.getRemoteAddr() + ": "+file.getName() + "]");
			file.delete();
		}

		//Return OK
		out.print("OK");
		out.close();
		return;
	}

	//Read one file from the servlet request.
	//Write the file with a temporary name in the supplied
	//directory and return a File pointing to the result.
	private File getPostedFile(HttpServletRequest req, File dir) {
		File file;
		try {
			String prefix = req.getRequestURL().toString();
			prefix = (new URL(prefix)).getProtocol() + "-";
			file = File.createTempFile(prefix,".md",dir);
		}
		catch (Exception e) {
			Log.message(serviceName+": Unable to create a temp file to receive an object.");
			logger.warn("Unable to create a temp file to receive an object.");
			file = null;
			return file;
		}
		try {
			ServletInputStream sis = req.getInputStream();
			FileOutputStream fos = new FileOutputStream(file);
			byte[] b = new byte[10000];
			int len;
			while ((len=sis.read(b,0,b.length)) > 0) fos.write(b,0,len);
			fos.flush();
			fos.close();
		}
		catch (Exception e) {
			file = null;
			Log.message(serviceName+": Exception caught while receiving an object.");
			logger.info("Exception caught while receiving an object.");
		}
		return file;
	}

}