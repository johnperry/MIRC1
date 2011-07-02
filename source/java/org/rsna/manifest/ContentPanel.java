/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.manifest;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.zip.*;
import javax.swing.*;
import javax.swing.event.*;
import org.rsna.mircsite.util.DicomObject;
import org.rsna.util.PairedLayout;
import org.rsna.util.HtmlJPanel;
import org.rsna.util.DicomTextPanel;

/**
 * A JPanel that provides a UI for entering manifest contents.
 */
public class ContentPanel extends JPanel implements ActionListener {

    TopPanel top;
    FooterPanel footer;
    HtmlJPanel text;
    InstanceSelector selector;
    DicomTextPanel dicomTextPanel;
    Color background = Color.getHSBColor(0.58f, 0.17f, 0.95f);

	/**
	 * Class constructor.
	 */
    public ContentPanel(InstanceSelector selector) {
		super();
		this.setLayout(new BorderLayout());
		this.selector = selector;

		top = new TopPanel();

		text = new HtmlJPanel();
		text.editor.setContentType("text/plain");
		text.editor.setEditable(true);

		footer = new FooterPanel();
		footer.save.addActionListener(this);

		JPanel left = new JPanel(new BorderLayout());
		left.add(top,BorderLayout.NORTH);
		left.add(text,BorderLayout.CENTER);
		left.add(footer,BorderLayout.SOUTH);

		dicomTextPanel = new DicomTextPanel(false);

		JSplitPane jSplitPane = new JSplitPane();
		jSplitPane.setLeftComponent(left);
		jSplitPane.setRightComponent(dicomTextPanel);
		jSplitPane.setResizeWeight(0.05D);
		jSplitPane.setDividerLocation(0.3D);
		jSplitPane.setContinuousLayout(true);
		this.add(jSplitPane,BorderLayout.CENTER);
    }

    /**
     * The ActionListener implementation.
     * @param event the event.
     */
    public void actionPerformed(ActionEvent event) {
		try {
			Manifest manifest =
				new Manifest(
						top.uid.getText(),
						top.authorName.getText(),
						top.accessionNumber.getText(),
						top.manufacturer.getText(),
						top.refPhysName.getText(),
						text.editor.getText(),
						selector.getSelectedFiles());
			File dir = selector.getCurrentDirectory();
			File file = manifest.save(dir);
			dicomTextPanel.openFile(file);
			zip(file,selector.getSelectedFiles());
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(
				this, ex.getMessage());
		}
	}

	private void zip(File file, File[]files) {
		try {
			DicomObject manifest = new DicomObject(file);
			zipFiles(file, files, file.getName()+".zip");
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(
				this,"Unable to produce the zip file:\n\n"+ex.getMessage());
		}
	}

	//Zip a collection of files.
	//All the input files and the output zip
	//file must be in the same directory.
	private void zipFiles(
				File manifest,
				File[] files,
				String zipfilename)  throws Exception {

		File dir = new File(manifest.getAbsolutePath());
		dir = dir.getParentFile();

		//Get the various streams and buffers
		FileOutputStream fout = new FileOutputStream(new File(dir,zipfilename));
		ZipOutputStream zout = new ZipOutputStream(fout);

		//Add in the manifest
		zipFile(manifest, zout);

		//Add in the instances
		for (int i=0; i<files.length; i++) {
			zipFile(files[i], zout);
		}
		zout.close();
	}

	//Add one file to a zip file
	private void zipFile(File file, ZipOutputStream zout) throws Exception {
		String sopInstanceUID = "";
		try {
			DicomObject dob = new DicomObject(file);
			sopInstanceUID = dob.getSOPInstanceUID();
		}
		catch (Exception ex) { return; }
		File in;
		FileInputStream fin;
		ZipEntry ze;
		byte[] buffer = new byte[10000];
		int bytesread;
		ze = new ZipEntry(sopInstanceUID);
		if (file.exists()) {
			fin = new FileInputStream(file);
			zout.putNextEntry(ze);
			while ((bytesread = fin.read(buffer)) > 0) {
				zout.write(buffer,0,bytesread);
			}
			zout.closeEntry();
			fin.close();
		}
	}

	class TopPanel extends JPanel {
		public JTextField uid;
		public JTextField authorName;
		public JTextField accessionNumber;
		public JTextField manufacturer;
		public JTextField refPhysName;
		public TopPanel() {
			super();
			this.setBackground(background);
			this.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
			this.setLayout(new PairedLayout(5,5));
			this.add(new JLabel("UID:"));
			uid = new JTextField(30);
			this.add(uid);
			this.add(new JLabel("Username:"));
			authorName = new JTextField(30);
			this.add(authorName);
			this.add(new JLabel("Accession Number:"));
			accessionNumber = new JTextField(30);
			this.add(accessionNumber);
			this.add(new JLabel("Manufacturer:"));
			manufacturer = new JTextField(30);
			this.add(manufacturer);
			this.add(new JLabel("Ref. Phys. Name:"));
			refPhysName = new JTextField(30);
			this.add(refPhysName);
		}
	}

	class FooterPanel extends JPanel {
		public JButton save;
		public FooterPanel() {
			super();
			this.setBackground(background);
			save = new JButton("Save");
			this.add(save);
		}
	}

}
