/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.Hashtable;
import org.apache.log4j.Logger;

/**
 * A singleton class for managing Tomcat users and MIRC groups together.
 * The getInstance method of this class is used to obtain an instance that
 * matches the MIRC implementation. The purpose of this approach is to make
 * it easy to switch methods of managing users without affecting other code.
 * The reason this is a singleton is to make it easier to solve the critical
 * section problem when multiple users may be updating their account information
 * simultaneously.
 */
public abstract class TomcatUsers {

	static final Logger logger = Logger.getLogger(TomcatUsers.class);
	protected static TomcatUsers tcUsersInstance = null;

	/**
	 * Protected constructor so this class can't be instantiated directly.
	 */
	protected TomcatUsers() { }

	/**
	 * Get a TomcatUsers instance that matches the MIRC implementation.
	 * @param file any file within the Tomcat/webapps directory tree.
	 * @return the TomcatUsers object for managing Tomcat users and MIRC groups.
	 */
	public static TomcatUsers getInstance(File file) {
		if (tcUsersInstance == null) {
			//Get the TomcatUsers implementation class name from the configuration.
			MircConfig mircConfig = MircConfig.getInstance(file);
			String className = mircConfig.getTomcatUsersClassName();
			try {
				Class theClass = Class.forName(className);
				Class[] args = new Class[] { Class.forName("java.io.File") };
				Constructor constructor = theClass.getConstructor(args);
				Object[] argObjects = new Object[] { file };
				tcUsersInstance = (TomcatUsers)constructor.newInstance(argObjects);
			}
			catch (Exception ex) {
				logger.warn(
					"Unable to load the TomcatUsers class: " + className, ex);
			}
		}
		return tcUsersInstance;
	}

	/**
	 * Determine whether a specific role exists.
	 * @return true if the role exists; false otherwise.
	 */
	public abstract boolean hasRole(String rolename);

	/**
	 * Determine whether a specific group exists.
	 * @return true if the group exists; false otherwise.
	 */
	public abstract boolean hasGroup(String groupname);

	/**
	 * Get a specific Tomcat user.
	 * @return the user or null if unable.
	 */
	public abstract TomcatUser getTomcatUser(String username);

	/**
	 * Get a specific MIRC group.
	 * @return the group or null if unable.
	 */
	public abstract MircGroup getMircGroup(String groupname);

	/**
	 * Add a Tomcat user to the database or update the user if it exists.
	 */
	public abstract void addTomcatUser(TomcatUser user);

	/**
	 * Add a MIRC group to the database or update the group if it exists.
	 */
	public abstract void addMircGroup(MircGroup group);

}

