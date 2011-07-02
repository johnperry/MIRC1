/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import javax.servlet.http.HttpServletRequest;
import sun.misc.BASE64Encoder;
import sun.misc.BASE64Decoder;

/**
 * A class to encapsulate a visa.
 */
public class Visa {

	public String url;
	public String username;
	public String password;

	/**
	 * Construct a new Visa from Strings.
	 * @param url the URL of the server.
	 * @param username the username on the server.
	 * @param password the password on the server.
	 */
	public Visa(String url, String username, String password) {
		this.url = getServerURL(url.trim());
		this.username = username.trim();
		this.password = password.trim();
	}

	/**
	 * Construct a new Visa from an HttpServletRequest.
	 * @param req the request providing the user's credentials,
	 * from which to construct the original Visa.
	 */
	public Visa(HttpServletRequest req) {
		//Get the URL of the request.
		this.url = getServerURL(req.getRequestURL().toString());
		this.username = "";
		this.password = "";
		//Get the Authorization header so
		//we can get the username and password
		String auth = req.getHeader("Authorization");
		auth = (auth != null) ? auth.trim() : "";
		if (auth.toLowerCase().startsWith("basic")) {
			auth = auth.substring(5).trim();
			try {
				BASE64Decoder b64Decoder = new BASE64Decoder();
				String creds = new String(b64Decoder.decodeBuffer(auth));
				int k = creds.indexOf(":");
				if (k > 0) {
					this.username = creds.substring(0,k).trim();
					this.password = creds.substring(k+1).trim();
				}
			}
			catch (Exception nocreds) { }
		}
	}

	/**
	 * Get the username and password in the form for
	 * a basic authorization header: "Basic " + base64(username:password).
	 * @return the basic authorization string.
	 */
	public String getBasicAuthorization() {
		BASE64Encoder b64Encoder = new BASE64Encoder();
		return "Basic " + b64Encoder.encode((username + ":" + password).getBytes());
	}

	/**
	 * Get the server part of a URL string.
	 * @return the URL string up to the beginning of the path information,
	 * and not including the leading slash. If the URL does not include "://"
	 * or a path, then return the entire string.
	 */
	public static String getServerURL(String url) {
		int k = url.indexOf("://");
		if (k == -1) return url;
		k = url.indexOf("/",k+3);
		if (k == -1) return url;
		return url.substring(0,k);
	}

}




