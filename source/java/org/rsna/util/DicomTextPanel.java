/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import org.rsna.mircsite.util.DicomObject;

/**
 * A JPanel that provides a text dump of a DicomObject.
 */
public class DicomTextPanel extends JPanel implements ActionListener {

	JFileChooser chooser = null;
	DicomObject dicomObject;
    ButtonPanel buttonPanel;
    TextPanel textPanel;
    Color background = Color.getHSBColor(0.58f, 0.17f, 0.95f);

	/**
	 * Class constructor; creates a DicomTextPanel with a button panel.
	 */
    public DicomTextPanel() {
		this(true);
	}

	/**
	 * Class constructor; creates a DicomTextPanel with an optional button panel.
	 */
    public DicomTextPanel(boolean showButtonPanel) {
		super();
		this.setLayout(new BorderLayout());
		this.setBackground(background);

		if (showButtonPanel) {
			buttonPanel = new ButtonPanel();
			buttonPanel.addActionListener(this);
			this.add(buttonPanel,BorderLayout.NORTH);
		}

		textPanel = new TextPanel();
		JScrollPane jsp = new JScrollPane();
		jsp.setViewportView(textPanel);
		jsp.getVerticalScrollBar().setUnitIncrement(25);
		jsp.getHorizontalScrollBar().setUnitIncrement(15);
		this.add(jsp,BorderLayout.CENTER);
    }

    /**
     * The ActionListener implementation.
     * @param event the event indicating which button was clicked.
     */
    public void actionPerformed(ActionEvent event) {
		if (chooser == null) {
			File here = new File(System.getProperty("user.dir"));
			chooser = new JFileChooser(here);
		}
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			openFile(chooser.getSelectedFile());
		}
	}

    /**
     * Open and parse a DICOM file and display its elements.
     * @param file the DICOM file.
     */
	public void openFile(File file) {
		try {
			dicomObject = new DicomObject(file);
			textPanel.editor.setText(dicomObject.getElementTable());
			textPanel.editor.setCaretPosition(0);
		}
		catch (Exception e) {
			JOptionPane.showMessageDialog(this,"Exception:\n\n"+e.getMessage());
		}
	}

	//Miscellaneous GUI Panels

	class TextPanel extends JPanel {
		public JEditorPane editor;
		public TextPanel() {
			editor = new JEditorPane("text/html","");
			editor.setEditable(false);
			this.setLayout(new BorderLayout());
			this.add(editor,BorderLayout.CENTER);
		}
	}

	class ButtonPanel extends JPanel {
		public JButton open;
		public ButtonPanel() {
			super();
			this.setLayout(new FlowLayout(FlowLayout.LEFT));
			this.setBackground(background);
			open = new JButton("Open");
			open.setToolTipText("Open a DICOM file");
			this.add(open);
		}
		public void addActionListener(ActionListener listener) {
			open.addActionListener(listener);
		}
	}

}
