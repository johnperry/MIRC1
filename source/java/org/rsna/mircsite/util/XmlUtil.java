/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.dom.DOMResult;
import org.xml.sax.InputSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Encapsulates static methods for working with XML objects.
 */
public class XmlUtil {

	/**
	 * Parses an XML string.
	 * @param xmlString the file containing the XML to parse.
	 * @return the XML DOM document.
	 */
	public static Document getDocumentFromString(String xmlString) throws Exception {
		StringReader sr = new StringReader(xmlString);
		DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		return db.parse(new InputSource(sr));
	}

	/**
	 * Parses an XML file.
	 * @param realFilePath the path to the file containing the XML to parse.
	 * @return the XML DOM document.
	 */
	public static Document getDocument(String realFilePath) throws Exception {
		File file = new File(realFilePath);
		DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		return db.parse(file);
	}

	/**
	 * Parses an XML file.
	 * @param file the file containing the XML to parse.
	 * @return the XML DOM document.
	 */
	public static Document getDocument(File file) throws Exception {
		DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		return db.parse(file);
	}

	/**
	 * Creates a new empty XML DOM document.
	 * @return the XML DOM document.
	 */
	public static Document getDocument() throws Exception {
		DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		return db.newDocument();
	}

	/**
	 * Parses an XML file and returns the name of the document element.
	 * @param realFilePath the path to the file containing the XML to parse.
	 * @return the name of the document element, or null if the file does not parse.
	 */
	public static String getDocumentElementName(String realFilePath) {
		return getDocumentElementName(new File(realFilePath));
	}

	/**
	 * Parses an XML file and returns the name of the document element.
	 * @param file the file containing the XML to parse.
	 * @return the name of the document element, or null if the file does not parse.
	 */
	public static String getDocumentElementName(File file) {
		try {
			DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document d = db.parse(file);
			Element root = d.getDocumentElement();
			return root.getTagName();
		}
		catch (Exception e) { return null; }
	}

	/**
	 * Transforms an XML file using an XSL file and no parameters.
	 * @param docFile the file containing the XML to transform.
	 * @param xslFile the file containing the XSL transformation program.
	 * @return the transformed text.
	 */
	public static String getTransformedText(File docFile, File xslFile) throws Exception {
		String[] nullString = {};
		return getTransformedText(new StreamSource(docFile), new StreamSource(xslFile), nullString);
	}

	/**
	 * Transforms an XML file using an XSL file and an array of parameters.
	 * The parameter array consists of a sequence of pairs of (String parametername)
	 * followed by (Object parametervalue) in an Object[].
	 * @param docFile the file containing the XML to transform.
	 * @param xslFile the file containing the XSL transformation program.
	 * @param params the array of transformation parameters.
	 * @return the transformed text.
	 */
	public static String getTransformedText(File docFile, File xslFile, Object[] params) throws Exception {
		return getTransformedText(new StreamSource(docFile), new StreamSource(xslFile), params);
	}

	/**
	 * Transforms an XML DOM Document using an XSL file and no parameters.
	 * @param document the XML DOM Document to transform.
	 * @param xslFile the file containing the XSL transformation program.
	 * @return the transformed text.
	 */
	public static String getTransformedText(Document document, File xslFile) throws Exception {
		String[] nullString = {};
		return getTransformedText(new DOMSource(document), new StreamSource(xslFile), nullString);
	}

	/**
	 * Transforms an XML DOM Document using an XSL file and an array of parameters.
	 * The parameter array consists of a sequence of pairs of (String parametername)
	 * followed by (Object parametervalue) in an Object[].
	 * @param document the XML DOM Document to transform.
	 * @param xslFile the file containing the XSL transformation program.
	 * @param params the array of transformation parameters.
	 * @return the transformed text.
	 */
	public static String getTransformedText(Document document, File xslFile, Object[] params) throws Exception {
		return getTransformedText(new DOMSource(document), new StreamSource(xslFile), params);
	}

	/**
	 * Transforms an XML string using an XSL file and no parameters.
	 * @param xmlString the XML string to transform.
	 * @param xslFile the file containing the XSL transformation program.
	 * @return the transformed text.
	 */
	public static String getTransformedText(String xmlString, File xslFile) throws Exception {
		String[] nullString = {};
		StringReader sr = new StringReader(xmlString);
		return getTransformedText(new StreamSource(sr), new StreamSource(xslFile), nullString);
	}

