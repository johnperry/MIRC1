/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.fileservice;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.rsna.mircsite.util.Conferences;
import org.rsna.mircsite.util.ContentType;
import org.rsna.mircsite.util.FileUtil;
import org.rsna.mircsite.util.HtmlUtil;
import org.rsna.mircsite.util.ServletUtil;
import org.rsna.mircsite.util.StringUtil;
import org.rsna.mircsite.util.XmlUtil;
import org.w3c.dom.Document;

/**
 * The File Service Admin Service servlet.
 * <p>
 * This servlet provides a browser-accessible user interface for
 * configuring the file service's fileservice.xml file. It also
 * provides control of the File Service's DICOM service as well
 * as a link to the Anonymizer Configurator.
 * <p>
 * This servlet responds to both HTTP GET and POST.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class AdminService extends HttpServlet {

	String[] yesNo = new String[] {"yes","no"};

	/** The name of the File Service configuration file. */
	public static String configFilename = "fileservice.xml";
	/** The autostart attribute */
	public static String autostart = "no";
	/** The autostart enabled flag */
	public static boolean autostartEnabled = false;
	/** The Application Entity Title for the File Service DICOM Storage SCP */
	public static String aeTitle = "FILESERVICE";
	/** The File Service DICOM Storage SCP port */
	public static String port = "7777";
	/** The anonymize element value */
	public static String anonymize = "yes";
	/** The anonymizer enabled flag */
	public static boolean anonymizerEnabled = true;
	/** The shared file cabinet timeout in hours */
	public static int timeout = 0;
	/** The root of the servlet */
	public static File root = null;
	/** The File Service configuration file */
	public static File configFile = null;

	static DicomObjectProcessor dicomObjectProcessor;
	SharedFileCabinetManager sfcManager;

	static int maxsize = 75;

	/**
	 * Initialize the configuration on startup. This method is called when the
	 * servlet container parses the webapp's web.xml file. All the static
	 * configuration parameters for the file service are loaded here.
	 */
	public void init() {
		ContentType.load(getServletContext());
		root = new File(getServletContext().getRealPath("/"));
		Conferences.load(root);	//load the conferences database
		configFile = new File(root,configFilename);
		getFileServiceConfig();
		dicomObjectProcessor = new DicomObjectProcessor();
		if (autostartEnabled) dicomObjectProcessor.start();
		sfcManager = new SharedFileCabinetManager(root);
		sfcManager.start();
	}

	/**
	 * Close down the servlet. This method only closes the Conferences database.
	 * This method is called when the servlet container takes the servlet out of service.
	 */
	public void destroy() {
		Conferences.close();
	}

	/**
	 * The servlet method that responds to an HTTP GET.
	 * <p>
	 * This method returns an HTML page containing a form for
	 * changing the contents of the fileservice.xml file.
	 * The initial contents of the form are constructed
	 * from the values in the file.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doGet(
			HttpServletRequest req,
			HttpServletResponse res)
				throws ServletException {

		//Force the encoding for the input.
		try { req.setCharacterEncoding("UTF-8"); }
		catch (Exception ignore) { }

		//See if this is an SCP start
		if (req.getParameter("start") != null) {
			if (!dicomObjectProcessor.isAlive())
				dicomObjectProcessor.start();
			else dicomObjectProcessor.restartSCP();
		}

		//Make the page and send it out.
		ServletUtil.sendPageNoCache(res,getPage());
	}

	/**
	 * The servlet method that responds to an HTTP POST.
	 * <p>
	 * This method interprets the posted parameters as a new configuration
	 * for the trial/trial.xml file and updates the file accordingly.
	 * It then returns an HTML page containing a new form
	 * constructed from the new contents of the file.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doPost(
			HttpServletRequest req,
			HttpServletResponse res)
				throws ServletException {

		//Force the encoding for the input.
		try { req.setCharacterEncoding("UTF-8"); }
		catch (Exception ignore) { }

		//Get the parameters
		String autostart = req.getParameter("autostart").trim();
		String dicomStoreAETitle = req.getParameter("dmae").trim();
		String dicomStorePort = req.getParameter("dmport").trim();
		String anonymize = req.getParameter("dmanon").trim();
		String maxsizeString = req.getParameter("maxsize").trim();
		try { maxsize = Integer.parseInt(maxsizeString); }
		catch (Exception ex) { maxsize = 75; }
		if (maxsize < 50) maxsize = 50;
		String timeoutString = req.getParameter("timeout").trim();
		int timeout;
		try { timeout = Integer.parseInt(timeoutString); }
		catch (Exception ex) { timeout = 0; }

		boolean scpParamsChanged =
			!dicomStoreAETitle.equals(this.aeTitle) ||
			!dicomStorePort.equals(this.port);

		//Create the fileservice.xml text string
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n"
					+"<file-service>\n\n"
					+"  <dicom-store autostart=\"" + autostart + "\">\n"
					+"    <ae-title>" + dicomStoreAETitle + "</ae-title>\n"
					+"    <port>" + dicomStorePort + "</port>\n"
					+"    <anonymize>" + anonymize + "</anonymize>\n"
					+"  </dicom-store>\n\n"
					+"  <maxsize>"+maxsize+"</maxsize>\n\n"
					+"  <timeout>"+timeout+"</timeout>\n\n"
					+"</file-service>\n";

		//Make sure that this string parses
		try { XmlUtil.getDocumentFromString(xml); }
		catch (Exception e) {
			ServletUtil.sendPageNoCache(res,getErrorPage(e.getMessage()));
			return;
		}

		//Update the file
		FileUtil.setFileText(configFile, FileUtil.utf8, xml);

		//Restart the SCP if necessary
		if (scpParamsChanged && dicomObjectProcessor.isRunning()) {
			getFileServiceConfig();
			dicomObjectProcessor.restartSCP();
		}

		//Make a new page from the new data and send it out
		ServletUtil.sendPageNoCache(res,getPage());
	}

	/**
	 * Get the File Service configuration file and load its values
	 * into the static fields for access by other servlets.
	 * @return the XML Document containing the configuration parameters.
	 * @throws Exception if the parameters cannot be obtained.
	 */
	public boolean getFileServiceConfig() {
		try {
			Document xmlDoc = XmlUtil.getDocument(configFile);
			autostart =
				XmlUtil.getValueViaPath(
					xmlDoc,"file-service/dicom-store@autostart").trim();
			autostartEnabled = autostart.equals("yes");
			aeTitle =
				XmlUtil.getValueViaPath(
					xmlDoc,"file-service/dicom-store/ae-title").trim();
			port =
				XmlUtil.getValueViaPath(
					xmlDoc,"file-service/dicom-store/port").trim();
			anonymize =
				XmlUtil.getValueViaPath(
					xmlDoc,"file-service/dicom-store/anonymize").trim();
			anonymizerEnabled = anonymize.equals("yes");
			String maxsizeString =
				XmlUtil.getValueViaPath(
					xmlDoc,"file-service/maxsize").trim();
			try { maxsize = Integer.parseInt(maxsizeString); }
			catch (Exception ex) { maxsize = 75; }
			if (maxsize < 50) maxsize = 50;
			String timeoutString =
				XmlUtil.getValueViaPath(
					xmlDoc,"file-service/timeout").trim();
			try { timeout = Integer.parseInt(timeoutString); }
			catch (Exception ex) { timeout = 0; }
			if (timeout < 0) timeout = 0;
			return true;
		}
		catch (Exception failed) { return false; }
	}

	//Create an HTML page containing the form for configuring the file.
	private String getPage() {
		if (getFileServiceConfig())
			return responseHead() + makeTables() + responseTail();
		else return getUnablePage();
	}

	private String makeTables() {
		String table =
			"<h3>DICOM Service</h3>\n" +
			"<table border=\"1\">\n" +
			makeRow2("Autostart","autostart",autostart,yesNo) +
			makeRow2("Status",getStatus()) +
			"</table>\n" +
			"<br><table border=\"1\">\n" +
			makeRow2("Store AE Title","dmae",aeTitle,150) +
			makeRow2("Store Port","dmport",port,150) +
			makeRow2("Anonymizer Enabled","dmanon",anonymize,yesNo) +
			makeRow2("Maximum upload size (MB)","maxsize",""+maxsize,50) +
			makeRow2("Shared File Cabinet Timeout (hours)","timeout",""+timeout,50) +
			"</table>\n" +
			"<br><input type=\"button\" value=\"Update the Anonymizer\" "
				+ "onclick=\"window.open('admin/anconfig','_blank');\"/>\n" +
			"<br><br><input type=\"button\" value=\"Start/restart the DICOM Service\" "
				+ "onclick=\"window.open('admin?start','_self');\"/>\n" +
			"<br>";
		return table;
	}

	private String getStatus() {
		if (dicomObjectProcessor != null)
			return dicomObjectProcessor.getStatus();
		return "not running";
	}

	private String makeRow2(String label, String value) {
		return "<tr><td><b>" + label + "</b></td><td>" + value + "</td></tr>\n";
	}

	private String makeRow2(String label, String key, String value) {
		return "<tr><td><b>" + label + "</b></td><td>"
				+ "<input name=\""+key+"\" value=\""+value+"\"/>"
				+ "</td></tr>\n";
	}

	private String makeRow2(String label, String key, String value, int width) {
		return "<tr><td><b>" + label + "</b></td><td>"
				+ "<input style=\"width:"+width+"\" name=\""+key+"\" value=\""+value+"\"/>"
				+ "</td></tr>\n";
	}

	private String makeRow2(String label, String key, String value, String[] values) {
		String row = "<tr><td><b>" + label + "</b></td><td>" + "<select name=\"" + key + "\">";
	    for (int i=0; i<values.length; i++) {
			row += "<option value=\"" + values[i] +"\"";
			if (value.trim().equals(values[i].trim())) row += " selected=\"true\"";
			row += ">" + values[i] + "</option>";
		}
		row += "</select></td></tr>\n";
		return row;
	}

	private String responseHead() {
		String head =
				"<html>\n"
			+	" <head>\n"
			+	"  <title>File Service Admin</title>\n"
			+	"   <style>\n"
			+	"    body {background-color:#c6d8f9; margin:0; padding:0;}\n"
			+	"    h1 {padding-top:10;}\n"
			+	"    input {width:300}\n"
			+	"    th {text-align:center; padding 5}\n"
			+	"    td {text-align:left; padding:1 5 1 5}\n"
			+	"   </style>\n"
			+	" </head>\n"
			+	" <body>\n"
			+	HtmlUtil.getCloseBox()
			+	"  <center>\n"
			+	"   <h1>File Service Admin</h1>\n"
			+	"   <form method=\"post\" action=\"\" accept-charset=\"UTF-8\">\n";
		return head;
	}

	private String responseTail() {
		String tail =
				"    <br/>\n"
			+	"    <input type=\"submit\" value=\"Update fileservice.xml\">\n"
			+	"   </form>\n"
			+	"  </center>\n"
			+	" </body>\n"
			+	"</html>\n";
		return tail;
	}

	private String getErrorPage(String message) {
		return	"<html>\n"
			+	" <head>\n"
			+	"  <title>File Service Admin</title>\n"
			+	"   <style>\n"
			+	"    body {background-color:#c6d8f9; margin:0; padding:0;}\n"
			+	"    h1 {padding-top:10;}\n"
			+	"   </style>\n"
			+	" </head>\n"
			+	" <body>\n"
			+	"  <center>\n"
			+	"   <h1>File Service Admin</h1>\n"
			+	"   <p>The submitted data caused the fileservice xml object not to parse.</p>\n"
			+	"   <p>The fileservice.xml file was not updated.</p>\n"
			+	"   <p>The parser's error message was:</p>\n"
			+	"   <p>" + message + "</p>\n"
			+	"  </center>\n"
			+	" </body>\n"
			+	"</html>\n";
	}

	private String getUnablePage() {
		return	"<html>\n"
			+	" <head>\n"
			+	"  <title>File Service Admin</title>\n"
			+	"   <style>\n"
			+	"    body {background-color:#c6d8f9; margin:0; padding:0;}\n"
			+	"    h1 {padding-top:10;}\n"
			+	"   </style>\n"
			+	" </head>\n"
			+	" <body>\n"
			+	"  <center>\n"
			+	"   <h1>File Service Admin</h1>\n"
			+	"   <p>The servlet was unable to obtain the fileservice xml object.</p>\n"
			+	"  </center>\n"
			+	" </body>\n"
			+	"</html>\n";
	}
}
