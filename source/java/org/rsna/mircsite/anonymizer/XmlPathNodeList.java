/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.anonymizer;

import java.util.LinkedList;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A class to assist in walking down an XML path, with support for wildcards.
 * A path consists of a starting node and a text string in the format of an
 * XPath expression, except that instead of predicates, path segments can have
 * wildcards or predicate-like qualifiers as described in the documentation
 * for the segment field below.
 */
class XmlPathNodeList extends LinkedList implements NodeList {

	/**
	 * Construct a new NodeList.
	 */
	public XmlPathNodeList() {
		super();
	}

	/**
	 * Get the size of the NodeList.
	 * @return the size of the NodeList.
	 */
	public int getLength() {
		return this.size();
	}

	/**
	 * Get a node from the list.
	 * @return the next node in the NodeList.
	 */
	public Node item(int index) {
		if (index >= this.size()) return null;
		return (Node)this.get(index);
	}
}

