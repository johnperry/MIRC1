/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.io.StringWriter;

/**
 * Encapsulates static methods for working with XML (and HTML) strings.
 */
public class XmlStringUtil {

	/**
	 * Searches an XML string for an entity definition
	 * and returns its value.
	 * @param xmlString the XML string to search.
	 * @param theName the name of the entity to find.
	 * @return the value of the named entity.
	 */
	public static String getEntity(String xmlString, String theName) {
		int begin = -1;
		int end;
		String name, value;
		while ((begin = xmlString.indexOf("!ENTITY",begin+1)) != -1) {
			begin = StringUtil.skipWhitespace(xmlString,begin+7);
			end = StringUtil.findWhitespace(xmlString,begin);
			name = xmlString.substring(begin,end);
			if (name.equals(theName)) {
				begin = xmlString.indexOf("\"",end) + 1;
				end = xmlString.indexOf("\"",begin);
				return xmlString.substring(begin,end);
			}
		}
		return "";
	}

	/**
	 * Replaces the value of an entity in an XML string
	 * and returns the updated XML string.
	 * @param xmlString the XML string.
	 * @param theName the name of the entity to modify.
	 * @param theValue the replacement value for the named entity. If null,
	 * no replacement is done.
	 * @return the updated XML string.
	 */
	public static String setEntity(String xmlString, String theName, String theValue) {
		if (theValue != null) {
			int begin = -1;
			int end;
			String name, value;
			while ((begin = xmlString.indexOf("!ENTITY",begin+1)) != -1) {
				begin = StringUtil.skipWhitespace(xmlString,begin+7);
				end = StringUtil.findWhitespace(xmlString,begin);
				name = xmlString.substring(begin,end);
				if (name.equals(theName)) {
					begin = xmlString.indexOf("\"",end) + 1;
					end = xmlString.indexOf("\"",begin);
					return xmlString.substring(0,begin) + theValue.trim() + xmlString.substring(end);
				}
			}
		}
		return xmlString;
	}

	/**
	 * Makes a MIRCqueryresult string with only a preamble.
	 * @param preambleString the text of the preamble.
	 * @return the MIRCqueryresult XML string.
	 */
	public static String makeMQRString(String preambleString) {
		return
			"<MIRCqueryresult>" +
				"<preamble>" + preambleString + "</preamble>" +
			"</MIRCqueryresult>";
	}

	/**
	 * Makes a tag string readable when rendered in HTML. The method
	 * escapes all the angle brackets and inserts spaces before and
	 * after tags so a browser will wrap the text correctly.
	 * This method is used in many places to return an XML string to the
	 * user in the event of an error.
	 * @param tagString the string containing XML tags.
	 * @return the readable tag string, or "null" if tagString is null.
	 */
	public static String makeReadableTagString(String tagString) {
		if (tagString == null) return "null";
		StringWriter sw = new StringWriter();
		char c;
		for (int i=0; i<tagString.length(); i++) {
			c = tagString.charAt(i);
			if (c == '<') sw.write(" &#60;");		//note the leading space
			else if (c == '>') sw.write("&#62; ");	//note the trailing space
			else if (c == '&') sw.write("&#38;");
			else if (c == '\"') sw.write("&#34;");
			else sw.write(c);
		}
		return sw.toString();
	}

	static final String ref = "<reference>";
	static final String endRef = "</reference>";
	/**
	 * Makes a string containing reference elements. This method is used by the
	 * author service to parse the contents of the References section and create
	 * reference elements from the text. Each reference element is separated
	 * from the next by at least two newline characters (and other miscellaneous
	 * whitespace).
	 * @param theString the text of the preamble.
	 * @return the string with reference elements inserted.
	 */
	public static String makeReferenceString(String theString) {
		theString = theString.trim();
		if (theString.equals("")) return "";
		theString = theString.replaceAll("[\\s]*?\\n[\\s]*?\\n[\\s]*+",endRef+ref);
		if (!theString.startsWith(ref)) theString = ref + theString;
		if (!theString.endsWith(endRef)) theString = theString + endRef;
		return makeFilteredString(theString);
	}

	/**
	 * Escapes the ampersand, angle bracket, single and double quote characters
	 * in a string.
	 * @param theString the string in which to escape the special characters.
	 * @return the modified string.
	 */
	public static String escapeChars(String theString) {
		return theString.replace("&","&amp;")
						.replace(">","&gt;")
						.replace("<","&lt;")
						.replace("\"","&quot;")
						.replace("'","&apos;");
	}

