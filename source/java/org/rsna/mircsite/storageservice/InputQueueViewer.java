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
import org.rsna.mircsite.util.*;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The viewer for the input queue.
 */
public class InputQueueViewer extends HttpServlet {

	static final Logger logger = Logger.getLogger(InputQueueViewer.class);
	public static HtmlUtil html = new HtmlUtil();

	/**
	 * The servlet method that responds to an HTTP GET.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 * @throws ServletException if the servlet cannot handle the request.
	 */
	public void doGet(
		HttpServletRequest req,
		HttpServletResponse res
		) throws IOException, ServletException {

		//Force the encoding for the input.
		try { req.setCharacterEncoding("UTF-8"); }
		catch (Exception ignore) { }

		String sitename = StorageConfig.getSitename();
		String page = "";
		String param;

		//Figure out what to do and do it
		if ((param=req.getParameter("deletequeueentry")) != null)
			page = deleteQueueEntry(req.getContextPath(),param);

		else if ((param=req.getParameter("acceptqueueentry")) != null)
			page = acceptQueueEntry(req.getContextPath(),param);

		else if ((param=req.getParameter("publishqueueentry")) != null)
			page = publishQueueEntry(req.getContextPath(),param);

		else page = listInputQueue(req.getContextPath(),1);

		//Make the full page and return it.
		page = html.html(getHead(sitename) +
						html.body("scroll=\"no\"",
							HtmlUtil.getCloseBox() +
							heading(sitename+" Input Queue") +
							html.div("id=\"results\" class=\"div\"",page)));
		ServletUtil.sendPageNoCache(res,page);
	}

	//List the input queue, scrolling the display to a specific line number.
	private String listInputQueue(String contextPath, int entryNumber) {
		String linkText;
		String names;
		int k;
		String narrow = "width=\"40\" align=\"center\"";
		String wide = "width=\"80\" align=\"center\"";
		String here = "id=\"here\" " + narrow;
		String deletePath = "?deletequeueentry=";
		String acceptPath = "?acceptqueueentry=";
		String publishPath = "?publishqueueentry=";
		String editPath = contextPath+"/author/update?doc=";
		try {
			String docbase = StorageConfig.getDocbase();
			Document inputQueueXML = InputQueue.getInputQueueXML();
			Element root = inputQueueXML.getDocumentElement();
			NodeList docs = root.getElementsByTagName("doc");
			StringWriter sw = new StringWriter();
			sw.write("<center><table border=\"1\" cellpadding=\"3\" width=\"100%\">\n");
			String docref;
			String publish;
			int line;
			if (docs.getLength() > 0)
			for (int i=0; i<docs.getLength(); i++) {
				docref = XmlUtil.getElementValue(docs.item(i));
				publish = ((Element)(docs.item(i))).getAttribute("publish");
				line = i+1;
				sw.write("<tr>");
				if (line == entryNumber) sw.write(html.td(here,Integer.toString(line)));
				else sw.write(html.td(narrow,Integer.toString(line)));
				sw.write(html.td(wide, buttonCode("Delete",deletePath+docref+"&line="+line,0)));

				if (publish.equals("yes")) {
					sw.write(html.td(wide, buttonCode("Publish",publishPath+docref,0)));
				}
				else {
					sw.write(html.td(wide, buttonCode("Accept",acceptPath+docref,0)));
				}
				sw.write(html.td(wide, altButtonCode("Edit",editPath+docref,0)));

				Element xml = null;
				try {
					File file = new File(getServletContext().getRealPath(docref));
					Document xmlDoc = XmlUtil.getDocument(file);
					xml = xmlDoc.getDocumentElement();
				}
				catch (Exception ex) { }
				if (xml != null) {
					linkText = XmlUtil.getValueViaPath(xml,"MIRCdocument/title");
					names = "";
					Node child = xml.getFirstChild();
					while (child != null) {
						if ((child.getNodeType() == Node.ELEMENT_NODE)
								&& child.getNodeName().equals("author")) {
							names += XmlUtil.getValueViaPath(child,"author/name")+"<br/>";
						}
						child = child.getNextSibling();
					}
				}
				else {
					linkText = docref;
					if ((k=linkText.indexOf("/")) >= 0)
						linkText = linkText.substring(k+1);
					names = "";
				}
				sw.write(html.td(makeAnchorTag(docbase + docref,linkText)));
				sw.write(html.td(names));
				sw.write("</tr>\n");
			}
			sw.write("</table>");
			if (docs.getLength() > 0) {
				sw.write("<p>" + docs.getLength() + " document");
				if (docs.getLength() > 1) sw.write("s");
				sw.write(" in the input queue</p></center>\n");
			}
			else sw.write(html.p("The queue is empty.") + "</center>\n");
			return sw.toString();
		}
		catch (Exception e) { return "Exception: " + e.getMessage(); }
	}

	//Delete an input queue entry and then display the input queue,
	//scrolling the display to the next line.
	private String deleteQueueEntry(String contextPath, String entry) {
		int entryFound;
		if ((entryFound = InputQueue.deleteQueueEntry(entry)) == -1)
			return	html.p("The attempt to remove the " + html.b(entry) + " queue element failed.")
						+ html.p("The directory containing the document submission "
								+ "was left in place.");
		String text = html.p("The " + html.b(entry) + " queue element "
													+	"was successfully removed from the queue.");
		MircIndex.getInstance().removeDocument(entry);
		File file = new File(StorageConfig.basepath + entry);
		File parent = file.getParentFile();
		if ((parent.getName().equals("documents")) || (countXMLDocs(parent) != 1))
			return text
					+ html.p("The directory containing the document submission contained "
					+ "multiple MIRCdocuments and/or directories. The submission "
					+ "directory was left in place.");
		if (AdminService.removeDocument(parent)) return listInputQueue(contextPath, entryFound);
		return text + html.p("The attempt to delete the " + html.b(parent.getName())
													+	" directory failed.");
	}

