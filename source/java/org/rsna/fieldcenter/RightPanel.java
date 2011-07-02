/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.fieldcenter;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import org.rsna.util.ApplicationProperties;
import org.rsna.util.FileEvent;
import org.rsna.util.FileListener;
import org.rsna.util.SourcePanel;

/**
 * A JPanel that provides a user interface for the manual queuing part
 * of the ManualSelectionPanel, including starting the queuing process
 * and logging the results.
 */
public class RightPanel extends JPanel
						implements FileListener, ActionListener {

	JPanel centerPanel;
	FooterPanel footerPanel;
	ApplicationProperties properties;
	SourcePanel sourcePanel;
	Color background = Color.getHSBColor(0.58f, 0.17f, 0.95f);

	SelectorScrollPane selectorScrollPane;
	File currentSelection = null;
	Selector selector;

	static final String buttonName = "Queue";

	int fileNumber = 0;

	/**
	 * Class constructor; creates an instance of the RightPanel and
	 * initializes the user interface for it.
	 * @param properties the properties object that is to be used
	 * and updated as GUI components change.
	 * @param sourcePanel the panel that contains file or directory
	 * selected for anonymization.
	 */
	public RightPanel(
			ApplicationProperties properties,
			SourcePanel sourcePanel) {
		super();
		this.properties = properties;
		this.sourcePanel = sourcePanel;
		this.setBackground(background);
		this.setLayout(new BorderLayout());
		selectorScrollPane = new SelectorScrollPane();
		footerPanel = new FooterPanel();
		this.add(selectorScrollPane,BorderLayout.CENTER);
		this.add(footerPanel,BorderLayout.SOUTH);

		sourcePanel.getDirectoryPane().addFileListener(this);
		footerPanel.button.addActionListener(this);
	}

	/**
	 * The FileListener implementation.
	 * This method handles events from the SourcePanel and
	 * the Selector.
	 * @param event the event containing the File currently selected
	 * (if the source is the SourcePanel), or the File entered into the
	 * input queue (if the source is the Selector).
	 */
	public void fileEventOccurred(FileEvent event) {
		if (event.type == FileEvent.SELECT) {
			currentSelection = event.after;
		}
		else if (event.type == FileEvent.MOVE) {
			selectorScrollPane
				.appendString(
					++fileNumber
					+ ": "
					+ event.before.getAbsolutePath()
					+ "\n");
		}
		else if (event.type == FileEvent.NO_MORE_FILES) {
			selectorScrollPane
				.appendString(
					"\nDone:  " + fileNumber + " file"
					+ ((fileNumber != 1) ? "s" : "")
					+ " queued");
			selectorScrollPane.displayAll();
			footerPanel.button.setText(buttonName);
		}
	}

	/**
	 * The ActionListener for the Send/Cancel button.
	 * The name of the button is used to indicate the state of
	 * the process and what can happen next.
	 * @param event the event indicating what happened.
	 */
	public void actionPerformed(ActionEvent event) {
		String buttonText = footerPanel.button.getText();
		if (event.getSource() instanceof JButton) {
			if (buttonText.equals(buttonName)) {
				boolean subdirectories = sourcePanel.getSubdirectories();
				if (currentSelection != null) {
					try {
						selectorScrollPane.clear();
						selector = new Selector(
							sourcePanel.getFileFilter(),
							currentSelection,
							subdirectories,
							footerPanel.queueDicomObjects.isSelected(),
							footerPanel.queueXmlObjects.isSelected(),
							footerPanel.queueZipFiles.isSelected(),
							footerPanel.unpackZipFiles.isSelected(),
							footerPanel.queueZipObjects.isSelected(),
							footerPanel.unpackZipObjects.isSelected(),
							footerPanel.queueFileObjects.isSelected() );
						selector.addFileListener(this);
						fileNumber = 0;
						selector.start();
						footerPanel.button.setText("Cancel");
					}
					catch (Exception e) {
						JOptionPane.showMessageDialog(this,e.getMessage());
					}
				}
				else Toolkit.getDefaultToolkit().beep();
			}
			else if (buttonText.equals("Cancel")) {
				selector.interrupt();
			}
		}
	}

	//Class to display the footer with the action button and
	//the checkbox for forcing the content type.
	class FooterPanel extends JPanel implements ActionListener {
		public JButton button;
		public JCheckBox queueDicomObjects;
		public JCheckBox queueXmlObjects;
		public JCheckBox queueZipFiles;
		public JCheckBox unpackZipFiles;
		public JCheckBox queueZipObjects;
		public JCheckBox unpackZipObjects;
		public JCheckBox queueFileObjects;

		public FooterPanel() {
			super();
			this.setBackground(background);
			this.setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));

			queueDicomObjects = getCheckBox("queueDicomObjects","DicomObjects", true);
			queueXmlObjects = getCheckBox("queueXmlObjects", "XmlObjects", true);
			queueZipFiles = getCheckBox("queueZipFiles", "ZipFiles", false);
			unpackZipFiles = getCheckBox("unpackZipFiles", "Unpack ZipFiles", false);
			queueZipObjects = getCheckBox("queueZipObjects", "ZipObjects", false);
			unpackZipObjects = getCheckBox("unpackZipObjects", "Unpack ZipObjects", false);
			queueFileObjects = getCheckBox("queueFileObjects", "FileObjects", false);
			button = new JButton(buttonName);

			Box box = new Box(BoxLayout.X_AXIS);
			box.add(queueDicomObjects);
			box.add(Box.createHorizontalGlue());
			this.add(box);

			box = new Box(BoxLayout.X_AXIS);
			box.add(queueXmlObjects);
			box.add(Box.createHorizontalGlue());
			this.add(box);

			box = new Box(BoxLayout.X_AXIS);
			box.add(queueZipFiles);
			Dimension d = queueZipFiles.getPreferredSize();
			d.width = 100;
			queueZipFiles.setPreferredSize(d);
			box.add(unpackZipFiles);
			box.add(Box.createHorizontalGlue());
			this.add(box);

			box = new Box(BoxLayout.X_AXIS);
			box.add(queueZipObjects);
			d = queueZipObjects.getPreferredSize();
			d.width = 100;
			queueZipObjects.setPreferredSize(d);
			box.add(unpackZipObjects);
			box.add(Box.createHorizontalGlue());
			this.add(box);

			box = new Box(BoxLayout.X_AXIS);
			box.add(queueFileObjects);
			box.add(Box.createHorizontalGlue());
			box.add(button);
			this.add(box);
		}

		public JCheckBox getCheckBox(String prop, String label, boolean defaultValue) {
			String value = (String)properties.getProperty(prop);
			if (value == null) {
				value = defaultValue ? "yes" : "no";
				properties.setProperty(prop,value);
			}
			JCheckBox cb = new JCheckBox(label, value.equals("yes"));
			cb.setBackground(background);
			cb.addActionListener(this);
			return cb;
		}

		public void actionPerformed(ActionEvent evt) {
			properties.setProperty("queueDicomObjects",(queueDicomObjects.isSelected() ? "yes" : "no"));
			properties.setProperty("queueXmlObjects",(queueXmlObjects.isSelected() ? "yes" : "no"));
			properties.setProperty("queueZipFiles",(queueZipFiles.isSelected() ? "yes" : "no"));
			properties.setProperty("unpackZipFiles",(unpackZipFiles.isSelected() ? "yes" : "no"));
			properties.setProperty("queueZipObjects",(queueZipObjects.isSelected() ? "yes" : "no"));
			properties.setProperty("unpackZipObjects",(unpackZipObjects.isSelected() ? "yes" : "no"));
			properties.setProperty("queueFileObjects",(queueFileObjects.isSelected() ? "yes" : "no"));
		}
	}

}
