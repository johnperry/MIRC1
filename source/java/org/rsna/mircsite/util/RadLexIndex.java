/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import jdbm.RecordManagerOptions;
import jdbm.btree.BTree;
import jdbm.helper.FastIterator;
import jdbm.helper.Tuple;
import jdbm.helper.TupleBrowser;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Encapsulates an index of RadLex terms.
 */
public class RadLexIndex {

	static final Logger logger = Logger.getLogger(RadLexIndex.class);

	private static RecordManager recman = null;
	private static final String indexName = "RadLexIndex";
	private static final String xmlName = "radlex.xml";
	private static final String radlexTreeName = "radlex";
	private static BTree index = null;

	/**
	 * Load the RadLex index from the JDBM files,
	 * creating the JDBM files if necessary.
	 */
	public static synchronized void loadIndex(File dir) {
		if (recman == null) {
			try {
				File indexFile = new File(dir, indexName);
				recman = getRecordManager(indexFile.getAbsolutePath());
				index = getBTree(recman, radlexTreeName);
				if (index.size() == 0) createIndex(dir);
			}
			catch (Exception ignore) { }
		}
	}

	/**
	 * Commit changes and close the index.
	 * No errors are reported and no operations
	 * are available after this call without calling
	 * loadIndex.
	 */
	public static synchronized void close() {
		if (recman != null) {
			try {
				recman.commit();
				recman.close();
				recman = null;
				index = null;
			}
			catch (Exception ignore) { }
		}
	}

	/**
	 * Get the array of terms indexed by a specified key.
	 * @param key the first word of the term
	 * @return the array of terms which have the specified key,
	 * arranged order from longest to shortest, or null if
	 * no term exists for the specified key.
	 */
	public static synchronized Term[] getTerms(String key) {
		if (index != null) {
			try { return (Term[])index.find(key.toLowerCase()); }
			catch (Exception ex) { }
		}
		return null;
	}

	/**
	 * Get an XML Element containing all the terms
	 * in the index which start with a word which starts
	 * with the supplied string.
	 * @param keyString the beginning of the first word
	 * of the matching terms.
	 * @return an XML element containing suggested terms
	 * matching the keyString.
	 */
	public static synchronized Element getSuggestedTerms(String keyString) {
		if (index != null) {
			Tuple tuple = new Tuple();
			keyString = keyString.toLowerCase();
			try {
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				dbf.setNamespaceAware(true);
				DocumentBuilder db = dbf.newDocumentBuilder();
				Document doc = db.newDocument();
				Element root = doc.createElement("RadLexTerms");
				doc.appendChild(root);
				TupleBrowser browser = index.browse(keyString);
				while ( browser.getNext(tuple) && ((String)tuple.getKey()).startsWith(keyString) ) {
					Term[] terms = (Term[])tuple.getValue();
					for (int i=0; i<terms.length; i++) {
						Element term = doc.createElement("term");
						term.setAttribute("id", terms[i].id);
						term.appendChild(doc.createTextNode(terms[i].text));
						root.appendChild(term);
					}
				}
				return root;
			}
			catch (Exception ex) { }
		}
		return null;
	}

	//Get a RecordManager
	private static RecordManager getRecordManager(String filename) throws Exception {
		Properties props = new Properties();
		props.put( RecordManagerOptions.THREAD_SAFE, "true" );
		return RecordManagerFactory.createRecordManager( filename, props );
	}

	//Get a named BTree, or create it if it doesn't exist.
	private static BTree getBTree(RecordManager recman, String name) throws Exception {
		BTree index = null;
		long recid = recman.getNamedObject(name);
		if ( recid != 0 )
			index = BTree.load( recman, recid );
		else {
			index = BTree.createInstance( recman, new StringComparator() );
			recman.setNamedObject( name, index.getRecid() );
			recman.commit();
		}
		return index;
	}

	/**
	 * Create the RadLex index JDBM files from the radlex.xml file.
	 * @param dir the directory in which to create the RadLex index.
	 * This must be the same directory in which the radlex.xml file
	 * is located.
	 */
	public static synchronized void createIndex(File dir) {
		File indexFile = new File(dir, indexName);
		String filename = indexFile.getAbsolutePath();

		//First close and delete the existing database if it is present
		close();
		(new File(filename + ".db")).delete();
		(new File(filename + ".lg")).delete();

		try {
			//Now get a new Record manager and create the (empty) index.
			recman = getRecordManager(filename);
			index = getBTree(recman, radlexTreeName);

			//Parse the XML file
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware(true);
			DocumentBuilder db = dbf.newDocumentBuilder();
			File xmlFile = new File(dir, xmlName);
			Document radlex = db.parse(xmlFile);

			//Put the terms in the index
			Element root = radlex.getDocumentElement();
			Node child = root.getFirstChild();
			while (child != null) {
				if ((child.getNodeType() == Node.ELEMENT_NODE) && child.getNodeName().equals("term")) {
					Element term = (Element)child;
					String id = term.getAttribute("id");
					Node name = term.getFirstChild();
					while (name != null) {
						if ((name.getNodeType() == Node.ELEMENT_NODE) && name.getNodeName().equals("name")) {
							String nameString = name.getTextContent();
							Term t = new Term(id, nameString);
							addTerm(t);
							break;
						}
						name = name.getNextSibling();
					}
				}
				child = child.getNextSibling();
			}
		}
		catch (Exception quit) { }
		finally {
			try {
				//Commit and close the database to force the database into a clean state.
				close();

				//Now reopen everything
				recman = getRecordManager(filename);
				index = getBTree(recman, radlexTreeName);
			}
			catch (Exception ignore) { }
		}
	}

	//Add a term to the index
	private static void addTerm(Term term) {
		try {
			String key = term.getKey();
			Term[] terms = (Term[])index.find(key);
			if (terms == null) {
				terms = new Term[] { term };
				index.insert(key, terms, true);
			}
			else {
				Term[] more = new Term[ terms.length + 1 ];
				for (int i=0; i<terms.length; i++) more[i] = terms[i];
				more[ terms.length ] = term;
				Arrays.sort(more);
				index.insert(key, more, true);
			}
		}
		catch (Exception skip) { }
	}

	static class StringComparator  implements Comparator<String>, Serializable {
		public static final long serialVersionUID = 1;
		public StringComparator() { }
		public int compare( String s1, String s2 ) {
			return s1.compareTo(s2);
		}
		public boolean equals(Object obj) {
			return this.equals(obj);
		}
	}

}
