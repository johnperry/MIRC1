package org.rsna.mircsite.queryservice.news;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Go to the current news item.
 * @author RBoden
 *
 */
public class NewsServlet extends BaseServlet {

	private static final long serialVersionUID = 1231231244512312l;



	protected void doIt(HttpServletRequest req, HttpServletResponse res)
	throws ServletException {
		try {
			Item myItem = new NewsManager().getNewestItem(getRssXmlFilePath());
			if( myItem != null && myItem.getLink() != null ) {
				res.sendRedirect(myItem.getLink());
				return;
			}
			RequestDispatcher dispatcher = getServletContext().getRequestDispatcher("/news/NoNews.jsp");
			dispatcher.forward(req,res);
		} catch( IOException ioe ){
			throw new ServletException(ioe);
		}

	}

}
