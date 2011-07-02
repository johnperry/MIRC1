/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.storageservice;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import org.apache.log4j.Logger;
import org.rsna.mircsite.util.StringUtil;
import org.rsna.mircsite.util.TomcatUser;
import org.rsna.mircsite.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Methods for constructing an XQuery to query the document index
 * and for processing the results of the query to produce a final
 * MIRCqueryresult.
 */
public class MircQueryProgram {

	static final Logger logger = Logger.getLogger(MircQueryProgram.class);

	/**
	 * Class constructor. Initialize the static table for parsing elements.
	 */
	public MircQueryProgram() {
		if (epTable == null) epTable = getEPTable();
	}

	/**
	 * Process a MIRCqueryresult, constructing the docref attribute and
	 * selecting the title and abstract requested by the user (known vs.
	 * unknown). The docref attribute is determined from the docref
	 * and filename attributes contained in the MIRCdocument and the
	 * attributes of the MIRCquery (display and bgcolor).
	 * @param context the URL path to the servlet.
	 * @param mircQueryResultXML the MIRCqueryresult Document.
	 * @param mircQueryXML the MIRCquery Document.
	 * @return the modified MIRCqueryresult Document.
	 */
	public Document processQueryResult(
						String context,
						Document mircQueryResultXML,
						Document mircQueryXML) {
		Element qRoot = mircQueryXML.getDocumentElement();
		boolean unknown = qRoot.getAttribute("unknown").equals("yes");
		String bgcolor = qRoot.getAttribute("bgcolor").trim();
		String display = qRoot.getAttribute("display").trim();
		String icons = qRoot.getAttribute("icons").trim();
		Element qrRoot = mircQueryResultXML.getDocumentElement();
		Node child = qrRoot.getFirstChild();
		while (child != null) {
			if ((child.getNodeType() == Node.ELEMENT_NODE) &&
				child.getNodeName().equals("MIRCdocument")) {
					fixResult(context,(Element)child, unknown, bgcolor, icons, display);
			}
			child = child.getNextSibling();
		}
		return mircQueryResultXML;
	}

