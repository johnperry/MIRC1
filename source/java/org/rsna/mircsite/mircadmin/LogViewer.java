/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.mircadmin;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.rsna.mircsite.util.FileUtil;
import org.rsna.mircsite.util.HtmlUtil;
import org.rsna.mircsite.util.ServletUtil;
import org.rsna.mircsite.util.StringUtil;

/**
 * The LogViewer servlet.
 * <p>
 * This servlet provides a browser-accessible user interface for
 * viewing the logs in the Tomcat/logs directory.
 */
public class LogViewer extends HttpServlet {

	/**
	 * The servlet method that responds to an HTTP GET.
	 * <p>
	 * If called with no file path, this method returns an
	 * HTML page listing the files in the Tomcat/logs directory in reverse
	 * chronological order. Each filename is a link to display the file's
	 * contents. If called with a file path, this method returns
	 * the contents of the file in an HTML page.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doGet(
				HttpServletRequest req,
				HttpServletResponse res
				) throws ServletException {

		//Get the logs directory
		File dir = getLogsDirectory();

		//Get the filename, if present
		String filename = req.getPathInfo();
		String context = req.getContextPath();
		String servlet = req.getServletPath();
		if (servlet != null) context += servlet;

		//Get the page
		String page;
		if ((filename == null) || filename.trim().equals("") || filename.trim().equals("/"))
			page = getDirectoryPage(dir,context);
		else
			page = getFilePage(new File(dir,filename.substring(filename.lastIndexOf("/")+1)));

		//Send it out
		ServletUtil.sendPageNoCache(res,page);
	}

	//Find the Tomcat/logs directory

	private File getLogsDirectory() {
		File dir = new File(getServletContext().getRealPath("/"));
		while (!dir.getName().equals("webapps")) dir = dir.getParentFile();
		return new File(dir.getParentFile(),"logs");
	}

	//Make a page listing the files in the directory, sorted by last modified date.
	private String getDirectoryPage(File dir,String contextPath) {
		File[] files = FileUtil.listSortedFiles(dir);

		//Make the page
		StringBuffer page = new StringBuffer(responseHead());
		page.append("<div class=\"logdir\">");
		page.append("<table>");
		for (int i=files.length-1; i>=0; i--) {
			if (files[i].length() != 0) {
				page.append(
					HtmlUtil.tr(
						HtmlUtil.td(
							HtmlUtil.a(
								contextPath+"/"+files[i].getName(),
								"logtext",
								files[i].getName()
							)
						) +
						HtmlUtil.td(
							StringUtil.getDateTime(files[i].lastModified()).replace("T","&nbsp;&nbsp;&nbsp;")
						) +
						HtmlUtil.td(
							"style=\"text-align:right\"",
							StringUtil.insertCommas(Long.toString(files[i].length()))
						)
					)
				);
			}
		}
		page.append("</table>");
		page.append("</div>");
		page.append("<hr>");
		page.append("<iframe name=\"logtext\" id=\"logtext\">-</iframe>");
		page.append(responseTail());
		return page.toString();
	}

	//Make a page displaying the contents of a file.
	private String getFilePage(File file) {
		return "<pre>" + FileUtil.getFileText(file) + "</pre>";
	}

	private String responseHead() {
		String head =
				"<html>\n"
			+	" <head>\n"
			+	"  <title>LogViewer</title>\n"
			+	"   <style>\n"
			+	"    body {background-color:#c6d8f9; margin-top:0; padding-right:0;}\n"
			+	"    h1 {padding-top:10; padding-bottom:5;}\n"
			+	"    iframe {height:100; width:100%}\n"
			+	"    td {text-align:left; padding:5; padding-right:20}\n"
			+	"    .logdir {height:150; background-color:white; overflow:auto}\n"
			+	"   </style>\n"
			+	script()
			+	" </head>\n"
			+	" <body scroll=\"no\">\n"
			+	HtmlUtil.getCloseBox()
			+	"  <br clear=\"both\"/>";
		return head;
	}

	private String responseTail() {
		String tail =
				" </body>\n"
			+	"</html>\n";
		return tail;
	}

	private String script() {
		return
				"<script>\n"
			+	"function adjustHeight() {\n"
			+	"	var logtext = document.getElementById('logtext');\n"
			+	"	var h = getHeight() - logtext.offsetTop;\n"
			+	"	if (h < 50) h = 50;\n"
			+	"	logtext.style.height = h;\n"
			+	"}\n"
			+	"function getHeight() {\n"
			+	"	if (document.all) return document.body.clientHeight;\n"
			+	"	return window.innerHeight - 22;\n"
			+	"}\n"
			+	"onload = adjustHeight;\n"
			+	"onresize = adjustHeight;\n"
			+	"</script>\n";
	}
}











