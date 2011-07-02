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
import org.rsna.mircsite.dicomservice.TrialConfig;
import org.rsna.mircsite.util.FileUtil;
import org.rsna.mircsite.util.ServletUtil;
import org.rsna.mircsite.util.StringUtil;

/**
 * The Storage Configurator servlet.
 * <p>
 * This servlet provides a browser-accessible user interface for
 * configuring the storage service's storage.xml file.
 * <p>
 * This servlet responds to both HTTP GET and POST.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class StorageConfigurator extends HttpServlet {

	/**
	 * The servlet method that responds to an HTTP GET.
	 * <p>
	 * This method returns an HTML page containing a form for
	 * changing the entity values in the storage.xml file.
	 * The initial contents of the form are constructed
	 * from the values in the file.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doGet(
				HttpServletRequest req,
				HttpServletResponse res
				) throws ServletException {

		//Get the contents of the storage.xml file.
		File storageXMLFile = new File(getServletContext().getRealPath("/storage.xml"));
		String storageXML = FileUtil.getFileText(storageXMLFile);

		//Make the page and send it out.
		ServletUtil.sendPageNoCache(res,getPage(storageXML));
	}

	/**
	 * The servlet method that responds to an HTTP POST.
	 * <p>
	 * This method interprets the posted parameters as a new set
	 * of entity values and updates the storage.xml file accordingly.
	 * It then returns an HTML page containing a new form
	 * constructed from the new contents of the file.
	 * <p>
	 * Note that this method only modifies the entity values;
	 * it does not modify the XML text of the storage.xml file.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doPost(
				HttpServletRequest req,
				HttpServletResponse res
				) throws ServletException {

		//Get the storage.xml file.
		File storageXMLFile = new File(getServletContext().getRealPath("/storage.xml"));
		String storageXML = FileUtil.getFileText(storageXMLFile);

		//Force the encoding for the input.
		try { req.setCharacterEncoding("UTF-8"); }
		catch (Exception ignore) { }

		//Get the parameters and update the text string.
		Enumeration en = req.getParameterNames();
		while (en.hasMoreElements()) {
			String key = ((String)en.nextElement()).trim();
			String value = req.getParameter(key);
			storageXML = updateEntity(storageXML,key,value);
		}

		//Update the file.
		FileUtil.setFileText(storageXMLFile, FileUtil.utf8, storageXML);

		//Reload the Configuration
		StorageConfig.load(getServletContext());
		TrialConfig.load(getServletContext());

		//Make a new page from the new data and send it out.
		ServletUtil.sendPageNoCache(res,getPage(storageXML));
	}

	//Create an HTML page containing the form for configuring
	//the entity values.
	private String getPage(String storageXML) {
		Hashtable<String,String> h = hashEntities(storageXML);
		return responseHead() + makeTableRows(h) + responseTail();
	}

	//Create the rows in the form.
	private String makeTableRows(Hashtable<String,String> h) {
		String[] yesNo = new String[] {"yes","no"};
		String[] openRestricted = new String[] {"open","restricted"};
		String[] lmdateTitle = new String[] {"lmdate","title"};
		String rows = "";
		rows += makeRow("Mode",												StorageConfig.getMode());
		rows += makeRow("Query Mode",					"querymode",		h.get("querymode"),		openRestricted);
		rows += makeRow("Results Ordering",				"orderby",			h.get("orderby"),		lmdateTitle);
		rows += makeRow("Site Name",					"sitename",			h.get("sitename"));
		rows += makeRow("Tag Line",						"tagline",			h.get("tagline"));
		rows += makeRow("Site URL",											h.get("siteurl"));
		rows += makeRow("Servlet Name",										h.get("servletname"));
		rows += makeRow("Stored Documents Timeout (days)","doctimeout",		h.get("doctimeout"));
		rows += makeRow("Deleted Documents Timeout (days)","ddtimeout",		h.get("ddtimeout"));
		rows += makeRow("JPEG Quality Setting (0-100 or -1 for default)","jpegquality",	h.get("jpegquality"));
		rows += makeRow("Document Access Log Enabled",	"doclog",			h.get("doclog"),		yesNo);
		rows += makeRow("PHI Access Log Enabled",		"philog",			h.get("philog"),		yesNo);
		rows += makeRow("PHI Access Log Export Enabled","philogexport",		h.get("philogexport"),	yesNo);
		rows += makeRow("PHI Access Log Export URL",	"philogexporturl",	h.get("philogexporturl"));
		rows += makeRow("Author Service Enabled",		"docauthoring",		h.get("docauthoring"),	yesNo);
		rows += makeRow("Author Service Autoindex",		"authorindex",		h.get("authorindex"),	yesNo);
		rows += makeRow("Submit Service Enabled",		"docsubmission",	h.get("docsubmission"),	yesNo);
		rows += makeRow("Submit Service Autoindex",		"autoindex",		h.get("autoindex"),		yesNo);
		rows += makeRow("Maximum Submission Size (MB)",	"maxsize",			h.get("maxsize"));
		rows += makeRow("Zip Service Enabled",			"zipsubmission",	h.get("zipsubmission"),	yesNo);
		rows += makeRow("Zip Service Autoindex",		"zipautoindex",		h.get("zipautoindex"),	yesNo);
		rows += makeRow("Maximum Zip Size (MB)",		"zipmaxsize",		h.get("zipmaxsize"));
		rows += makeRow("DICOM Service Enabled",		"dicomenable",		h.get("dicomenable"),	yesNo);
		rows += makeRow("TCE Service Enabled",			"tceenable",		h.get("tceenable"),		yesNo);
		rows += makeRow("Version",											h.get("version"));
		return rows;
	}

	private String makeRow(String label, String value) {
		return "<tr><td>" + label + "</td><td>" + value + "</td></tr>\n";
	}

	private String makeRow(String label, String key, String value) {
		return "<tr><td>" + label + "</td><td>"
					+ "<input name=\""+key+"\" value=\""+value+"\"/>"
					+ "</td></tr>\n";
	}

	private String makeRow(String label, String key, String value, String[] values) {
		String row = "<tr><td>" + label + "</td><td>" + "<select name=\"" + key + "\">";
	    for (int i=0; i<values.length; i++) {
			row += "<option value=\"" + values[i] +"\"";
			if (value.trim().equals(values[i].trim())) row += " selected=\"true\"";
			row += ">" + values[i] + "</option>";
		}
		row += "</select></td></tr>\n";
		return row;
	}

	//Create a hashtable containing all the entity values
	//in an XML string.
	public static Hashtable hashEntities(String xml) {
		Hashtable h = new Hashtable();
		int begin = -1;
		int end;
		String name, value;
		while ((begin = xml.indexOf("!ENTITY",begin+1)) != -1) {
			begin = StringUtil.skipWhitespace(xml,begin+7);
			end = StringUtil.findWhitespace(xml,begin);
			name = xml.substring(begin,end);
			begin = xml.indexOf("\"",end) + 1;
			end = xml.indexOf("\"",begin);
			value = xml.substring(begin,end);
			if (name.equals("siteurl") && !value.startsWith("http://")) {
				value = "http://" + value;
			}
			h.put(name,value);
		}
		return h;
	}

	//Modify the value of a named entity in an XML string.
	private static String updateEntity(String xml, String theName, String theValue) {
		if (theValue != null) {
			int begin = -1;
			int end;
			String name, value;
			while ((begin = xml.indexOf("!ENTITY",begin+1)) != -1) {
				begin = StringUtil.skipWhitespace(xml,begin+7);
				end = StringUtil.findWhitespace(xml,begin);
				name = xml.substring(begin,end);
				if (name.equals(theName)) {
					begin = xml.indexOf("\"",end) + 1;
					end = xml.indexOf("\"",begin);
					return xml.substring(0,begin) + theValue + xml.substring(end);
				}
			}
		}
		return xml;
	}

	private String responseHead() {
		String head =
				"<html>\n"
			+	" <head>\n"
			+	"  <title>Storage Service Configurator</title>\n"
			+	"   <style>\n"
			+	"    body {background-color:#e0e0e0}\n"
			+	"    .td {text-align:left; padding 5}\n"
			+	"   </style>\n"
			+	" </head>\n"
			+	" <body>\n"
			+	"  <center>\n"
			+	"   <h1>Storage Service Configurator</h1>\n"
			+	"   <form method=\"post\" accept-charset=\"UTF-8\" action=\"\">\n"
			+	"    <table border=\"1\">\n";
		return head;
	}

	private String responseTail() {
		String tail =
				"    </table>\n"
			+	"    <br/>\n"
			+	"    <input type=\"submit\" value=\"Update storage.xml\">\n"
			+	"   </form>\n"
			+	"  </center>\n"
			+	" </body>\n"
			+	"</html>\n";
		return tail;
	}

}











