/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.log;

import java.util.*;

/**
 * A recycling log of the last 200 events.
 */
public class Log {

	private static int logSize = 100;
	private static int next = 0;
	private static int size = 0;

	private static String[] logStrings = new String[logSize];

	/**
	 * Get an HTML table string with the current contents of the log.
	 * @return the log contents in an HTML table element.
	 */
	public static synchronized String getLog() {
		return "<table>" + getText() + "</table>";
	}

	/**
	 * Clear the log.
	 */
	public static synchronized void clearLog() {
		size = 0;
	}

	/**
	 * Set the depth of the log.
	 * @param depth the depth of the log.
	 */
	public static synchronized void setDepth(int depth) {
		if (depth < 1) return;
		if (depth != logSize) {
			logStrings = new String[depth];
			logSize = depth;
			next = 0;
			size = 0;
		}
	}

	/**
	 * Get the depth of the log.
	 * @return the depth of the log.
	 */
	public static int getDepth() {
		return logSize;
	}

	//Get the text in the log.
	private static String getText() {
		int first = (next - size + logSize) % logSize;
		StringBuffer sb = new StringBuffer(10000);
		for (int i=0; i<size; i++) {
			sb.append(logStrings[ (first + i) % logSize ]);
		}
		return sb.toString();
	}

	/**
	 * Add a text entry to the log, automatically including the date and time.
	 * @param text the text to add to the log.
	 */
	public static synchronized void message(String text) {
		logStrings[next] = "<tr>"
							+ "<td width=\"150\" valign=\"top\">"+logTime()+"</td>"
							+ "<td>"+text+"</td>"
							+ "</tr>";
		next = (next + 1) % logSize;
		size = (size < logSize) ? size+1 : logSize;
	}

	//Get the date and time for a log entry.
	private static String logTime() {
		GregorianCalendar now = new GregorianCalendar();
		return now.get(now.YEAR) + "-" +
				two(now.get(now.MONTH)+1) + "-" +
				two(now.get(now.DAY_OF_MONTH)) + " " +
				two(now.get(now.HOUR_OF_DAY)) + ":" +
				two(now.get(now.MINUTE)) + ":" +
				two(now.get(now.SECOND));
	}

	//Make a two-digit, leading-zero integer entry
	private static String two(int i) {
		if (i < 10) return "0" + i;
		return "" + i;
	}

}