/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.storageservice;

import java.awt.Dimension;
import java.io.*;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Properties;
import org.apache.log4j.Logger;
import org.rsna.mircsite.anonymizer.DicomAnonymizer;
import org.rsna.mircsite.anonymizer.LocalRemapper;
import org.rsna.mircsite.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

/**
  * A class to encapsulate a MIRCdocument. This class is used by both the
  * Author Service and the Dicom Service to create MIRCdocuments and to
  * insert images and other files into them.
  */
public class MircDocument {

	static final Logger logger = Logger.getLogger(MircDocument.class);

	static final SkipElements skip = new SkipElements();

	public File docDir;
	public File docFile;
	public String docText;
	public String indexEntry;
	private int jpegQuality = -1;

	/**
	 * Class constructor; creates a new MircDocument object. If the
	 * referenced document file exists, it is loaded.
	 * @param docFilename pointing to the MircDocument to be loaded.
	 * @throws IOException if the document cannot be found.
	 */
	public MircDocument(String docFilename, String indexEntry) throws IOException {
		this(new File(docFilename),indexEntry);
	}

	/**
	 * Class constructor; creates a new MircDocument object. If the
	 * referenced document file exists, it is loaded.
	 * @param docFile pointing to the MircDocument to be loaded.
	 * @throws IOException if the document cannot be found.
	 */
	public MircDocument(File docFile, String indexEntry) throws IOException {
		this.docFile = docFile;
		if (!docFile.exists())
			throw new IOException(docFile + " could not be found.");
		docDir = docFile.getParentFile();
		docText = FileUtil.getFileText(docFile);
		this.indexEntry = indexEntry;
		jpegQuality = StorageConfig.getJPEGQuality();
	}

	/**
	 * Class constructor used by the Dicom Service; creates a new MircDocument to
	 * contain a FileObject. The FileObject defines the directory name
	 * (StudyInstanceUID) and document name (MIRCdocument). If the document
	 * exists, it is loaded; if not, the document name StudyInstanceUID is tried.
	 * for compatibility with previous releases. If the document does not exist, it is
	 * created from the template. The FileObject is not used for any other purpose in the constructor.
	 * @param fileObject the FileObject that defines the MircDocument's directory and filename.
	 * @throws Exception if the document cannot be created for any reason, including that the
	 * FileObject does not contain a StudyInstanceUID.
	 */
	public MircDocument(FileObject fileObject) throws Exception {

		//The Study Instance UID will be the directory name.
		String siUID = fileObject.getStudyUID();
		if ((siUID == null) || siUID.equals("")) throw new Exception("missing StudyInstanceUID");

		//Make a file pointing to the directory where the document should be stored
		String docDirString =
			StorageConfig.basepath + "documents" + File.separator + siUID + File.separator;
		docDir = new File(docDirString);

		//Create the documents and/or siUID directories in case they don't exist
		docDir.mkdirs();

		//Make a File for the MIRCdocument.
		//Make one for "MIRCdocument, and one for StudyInstanceUID.
		docFile = new File(docDir, "MIRCdocument.xml");
		File altFile = new File(docDir, siUID+".xml");

		//If the siUID.xml file exists, get the file contents,
		//else make a new document from the template.
		if (docFile.exists())
			docText = Template.getText(docFile,fileObject);
		else if (altFile.exists()) {
			docFile = altFile;
			docText = Template.getText(docFile,fileObject);
		}
		else
			docText = Template.getText(fileObject);
		if (docText == null) throw new Exception("Unable to produce MIRCdocument template");

		//Make the string for the indexEntry.
		indexEntry = "documents/" + siUID + "/" + docFile.getName();

		//And get the JPEG quality setting.
		jpegQuality = StorageConfig.getJPEGQuality();
	}

	/**
	 * Save the current document text.
	 */
	public void save() {
		FileUtil.setFileText(docFile, docText);
	}

	/**
	 * Insert key elements into the MircDocument.
	 * This method is used by the Zip Service.
	 * It does not re-index the document.
	 * @param title the title of the document.
	 * @param name the author's name.
	 * @param affiliation the author's affiliation.
	 * @param contact the author's contact information.
	 * @param abstracttext the abstract of the document.
	 * @param keywords the keywords.
	 * @param owner the username of the owner of the document.
	 * @param read the read privilege for the document.
	 * @param update the update privilege for the document.
	 * @param export the export privilege for the document.
	 * @param overwriteTemplate true if supplied parameters are
	 * to overwrite the values in the template; false if the template
	 * parameters are not to be overwritten. Note: the name,
	 * affiliation, contact, and username are always overwritten,
	 * since the owner must be the user who is creating the document.
	 * @return true if the operation succeeded, false otherwise;
	 */
	public synchronized boolean insert(
				String title,
				String name,
				String affiliation,
				String contact,
				String abstracttext,
				String keywords,
				String owner,
				String read,
				String update,
				String export,
				boolean overwriteTemplate
				) {

		//Do this in XML rather than text.
		Document xml;
		try { xml = XmlUtil.getDocumentFromString(docText); }
		catch (Exception ex) { return false; }
		Element root = xml.getDocumentElement();

		if (!title.trim().equals("") && overwriteTemplate)
			replaceElementValue(root,"title",title);
		if (!name.trim().equals(""))
			replaceElementValue(root,"author/name",name);
		if (!affiliation.trim().equals(""))
			replaceElementValue(root,"author/affiliation",affiliation);
		if (!contact.trim().equals(""))
			replaceElementValue(root,"author/contact",contact);
		if (!owner.trim().equals(""))
			replaceElementValue(root,"authorization/owner",owner);
		if (overwriteTemplate) {
			replaceElementValue(root,"abstract",abstracttext);
			replaceElementValue(root,"keywords",keywords);
			replaceElementValue(root,"authorization/read",read);
			replaceElementValue(root,"authorization/update",update);
			replaceElementValue(root,"authorization/export",export);
		}
		setPubDate(root);

		//Get the text back.
		docText = XmlUtil.toString(xml);

		//And save the MIRCdocument
		return FileUtil.setFileText(docFile,docText);
	}

