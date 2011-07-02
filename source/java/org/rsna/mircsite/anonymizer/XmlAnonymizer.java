/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.anonymizer;

import java.io.File;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Properties;
import org.apache.log4j.Logger;
import org.rsna.mircsite.util.FileUtil;
import org.rsna.mircsite.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.Text;
import org.w3c.dom.NodeList;

/**
 * The MIRC XML anonymizer. This anonymizer provides de-identification and
 * re-identification of XML objects for clinical trials. Each element
 * is scriptable. The script language is documented on the MIRC wiki.
 * <p>
 * See the <a href="http://mircwiki.rsna.org">
 * MIRC wiki</a> for more more information.
 */
public class XmlAnonymizer {

	static final Logger logger = Logger.getLogger(XmlAnonymizer.class);

	/**
	 * Anonymizes the input file, writing the result to the output file.
	 * The fields to anonymize are scripted in the properties file.
	 * <p>
	 * @param inFile the file to anonymize.
	 * @param outFile the output file. It may be same as inFile you if want
	 * to anonymize in place.
	 * @param cmds the properties file containing the anonymization commands.
     * @param remapper the Remapper to use for remapping function calls.
	 * @return true if successful; false otherwise, in which case the
	 * input file is not modified.
	 */
	public static boolean anonymize(
			File inFile,
			File outFile,
			String cmds,
			Remapper remapper) {
		try {
			Document xmlDocument = XmlUtil.getDocument(inFile);
			Hashtable store = new Hashtable();
			XmlCommandHandler ch = new XmlCommandHandler(cmds);
			XmlCommand cmd = null;
			String value = null;
			while ((cmd=ch.getNextCommand()) != null) {
				if (cmd.type == cmd.ASSIGN) {
					value = (new XmlScript(xmlDocument,store,cmd.right,remapper)).getValue("");
					//Trap print commands
					if (cmd.left.equals("$print"))
						System.out.println(value);
					else
						store.put(cmd.left,value);
				}
				else if (cmd.type == cmd.PATH) {
					processPath(
						xmlDocument,
						cmd.left,
						new XmlScript(xmlDocument,store,cmd.right,remapper));
				}
			}
			FileUtil.setFileText(outFile,XmlUtil.toString(xmlDocument));
		}
		catch (Exception ex) {
			logger.warn("XML Anonymizer exception ",ex);
			return false; }
		return true;
	}

	private static void processPath(Node node, String path, XmlScript script) throws Exception {
		processPath(new XmlPathElement(node,path), script);
	}

	private static void processPath(XmlPathElement pe, XmlScript script) throws Exception {

		//If the next segment is an attribute, process it.
		if (pe.segmentIsAttribute()) {
			processAttribute(pe,script);
			return;
		}

		//No, see if the next segment is empty, meaning that
		//the parent node of this XmlPathElement is the end of
		//the path. If so, process the parent node (which must
		//be an Element).
		if (pe.isEndSegment()) {
			processElement(pe,script);
			return;
		}

		//This is not the end of the path; get the NodeList;
		//then see if there is an element available for the segment.
		NodeList nl = pe.getNodeList();
		if ((nl == null) || (nl.getLength() == 0)) {
			//The element identified by the segment is missing.
			//If it is required, then create it and process it;
			//otherwise, forget it and don't continue down the path.
			if (script.isRequired()) processPath(pe.createPathElement(), script);
			return;
		}

		//If we get here, one or more elements identified by
		//the segment are present and this is not the end of
		//the path; process the child segments.
		String remainingPath = pe.getRemainingPath();
		for (int i=0; i<nl.getLength(); i++) {
			processPath(nl.item(i),remainingPath,script);
		}
	}

	private static void processAttribute(XmlPathElement pe, XmlScript script) throws Exception {
		String peValue = pe.getValue();
		String scriptValue = script.getValue(peValue);
		if (script.isRemoved()) pe.removeAttribute();
		else pe.setValue(scriptValue);
	}

	private static void processElement(XmlPathElement pe, XmlScript script) throws Exception {
		String peValue = pe.getValue();
		String scriptValue = script.getValue(peValue);
		if (script.isRemoved()) pe.removeElement();
		else pe.setValue(scriptValue);
	}

}
