/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.io.*;
import org.rsna.mircsite.util.FileUtil;
import org.rsna.mircsite.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A generic XML object for clinical trials metadata, providing
 * parsing and access to certain common elements.
 */
public class XmlObject extends FileObject {

	Document document = null;

	/**
	 * Class constructor; opens and parses an XML file.
	 * @param file the file containing the XML object.
	 * @throws Exception if the object does not parse.
	 */
	public XmlObject(File file) throws Exception {
		super(file);
		document = XmlUtil.getDocument(file);
	}

	/**
	 * Set the standard extension for an XmlObject (".xml").
	 * @return the file after modification.
	 */
	public File setStandardExtension() {
		return setExtension(".xml");
	}

	/**
	 * Get a prefix for an XmlObject ("XML-").
	 * @return a prefix for a XmlObject.
	 */
	public String getTypePrefix() {
		return "XML-";
	}

	/**
	 * Get the parsed XML DOM object.
	 * @return the document.
	 */
	public Document getDocument() {
		return document;
	}

	/**
	 * Get the document element name (the root element) of this object.
	 * @return the document element name.
	 */
	public String getDocumentElementName() {
		if (document == null) return "";
		return document.getDocumentElement().getTagName();
	}

	/**
	 * Get the UID of this object. This requires that the
	 * UID be stored as the "uid" attribute of the root element.
	 * @return the UID, or the empty string if the UID is missing.
	 */
	public String getUID() {
		if (document == null) return "";
		return document.getDocumentElement().getAttribute("uid").replaceAll("\\s","");
	}

	/**
	 * Get the description for this object. This method looks in
	 * two places to find the description element:
	 * <ol>
	 * <li>the description child element of the root element
	 * <li>the description child of the first child of the root element.
	 * </ol>
	 * @return the description, or the file name if it cannot be obtained.
	 */
	public String getDescription() {
		if (document == null) return "";
		String desc;
		Element root = document.getDocumentElement();
		String rootName = root.getTagName();
		desc = getValue(rootName + "/description");
		if (!desc.trim().equals("")) return desc;

		Node child = root.getFirstChild();
		while ((child != null) && (child.getNodeType() != Node.ELEMENT_NODE))
			child = child.getNextSibling();
		if (child == null) return file.getName();
		String path = rootName + "/" + child.getNodeName() + "/description";
		desc = getValue(path);
		if (!desc.trim().equals("")) return desc;
		return file.getName();
	}

	/**
	 * Get the study's unique identifier. This method looks in
	 * several places to find the StudyUID:
	 * <ol>
	 * <li>the study-uid attribute of the root element
	 * <li>the StudyInstanceUID attribute of the root element
	 * <li>the study-uid child element of the root element
	 * <li>the StudyInstanceUID child element of the root element
	 * <li>the study-uid child of the first child of the root element.
	 * <li>the StudyInstanceUID child of the first child of the root element.
	 * </ol>
	 * @return the study's unique identifier, if available; otherwise the empty string.
	 */
	public String getStudyUID() {
		if (document == null) return "";
		String siuid;
		Element root = document.getDocumentElement();
		String rootName = root.getTagName();
		siuid = getValue(rootName + "@study-uid");
		if (!siuid.equals("")) return siuid;
		siuid = getValue(rootName + "@StudyInstanceUID");
		if (!siuid.equals("")) return siuid;
		siuid = getValue(rootName + "/study-uid");
		if (!siuid.equals("")) return siuid;
		siuid = getValue(rootName + "/StudyInstanceUID");
		if (!siuid.equals("")) return siuid;

		Node child = root.getFirstChild();
		while ((child != null) && (child.getNodeType() != Node.ELEMENT_NODE))
			child = child.getNextSibling();
		if (child == null) return "";
		String path = rootName + "/" + child.getNodeName() + "/study-uid";
		siuid = getValue(path);
		if (!siuid.equals("")) return siuid;
		path = rootName + "/" + child.getNodeName() + "/StudyInstanceUID";
		return getValue(path);
	}

	/**
	 * Get the value of the node identified by a path. This method is
	 * a convenience method that can only be relied on if the path to the
	 * node can be found by taking the first element matching each step of
	 * the path.
	 * @param path the path from the root element to the node. Note that this
	 * method uses the XmlUtil.getValueViaPath(String) method, and the path
	 * syntax is slightly different from that of XPath. Specifically,
	 * attributes are identified as "node@attribute" rather than "node/@attribute".
	 * @return the value, or the empty string if the node identified by the path is missing.
	 */
	public String getValue(String path) {
		if (document == null) return "";
		return XmlUtil.getValueViaPath(document.getDocumentElement(),path);
	}

	/**
	 * Get the text of this object.
	 * @return the complete text of the file.
	 */
	public String getText() {
		if (document == null) return "";
		return FileUtil.getFileText(file);
	}

}
