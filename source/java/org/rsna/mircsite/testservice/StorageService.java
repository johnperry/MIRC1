/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.testservice;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.rsna.mircsite.storageservice.MircQueryProgram;
import org.rsna.mircsite.util.*;
import org.w3c.dom.Document;

/**
 * The MIRC Test Storage Service servlet.
 * <p>
 * This Storage Service echoes queries and displays the query
 * program produced in response to the query. It is for use
 * by developers who may want to see exactly what query they
 * are receiving.
 * <br>
 * This servlet responds to HTTP POST.
 * <br>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class StorageService extends HttpServlet {

	/**
	 * The servlet method that responds to an HTTP POST.
	 * <p>
	 * This method interprets the parameters as a query generated by the
	 * Query Service, uses it to search the index, and returns a MIRCqueryresult.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doPost(
				HttpServletRequest req,
				HttpServletResponse res
				) throws IOException, ServletException {

		res.setContentType("text/html; charset=\"UTF-8\"");
		PrintWriter out = res.getWriter();

		//Check that this is a post of a MIRCquery
		String requestContentType = req.getContentType();
		if ((requestContentType != null) &&
				(requestContentType.toLowerCase().indexOf("text/xml") >= 0)) {

			//Yes, read the entire query
			int n;
			BufferedReader in = new BufferedReader(new InputStreamReader(req.getInputStream()));
			StringWriter sw = new StringWriter();
			char[] cbuf = new char[1024];
			while ((n = in.read(cbuf,0,1024)) != -1) { sw.write(cbuf,0,n); }
			String mircQueryString = sw.toString();

			//Parse the MIRCquery
			Document mircQueryXML;
			try {
				mircQueryXML = XmlUtil.getDocumentFromString(mircQueryString);
			}
			catch (Exception e) {
				out.print(
					XmlStringUtil.makeMQRString(
						"Error parsing the MIRCquery:"
						+ "<br/>Exception message: " + e.getMessage()
						+ "<br/>MIRCquery string length: " + mircQueryString.length()
						+ "<br/>MIRCquery string:<br/><br/>"
						+ XmlStringUtil.makeReadableTagString(mircQueryString)));
				out.close();
				return;
			}

			//Get the user's credentials, if any.
			Visa visa = new Visa(req);
			TomcatUser tcUser = null;
			if ((visa.username != null) && !visa.username.equals("") &&
				(visa.password != null) && !visa.password.equals("")) {
				File rootDir = new File(getServletContext().getRealPath("/"));
				TomcatUsers tcUsers = TomcatUsers.getInstance(rootDir);
				tcUser = tcUsers.getTomcatUser(visa.username);
				if ((tcUser != null) && !visa.password.equals(tcUser.password)) {
					tcUser = null;
				}
			}
			//Since there are no admin or user roles for the test service,
			//force some values that will assist in testing.
			if (tcUser != null) {
				tcUser.isAdmin = false;
				tcUser.isUser = true;
			}

			//Now get the query program
			MircQueryProgram mqp = new MircQueryProgram();
			String xQuery =
				mqp.getXQuery("restricted","lmdate",tcUser,mircQueryXML,"tagline");

			//and return a dummy document with the contents of the query
			out.print(getMircQueryResult(mircQueryString,xQuery));
			out.close();
		}

		else {
			out.println(
				XmlStringUtil.makeMQRString(
					"Unsupported Content-Type: "+req.getContentType()));
			out.close();
		}
	}

	//Produce a MIRCqueryresult string that displays the contents of
	//the query in the preamble, allowing Query Services to handle the result like
	//any normal MIRCqueryresult.
	private String getMircQueryResult(String mqs, String xqs) {

		String returnString =
			  "<MIRCqueryresult>"
			+ "<preamble>"
			+ "<p>This test site echoes the contents of the MIRCquery.</p>"
			+ "<br/>"
			+ "<font color=\"red\">MIRCquery:<br/></font><br/>"
			+ "<b>" + XmlStringUtil.makeReadableTagString(mqs) + "</b>"
			+ "<br/><br/>"
			+ "<font color=\"red\">XQuery:<br/></font><br/>"
			+ XmlStringUtil.makeReadableTagString(xqs)
			+ "<br/>" + "\n</preamble>"
			+ "</MIRCqueryresult>";

		return returnString;
	}

}