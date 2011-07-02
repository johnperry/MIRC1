/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.installer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;

/**
 * The top-level class of the MIRCsite-installer.
 * This program installs and/or upgrades a MIRC site.
 */
public class Installer extends JFrame {

	/**
	 * The main method; starts the program.
	 */
	public static void main(String args[]) {
		new Installer();
	}

	/**
	 * Class constructor; creates a new installer and loads all the pages.
	 */
	public Installer() {
		//Get the manifest right now before anybody needs it.
		manifest = Configurator.hashManifest(this);
		releaseVersion = Configurator.getQueryServiceVersion();

		//Now set up the UI.
		this.getContentPane().setLayout(new BorderLayout());
		setTitle(windowTitle);
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent evt) {exit();} });
		cardLayout = new CardLayout();
		mainPanel = new JPanel(cardLayout);
		this.getContentPane().add(mainPanel, BorderLayout.CENTER);
		background = new Color(0xEBEBEB);

		componentList = new InstallerPage[] {
			new WelcomePage(),
			new SelectDestinationPage(),
			new UpgradePage(),
			new QueryServicePage(),
			new StorageServicePage(),
			new TestStorageServicePage(),
			new AdminUserPage(),
			new TomcatRealmEnablePage(),
			new TomcatSingleSignOnEnablePage(),
			new TomcatDirectoryListingDisablePage(),
			new RedirectorPage(),
			new FinishedPage()
		};

		for (int i=0; i<componentList.length; i++) {
			mainPanel.add(componentList[i], componentList[i].id);
		}

		currentComponent = 0;
		pack();
		centerFrame();
		setVisible(true);
	}

	/**
	 * Display and activate the next page of the installer.
	 */
	public static void nextPage() {
		InstallerPage page = null;
		while (currentComponent < componentList.length - 1) {
			currentComponent++;
			page = componentList[currentComponent];
			if (page.activate()) {
				cardLayout.show(mainPanel,page.id);
				break;
			}
		}
	}

	/**
	 * Exit the program.
	 */
	public static void exit() {
		System.exit(0);
	}

	//Size and position the program's window.
	private void centerFrame() {
		Toolkit t = getToolkit();
		Dimension scr = t.getScreenSize ();
		setSize(scr.width/2, scr.height/2);
		int x = (scr.width-getSize().width)/2;
		int y = (scr.height-getSize().height)/2;
		setLocation(new Point (x,y));
	}

	private static JPanel			mainPanel;
	private static CardLayout		cardLayout;
	private static InstallerPage[]	componentList;
	private static int				currentComponent;

	/** The window title. */
	public	static String			windowTitle = "MIRCsite Installer";

	/** The Tomcat root directory. */
	public	static File				tomcat = null;

	/** The Tomcat conf directory. */
	public	static File				conf = null;

	/** The Tomcat shared libraries directory. */
	public	static File				lib = null;

	/** The Tomcat webapps directory. */
	public	static File				webapps = null;

	/** The Tomcat version */
	public	static String			tomcatVersion = "5.5";

	/** The backup directory to hold files that are modified during an upgrade. */
	public	static File				backupDirectory = null;

	/** The version designation of the release being installed. */
	public	static String			releaseVersion = "";

	/** The version designation of the currently installed version (that is being upgraded). */
	public	static String			installedVersion = "";

	/** The values of the entities to be used during this installation. */
	public	static Hashtable<String,String>	queryService = null;

	/** The hashed values of the attributes in the installer's manifest. */
	public	static Hashtable<String,String>	manifest = null;

	/** The background color for use in all the pages. */
	public	static Color			background;

}
