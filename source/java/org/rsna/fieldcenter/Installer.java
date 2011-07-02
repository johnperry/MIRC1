/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.fieldcenter;

import org.rsna.installer.SimpleInstaller;

/**
 * The FieldCenter program installer, consisting of just a
 * main method that instantiates a SimpleInstaller.
 */
public class Installer {

	static String windowTitle = "FieldCenter Installer";
	static String programName = "FieldCenter";
	static String introString = "<p><b>FieldCenter</b> provides image transport for clinical trials.</p>"
								+ "<p>This program installs and configures the software components "
								+ "required to transmit images from a clinical trial field "
								+ "center to a principal investigator's MIRC site.</p>";

	public static void main(String args[]) {
		new SimpleInstaller(windowTitle,programName,introString);
	}
}