	private void setPubDate(Element root) {
		Element pd = XmlUtil.getElementViaPath(root,root.getTagName() + "/publication-date");
		if (pd != null) {
			String text = pd.getTextContent();
			if (text.equals("")) {
				pd.setTextContent(StringUtil.getDate());
			}
		}
	}

	/**
	 * Insert a text file into the MircDocument, creating a Notes section
	 * if it does not exist. This method does not re-index the document.
	 * @param file the text file to insert into the MircDocument.
	 * @return true if the operation succeeded, false otherwise;
	 */
	public synchronized boolean insert(File file) {
		//Do this in XML rather than text.
		Document xml;
		try { xml = XmlUtil.getDocumentFromString(docText); }
		catch (Exception ex) { return false; }
		Element root = xml.getDocumentElement();
		String rootName = root.getTagName() + "/";
		Element notes = getNotesSection(root);

		//Put in the paragraphs. Paragraphs are denoted by two newlines,
		//separated only by whitespace.
		String text = FileUtil.getFileText(file);
		String[] pArray = text.split("\\n\\s*\\n");
		for (int i=0; i<pArray.length; i++) {
			String pString = pArray[i].trim();
			if (!pString.equals("")) {
				Element pNode = root.getOwnerDocument().createElement("p");
				//Note: we do not escape the characters in pString
				//because that apparently happens automatically when
				//the Text node is created.
				Text textNode = root.getOwnerDocument().createTextNode(pString);
				pNode.appendChild(textNode);
				notes.appendChild(pNode);
			}
		}
		//Get the text back.
		docText = XmlUtil.toString(xml);

		//And save the MIRCdocument
		return FileUtil.setFileText(docFile,docText);
	}

	/**
	 * Insert a MircImage into the MircDocument, creating any necessary JPEGs.
	 * This method is used by the Author Service. It does not support research
	 * datasets because it allows DicomObjects to be inserted from multiple
	 * StudyInstanceUIDs. This method does not re-index the document.
	 * @param image the image to insert into the MircDocument.
	 * @return true if the operation succeeded, false otherwise;
	 */
	public synchronized boolean insert(MircImage image) throws Exception {
		//Handle any insert-megasave elements
		insertMegasave(image);

		//And handle any insert-image elements
		insertImage(image);

		//And save the MIRCdocument
		return FileUtil.setFileText(docFile,docText);
	}

	/**
	 * Insert a FileObject into the MircDocument. This method is used by the
	 * Dicom Service to insert metadata files into clinical trial documents.
	 * @param fileObject the object to insert into the MircDocument.
	 * @param name the object to insert into the MircDocument.
	 */
	public boolean insert(FileObject fileObject, String name) {

		//Set the extension of the file in case it hasn't been done yet.
		fileObject.setStandardExtension();

		//Move the object to the document's directory.
		//Note that this allows duplicates so that if multiple
		//metadata objects are received, they will all be stored
		//and indexed.
		fileObject.moveToDirectory(docDir,name);

		//Look for the insertion point
		String emptyElement = "<metadata-refs/>";
		String startElement = "<metadata-refs>";
		String endElement = "</metadata-refs>";
		int k = docText.indexOf(endElement);
		if (k < 0) {
			k = docText.indexOf(emptyElement);
			if (k < 0) return false;
			//Replace the empty element with a start and end.
			docText = docText.substring(0,k) +
						startElement + "\n" + endElement +
							docText.substring(k + emptyElement.length());
			//Position the index to the start of the end element.
			k = docText.indexOf(endElement,k);
		}

		//Get the text of the metadata element to insert.
		String insert = "<metadata href=\"" + fileObject.getFile().getName() + "\">\n";
		insert += "  <type>" + fileObject.getType() + "</type>\n";
		insert += "  <date>" + fileObject.getDate() + "</date>\n";
		insert += "  <desc>" + fileObject.getDescription() + "</desc>\n";
		insert += "</metadata>\n";

		//And now insert the element in the document.
		docText = docText.substring(0,k) + insert + docText.substring(k);

		//And save the MIRCdocument
		FileUtil.setFileText(docFile,docText);

		//Re-index the document.
		return MircIndex.getInstance().insertDocument(indexEntry);
	}

	/**
	 * Insert a DicomObject's element contents into the MircDocument by processing
	 * the document text, fetching elements from the DicomObject in response to
	 * DICOM element commands (g....e....). This method does not actually insert
	 * the DicomObject into the document; that is, it only inserts elements, not
	 * references to images or metadata objects. IMPORTANT NOTE: this method does
	 * NOT anonymize the DicomObject.
	 * @param dicomObject the object whose elements are to be inserted into the MircDocument.
	 * @return true if successful; false otherwise.
	 */
	public boolean insertDicomElements(DicomObject dicomObject) throws Exception {

		//Process the document as a template.
		docText = Template.getText(docText,dicomObject);

		//And save the MIRCdocument
		FileUtil.setFileText(docFile,docText);

		//Re-index the document.
		return MircIndex.getInstance().insertDocument(indexEntry);
	}

