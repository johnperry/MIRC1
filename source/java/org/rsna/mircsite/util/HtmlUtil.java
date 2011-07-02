/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.util.*;
import javax.swing.*;

/**
 * Encapsulates static methods for creating HTML elements.
 */
public class HtmlUtil {

	/**
	 * Creates an anchor element.
	 * @param href the link URL.
	 * @param text the displayed link text.
	 * @return the element string.
	 */
	public static String a(String href, String text) {
		return "<a href=\"" + href + "\">" + text+ "</ta>";
	}

	/**
	 * Creates an anchor element with a target attribute.
	 * @param href the link URL.
	 * @param target the target.
	 * @param text the displayed link text.
	 * @return the element string.
	 */
	public static String a(String href, String target, String text) {
		return "<a href=\"" + href + "\""
				+ (!target.equals("")? " target=\"" + target + "\"" : "") + "\">"
				+ text
				+ "</a>";
	}

	/**
	 * Creates a bold element.
	 * @param text the text to be bolded.
	 * @return the element string.
	 */
	public static String b(String text) {
		return "<b>" + text + "</b>";
	}

	/**
	 * Creates a body element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String body(String text) {
		return "<body>" + text + "</body>";
	}

	/**
	 * Creates a body element with attributes.
	 * @param attributes the attributes of the element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String body(String attributes, String text) {
		return "<body" + (!attributes.equals("")? " "+attributes : "") + ">" + text+ "</body>";
	}

	/**
	 * Creates a break element.
	 * @return the element string.
	 */
	public static String br() {
		return "<br/>";
	}

	/**
	 * Creates a button element.
	 * @param text the name to be displayed in the button.
	 * @return the element string.
	 */
	public static String button(String text) {
		return "<button>" + text + "</button>";
	}

	/**
	 * Creates a button element with attributes.
	 * @param attributes the attributes of the element.
	 * @param text the name to be displayed in the button.
	 * @return the element string.
	 */
	public static String button(String attributes, String text) {
		return "<button" + (!attributes.equals("")? " "+attributes : "") + ">" + text+ "</button>";
	}

	/**
	 * Creates a center element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String center(String text) {
		return "<center>" + text + "</center>";
	}

	/**
	 * Creates a div element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String div(String text) {
		return "<div>" + text + "</div>";
	}

	/**
	 * Creates a div element with attributes.
	 * @param attributes the attributes of the element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String div(String attributes, String text) {
		return "<div" + (!attributes.equals("")? " "+attributes : "") + ">" + text+ "</div>";
	}

	/**
	 * Creates an h2 element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String h2(String text) {
		return "<h2>" + text + "</h2>";
	}

	/**
	 * Creates an h2 element.
	 * @param text the contents of the element.
	 * @param className the CSS class of the element.
	 * @return the element string.
	 */
	public static String h2(String text, String className) {
		return "<h2" + (!className.equals("")?" class=\""+className+"\"":"") + ">" + text + "</h2>";
	}

	/**
	 * Creates an h3 element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String h3(String text) {
		return "<h3>" + text + "</h3>";
	}

	/**
	 * Creates a head element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String head(String text) {
		return "<head>" + text + "</head>";
	}

	/**
	 * Creates an html element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String html(String text) {
		return "<html>" + text + "</html>";
	}

	/**
	 * Creates a paragraph element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String p(String text) {
		return "<p>" + text + "</p>";
	}

	/**
	 * Creates a paragraph element.
	 * @param text the contents of the element.
	 * @param className the CSS class of the element.
	 * @return the element string.
	 */
	public static String p(String text, String className) {
		return "<p" + (!className.equals("")?" class=\""+className+"\"":"") + ">" + text + "</p>";
	}

	/**
	 * Creates a style element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String style(String text) {
		return "<style>" + text + "</style>";
	}

	/**
	 * Creates a table element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String table(String text) {
		return "<table>" + text + "</table>";
	}

	/**
	 * Creates a table element with attributes.
	 * @param attributes the attributes of the element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String table(String attributes, String text) {
		return "<table" + (!attributes.equals("")? " "+attributes : "") + ">" + text+ "</table>";
	}

	/**
	 * Creates a table data element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String td(String text) {
		return "<td>" + text + "</td>";
	}

	/**
	 * Creates a table data element with attributes.
	 * @param attributes the attributes of the element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String td(String attributes, String text) {
		return "<td" + (!attributes.equals("")? " "+attributes : "") + ">" + text+ "</td>";
	}

	/**
	 * Creates a th element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String th(String text) {
		return "<th>" + text + "</th>";
	}

	/**
	 * Creates a th element with attributes.
	 * @param attributes the attributes of the element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String th(String attributes, String text) {
		return "<th" + (!attributes.equals("")? " "+attributes : "") + ">" + text+ "</th>";
	}

	/**
	 * Creates a thead element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String thead(String text) {
		return "<thead>" + text + "</thead>";
	}

	/**
	 * Creates a title element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String title(String text) {
		return "<title>" + text + "</title>";
	}

	/**
	 * Creates a table row element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String tr(String text) {
		return "<tr>" + text + "</tr>";
	}

	/**
	 * Creates a table row element with attributes.
	 * @param attributes the attributes of the element.
	 * @param text the contents of the element.
	 * @return the element string.
	 */
	public static String tr(String attributes, String text) {
		return "<tr" + (!attributes.equals("")? " "+attributes : "") + ">" + text+ "</tr>";
	}

