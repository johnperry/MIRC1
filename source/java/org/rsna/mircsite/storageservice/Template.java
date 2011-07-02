/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.storageservice;

import java.io.File;
import org.rsna.mircsite.dicomservice.TrialConfig;
import org.rsna.mircsite.util.*;
import org.apache.log4j.Logger;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
  * A class to encapsulate a a static method for creating a
  * MIRCdocument XML string from a template file and the contents
  * of a DicomObject.
  */
public class Template {

	static final Logger logger = Logger.getLogger(Template.class);

	/**
	 * Create a MIRCdocument XML string by parsing a file. This method
	 * does no additional processing on the text.
	 * @param mircDocument the file containing the MIRCdocument XML string.
	 */
	public static String getText(File mircDocument) {
		try {
			Document templateXML;
			//Parse the MIRCdocument file to catch any problems,
			//then just return the file text.
			templateXML = XmlUtil.getDocument(mircDocument);
			return FileUtil.getFileText(mircDocument);
		}
		catch (Exception e) { return null; }
	}

	/**
	 * Create a MIRCdocument XML string by parsing the clinical
	 * trial template file. If the FileObject is an instance of
	 * a DicomObject, insert data from the DicomObject where
	 * called for in the template.
	 * @param fileObject the FileObject to be used to create
	 * the XML string.
	 */
	public static String getText(FileObject fileObject) {
		File mircDocument = new File(StorageConfig.basepath + TrialConfig.templateFilename);
		if (!(fileObject instanceof DicomObject)) return getText(mircDocument);
		return getText(mircDocument,fileObject);
	}

	/**
	 * Update a MIRCdocument XML string by parsing the string to obtain a
	 * MIRCdocument. If the MIRCdocument contains insertion elements,
	 * insert data from the DicomObject where called for in the MIRCdocument.
	 * @param mircDocument the MIRCdocument XML string.
	 * @param dicomObject the object to be used to update the MIRCdocument.
	 */
	public static String getText(String mircDocument, DicomObject dicomObject) {

		//Don't insert elements from anything but images.
		if (!dicomObject.isImage()) return mircDocument;

		//It's an image; process the document.
		try {
			//get the mircDocument DOM Document
			Document templateXML;
			templateXML = XmlUtil.getDocumentFromString(mircDocument);
			Element root = templateXML.getDocumentElement();

			if (checkTree(root)) {
				//This MIRCdocument has not been loaded with values from the
				//first non-manifest DicomObject, so we have to process the
				//XML object.
				return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
					+ getElementText(root,dicomObject);
			}
		}
		catch (Exception e) { }
		return mircDocument;
	}

	/**
	 * Get a MIRCdocument XML string by parsing an existing
	 * MIRCdocument. If the FileObject is an instance of a DicomObject,
	 * and the document contains insertion elements (indicating that it
	 * has not yet received a DicomObject), insert data from the DicomObject
	 * where called for in the MIRCdocument.
	 * @param mircDocument the file containing the MIRCdocument XML string.
	 * @param fileObject the FileObject to be used to update
	 * the MIRCdocument.
	 */
	public static String getText(File mircDocument, FileObject fileObject) {
		if (!(fileObject instanceof DicomObject)) return getText(mircDocument);
		DicomObject dicomObject = (DicomObject)fileObject;
		try {
			//get the mircDocument file
			Document templateXML;
			templateXML = XmlUtil.getDocument(mircDocument);
			Element root = templateXML.getDocumentElement();

			if (dicomObject.isManifest()) {
				//This is a TCE manifest. All we do for this object is to
				//set the title, author, abstract, and notes sections.
				//We leave all the other elements intact so they can be
				//processed when an image object is received.
				return insertManifestText(root,dicomObject);
			}
			else if (dicomObject.isRawData()) {
				if (checkTree(root)) {
					//This MIRCdocument has not been loaded with values from the
					//first non-manifest DicomObject, so we have to process the
					//XML object.
					return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
								+ getElementText(root,dicomObject);
				}
				else return FileUtil.getFileText(mircDocument);
			}
			else if (!dicomObject.isImage()) {
				//This is not an image, so we should leave all the elements
				//in the MIRCdocument intact so they can be processed when an
				//image has been received. If we ever want to process KOS or SR
				//objects, we should do it here.
				return getText(mircDocument);
			}
			else if (checkTree(root)) {
				//This MIRCdocument has not been loaded with values from the
				//first non-manifest DicomObject, so we have to process the
				//XML object.
				return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
					+ getElementText(root,dicomObject);
			}
			else {
				//There is nothing to process, just return the document text.
				return FileUtil.getFileText(mircDocument);
			}
		}
		catch (Exception e) { return null; }
	}

