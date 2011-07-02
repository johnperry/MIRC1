/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;

/**
 * Encapsulates static methods for returning text in servlet responses.
 */
public class ServletUtil {

	/**
	 * Sends a String in the HttpServletResponse with caching turned off.
	 * @param res the HttpServletResponse provided by the servlet container.
	 * @param page the page to send.
	 * @return true if the page was sent; false otherwise.
	 */
	public static boolean sendPageNoCache(HttpServletResponse res, String page) {
		return sendPage(res,page,false);
	}

	/**
	 * Sends a String in the HttpServletResponse with caching allowed.
	 * @param res the HttpServletResponse provided by the servlet container.
	 * @param page the page to send.
	 * @return true if the page was sent; false otherwise.
	 */
	public static boolean sendPage(HttpServletResponse res, String page) {
		return sendPage(res,page,true);
	}

	/**
	 * Sends a String in the HttpServletResponse.
	 * @param res the HttpServletResponse provided by the servlet container.
	 * @param page the page to send.
	 * @param cache true if caching allowed; false if caching not allowed.
	 * @return true if the response was sent; false otherwise.
	 */
	public static boolean sendPage(HttpServletResponse res, String page, boolean cache) {
		return sendText(res, "text/html", page, cache);
	}

	/**
	 * Sends a UTF-8 String in the HttpServletResponse.
	 * @param res the HttpServletResponse provided by the servlet container.
	 * @param contentType the content type (for example, "text/plain").
	 * @param text the text to send.
	 * @param cache true if caching allowed; false if caching not allowed.
	 * @return true if the response was sent; false otherwise.
	 */
	public static boolean sendText(HttpServletResponse res, String contentType, String text, boolean cache) {
		res.setContentType(contentType + "; charset=\"UTF-8\"");
		if (!cache) res.addHeader("Cache-Control","no-cache");
		try {
			PrintWriter out = res.getWriter();
			out.print(text);
			out.flush();
			out.close();
			return true;
		}
		catch (Exception e) { return false; }
	}

	/**
	 * Sends a standard HTTP error in the HttpServletResponse.
	 * @param res the HttpServletResponse provided by the servlet container.
	 * @param error a standard HTTP error integer.
	 * @return true if the error was sent; false otherwise.
	 */
	public static boolean sendError(HttpServletResponse res, int error) {
		try {
			res.sendError(error);
			return true;
		}
		catch (Exception e) { return false; }
	}

	/**
	 * Attempts to get authentication. If the user is authenticated, this
	 * method sends SC_FORBIDDEN. If the user is not authenticated, and the
	 * path is null,this method sends SC_UNAUTHORIZED. If the user is not
	 * authenticated and the path is not null, this method sends a redirector
	 * to the login service, with a path returning the user to the
	 * requested resource after authentication.
	 * @param req the HttpServletRequest provided by the servlet container.
	 * @param res the HttpServletResponse provided by the servlet container.
	 * @param path the path to which to return after authentication.
	 * @return true if successful; false otherwise.
	 */
	public static boolean authenticate(
						HttpServletRequest req,
						HttpServletResponse res,
						String path) {
		try {
			String username = req.getRemoteUser();
			if (username != null) res.sendError(res.SC_FORBIDDEN);
			else {
				if (path != null) sendRedirector(res, req.getContextPath()+"/login?path="+path);
				else {
					res.addHeader("WWW-Authenticate","Basic realm=\"Storage Service\"");
					res.sendError(res.SC_UNAUTHORIZED);
				}
			}
			return true;
		}
		catch (Exception e) { return false; }
	}

	/**
	 * Sends a redirector to a path.
	 * @param res the HttpServletResponse provided by the servlet container.
	 * @param path the path to the new page.
	 */
	public static void sendRedirector(HttpServletResponse res, String path) {
		sendPage(res, HtmlUtil.getRedirector(path), false);
	}

	/**
	 * Sends a binary file in the HttpServletResponse,
	 * with no disposition header.
	 * @param res the HttpServletResponse provided by the servlet container.
	 * @param contentType the content type.
	 * @param file the binary file.
	 * @return true if the file was sent; false otherwise.
	 */
	public static boolean sendBinaryFileContents(
						HttpServletResponse res,
						String contentType,
						File file) {
		return sendBinaryFileContents(res,contentType,file,false);
	}

	/**
	 * Sends a binary file in the HttpServletResponse,
	 * with an optional disposition header.
	 * @param res the HttpServletResponse provided by the servlet container.
	 * @param contentType the content type.
	 * @param file the binary file.
	 * @param addDispositionHeader true if a disposition header is to be added; false otherwise.
	 * @return true if the file was sent; false otherwise.
	 */
	public static boolean sendBinaryFileContents(
						HttpServletResponse res,
						String contentType,
						File file,
						boolean addDispositionHeader) {
		int bytecount = 0;
		try {
			FileInputStream fis = new FileInputStream(file);
			res.setContentType(contentType);
			if (addDispositionHeader) {
				String disposition = "attachment; filename=\"" + file.getName() + "\"";
				res.addHeader("Content-Disposition",disposition);
			}
			res.addHeader("Content-Length",Long.toString(file.length()));
			OutputStream out = res.getOutputStream();
			int len;
			byte[] b = new byte[1024];
			while ((len=fis.read(b)) > 0) {
				out.write(b,0,len);
				bytecount += len;
			}
			out.flush();
			out.close();
			fis.close();
			return true;
		}
		catch (Exception e) { }
		return false;
	}

	/**
	 * Sends a text file in the HttpServletResponse.
	 * @param res the HttpServletResponse provided by the servlet container.
	 * @param contentType the content type.
	 * @param file the text file.
	 * @return true if the file was sent; false otherwise.
	 */
	public static boolean sendTextFileContents(
						HttpServletResponse res,
						String contentType,
						File file) throws IOException {
		try {
			res.setContentType(contentType);
			PrintWriter out = res.getWriter();
			String text = FileUtil.getFileText(file);
			out.print(text);
			out.flush();
			return true;
		}
		catch (Exception e) { res.sendError(res.SC_NO_CONTENT); }
			return false;
	}

}











