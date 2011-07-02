package org.rsna.mircsite.queryservice.news;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The base news servlet, with some global functionality useful to all news servlets.
 * @author RBoden
 *
 */
public abstract class BaseServlet extends HttpServlet {

	private static final long serialVersionUID = 1231231244512312l;


	public void doPost(HttpServletRequest req, HttpServletResponse res)
	throws ServletException {
		doIt(req, res);
	}

	public void doGet(HttpServletRequest req, HttpServletResponse res)
	throws ServletException {
		doIt(req, res);
	}

	/**
	 *	This will be the main method of the servlet.
	 */
	protected abstract void doIt(HttpServletRequest req, HttpServletResponse res)
	throws ServletException;

	/**
	 * Gets the full path to the news rss file on the filesystem.
	 */
	protected String getRssXmlFilePath() {
		return getServletContext().getRealPath(NewsManager.RSS_FILENAME);
	}

	/**
	 * Gets the URL to the query service.
	 */
	protected String getFullQueryServiceUrl(HttpServletRequest req) {
		return "http://"+req.getServerName()+":"+req.getServerPort()+"/mirc/query";
	}


}
