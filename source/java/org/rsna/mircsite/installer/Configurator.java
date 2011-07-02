/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.installer;

import java.io.File;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.jar.*;

/**
 * A class to encapsulate static methods for accessing and updating
 * the configuration entities in the Query Service and Storage Service
 * configuration XML files.
 */
public class Configurator {

	/**
	 * Determine whether this installation is on the RSNA site.
	 * The RSNA site uses a different background header image.
	 * Use the existence of that image as the indicator.
	 * @param mirc the query service webapp root directory.
	 * @return true if this is the RSNA site; false otherwise.
	 */
	public static boolean checkForRSNASite(File mirc) {
		File headerFile = new File(mirc, "RSNAheaderbackground.jpg");
		if (headerFile.exists()) return true;
		return false;
	}

	/**
	 * Get an array containing the values of all the entities
	 * in the web.xml files of all the webapps.
	 * @param webapps the directory to be searched for webapps.
	 * @return the list of all entity values.
	 */
	public static String[] getRoleEntities(File webapps) {
		if (!webapps.isDirectory()) return new String[0];
		Hashtable h = new Hashtable();
		File[] apps = webapps.listFiles();
		//Put all the entitiy values into a hashtable to avoid duplicates.
		for (int i=0; i<apps.length; i++) {
			if (apps[i].isDirectory()) {
				File webXMLFile =
					new File(apps[i],"WEB-INF"+File.separator+"web.xml");
				if (webXMLFile.exists()) {
					String webXML = FileInstaller.getFileText(webXMLFile);
					insertEntityValues(webXML,h);
				}
			}
		}
		//Get the entities out of the table and return them in an array.
		String[] entities = new String[h.size()];
		Enumeration en = h.keys();
		for (int i=0; i<entities.length && en.hasMoreElements(); i++)
			entities[i] = (String)en.nextElement();
		return entities;
	}

	//Get all the entities and put their values in a hashtable as the keys.
	private static void insertEntityValues(String s, Hashtable h) {
		int begin = -1;
		int end;
		String value;
		while ((begin = s.indexOf("!ENTITY",begin+1)) != -1) {
			begin = skipWhitespace(s,begin+7);
			end = findWhitespace(s,begin);
			begin = s.indexOf("\"",end) + 1;
			end = s.indexOf("\"",begin);
			value = s.substring(begin,end);
			h.put(value,"x");
		}
	}

	/**
	 * Create a hashtable containing the values of the entities in the
	 * mirc.xml file, plus the pageheader, pagefooter, and disclaimer elements.
	 * @param s the text of the mirc.xml file.
	 * @param pageElements true if the pageheader, pagefooter, and
	 * disclaimer elements are to be included; false otherwise.
	 * @return a new hashtable containing the entities and elements.
	 */
	public static Hashtable hashQueryService(String s, boolean pageElements) {
		Hashtable h = new Hashtable();
		insertEntities(s,h);
		insertElement(s,h,"server",true);

		//The pageElements test is removed because these elements
		//are not present in the query service as of T33.
		/*
		if (pageElements) {
			insertElement(s,h,"pageheader",false);
			insertElement(s,h,"pagefooter",false);
			insertElement(s,h,"disclaimer",false);
		}
		*/
		insertVersion(h);
		return h;
	}

	//Get a single element and put it in the hashtable.
	private static void insertElement(String s, Hashtable h, String e, boolean update) {
		String startTarget = "<" + e ;
		String endTarget = "</" + e + ">";
		int begin = s.indexOf(startTarget);
		if (begin < 0) return;
		int end = s.lastIndexOf(endTarget);
		if (end < 0) return;
		String value = s.substring(begin,end);
		if (update) value = value.replace("http://&siteurl;","&siteurl;");
		h.put(e,value);
	}

