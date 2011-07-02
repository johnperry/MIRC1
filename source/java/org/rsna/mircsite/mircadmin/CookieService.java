/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.mircadmin;

import java.io.*;
import java.net.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.rsna.mircsite.util.ServletUtil;

/**
 * The Cookie Service servlet.
 * <p>
 * This servlet lists all the cookies it receives in a request.
 * It can be useful during development when working on login and
 * logout code. It turns out, however, that the real problem with
 * logout is in the browser. This servlet is left in the system
 * in case somebody figures out how to get around the browser
 * problem, in which case, it may be helpful in testing.
 * <p>
 * No system functionality would be lost if this servlet were
 * removed from the system.
 * <p>
 * This servlet responds to an HTTP GET.
 */
public class CookieService extends HttpServlet {

	/**
	 * The servlet method that responds to an HTTP GET.
	 * <p>
	 * This method returns an HTML page identifying the user and
	 * containing a table of all the cookies that were received in
	 * the request, along with their parameters.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doGet(
				HttpServletRequest req,
				HttpServletResponse res
				) throws ServletException {

		//Get the user's name and information
		String username = req.getRemoteUser();
		if (username == null) username = "[not authenticated]";

		//Get the cookies
		Cookie[] cookies = req.getCookies();

		//Get ready to return the cookie list
		String message = "<html><head><title>Cookie List</title></head><body>"
			+ "<h1>Cookie List for " + username + "</h1>";

		if ((cookies != null) && (cookies.length > 0)) {
			//Make a table
			message += "<table border=\"1\">"
				+ "<tr><td>Name</td><td>Value</td><td>MaxAge</td>"
				+ "<td>Path</td><td>Secure</td><td>Domain</td></tr>";

			//List the cookies
			Cookie c;
			for (int i=0; i<cookies.length; i++) {
				c = cookies[i];
				message += "<tr>"
					+ "<td>" + c.getName() + "</td>"
					+ "<td>" + c.getValue() + "</td>"
					+ "<td>" + c.getMaxAge() + "</td>"
					+ "<td>" + c.getPath() + "</td>"
					+ "<td>" + c.getSecure() + "</td>"
					+ "<td>" + c.getDomain() + "</td>"
					+ "</tr>";
			}
			message += "</table>";
		}
		else message += "<p>No cookies were found.</p>";

		message += "</body></html>";

		//Send the result
		ServletUtil.sendPageNoCache(res,message);
	}

}
