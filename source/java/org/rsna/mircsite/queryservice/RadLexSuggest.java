/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.queryservice;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.rsna.mircsite.util.RadLexIndex;
import org.rsna.mircsite.util.ServletUtil;
import org.rsna.mircsite.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * The RadLex term suggester servlet.
 * This servlet provides AJAX access to the RadLex index. It responds
 * to a GET by finding those terms in the index whose first word starts
 * with a key query parameter.
 */
public class RadLexSuggest extends HttpServlet {

	/**
	 * The servlet method that responds to an HTTP GET.
	 * This method returns an XML object which contains RadLex terms
	 * whose first word starts with the supplied key query parameter.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doGet(
			HttpServletRequest req,
			HttpServletResponse res)
				throws ServletException {

		try { req.setCharacterEncoding("UTF-8"); }
		catch (Exception ignore) { }

		try {
			res.setContentType("text/xml; charset=\"UTF-8\"");
			PrintWriter out = res.getWriter();

			String key = req.getParameter("key");
			Element result = RadLexIndex.getSuggestedTerms(key);
			if (result == null) out.print("<RadLexTerms/>");
			else out.print(XmlUtil.toString(result));

			out.close();
		}
		catch (Exception error) { ServletUtil.sendError(res, 404); }
	}

}











