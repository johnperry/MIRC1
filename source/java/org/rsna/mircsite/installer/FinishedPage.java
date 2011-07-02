/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.installer;

import javax.swing.*;
import java.io.*;
import java.net.*;

/**
 * The last page of the MIRC installer.
 */
public class FinishedPage extends InstallerHtmlPage {

	/**
	 * Class constructor; create the page with the Finished button
	 */
	public FinishedPage() {
		super(false);
		setButtons();
		id = "finished";
	}

	/**
	 * Create the page and display it.
	 */
	public boolean activate() {
		setText(getAllFinishedPage());
		return true;
	}

	//Make the HTML text of the page.
	private String getAllFinishedPage() {
		String page = makeHeader();
		page += "<p>The installation is complete.</p>";
		File[] files = Installer.backupDirectory.listFiles();
		if (files.length > 0) {
			page += "<p>Backups of all configuration files that were updated "
						+ "have been stored in: <br/><br/><b><center>"
						+ Installer.backupDirectory.getAbsolutePath()
						+ "</center></b></p>";
		}
		page += "<p>Be sure to restart Tomcat.</p>";
		page += "<p>Click <b>Finished</b> to exit the installer.</p>";
		page += makeFooter();
		return page;
	}

}