	/**
	 * Generate an HTML page that presents a message with a close button.
	 * @param title the page title.
	 * @param message the body of the text.
	 * @return the HTML page.
	 */
	public static String getPageWithCloseButton(String title, String message) {
		return getPage(title,message,"window.close();","OK");
	}

	/**
	 * Generate an HTML page that presents a message with a back button
	 * that goes back one page in the history.
	 * @param title the page title.
	 * @param message the body of the text.
	 * @return the HTML page.
	 */
	public static String getPageWithBackButton(String title, String message) {
		return getPageWithBackButton(title, message, -1);
	}

	/**
	 * Generate an HTML page that presents a message with a back button
	 * that goes back a variable (negative) number of pages in the history.
	 * @param title the page title.
	 * @param message the body of the text.
	 * @return the HTML page.
	 */
	public static String getPageWithBackButton(String title, String message, int howFar) {
		return getPage(title,message,"window.history.go(" + howFar + ");","Back");
	}

	/**
	 * Generate an HTML page with an action button.
	 * @param title the page title.
	 * @param message the body of the text.
	 * @param action the script of the action to be taken when the button is clicked.
	 * @param text the text label of the button.
	 * @return the HTML page.
	 */
	public static String getPage(String title, String message, String action, String text) {
		return
			html(
				head(
					title(title) +
					style("body {background-color:#e0e0e0}")
				) +
				body(
					center(
						h2(title) +
						p(message) +
						br() +
						button("style=\"width:50\" onclick=\""+action+"\"",text)
					)
				)
			);
	}

	/**
	 * Generate an HTML page with a title and body text.
	 * @param title the page title.
	 * @param body the body element contents (not including the element itself).
	 * @return the HTML page.
	 */
	public static String getPage(String title, String body) {
		return
			html(
				head(
					title(title) +
					style("body {background-color:#e0e0e0}")
				) +
				body(body)
			);
	}

	/**
	 * Generate an HTML page with a redirector to a URL.
	 * @param url the URL to which the browser will be redirected.
	 * @return the HTML page.
	 */
	public static String getRedirector(String url) {
		return
			"<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">\n" +
			"<HTML>\n" +
			"<HEAD><TITLE></TITLE></HEAD>\n" +
			"<META HTTP-EQUIV=\"Refresh\" CONTENT=\"0;url=" + url + "\">\n" +
			"<BODY></BODY>\n" +
			"</HTML>";
	}

	/**
	 * Generate an HTML page that presents a message with a close box
	 * in the upper right corner linking back to the MIRC query page.
	 * @param stylesheet the name of the CSS stylesheet to load.
	 * @param title the page title.
	 * @param message the body of the text.
	 * @param className the CSS class for the title and message.
	 * @return the HTML page.
	 */
	public static String getStyledPageWithCloseBox(
							String stylesheet,
							String title,
							String message,
							String className) {
		return
			html(
				head(
					title(title) +
					getStylesheetLink(stylesheet)
				) +
				body(
					getCloseBox() +
					center(
						h2(title,className) +
						p(message,className)
					)
				)
			);
	}

	/**
	 * Generate a style element.
	 * @param stylesheet the path to the stylesheet
	 * @return the HTML code for a style element.
	 */
	public static String getStylesheetLink(String stylesheet) {
		return "<link rel=\"stylesheet\" href=\""+stylesheet+"\" type=\"text/css\"/>";
	}

	/**
	 * Generate a close box linking to \mirc\query,
	 * using the _self target. This method calls the
	 * more general getCloseBox method.
	 * @return the HTML code for the close box div.
	 */
	public static String getCloseBox() {
		return getCloseBox("/mirc/query", "_self", "Return to the MIRC home page");
	}

	/**
	 * Generate an HTML div with a close box.
	 * The div floats right and links to a new page.
	 * @param url the URL to load.
	 * @param target the target window.
	 * @return the HTML code for the close box div.
	 */
	public static String getCloseBox(String url, String target, String title) {
		return
			"<div style=\"float:right;\">\n"
		  + " <img src=\"/mirc/images/closebox.gif\"\n"
		  + "  onclick=\"window.open('"+url+"','"+target+"');\"\n"
		  + "  title=\""+title+"\"\n"
		  + "  style=\"margin:2\"/>\n"
		  + "</div>\n";
	}


}