/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.manifest;

import org.rsna.installer.SimpleInstaller;

/**
 * The FieldCenter program installer, consisting of just a
 * main method that instantiates a SimpleInstaller.
 */
public class Installer {

	static String windowTitle = "ManifestGenerator Installer";
	static String programName = "ManifestGenerator";
	static String introString = "<p><b>ManifestGenerator</b> constructs IHE TFCTE manifests"
								+" for testing TFCTE integration profile implementations.</p>";

	public static void main(String args[]) {
		new SimpleInstaller(windowTitle,programName,introString);
	}
}