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
import org.rsna.mircsite.util.XmlUtil;

/**
 * The Trial Configurator servlet.
 * <p>
 * This servlet provides a browser-accessible user interface for
 * configuring the storage service's trial/trial.xml file.
 * <p>
 * This servlet responds to both HTTP GET and POST.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class TrialConfigurator extends HttpServlet {

	String[] yesNo = new String[] {"yes","no"};
	String[] autoQC = new String[] {"auto","QC"};
	String[] autoQCdisabled = new String[] {"auto","QC","disabled"};

	/**
	 * The servlet method that responds to an HTTP GET.
	 * <p>
	 * This method returns an HTML page containing a form for
	 * changing the contents of the trial/trial.xml file.
	 * The initial contents of the form are constructed
	 * from the values in the file.
	 * <p>
	 * The TrialConfigurator uses the TrialConfig object to obtain
	 * and save the trial.xml file. This is a different approach from
	 * that of the StorageConfigurator because the storage.xml file is
	 * completely driven by the DTD ENTITY values and the trial.xml
	 * file is not.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doGet(HttpServletRequest req, HttpServletResponse res)
											throws ServletException {

		//Make the page from the TrialConfig object and send it out.
		ServletUtil.sendPageNoCache(res,getPage());
	}

	/**
	 * The servlet method that responds to an HTTP POST.
	 * <p>
	 * This method interprets the posted parameters as a new configuration
	 * for the trial/trial.xml file and updates the file accordingly.
	 * It then returns an HTML page containing a new form
	 * constructed from the new contents of the file.
	 * <p>
	 * The TrialConfigurator uses the TrialConfig object to obtain
	 * and save the trial.xml file. This is a different approach from
	 * that of the StorageConfigurator because the storage.xml file is
	 * completely driven by the DTD ENTITY values and the trial.xml
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
		String keyfile = req.getParameter("keyfile");
		String uidroot = req.getParameter("uidroot");
		String basedate = req.getParameter("basedate");
		String ptidpref = req.getParameter("ptprefix");
		String ptidsuff = req.getParameter("ptsuffix");
		String ptidfirst = req.getParameter("ptfirst");
		String ptidwidth = req.getParameter("ptwidth");

		String autostart = req.getParameter("autostart");
		String log = req.getParameter("log");
		String overwrite = req.getParameter("overwrite");
		String dicomStoreAETitle = req.getParameter("dmae");
		String dicomStorePort = req.getParameter("dmport");
		String dicomImportAnonymize = req.getParameter("dmanon");
		String dicomExportMode = req.getParameter("demode");
		String[] dicomExportAETitle = getArray(req,"dxaet");
		String[] dicomExportIP = getArray(req,"dxip");
		String[] dicomExportDir = getArray(req,"dxdir");
		String httpImportAnonymize = req.getParameter("hmanon");
		String[] httpImportIP = getArray(req,"hmip");
		String[] httpExportURL = getArray(req,"hxurl");
		String[] httpExportDir = getArray(req,"hxdir");
		String databaseExportMode = req.getParameter("dbemode");
		String databaseExportAnonymize = req.getParameter("dbeanon");
		String databaseClassName = req.getParameter("dbclass");
		String databaseExportInterval = req.getParameter("dbint");
		String preprocessorEnabled = req.getParameter("ppenb");
		String preprocessorClassName = req.getParameter("ppclass");

		//Create the trial.xml text string
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n"
					+"<clinical-trial overwrite=\""+overwrite+"\" autostart=\"" + autostart + "\" log=\"" + log + "\">\n\n"
					+"  <remapper key-file=\""+keyfile+"\">\n"
					+"    <uid-root>"+uidroot+"</uid-root>\n"
					+"    <base-date>"+basedate+"</base-date>\n"
					+"    <patient-id first=\""+ptidfirst+"\" width=\""+ptidwidth+"\">\n"
					+"      <prefix>"+ptidpref+"</prefix>\n"
					+"      <suffix>"+ptidsuff+"</suffix>\n"
					+"    </patient-id>\n"
					+"  </remapper>\n\n"
					+"  <dicom-store>\n"
					+"    <ae-title>" + dicomStoreAETitle + "</ae-title>\n"
					+"    <port>" + dicomStorePort + "</port>\n"
					+"    <anonymize>" + dicomImportAnonymize + "</anonymize>\n"
					+"  </dicom-store>\n\n";

		xml += "  <http-export>\n";
		for (int i=0; i<httpExportURL.length; i++) {
			if (!httpExportURL[i].equals("") && !httpExportDir[i].equals("")) {
				xml += "    <site directory=\"" + httpExportDir[i] + "\">"
						+ httpExportURL[i]
						+ "</site>\n";
			}
		}
		xml += "  </http-export>\n\n";
		xml += "  <http-import>\n";
		xml += "    <preprocessor enabled=\""+preprocessorEnabled+"\">\n";
		xml += "      <preprocessor-class-name>" + preprocessorClassName + "</preprocessor-class-name>\n";
		xml += "    </preprocessor>\n\n";
		xml += "    <anonymize>" + httpImportAnonymize + "</anonymize>\n";
		for (int i=0; i<httpImportIP.length; i++) {
			if (!httpImportIP[i].equals("")) {
				xml += "    <site>" + httpImportIP[i] + "</site>\n";
			}
		}
		xml += "  </http-import>\n\n";
		xml += "  <dicom-export mode=\""+dicomExportMode+"\">\n";
		for (int i=0; i<dicomExportIP.length; i++) {
			if (!dicomExportAETitle[i].equals("")
					&& !dicomExportIP[i].equals("")
					&& !dicomExportDir[i].equals("")) {
				xml += "    <destination-dicom-store directory=\"" + dicomExportDir[i] + "\">\n";
				xml += "      <ae-title>" + dicomExportAETitle[i] + "</ae-title>\n";
				xml += "      <ip-address>" + dicomExportIP[i] + "</ip-address>\n";
				xml += "    </destination-dicom-store>\n";
			}
		}
		xml += "  </dicom-export>\n\n";
		xml += "  <database-export mode=\""+databaseExportMode+"\">\n";
		xml += "    <anonymize>" + databaseExportAnonymize + "</anonymize>\n";
		xml += "    <interval>" + databaseExportInterval + "</interval>\n";
		xml += "    <database-class-name>" + databaseClassName + "</database-class-name>\n";
		xml += "  </database-export>\n\n";
		xml += "</clinical-trial>\n";

		//Make sure that this string parses
		try { XmlUtil.getDocumentFromString(xml); }
		catch (Exception e) {
			ServletUtil.sendPageNoCache(res,getErrorPage(e.getMessage()));
			return;
		}

		//Update the file
		File file = new File(TrialConfig.basepath + TrialConfig.configFilename);
		FileUtil.setFileText(file, FileUtil.utf8, xml);

		//Reload the configuration
		StorageConfig.load(getServletContext());
		TrialConfig.load(getServletContext());

		//Make a new page from the new data and send it out
		ServletUtil.sendPageNoCache(res,getPage());
	}

	//Find all the values of a specific parameter array
	//and return them in a String[].
	private String[] getArray(HttpServletRequest req, String param) {
		LinkedList list = new LinkedList();
		boolean done = false;
		Object value;
		for (int i=0; ((value=req.getParameter(param+"["+i+"]"))!=null); i++)
			list.add(value);
		return (String[])list.toArray(new String[list.size()]);
	}

	//Create an HTML page containing the form for configuring the file.
	private String getPage() {
		return responseHead() + makeTables() + responseTail();
	}

	private String makeTables() {
		return makeMainTable()
				+ makeRemapperTable()
				+ makeDicomImportTable()
				+ makeDicomExportTable()
				+ makeHttpImportTable()
				+ makeHttpExportTable()
				+ makeDatabaseExportTable();
	}

	private String makeMainTable() {
		String table = "<table border=\"1\">\n";
		table += makeRow2("Autostart","autostart",TrialConfig.getAutostart(),yesNo);
		table += makeRow2("Log","log",TrialConfig.getLog(),yesNo);
		table += makeRow2("Allow overwrite","overwrite",TrialConfig.getOverwrite(),yesNo);
		table += "<tr><td colspan=\"2\">";
		table += "<input type=\"button\" value=\"Update the Anonymizer\" "
				+ "onclick=\"window.open('anconfig','_blank');\"/>\n";
		table += "</td></tr>";
		table += "</table>\n";
		return table;
	}

	private String makeRemapperTable() {
		String table = "<br><h3>Central Remapper Parameters</h3>\n";
		table += "<table border=\"1\">\n";
		table += makeRow2("External key file","keyfile",TrialConfig.getKeyFile(),150);
		table += makeRow2("UID root","uidroot",TrialConfig.getUIDRoot(),150);
		table += makeRow2("Base date","basedate",TrialConfig.getBaseDate(),150);
		table += makeRow2("Patient ID Prefix","ptprefix",TrialConfig.getPtIdPrefix(),150);
		table += makeRow2("Patient ID First Value","ptfirst",""+TrialConfig.getFirstPtId(),150);
		table += makeRow2("Patient ID Width","ptwidth",""+TrialConfig.getPtIdWidth(),150);
		table += makeRow2("Patient ID Suffix","ptsuffix",TrialConfig.getPtIdSuffix(),150);
		table += "</table>\n";
		return table;
	}

	private String makeDicomImportTable() {
		String table = "<br><h3>DICOM Import Service</h3>\n";
		table += "<table border=\"1\">\n";
		table += makeRow2("Store AE Title","dmae",TrialConfig.getDicomStoreAETitle(),150);
		table += makeRow2("Store Port","dmport",TrialConfig.getDicomStorePort(),150);
		table += makeRow2("Anonymizer Enabled","dmanon",TrialConfig.getDicomImportAnonymize(),yesNo);
		table += "</table>\n";
		return table;
	}

	private String makeDicomExportTable() {
		String table = "<br><h3>DICOM Export Service</h3>\n";
		table += "<table border=\"1\">\n";
		table += makeRow2("Mode","demode",TrialConfig.getDicomExportMode(),autoQC);
		table += "</table>\n";
		table += "<br/>";
		table += "<table border=\"1\">\n";
		table += "<thead><tr><th>AE Title</th>"
					 + "<th>IP Address : Port<br>[192.168.0.99:2222]</th>"
					 + "<th>Unique Directory Name<br>[one word]</th></tr></thead>\n";
		String[] aets = TrialConfig.getDicomExportAETitles();
		String[] ips = TrialConfig.getDicomExportIPAddresses();
		String[] dirs = TrialConfig.getDicomExportDirectories();
		for (int i=0; i<aets.length; i++) {
			table += makeRow3h("dxaet["+i+"]",aets[i],150,
												 "dxip["+i+"]",ips[i],300,
												 "dxdir["+i+"]",fixDir(dirs[i]),200);
		}
		table += makeRow3h("dxaet["+aets.length+"]","",150,
											 "dxip["+aets.length+"]","",300,
											 "dxdir["+aets.length+"]","",200);
		table += "</table>\n";
		return table;
	}

	private String makeDatabaseExportTable() {
		String table = "<br><h3>Database Export Service</h3>\n";
		table += "<table border=\"1\">\n";
		table += makeRow2("Mode","dbemode",TrialConfig.getDatabaseExportMode(),autoQCdisabled);
		table += makeRow2("Anonymizer enabled","dbeanon",TrialConfig.getDatabaseExportAnonymize(),yesNo);
		table += makeRow2("Sleep interval (ms)","dbint",""+TrialConfig.getDatabaseExportInterval(),300);
		table += makeRow2("Database class name","dbclass",TrialConfig.getDatabaseClassName(),300);
		table += "</table>\n";
		return table;
	}

	private String makeHttpImportTable() {
		String table = "<br><h3>HTTP Import Service</h3>\n";
		table += "<table border=\"1\">\n";
		table += makeRow2("Preprocessor enabled","ppenb",TrialConfig.getPreprocessorEnabled(),yesNo);
		table += makeRow2("Preprocessor class name","ppclass",TrialConfig.getPreprocessorClassName(),300);
		table += makeRow2("Anonymizer enabled","hmanon",TrialConfig.getHttpImportAnonymize(),yesNo);
		table += "</table>\n";
		table += "<table border=\"1\">\n";
		table += "<thead><tr><th>IP Address<br>[192.168.0.99] or *</th></tr></thead>\n";
		String[] ips = TrialConfig.getHttpImportIPAddresses();
		for (int i=0; i<ips.length; i++) {
			table += makeRow1h("hmip["+i+"]",ips[i]);
		}
		table += makeRow1h("hmip["+ips.length+"]","");
		table += "</table>\n";
		return table;
	}

	private String makeHttpExportTable() {
		String table = "<br><h3>HTTP Export Service</h3>\n";
		table += "<table border=\"1\">\n";
		table += "<thead><tr><th>URL<br>[http://university.edu:8080/trial/import/doc]"
					 + "<br>[https://university.edu:8443/trial/import/doc]</th>"
					 + "<th>Unique Directory Name<br>[one word]</th></tr></thead>\n";
		String[] urls = TrialConfig.getHttpExportURLs();
		String[] dirs = TrialConfig.getHttpExportDirectories();
		for (int i=0; i<urls.length; i++) {
			table += makeRow2h("hxurl["+i+"]",urls[i],300,
												 "hxdir["+i+"]",fixDir(dirs[i]),200);
		}
		table += makeRow2h("hxurl["+urls.length+"]","",300,
											 "hxdir["+urls.length+"]","",200);
		table += "</table>\n";
		return table;
	}

	private String makeRow1h(String key, String value) {
		return "<tr><td><input name=\""+key+"\" value=\""+value+"\"/></td></tr>\n";
	}

	private String makeRow2h(
				String key1, String value1,
				String key2, String value2) {
		return "<tr><td><input name=\""+key1+"\" value=\""+value1+"\"/></td>"
				+ "<td><input name=\""+key2+"\" value=\""+value2+"\"/></td></tr>\n";
	}

	private String makeRow2h(
				String key1, String value1, int w1,
				String key2, String value2, int w2) {
		return "<tr><td><input style=\"width:"+w1+"\" name=\""+key1+"\" value=\""+value1+"\"/></td>"
				+ "<td><input style=\"width:"+w2+"\" name=\""+key2+"\" value=\""+value2+"\"/></td></tr>\n";
	}

	private String makeRow3h(
				String key1, String value1, int w1,
				String key2, String value2, int w2,
				String key3, String value3, int w3) {
		return "<tr><td><input style=\"width:"+w1+"\" name=\""+key1+"\" value=\""+value1+"\"/></td>"
				+ "<td><input style=\"width:"+w2+"\" name=\""+key2+"\" value=\""+value2+"\"/></td>"
				+ "<td><input style=\"width:"+w3+"\" name=\""+key3+"\" value=\""+value3+"\"/></td></tr>\n";
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

	private String fixDir(String dir) {
		return dir.substring(dir.lastIndexOf(File.separator)+1);
	}

	private String responseHead() {
		String head =
				"<html>\n"
			+	" <head>\n"
			+	"  <title>DICOM Service Configurator</title>\n"
			+	"   <style>\n"
			+	"    body {background-color:#e0e0e0}\n"
			+	"    input {width:300}\n"
			+	"    th {text-align:center; padding 5}\n"
			+	"    td {text-align:left; padding:1 5 1 5}\n"
			+	"   </style>\n"
			+	" </head>\n"
			+	" <body>\n"
			+	"  <center>\n"
			+	"   <h1>DICOM Service Configurator</h1>\n"
			+	"   <form method=\"post\" accept-charset=\"UTF-8\" action=\"\">\n";
		return head;
	}

	private String responseTail() {
		String tail =
				"    <br/>\n"
			+	"    <input type=\"submit\" value=\"Update trial.xml\">\n"
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
			+	"  <title>DICOM Service Configurator</title>\n"
			+	"   <style>\n"
			+	"    body {background-color:#e0e0e0}\n"
			+	"   </style>\n"
			+	" </head>\n"
			+	" <body>\n"
			+	"  <center>\n"
			+	"   <h1>DICOM Service Configurator</h1>\n"
			+	"   <p>The submitted data caused the trial xml object not to parse.</p>\n"
			+	"   <p>The trial.xml file was not updated.</p>\n"
			+	"   <p>The parser's error message was:</p>\n"
			+	"   <p>" + message + "</p>\n";
		return page;
	}

}
