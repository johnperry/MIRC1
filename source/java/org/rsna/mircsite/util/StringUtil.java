/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.io.*;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Properties;
import org.apache.log4j.Logger;

/**
 * Encapsulates static methods for working with strings. String methods
 * specifically for working with XML strings are found in XmlStringUtil.
 */
public class StringUtil {

	static final Logger logger = Logger.getLogger(StringUtil.class);

	/**
	 * Makes a string that defines a path from the root of the
	 * storage service's documents tree to a specific document
	 * directory. The path has the form: YYYY/MM/DDhhmmsssss
	 * where the values come from the current time and the slash
	 * character is actually either a slash or backslash, depending
	 * on the platform. This groups documents by month and year,
	 * keeping too many documents from appearing in any one directory.
	 * @return the path string.
	 */
	public static String makePathFromDate() {
		Calendar now = Calendar.getInstance();
		return intToString(now.get(Calendar.YEAR), 4)
						 + File.separator
						 + intToString(now.get(Calendar.MONTH) + 1, 2)
						 + File.separator
						 + intToString(now.get(Calendar.DAY_OF_MONTH), 2)
						 + intToString(now.get(Calendar.HOUR_OF_DAY), 2)
						 + intToString(now.get(Calendar.MINUTE), 2)
						 + intToString(now.get(Calendar.SECOND), 2)
						 + intToString(now.get(Calendar.MILLISECOND), 3);
	}

	/**
	 * Makes a string for the current time in the form: YYYYMMDDhhmmsssss.
	 * This method is suitable for defining a filename..
	 * @return the string.
	 */
	public static String makeNameFromDate() {
		Calendar now = Calendar.getInstance();
		return intToString(now.get(Calendar.YEAR), 4)
						 + intToString(now.get(Calendar.MONTH) + 1, 2)
						 + intToString(now.get(Calendar.DAY_OF_MONTH), 2)
						 + intToString(now.get(Calendar.HOUR_OF_DAY), 2)
						 + intToString(now.get(Calendar.MINUTE), 2)
						 + intToString(now.get(Calendar.SECOND), 2)
						 + intToString(now.get(Calendar.MILLISECOND), 3);
	}

	/**
	 * Makes a datetime string for the current time in the standard form:
	 * YYYY-MM-DDThh:mm:ss.
	 * @return the string.
	 */
	public static String getDateTime() {
		return getDateTime(-1,"T");
	}

	/**
	 * Makes a datetime string for the current time in the standard form:
	 * YYYY-MM-DD[sep]hh:mm:ss, where [sep] is the supplied separator string.
	 * @return the string.
	 */
	public static String getDateTime(String sep) {
		return getDateTime(-1,sep);
	}

	/**
	 * Makes a datetime string for the specified time in the standard form:
	 * YYYY-MM-DDThh:mm:ss.
	 * @param time the time in milliseconds.
	 * @return the string.
	 */
	public static String getDateTime(long time) {
		return getDateTime(time,"T");
	}

	/**
	 * Makes a datetime string for the specified time in the form:
	 * YYYY-MM-DD[sep]hh:mm:ss, where [sep] is the supplied separator string.
	 * @param time the time in milliseconds.
	 * @param sep the separator string to insert between the date and the time.
	 * @return the string.
	 */
	public static String getDateTime(long time, String sep) {
		Calendar now = Calendar.getInstance();
		if (time != -1) now.setTimeInMillis(time);
		return intToString(now.get(Calendar.YEAR), 4)
						 + "-"
						 + intToString(now.get(Calendar.MONTH) + 1, 2)
						 + "-"
						 + intToString(now.get(Calendar.DAY_OF_MONTH), 2)
						 + sep
						 + intToString(now.get(Calendar.HOUR_OF_DAY), 2)
						 + ":"
						 + intToString(now.get(Calendar.MINUTE), 2)
						 + ":"
						 + intToString(now.get(Calendar.SECOND), 2);
	}

	/**
	 * Makes a date string for the current time in the standard form: YYYY-MM-DD.
	 * @return the string.
	 */
	public static String getDate() {
		Calendar now = Calendar.getInstance();
		return intToString(now.get(Calendar.YEAR), 4)
						 + "-"
						 + intToString(now.get(Calendar.MONTH) + 1, 2)
						 + "-"
						 + intToString(now.get(Calendar.DAY_OF_MONTH), 2);
	}

	/**
	 * Makes a time string for the current time in the standard form: hh:mm:ss.
	 * @return the string.
	 */
	public static String getTime() {
		Calendar now = Calendar.getInstance();
		return intToString(now.get(Calendar.HOUR_OF_DAY), 2)
						 + ":"
						 + intToString(now.get(Calendar.MINUTE), 2)
						 + ":"
						 + intToString(now.get(Calendar.SECOND), 2);
	}

