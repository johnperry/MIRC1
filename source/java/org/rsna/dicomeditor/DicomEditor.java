/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.dicomeditor;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import org.apache.log4j.*;
import org.rsna.mircsite.anonymizer.IdTable;
import org.rsna.util.AnonymizerPanel;
import org.rsna.util.ApplicationProperties;
import org.rsna.util.DicomViewer;
import org.rsna.util.FileUtil;
import org.rsna.util.HtmlJPanel;
import org.rsna.util.Key;
import org.rsna.util.SourcePanel;

/**
 * The DicomEditor program provides a DICOM viewer and
 * element editor plus an anonymizer that can process a
 * single file, all the files in a single directory, or
 * all the files in a directory tree.
 */
public class DicomEditor extends JFrame {

    private String					windowTitle = "DicomEditor - version 22";
    private MainPanel				mainPanel;
    private JPanel					splitPanel;
    private SourcePanel				sourcePanel;
    private RightPanel				rightPanel;
    private AnonymizerPanel			anonymizerPanel;
    private DicomViewer 			viewerPanel;
    private Decoder		 			decoderPanel;
    private HtmlJPanel 				helpPanel;
    private ApplicationProperties	properties;

    public static String propfile 			= "dicomeditor.properties";
    public static String idtablepropfile 	= "idtable.properties";
    public static String dicomScriptFile	= "dicom-anonymizer.properties";
    public static String lookupTableFile	= "lookup-table.properties";
    public static String xmlScriptFile		= "xml-anonymizer.script";
    public static String helpfile 			= "help.html";

	Color background = Color.getHSBColor(0.58f, 0.17f, 0.95f);

	/**
	 * The main method to start the program. If the args array has no parameters,
	 * the program starts normally.
	 * <p>
	 * If the args array contains exactly two parameters, the program
	 * attempts to open the second parameter as a DICOM object and view it.
	 * The two command line parameters must be:
	 * <ol>
	 * <li>the path to the DicomEditor directory</li>
	 * <li>the path to the DICOM object to be viewed</li>
	 * </ol>
	 * This is a kludge to allow a user to double-click an image
	 * and have it open immediately in the Viewer.
	 *
	 * To make this work on a Windows system, go to the Folder Options and create
	 * a DCM file type. Then click the Advanced button and create an OPEN action
	 * with an application string like this:
	 * <p>
	 * "Path-to-javaw.exe" -Xms128m -Xmx512m -jar "path-to-DicomEditor.jar" "path-to-DicomEditor" "%1"
	 * @param args the list of arguments from the command line.
	 */
    public static void main(String args[]) {
		Logger.getRootLogger().addAppender(
				new ConsoleAppender(
					new PatternLayout("%d{HH:mm:ss} %-5p [%c{1}] %m%n")));
		Logger.getRootLogger().setLevel(Level.WARN);
        if (args.length == 2) {
			//When the program is started from the double-click of a DICOM file, the
			//user.dir System property doesn't point to the directory where the
			//DicomEditor program is located, so we have to change the path strings
			//for the properties files and the help file.
			//args[0] = the DicomEditor directory
			File programDir = new File(args[0]);
			propfile = (new File(programDir,propfile)).getAbsolutePath();
			idtablepropfile = (new File(programDir,idtablepropfile)).getAbsolutePath();
			dicomScriptFile = (new File(programDir,dicomScriptFile)).getAbsolutePath();
			helpfile = (new File(programDir,helpfile)).getAbsolutePath();

			//Now instantiate the DicomEditor.
			DicomEditor editor = new DicomEditor();

			//Finally, select the viewer tab and open the image.
			//args[1] = the file to open
			editor.mainPanel.tabbedPane.setSelectedIndex(1);
			editor.viewerPanel.openImage(new File(args[1]));
		}
		else {
			//This is a normal start for the program.
			DicomEditor editor = new DicomEditor();
		}
    }

	/**
	 * Class constructor; creates the program main class.
	 */
    public DicomEditor() {
		properties = new ApplicationProperties(propfile);
		setTitle(windowTitle);
		addWindowListener(new WindowCloser());
		sourcePanel = new SourcePanel(properties,"Directory",background);
		rightPanel =
			new RightPanel(
				properties, sourcePanel,
				dicomScriptFile, lookupTableFile,
				xmlScriptFile, background);
		JSplitPane jSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,sourcePanel,rightPanel);
		jSplitPane.setResizeWeight(0.5D);
		jSplitPane.setContinuousLayout(true);
		splitPanel = new JPanel(new BorderLayout());
		splitPanel.add(jSplitPane,BorderLayout.CENTER);
		anonymizerPanel = new AnonymizerPanel(dicomScriptFile,properties,background);
		viewerPanel = new DicomViewer(null,dicomScriptFile,background);
		decoderPanel = new Decoder(viewerPanel, background);
		helpPanel = new HtmlJPanel(FileUtil.getFileText(new File(helpfile)));
		mainPanel = new MainPanel(
			splitPanel,
			viewerPanel,
			anonymizerPanel,
			decoderPanel,
			helpPanel);
		sourcePanel.addFileListener(viewerPanel);
		getContentPane().add(mainPanel, BorderLayout.CENTER);
		pack();
		centerFrame();
		setVisible(true);
		boolean prompt = IdTable.requiresPrompt(idtablepropfile);
		String key = Key.getEncryptionKey(this,prompt);
		if (!IdTable.initialize(idtablepropfile,key)) {
			JOptionPane.showMessageDialog(
				this,
				  "The key did not match the IdTable.\n"
				+ "Restart the program to try again.\n");
			System.exit(0);
		}
		//Disable storeLater because we catch it in the WindowCloser.
		IdTable.setStoreLaterEnable(false);
		//Check the version of the IdTable
		int idVersion = IdTable.getVersion();
		if (idVersion != 1) {
			String siteid = JOptionPane.showInputDialog(
				this,
				"The remapping table (version "+idVersion+") is not\n"+
				"the latest version. Enter the Site ID parameter below\n"+
				"to upgrade the table, or click Cancel to exit.");
			if (siteid != null) IdTable.convert(siteid);
			else System.exit(0);
		}
    }

    class WindowCloser extends WindowAdapter {
		public void windowClosing(WindowEvent evt) {
			properties.store();
			IdTable.storeNow(false); //only store if the table is dirty
			System.exit(0);
		}
    }

    private void centerFrame() {
		Toolkit t = getToolkit();
		Dimension scr = t.getScreenSize ();
		setSize(scr.width*4/5, scr.height*2/3);
		setLocation (new Point ((scr.width-getSize().width)/2,(scr.height-getSize().height)/2));
    }

	class MainPanel extends JPanel {
		public JTabbedPane tabbedPane;
		public MainPanel(JPanel source,
						 JPanel viewer,
						 JPanel script,
						 JPanel decoder,
						 JPanel help) {
			super();
			this.setLayout(new BorderLayout());
			tabbedPane = new JTabbedPane();
			tabbedPane.addTab("Directory",source);
			tabbedPane.addTab("DICOM Viewer",viewer);
			tabbedPane.addTab("DICOM Anonymizer",script);
			tabbedPane.addTab("Decoder",decoder);
			tabbedPane.addTab("Help",help);
			this.add(tabbedPane,BorderLayout.CENTER);
		}
	}

}