	//Get all the entities and put them in the hashtable.
	private static void insertEntities(String s, Hashtable h) {
		int begin = -1;
		int end;
		String name, value;
		while ((begin = s.indexOf("!ENTITY",begin+1)) != -1) {
			begin = skipWhitespace(s,begin+7);
			end = findWhitespace(s,begin);
			name = s.substring(begin,end);
			begin = s.indexOf("\"",end) + 1;
			end = s.indexOf("\"",begin);
			value = s.substring(begin,end);
			if (name.equals("siteurl") && !value.startsWith("http://")) {
				value = "http://" + value;
			}
			h.put(name,value);
		}
	}

	/**
	 *Get all the attributes from the manifest of the installer jar
	 *and put them in the supplied hashtable.
	 * @param object the object to be used to get access to the jar.
	 */
	public static Hashtable hashManifest(Object object) {
		Hashtable h = new Hashtable();
		try {
			File temp = File.createTempFile("MANIFEST",".jar");
			FileInstaller.resourceCopy(object, "/modules/queryservice.war", temp);
			JarFile jar = new JarFile(temp);
			Manifest mf = jar.getManifest();
			Attributes attrs = mf.getMainAttributes();
			String version = attrs.getValue("version");
			String date = attrs.getValue("date");
			h.put("version",version);
			h.put("date",date);
			jar.close();
			temp.delete();
		}
		catch (Exception ex) {
			System.out.println("Exception: "+ex.getMessage());
			h.put("version","T0");
			h.put("date","0000-00-00 at 00:00:00");
		}
		return h;
	}

	/**
	 * Create a hashtable containing the three supplied values.
	 * This method is used for new installations.
	 * @param sitename the name of the site.
	 * @param siteurl the fully qualified URL of the site, including the port.
	 * @param addresstype "static" if the IP address is not to be
	 * adjusted on server startup; "dynamic" if the IP address is to
	 * be acquired from the OS and all the configuration files are to be
	 * adjusted for it on server startup.
	 * @return a new hashtable with the values added.
	 */
	public static Hashtable hashQueryService(
					String sitename, String siteurl, String addresstype) {
		Hashtable h = new Hashtable();
		h.put("sitename",sitename);
		h.put("siteurl",siteurl);
		h.put("addresstype",addresstype);
		insertVersion(h);
		return h;
	}

	/**
	 * Get the release version entity from the text of the mirc.xml file.
	 * @param s the text of the mirc.xml file.
	 * @return the text of the version entity.
	 */
	public static String getQueryServiceVersion(String s) {
		int begin = -1;
		int end;
		String name;
		String value = null;
		while ((begin = s.indexOf("!ENTITY",begin+1)) != -1) {
			begin = skipWhitespace(s,begin+7);
			end = findWhitespace(s,begin);
			name = s.substring(begin,end);
			begin = s.indexOf("\"",end) + 1;
			end = s.indexOf("\"",begin);
			value = s.substring(begin,end);
			if (name.equals("version")) return value;
		}
		return value;
	}

	/**
	 * Get the release version entity from the manifest hashtable.
	 * @return the text of the version attribute in the manifest.
	 */
	public static String getQueryServiceVersion() {
		return (String)Installer.manifest.get("version");
	}

	/**
	 * Get the integer part of the release version.
	 */
	public static int getQueryServiceVersionInt() {
		String v = Installer.installedVersion;
		if (v == null) return 0;
		v = v.replaceAll("[^\\d]","");
		return Integer.parseInt(v);
	}

	/**
	 * Determine whether the currently installed release version was installed
	 * since a specific release.
	 * @param sinceVersion the version to be compared to the currently installed version.
	 * @return true if the installed version is greater than or equal to the specified version.
	 */
	public static boolean since(String sinceVersion) {
		try {
			int sinceVersionInt = Integer.parseInt(sinceVersion.replaceAll("[^\\d]",""));
			return (getQueryServiceVersionInt() >= sinceVersionInt);
		}
		catch (Exception ex) { return true; }
	}