	/**
	 * Converts a positive int to a String with at least n digits,
	 * padding with leading zeroes.
	 * @param theValue the int to be converted.
	 * @param nDigits the number of digits to return.
	 * @return the converted value.
	 */
	public static String intToString(int theValue, int nDigits) {
		String s = Integer.toString(theValue);
		int k = nDigits - s.length();
		for (int i=0; i<k; i++) s = "0" + s;
		return s;
	}

	/**
	 * Inserts commas every 3 characters, starting at the low-order
	 * end of a long numeric string.
	 * @param s the string to be modified.
	 * @return the modified string.
	 */
	public static String insertCommas(String s) {
		int n = s.length();
		while ((n=n-3) > 0) s = s.substring(0,n) + "," + s.substring(n);
		return s;
	}

	/**
	 * Parses a string into a base-10 int, returning 0 if an error occurs.
	 * @param theString the string to be parsed.
	 * @return the parsed value, or zero if an error occurred in parsing.
	 */
	public static int getInt(String theString) {
		if (theString == null) return 0;
		if (theString.equals("")) return 0;
		try { return Integer.parseInt(theString); }
		catch (NumberFormatException e) { return 0; }
	}

	/**
	 * Skips over whitespace in a string.
	 * @param theString the string.
	 * @param index the starting position in the string.
	 * @return the position of the next non-whitespace character,
	 * of the length of the string the rest of the string is whitespace.
	 */
	public static int skipWhitespace(String theString, int index) {
		while ((index < theString.length()) &&
					Character.isWhitespace(theString.charAt(index)))
			index++;
		return index;
	}

	/**
	 * Finds the next whitespace in a string.
	 * @param theString the string.
	 * @param index the starting position in the string.
	 * @return the position of the next whitespace character,
	 * or the length of the string if no whitespace is found.
	 */
	public static int findWhitespace(String theString, int index) {
		while ((index < theString.length()) &&
					!Character.isWhitespace(theString.charAt(index)))
			index++;
		return index;
	}

	/**
	 * Skips the current word in a string. A word is defined to start with a
	 * character that Java accepts as the start of an identifier and continue
	 * until a character that Java does not allow to appear in an identifier.
	 * The method must be called with the index pointing to the beginning of a word.
	 * @param theString the string.
	 * @param index the starting position in the string.
	 * @return the position of the next character after the word,
	 * of the length of the string if the word goes to the end of the string.
	 */
	public static int skipWord(String theString, int index) {
		if (!Character.isJavaIdentifierStart(theString.charAt(index++))) return -1;
		while (index < theString.length()) {
			if (!Character.isJavaIdentifierPart(theString.charAt(index))) return index;
			index++;
		}
		return index;
	}

	/**
	 * Positions the index to the first character in the current line.
	 * @param theString the string.
	 * @param index the starting position in the string.
	 * @return the position of the first character in the current line.
	 */
	public static int lineStart(String theString, int index) {
		while ((index>0) && (theString.charAt(index-1) != '\n')) index--;
		return index;
	}

	/**
	 * Positions the index to the first character of the next line.
	 * @param theString the string.
	 * @param index the starting position in the string.
	 * @return the position of the first character of the next line.
	 */
	public static int nextLine(String theString, int index) {
		index++;
		while ((index < theString.length()) && (theString.charAt(index-1) != '\n'))
			index++;
		return index;
	}

	/**
	 * Returns the contents of the current line as a string.
	 * @param theString the string.
	 * @param index the starting position in the string.
	 * @return a string containing all the characters of the current line,
	 * including the newline at the end, if present.
	 */
	public static String getLine(String theString, int index) {
		int a = lineStart(theString,index);
		int b = nextLine(theString,index);
		return theString.substring(a,b);
	}

	/**
	 * Replaces the contents of the current line.
	 * @param theString the string.
	 * @param index the starting position in the string.
	 * @param replacement the replacement text for the current line.
	 * @return the updated string.
	 */
	public static String replaceLine(String theString, int index, String replacement) {
		int a = lineStart(theString,index);
		int b = nextLine(theString,index);
		return theString.substring(0,a) + replacement + theString.substring(b);
	}

	/**
	 * Replaces coded identifiers with values from a hashtable.
	 * Identifiers are coded as ${name}. The identifier is replaced
	 * by the string value in the hashtable, using the name as the key.
	 * @param string to be processed.
	 * @param table the table of replacement strings
	 */
	public static String replace(String string, Properties table) {
		try {
			Pattern pattern = Pattern.compile("\\$\\{\\w+\\}");
			Matcher matcher = pattern.matcher(string);
			StringBuffer sb = new StringBuffer();
			while (matcher.find()) {
				String group = matcher.group();
				String key = group.substring(2, group.length()-1).trim();
				String repl = table.getProperty(key);
				if (repl == null) repl = matcher.quoteReplacement(group);
				matcher.appendReplacement(sb, repl);
			}
			matcher.appendTail(sb);
			return sb.toString();
		}
		catch (Exception ex) {
			logger.warn(ex);
			return string;
		}
	}

}

