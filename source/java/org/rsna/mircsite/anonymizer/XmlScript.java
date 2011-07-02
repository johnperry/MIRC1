/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.anonymizer;

import java.util.Hashtable;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * A utility for executing script commands.
 */
class XmlScript {

	public String script = null;
	Document document = null;
	Hashtable table = null;
	Remapper remapper = null;


	/**
	 * Construct a new XmlScript.
	 * @param script the text of the script command.
	 */
	public XmlScript(Document document, Hashtable table, String script, Remapper remapper) {
		this.document = document;
		this.table = table;
		if (script != null) script = script.trim();
		this.script = script;
		this.remapper = remapper;
	}

	/**
	 * Determine whether the script contains a $require()
	 * function call.
	 * @return true if the require function is present in
	 * the script; false otherwise.
	 */
	public boolean isRequired() {
		return script.startsWith("$require(");
	}

	/**
	 * Determine whether the script contains a $remove()
	 * function call.
	 * @return true if the remove function is present in
	 * the script; false otherwise.
	 */
	public boolean isRemoved() {
		return script.startsWith("$remove(");
	}

	/**

	 * Get the value of the script.
	 * @param the current value of the node whose value
	 * is to be replaced.
	 * @return the value computed from the script and the current
	 * node value.
	 * @throws Exception if it is impossible to execute the script.
	 */
	public String getValue(String nodeValue) throws Exception {
		if (script == null) return "null";
		String value = "";
		String name;
		String temp;
		String params;
		XmlScript paramsScript;
		int k = 0;
		int kk;
		int kp;
		while (k < script.length()) {
			if (script.charAt(k) =='\"') {
				//it's a literal
				k++;
				kk = script.indexOf("\"",k);
				if (kk == -1)  {
					value += script.substring(k);
					k = script.length();
				}
				else if (script.charAt(kk-1) == '\\') {
					value += script.substring (k,kk-1) + "\"";
					k = kk;
				}
				else {
					value += script.substring(k,kk);
					k = kk + 1;
				}
			}
			else if (script.charAt(k) == '$') {
				//it's either a function call or a variable reference
				kk = findDelimiter(script,k);
				if ((kk < script.length()) && (script.charAt(kk) == '(')) {
					//it's a function call
					name = script.substring(k,kk);
					kp = findParamsEnd(script,kk);
					temp = script.substring(kk+1,kp);
					k = kp + 1;

					if (name.equals("$require")) {
						paramsScript = new XmlScript(document,table,temp,remapper);
						value += paramsScript.getValue(nodeValue);
					}

					else if (name.equals("$remove")) {
						return "";
					}

					else if (name.equals("$uid")) {
						paramsScript = new XmlScript(document,table,temp,remapper);
						String uidroot = paramsScript.getValue("");
						remapper.clear();
						if ((nodeValue == null) || (nodeValue.trim().equals("")))
							remapper.getUID(0,uidroot);
						else
							remapper.getUID(0,uidroot,nodeValue.trim());
						Hashtable<String,String> rv = remapper.getRemappedValues();
						value += rv.get("0");
					}
				}
				else {
					//it's a variable reference
					name = script.substring(k,kk);
					temp = (String)table.get(name);
					if (temp == null) temp = "null";
					value += temp;
					k = kk;
				}
			}
			else if (script.charAt(k) =='/') {
					//it's a path expression
					kk = findDelimiter(script,k);
					String path = script.substring(k,kk).trim();
					value += getPathValue(new XmlPathElement(document,path));
					k = kk;
			}
			else k++;
		}
		return value;
	}

	//Get the index of the delimiter of a name. Delimiters are:
	//   whitespace
	//   the end of the string
	//   ( or , or )
	private int findDelimiter(String s, int k) {
		char c;
		while (k < s.length()) {
			c = s.charAt(k);
			if (!Character.isWhitespace(c)
				&& (c != '(') && (c != ',') && (c != ')')) k++;
			else return k;
		}
		return k;
	}

	//Get the index of the end parenthesis in parameter
	//list in a function call, allowing for nesting.
	//This method is called with k pointing to the
	//the opening parenthesis character.
	private int findParamsEnd(String s, int k) {
		int count = 0;
		char c;
		while (k < s.length()) {
			c = s.charAt(k++);
			if (c == '(') count++;
			else if (c == '"') k = skipQuote(s,k);
			else if (c == ')') count--;
			if (count == 0) return k-1;;
		}
		return k;
	}

	//Get the index of the character after the end of
	//a quoted string. This method is called with k
	//pointing to the character after the starting quote.
	private int skipQuote(String s, int k) {
		boolean esc = false;
		while (k < s.length()) {
			if (esc) esc = false;
			else if (s.charAt(k) == '\\') esc = true;
			else if (s.charAt(k) == '"') return k+1;
			k++;
		}
		return k;
	}

	//Get the value from the end of a path,
	//always choosing the first node if a segment
	//produces multiple nodes.
	private String getPathValue(XmlPathElement pe) {

		//If the next segment is an attribute, get the value.
		if (pe.segmentIsAttribute()) {
			return pe.getValue();
		}

		//No, see if the next segment is empty, meaning that
		//the parent node of this XmlPathElement is the end of
		//the path. If so, get the value of the parent node.
		if (pe.isEndSegment()) {
			return pe.getValue();
		}
		//This is not the end of the path; get the NodeList;
		//then see if there is an element available for the segment.
		NodeList nl = pe.getNodeList();

		if ((nl == null) || (nl.getLength() == 0)) {
			//The element identified by the segment is missing.
			return "null";
		}

		//If we get here, one or more elements identified by
		//the segment are present and this is not the end of
		//the path; pick the first child segment.
		String remainingPath = pe.getRemainingPath();
		return getPathValue(new XmlPathElement(nl.item(0),remainingPath));
	}
}