	/**
	 * Update the mirc.xml text with entity values from the supplied hashtable.
	 * @param s the text to be updated.
	 * @param h the hashtable containing the entities.
	 * @return the updated text.
	 */
	public static String updateQueryServiceConfiguration(String s, Hashtable h) {
		int begin, end;
		s = fixEntity(s,"mode",(String)h.get("mode"));
		s = fixEntity(s,"sitename",(String)h.get("sitename"));
		s = fixEntity(s,"masthead",(String)h.get("masthead"));
		s = fixEntity(s,"mastheadheight",(String)h.get("mastheadheight"));
		s = fixEntity(s,"showsitename",(String)h.get("showsitename"));
		s = fixEntity(s,"showlogin",(String)h.get("showlogin"));
		s = fixEntity(s,"showptids",(String)h.get("showptids"));
		s = fixEntity(s,"siteurl",(String)h.get("siteurl"));
		s = fixEntity(s,"addresstype",(String)h.get("addresstype"));
		s = fixEntity(s,"tcusersclass",(String)h.get("tcusersclass"));
		s = fixEntity(s,"acctenb",(String)h.get("acctenb"));
		s = fixEntity(s,"gpenb",(String)h.get("gpenb"));
		s = fixEntity(s,"defroles",(String)h.get("defroles"));
		s = fixEntity(s,"startpage",(String)h.get("startpage"));
		s = fixEntity(s,"resultsdefault",(String)h.get("resultsdefault"));
		s = fixEntity(s,"disclaimerurl",(String)h.get("disclaimerurl"));
		s = fixEntity(s,"version",(String)h.get("version"));
		s = fixEntity(s,"date",(String)h.get("date"));
		s = fixEntity(s,"timout",(String)h.get("timout"));
		s = fixEntity(s,"proxyip",(String)h.get("proxyip"));
		s = fixEntity(s,"proxyport",(String)h.get("proxyport"));
		s = fixEntity(s,"proxyuser",(String)h.get("proxyuser"));
		s = fixEntity(s,"proxypassword",(String)h.get("proxypassword"));

		//The following three lines are commented out for T33 and later
		//because these elements are not present in the mirc.xml file any more.
		//s = fixElement(s,"pageheader",(String)h.get("pageheader"));
		//s = fixElement(s,"pagefooter",(String)h.get("pagefooter"));
		//s = fixElement(s,"disclaimer",(String)h.get("disclaimer"));

		//Install the servers, but fix the storageurl entity if it exists
		String servers = (String)h.get("server");
		if (servers != null) {
			String storageurl = (String)h.get("storageurl");
			if (storageurl != null) {
				storageurl = storageurl.replace("&siteurl;","");
				servers = servers.replace("&storageurl;","&siteurl;"+storageurl);
			}
			s = fixElement(s,"server",servers);
		}
		return s;
	}

	//Replace the value of an element in an XML string.
	private static String fixElement(String s, String theName, String theValue) {
		if (theValue != null) {
			String startTarget = "<" + theName ;
			String endTarget = "</" + theName + ">";
			int begin = s.indexOf(startTarget);
			if (begin >= 0) {
				int end = s.lastIndexOf(endTarget);
				s = s.substring(0,begin) + theValue + s.substring(end);
			}
		}
		return s;
	}

	//Replace the value of an entity in an XML string.
	private static String fixEntity(String s, String theName, String theValue) {
		if (theValue != null) {
			int begin = -1;
			int end;
			String name, value;
			while ((begin = s.indexOf("!ENTITY",begin+1)) != -1) {
				begin = skipWhitespace(s,begin+7);
				end = findWhitespace(s,begin);
				name = s.substring(begin,end);
				if (name.equals(theName)) {
					begin = s.indexOf("\"",end) + 1;
					end = s.indexOf("\"",begin);
					return s.substring(0,begin) + theValue + s.substring(end);
				}
			}
		}
		return s;
	}

	/**
	 * Insert a new server tag in a mirc.xml text string.
	 * @param s the mirc.xml text.
	 * @param theURL the storage service's URL.
	 * @param theName the storage service's text name.
	 * @return the updated mirc.xml text string.
	 */


	// I need to make this work, whether there is a server in the list or not



