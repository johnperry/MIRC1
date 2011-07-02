/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.dicomeditor;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import org.apache.log4j.*;
import org.rsna.mircsite.util.DicomObject;
import org.rsna.util.DicomViewer;

import sun.misc.BASE64Encoder;
import sun.misc.BASE64Decoder;

import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * A JPanel that provides a decoder for encrypted elements in a DICOM file.
 */
public class Decoder extends JPanel implements ActionListener {

	static BASE64Encoder b64Encoder = new BASE64Encoder();
	static BASE64Decoder b64Decoder = new BASE64Decoder();

	JFileChooser 	chooser = null;
	DicomObject		dicomImage;
    ButtonPanel		buttonPanel;
    JEditorPane		text;
    DicomViewer		viewer;

	Color background;

	static final Logger logger = Logger.getLogger(Decoder.class);

	/**
	 * Class constructor; creates a Decoder JPanel.
	 * @param background the background color.
	 */
    public Decoder(DicomViewer viewer, Color background) {
		super();
		this.viewer = viewer;
		this.setLayout(new BorderLayout());
		this.background = background;

		buttonPanel = new ButtonPanel();
		buttonPanel.open.addActionListener(this);
		buttonPanel.go.addActionListener(this);
		this.add(buttonPanel,BorderLayout.NORTH);
		this.setBackground(background);

		text = new JEditorPane();
		text.setEditable(false);
		this.add(text,BorderLayout.CENTER);
    }

    /**
     * The ActionListener implementation.
     * @param event the event indicating which button was clicked.
     */
    public void actionPerformed(ActionEvent event) {
		Object source = event.getSource();
		if (source.equals(buttonPanel.open)) openImage();
		else if (source.equals(buttonPanel.go)) go();
	}

	//Open an image file. If necessary, provide a chooser
	//and receive the user's selection.
	private void openImage() {
		if (chooser == null) {
			if ((viewer == null) || (viewer.getCurrentFile() == null)) {
				chooser = new JFileChooser(new File(System.getProperty("user.dir")));
			}
			else {
				chooser = new JFileChooser(viewer.getCurrentFile());
			}
		}
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			File file = chooser.getSelectedFile();
			try {
				dicomImage = new DicomObject(file);
				buttonPanel.go.setEnabled(true);
			}
			catch (Exception ex) {
				text.setText("Error opening "+file);
			}
		}
	}

	//Do the decoding
	private void go() {
		String element = buttonPanel.element.getText();
		element = element.replaceAll("[^a-fA-F0-9]","");
		if (element.length() != 8) {
			text.setText(buttonPanel.element.getText() + " is not a valid element designation");
			return;
		}
		int tag = 0;
		try { tag = Integer.parseInt(element,16); }
		catch (Exception ex) {
			text.setText(element + " is not a valid element designation");
			return;
		}
		String value = new String(dicomImage.getElementByteBuffer(tag).array());
		Cipher cipher = getCipher(buttonPanel.key.getText().trim());
		if (cipher == null) {
			text.setText("Unable to create a cipher with the specified key.");
			return;
		}
		try {
			byte[] encrypted = b64Decoder.decodeBuffer(value);
			byte[] decrypted = cipher.doFinal(encrypted);
			String result = new String(decrypted,"UTF-8");
			text.setText("Encrypted value =\n" + value + "\n\n\nDecrypted value =\n" + result);
		}
		catch (Exception ex) {
			text.setText("Unable to decript the data: " + value);
		}
	}

	//Get a Cipher initialized with the specified key.
	private static Cipher getCipher(String keyText) {
		try {
			Provider sunJce = new com.sun.crypto.provider.SunJCE();
			Security.addProvider(sunJce);
			byte[] key = getEncryptionKey(keyText,128);
			SecretKeySpec skeySpec = new SecretKeySpec(key,"Blowfish");

			SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
			byte[] seed = random.generateSeed(8);
			random.setSeed(seed);

			Cipher cipher = Cipher.getInstance("Blowfish");
			cipher.init(Cipher.DECRYPT_MODE, skeySpec, random);
			return cipher;
		}
		catch (Exception ex) {
			return null;
		}
	}

	static String nonce = "tszyihnnphlyeaglle";
	static String pad = "===";
	private static byte[] getEncryptionKey(String keyText, int size) throws Exception {
		if (keyText == null) keyText = "";
		keyText = keyText.trim();

		//Now make it into a base-64 string encoding the right number of bits.
		keyText = keyText.replaceAll("[^a-zA-Z0-9+/]","");

		//Figure out the number of characters we need.
		int requiredChars = (size + 5) / 6;
		int requiredGroups = (requiredChars + 3) / 4;
		int requiredGroupChars = 4 * requiredGroups;

		//If we didn't get enough characters, then throw some junk on the end.
		while (keyText.length() < requiredChars) keyText += nonce;

		//Take just the right number of characters we need for the size.
		keyText = keyText.substring(0,requiredChars);

		//And return the string padded to a full group.
		keyText = (keyText + pad).substring(0,requiredGroupChars);
		return b64Decoder.decodeBuffer(keyText);
	}

	class ButtonPanel extends JPanel {
		public JButton open;
		public JTextField element;
		public JTextField key;
		public JLabel result;
		public JButton go;
		public ButtonPanel() {
			super();
			this.setBackground(background);
			open = new JButton("Open");
			open.setToolTipText("Open an image file");
			this.add(open);
			this.add(Box.createHorizontalStrut(20));
			this.add(new JLabel("Element:"));
			element = new JTextField("(0009,1002)",10);
			this.add(element);
			this.add(Box.createHorizontalStrut(20));
			this.add(new JLabel("Key:"));
			key = new JTextField(20);
			this.add(key);
			this.add(Box.createHorizontalStrut(20));
			go = new JButton("GO");
			go.setEnabled(false);
			this.add(go);
		}
	}

}
