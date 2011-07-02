/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.storageservice;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.rsna.mircsite.util.FileUtil;
import org.rsna.mircsite.util.HtmlUtil;
import org.rsna.mircsite.util.MircIndexEntry;
import org.rsna.mircsite.util.ServletUtil;
import org.rsna.mircsite.util.StringUtil;
import org.rsna.mircsite.util.XmlUtil;
import org.w3c.dom.*;

/**
 * A servlet to return a summary of documents produced by authors.
 * <p>
 * The servlet responds to both HTTP GET and POST.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class AuthorSummary extends HttpServlet {

	private static final long serialVersionUID = 123123127;

	static final Logger logger = Logger.getLogger(AuthorSummary.class);

	/**
	 * The servlet method that responds to an HTTP GET.
	 * <p>
	 * If called with no query string, it returns a web page containing a
	 * form for requesting a summary page. If called with a query string,
	 * it returns a summary in the form specified in the query string.
	 * <p>The query parameters are:
	 * <ul>
	 * <li>start: the first date to accept (YYYYMMDD)
	 * <li>end: the last date to accept (YYYYMMDD)
	 * <li>format: html, xml, or csv (default = html)
	 * <li>title: yes or no (default = no)
	 * <li>name: yes or no (default = no)
	 * </ul>
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 * @throws ServletException if the servlet cannot handle the request.
	 * @throws IOException if any IO error occurs.
	 */
	public void doGet(
					HttpServletRequest req,
					HttpServletResponse res
					) throws ServletException {

		try {
			req.setCharacterEncoding("UTF-8");
			if ((req.getParameter("start") == null) || (req.getParameter("end") == null)) {
				//No parameters, just return the page;
				res.setContentType("text/html; charset=UTF-8");
				PrintWriter out = res.getWriter();
				out.write(getPage(req));
				out.flush();
				out.close();
				return;
			}
			else doQuery(req, res);
		}
		catch (Exception error) { throw new ServletException(error.getMessage()); }
	}

	/**
	 * The servlet method that responds to an HTTP POST.
	 * <p>
	 * Returns content depending on the parameters, which are the same as
	 * the query parameters specified for a GET.
	 * <p>
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 * @throws ServletException if the servlet cannot handle the request.
	 * @throws IOException if any IO error occurs.
	 */
	public void doPost(
					HttpServletRequest req,
					HttpServletResponse res
					) throws ServletException {

		try {
			res.setContentType("text/plain; charset=UTF-8");
			req.setCharacterEncoding("UTF-8");
			doQuery(req, res);
		}
		catch (Exception error) { throw new ServletException(error.getMessage()); }
	}

	private void doQuery(HttpServletRequest req, HttpServletResponse res) throws Exception {
		//Get the sitename from the Storage Service
		String sitename = StorageConfig.getSitename();

		//Get the parameters from the request
		String start = getParameter(req, "start", "");
		String end = getParameter(req, "end", "30000000");
		String format = getParameter(req, "format", "html");
		String title = getParameter(req, "title", "no");
		String name = getParameter(req, "name", "no");
		String date = getParameter(req, "date", "no");
		String access = getParameter(req, "access", "no");
		String user = getParameter(req,"user","");
		if (user != null) user = user.trim();

		//If the request does not come from an admin user,
		//only allow the user to see his documents.
		if (!userIsAdmin(req)) user = req.getRemoteUser();

		//Get all the documents in the index
		MircIndex index = MircIndex.getInstance();
		MircIndexEntry[] mies = index.query("");
		index.sortByPubDate(mies);

		//Now process the entries in accordance with the request
		/*
		  <IndexSummary>
			<StorageService>{$ssname}</StorageService>
			<Context>{$context}</Context>
			<StartDate>{$date1}</StartDate>
			<EndDate>{$date2}</EndDate>
			<IndexedDocs>{$totaldocs}</IndexedDocs>
			<DocsInRange>{$totalmatches}</DocsInRange>
			<UnownedDocs>{count($unowneddocs)}</UnownedDocs>
			{for $x in $ownertable return $x}
			{if ((($user="*") or ($user="")) and count($unowneddocs) > 0) then
			  <Unowned>{for $q in $unownedtable return $q}</Unowned> else ()}
		  </IndexSummary>
		*/

		//Construct the XML document containing the results.
		//Note: this has the same schema as the object which used to be
		//created by the XQuery of the eXist database in T35 and earlier.
		Document doc = XmlUtil.getDocument();
		Element root = doc.createElement("IndexSummary");
		doc.appendChild(root);
		addElement(root, "StorageService", sitename);
		addElement(root, "Context", req.getContextPath());
		addElement(root, "StartDate", start);
		addElement(root, "EndDate", end);
		addElement(root, "IndexedDocs", Integer.toString(mies.length));

		//Now narrow down the list to the date range
		MircIndexEntry[] selected = selectByDate(mies, start, end);
		addElement(root, "DocsInRange", Integer.toString(selected.length));

		//Get the unowned docs
		MircIndexEntry[] unowned = selectUnowned(mies);
		addElement(root, "UnownedDocs", Integer.toString(unowned.length));

		//Now put in the selected user(s) documents
		String[] owners = getOwners(selected, user);
		for (String owner : owners) {
			addOwnerResult(root, mies, selected, owner);
		}

		//Finally, put in the unowned documents, if appropriate
		if (user.equals("*") || user.equals("")) {
			addDocs(root, unowned);
		}

		//Return it in the requested format
		if (format.equals("xml")) {
			res.setContentType("text/html; charset=UTF-8");
			String disposition = "attachment; filename=summary.xml";
			res.setHeader("Content-Disposition",disposition);
			PrintWriter out = res.getWriter();
			out.write(XmlUtil.toString(doc));
			out.flush();
			out.close();
			return;
		}

		//Make an array or parameters for the transformations
		Object[] params = {
					"show-titles",	title,
					"show-names",	name,
					"show-dates",	date,
					"show-access",	access };

		if (format.equals("csv")) {
			res.setContentType("text/plain; charset=UTF-8");
			String disposition = "attachment; filename=summary.csv";
			res.setHeader("Content-Disposition",disposition);
			PrintWriter out = res.getWriter();
			File xfmFile = new File(getServletContext().getRealPath("summaryToCSV.xsl"));
			out.write(XmlUtil.getTransformedText(doc, xfmFile, params));
			out.flush();
			out.close();
			return;
		}

		//None of the above formats; return it as HTML
		res.setContentType("text/html; charset=UTF-8");
		PrintWriter out = res.getWriter();
		File xfmFile = new File(getServletContext().getRealPath("summaryToHTML.xsl"));
		String result = XmlUtil.getTransformedText(doc, xfmFile, params);
		File summaryFile = new File(getServletContext().getRealPath("summary.html"));
		FileUtil.setFileText(summaryFile, result);
		out.write(result);
		out.flush();
		out.close();
		return;
	}

	private String getParameter(HttpServletRequest req, String name, String defValue) {
		String value = req.getParameter(name);
		return (value != null) ? value : defValue;
	}

	private String getPage(HttpServletRequest req) {
		String today = StringUtil.getDate().replace("-","");
		StringBuffer sb = new StringBuffer();
		sb.append("<html>");
		sb.append("<head><title>Author Summary Request</title></head>");
		sb.append("<body style=\"background: #c6d8f9; margin:0; padding:0;\">");
		sb.append(HtmlUtil.getCloseBox());

		sb.append("<center>");
		sb.append("<br/><h1>Author Summary Request</h1>");

		sb.append("<form method=\"post\" action=\"\" accept-charset=\"UTF-8\">");

		sb.append("<table border=\"1\">");

		if (userIsAdmin(req)) {
			String username = req.getRemoteUser();
			sb.append("<tr>");
			sb.append("<td>Username (* to select all users):</td>");
			sb.append("<td><input type=\"text\" name=\"user\" value=\"*\"/></td>");
			sb.append("</tr>");
		}

		sb.append("<tr>");
		sb.append("<td>Start date (inclusive, YYYYMMDD):</td>");
		sb.append("<td><input type=\"text\" name=\"start\"/></td>");
		sb.append("</tr>");

		sb.append("<tr>");
		sb.append("<td>End date (inclusive, YYYYMMDD):</td>");
		sb.append("<td><input type=\"text\" name=\"end\" value=\""+today+"\"/></td>");
		sb.append("</tr>");

		sb.append("<tr>");
		sb.append("<td>Show document titles on web page:</td>");
		sb.append("<td><input type=\"checkbox\" name=\"title\" value=\"yes\" checked/></td>");
		sb.append("</tr>");

		sb.append("<tr>");
		sb.append("<td>Show author names on web page:</td>");
		sb.append("<td><input type=\"checkbox\" name=\"name\" value=\"yes\" checked/></td>");
		sb.append("</tr>");

		sb.append("<tr>");
		sb.append("<td>Show creation dates on web page:</td>");
		sb.append("<td><input type=\"checkbox\" name=\"date\" value=\"yes\" checked/></td>");
		sb.append("</tr>");

		sb.append("<tr>");
		sb.append("<td>Show access on web page:</td>");
		sb.append("<td><input type=\"checkbox\" name=\"access\" value=\"yes\" checked/></td>");
		sb.append("</tr>");

		sb.append("<tr>");
		sb.append("<td>Output format:</td>");
		sb.append("<td><select name=\"format\">");
		sb.append("<option value=\"csv\">Spreadsheet</option>");
		sb.append("<option value=\"html\" selected>Web page</option>");
		sb.append("<option value=\"xml\">XML</option>");
		sb.append("</select></td>");
		sb.append("</tr>");

		sb.append("</table>");

		sb.append("<p><input type=\"submit\" value=\"Submit\"/></p>");

		sb.append("</form>");

		sb.append("</center>");

		sb.append("</body>");
		sb.append("</html>");
		return sb.toString();
	}

	public static boolean userIsAdmin(HttpServletRequest req) {
		String adminRoleName = StorageConfig.getAdminRoleName().trim();
		if (req.isUserInRole(adminRoleName)) return true;
		return false;
	}

    /*
    <Owner>
      <username>{$x}</username>
      <IndexedDocs>{count(/doc/sm/owner[user=$x])}</IndexedDocs>
      <DocsInRange>{count($matches/sm/owner[user=$x])}</DocsInRange>
      <PublicDocsInRange>{count($matches/sm[owner[user=$x] and (access="public")])}</PublicDocsInRange>
      {for $y in $matches[sm/owner[user=$x]]
        return
          <doc>
            {$y/MIRCdocument/title}
            <file>{string($y/MIRCdocument/@filename)}</file>
            {for $z in $y/MIRCdocument/author return $z/name}
            {$y/sm/pubdate}
            {$y/sm/access}
          </doc>
      }
    </Owner>
    */
	private void addOwnerResult(Element parent, MircIndexEntry[] mies, MircIndexEntry[] selected, String owner) {
		Document doc = parent.getOwnerDocument();
		Element child = doc.createElement("Owner");
		parent.appendChild(child);
		addElement(child, "username", owner);
		MircIndexEntry[] docs = selectByOwner(mies, owner);
		addElement(child, "IndexedDocs", Integer.toString(docs.length));
		docs = selectByOwner(selected, owner);
		addElement(child, "DocsInRange", Integer.toString(docs.length));
		addElement(child, "PublicDocsInRange", Integer.toString(countPublicDocs(docs)));
		addDocs(child, docs);
	}

	private void addDocs(Element parent, MircIndexEntry[] mies) {
		for (MircIndexEntry mie : mies) {
			Element docEl = parent.getOwnerDocument().createElement("doc");
			parent.appendChild(docEl);
			addElement(docEl, "title", mie.title);
			addElement(docEl, "file", mie.md.getAttribute("filename"));
			addAuthorNames(docEl, mie);
			addElement(docEl, "pubdate", mie.pubdate);
			addElement(docEl, "access", mie.access);
		}
	}

	private void addAuthorNames(Element parent, MircIndexEntry mie) {
		Document doc = parent.getOwnerDocument();
		NodeList nl = mie.md.getElementsByTagName("name");
		for (int i=0; i<nl.getLength(); i++) {
			Element name = doc.createElement("name");
			name.setTextContent( nl.item(i).getTextContent() );
			parent.appendChild(name);
		}
	}

	private int countPublicDocs(MircIndexEntry[] mies) {
		int count = 0;
		for (MircIndexEntry mie : mies) {
			if (mie.isPublic) count++;
		}
		return count;
	}

	private MircIndexEntry[] selectByOwner(MircIndexEntry[] mies, String owner) {
		LinkedList<MircIndexEntry> list = new LinkedList<MircIndexEntry>();
		for (MircIndexEntry mie : mies) {
			if (mie.owners.contains(owner)) {
				list.add(mie);
			}
		}
		return list.toArray(new MircIndexEntry[list.size()]);
	}

	private String[] getOwners(MircIndexEntry[] mies, String owner) {
		HashSet<String> owners = new HashSet<String>();
		if (owner.equals("*") || owner.equals("")) {
			for (MircIndexEntry mie : mies) {
				owners.addAll(mie.owners);
			}
			String[] names = owners.toArray(new String[owners.size()]);
			Arrays.sort(names);
			return names;
		}
		else return new String[] { owner };
	}

	private MircIndexEntry[] selectUnowned(MircIndexEntry[] mies) {
		LinkedList<MircIndexEntry> list = new LinkedList<MircIndexEntry>();
		for (MircIndexEntry mie : mies) {
			if (mie.owners.size() == 0) {
				list.add(mie);
			}
		}
		return list.toArray(new MircIndexEntry[list.size()]);
	}

	private MircIndexEntry[] selectByDate(MircIndexEntry[] mies, String start, String end) {
		LinkedList<MircIndexEntry> list = new LinkedList<MircIndexEntry>();
		for (MircIndexEntry mie : mies) {
			if ((mie.pubdate.compareTo(start) >= 0) && (mie.pubdate.compareTo(end) <= 0)) {
				list.add(mie);
			}
		}
		return list.toArray(new MircIndexEntry[list.size()]);
	}

	private void addElement(Element parent, String name, String text) {
		Element child = parent.getOwnerDocument().createElement(name);
		child.setTextContent(text);
		parent.appendChild(child);
	}

}

