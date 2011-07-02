/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.installer;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

/**
 * An installer page that displays an HTML page.
 */
public class InstallerHtmlPage extends InstallerPage {

	JEditorPane htmlPane;

	/**
	 * Class constructor; creates an HTML page that has is configured
	 * not to be the last page on the installer.
	 */
	public InstallerHtmlPage() {
		this(true);
	}

	/**
	 * Class constructor; creates an HTML page with or without a next page.
	 * @param next true if there is a next page; false otherwise.
	 */
	public InstallerHtmlPage(boolean next) {
		super(next);
		JScrollPane scrollPane = new JScrollPane();
		htmlPane = new JEditorPane();
		htmlPane.setEditable(false);
		htmlPane.setContentType("text/html");
		htmlPane.setBackground(Installer.background);
		scrollPane.setViewportView(htmlPane);
		this.add(scrollPane,BorderLayout.CENTER);
	}

	/**
	 * Load the HTML text into the page and scroll to the top.
	 * @param text the HTML text.
	 */
	public void setText(String text) {
		htmlPane.setText(text);
		htmlPane.setCaretPosition(0);
	}

	/**
	 * Load a page from a resource into the editor and scroll to the top.
	 * @param url the URL of the resource.
	 */
	public void setPage(URL url) {
		try {
			htmlPane.setPage(url);
			htmlPane.setCaretPosition(0);
		}
		catch (Exception e) {
			JOptionPane.showMessageDialog(this,"setPage exception:\n\n" + e.getMessage());
		}
	}

	/**
	 * Set the position of the caret on the page.
	 */
	public void setCaretPosition(int position) {
		htmlPane.setCaretPosition(position);
	}

	/**
	 * Convenience method for all subclasses to use in making the top part of an HTML page.
	 */
	public String makeHeader() {
		return "<html><head></head><body>\n"
			+	"	<center>\n"
			+	"		<h1 style=\"color:red\">" + Installer.windowTitle + "</h1>\n"
			+			"<b>Release " + Installer.releaseVersion + "</b>\n"
			+	"	</center>\n";
	}

	/**
	 * Convenience method for all subclasses to use in making the last part of an HTML page.
	 */
	public String makeFooter() {
		return "</body></html>";
	}

}