	/**
	 * Insert a DicomObject into the MircDocument, creating any necessary JPEGs and
	 * datasets. This method is used by the DICOM Service to insert DICOM objects
	 * into clinical trial documents. It supports the accumulation of research
	 * datasets both with and without PHI. This method re-indexes the document
	 * after the object has been inserted.
	 * @param dicomObject the object to insert into the MircDocument.
	 * @param allowOverwrite true if the object is to be allowed to overwrite another
	 * object with the same name in the document; false if a new name is to be assigned
	 * if necessary to avoid duplicates.
	 * @param anonymized true if the dicomObject has already been anonymized; false otherwise.
	 * @param scripts the anonymizer properties file for use in creating anonymized datasets.
	 * @param lookupTable the local lookup table for external remapping, or null.
	 */
	public boolean insert(
			DicomObject dicomObject,
			boolean allowOverwrite,
			boolean anonymized,
			Properties scripts,
			Properties lookupTable) throws IOException, Exception {

		//Force the ".dcm" extension.
		dicomObject.setStandardExtension();

		//Always process the insert-dataset elements
		//in case the anonymizer has changed.
		insertDatasetObject(dicomObject,anonymized,scripts,lookupTable);

		//Make sure the DicomObject is an image
		if (!dicomObject.isImage()) {
			//It's not; handle the special cases and then
			//treat the object as a metadata object.
			if (dicomObject.isManifest())
				insertManifestData(dicomObject);
			else if (dicomObject.isAdditionalTFInfo()) {
				try { insertAdditionalTFInfo(dicomObject); }
				catch (Exception ex) {
					//If we get here, the ATFI object caused an exception.
					//To make it possible to debug the object, insert it
					//as a metadata object and log the exception.
					insert(dicomObject, dicomObject.getFile().getName());
					logger.warn("Unable to insert the ATFI object: "+dicomObject.getFile());
				}
			}
			return insert(dicomObject, dicomObject.getFile().getName());
		}

		//Move the object into the document's directory,
		//allowing duplicates only if enabled. Note that this might
		//change the name of the dicomObject. First, though, make
		//a clone so we can be certain that the original file is
		//removed from the queue.
		File tempClone = new File(dicomObject.getFile().getAbsolutePath());
		dicomObject.moveToDirectory(docDir,allowOverwrite);
		tempClone.delete();

		//If the file is already in the text, don't modify docText,
		//but call the insertXXX methods anyway so that any changes
		//in the DicomObject (for example, WW/WL) can be reflected
		//in the JPEGs.
		//Note that if the name was changed in the moveToDirectory call,
		//a new instance of the object will be inserted and the text will
		//be modified.
		String name = dicomObject.getFile().getName();
		String target = "src=\""+name+"\"";
		boolean modifyText = (docText.indexOf(target) == -1);

		//Handle any insert-megasave elements
		insertMegasave(dicomObject,modifyText);

		//And handle any insert-image elements
		insertImage(dicomObject,modifyText);

		//And save the MIRCdocument
		FileUtil.setFileText(docFile,docText);

		//Re-index the document.
		return MircIndex.getInstance().insertDocument(indexEntry);
	}

	//Insert data from a TCE manifest.
	private void insertManifestData(DicomObject dicomObject) {

		//Do this in XML rather than text.
		Document xml;
		try { xml = XmlUtil.getDocumentFromString(docText); }
		catch (Exception ex) { return; }
		Element root = xml.getDocumentElement();
		String rootName = root.getTagName() + "/";

		//Try to get an author's name
		String[] observerList = dicomObject.getObserverList();
		if ((observerList != null) && (observerList.length > 0)) {
			String name = observerList[0];
			String[] names = name.split("\\^");
			if (names.length > 1) {
				name = names[0];
				for (int i=names.length-1; i>0; i--) name = names[i]+" "+name;
			}
			replaceElementValue(root,"author/name",name);
		}

		//Don't put in the modality from the manifest since it is always "KO".
		//replaceElementValue(root,"modality",dicomObject.getModality());

		//Now put in the Key Object Description, if it is there.
		String kodText = dicomObject.getKeyObjectDescription();
		if (kodText != null) {
			//Put the entire text in the section with the heading "Notes".
			Element notes = getNotesSection(root);

			//Break it into lines and put it all in a paragraph.
			String[] lines = kodText.split("\\n");
			String title = StringUtil.getDateTime();
			title = title.replace("T"," at ");
			title = "Manifest Processed on " + title;
			Element p = notes.getOwnerDocument().createElement("p");
			Element b = p.getOwnerDocument().createElement("b");
			b.appendChild(b.getOwnerDocument().createTextNode(title));
			p.appendChild(b);
			p.appendChild(p.getOwnerDocument().createElement("br"));
			p.appendChild(p.getOwnerDocument().createElement("br"));
			for (int i=0; i<lines.length; i++) {
				p.appendChild(p.getOwnerDocument().createTextNode(lines[i]));
				p.appendChild(p.getOwnerDocument().createElement("br"));
			}
			notes.appendChild(p);

			//Now parse the text and update any elements.
			Hashtable<String,String> table = dicomObject.getParsedText(kodText);
			if (table != null) insert(root,table);
		}
		docText = XmlUtil.toString(xml);
		//And save the MIRCdocument
		FileUtil.setFileText(docFile,docText);
	}