	private void fixResult(
				String context,
				Element md,
				boolean unknown,
				String bgcolor,
				String icons,
				String display) {
		//First fix the docref
		String docref = md.getAttribute("docref").trim();
		String path = md.getAttribute("filename").trim();
		md.removeAttribute("filename");
		if (docref.equals("")) {
			//There is no docref, use the context and the path.
			docref = context + path;
		}
		else {
			//docref is present, see what kind of a docref it is.
			if (docref.indexOf("://") != -1) {
				//docref is absolute. It does not need to be modified.
			}
			else if (docref.startsWith("/")) {
				//docref is relative to the root of the servlet.
				//In this implementation, the servlet is at context.
				//In principle, this means that the absolute path is
				//the sum of context and docref, except that
				//since context ends with "/" and docref starts with "/"
				//we have to be careful to remove one of the "/" characters.
				docref = context + docref.substring(1);
			}
			else {
				//docref is relative to the directory containing the MIRCdocument.
				//This can happen when a MIRCdocument is an index card for
				//another file type located in the same directory or a subdirectory.
				//The path contains the path to the MIRCdocument file, so we need
				//all the path up to the last "/".
				docref = context +
							path.substring(0,path.lastIndexOf("/")+1) +
								docref;
			}
		}
		String qps = "";
		if (unknown) qps += "unknown=yes";
		if (!bgcolor.equals("")) {
			if (!qps.equals("")) qps += "&";
			qps += "bgcolor="+bgcolor;
		}
		if (!display.equals("")) {
			if (!qps.equals("")) qps += "&";
			qps += "display="+display;
		}
		if (!icons.equals("")) {
			if (!qps.equals("")) qps += "&";
			qps += "icons="+icons;
		}
		if (!qps.equals("")) docref += "?" + qps;
		md.setAttribute("docref",docref);

		//Now fix the title and abstract elements and remove the alternative elements.
		Element title = null;
		Element altTitle = null;
		Element abs = null;
		Element altAbs = null;
		Element category = null;
		Node child = md.getFirstChild();
		while (child != null) {
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				if (child.getNodeName().equals("title")) title = (Element)child;
				else if (child.getNodeName().equals("alternative-title")) altTitle = (Element)child;
				else if (child.getNodeName().equals("abstract")) abs = (Element)child;
				else if (child.getNodeName().equals("alternative-abstract")) altAbs = (Element)child;
				else if (child.getNodeName().equals("category")) category = (Element)child;
			}
			child = child.getNextSibling();
		}
		if (unknown) {
			if (altTitle != null) copy(altTitle, title, "title");
			else {
				while ((child = title.getFirstChild()) != null) title.removeChild(child);
				String titleString = "Unknown";
				if (category != null) {
					String cat = getTextValue(category).trim();
					if (!cat.equals("")) titleString += " - " + cat;
				}
				title.appendChild(title.getOwnerDocument().createTextNode(titleString));
			}
			if (altAbs != null)  copy(altAbs, abs, "abstract");
			else if (abs != null) md.removeChild(abs);
		}
		if (altTitle != null) md.removeChild(altTitle);
		if (altAbs != null) md.removeChild(altAbs);
	}

	private void copy(Element from, Element to, String name) {
		if (to == null) {
			to = from.getOwnerDocument().createElement(name);
			from.getParentNode().insertBefore(to,from);
		}
		Node child;
		while ((child = to.getFirstChild()) != null) to.removeChild(child);
		while ((child = from.getFirstChild()) != null) {
			from.removeChild(child);
			to.appendChild(child);
		}
	}

	//===================================================================================

	/**
	 * Create the XQuery code for an entire query.
	 * @param queryMode "open" for the original query method (all results
	 * are returned for all users), or "restricted" for sites requiring
	 * authentication to see anything more than public documents in
	 * query results.
	 * @param orderBy the flag indicating how to order the results ("title" or "lmdate").
	 * @param tcUser the TomcatUser if authenticated; otherwise null;
	 * @param mircQueryXML the MIRCquery Document.
	 * @param tagline the site's tagline, used in the query results list
	 * as a subheading under the name of the site.
	 * @return the XQuery code to implement the MIRCquery.
	 */
	public String getXQuery (
						String queryMode,
						String orderBy,
						TomcatUser tcUser,
						Document mircQueryXML,
						String tagline) {

		Element root = mircQueryXML.getDocumentElement();
		String x = root.getAttribute("firstresult");
		int firstresult = 1;
		try { firstresult = Integer.parseInt(x); }
		catch (Exception ex) { }
		if (firstresult < 1) firstresult = 1;
		x = root.getAttribute("maxresults");
		int maxresults = 20;
		try { maxresults = Integer.parseInt(x); }
		catch (Exception ex) { }
		if (maxresults < 1) maxresults = 1;

		if (orderBy.equals("title"))
			orderBy = "    order by $x/../lc/title\n";
		else
			orderBy = "    order by number($x/../sm/lmdate) descending\n";

		String searchcode =
			"let $firstresult := "+firstresult+"\n"
		+	"let $maxresults := "+maxresults+"\n"
		+	"let $tagline := \""+tagline+"\"\n"
		+	"let $matches :=\n"
		+	"  for $x in " + getXPath(queryMode, tcUser, mircQueryXML) + "\n"
		+	       orderBy
		+	"      return $x\n"
		+	"let $n := count($matches)\n"
		+	"let $page := \n"
		+	"  for $y in $matches[(position() >= $firstresult) and (position() < $firstresult+$maxresults)]\n"
		+	"    return $y\n"
		+	"return\n"
		+	"  <MIRCqueryresult>\n"
		+	"    <preamble>\n"
		+	"      {if ($tagline=\"\") then \"\" else <p><b>{$tagline}</b></p>}\n"
		+	"      <p>Total search matches: {$n}</p>\n"
		+	"    </preamble>\n"
		+	"    {for $z in $page return $z}\n"
		+	"  </MIRCqueryresult>\n";

		return searchcode;
	}

	//===================================================================================

	//Get the XPath expression that implements the
	//selection of MIRCdocuments defined by the MIRCquery
	private String getXPath(String queryMode,
							TomcatUser tcUser,
							Document mircQueryXML) {
		Element root = mircQueryXML.getDocumentElement();
		if (root.getTagName().equals("MIRCquery")) {
			StringBuffer sb = new StringBuffer();
			sb.append("/doc/MIRCdocument");
			String searchFields = checkChildren("../lc", root);
			String authorization = checkUserAndRoles("../lc", queryMode, tcUser);
			if (searchFields.length() == 0) {
				if (authorization.length() != 0) {
					sb.append("[");
					sb.append(authorization);
					sb.append("]");
				}
			}
			else {
				sb.append("[");
				sb.append(searchFields);
				if (authorization.length() != 0) {
					sb.append(" and ");
					sb.append(authorization);
				}
				sb.append("]");
			}
			return sb.toString();
		}
		else return "/x[1=0]"; //This isn't a MIRCquery; return zero results.
	}

	private String checkChildren(String path, Element el) {
		StringBuffer sb = new StringBuffer();
		Node child = el.getFirstChild();
		boolean and = false;
		while (child != null) {
			short nodeType = child.getNodeType();
			if (nodeType == Node.TEXT_NODE) {
				String text = child.getNodeValue().trim();
				if (text.length() != 0) {
					String containsText = checkText(text);
					if (containsText.length() != 0) {
						if (and) sb.append(" and ");
						sb.append(containsText);
						and = true;
					}
				}
			}

			else if (nodeType == Node.ELEMENT_NODE) {
				ElementParam ep = getElementParam(child.getNodeName());
				if (ep.children) {
					String childQuery;
					if (!ep.range)
						childQuery = checkChildren(ep.path, (Element)child);
					else
						childQuery = checkPtAge(ep.path, (Element)child);
					if (childQuery.length() != 0) {
						if (and) sb.append(" and ");
						sb.append(childQuery);
						and = true;
					}
				}
				else {
					if (and) sb.append(" and ");
					sb.append(ep.path);
					and = true;
				}
			}
			child = child.getNextSibling();
		}
		return and ? path + "[" + sb.toString() + "]" : "";
	}

	//===========================================================================

	//Produce the search code for one text node:
	private String checkText(String text) {
		String x = "";
		boolean and = false;
		LinkedList<String> tokens = getTokens(text);
		Iterator<String> tIterator = tokens.iterator();
		String s;
		while (tIterator.hasNext()) {
			s = tIterator.next().toLowerCase();
			if (s.length() == 1) {
				if (s.equals("(")) {
					if (and) x += " and ";
					x += s + " ";
					and = false;
				}
				else if (s.equals(")")) {
					x += s;
					and = true;
				}
				else if (s.equals("|")) {
					x += " or ";
					and = false;
				}
				else {
					if (and) x += " and ";
					x += "contains(.,\"" + s + "\") ";
					and = true;
				}
			}
			else {
				if (and) x += " and ";
				x += "contains(.,\"" + s + "\") ";
				and = true;
			}
		}
		return x;
	}

	//Get the LinkedList of tokens made from one text node.
	private LinkedList<String> getTokens (String s) {
		LinkedList<String> tokens = new LinkedList<String>();
		int tlen;
		int i;
		if (s != null) {
			i = StringUtil.skipWhitespace(s,0);
			while (i < s.length()) {
				tlen = getTokenLength(s,i);
				if ((s.charAt(i) == '\"') && (s.charAt(i+tlen-1) == '\"'))
					tokens.add(s.substring(i+1,i+tlen-1));
				else
					tokens.add(s.substring(i,i+tlen)); //ignore the quote if it isn't matched
				i = StringUtil.skipWhitespace(s,i+tlen);
			}
		}
		return tokens;
	}

	//Get the length of the current token:
	private int getTokenLength (String s, int i) {
		int tlen = 0;
		char c;
		if (i < s.length()) {
			c = s.charAt(i);
			if (c == '\"') {
				i++; tlen++;
				while (i < s.length()) {
					tlen++;
					if (s.charAt(i) == '\"') break;
					i++;
				}
			}
			else if ((c == '|') || (c == '(') || (c == ')')) {
				tlen = 1;
			}
			else {
				while (i < s.length()) {
					c = s.charAt(i);
					if ((c == ' ') || (c == '\t') || (c == '|') || (c == '(') || (c == ')')) break;
					i++; tlen++;
				}
			}
		}
		return tlen;
	}

	//===========================================================================

	//Produce the search code for the pt-age range query.
	private String checkPtAge(String path, Element ptAge) {
		String sc = "";
		int[] ageRange = getAgeRange(ptAge);
		if (ageRange[0] >= 0) {
			if (ageRange[1] != ageRange[2])
				sc = path + "[(number(.)>="+ageRange[1]+") and (number(.)<="+ageRange[2]+")]";
			else
				sc = path + "[(number(.)="+ageRange[1]+")]";
		}
		return sc;
	}

	//Get the query age range in days
	private int[] getAgeRange(Element ptAge) {
		int[] ageRange = {-1,0,0};
		ageRange = updateAgeRange(ageRange, getChildText(ptAge,"years"), 365);
		ageRange = updateAgeRange(ageRange, getChildText(ptAge,"months"), 30);
		ageRange = updateAgeRange(ageRange, getChildText(ptAge,"weeks"), 7);
		ageRange = updateAgeRange(ageRange, getChildText(ptAge,"days"), 1);
		return ageRange;
	}

	//Get the text value of a child element with a specified name.
	private String getChildText(Element el, String childName) {
		String value = "";
		Node child = el.getFirstChild();
		while (child != null) {
			if ((child.getNodeType() == Node.ELEMENT_NODE) &&
				child.getNodeName().equals(childName)) {
					return getTextValue((Element)child);
			}
			child = child.getNextSibling();
		}
		return value;
	}

	//Get the sum of all the text nodes of an element

	private String getTextValue(Element el) {
		String value = "";
		Node child = el.getFirstChild();
		while (child != null) {
			if (child.getNodeType() == Node.TEXT_NODE) {
				value += child.getNodeValue();
			}
			child = child.getNextSibling();
		}
		return value;
	}

	//Update the age range array.
	//
	//The values in the array are:
	//0: a flag indicating that values have been inserted.
	//1: the lower limit of the age range.
	//2: the upper limit of the age range.
	//
	//The input string is a number or range (e.g., 17 or 11-19).
	//The multiplier converts the number to days.
	//If an error occurs while parsing the input string, the
	//age range is returned with the best value possible under
	//the circumstances.
	private int[] updateAgeRange(int[] a, String s, int multiplier) {
		s = s.trim();
		if (s.equals("")) return a;
		String[] sParts = s.split("-");
		if (sParts.length == 0) return a;
		int v1 = 0;
		try {
			v1 = Integer.parseInt(sParts[0].trim()) * multiplier;
			a[0] = 0;
			a[1] += v1;
		}
		catch (Exception e) { return a; }
		if (sParts.length == 1) {
			a[2] += v1;
			return a;
		}
		try {
			int v2 = Integer.parseInt(sParts[1].trim()) * multiplier;
			a[2] += v2;
		}
		catch (Exception e) {
			a[2] += v1;
		}
		return a;
	}

	//===========================================================================

	//Generate the code to verify the username and roles during the query.
	private String checkUserAndRoles(String path, String queryMode, TomcatUser tcUser) {
		//If in open mode, allow everything. (This was the original system behavior.)
		if (queryMode.equals("open")) {
			return "";
		}
		//If the user is not authenticated, only show him public documents.
		if (tcUser == null) {
			return path + "/read[role='*']";
		}
		//The user is authenticated; if he is an admin, let him see everything.
		else if (tcUser.isAdmin) {
			return "";
		}
		//The user is not an admin; see if he has the storage service's user role.
		//If he does not, then treat the user as if he is not authenticated and
		//only show him public documents.
		else if (!tcUser.isUser) {
			return path + "/read[role='*']";
		}
		//Okay, we have an authenticated user who is allowed access to the
		//storage service but who is not an admin; only show him the things
		//he is allowed to see.
		String sc = path + "[read[(role='*') or (user='" + tcUser.username + "')";
		String[] roles = tcUser.roles.split("[\\s,;]");
		for (int i=0; i<roles.length; i++) {
			String role = roles[i].trim();
			if (!role.equals("")) sc += " or (role='" + role + "')";
		}
		return "(" + sc + "]])";
	}

	//===========================================================================

	//The rest of this code is for determining which elements must do
	//full searches and which can just do a first-level search.
	//Additionally, this identifies certain elements (e.g., peer-review)
	//that simply query for the existence of the element rather than
	//requiring a match on its contents. And finally, this identifies
	//certain elements (e.g., pt-age) that support a range of values.

	private static Hashtable<String,ElementParam> epTable = null;

	//Get the ElementParam for an element. If an element does not
	//appear in the table, return an ElementParam that forces a
	//search through the entire document for the element. This
	//supports searches for elements that extend the MIRCquery
	//schema without modifying this code.
	private ElementParam getElementParam(String name) {
		ElementParam ep = epTable.get(name);
		if (ep != null) return ep;
		return new ElementParam(name, ".//"+name, true, false);
	}

	class ElementParam {
		public String name;
		public String path; //path to use for query - used to determine whether to do in-depth search (.//)
		public boolean children; //true = process children; false = just text for existence of element
		public boolean range; //true = test for children in range; false = match contents of children

		public ElementParam(String name) {
			this(name, name, true, false);
		}

		public ElementParam(String name, boolean children, boolean range) {
			this(name, name, children, range);
		}

		public ElementParam(String name, String path, boolean children, boolean range) {
			this.name = name;
			this.path = path;
			this.children = children;
			this.range = range;
		}
	}

	//For now, this table is hard-coded.
	//To make it easy to extend the schema in the future, this table
	//should be built from an XML file in the root of the storage
	//service.
	private Hashtable<String,ElementParam> getEPTable() {
		Hashtable<String,ElementParam> epTable = new Hashtable<String,ElementParam>();
		epTable.put("title",		new ElementParam("title"));
		epTable.put("author",		new ElementParam("author"));
		epTable.put("name",			new ElementParam("name"));
		epTable.put("affiliation",	new ElementParam("affiliation"));
		epTable.put("contact",		new ElementParam("contact"));
		epTable.put("abstract",		new ElementParam("abstract"));
		epTable.put("keywords",		new ElementParam("keywords"));
		epTable.put("pt-name",		new ElementParam("pt-name"));
		epTable.put("pt-id",		new ElementParam("pt-id"));
		epTable.put("pt-mrn",		new ElementParam("pt-mrn"));
		epTable.put("pt-age",		new ElementParam("pt-age",true,true));
		epTable.put("pt-sex",		new ElementParam("pt-sex"));
		epTable.put("pt-race",		new ElementParam("pt-race"));
		epTable.put("pt-species",	new ElementParam("pt-species"));
		epTable.put("pt-breed",		new ElementParam("pt-breed"));
		epTable.put("document-type",new ElementParam("document-type"));
		epTable.put("category",		new ElementParam("category"));
		epTable.put("level",		new ElementParam("level"));
		epTable.put("access",		new ElementParam("access"));
		epTable.put("peer-review",	new ElementParam("peer-review",false,false));
		epTable.put("language",		new ElementParam("language"));
		return epTable;
	}

}