	/**
	 * Transforms an XML string using an XSL file and an array of parameters.
	 * The parameter array consists of a sequence of pairs of (String parametername)
	 * followed by (Object parametervalue) in an Object[].
	 * @param xmlString the XML string to transform.
	 * @param xslFile the file containing the XSL transformation program.
	 * @param params the array of transformation parameters.
	 * @return the transformed text.
	 */
	public static String getTransformedText(String xmlString, File xslFile, Object[] params) throws Exception {
		StringReader sr = new StringReader(xmlString);
		return getTransformedText(new StreamSource(sr), new StreamSource(xslFile), params);
	}

	/**
	 * Transforms an XML DOM Document using an XSL string and no parameters.
	 * @param document the XML DOM Document to transform.
	 * @param xslString the string containing the XSL transformation program.
	 * @return the transformed text.
	 */
	public static String getTransformedText(Document document, String xslString) throws Exception {
		String[] nullString = {};
		StringReader sr = new StringReader(xslString);
		return getTransformedText(new DOMSource(document), new StreamSource(sr), nullString);
	}

	/**
	 * Transforms an XML DOM Document using an XSL string and an array of parameters.
	 * The parameter array consists of a sequence of pairs of (String parametername)
	 * followed by (Object parametervalue) in an Object[].
	 * @param document the XML DOM Document to transform.
	 * @param xslString the string containing the XSL transformation program.
	 * @param params the array of transformation parameters.
	 * @return the transformed text.
	 */
	public static String getTransformedText(Document document, String xslString, Object[] params) throws Exception {
		StringReader sr = new StringReader(xslString);
		return getTransformedText(new DOMSource(document), new StreamSource(sr), params);
	}

	/**
	 * General method for transformation to text. Transforms a Source
	 * document using a Source XSL document and an array of parameters.
	 * The parameter array consists of a sequence of pairs of (String parametername)
	 * followed by (Object parametervalue) in an Object[].
	 * @param doc the Source XML document to transform.
	 * @param xsl the Source XSL transformation program.
	 * @param params the array of transformation parameters.
	 * @return the transformed text.
	 */
	public static String getTransformedText(Source doc, Source xsl, Object[] params) throws Exception {
		TransformerFactory tFactory = TransformerFactory.newInstance();
		Transformer transformer = tFactory.newTransformer(xsl);
		if ((params != null) && (params.length > 1)) {
			for (int i=0; i<params.length; i=i+2) {
				transformer.setParameter((String)params[i],params[i+1]);
			}
		}
		StringWriter sw = new StringWriter();
		transformer.transform(doc, new StreamResult(sw));
		return sw.toString();
	}

	/**
	 * Transforms an XML file using an XSL file and no parameters.
	 * @param docFile the file containing the XML to transform.
	 * @param xslFile the file containing the XSL transformation program.
	 * @return the transformed DOM Document.
	 */
	public static Document getTransformedDocument(File docFile, File xslFile) throws Exception {
		String[] nullString = {};
		return getTransformedDocument(new StreamSource(docFile), new StreamSource(xslFile), nullString);
	}

	/**
	 * Transforms an XML file using an XSL file and an array of parameters.
	 * The parameter array consists of a sequence of pairs of (String parametername)
	 * followed by (Object parametervalue) in an Object[].
	 * @param docFile the file containing the XML to transform.
	 * @param xslFile the file containing the XSL transformation program.
	 * @param params the array of transformation parameters.
	 * @return the transformed DOM Document.
	 */
	public static Document getTransformedDocument(File docFile, File xslFile, Object[] params) throws Exception {
		return getTransformedDocument(new StreamSource(docFile), new StreamSource(xslFile), params);
	}

	/**
	 * Transforms an XML DOM Document using an XSL file and an array of parameters.
	 * The parameter array consists of a sequence of pairs of (String parametername)
	 * followed by (Object parametervalue) in an Object[].
	 * @param doc the Document to transform.
	 * @param xslFile the file containing the XSL transformation program.
	 * @param params the array of transformation parameters.
	 * @return the transformed DOM Document.
	 */
	public static Document getTransformedDocument(Document doc, File xslFile, Object[] params) throws Exception {
		return getTransformedDocument(new DOMSource(doc), new StreamSource(xslFile), params);
	}

	/**
	 * Transforms an XML file using an XSL DOM Document and an array of parameters.
	 * The parameter array consists of a sequence of pairs of (String parametername)
	 * followed by (Object parametervalue) in an Object[].
	 * @param docFile the file containing the XML to transform.
	 * @param xslFile the file containing the XSL transformation program.
	 * @param params the array of transformation parameters.
	 * @return the transformed DOM Document.
	 */
	public static Document getTransformedDocument(File docFile, Document xslFile, Object[] params) throws Exception {
		return getTransformedDocument(new StreamSource(docFile), new DOMSource(xslFile), params);
	}

