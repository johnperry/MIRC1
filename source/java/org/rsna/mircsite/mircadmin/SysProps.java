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
 * The SysProps servlet.
 * <p>
 * This servlet presents a dynamically generated page
 * showing all the System properties.
 * <p>
 * This servlet responds to both HTTP GET and POST.
 * <p>
 * This servlet is primarily for confirming the configurations
 * in the field.
 */
public class SysProps extends HttpServlet {

	private static final Runtime runtime = Runtime.getRuntime();

	/**
	 * The servlet method that responds to an HTTP GET.
	 * <p>
	 * This method returns an HTML page displaying the System properties.
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

	//Create an HTML page containing the form.
	private String getPage() {
		String page =
				"<html>\n"
			+	" <head>\n"
			+	"  <title>System Properties</title>\n"
			+	"   <style>\n"
			+	"    body {background-color:#c6d8f9; margin:0; padding:0;}\n"
			+	"    h1 {padding-top:10;}\n"
			+	"    td {text-align:left; padding:5; padding-left:10; padding-right:10;}\n"
			+	"   </style>\n"
			+	" </head>\n"
			+	" <body>\n"
			+	   HtmlUtil.getCloseBox()
			+	"  <center>\n"
			+	    displayProperties()
			+	"  </center>\n"
			+	" </body>\n"
			+	"</html>\n";
		return page;
	}

	//Return a String containing the HTML text of a table
	//displaying all the Java System properties.
	private String displayProperties() {
		String sep = System.getProperty("path.separator",";");
		String v;
		String s =
			 	"<h1>System Properties</h1>\n"
			+	"<table border=\"1\">\n";
		Properties p = System.getProperties();
		String[] n = new String[p.size()];
		Enumeration e = p.propertyNames();
		for (int i=0; i< n.length; i++) n[i] = (String)e.nextElement();
		Arrays.sort(n);
		for (int i=0; i<n.length; i++) {
			v = p.getProperty(n[i]);

			//Make path and dirs properties more readable by
			//putting each element on a separate line.
			if (n[i].endsWith(".path") ||
				n[i].endsWith(".dirs"))
					v = v.replace(sep,sep+"<br/>");

			//Make definition, access, and loader properties more
			//readable by putting each element on a separate line.
			if (n[i].endsWith(".definition") ||
				n[i].endsWith(".access") ||
				n[i].endsWith(".loader"))
					v = v.replace(",",",<br>");

			s += "<tr><td>" + n[i] + "</td><td>" + v + "</td></tr>\n";
		}
		s += "</table>\n";
		return s;
	}

}
