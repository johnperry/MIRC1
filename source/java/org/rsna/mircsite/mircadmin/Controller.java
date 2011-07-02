/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.mircadmin;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;
import org.rsna.mircsite.util.HtmlUtil;
import org.rsna.mircsite.util.ServletUtil;
import org.rsna.mircsite.util.StringUtil;

/**
 * The Controller servlet.
 * <p>
 * This servlet provides a browser-accessible user interface for
 * setting the logger levels for the root logger, the org logger,
 * and the org.rsna logger. It also displays the current memory
 * allocation and allows triggering of garbage collection and can
 * separately enable or disable extra logging at garbage collection
 * time.
 * <p>
 * This servlet responds to both HTTP GET and POST.
 * <p>
 * This servlet is primarily for the developer.
 */
public class Controller extends HttpServlet {

	public static boolean logGC = false;

	final static Logger rootLogger = Logger.getRootLogger();
	final static Logger orgLogger = Logger.getLogger("org");
	final static Logger orgRsnaLogger = Logger.getLogger("org.rsna");

	private static final Runtime runtime = Runtime.getRuntime();

	private static long usedMemory() {
		return runtime.totalMemory() - runtime.freeMemory();
	}

	/**
	 * The servlet method that responds to an HTTP GET.
	 * <p>
	 * This method returns an HTML page displaying the current memory
	 * allocation and a form for setting the levels of the various loggers,
	 * and for triggering garbage collection. It also displays a table
	 * containing the values of all the Java System properties.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doGet(
				HttpServletRequest req,
				HttpServletResponse res
				) throws ServletException {

		//Make the page and send it out
		ServletUtil.sendPageNoCache(res,getPage());
	}

	/**
	 * The servlet method that responds to an HTTP POST.
	 * <p>
	 * This method interprets the posted parameters, configures the
	 * loggers accordingly, and, if instructed, runs the aggressive
	 * garbage collector. It then returns an HTML page containing
	 * a new form with the latest values.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doPost(
				HttpServletRequest req,
				HttpServletResponse res
				) throws ServletException {

		//Force the encoding for the input.
		try { req.setCharacterEncoding("UTF-8"); }
		catch (Exception ignore) { }

		//Get the parameters and update
		Enumeration en = req.getParameterNames();
		while (en.hasMoreElements()) {
			String key = ((String)en.nextElement()).trim();
			String value = req.getParameter(key);
			if (key.equals("logGC")) logGC = value.equals("true");
			else if (key.equals("rootLevel")) rootLogger.setLevel(Level.toLevel(value));
			else if (key.equals("orgLevel")) orgLogger.setLevel(Level.toLevel(value));
			else if (key.equals("orgRsnaLevel")) orgRsnaLogger.setLevel(Level.toLevel(value));
			else if (key.equals("GC") && value.equals("yes")) GarbageCollector.collect();
		}

		//Make a new page from the new data and send it out
		ServletUtil.sendPageNoCache(res,getPage());
	}

	//Create an HTML page containing the form.
	private String getPage() {
		return responseHead() + makeTableRows() + responseTail();
	}

	private String makeTableRows() {
		String[] yesNo = new String[] {"yes","no"};
		String[] trueFalse = new String[] {"true","false"};
		String[] log4jlevels = new String[] {"DEBUG","INFO","WARN","ERROR","FATAL"};
		String rows = "";
		rows += makeRow("Current Memory in Use",
						StringUtil.insertCommas(Long.toString(usedMemory())));
		rows += makeRow("Force Garbage Collection Now","GC","no",yesNo);
		rows += makeRow("Garbage Collection Logging Enabled","logGC",
						Boolean.toString(logGC),trueFalse);
		rows += makeRow("Set Log4J Root Logger Level","rootLevel",
						rootLogger.getEffectiveLevel().toString(),log4jlevels);
		rows += makeRow("Set Log4J org Logger Level","orgLevel",
						orgLogger.getEffectiveLevel().toString(),log4jlevels);
		rows += makeRow("Set Log4J org.rsna Logger Level","orgRsnaLevel",
						orgRsnaLogger.getEffectiveLevel().toString(),log4jlevels);
		return rows;
	}

	private String makeRow(String label, String value) {
		return "<tr><td><b>" + label + "</b></td><td>" + value + "</td></tr>\n";
	}

	private String makeRow(String label, String key, String value) {
		return "<tr><td><b>" + label + "</b></td><td>"
						+ "<input name=\""+key+"\" value=\""+value+"\"/>"
						+ "</td></tr>\n";
	}

	private String makeRow(String label, String key, String value, String[] values) {
		String row = "<tr><td><b>" + label + "</b></td><td>"
     				+ "<select name=\"" + key + "\">";
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
			+	"  <title>Controller</title>\n"
			+	"   <style>\n"
			+	"    body {background-color:#c6d8f9; margin:0; padding:0;}\n"
			+	"    h1 {padding-top:10;}\n"
			+	"    td {text-align:left; padding:5; width:50%}\n"
			+	"   </style>\n"
			+	" </head>\n"
			+	" <body>\n"
			+	HtmlUtil.getCloseBox()
			+	"  <center>\n"
			+	"   <h1>Controller</h1>\n"
			+	"   <form method=\"post\" accept-charset=\"UTF-8\" action=\"\">\n"
			+	"    <table border=\"1\">\n";
		return head;
	}

	private String responseTail() {
		String tail =
				"    </table>\n"
			+	"    <br/>\n"
			+	"    <input type=\"submit\" value=\"Update Parameters\">\n"
			+	"   </form>\n"
			+	"  </center>\n"
			+	" </body>\n"
			+	"</html>\n";
		return tail;
	}

}