	/**
	 * General method for transformation to a DOM Document. Transforms a Source
	 * document using a Source XSL document and an array of parameters.
	 * The parameter array consists of a sequence of pairs of (String parametername)
	 * followed by (Object parametervalue) in an Object[].
	 * @param doc the Source XML document to transform.
	 * @param xsl the Source XSL transformation program.
	 * @param params the array of transformation parameters.
	 * @return the transformed text.
	 */
	public static Document getTransformedDocument(Source doc, Source xsl, Object[] params) throws Exception {
		TransformerFactory tFactory = TransformerFactory.newInstance();
		Transformer transformer = tFactory.newTransformer(xsl);
		if ((params != null) && (params.length > 1)) {
			for (int i=0; i<params.length; i=i+2) {
				transformer.setParameter((String)params[i],params[i+1]);
			}
		}
		DOMResult domResult = new DOMResult();
		transformer.transform(doc,domResult);
		return (Document) domResult.getNode();
	}

	/**
	 * Count the number of nodes in a Node tree with
	 * a specified name. If the node is a Document,
	 * use the document element as the starting point.
	 * @param node the top of the tree to search.
	 * @param name the name of the nodes to count.
	 * @return the number of nodes with the specified name.
	 */
	public static int getNamedNodeCount(Node node, String name) {
		try {
			if (node instanceof Document) node = ((Document)node).getDocumentElement();
			if (node instanceof Element) {
				NodeList nodeList = ((Element)node).getElementsByTagName(name);
				return nodeList.getLength();
			}
			return 0;
		}
		catch (Exception e) {return 0;}
	}

	/**
	 * Get an array of specific attributes for specific elements
	 * in a Node tree. If the node is a Document, use the document
	 * element as the starting point.
	 * @param node the top of the tree to search.
	 * @param nodeName the element names to include in the search.
	 * @param attrName the name of the attributes to include.
	 * @return the array of attribute values natching the criteria.
	 */
	public static String[] getAttributeValues(Node node, String nodeName, String attrName) {
		if (node instanceof Document) node = ((Document)node).getDocumentElement();
		if (!(node instanceof Element)) return new String[0];
		NodeList nodeList = ((Element)node).getElementsByTagName(nodeName);
		ArrayList list = new ArrayList();
		for (int i=0; i<nodeList.getLength(); i++) {
			Attr attr = ((Element)nodeList.item(i)).getAttributeNode(attrName);
			if (attr != null) list.add(attr.getValue());
		}
		String[] values = new String[list.size()];
		return (String[])list.toArray(values);
	}

	/**
	 * Get an element specified by a starting node and a path string.
	 * If the starting node is a Document, use the document element
	 * as the starting point.
	 * A path to an element has the form: elem1/.../elemN
	 * @param node the top of the tree to search.
	 * @param path the path from the top of the tree to the desired element.
	 * @return the first element matching the path, or null if no element
	 * exists at the path location or if the starting node is not an element.
	 */
	public static Element getElementViaPath(Node node, String path) {
		if (node instanceof Document) node = ((Document)node).getDocumentElement();
		if (!(node instanceof Element)) return null;
		int k = path.indexOf("/");
		String firstPathElement = path;
		if (k > 0) firstPathElement = path.substring(0,k);
		if (node.getNodeName().equals(firstPathElement)) {
			if (k < 0) return (Element)node;
			path = path.substring(k+1);
			NodeList nodeList = ((Element)node).getChildNodes();
			Node n;
			for (int i=0; i<nodeList.getLength(); i++) {
				n = nodeList.item(i);
				if ((n instanceof Element) && ((n = getElementViaPath(n,path)) != null))
					return (Element)n;
			}
		}
		return null;
	}

	/**
	 * Get the value of a node specified by a starting node and a
	 * path string. If the starting node is a Document, use the
	 * document element as the starting point.
	 * A path to an element has the form: elem1/.../elemN
	 * A path to an attribute has the form: elem1/.../elemN@attr
	 * The value of an element node is the sum of all the element's
	 * first generation child text nodes. Note that this is not what you
	 * would get from a mixed element in an XSL program.
	 * @param node the top of the tree to search.
	 * @param path the path from the top of the tree to the desired node.
	 * @return the value of the first node matching the path, or the
	 * empty string if no node exists at the path location or if the
	 * starting node is not an element.
	 */
	public static String getValueViaPath(Node node, String path) {
		if (node instanceof Document) node = ((Document)node).getDocumentElement();
		if (!(node instanceof Element)) return "";
		path = path.trim();
		int kAtsign = path.indexOf("@");

		//If the target is an element, get the element's value.
		if (kAtsign == -1) {
			Element target = getElementViaPath(node,path);
			if (target == null) return "";
			return getElementValue(target);
		}

		//The target is an attribute; first find the element.
		String subpath = path.substring(0,kAtsign);
		Element target = getElementViaPath(node,subpath);
		if (target == null) return null;
		String name = path.substring(kAtsign+1);
		return target.getAttribute(name);
	}

