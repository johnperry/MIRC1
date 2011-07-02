
/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.installer;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.border.*;

/**
 * The parent of all the installer page classes.
 */
public class InstallerPage extends JPanel {

	/**
	 * Class constructor; creates a page configured as not the last
	 * page in the installer.
	 */
	public InstallerPage() {
		this(true);
	}

	/**
	 * Class constructor; creates an HTML page with or without a next page.
	 * If there isn't a next page, the Next button is not displayed and the
	 * Cancel button is given the text "Finished".
	 * @param next true if there is a next page; false otherwise.
	 */
	public InstallerPage(boolean next) {
		super();
		Border etchedBorder = BorderFactory.createEtchedBorder(Color.black,Color.white);
		this.setBorder(etchedBorder);
		this.setLayout(new BorderLayout());
		this.setBackground(Installer.background);
		this.next = next;
		buttonPanel = new JPanel();
		buttonPanel.setBackground(Color.gray);
		buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		this.add(buttonPanel,BorderLayout.SOUTH);
	}

	/**
	 * Dummy method that is called when the page is activated; overridden by
	 * subclasses if they need to do something when the page is first shown.
	 * The return value is true if the page is active or false if the page
	 * determines that it does not need to be displayed, in which case the
	 * installer skips to the next page.
	 * @return true;
	 */
	public boolean activate() {
		return true;
	}

	/**
	 * Add an array of buttons at the bottom of the page. This method also
	 * installs the Next and Cancel buttons.
	 * @param buttons the array of buttons.    
	 */
	public void setButtons(JButton[] buttons) {
		for (int i=0; i<buttons.length; i++) buttonPanel.add(buttons[i]);
		setButtons();
	}

	/**
	 * Install the Next and Cancel buttons, if appropriate, and configure the
	 * text of the Cancel button.
	 */
	public void setButtons() {
		if (next) {
			nextButton = new JButton("Next");
			nextButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent evt) { Installer.nextPage(); }
			});
			buttonPanel.add(nextButton);
		}
		String cancelName = next ? "Cancel" : "Finished";
		cancelButton = new JButton(cancelName);
		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { Installer.exit(); }
		});
		buttonPanel.add(cancelButton);
	}

	/**
	 * Set the enable on the Next button.
	 * @param enable true if the Next button is to be enabled; false otherwise.
	 */
	public void setNextEnabled(boolean enable) {
		nextButton.setEnabled(enable);
	}

	/**
	 * Get the text of a resource from the jar.
	 * @param resource the path to the resource.
	 * @return the text in the resource file in the jar.
	 */
	public String getResourceText(String resource) {
		try {
			InputStreamReader isr =
				new InputStreamReader(getClass().getResourceAsStream(resource));
			char[] chars = new char[4096];
			StringWriter sw = new StringWriter();
			int n;
			while ((n=isr.read(chars,0,chars.length)) != -1) sw.write(chars,0,n);
			isr.close();
			return sw.toString();
		}
		catch (Exception e) { }
		return null;
	}

	private JPanel	buttonPanel;
	private boolean next;
	private JButton nextButton;
	private JButton cancelButton;

	/** The ID of the page. */
	public	String 	id = "";

}
