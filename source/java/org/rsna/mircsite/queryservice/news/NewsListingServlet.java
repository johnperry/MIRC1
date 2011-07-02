package org.rsna.mircsite.queryservice.news;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Returns a listing of the news items configurably based on parameters.
 * <br/>
 * <ul>
 * <li>numberOfLinesToInclude: pass in the number of news items you want to go back and list
 * (up to a maximum of 10)</li>
 * <li>includeDescription: true or false, this tells the servlet whether or not you want the
 * description included</li>
 * <li>includeImage: true or false, this tells the servlet whether or not you want to include
 * the base channel image</li>
 * </ul>
 * @author RBoden
 *
 */
public class NewsListingServlet extends BaseServlet {

	private static final long serialVersionUID = 1231231244512312l;

	protected void doIt(HttpServletRequest req, HttpServletResponse res)
			throws ServletException {
		int numberOfLinesToInclude = 1;
		try {
			numberOfLinesToInclude = Integer.parseInt(req.getParameter("numberOfLinesToInclude"));
		} catch( NumberFormatException nfe) {
			//oh well, we'll just use one
		}
		if( numberOfLinesToInclude <= 0 ) {
			numberOfLinesToInclude = 1;
		}
		boolean includeDescription = Boolean.parseBoolean(req.getParameter("includeDescription"));
		boolean includeImage = Boolean.parseBoolean(req.getParameter("includeImage"));
		PrintWriter writer = null;
		try {
			ItemCollection collection = new NewsManager().getAllItems(getRssXmlFilePath());
			Iterator<Item> iter = collection.getItemCollection().iterator();
			res.setContentType("text/html; charset=\"UTF-8\"");
			writer =  res.getWriter();
			if( includeImage && collection.getImageLink() != null && collection.getImageLink().trim().length() > 0 && !collection.getImageLink().equals("null")) {
				writer.write("<img src=\"");
				writer.write(collection.getImageLink());
				writer.write("\"/>\n");

			}
			for(int i=0; i < numberOfLinesToInclude; i++  ) {
				Item item = iter.next();
				writer.write("<p><a href=\"");
				writer.write(item.getLink());
				writer.write("\">\n");
				writer.write(item.getTitle());
				writer.write("</a></p>\n");
				if( includeDescription ) {
					writer.write("<p>");
					writer.write(item.getDescription());
					writer.write("</p>\n");
				}
				writer.write("<br/>\n");
			}
		} catch( IOException ioe ) {
			throw new ServletException(ioe);
		} finally {
			if( writer != null ) { writer.close(); }
		}


	}

}
