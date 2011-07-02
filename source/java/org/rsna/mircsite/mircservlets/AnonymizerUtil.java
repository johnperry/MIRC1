/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.mircservlets;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.rsna.mircsite.util.*;

/**
 * The Anonymizer Configurator servlet.
 * <p>
 * This servlet provides a browser-accessible user interface for
 * configuring the anonymizer.properties file.
 * <p>
 * This servlet responds to both HTTP GET and POST.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class AnonymizerUtil extends HttpServlet {

	/** Properties filename */
	public String propertiesFilename = "dicom-anonymizer.properties";

	/**
	 * The servlet method that responds to an HTTP GET.
	 * <p>
	 * This method returns an HTML page containing a form for
	 * changing the contents of the anonymizer.properties file.
	 * <p>
	 * The contents of the form are constructed
	 * from the text of the file, not from a Properties object
	 * because all properties must be configurable, even those
	 * that are commented out in the properties file.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doGet(
			HttpServletRequest req,
			HttpServletResponse res)
				throws ServletException {

		//Reads the text of the anonymizer.properties file and
		//return a page with a form for configuring it.
		ServletUtil.sendPageNoCache(res,getPage());
	}

	/**
	 * The servlet method that responds to an HTTP POST.
	 * <p>
	 * This method interprets the posted parameters as a new configuration
	 * for the anonymizer.properties file and updates the file accordingly.
	 * It then returns an HTML page containing a new form constructed
	 * from the new contents of the file.
	 * <p>
	 * The contents of the form are constructed from the text
	 * of the file, not from a Properties object because
	 * all properties must be configurable, even those
	 * that are commented out in the properties file.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doPost(
			HttpServletRequest req,
			HttpServletResponse res)
				throws ServletException {

		//Make a header for the properties file.
		StringBuffer props = new StringBuffer();
		props.append("# Anonymizer Properties\n#\n");

		//Make all the properties
		boolean selected;
		String name;
		String value;

		//First do the params
		int row = 0;
		while ((name=(String)req.getParameter("np"+row)) != null) {
			value = (String)req.getParameter("vp"+row);
			props.append("param." + name + "=" + value + "\n");
			row++;
		}

		//Now do the DICOM elements
		row = 0;
		while ((name=(String)req.getParameter("n"+row)) != null) {
			selected = (req.getParameter("cb"+row) != null);
			value = (String)req.getParameter("v"+row);
			props.append((selected ? "set." : "#set.") + name + "=" + value + "\n");
			row++;
		}

		//Now do the keep-groups
		row = 0;
		while ((name=(String)req.getParameter("nkeep"+row)) != null) {
			selected = (req.getParameter("cbkeep"+row) != null);
			value = (String)req.getParameter("vkeep"+row);
			props.append((selected ? "keep." : "#keep.") + name + "=" + value + "\n");
			row++;
		}

		//Now handle the global removes
		selected = (req.getParameter("cbprivategroups") != null);
		props.append((selected ? "" : "#") + "remove.privategroups=\n");
		selected = (req.getParameter("cbunspecifiedelements") != null);
		props.append((selected ? "" : "#") + "remove.unspecifiedelements=\n");
		selected = (req.getParameter("cboverlays") != null);
		props.append((selected ? "" : "#") + "remove.overlays=\n");

		//Update the file
		File propsFile = getPropertiesFile();
		FileUtil.setFileText(propsFile,props.toString());

		//Make a new page from the new data and send it out
		ServletUtil.sendPageNoCache(res,getPage());
	}

	/**
	 * Get a file pointing to the anonymizer properties file.
	 * This method points to a file called dicom-anonymizer.properties
	 * in the root of the servlet. If you want to point somewhere
	 * else, you can override this method or the propertiesFilename
	 * field.
	 * @return the anonymizer.properties file.
	 */
	public File getPropertiesFile() {
		File root = new File(getServletContext().getRealPath("/"));
		return new File(root,propertiesFilename);
	}

	//Create an HTML page containing the form for configuring the file.
	private String getPage() {
		return responseHead() + makeTable() + responseTail();
	}

	private String makeTable() {
		File propsFile = getPropertiesFile();
		String props = FileUtil.getFileText(propsFile);
		BufferedReader br = new BufferedReader(new StringReader(props));

		StringBuffer table = new StringBuffer("<table border=\"1\" width=\"85%\">\n");
		table.append("<thead><tr><th class=\"lt\">Select</th>" +
					 "<th class=\"ct\">DICOM Element</th>" +
					 "<th class=\"rt\">Replacement</th></tr></thead>\n");

		StringBuffer extraRows = new StringBuffer("<tr><td colspan=\"3\">&nbsp;</td></tr>\n");
		String prop;
		int setRow = 0;
		int paramRow = 0;
		int keepRow = 0;
		try {
			while ((prop = br.readLine()) != null) {
				prop = prop.trim();
				if (prop.startsWith("param.")) {
					table.append(makeParamRow(paramRow++,prop));
				}
				else if (prop.startsWith("set.") || prop.startsWith("#set.")) {
					table.append(makeSetRow(setRow++,prop));
				}
				else if (prop.startsWith("keep.") || prop.startsWith("#keep.")) {
					extraRows.append(makeKeepRow(keepRow++,prop));
				}
				else if (prop.startsWith("remove.") || prop.startsWith("#remove.")) {
					extraRows.append(makeRemoveRow(prop));
				}
			}
		} catch (Exception ignore) { }

		table.append(extraRows);
		table.append("</table>\n");
		return table.toString();
	}

	private String makeParamRow(int row, String prop) {
		int nameStart = prop.indexOf("param.") + 6;
		int valueStart = prop.indexOf("=");
		if (valueStart == -1) return "";
		String name = prop.substring(nameStart,valueStart).trim();
		String value = prop.substring(valueStart + 1);
		return "<tr><td></td>"
				 + "<td>"+label("np"+row,name)+"</td>"
				 + "<td>"+textInput("vp"+row,value)+"</td>"
				 + "</tr>\n";
	}

	private String makeSetRow(int row, String prop) {
		int nameStart = prop.indexOf("set.") + 4;
		int valueStart = prop.indexOf("=");
		if (valueStart == -1) return "";
		String name = prop.substring(nameStart,valueStart).trim();
		String value = prop.substring(valueStart + 1);
		boolean cbSelected = !prop.startsWith("#");
		return "<tr><td>"+checkBox(row,cbSelected)+"</td>"
				 + "<td>"+label("n"+row,name)+"</td>"
				 + "<td>"+textInput("v"+row,value)+"</td>"
				 + "</tr>\n";
	}

	private String makeKeepRow(int row, String prop) {
		int nameStart = prop.indexOf("keep.") + 5;
		int valueStart = prop.indexOf("=");
		if (valueStart == -1) return "";
		String name = prop.substring(nameStart,valueStart).trim();
		String value = prop.substring(valueStart + 1).trim();
		if (value.equals("")) {
			if (name.equals("group18")) value = "Keep group 18 [recommended]";
			else if (name.equals("group20")) value = "Keep group 20 [recommended]";
			else if (name.equals("group28")) value = "Keep group 28 [recommended]";
		}
		boolean cbSelected = !prop.startsWith("#");
		return "<tr><td>"+checkBox("keep"+row,cbSelected)+"</td>"
				 + "<td colspan=\"2\">"+value+hidden("nkeep"+row,name)+hidden("vkeep"+row,value)+"</td>"
				 + "</tr>\n";
	}

	private String makeRemoveRow(String prop) {
		int nameStart = prop.indexOf("remove.") + 7;
		int valueStart = prop.indexOf("=");
		if (valueStart == -1) return "";
		String label;
		String name = prop.substring(nameStart,valueStart).trim();
		if (name.equals("privategroups")) label = "Remove private groups";
		else if (name.equals("unspecifiedelements")) label = "Remove unspecified elements";
		else if (name.equals("overlays")) label = "Remove overlays (groups 60xx) [not recommended]";
		else return "";
		boolean cbSelected = !prop.startsWith("#");
		return "<tr><td>"+checkBox(name,cbSelected)+"</td>"
						 + "<td colspan=\"2\">"+label+"</td>"
						 + "</tr>\n";
	}

	private String checkBox(int row, boolean cbSelected) {
		return "<input type=\"checkbox\" name=\"cb"+row + "\" value=\"yes\""
						+ (cbSelected ? " checked" : "") + "></input>";
	}

	private String checkBox(String name, boolean cbSelected) {
		return "<input type=\"checkbox\" name=\"cb"+name + "\" value=\"yes\""
						+ (cbSelected ? " checked" : "") + "></input>";
	}

	private String label(String name, String text) {
		String spacedText;
		int k = text.indexOf("]");
		if (k != -1) spacedText = text.substring(0,k+1) + " " + text.substring(k+1);
		else spacedText = text;
		return spacedText + hidden(name,text);
	}

	private String hidden(String name, String text) {
		return "<input type=\"hidden\" name=\"" + name + "\" value=\"" + text + "\"/>";
	}

	private String textInput(String name, String value) {
		return "<input name=\"" + name + "\" value='" + value + "'/>";
	}

	private String responseHead() {
		String head =
				"<html>\n"
			+	" <head>\n"
			+	"  <title>DICOM Anonymizer Configurator</title>\n"
			+	"   <style>\n"
			+	"    body {background-color:#e0e0e0}\n"
			+	"    input {width:100%}\n"
			+	"    th {text-align:center; padding 5}\n"
			+	"    td {text-align:left; padding:1 5 1 5}\n"
			+	"    .lt {width:10%}\n"
			+	"    .ct {width:50%}\n"
			+	"    .rt {width:40%}\n"
			+	"    .button {width:250}\n"
			+	"   </style>\n"
			+	" </head>\n"
			+	" <body>\n"
			+	"  <center>\n"
			+	"   <h1>DICOM Anonymizer Configurator</h1>\n"
			+	"   <form method=\"post\" accept-charset=\"UTF-8\" action=\"\">\n";
		return head;
	}

	private String responseTail() {
		String tail =
				"    <br/>\n"
			+	"    <input class=\"button\" type=\"submit\" value=\"Update anonymizer.properties\">\n"
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
			+	"  <title>DICOM Anonymizer Configurator</title>\n"
			+	"   <style>\n"
			+ "    body {background-color:#e0e0e0}\n"
			+	"   </style>\n"
			+	" </head>\n"
			+	" <body>\n"
			+	"  <center>\n"
			+	"   <h1>DICOM Anonymizer Configurator</h1>\n"
			+	"   <p>An error occurred while updating the anonymizer.properties file.</p>\n";
		return page;
	}

}