	/**
	 * Get the value of an element. If the node is a Document, use the
	 * document element as the element node.
	 * The value of an element node is the sum of all the element's
	 * first generation child text nodes. Note that this is not what you
	 * would get from a mixed element in an XSL program.
	 * @param node the node.
	 * @return the value of the element, or an empty string if the
	 * node is not an element.
	 */
	public static String getElementValue(Node node) {
		if (node instanceof Document) node = ((Document)node).getDocumentElement();
		if (!(node instanceof Element)) return "";
		NodeList nodeList = node.getChildNodes();
		String value = "";
		for (int i=0; i<nodeList.getLength(); i++) {
			Node n = nodeList.item(i);
			if (n.getNodeType() == Node.TEXT_NODE) value += n.getNodeValue();
		}
		return value;
	}

	/**
	 * Determine whether an element has any child elements.
	 * @param element the element to check.
	 * @return true if the element has child elements; false otherwise.
	 */
	public static boolean hasChildElements(Element element) {
		Node child = element.getFirstChild();
		while (child != null) {
			if (child.getNodeType() == Node.ELEMENT_NODE) return true;
			child = child.getNextSibling();
		}
		return false;
	}

	/**
	 * Get the value of a node's attribute. If the node is a Document, use the
	 * document element as the element node.
	 * @param node the node.
	 * @param name the attribute.
	 * @return the value of the attribute, or an empty string if the
	 * node is not an element or the attribute is missing.
	 */
	public static String getAttributeValue(Node node, String name) {
		if (node instanceof Document) node = ((Document)node).getDocumentElement();
		if (!(node instanceof Element)) return "";
		return ((Element)node).getAttribute(name);
	}

	/**
	 * Get the XML text corresponding to an XML DOM node and its children.
	 * @param node the node to convert to a string.
	 * @return the XML string for the node.
	 */
	public static String toString(Node node) {
		StringWriter sw = new StringWriter();
		renderNode(sw,node);
		return sw.toString();
	}

	//Recursively walk the tree and write the nodes to a StringWriter.
	private static void renderNode(StringWriter sw, Node node) {
		switch (node.getNodeType()) {

			case Node.DOCUMENT_NODE:
				sw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
				NodeList nodes = node.getChildNodes();
				if (nodes != null) {
					for (int i=0; i<nodes.getLength(); i++) {
						renderNode(sw,nodes.item(i));
					}
				}
				break;

			case Node.ELEMENT_NODE:
				String name = node.getNodeName();
				NamedNodeMap attributes = node.getAttributes();
				if (attributes.getLength() == 0) {
					sw.write("<" + name + ">");
				}
				else {
					sw.write("<" + name + " ");
					int attrlen = attributes.getLength();
					for (int i=0; i<attrlen; i++) {
						Node current = attributes.item(i);
						sw.write(current.getNodeName() + "=\"" +
							XmlStringUtil.escapeChars(current.getNodeValue()));
						if (i < attrlen-1)
							sw.write("\" ");
						else
							sw.write("\">");
					}
				}
				NodeList children = node.getChildNodes();
				if (children != null) {
					for (int i=0; i<children.getLength(); i++) {
						renderNode(sw,children.item(i));
					}
				}
				sw.write("</" + name + ">");
				break;

			case Node.TEXT_NODE:
			case Node.CDATA_SECTION_NODE:
				sw.write(XmlStringUtil.escapeChars(node.getNodeValue()));
				break;

			case Node.PROCESSING_INSTRUCTION_NODE:
				sw.write("<?" + node.getNodeName() + " " +
					XmlStringUtil.escapeChars(node.getNodeValue()) + "?>");
				break;

			case Node.ENTITY_REFERENCE_NODE:
				sw.write("&" + node.getNodeName() + ";");
				break;

			case Node.DOCUMENT_TYPE_NODE:
				// Ignore document type nodes
				break;
		}
		return;
	}

}
