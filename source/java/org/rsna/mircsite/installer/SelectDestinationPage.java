/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.installer;

import java.io.*;
import javax.swing.*;
import java.awt.event.*;
import java.util.*;

/**
 * The page allowing the user to select where to install the MIRC site.
 */
public class SelectDestinationPage extends InstallerHtmlPage {

	/**
	 * Class constructor; set up a Browse button.
	 */
	public SelectDestinationPage() {
		super();
		JButton browseButton = new JButton("Browse...");
		browseButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { browse(); }
		});
		setButtons(new JButton[] {browseButton});
		setNextEnabled(false);
		Installer.tomcat = null;
		id = "seldest";
	}

	/**
	 * Try to find Tomcat. Display a page with the result and allow
	 * the user to browse to either find Tomcat or find a different instance.
	 */
	public boolean activate() {
		String page;
		File root = new File(System.getProperty("user.dir"));
		root = new File(root.getAbsolutePath());
		File parent = root;
		while ( (parent = root.getParentFile()) != null) root = parent;
		if ( (Installer.tomcat = findTomcat(root, 3)) == null ) {
			Installer.backupDirectory = null;
			page = tomcatNotFoundPage();
		}
		else {
			setInstallerDirectories();
			page = tomcatFoundPage(Installer.tomcat);
			this.setNextEnabled(true);
		}
		setText(page);
		return true;
	}

	private void setInstallerDirectories() {
		File sharedLib = new File(Installer.tomcat, "shared/lib");
		File lib = new File(Installer.tomcat, "lib");
		if (sharedLib.exists() && !lib.exists()) {
			Installer.tomcatVersion = "5.5";
			Installer.lib = sharedLib;
		}
		else {
			Installer.tomcatVersion = "6";
			Installer.lib = lib;
		}
		System.out.println("   Tomcat version is "+Installer.tomcatVersion);
		Installer.conf = new File(Installer.tomcat, "conf");
		Installer.webapps = new File(Installer.tomcat, "webapps");
		setBackupDirectory();
	}

	//Create a backup directory for this installation.
	//The backup directory is a child of the backups directory
	//which is a child of Tomcat.
	private void setBackupDirectory() {
		File backups = new File(Installer.tomcat, "backups");
		Calendar c = new GregorianCalendar();
		String date =
			toString(c.get(Calendar.YEAR),4) +
			toString(c.get(Calendar.MONTH)+1,2) +
			toString(c.get(Calendar.DAY_OF_MONTH),2) + "-" +
			toString(c.get(Calendar.HOUR_OF_DAY),2) +
			toString(c.get(Calendar.MINUTE),2) +
			toString(c.get(Calendar.SECOND),2) + File.separator;
		Installer.backupDirectory = new File(backups, date);
		Installer.backupDirectory.mkdirs();
	}

	//Convert an integer to a String of a specified width, with leading zeros.
	private String toString(int i, int places) {
		String s = Integer.toString(i);
		while (s.length() < places) s = "0" + s;
		return s;
	}

	//Get a directory from the file chooser and determine whether it is Tomcat,
	//then set the page to be displayed.
	private void browse() {
		String page;
		File dirFile = getDirectory(new File(System.getProperty("user.dir")));
		if (dirFile != null) {
			if (FileInstaller.contentsCheck(dirFile, names)) {
				Installer.tomcat = dirFile;
				setInstallerDirectories();
				page = tomcatSelectedPage(Installer.tomcat);
				this.setNextEnabled(true);
			}
			else {
				this.setNextEnabled(false);
				Installer.tomcat = null;
				Installer.backupDirectory = null;
				page = notTomcatPage(dirFile);
			}
			setText(page);
		}
	}

	//Get a directory from the file chooser.
	private File getDirectory(File startDir) {
		if (chooser == null) {
			chooser = new JFileChooser();
			chooser.setCurrentDirectory(startDir);
		}
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			File f = chooser.getSelectedFile();
			return f;
		}
		return null;
	}

	//Find the Tomcat instance. Start by looking for it in the Program Files
	//directory in case it is a standard installation on a Windows system.
	//If it isn't there, then walk a specified number of levels down the
	//directory tree on the current root to see if Tomcat is around.
	private File findTomcat(File dir, int level) {
		//First look in the suggested place
		File tomcat = walkTree(dir, level);
		if (tomcat != null) return tomcat;

		//No luck there, try C:/Program Files if it's there.
		File programFiles = new File("C:/Program Files");
		if (programFiles.exists()) {
			tomcat = walkTree(programFiles, 2);
		}
		if (tomcat != null) return tomcat;

		//No luck there, try C:/Program Files (x86) if it's there.
		programFiles = new File("C:/Program Files (x86)");
		if (programFiles.exists()) return walkTree(programFiles, 2);
		else return null;
	}

	//Walk the tree under a directory and find Tomcat.
	private File walkTree(File dir, int level) {
		if (dir == null) return null;
		if (!dir.exists()) return null;
		if (!dir.isDirectory()) return null;
		if (dir.getName().toLowerCase().contains("windows")) return null;

		System.out.println("Looking for Tomcat in "+dir);

		//See if this directory is Tomcat.
		String name = dir.getName().toLowerCase();
		if (name.contains("tomcat") || name.contains("instance")) {
			System.out.println("   found a directory that might be it");
			boolean check = FileInstaller.contentsCheck(dir, names);
			System.out.println("   " + (check ? "looks good" : "not this one"));
			if (check) return dir;
		}

		//It's not; see if it contains Tomcat
		if (level > 0) {
			File[] files = dir.listFiles();
			if (files != null) {
				for (int i=0; i<files.length; i++) {
					if ((files[i] != null) && files[i].exists() && files[i].isDirectory()) {
						File tomcat = walkTree(files[i], level-1);
						if (tomcat != null) return tomcat;
					}
				}
			}
		}
		return null;
	}

	//The list of directories which must be present to believe
	//that this is an instance of Tomcat's root directory.
	static final String[] names = new String[] {"conf", "webapps"};

	//The file chooser. Keep this around so that the user can reuse it if necessary.
	private JFileChooser chooser = null;

	//The various HTML pages.

	private String tomcatNotFoundPage() {
		String page = makeHeader();
		page += "<p>The installer did not find an instance of Tomcat.</p>";
		page +=	"<p>Click <b>Browse</b>, navigate to the Tomcat instance directory, "
					+ "and click <b>Open</b> (on Windows or Linux) or <b>Choose</b> (on Mac).</p>";
		return page;
	}

	private String tomcatFoundPage(File directory) {
		String page = makeHeader();
		page += "<p>The installer found an instance of Tomcat in the following directory:</p>";
		page += body(directory);
		return page;
	}

	private String notTomcatPage(File directory) {
		String page = makeHeader();
		page += "<p>The directory you have selected is not an instance of Tomcat:</p>";
		page += "<center style=\"color:red\"><b>" + directory.getAbsolutePath() + "</b></center>";
		page +=	"<p>Click <b>Browse</b>, navigate to the Tomcat instance directory, "
					+ "and click <b>Open</b> (on Windows or Linux) or <b>Choose</b> (on Mac).</p>";
		return page;
	}

	private String tomcatSelectedPage(File directory) {
		String page = makeHeader();
		page += "<p>You have selected the following instance of Tomcat:</p>";
		page += body(directory);
		return page;
	}

	private String body(File directory) {
		String page = "";
		page += "<center style=\"color:red\"><b>" + directory.getAbsolutePath() + "</b></center>";
		page +=	"<p>If you want to install the MIRC site software in this location, "
					+ "click <b>Next</b>.</p>";
		page += "<p>If you want to select a different instance of Tomcat in which to install "
					+ "the MIRC site software, click "
					+	"<b>Browse</b>, navigate to the desired directory, and click <b>Open</b> "
					+ "(on Windows or Linux) or <b>Choose</b> (on Mac).</p>";
		page += makeFooter();
		return page;
	}

}

