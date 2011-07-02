/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * A class for parsing a DICOM URL in the form used by dcm4che.
 */
public class DicomURL {

	public String calledAET = "";
	public String callingAET = "";
	public String host = "";
	public int port = -1;

	/**
	 * Class constructor; parses and stores a DICOM URL without using the
	 * Java URL class, which requires that it know the protocol.
	 * @param urlString the DICOM URL in the form:
	 * "dicom://calledAET:callingAET@host:port".
	 * @throws Exception if the string cannot be parsed.
	 */
	public DicomURL(String urlString) throws Exception {

		//Find the AE titles
		int k = urlString.indexOf("://") + 3;

		//Get the calledAET
		int kk = urlString.indexOf(":",k);
		if (kk == -1) throw new Exception("Missing separator [:] for AE Titles");
		calledAET = urlString.substring(k,kk).trim();

		//Get the callingAET
		k = ++kk;
		kk = urlString.indexOf("@",k);
		if (kk == -1) throw new Exception("Missing terminator [@] for CallingAET");
		callingAET = urlString.substring(k,kk).trim();

		//Get the host address
		k = ++kk;
		kk = urlString.indexOf(":",k);
		if (kk == -1) throw new Exception("Missing separator [:] for Host and Port");
		host = urlString.substring(k,kk).trim();

		//Get the port
		k = ++kk;
		String portString = urlString.substring(k).trim();
		try { port = Integer.parseInt(portString); }
		catch (Exception e) { throw new Exception("Unparseable port number ["+portString+"]"); }
	}
}

