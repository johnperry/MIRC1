package org.rsna.mircsite.queryservice.news;

import java.util.Date;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rsna.mircsite.util.XmlStringUtil;

/**
 * Servlet that adds a news item.
 * Required parameters
 * <ul>
 * <li>description: a brief description of the case</li>
 * <li>link: a web link to the case (http://...)</li>
 * <li>title: a title of the case</li>
 * <li>image: a thumbnail image (if available) associated with the case</li>
 * <ul>
 * @author RBoden
 *
 */
public class AddItemServlet extends BaseServlet{

	private static final long serialVersionUID = 1231231244512312l;


	protected void doIt(HttpServletRequest req, HttpServletResponse res)
	throws ServletException {
		try { req.setCharacterEncoding("UTF-8"); }
		catch (Exception ignore) { }
		Item myItem = new Item();
		String xmlPath = getRssXmlFilePath();
		String serverPath = getFullQueryServiceUrl(req);
		myItem.setDescription(XmlStringUtil.escapeChars(req.getParameter("description")));
		myItem.setLink(req.getParameter("link"));
		myItem.setPubDate(new Date());
		myItem.setTitle(XmlStringUtil.escapeChars(req.getParameter("title")));
		myItem.setImageLink(req.getParameter("image"));

		try {
			new NewsManager().addItem(myItem, xmlPath, serverPath);
			RequestDispatcher dispatcher = getServletContext().getRequestDispatcher("/news/ItemAddedSuccessfully.jsp");
			dispatcher.forward(req,res);
		} catch( Exception ex ) {
			throw new ServletException(ex);
		}
	}

}
