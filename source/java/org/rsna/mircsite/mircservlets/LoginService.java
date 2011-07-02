/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.mircservlets;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rsna.mircsite.util.ServletUtil;

/**
 * The MIRC Login Service servlet.
 * <p>
 * The Login Service is a simple servlet that is configured in
 * the storage service's web.xml file to require authentication
 * of a user.
 * <p>
 * This servlet allows a user to authenticate himself with
 * a storage service before accessing documents on it,
 * allowing the XMLServer to match the user against
 * the document's authorizations. If a user is not authenticated
 * when he accesses a document, the XMLServer must treat the
 * user as having only public access.
 * <p>
 * This servlet responds to HTTP GET.
 */
public class LoginService extends HttpServlet {
	
	private static final long serialVersionUID = 12312312231l;

	static final String loginMessage = "You are now logged in.";
	static final String logoutMessage = "You are now logged out.";

	/**
	 * The servlet method that responds to an HTTP GET.
	 * <p>
	 * If called with no query string, it returns a page forcing
	 * the browser to return to the previous page (typically the
	 * Query Service's query page.
	 * <p>
	 * If called with a path query parameter, it sends a redirector
	 * to the path. This approach is used by the XMLServer to force
	 * a user to authenticate before accessing a protected resource.
	 * <p>
	 * If called with extra path info, it attempts to log the user
	 * off the Tomcat instance and then returns a page forcing the
	 * browser to return to the previous page.
	 * <p>
	 * The log off is accomplished by setting the maxage parameter
	 * to zero for any cookie with the name JSESSIONIDSSO. This is
	 * the cookie that is created by the Tomcat Single Sign-on
	 * Authenticator.
	 * <p>
	 * Unfortunately, this is insufficient to accomplish the goal of
	 * allowing the user to log in again when he accesses the
	 * Tomcat instance in the same session because the browser
	 * remembers the user's credentials and returns them without the
	 * user having a chance to change them. Nevertheless, it's all
	 * we can do, so we do it.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doGet(HttpServletRequest req, HttpServletResponse res)
											throws ServletException {
		//Force the encoding for the input.
		try { req.setCharacterEncoding("UTF-8"); }
		catch (Exception ignore) { }

		//See if there is a path
		String path = req.getParameter("path");
		if (path != null) {
			//Send a redirector to the path.
			ServletUtil.sendRedirector(res,path);
			return;
		}

		//Assume it's a login
		String message = loginMessage;

		//See if it's a logout
		if (req.getPathInfo() != null) {
			
			//It's a logout, zero the maxage value for the SSO authenticator cookie
			Cookie[] cookies = req.getCookies();
			if (cookies != null) {
				for (int i=0; i<cookies.length; i++) {
					if (cookies[i].getName().equals("JSESSIONIDSSO")) {
						cookies[i].setValue(null);
						cookies[i].setMaxAge(0);
						res.addCookie(cookies[i]);
					}
				}
			}
			//and say goodbye.
			message = logoutMessage;
		}

		//Send a page returning the user to the page whence he came.
		String page = "<html><head><title>Login</title>\n"
					+ "<script>function goback() {window.history.go(-1);}\n"
					+ "window.onload = goback;</script>\n"
					+ "</head><body>" + message + "</body></html>";
		ServletUtil.sendPageNoCache(res,page);
	}
}
