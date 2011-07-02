package org.rsna.mircsite.installer.filemover;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JFileChooser;


/**
 * This is the only page in the application
 * @author RBoden
 *
 */
public class WelcomePage extends BasePage {
	
	private static final long serialVersionUID = 1231234123124123l;
	
	private static final String TOMCAT_ERROR = "<font color=red>The directory you have selected is not an instance of Tomcat, please select another</font></p>";	
	
	private static final String HEADER = "<html><head></head><body><center><h1 style=\"color:red\">MIRC File Mover</h1></center><p>This program will copy your files from an old installation of tomcat, to a new one.</p><p><b>Ensure that Tomcat is not running</b>, and select your source, and destination tomcat installations using the buttons below, and then click <b>Backup</b></p>";
	
	private static final String FOOTER = "<p><center>Copyright 2005: RSNA</center></p></body></html>";
	
	private String sourceDirectory = null;
	private String destinationDirectory = null;
	public boolean isComplete = false; 
	private boolean isErrorCopying = false;
	private String currentFile = null;
		

	public WelcomePage(){
		super();
		// setup buttons (including listeners)
		JButton sourceButton = new JButton("Change Source...");
		sourceButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				sourceDirectory = browse();
				htmlPane.setText(getText());
				htmlPane.setCaretPosition(0);
			}
		});
		buttonPanel.add(sourceButton);
		JButton destButton = new JButton("Change Destination...");
		destButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { 
				destinationDirectory = browse();
				htmlPane.setText(getText());
				htmlPane.setCaretPosition(0);
			}
		});
		buttonPanel.add(destButton);		
		JButton backupButton = new JButton("Backup");
		backupButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { backup(); }
		});
		buttonPanel.add(backupButton);
		
		File defaultDir = new File(File.separator);
		sourceDirectory = FileUtil.findParticularTomcatVersion("4.", defaultDir);
		destinationDirectory = FileUtil.findParticularTomcatVersion("5.", defaultDir);
	
		
		// setup text
		htmlPane.setText(getText());
		htmlPane.setCaretPosition(0);		
		
		// set page id
		id = "Welcome";		
		
	}
	
	/**
	 * External thread calls this method to draw an updated screen whenever a new file is copied, 
	 * and when it is finished, so the user can see progress.
	 */
	public void drawNewFile(String fileName) {
		currentFile = fileName;
		htmlPane.setText(getText());
		htmlPane.setCaretPosition(0);
		htmlPane.repaint();
		buttonPanel.setVisible(false);
	}

	
	/**
	 * this method generates the text for the page, which can vary depending on a few different conditions
	 * 
	 * @return
	 */
	private String getText(){
		String page = HEADER;
		page += " <p><b>Source Tomcat Installation: </b>";
		if( sourceDirectory == null ) {
			page += TOMCAT_ERROR;
		} else {
			page += sourceDirectory+"</p>";			
		}
		page += "<p><b>Destination Tomcat Installation: </b>";
		if( destinationDirectory == null ) {
			page += TOMCAT_ERROR;
		} else {
			page += destinationDirectory+"</p>";
		}
		page += "<p><i>Note: It may take a few minutes to backup your files when you click \"Backup\".</i></p>";
		if( currentFile != null ) {
			
		
			page += "<p>working";
			for( int i = 0; i < count; i++){ 
				page += ".";
			}
			if( count < 20 ) {
				count++;
			} else {
				count = 0;
			}
			page += "</p>";

		}
		if( isComplete && currentFile == null) {
			page += "<p><font color=red>Transfer is complete!</font></p>";
		}
		if( isErrorCopying ) {
			page += "<p><font color=red>There was a problem copying the files, are you sure you stopped tomcat?  You may need to use the manual instructions.</font></p>";
		}
		page += FOOTER;
		return page;
	}
	private int count = 0;
	/** 
	 * invoked when the backup button is clicked.
	 *
	 */
	private void backup() {
	
		// default to false
		isComplete = false;
		
		try {
			// call an external thread that will perform the copy process.
			FileCopyThread fileThread = new FileCopyThread(sourceDirectory, destinationDirectory, this);
			fileThread.start();
			
		} catch( Exception ex ) {
			isErrorCopying = true;
		}
		// all set
		
		htmlPane.setText(getText());
		htmlPane.setCaretPosition(0);			
		
		
	}
	

	/**
	 * Called when one of the file buttons is clicked, will invoke a menu for the 
	 * user to select a directory
	 */
	private String browse() {
		File dir = getDirectory(this);
		String dirString = null;
		if( !FileUtil.isDirectoryTomcat(dir)) {
			dirString = null;
		} else {
			dirString = dir.getAbsolutePath() + File.separator; 
		}
		
		
		return 	dirString;
	}
	
	/**
	 * invokes the file chooser, and gets  file back from it.
	 * @param component
	 * @return
	 */
	private File getDirectory(Component component) {
		JFileChooser chooser = null;
		if (chooser == null) chooser = new JFileChooser();
		File defaultDirectory = new File(File.separator);
		chooser.setCurrentDirectory(defaultDirectory);
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (chooser.showOpenDialog(component) == JFileChooser.APPROVE_OPTION) {
			File f = chooser.getSelectedFile();
			return f;
		}
		return null;
	}	
	
 


	


	
	
}
