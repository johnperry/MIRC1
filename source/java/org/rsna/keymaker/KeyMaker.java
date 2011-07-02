/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.keymaker;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.swing.*;
import org.rsna.util.FileUtil;
import org.rsna.util.Key;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

/**
 * The Keymaker program provides a UI for creating an encryption keyfile for the IdTable.
 */
public class KeyMaker extends JFrame implements ActionListener {

	String windowTitle = "KeyMaker";
	JEditorPane textPane;
	JButton textkeyButton;
	JButton randomkeyButton;
	JButton savekeyButton;
	JButton testButton;
	JTextField keySize;
	JFileChooser chooser = null;
	File keyFile;

	String key = null;
	static final String defaultKey = "FveQxzb+JRib1XItpIZVfw==";
    static String charsetName = "UTF-8";

	public static void main(String args[]) {
		new KeyMaker();
	}

	/**
	 * Class constructor; creates a new instance of the KeyMaker
	 * program. It acquires a key from the user, performs various
	 * checks, and saves the key to a disk file.
	 */
	public KeyMaker() {
		initComponents();
		setVisible(true);
	}

	/**
	 * The ActionListener implementation
	 * @param event the event.
	 */
	public void actionPerformed(ActionEvent event) {
		if (event.getSource().equals(textkeyButton)) makeTextKey();
		if (event.getSource().equals(randomkeyButton)) makeRandomKey();
		if (event.getSource().equals(savekeyButton)) saveKey();
		if (event.getSource().equals(testButton)) testLengths();
	}

	void makeTextKey() {
		int intSize = getKeySize();
		key = Key.getEncryptionKey(this,true,intSize);
		if (key == null) key = defaultKey;
		showResult(key,intSize);
	}

	void makeRandomKey() {
		int intSize = getKeySize();
		key = getKey(intSize);
		showResult(key,intSize);
	}

	void showResult(String key, int intSize) {
		String result =
			"Key (size="+intSize+"):\n"
			+ key
			+ "\n\n"
			+ checkCipher(key)
			+ "\n\n"
			+ "Encrypted key:\n"
			+ getEncryptedKey(key)
			+ "\n\n";
		if (intSize > 128)
			result += "IMPORTANT:\n"
					+ "Any key longer than 128 bits requires the Java\n"
					+ "Cryptography Extension with unlimited strength.\n"
					+ "See http://java.sun.com/products/jce/javase.html.";
		textPane.setText(result);
	}