	public static String addStorageServiceElement(String s, String theURL, String theName) {
		String beginTag = "<server";
		String endTag = "</server>";
		// find the index of the first server tag, and the last /server tag
		int begin = s.indexOf(beginTag);
		int end = s.lastIndexOf(endTag);
		// if we didn't find any server elements it's an error
		if ((begin < 0) || (end < 0) || (end <= begin)) return s;
		// change the begin to the start of the line, and the end to the beginning of the line after
		begin = lineStart(s,begin);
		end = nextLine(s,end);
		// generate a string containing all the server elements
		String servers = s.substring(begin,end);
		// don't add it if it's already there
		if (servers.indexOf("\"" + theURL + "\"") >= 0) return s;
		// generate the new element
		String theElement = "  <server address=\"" + theURL + "\">\n"
											+ "    " + theName + "\n"
											+ "  </server>\n";
		// set k to the closing element of the first server tag
		int k = servers.indexOf(endTag);
		// if the first server is RSNA, put this server at the beginning, otherwise put it at the end
		if (servers.substring(0,k).indexOf("rsna") > 0) {
			servers = theElement + servers;
		} else {
			servers += theElement;
		}
		// create the new mirc.xml with the new servers element embedded
		s = s.substring(0,begin) + servers + s.substring(end);
		return s;
	}

	/**
	 * Remove the Test Storage Service from a mirc.xml text string.
	 * @param s the mirc.xml text.
	 * @return the updated mirc.xml text string.
	 */
	public static String removeTestStorageServiceElement(String s) {
		//Note, we have to be careful in looking for a test server to remove.
		//Older versions used a URL like ".../mirctest".
		//To be compatible with Tomcat 5, the URL must include a path
		//or Tomcat will redirect a POST into a GET. Thus, we have to use
		//a URL like ".../mirctest/". To make it possible to remove old
		//test services, we have to look for the old version of the URL.
		String testServer = "address=\"&siteurl;/mirctest";
		int begin = -1;
		int end;
		String name, value;
		while ((begin = s.indexOf("<server",begin+1)) != -1) {
			end = s.indexOf(">",begin);
			if (s.substring(begin,end).indexOf(testServer) >= 0) {
				end = s.indexOf("</server>",begin) + 9;
				s = s.substring(0,begin) + s.substring(end);
				begin = findNewLine(s,begin-1,-1) + 1;
				end = findNewLine(s,begin,+1);
				if (s.substring(begin,end).trim().equals(""))
					s = s.substring(0,begin) + s.substring(end+1);
				return s;
			}
		}
		return s;
	}

	/**
	 * Create a hashtable containing the values of the entities in the
	 * storage.xml file.
	 * @param s the text of the storage.xml file.
	 * @return a new hashtable containing the entities of the storage.xml file
	 * (except for the version entity), plus the contents of the author-service
	 * element.
	 */
	public static Hashtable hashStorageService(String s) {
		Hashtable h = new Hashtable();
		insertEntities(s,h);
		insertElement(s,h,"author-service",false);
		//Put in the version information from the manifest hashtable.
		insertVersion(h);
		return h;
	}

	//Put the version and date information into a hashtable
	private static void insertVersion(Hashtable h) {
		String x = (String)Installer.manifest.get("version");
		if (x == null) x = "T0";
		h.put("version",x);
		x = (String)Installer.manifest.get("date");
		if (x == null) x = "00:00:00";
		h.put("date",x);
	}

	/**
	 * Create a hashtable containing values for the specified entities.
	 * @param sitename the text name of the site.
	 * @param servletname the one-word name of the servlet (also the servlet's
	 * directory name).
	 * @param tagline the tag line to be displayed under the site name in query results.
	 * @param autoindex "static" or "dynamic".
	 * @return a new hashtable containing the values specified.
	 */
	public static Hashtable hashStorageService( String sitename, String servletname,
						 						String tagline, String autoindex) {
		Hashtable h = new Hashtable();
		h.put("sitename",sitename);
		h.put("servletname",servletname);
		h.put("tagline",tagline);
		h.put("autoindex",autoindex);
		//Put in the version information from the manifest hashtable.
		insertVersion(h);
		return h;
	}