	//Walk the tree from a specified element and return true if
	//a DICOM tag element is found (<g...e.../>), indicating
	//that no DicomObjects have yet been inserted in the document.
	private static boolean checkTree(Element element) {
		if (dicomTag(element)) return true;
		NodeList nodeList = element.getChildNodes();
		Node node;
		for (int i=0; i<nodeList.getLength(); i++) {
			node = nodeList.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				if (checkTree((Element)node)) return true;
			}
		}
		return false;
	}

	//Insert specific information from a TCE manifest into a document.
	//This method must be called with the root element.
	private static String insertManifestText(Element root, DicomObject manifest) {
		//setChildElementText(root,"title","The Title");
		return XmlUtil.toString(root.getOwnerDocument());
	}

	//Walk the tree from a specified element,
	//inserting data from a DicomObject where required.
	private static String getElementText(Element element, DicomObject dicomObject) {

		if (dicomTag(element))
			return getDicomElementText(element,dicomObject);

		if (element.getTagName().equals("block"))
			return getBlockText(element,dicomObject);

		StringBuffer sb = new StringBuffer();
		sb.append("<" + element.getTagName());
		NamedNodeMap attributes = element.getAttributes();
		Attr attr;
		for (int i=0; i<attributes.getLength(); i++) {
			attr = (Attr)attributes.item(i);
			String attrValue = attr.getValue().trim();
			if (dicomTag(attrValue)) {
				attrValue = getDicomElementText(attrValue,dicomObject);
			}
			sb.append(" " + attr.getName() + "=\"" + attrValue + "\"");
		}
		sb.append(">");
		if (element.getTagName().equals("table"))
			sb.append(getTableText(element,dicomObject));
		else if (element.getTagName().equals("publication-date"))
			sb.append(StringUtil.getDate());
		else
			sb.append(getChildrenText(element,dicomObject));
		sb.append("</" + element.getTagName() + ">");
		return sb.toString();
	}

	//Return all the text from the child nodes of an element,
	//inserting data from the DicomObject where required.
	private static String getChildrenText(Element element, DicomObject dicomObject) {
		StringBuffer sb = new StringBuffer();
		Node node;
		NodeList nodeList = element.getChildNodes();
		for (int i=0; i<nodeList.getLength(); i++) {
			node = nodeList.item(i);
			if (node.getNodeType() == Node.TEXT_NODE) {
				sb.append(node.getNodeValue());
			}
			else if (node.getNodeType() == Node.ELEMENT_NODE) {
				sb.append(getElementText((Element)node, dicomObject));
			}
		}
		return sb.toString();
	}

	//Process a block element, inserting its contents before the element itself,
	//allowing multiple instances of the element to be created, one for each DicomObject.
	private static String getBlockText(Element element, DicomObject dicomObject) {
		StringBuffer sb = new StringBuffer();
		sb.append(getChildrenText(element,dicomObject));
		sb.append(XmlUtil.toString(element));
		return sb.toString();
	}

	//Handle HTML table elements specially, automatically
	//inserting the DICOM element name and the value for each child element.
	private static String getTableText(Element element, DicomObject dicomObject) {
		StringBuffer sb = new StringBuffer();
		Node node;
		Element elem;
		NodeList nodeList = element.getChildNodes();
		for (int i=0; i<nodeList.getLength(); i++) {
			node = nodeList.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				elem = (Element)node;
				if (dicomTag(elem)) {
					sb.append("\n<tr>");
					sb.append("<td width=\"200\">" + getDicomElementName(elem) + "</td>");
					sb.append("<td>" + getDicomElementText(elem,dicomObject) + "</td>");
					sb.append("</tr>");
				}
				else sb.append(getElementText(elem,dicomObject));
			}
		}
		return sb.toString();
	}

	//Determine whether the argument is a
	//DICOM tag instruction, calling for the insertion
	//of the value of the element. The DICOM tag instruction
	//has the form <GxxxxEyyyy>. There can optionally be
	//attributes, but they are ignored. The name is not
	//case-sensitive.
	private static boolean dicomTag(Element element) {
		return element.getTagName().matches("[gG][0-9a-fA-F]{4}[eE][0-9a-fA-F]{4}.*");
	}

	//The same method for checking an attribute value for
	//the form: @GxxxxEyyyy
	private static boolean dicomTag(String name) {
		return name.matches("@[gG][0-9a-fA-F]{4}[eE][0-9a-fA-F]{4}.*");
	}

	//Create the tag int from the name of the
	//DICOM tag instruction.
	private static int getTag(Element element) {
		if (dicomTag(element)) {
			String s = element.getTagName();
			s = s.substring(1,5) + s.substring(6,10);
			try { return Integer.parseInt(s,16); }
			catch (Exception e) { }
		}
		return -1;
	}

	//Create the tag int from an attribute value as described in the
	//dicomTag(String) function..
	private static int getTag(String name) {
		if (dicomTag(name)) {
			name = name.substring(2,6) + name.substring(7,11);
			try { return Integer.parseInt(name,16); }
			catch (Exception e) { }
		}
		return -1;
	}

	//Get the dcm4che name of a DICOM element,
	//given the DICOM tag instruction element.
	private static String getDicomElementName(Element element) {
		int tag = getTag(element);
		if (tag == -1) return "UNKNOWN DICOM ELEMENT";
		String tagName = DicomObject.getElementName(tag);
		if (tagName != null) return tagName;
		tagName = element.getAttribute("desc");
		if (!tagName.equals("")) return tagName;
		return "UNKNOWN DICOM ELEMENT";
	}

	//Get the text value of a DICOM element in the
	//DicomObject, given the DICOM tag instruction.
	private static String getDicomElementText(Element element, DicomObject dicomObject) {
		int tag = getTag(element);
		return getDicomElementText(tag, dicomObject);
	}

	//The same function for getting the element from an attribute value
	//as determined by the dicomTag(String) function.
	private static String getDicomElementText(String name, DicomObject dicomObject) {
		int tag = getTag(name);
		return getDicomElementText(tag, dicomObject);
	}

	private static String getDicomElementText(int tag, DicomObject dicomObject) {
		if (tag == -1) return "UNKNOWN DICOM ELEMENT";
		try {
			String value = dicomObject.getElementValue(tag);
			if (value != null) return XmlStringUtil.escapeChars(value);
			return "";
		}
		catch (Exception e) { };
		return "value missing";
	}

}