	/**
	 * Filter an XML string and escape the angle brackets in unmatched tags.
	 * This method is used to defend against a user inserting tags that make
	 * a block of text not well-formed.
	 * <p>
	 * This method is not bullet-proof, but it protects against most
	 * common mistakes. The rest have to be caught by the parser.
	 * @param theString the string in which to escape the special characters.
	 * @return the modified string.
	 */
	public static String makeFilteredString(String theString) {
		String t = theString;
		t = t.replaceAll("<br[\\s]*>","<br />").replaceAll("</br[\\s]*>","");
		t = t.replaceAll("<hr[\\s]*>","<hr />").replaceAll("</hr[\\s]*>","");
		t = t.replace("&","&amp;");
		String s = "";
		int left;
		int right;
		while (t.length() > 0) {
			left = t.indexOf("<");
			if (left == -1) return s + t.replace(">","&gt;");
			right = t.indexOf(">");
			if (right == -1) return s + t.replace("<","&lt;");
			if (right < left) {
				s += t.substring(0,right) + "&gt;";
				t = t.substring(right+1,t.length());
			}
			else {
				if (isItATag(t.substring(left,right+1))) {
					s += t.substring(0,right+1);
					t = t.substring(right+1,t.length());
				}
				else {
					s += t.substring(0,left) + "&lt;";
					t = t.substring(left+1,t.length());
				}
			}
		}
		return s;
	}

	/**
	 * Skip over an attribute in an XML string.
	 * @param xmlString the XML string.
	 * @param i the index of the start of the attribute.
	 * @return the index of the next non-whitespace
	 * character after the attribute.
	 */
	private static int skipAttribute(String xmlString, int i) {
		if ((i=StringUtil.skipWord(xmlString,i)) < 0) return -1;
		i = StringUtil.skipWhitespace(xmlString,i);
		if (xmlString.charAt(i) != '=') return -1;
		i = StringUtil.skipWhitespace(xmlString,i+1);
		if (xmlString.charAt(i) != '\"') return -1;
		i = xmlString.indexOf('\"',i+1);
		if (i < 0) return -1;
		i = StringUtil.skipWhitespace(xmlString,i+1);
		return i;
	}

	//Determine whether a string is an XML tag.
	private static boolean isItATag(String s) {
		if (s.charAt(0) != '<') return false;
		boolean endTag = false;
		int i = StringUtil.skipWhitespace(s,1);
		if (s.charAt(i) == '/') {
			endTag = true;
			i = StringUtil.skipWhitespace(s,i+1);
		}
		if ((i=StringUtil.skipWord(s,i)) < 0) return false;
		i = StringUtil.skipWhitespace(s,i);
		if (s.charAt(i) == '>') return true;
		if (endTag) return false;
		while (i < s.length()) {
			if (s.charAt(i) == '/') {
				i = StringUtil.skipWhitespace(s,i+1);
				if (s.charAt(i) == '>') return true;
				return false;
			}
			if (s.charAt(i) == '>') return true;
			if ((i=skipAttribute(s,i+1)) < 0) return false;
		}
		return false;
	}

	/**
	 * Find the integer value of an attribute starting at position k
	 * in a text string, returning the value of the attribute, or
	 * defaultValue if the attribute is not present.
	 * @param text the XML string.
	 * @param k the starting point in the XML string to search for the attribute.
	 * @param attr the name of the attribute.
	 * @param defaultValue the value to be returned if the attribute is not present
	 * or if the conversion of the attribute value to an integer fails.
	 * @return the integer value of the attribute, or the defaultValue.
	 */
	public static int getAttributeInt(String text, int k, String attr, int defaultValue) {
		int value = defaultValue;
		String attrValue = getAttribute(text,k,attr);
		if (attrValue != null) {
			try { value = Integer.parseInt(attrValue); }
			catch (Exception e) { };
		}
		return value;
	}

	/**
	 * Find the string value of an attribute starting at position k in
	 * a text string, returning the string value of the attribute, or
	 * null if the attribute is not present. Note: this method returns
	 * the value of the attribute with normalized whitespace.
	 * @param text the XML string.
	 * @param k the starting point in the XML string to search for the attribute.
	 * @param attr the name of the attribute.
	 * @return the string value of the attribute, or null if the attribute is not
	 * present.
	 */
	public static String getAttribute(String text, int k, String attr) {
		int kk = text.indexOf(">",k);
		if (kk < 0) return null;
		String t = text.substring(k,kk).replaceAll("\\s+"," ").replaceAll(" =","=").replaceAll("= ","=");
		k = t.indexOf(" " + attr + "=");
		if (k < 0) return null;
		k = t.indexOf("=",k) + 2;
		if (t.charAt(k-1) != '"') return null;
		kk = t.indexOf("\"",k);
		if (kk < 0) return null;
		return t.substring(k,kk);
	}
}