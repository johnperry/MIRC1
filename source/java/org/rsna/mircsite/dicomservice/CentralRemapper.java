/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.dicomservice;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.log4j.Logger;
import org.rsna.mircsite.anonymizer.IdTable;
import org.rsna.mircsite.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The MIRC remapper servlet.
 * This servlet supports requests for remapping PHI. It receives
 * a POST of Content-Type text/xml and returns the remapped values
 * in the text/xml response text.
 */
public class CentralRemapper extends HttpServlet {

	static final Logger logger = Logger.getLogger(CentralRemapper.class);

	/**
	 * The servlet method that responds to an HTTP POST.
	 * This method reads the query text/xml, uses it as parameters
	 * to call the identified methods in the IdTable, and returns
	 * the results in a text/xml stream.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doPost(
				HttpServletRequest req,
				HttpServletResponse res
				) throws IOException, ServletException {

		try { req.setCharacterEncoding("UTF-8"); }
		catch (Exception ignore) { }
		res.setContentType("text/xml; charset=\"UTF-8\"");
		PrintWriter out = res.getWriter();

		//Check that this is a post of the correct content type.
		//Note that the test of the content type is not done
		//with .equals("text/xml") because sometimes the
		//content type header also includes the charset.
		String requestContentType = req.getContentType();
		if ((requestContentType == null) ||
				(requestContentType.toLowerCase().indexOf("text/xml") < 0)) {
			//Unknown content type
			res.sendError(res.SC_NOT_FOUND);
			out.close();
		}

		//The content type is correct; get the request
		int n;
		BufferedReader in = req.getReader();
		StringWriter sw = new StringWriter();
		char[] cbuf = new char[1024];
		while ((n = in.read(cbuf,0,1024)) != -1) { sw.write(cbuf,0,n); }
		String requestString = sw.toString();

		//Parse the requestString to get the request XML DOM object,
		//and create a new response XML DOM object.
		Document requestXML;
		Document responseXML;
		try {
			requestXML = XmlUtil.getDocumentFromString(requestString);
			DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			responseXML = db.newDocument();
		}
		catch (Exception e) {
			res.sendError(res.SC_NOT_FOUND);
			out.close();
			return;
		}

		Element responseRoot = responseXML.createElement("response");
		responseXML.appendChild(responseRoot);

		//Now process the request document and populate the response document.
		Element requestRoot = requestXML.getDocumentElement();
		Node child = requestRoot.getFirstChild();
		while (child != null) {
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				String method = child.getNodeName();

				if (method.equals("getOriginalDate"))
					getOffsetDate(responseRoot,(Element)child);

				if (method.equals("getOffsetDate"))
					getOffsetDate(responseRoot,(Element)child);

				else if (method.equals("getAccessionNumber"))
					getAccessionNumber(responseRoot,(Element)child);

				else if (method.equals("getGenericID"))
					getGenericID(responseRoot,(Element)child);

				else if (method.equals("getInteger"))
					getGenericID(responseRoot,(Element)child);

				else if (method.equals("getUID"))
					getUID(responseRoot,(Element)child);

				else if (method.equals("getOriginalUID"))
					getOriginalUID(responseRoot,(Element)child);

				else if (method.equals("getPtID"))
					getPtID(responseRoot,(Element)child);
			}
			child = child.getNextSibling();
		}

		//Return the response.
		out.print(XmlUtil.toString(responseXML));
		out.flush();
		out.close();
		return;
	}

	private void getOriginalDate(Element responseRoot, Element call) {
		Element e = createResponseElement(responseRoot, call);
		String siteid = getParam(call,"siteid");
		String ptid = getParam(call,"ptid");
		String tag = getParam(call,"tag");
		String date = getParam(call,"date");
		String base = TrialConfig.getBaseDate();
		date = IdTable.getOriginalDate(siteid,ptid,tag,date,base);
		setValue(e,date);
	}

	private void getOffsetDate(Element responseRoot, Element call) {
		Element e = createResponseElement(responseRoot, call);
		String siteid = getParam(call,"siteid");
		String ptid = getParam(call,"ptid");
		String tag = getParam(call,"tag");
		String date = getParam(call,"date");
		String base = TrialConfig.getBaseDate();
		date = IdTable.getOffsetDate(siteid,ptid,tag,date,base);
		setValue(e,date);
	}

	private void getAccessionNumber(Element responseRoot, Element call) {
		Element e = createResponseElement(responseRoot, call);
		String tag = getParam(call,"tag");
		String gid = getParam(call,"gid");
		gid = IdTable.getAccessionNumber(tag,gid);
		setValue(e,gid);
	}

	private void getInteger(Element responseRoot, Element call) {
		Element e = createResponseElement(responseRoot, call);
		String gid = IdTable.getInteger();
		setValue(e,gid);
	}

	private void getGenericID(Element responseRoot, Element call) {
		Element e = createResponseElement(responseRoot, call);
		String tag = getParam(call,"tag");
		String gid = getParam(call,"gid");
		gid = IdTable.getGenericID(tag,gid);
		setValue(e,gid);
	}

	private void getUID(Element responseRoot, Element call) {
		Element e = createResponseElement(responseRoot, call);
		String uid = getParam(call,"uid");
		if (uid == null)
			uid = IdTable.getUID(TrialConfig.getUIDRoot());
		else
			uid = IdTable.getUID(TrialConfig.getUIDRoot(),uid);
		setValue(e,uid);
	}

	private void getOriginalUID(Element responseRoot, Element call) {
		Element e = createResponseElement(responseRoot, call);
		String uid = getParam(call,"uid");
		uid = IdTable.getOriginalUID(uid);
		setValue(e,uid);
	}

	private void getPtID(Element responseRoot, Element call) {
		Element e = createResponseElement(responseRoot, call);
		String siteid = getParam(call,"siteid");
		String ptid = getParam(call,"ptid");
		String prefix = TrialConfig.getPtIdPrefix();
		String suffix = TrialConfig.getPtIdSuffix();
		int first = TrialConfig.getFirstPtId();
		int width = TrialConfig.getPtIdWidth();
		ptid = IdTable.getPtID(siteid,ptid,prefix,first,width,suffix);
		setValue(e,ptid);
	}

	private Element createResponseElement(Element responseRoot, Element call) {
		String id = call.getAttribute("id");
		if (id == null) id = "";
		Element e = responseRoot.getOwnerDocument().createElement(call.getNodeName());
		e.setAttribute("id",id);
		responseRoot.appendChild(e);
		return e;
	}

	private String getParam(Element call, String paramName) {
		Node child = call.getFirstChild();
		while (child != null) {
			if ((child.getNodeType() == Node.ELEMENT_NODE) &&
				(child.getNodeName().equals(paramName))) {
				return getTextValue(child);
			}
			child = child.getNextSibling();
		}
		return null;
	}

	private String getTextValue(Node node) {
		StringBuffer value = new StringBuffer();
		Node child = node.getFirstChild();
		while (child != null) {
			if (child.getNodeType() == Node.TEXT_NODE) {
				value.append(child.getNodeValue());
			}
			child = child.getNextSibling();
		}
		return value.toString();
	}

	private void setValue(Element e, String s) {
		e.appendChild(e.getOwnerDocument().createTextNode(s));
	}
}
