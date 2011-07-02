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
 * The Welcome page for the MIRCsite Installer.
 */
public class WelcomePage extends InstallerHtmlPage {

	/**
	 * Class constructor; create the page with the default Next button.
	 */
	public WelcomePage() {
		super();
		setButtons();
		setText(getWelcomePage());
		id = "welcome";
	}

	//The welcome HTML page.
	private String getWelcomePage() {
		String page = makeHeader();
		page += "<p><b>MIRC</b> is a project of the RSNA, creating "
					+ "an online community of medical imaging resources.</p>";
		page += "<p>This program installs and configures the software components "
					+ "required to run a MIRC site on an existing instance of Tomcat.</p>";
		page += "<p>This program allows you to upgrade an existing MIRC site or "
					+ "to install a new one.</p>";
		page += "<p><b>Ensure that Tomcat is not running</b> "
					+ "and then click <b>Next</b>.</p>";
		page += "<p><center>Copyright 2010: RSNA</center></p>";
		page += makeFooter();
		return page;
	}

}