	//Make a document public (set its authorization/read element to "*")
	//and then remove it from the input queue, listing the input queue again, scrolled
	//to the next line.
	private String publishQueueEntry(String contextPath, String entry) {
		makePublic(entry);
		return acceptQueueEntry(contextPath,entry);
	}

	private String acceptQueueEntry(String contextPath, String entry) {
		int entryFound;
		if (!MircIndex.getInstance().insertDocument(entry))
			return html.p(html.b(entry) + " could not be added to the index.")
					 + html.p("The element was left in the queue.");
		String text = html.p(html.b(entry) + " was successfully added to the index.");
		if ((entryFound = InputQueue.deleteQueueEntry(entry)) != -1)
			return listInputQueue(contextPath, entryFound);
		return text + html.b("The attempt to remove the element from the queue failed.");
	}

	//Make an author service document public.
	private void makePublic(String entry) {
		File docFile = new File(StorageConfig.basepath + entry);
		String docString = FileUtil.getFileText(docFile);
		docString = AuthorService.makePublic(docString);
		FileUtil.setFileText(docFile,docString);
	}

	//Determine whether a directory
	//has a standard storage service configuration - with
	//one MIRCdocument XML file and no subdirectories other
	//than the ones for identified and de-identified datasets.
	private int countXMLDocs(File dir) {
		File[] fileList = dir.listFiles();
		int count = 0;
		for (int i=0; i<fileList.length; i++) {
			if (fileList[i].isDirectory() &&
				!fileList[i].getName().equals("phi") &&
				!fileList[i].getName().equals("no-phi")) return -1;
			if (fileList[i].getName().trim().toLowerCase().endsWith(".xml")) {
				String name = XmlUtil.getDocumentElementName(fileList[i]);
				if (name.equals("MIRCdocument")) count++;
			}
		}
		return count;
	}

	//Make the head element, with the title, styles, and scripts.
	private String getHead(String name) {
		String title = html.title("Input Queue Viewer: " + name);
		String style =
			  "<style type=\"text/css\">\n"
			+		"h2 {margin-top:10; margin-left:10; padding-left:10; padding-top:10;}\n"
			+		"body {margin:0; padding:0; background: #c6d8f9;}\n"
			+		".div {border-width:thin;border-style:inset;\n"
			+			"padding:10px; margin:10px; overflow:auto}\n"
			+	"</style>\n";
		String script =
			 	"<script>\n"
			+		"function AdjustResultsHeight() {\n"
			+			"var dv = document.getElementById('results');\n"
			+			"var h = getHeight() - dv.offsetTop;\n"
			+		 	"if (h < 50) h = 50;\n"
			+			"dv.style.height = h;\n"
			+		"}\n"
			+		"function getHeight() {\n"
			+			"if (document.all) return document.body.clientHeight;\n"
			+			"return window.innerHeight-10;\n"
			+		"}\n"
			+		"window.onresize = AdjustResultsHeight;\n"
			+		"function scrollResults() {\n"
			+			"var here = document.getElementById(\"here\");\n"
			+			"if (here != null) {\n"
			+				"var results = document.getElementById(\"results\");\n"
			+				"results.scrollTop = here.offsetTop - results.clientHeight/4;\n"
			+			"}\n"
			+		"}\n"
			+		"function onLoad() {\n"
			+			"AdjustResultsHeight();\n"
			+			"scrollResults();\n"
			+		"}\n"
			+		"window.onload = onLoad;\n"
			+	"</script>\n";
		return html.head(title + style + script);
	}

	//Miscellaneous functions to assist in the production of the admin page.
	static final int defaultButtonWidth = 94;

	private String buttonCode(String buttonName, String buttonURL) {
		return buttonCode(buttonName, buttonURL, defaultButtonWidth);
	}

	private String altButtonCode(String buttonName, String buttonURL) {
		return altButtonCode(buttonName, buttonURL, defaultButtonWidth);
	}

	private String buttonCode(String buttonName, String buttonURL, int width) {
		String text = "<input type=\"button\" ";
		if (width>0) text += "style=\"width:" + width + "%\" ";
		text += "value=\"" + buttonName + "\" "
		  +	"onclick=\"document.location.replace('" + buttonURL + "');\"/>\n";
		return text;
	}

	private String altButtonCode(String buttonName, String buttonURL, int width) {
		String text = "<input type=\"button\" ";
		if (width>0) text += "style=\"width:" + width + "%\" ";
		text += "value=\"" + buttonName + "\" "
		  +	"onclick=\"window.open('" + buttonURL + "','_blank');\"/>\n";
		return text;
	}

	private String statusRow(String fieldname, int n) {
		String width = "width=\"50%\"";
		return html.tr(html.td(width,html.b(fieldname)) + html.td(width,Integer.toString(n)));
	}

	private String statusRow(String fieldname, String content) {
		String width = "width=\"50%\"";
		return html.tr(html.td(width,html.b(fieldname)) + html.td(width,content));
	}

	private String statusRow4(String a, String aa, String b, String bb ) {
		String width = "width=\"25%\"";
		return html.tr(html.td(width,html.b(a)) + html.td(width,aa) +
						html.td(width,html.b(b)) + html.td(width,bb));
	}

	private String makeAnchorTag(String url, String linkText) {
		return html.a(url,"_blank",linkText);
	}

	private String heading(String title) {
		return html.h2(title);
	}

	private int getInt(String intString) {
		if (intString == null) return -1;
		try { return Integer.parseInt(intString); }
		catch (Exception ex) { return -1; }
	}
}