	//Find the section element containing the notes.
	//This method creates a Notes section if one does not exist.
	private Element getNotesSection(Element root) {
		Node child = root.getFirstChild();
		while (child != null) {
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				Element el = (Element)child;
				if (el.getTagName().equals("section")
						&& el.getAttribute("heading").equals("Notes")) {
					return el;
				}
			}
			child = child.getNextSibling();
		}
		//Didn't find one, create it.
		Element section = root.getOwnerDocument().createElement("section");
		section.setAttribute("heading","Notes");
		root.appendChild(section);
		return section;
	}

	//Insert data from a TCE Additional Teaching File Info object.
	private void insertAdditionalTFInfo(DicomObject dicomObject) {
		//Do this in XML rather than text.
		Document xml;
		try { xml = XmlUtil.getDocumentFromString(docText); }
		catch (Exception ex) { return; }
		Element root = xml.getDocumentElement();
		//Get the Additional Teaching File Info.
		Hashtable<String,String> table = dicomObject.getAdditionalTFInfo();
		if (table != null) {
			//Okay, it's there. update any elements.
			insert(root,table);
			//Now process the document, looking for any ATFI-xxx elements
			//and replace them with the corresponding contents from the table.
			processATFIElements(root,table);
			//And save the MIRCdocument
			docText = XmlUtil.toString(xml);
			FileUtil.setFileText(docFile,docText);
		}
	}

	//Replace elements with tag names starting with ATFI- with the contents of
	//the table entry with the corresponding suffix (e.g. ATFI-abstract is
	//replaced by a text node containing the text from the abstract element
	//in the table.
	private void processATFIElements(Element el, Hashtable<String,String> table) {
		String elName = el.getTagName();
		if (elName.startsWith("ATFI-")) {
			String key = elName.substring(5);
			String value = table.get(key);
			if (value != null) {
				Node text = el.getOwnerDocument().createTextNode(value);
				Node parent = el.getParentNode();
				parent.replaceChild(text, el);
			}
		}
		else {
			Node child = el.getFirstChild();
			while (child != null) {
				Node nextChild = child.getNextSibling();
				if (child instanceof Element) processATFIElements((Element)child, table);
				child = nextChild;
			}
		}
	}

	//Insert the contents of a Hashtable by listing all the keys and replacing the value of any
	//element whose path from the root of the MircDocument is equal to the name of the key.
	//This method will not modify any element which contains child elements.
	private boolean insert(Element root, Hashtable<String,String> table) {
		try {
			//Get the keys
			Enumeration<String> keys = table.keys();
			//Put the values in the document
			while (keys.hasMoreElements()) {
				String key = keys.nextElement();
				String value = table.get(key);
				replaceElementValue(root,key,value);
			}
			//And save the MIRCdocument
			return FileUtil.setFileText(docFile,XmlUtil.toString(root.getOwnerDocument()));
		}
		catch (Exception ex) { return false; }
	}

	//Replace the value of an element with text.
	private void replaceElementValue(Element root, String path, String value) {
		Document owner = root.getOwnerDocument();
		Element el = XmlUtil.getElementViaPath(root,root.getTagName() + "/" +path);
		if ((el != null) && (value != null)) {
			if (el.getTagName().equals("title") && (el.getTextContent().trim().toLowerCase().equals("untitled")))  {
				//Handle title elements specially because some
				//templates have "Untitled" in the title element.
				Node child;
				while ((child = el.getFirstChild()) != null) el.removeChild(child);
				setElementChildren(el,value);
			}
			else if (el.getTagName().equals("name") && el.getParentNode().getNodeName().equals("author"))  {
				//Handle name elements specially so any template value can be removed.
				Node child;
				while ((child = el.getFirstChild()) != null) el.removeChild(child);
				setElementChildren(el,value);
			}
			else if (el.getTagName().equals("abstract") ||
					 el.getParentNode().getNodeName().equals("abstract")) {
				//Handle abstract elements specially because of the requirement
				//that abstract contents be wrapped in paragraph tags. Some
				//templates have the paragraphs in them, and some do not.
				//Further, AFTI objects don't supply the p tag in the path while
				//some PACS vendors' implementations of the KeyObjectDescription
				//elements in TCE manifests do. The strategy here is to remove
				//all paragraph children of the abstract element that have the text content
				//"None." and the wrap the supplied value in paragraphs, splitting
				//it on double-newlines.
				if (!el.getTagName().equals("abstract")) el = (Element)el.getParentNode();
				Node child = el.getFirstChild();
				while (child != null) {
					if (child.getNodeType() == Node.ELEMENT_NODE) {
						if (child.getTextContent().trim().toLowerCase().equals("none.")) el.removeChild(child);
					}
					child = child.getNextSibling();
				}
				insertParagraphs(el, value);
			}
			else {
				//This is a normal element found from a path,
				//just insert the value in paragraphs.
				setElementChildren(el,value);
			}
		}
		//Now try to find a section element with a heading like the element being
		//replaced and add the value to its children. Only do this for paths
		//consisting of a single element.
		if (path.indexOf("/") == -1) {
			NodeList nl = root.getElementsByTagName("section");
			for (int i=0; i<nl.getLength(); i++) {
				Element section = (Element)nl.item(i);
				String heading = section.getAttribute("heading").toLowerCase();
				if (path.equals(heading)) insertParagraphs(section, value);
			}
		}
	}

	private void insertParagraphs(Element el, String value) {
		//Break the value into paragraphs by double newlines
		//and append them to the element.
		String[] pArray = value.split("\\n\\s*\\n");
		for (int k=0; k<pArray.length; k++) {
			String pString = pArray[k].trim();
			if (!pString.equals("")) {
				Element pNode = el.getOwnerDocument().createElement("p");
				setElementChildren(pNode,pString);
				el.appendChild(pNode);
			}
		}
	}

	private void replaceElementValue(Element el, String value) {
		//Make sure that there are no child elements.
		if (!XmlUtil.hasChildElements(el)) {
			Node child;
			while ((child = el.getFirstChild()) != null) el.removeChild(child);
			//Now parse the value and append its nodes to the element.
			setElementChildren(el,value);
		}
	}

	//Parse a text string and append its nodes as children of an element.
	//If the text doesn't parse, then put it all in a text node and append that to the element.
	private void setElementChildren(Element el, String value) {
		try {
			Document valueDoc = XmlUtil.getDocumentFromString("<root>"+value+"</root>");
			Document elDoc = el.getOwnerDocument();
			Element valueRoot = valueDoc.getDocumentElement();
			Node vNode = valueRoot.getFirstChild();
			while (vNode != null) {
				Node elNode = elDoc.importNode(vNode,true);
				el.appendChild(elNode);
				vNode = vNode.getNextSibling();
			}
		}
		catch (Exception ex) {
			//There was an error, just insert the value as a text node.
			Text text = el.getOwnerDocument().createTextNode(value);
			el.appendChild(text);
		}
	}

	//Handle the insert-megasave element for DicomObjects in
	//clinical trial documents.
	private void insertMegasave(DicomObject dicomObject, boolean modifyText) {

		//Look for the insert point
		int k = docText.indexOf("<insert-megasave");

		//If we can't find the place, just return without modifying docText
		if (k < 0) return;

		//Get the paneWidth from the <image-section> element.
		//This is the space allocated in the display for the images.
		int paneWidth = 700;
		int imageSection = docText.substring(0,k).lastIndexOf("<image-section");
		if (imageSection != -1)
			paneWidth =
				XmlStringUtil.getAttributeInt(
					docText,imageSection,"image-pane-width",paneWidth);

		//Get the width attribute from the insert-megasave element.
		//This is the maximum size JPEG to be created for any base image.
		int maxWidth = XmlStringUtil.getAttributeInt(docText,k,"width",paneWidth);
		//Make sure the maximum width fits in the pane.
		if (maxWidth > paneWidth) maxWidth = paneWidth;

		//See if there are any min-* attributes.
		//minWidth is the minimum size JPEG to be created for any base image.
		int minWidth = XmlStringUtil.getAttributeInt(docText,k,"min-width",0);

		//esMinSize is for NCI's caImage system.
		//It is an extra image that can be created, but not referenced in the MIRCdocument.
		int esMinSize = XmlStringUtil.getAttributeInt(docText,k,"extra-image-min-size",0);

		//Get the image size;
		int imageWidth = dicomObject.getColumns();

		//Make the JPEG images
		String name = dicomObject.getFile().getName();
		String nameNoExt = name.substring(0,name.lastIndexOf("."));
		MircImage image;
		try { image = dicomObject.getMircImage(); }
		catch (Exception e) { return; }
		Dimension d_base = image.saveAsJPEG(new File(docDir,nameNoExt+"_base.jpeg"),maxWidth,minWidth,jpegQuality);
		Dimension d_icon = image.saveAsJPEG(new File(docDir,nameNoExt+"_icon.jpeg"),64,0);
		Dimension d_icon96 = image.saveAsJPEG(new File(docDir,nameNoExt+"_icon96.jpeg"),96,0); //for the author service

		//Make the image element
		String insert =
			"<image src=\""+nameNoExt+"_base.jpeg\" w=\""+d_base.width+"\" h=\""+d_base.height+"\">\n" +
			"  <alternative-image role=\"icon\" src=\""+nameNoExt+"_icon.jpeg\" w=\""+d_icon.width+"\" h=\""+d_icon.height+"\"/>\n";
		if (imageWidth > maxWidth) {
			Dimension d_full = image.saveAsJPEG(new File(docDir,nameNoExt+"_full.jpeg"),imageWidth,0,jpegQuality);
			insert +=
			"  <alternative-image role=\"original-dimensions\" src=\""+nameNoExt+"_full.jpeg\" w=\""+d_full.width+"\" h=\""+d_full.height+"\"/>\n";
		}
		insert +=
			"  <alternative-image role=\"original-format\" src=\""+name+"\"/>\n" +
			"  " + getOrderByElement(dicomObject) +
			"</image>\n";

		//Make the extra image, if so configured (for NCI caImage)
		if (esMinSize > 255)
			image.saveAsJPEG(
				new File(docDir,nameNoExt+"_extra.jpeg"),
				imageWidth,esMinSize,jpegQuality);

		//If text modification is allowed, put the
		//image element into the document text.
		if (modifyText) insertImageElement(insert,"insert-megasave");
	}

	//Handle the insert-image element for DicomObjects
	//in clinical trial documents.
	private void insertImage(DicomObject dicomObject, boolean modifyText) {

		//Look for the insert point
		int k = docText.indexOf("<insert-image");

		//If we can't find the place, just return without modifying docText
		if (k < 0) return;

		//We found it, see if there is a width attribute
		int imageWidth = dicomObject.getColumns();
		int imageHeight = dicomObject.getRows();
		int maxWidth = XmlStringUtil.getAttributeInt(docText,k,"width",imageWidth);

		//See if there are any min-size attributes
		//minSize is the minimum size JPEG to be created for any base image.
		int minWidth = XmlStringUtil.getAttributeInt(docText,k,"min-width",0);

		//esMinSize is for NCI's caImage system.
		//It is an extra image that can be created, but not referenced in the MIRCdocument.
		int esMinSize = XmlStringUtil.getAttributeInt(docText,k,"extra-image-min-size",0);

		//Make the JPEG images
		String name = dicomObject.getFile().getName();
		String nameNoExt = name.substring(0,name.lastIndexOf("."));
		MircImage image;
		try { image = dicomObject.getMircImage(); }
		catch (Exception e) { return; }
		Dimension d_base = image.saveAsJPEG(new File(docDir,nameNoExt+"_base.jpeg"),maxWidth,minWidth,jpegQuality);
		Dimension d_icon = image.saveAsJPEG(new File(docDir,nameNoExt+"_icon.jpeg"),64,0);
		Dimension d_icon96 = image.saveAsJPEG(new File(docDir,nameNoExt+"_icon96.jpeg"),96,0); //for the author service
		if (imageWidth > maxWidth) {
			image.saveAsJPEG(new File(docDir,nameNoExt+"_full.jpeg"),imageWidth,0,jpegQuality);
		}

		//Make the image element
		String insert =
			"<image href=\""+name+"\" w=\""+imageWidth+"\" h=\""+imageHeight+"\">\n" +
			"  <image src=\""+nameNoExt+"_base.jpeg\" w=\""+d_base.width+"\" h=\""+d_base.height+"\"/>\n" +
			"  " + getOrderByElement(dicomObject) +
			"</image>\n";

		//Make the extra image, if so configured (for NCI caImage)
		if (esMinSize > 255)
			image.saveAsJPEG(
				new File(docDir,nameNoExt+"_extra.jpeg"),
				imageWidth,esMinSize,jpegQuality);

		//If text modification is allowed, put the
		//image element into the document text.
		if (modifyText) insertImageElement(insert,"insert-image");
	}

	//Handle the insert-megasave element for MircImages.
	private void insertMegasave(MircImage image) throws Exception {

		//Look for the insert point
		int k = docText.indexOf("<insert-megasave");

		//If we can't find the place, just return without modifying docText
		if (k < 0) return;

		//Get the paneWidth from the <image-section> element
		int paneWidth = 700;
		int imageSection = docText.substring(0,k).lastIndexOf("<image-section");
		if (imageSection != -1)
			paneWidth =
				XmlStringUtil.getAttributeInt(
					docText,imageSection,"image-pane-width",paneWidth);

		//Get the width attribute from the insert-megasave element.
		//This is the maximum size JPEG to be created for any base image.
		int maxWidth = XmlStringUtil.getAttributeInt(docText,k,"width",paneWidth);
		//Make sure the maximum width fits in the pane.
		if (maxWidth > paneWidth) maxWidth = paneWidth;

		//minWidth is the minimum size JPEG to be created for any base image.
		int minWidth = XmlStringUtil.getAttributeInt(docText,k,"min-width",0);

		//Get the image size;
		int imageWidth = image.getWidth();
		int imageHeight = image.getHeight();

		//Get the name of the image and the extension
		String name = image.getFile().getName();
		int extIndex = name.lastIndexOf(".");
		String ext = ".";
		if (extIndex >= 0) {
			ext = name.substring(extIndex);
			name = name.substring(0,extIndex);
		}

		//Make the icons
		Dimension d_icon = image.saveAsJPEG(new File(docDir,name+"_icon.jpeg"),64,0);
		Dimension d_icon96 = image.saveAsJPEG(new File(docDir,name+"_icon96.jpeg"),96,0);

		//Now we have two high-level cases:
		//1. The original image is a DicomObject: then
		//     - make a jpeg for the the base image
		//     - make a jpeg for the icon alternative-image
		//     - use the DicomObject as the original-format alternative-image
		//     - and if the DicomObject is too large to fit in the allocated space:
		//          - make a jpeg for the original-dimensions alternative-image
		//
		//2. The original image is not a DicomObject: we have two sub-cases:
		//
		//2a. The original image fits in the allocated space:
		//     - use the original image for the the base image
		//     - make a jpeg for the icon alternative-image
		//
		//2b. The original image does not fit
		//     - make a jpeg for the the base image
		//     - make a jpeg for the icon alternative-image
		//     - use the original image as the original-dimensions alternative-image

		String insert;
		if (image.isDicomImage()) {
			//It's a DicomObject
			Dimension d_base = image.saveAsJPEG(new File(docDir,name+"_base.jpeg"),maxWidth,minWidth,jpegQuality);
			insert =
		  		"<image src=\""+name+"_base.jpeg\" w=\""+d_base.width+"\" h=\""+d_base.height+"\">\n" +
				"  <alternative-image role=\"icon\" src=\""+name+"_icon.jpeg\" w=\""+d_icon.width+"\" h=\""+d_icon.height+"\"/>\n";
			if (imageWidth > maxWidth) {
				Dimension d_full = image.saveAsJPEG(new File(docDir,name+"_full.jpeg"),imageWidth,0,jpegQuality);
				insert +=
				"  <alternative-image role=\"original-dimensions\" src=\""+name+"_full.jpeg\" w=\""+d_full.width+"\" h=\""+d_full.height+"\"/>\n";
			}
			insert +=
				"  <alternative-image role=\"original-format\" src=\""+name+".dcm\"/>\n" +
				"</image>\n";
			image.renameTo(new File(docDir,name+".dcm"));
		}
		else if (imageWidth <= maxWidth) {
			//It's not a DicomObject and the original image fits.
			insert =
				"<image src=\""+name+"_full"+ext+"\" w=\""+imageWidth+"\" h=\""+imageHeight+"\">\n" +
				"  <alternative-image role=\"icon\" src=\""+name+"_icon.jpeg\" w=\""+d_icon.width+"\" h=\""+d_icon.height+"\"/>\n" +
				"</image>\n";
			image.renameTo(new File(docDir,name+"_full"+ext));
		}
		else {
			//It's not a DicomObject and the original image doesn't fit.
			Dimension d_base = image.saveAsJPEG(new File(docDir,name+"_base.jpeg"),maxWidth,minWidth,jpegQuality);
			insert =
		  		"<image src=\""+name+"_base.jpeg\" w=\""+d_base.width+"\" h=\""+d_base.height+"\">\n" +
				"  <alternative-image role=\"icon\" src=\""+name+"_icon.jpeg\" w=\""+d_icon.width+"\" h=\""+d_icon.height+"\"/>\n" +
				"  <alternative-image role=\"original-dimensions\" src=\""+name+"_full"+ext+"\" w=\""+imageWidth+"\" h=\""+imageHeight+"\"/>\n" +
				"</image>\n";
			image.renameTo(new File(docDir,name+"_full"+ext));
		}

		//And now insert the image in the document.
		docText = docText.substring(0,k) + insert + docText.substring(k);
	}

	//Handle the insert-image element for MircImages.
	private void insertImage(MircImage image) throws Exception {
		//Look for the insert point
		int k = docText.indexOf("<insert-image");

		//If we can't find the place, just return without modifying docText
		if (k < 0) return;

		//We found it, see if there are width attributes.
		int imageWidth = image.getColumns();
		int imageHeight = image.getRows();
		int maxWidth = XmlStringUtil.getAttributeInt(docText,k,"width",imageWidth);
		if (maxWidth > imageWidth) maxWidth = imageWidth;
		int minWidth = XmlStringUtil.getAttributeInt(docText,k,"min-width",0);

		//Get the name of the image and the extension
		String name = image.getFile().getName();
		int extIndex = name.lastIndexOf(".");
		String ext = ".";
		if (extIndex >= 0) {
			ext = name.substring(extIndex);
			name = name.substring(0,extIndex);
		}

		//Make an icon for the author service editor to make
		//loading the editor page faster
		image.saveAsJPEG(new File(docDir,name+"_icon96.jpeg"),96,0);

		//Now we have two high-level cases:
		//1. The original image is a DicomObject: use it
		//   as the link image and make a jpeg to fit.
		//
		//2. The original image is not a DicomObject:
		//   then we have two sub-cases:
		//
		//2a. The original image fits: use it as the displayed image.
		//
		//2b. The original image does not fit: use it as the link
		//    image and make a jpeg to fit.

		String insert;
		if (image.isDicomImage()) {
			//It's a DicomObject
			Dimension d_base = image.saveAsJPEG(new File(docDir,name+"_base.jpeg"),maxWidth,minWidth,jpegQuality);
			insert =
		  		"<image href=\""+name+".dcm\" w=\""+imageWidth+"\" h=\""+imageHeight+"\">\n" +
		  		"  <image src=\""+name+"_base.jpeg\" w=\""+d_base.width+"\" h=\""+d_base.height+"\"/>\n" +
		  		"</image>\n";
			image.renameTo(new File(docDir,name+".dcm"));
		}
		else if (imageWidth <= maxWidth) {
			//It's not a DicomObject and the original image fits.
			insert = "<image src=\""+name+"_full"+ext+"\" w=\""+imageWidth+"\" h=\""+imageHeight+"\"/>\n";
			image.renameTo(new File(docDir,name+"_full"+ext));
		}
		else {
			//It's not a DicomObject and the original image doesn't fit.
			Dimension d_base = image.saveAsJPEG(new File(docDir,name+"_base.jpeg"),maxWidth,minWidth,jpegQuality);
			insert =
				"<image href=\""+name+"_full"+ext+"\" w=\""+imageWidth+"\" h=\""+imageHeight+"\">\n" +
		  		"  <image src=\""+name+"_base.jpeg\" w=\""+d_base.width+"\" h=\""+d_base.height+"\"/>\n" +
		  		"</image>\n";
			image.renameTo(new File(docDir,name+"_full"+ext));
		}

		//Create a caption text element if we need one
		String captionString = "";
		int caption = XmlStringUtil.getAttributeInt(docText,k,"caption",-1);
		if (caption != -1) {
			captionString = "<text-caption name=\"CAP"+caption+"\"";
			String jumpbuttons = XmlStringUtil.getAttribute(docText,k,"jump-buttons");
			if ((jumpbuttons != null) && jumpbuttons.equals("yes")) {
				captionString += " jump-buttons=\"yes\"";
			}
			String showbutton = XmlStringUtil.getAttribute(docText,k,"show-button");
			if ((showbutton != null) && showbutton.equals("yes")) {
				captionString += " show-button=\"yes\"";
			}
			captionString += "/>";
			//Fix the document so the next caption will have the next value.
			//This is necessary to make all the name attributes different.
			int k1 = docText.indexOf("caption",k);
			k1 = docText.indexOf("\"",k1) + 1;
			int k2 = docText.indexOf("\"",k1);
			docText = docText.substring(0,k1) + (caption+1) + docText.substring(k2);
		}

		//And now insert the image and caption (if any).
		docText = docText.substring(0,k) + insert + captionString + docText.substring(k);
	}

	//Handle the insert-dataset element
	private void insertDatasetObject(
			DicomObject dicomObject,
			boolean anonymized,
			Properties scripts,
			Properties lookupTable) {
		int k = -1;

		//Find all the insert-dataset elements and insert
		//the DicomObject into all of them, taking care
		//to anonymize if the dataset requires it.
		while ((k=docText.indexOf("<insert-dataset",k+1)) != -1) {

			//Check that the phi attribute is present and
			//we have the kind of data the element wants.
			String phi = XmlStringUtil.getAttribute(docText,k,"phi");
			if ((phi != null) && !(phi.equals("yes") && anonymized)) {

				//Okay, get all the directory and file names.
				String sopiUID = dicomObject.getSOPInstanceUID();
				String datasetDirName = (phi.equals("yes") ? "phi" : "no-phi");
				String seriesDirName = getSeriesDirName(dicomObject);
				File datasetDir = new File(docDir,datasetDirName);
				File seriesDir = new File(datasetDir,seriesDirName);
				File imageFile = new File(seriesDir,sopiUID + "-" + datasetDirName + ".dcm");

				//Create the directories and copy in the image.
				try {
					seriesDir.mkdirs();
					dicomObject.copyTo(imageFile);

					//Finally, anonymize the object if necessary
					if (!phi.equals("yes") && !anonymized)
						DicomAnonymizer.anonymize(
							imageFile, imageFile,
							scripts, lookupTable,
							new LocalRemapper(), false, false);
				}
				catch (Exception e) {
					//Something went wrong; just delete the object
					//from the series directory and log it.
					if (imageFile.exists()) imageFile.delete();
					logger.warn("Unable to insert DICOM object into dataset:\n" +
								e.toString() + "\n" + imageFile.getName());
				}
			}
		}
		//Note that this method doesn't actually update the docText because
		//the dataset is just a child directory of the document's directory,
		//and it does not appear in the docText.
	}

	//Create a name for a series directory.
	//If the series description is present, use that.
	//If not, try the series number.
	//If all else fails, use the SeriesInstanceUID.
	//Filter the text to make sure no characters are present
	//that would cause a problem as a directory name.
	private String getSeriesDirName(DicomObject dicomObject) {
		String name = dicomObject.getSeriesDescription();
		if ((name != null) && !name.trim().equals(""))
			return name.trim().replaceAll("\\s+","-").replaceAll("[:\\/]","-");
		name = dicomObject.getSeriesNumber();
		if ((name != null) && !name.trim().equals(""))
			return name.trim().replaceAll("\\s+","-").replaceAll("[:\\/]","-");
		name = dicomObject.getSeriesInstanceUID();
		if (name != null) return name;
		return "UnknownSeries";
	}

	//Insert the image element in the proper place in the document.
	private void insertImageElement(String element, String insertElementName) {
		try {
			Document insert = XmlUtil.getDocumentFromString(element);
			Element insertRoot = insert.getDocumentElement();
			Document doc = XmlUtil.getDocumentFromString(docText);
			Element docRoot = doc.getDocumentElement();
			NodeList list = doc.getElementsByTagName(insertElementName);
			if (list.getLength() == 0) return;
			Element insertElement = (Element)list.item(0);
			Element parent = (Element)insertElement.getParentNode();
			Element child;
			list = parent.getChildNodes();
			for (int i=0; i<list.getLength(); i++) {
				if (list.item(i).getNodeType() == Node.ELEMENT_NODE) {
					child = (Element)list.item(i);
					if (child.getTagName().equals(insertElementName) ||
							!inOrder(child, insertRoot)) {
						insertRoot = (Element)doc.importNode(insertRoot,true);
						parent.insertBefore(insertRoot,child);
						docText = XmlUtil.toString(doc);
						return;
					}
				}
			}
		}
		catch (Exception e) {
			logger.warn("Unable to insert the image element into the document.");
		}
	}

	//Determine whether two image elements are in order
	//by series, acquisition, and instance. If the order-by child is missing
	//from one of the elements, that child is first. If it is missing from
	//both elements, the elements are in order.
	private boolean inOrder(Element a, Element b) {
		Element aOrderBy = getNamedChild(a,"order-by");
		if (aOrderBy == null) return true;
		Element bOrderBy = getNamedChild(b,"order-by");
		if (bOrderBy == null) return false;
		int c;
		if ((c = compare(aOrderBy,bOrderBy,"series")) != 0) return (c > 0);
		if ((c = compare(aOrderBy,bOrderBy,"acquisition")) != 0) return (c > 0);
		if ((c = compare(aOrderBy,bOrderBy,"instance")) != 0) return (c > 0);
		return true;
	}

	//Get the first named child of an Element.
	private Element getNamedChild(Element a, String childName) {
		NodeList list = a.getElementsByTagName(childName);
		if (list.getLength() == 0) return null;
		return (Element)list.item(0);
	}

	//Compare an attribute of two elements.
	//Return +1 if the attribute values are in sequence,
	//zero if they are the same, or -1 if they are out of order.
	private int compare(Element a, Element b, String attrName) {
		int aValue = getAttr(a,attrName);
		int bValue = getAttr(b,attrName);
		if (aValue < bValue) return 1;
		if (aValue == bValue) return 0;
		return -1;
	}

	//Get an attribute value and convert it to an integer
	private int getAttr(Element a, String attrName) {
		String attr = a.getAttribute(attrName);
		try { return Integer.parseInt(attr); }
		catch (Exception e) { }
		return 0;
	}

	//Get a string containing the order-by element for a DicomObject
	private String getOrderByElement(DicomObject dicomObject) {
		return
			"<order-by" +
			" series=\"" + dicomObject.getSeriesNumber() + "\"" +
			" acquisition=\"" + dicomObject.getAcquisitionNumber() + "\"" +
			" instance=\"" + dicomObject.getInstanceNumber() + "\"/>\n";
	}

	/**
	 * Insert RadLex terms
	 * @return true if the operation succeeded, false otherwise;
	 */
	public synchronized boolean insertRadLexTerms() {
		try {
			//Get the MIRCdocument.
			Document xml = XmlUtil.getDocumentFromString(docText);
			Element root = xml.getDocumentElement();

			//Insert the terms
			insertRadLexTerms(root);

			//Get the text back.
			docText = XmlUtil.toString(xml);

			//And save the MIRCdocument
			return FileUtil.setFileText(docFile,docText);
		}
		catch (Exception ex) { return false; }
	}

	/**
	 * Insert RadLex terms.
	 * @param node the node on which to begin searching for RadLex terms.
	 */
	public static void insertRadLexTerms(Node node) {
		short type = node.getNodeType();
		if (type == Node.ELEMENT_NODE) {
			if (node.getNodeName().equals("term")) {
				//Replace the term node with its contents
				//and then process it. This will allow for
				//spelling corrections and changes in the
				//RadLex ontology.
				Document doc = node.getOwnerDocument();
				Node parent = node.getParentNode();
				String content = node.getTextContent();
				Text text = doc.createTextNode(content);
				parent.replaceChild(text, node);
				insertRadLexTerms(text);
			}
			else if (!skip.contains(node.getNodeName())) {
				Node child = node.getFirstChild();
				while (child != null) {
					//Note: get the next node now, before
					//anybody has a chance to remove the child
					Node next = child.getNextSibling();
					//Now insert the terms, which may result
					//in the child being removed from the document
					insertRadLexTerms(child);
					child = next;
				}
			}
		}
		else if (type == Node.TEXT_NODE) {
			Document doc = node.getOwnerDocument();
			Node parent = node.getParentNode();

			//Replace multiple whitespace characters with a single space.
			String text = node.getNodeValue().replaceAll("\\s+"," ");
			node.setNodeValue(text);

			Result result;
			while ((result=getFirstTerm(node)) != null) {
				//Okay, here is the situation. We have found a string
				//in the text node which matches a RadLex term.
				//We have to split the text node twice, once before
				//the term and once after it. The text node in the
				//middle will be the one containing the term. We must
				//then replace that node with a term element containing
				//the term text node.

				//First split the text node before the term.
				//The "node" variable will refer to the text node
				//containing the text before the term.
				//The "termText" variable will refer to the text node
				//containing the term text and the text (if any) after it
				Text termText = ((Text)node).splitText(result.start);

				//Now split the termText node after the term.
				//The "remainingText" variable will refer to the text node
				//containing all the text after the term.
				Text remainingText = termText.splitText(result.length);

				//Now we have to wrap the termText node in a term element.
				Element termElement = doc.createElement("term");
				termElement.setAttribute("lexicon", "RadLex");
				termElement.setAttribute("id", result.uid);
				parent.insertBefore(termElement, termText);
				//Note: the appendChild method removes the
				//appended node if it is already in the document,
				//and then appends it in the desired place.
				termElement.appendChild(termText);

				//Okay, we have processed this term, so all we have
				//to do is set up to check the remainingText node.
				node = remainingText;
			}
		}
	}

	static class Result {
		int start = 0;
		int length = 0;
		String uid = "";
		public Result(int start, int length, String uid) {
			this.start = start;
			this.length = length;
			this.uid = uid;
		}
	}

	static class Word {
		String text;
		int start;
		public Word(String text, int start) {
			this.text = text;
			this.start = start;
		}
	}

	private static Result getFirstTerm(Node node) {
		if (node.getNodeType() != Node.TEXT_NODE) return null;
		try {
			String text = node.getNodeValue();
			int k = 0;
			Word word;
			while ((word=getNextWord(text,k)) != null) {
				Term[] terms = RadLexIndex.getTerms(word.text);
				if (terms != null) {
					for (int i=0; i<terms.length; i++) {
						Term term = terms[i];
						//Note: only accept matching terms at least 5 characters long.
						if (term.matches(text, word.start) && (term.text.length() >= 5)) {
							return new Result(word.start, term.text.length(), term.id);
						}
					}
				}
				k = word.start + word.text.length();
			}
		}
		catch (Exception ex) { }
		return null;
	}

	private static Word getNextWord(String text, int k) {
		if (k < 0) return null;
		//Find the first letter character.
		while ((k < text.length()) && !Character.isLetter(text.charAt(k))) k++;
		//See if we ran off the end.
		if (k == text.length()) return null;
		//Find the end of the word (look for the next non-letter).
		int kk = k;
		while ((kk < text.length()) && Character.isLetter(text.charAt(kk))) kk++;
		//Return the word
		return new Word(text.substring(k, kk), k);
	}

	static class SkipElements extends HashSet<String> {
		public SkipElements() {
			super();
			this.add("title");
			this.add("alternative-title");
			this.add("author");
			this.add("patient");
			this.add("authorization");
			this.add("level");
			this.add("access");
			this.add("image-section");
			this.add("references");
			this.add("a");
			this.add("href");
			this.add("image");
			this.add("show");
			this.add("phi");
			this.add("document-type");
			this.add("document-id");
			this.add("creator");
			this.add("peer-review");
			this.add("publication-date");
			this.add("revision-history");
			this.add("rights");
			this.add("block");
			this.add("insert-image");
			this.add("insert-megasave");
			this.add("insert-dataset");
			this.add("metadata-refs");
			this.add("quiz");
		}
	}

}
