/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

/**
 * A class to encapsulate one MIRC group.
 */
public class MircGroup {

	public String groupname;
	public String password;

	/**
	 * Construct a new MircGroup.
	 * @param groupname the name of the group, which is used as a role name
	 * in its members' TomcatUser objects.
	 * @param password the password for the group, which is required to be known
	 * by anyone joining the group.
	 */
	public MircGroup(String groupname, String password) {
		this.groupname = groupname;
		this.password = password;
	}

	/**
	 * Construct a new MircGroup.
	 * @param group a MircGroup to be cloned in a new object.
	 */
	public MircGroup(MircGroup group) {
		this.groupname = group.groupname;
		this.password = group.password;
	}

}

