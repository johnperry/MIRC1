package org.rsna.mircsite.queryservice.news;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * Removes any items that match the link parameter.
 * @author RBoden
 *
 */
public class RemoveItemServlet extends BaseServlet{

	private static final long serialVersionUID = 1231231244512312l;


	protected void doIt(HttpServletRequest req, HttpServletResponse res)
	throws ServletException {
		NewsManager manager = new NewsManager();
		String link = req.getParameter("link");
		if( link == null ) {
			throw new ServletException("Incorrect parameter in request.");
		}
		String xmlPath = getRssXmlFilePath();
		String serverPath = getFullQueryServiceUrl(req);
		try {
			manager.removeItem(link, xmlPath, serverPath);
			RequestDispatcher dispatcher = getServletContext().getRequestDispatcher("/news/ItemRemovedSuccessfully.jsp");
			dispatcher.forward(req,res);

		} catch( IOException ioe ) {
			throw new ServletException(ioe);
		}



	}

}
