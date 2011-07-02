/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.exportmanager;

import org.rsna.installer.SimpleInstaller;

/**
 * The ExportManager program installer, consisting of just a
 * main method that instantiates a SimpleInstaller.
 */
public class Installer {

	static String windowTitle = "ExportManager Installer";
	static String programName = "ExportManager";
	static String introString = "<p><b>ExportManager</b> implements the IHE TFCTE integration"
								+" profile for teaching files and clinical trials.</p>"
								+ "<p>This program installs and configures the software components.</p>";

	public static void main(String args[]) {
		new SimpleInstaller(windowTitle,programName,introString);
	}
}