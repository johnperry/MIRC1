/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.mircservlets;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.*;
import org.rsna.mircsite.util.*;

/**
 * Sets the IP address of the MIRC site in the configuration files
 * of the query service (mirc.xml) and the storage service (storage.xml).
 */
public class SetIPAddress extends HttpServlet {

	/**
	 * The servlet method that is called when the webapp's web.xml
	 * file is parsed.
	 * <p>
	 * This servlet sets the IP address in the configuration file of
	 * the webapp if the addresstype is specified as "dynamic" and the
	 * IP portion of the siteurl is numeric. This allows a site to be
	 * installed on a system that obtains its IP address from a DHCP server
	 * without having to manually change the configuration files whenever
	 * the IP address changes.
	 * <p>
	 * This servlet works on both query service and storage service
	 * configuration files, so it can be called in both webapps. It takes
	 * the parameters from the query service's configuration file, which
	 * is always found at Tomcat/webapps/mirc/mirc.xml.
	 */
	public void init() {
		String ipAddress = IPUtil.getIPAddress();
		File root =  new File(getServletContext().getRealPath("/"));

		//Find the query service
		File mirc = new File(root.getParentFile(),"mirc");
		mirc = new File(mirc,"mirc.xml");

		//Get the addresstype and the siteurl entities from mirc.xml
		String text = FileUtil.getFileText(mirc);
		String addresstype = XmlStringUtil.getEntity(text,"addresstype");
		String siteurl = XmlStringUtil.getEntity(text,"siteurl");

		//Find the IP part; make sure it is properly formed.
		//If it doesn't start with "http://", assume something
		//is really wrong and do nothing.
		if (!siteurl.startsWith("http://")) return;

		//Only change the address if dynamic addressing is specified
		//and the address is not a domain name like "mirc.rsna.org".
		if (addresstype.equals("dynamic")) {
			int beginIP = 7;
			int endIP = siteurl.indexOf(":",beginIP);
			if (endIP < 0) endIP = siteurl.length();

			String ip = siteurl.substring(beginIP,endIP);
			if (ip.replaceAll("[\\d\\.\\s]","").trim().length() == 0) {
				//It's a numeric IP address and the addresstype is dynamic,
				//create a new siteurl using the IP address obtained from the OS.
				siteurl =
						siteurl.substring(0,beginIP)
							+ ipAddress
								+ siteurl.substring(endIP);
			}
		}

		//Now fix the siteurl entity in the configuration file found
		//in the root directory of this servlet. It might be mirc.xml or
		//storage.xml, depending on whether it is a query service or
		//a storage service, so try them both.
		setSiteURL(new File(root,"mirc.xml"),siteurl);
		setSiteURL(new File(root,"storage.xml"),siteurl);
	}

	//Update the definition of the siteurl entity to correspond
	//to the actual IP address of the MIRC site. Note that this
	//function works on the query service and storage service
	//configuration files because both use the same entity name
	//for the IP address.
	private void setSiteURL(File file, String siteurl) {
		//Get the configuration file
		if (!file.exists()) return;
		String text = FileUtil.getFileText(file);
		if (text == null) return;

		//Set the siteurl entity
		text = XmlStringUtil.setEntity(text,"siteurl",siteurl);

		//Update the configuration file
		FileUtil.setFileText(file,text);
	}

}
