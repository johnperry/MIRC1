/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License. (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.queryservice;

import java.io.File;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.rsna.mircsite.util.MircConfig;
import org.rsna.mircsite.util.ServletUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The MIRC Find Service servlet.
 * The Find Service responds to AJAX requests for storage service
 * IDs which correspond to a specified context..
 * This servlet responds to both HTTP GET and POST.
 */
public class FindService extends HttpServlet {

	private static final long serialVersionUID = 1123123213l;

	static final Logger logger = Logger.getLogger(FindService.class);

	/**
	 * The servlet method that responds to an HTTP GET.
	 * The context is supplied in the context query string.
	 * The context is in the form "/storage".
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 * @throws ServletException if the servlet cannot handle the request.
	 */
	public void doGet(HttpServletRequest req, HttpServletResponse res)
											throws ServletException {

		try {
			String context = req.getParameter("context") + "/";
			File mircXSLFile = new File(getServletContext().getRealPath("/mirc.xsl"));
			MircConfig mircConfig = MircConfig.getInstance(mircXSLFile);
			Document mircXML = mircConfig.getMircXML();

			Element root = mircXML.getDocumentElement();
			Node child = root.getFirstChild();
			int id = 0;
			while (child != null) {
				if (child.getNodeName().equals("server")) {
					id++;
					String adrs = ((Element)child).getAttribute("address");
					int k = adrs.indexOf("://");
					k = adrs.indexOf("/", k+3);
					if (adrs.substring(k).startsWith(context)) {
						String responseXML = "<server id=\""+id+"\" address=\""+adrs+"\"/>";
						ServletUtil.sendText(res, "text/xml", responseXML, false);
						return;
					}
				}
				child = child.getNextSibling();
			}
		}
		catch (Exception uhoh) { }
		ServletUtil.sendText(res, "text/xml", "<notFound/>" , false);
	}
}