	void saveKey() {
		if (chooser == null) chooser = new JFileChooser();
		int returnVal = chooser.showSaveDialog(this);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			keyFile = chooser.getSelectedFile();
			FileUtil.setFileText(keyFile,key);
			textPane.setText(textPane.getText()+"Key saved in:\n"+keyFile);
		}
	}

	void testLengths() {
		String s = "x0123456789012345678901234567890123456789"
				 + "0123456789012345678901234567890123456789";
		StringBuffer sb = new StringBuffer("Checking for acceptable key lengths\n\n");
		for (int len=64; len<=448; len++) {
			String key = getKey(s,len);
			sb.append("  " + len + ": ");
			sb.append(checkCipher(key));
			sb.append("\n");
		}

		sb.append("\nRechecking using byte arrays\n\n");
		byte[] b = {
			 0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
			10,11,12,13,14,15,16,17,18,19,
			20,21,22,23,24,25,26,27,28,29,
			30,31,32,33,34,35,36,37,38,39,
			40,41,42,43,44,45,46,47,48,49,
			50,51,52,53,54,55,56,57,58,59,
			60,61,62,63,64,65,66,67,68,69
		};
		for (int len=8; len<=56; len++) {
			byte[] key = getKey(b,len);
			sb.append("  " + len*8 + ": ");
			sb.append(checkCipher(key));
			sb.append("\n");
		}

		textPane.setText(sb.toString());
		textPane.setCaretPosition(0);
	}

	int getKeySize() {
		String size = keySize.getText();
		int intSize = 128;
		try { intSize = Integer.parseInt(size); }
		catch (Exception useDefault) { }
		if (intSize < 64) intSize = 64;
		return intSize;
	}

	String getKey(int size) {
		try {
			Provider sunJce = new com.sun.crypto.provider.SunJCE();
			KeyGenerator generator = KeyGenerator.getInstance("Blowfish",sunJce);
			generator.init(size);
			SecretKey key = generator.generateKey();
			byte[] keyBytes = key.getEncoded();
			BASE64Encoder b64Encoder = new BASE64Encoder();
			return b64Encoder.encode(keyBytes);
		}
		catch (Exception ex) { return null; }
	}

	String getKey(String s, int size) {
		String pad = "===";
		int requiredChars = (size + 5) / 6;
		int requiredGroups = (requiredChars + 3) / 4;
		int requiredGroupChars = 4 * requiredGroups;
		String key = s.substring(0,requiredChars);
		return (key + pad).substring(0,requiredGroupChars);
	}

	byte[] getKey(byte[] b, int size) {
		byte[] key = new byte[size];
		for (int i=0; i<size; i++) key[i] = b[i%b.length];
		return key;
	}

	//Initialize a Cipher with the specified base64 key.
	private static String getEncryptedKey(String encryptionKey) {
		BASE64Encoder b64Encoder;
		BASE64Decoder b64Decoder;
		Cipher enCipher;
		try {
			b64Encoder = new BASE64Encoder();
			Provider sunJce = new com.sun.crypto.provider.SunJCE();
			Security.addProvider(sunJce);
			b64Decoder = new BASE64Decoder();
			byte[] raw = b64Decoder.decodeBuffer(encryptionKey);
			SecretKeySpec skeySpec = new SecretKeySpec(raw,"Blowfish");
			enCipher = Cipher.getInstance("Blowfish");
			enCipher.init(Cipher.ENCRYPT_MODE, skeySpec);
			byte[] encrypted = enCipher.doFinal(encryptionKey.getBytes(charsetName));
			return b64Encoder.encode(encrypted);
		}
		catch (Exception ex) {
			return "Unable to encrypt the key:\n" + ex.toString();
		}
	}

	//Initialize a Cipher with the specified base64 key.
	private static String checkCipher(String encryptionKey) {
		BASE64Decoder b64Decoder = new BASE64Decoder();
		try {
			byte[] raw = b64Decoder.decodeBuffer(encryptionKey);
			return checkCipher(raw);
		}
		catch (Exception ex) {
			return
				"Unable to initialize the Cipher: "
				+ ex.getMessage();
		}
	}

	//Initialize a Cipher with the specified byte array key.
	private static String checkCipher(byte[] raw) {
		Cipher enCipher;
		Cipher deCipher;
		try {
			Provider sunJce = new com.sun.crypto.provider.SunJCE();
			Security.addProvider(sunJce);
			SecretKeySpec skeySpec = new SecretKeySpec(raw,"Blowfish");
			enCipher = Cipher.getInstance("Blowfish");
			enCipher.init(Cipher.ENCRYPT_MODE, skeySpec);
			deCipher = Cipher.getInstance("Blowfish");
			deCipher.init(Cipher.DECRYPT_MODE, skeySpec);
		}
		catch (Exception ex) {
			return
				"Unable to initialize the Cipher: "
				+ ex.getMessage();
		}
		return "Cipher successfully initialized.";
	}

	void initComponents() {
		JPanel mainPanel = new JPanel(new BorderLayout());
		JPanel buttonPanel = new JPanel();

		JLabel sizeLabel = new JLabel("Key Length:");
		buttonPanel.add(sizeLabel);

		keySize = new JTextField(10);
		buttonPanel.add(keySize);

		textkeyButton = new JButton("Make a Text Key");
		textkeyButton.addActionListener(this);
		buttonPanel.add(textkeyButton);

		randomkeyButton = new JButton("Make a Random Key");
		randomkeyButton.addActionListener(this);
		buttonPanel.add(randomkeyButton);

		savekeyButton = new JButton("Save the Key");
		savekeyButton.addActionListener(this);
		buttonPanel.add(savekeyButton);

		testButton = new JButton("Test Key Lengths");
		testButton.addActionListener(this);
		buttonPanel.add(testButton);

		mainPanel.add(buttonPanel,BorderLayout.NORTH);

		textPane = new JEditorPane("text/plain","");
		textPane.setEditable(false);
		Font font = new Font("Courier New", Font.PLAIN, 12);
		textPane.setFont(font);
		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setViewportView(textPane);
		mainPanel.add(scrollPane,BorderLayout.CENTER);

		setTitle(windowTitle);
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent evt) {
				exitForm(evt);
			}
		});
		getContentPane().add(mainPanel, BorderLayout.CENTER);
		pack();
		centerFrame();
	}

	void centerFrame() {
		Toolkit t = getToolkit();
		Dimension scr = t.getScreenSize ();
		setSize(scr.width/2, scr.height/2);
		setLocation (new Point ((scr.width-getSize().width)/2,
								(scr.height-getSize().height)/2));
	}

	void exitForm(java.awt.event.WindowEvent evt) {
		System.exit(0);
	}
}