	/**
	 * Update a storage.xml text string from the values in a hashtable.
	 * @param s the storage.xml text string.
	 * @param h the hashtable containing the entity and element values.
	 * @return the updated storage.xml text string.
	 */
	public static String updateStorageServiceConfiguration(String s, Hashtable h) {
		String key, value;
		Enumeration e = h.keys();
		while (e.hasMoreElements()) {
			key = (String)e.nextElement();
			value = (String)h.get(key);
			if (value != null) {
				if (key.equals("author-service")) s = fixElement(s,key,value);
				else s = fixEntity(s,key,value);
			}
		}
		return s;
	}

	/**
	 * Create a hashtable containing the entities in a web.xml text string.
	 * @param s the web.xml text string.
	 * @return a new hashtable containing the entities.
	 */
	public static Hashtable hashWebXML(String s) {
		Hashtable h = new Hashtable();
		insertEntities(s,h);
		return h;
	}


	/**
	 * Update a web.xml text string from the values in a hashtable.
	 * @param s the web.xml text string.
	 * @param h the hashtable containing the values of the entities.
	 * @return the updated web.xml text string.
	 */
	public static String updateWebXML(String s, Hashtable h) {
		Enumeration en = h.keys();
		String key;
		String value;
		while (en.hasMoreElements()) {
			key = (String)en.nextElement();
			value = (String)h.get(key);
			s = fixEntity(s,key,value);
		}
		return s;
	}

	/**
	 * Update a web.xml text string with a new role prefix.
	 * @param s the web.xml text string.
	 * @param p the role prefix.
	 * @return the updated web.xml text string.
	 */
	public static String setRolePrefix(String s, String p) {
		int k = 0;
		int kk;
		String entity;
		while ((k=s.indexOf("<!ENTITY",k)) != -1) {
			kk = s.indexOf(">",k);
			entity = s.substring(k,kk).replaceAll("\"SS-","\""+p+"-");
			s = s.substring(0,k) + entity + s.substring(kk);
			k += 8;
		}
		return s;
	}

	//Find the next non-whitespace character in a string.
	private static int skipWhitespace(String s, int k) {
		while ((k<s.length()) && Character.isWhitespace(s.charAt(k))) k += 1;
		return k;
	}

	//Find the next whitespace character in a string.
	private static int findWhitespace(String s, int k) {
		while ((k<s.length()) && !Character.isWhitespace(s.charAt(k))) k += 1;
		return k;
	}

	/**
	 * Find the index of the first character in the
	 * line containing a specified index.
	 * @param s the string to search.
	 * @param i the starting index.
	 * @return the index of the beginning of the current line.
	 */
	public static int lineStart(String s, int i) {
		while ((i>0) && (s.charAt(i-1) != '\n')) i--;
		return i;
	}

	/**
	 * Find the index of the first character after the
	 * next newline in a string starting at a specified index.
	 * @param s the string to search.
	 * @param i the starting index.
	 * @return the index of the beginning of the next line.
	 */
	public static int nextLine(String s, int i) {
		i++;
		while ((i<s.length()) && (s.charAt(i-1) != '\n')) i++;
		return i;
	}

	/**
	 * Get the entire line containing a specified index in a string.
	 * @param s the string to search.
	 * @param i a character index on the line.
	 * @return the entire line, including the newline at the end.
	 */
	public static String getLine(String s, int i) {
		int a = lineStart(s,i);
		int b = nextLine(s,i);
		return s.substring(a,b);
	}

	/**
	 * Replace the entire line containing a specified index in a string.
	 * @param s the string to search.
	 * @param i the starting index.
	 * @param contents the replacement for the line.
	 * @return the updated string.
	 */
	public static String replaceLine(String s, int i, String contents) {
		int a = lineStart(s,i);
		int b = nextLine(s,i);
		return s.substring(0,a) + contents + s.substring(b);
	}

	//Search for the next newline in the specified direction.
	private static int findNewLine(String s, int k, int inc) {
		while ((((k > 0) && (inc < 0)) ||
				((k < s.length()) && (inc > 0))) &&
				(s.charAt(k) != '\n')) k += inc;
		return k;
	}

}