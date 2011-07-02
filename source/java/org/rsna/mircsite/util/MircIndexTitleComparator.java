/*---------------------------------------------------------------
*  Copyright 2010 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.util.*;

/**
 * A Comparator for sorting MircIndexEntry objects.
 */
public class MircIndexTitleComparator implements Comparator {

	static final int up = 1;
	static final int down = -1;
	int dir = down;

	/**
	 * Create a forward order Comparator for title values.
	 */
	public MircIndexTitleComparator() {
		this(up);
	}

	/**
	 * Create a specified order Comparator for title values.
	 */
	public MircIndexTitleComparator(int direction) {
		if (direction >= 0) dir = up;
		else dir = down;
	}

	/**
	 * Compare.
	 */
	public int compare(Object o1, Object o2) {
		if ( (o1 instanceof MircIndexEntry) && (o2 instanceof MircIndexEntry)) {
			String o1title = ((MircIndexEntry)o1).title;
			String o2title = ((MircIndexEntry)o2).title;
			return o1title.compareTo( o2title ) * dir;
		}
		else return 0;
	}

}