/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.queryservice;

import java.io.*;
import java.net.*;
import javax.servlet.*;
import javax.servlet.http.*;

/**
 * The MIRC Query Service XML Server servlet.
 * <br>
 * This is a dummy XML server that refuses to serve anything.
 * Its purpose is to hide all XML files in the query service.
 * This servlet returns a Not Found error (404) for any HTTP GET.
 */
public class QueryXMLServer extends HttpServlet {

	/**
	 * The servlet method that responds to an HTTP GET.
	 * It returns a Not Found error (404) for every HTTP GET.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doGet(
			HttpServletRequest req,
			HttpServletResponse res
			) throws IOException, ServletException {

		res.sendError(res.SC_NOT_FOUND);
	}

}