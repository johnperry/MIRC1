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
public class MircIndexPubDateComparator implements Comparator {

	static final int up = 1;
	static final int down = -1;
	int dir = down;

	/**
	 * Create a reverse order Comparator for lmDate values.
	 */
	public MircIndexPubDateComparator() {
		this(up);
	}

	/**
	 * Create a specified order Comparator for lmDate values.
	 */
	public MircIndexPubDateComparator(int direction) {
		if (direction >= 0) dir = up;
		else dir = down;
	}

	/**
	 * Compare.
	 */
	public int compare(Object o1, Object o2) {
		if ( (o1 instanceof MircIndexEntry) && (o2 instanceof MircIndexEntry)) {
			String d1 = ((MircIndexEntry)o1).pubdate;
			String d2 = ((MircIndexEntry)o2).pubdate;
			return dir * d1.compareTo(d2);
		}
		else return 0;
	}

}