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
import org.rsna.mircsite.tceservice.TCEConfig;
import org.rsna.mircsite.util.FileUtil;
import org.rsna.mircsite.util.ServletUtil;
import org.rsna.mircsite.util.StringUtil;
import org.rsna.mircsite.util.XmlUtil;

/**
 * The TCE Configurator servlet.
 * <p>
 * This servlet provides a browser-accessible user interface for
 * configuring the storage service's tce/tce.xml file.
 * <p>
 * This servlet responds to both HTTP GET and POST.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class TCEConfigurator extends HttpServlet {

	String[] yesNo = new String[] {"yes","no"};

	/**
	 * The servlet method that responds to an HTTP GET.
	 * <p>
	 * This method returns an HTML page containing a form for
	 * changing the contents of the trial/trial.xml file.
	 * The initial contents of the form are constructed
	 * from the values in the file.
	 * <p>
	 * The TCEConfigurator uses the TCEConfig object to obtain
	 * and save the tce.xml file. This is a different approach from
	 * that of the StorageConfigurator because the storage.xml file is
	 * completely driven by the DTD ENTITY values and the tce.xml
	 * file is not.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doGet(HttpServletRequest req, HttpServletResponse res)
											throws ServletException {

		//Make the page from the TCEConfig object and send it out.
		ServletUtil.sendPageNoCache(res,getPage());
	}

	/**
	 * The servlet method that responds to an HTTP POST.
	 * <p>
	 * This method interprets the posted parameters as a new configuration
	 * for the tce/tce.xml file and updates the file accordingly.
	 * It then returns an HTML page containing a new form
	 * constructed from the new contents of the file.
	 * <p>
	 * The TCEConfigurator uses the TCEConfig object to obtain
	 * and save the tce.xml file. This is a different approach from
	 * that of the StorageConfigurator because the storage.xml file is
	 * completely driven by the DTD ENTITY values and the tce.xml
	 * file is not.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doPost(HttpServletRequest req, HttpServletResponse res)
											throws ServletException {

		//Force the encoding for the input.
		try { req.setCharacterEncoding("UTF-8"); }
		catch (Exception ignore) { }

		//Get the parameters
		String proxyPassword = req.getParameter("pxpw");
		String autostart = req.getParameter("autostart");
		String autocreate = req.getParameter("autocreate");
		String password = req.getParameter("password");
		String roles = req.getParameter("roles");
		String dicomStoreAETitle = req.getParameter("dmae");
		String dicomStorePort = req.getParameter("dmport");

		//Create the trial.xml text string
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n"
					+"<tce-service autostart=\"" + autostart + "\"\n"
					+"             autocreate=\"" + autocreate + "\"\n"
					+"             password=\"" + password + "\"\n"
					+"             roles=\"" + roles + "\">\n\n"
					+"  <dicom-store>\n"
					+"    <ae-title>" + dicomStoreAETitle + "</ae-title>\n"
					+"    <port>" + dicomStorePort + "</port>\n"
					+"  </dicom-store>\n\n"
					+"</tce-service>\n";

		//Make sure that this string parses
		try { XmlUtil.getDocumentFromString(xml); }
		catch (Exception e) {
			ServletUtil.sendPageNoCache(res,getErrorPage(e.getMessage()));
			return;
		}

		//Update the file
		File file = new File(TCEConfig.basepath + TCEConfig.configFilename);
		FileUtil.setFileText(file, FileUtil.utf8, xml);

		//Reload the configuration
		TCEConfig.load(getServletContext());

		//Make a new page from the new data and send it out
		ServletUtil.sendPageNoCache(res,getPage());
	}

	//Create an HTML page containing the form for configuring the file.
	private String getPage() {
		return responseHead() + makeTables() + responseTail();
	}

	private String makeTables() {
		return makeMainTable() + makeDicomImportTable();
	}

	private String makeMainTable() {
		String table = "<table border=\"1\">\n";
		table += makeRow2("Autostart","autostart",TCEConfig.getAutostart(),yesNo);
		table += makeRow2("Account Autocreate","autocreate",TCEConfig.getAccountAutocreate(),yesNo);
		table += makeRow2("Account Password","password",TCEConfig.getAccountPassword(),150);
		table += makeRow2("Account Roles","roles",TCEConfig.getAccountRoles(),150);
		table += "</table>\n";
		return table;
	}

	private String makeDicomImportTable() {
		String table = "<br><h3>TCE Import Service</h3>\n";
		table += "<table border=\"1\">\n";
		table += makeRow2("Store AE Title","dmae",TCEConfig.getDicomStoreAETitle(),150);
		table += makeRow2("Store Port","dmport",TCEConfig.getDicomStorePort(),150);
		table += "</table>\n";
		return table;
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
			+	"  <title>TCE Service Configurator</title>\n"
			+	"   <style>\n"
			+	"    body {background-color:#e0e0e0}\n"
			+	"    input {width:300}\n"
			+	"    th {text-align:center; padding 5}\n"
			+	"    td {text-align:left; padding:1 5 1 5}\n"
			+	"   </style>\n"
			+	" </head>\n"
			+	" <body>\n"
			+	"  <center>\n"
			+	"   <h1>TCE Service Configurator</h1>\n"
			+	"   <form method=\"post\" accept-charset=\"UTF-8\" action=\"\">\n";
		return head;
	}

	private String responseTail() {
		String tail =
				"    <br/>\n"
			+	"    <input type=\"submit\" value=\"Update tce.xml\">\n"
			+	"   </form>\n"
			+	"  </center>\n"
			+	" </body>\n"
			+	"</html>\n";
		return tail;
	}

	private String getErrorPage(String message) {
		String page =
				"<html>\n"
			+	" <head>\n"
			+	"  <title>TCE Service Configurator</title>\n"
			+	"   <style>\n"
			+	"    body {background-color:#e0e0e0}\n"
			+	"   </style>\n"
			+	" </head>\n"
			+	" <body>\n"
			+	"  <center>\n"
			+	"   <h1>TCE Service Configurator</h1>\n"
			+	"   <p>The submitted data caused the tce xml object not to parse.</p>\n"
			+	"   <p>The tce.xml file was not updated.</p>\n"
			+	"   <p>The parser's error message was:</p>\n"
			+	"   <p>" + message + "</p>\n";
		return page;
	}

}
