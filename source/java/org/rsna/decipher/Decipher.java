/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.decipher;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import org.rsna.util.Key;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;

import java.nio.charset.Charset;

/**
 * The Decipher program provides access to an encrypted IdTable.
 */
public class Decipher extends JFrame implements ActionListener {

	static final int all = 0;
	static final int ptids = 1;
	static final int dates = 2;

	String windowTitle = "Decipher";
	ColorPane textPane;
	JTextField textField;
	JButton searchPHI;
	JButton searchTrial;
	JCheckBox saveAs;
	JLabel message;
	JFileChooser chooser = null;
	File file;
	long fileLength;

	Pattern pattern;

	static BASE64Encoder b64Encoder;
	static BASE64Decoder b64Decoder;
	static Cipher enCipher;
	static Cipher deCipher;

	static Charset charset = Charset.forName("iso-8859-1");

	protected static final String defaultKey = "FveQxzb+JRib1XItpIZVfw==";
	String key = null;

	public static void main(String args[]) {
		new Decipher();
	}

	/**
	 * Class constructor; creates a new instance of the Decipher test
	 * program. It acquires a key from the user and allows the user
	 * to browse to an IdTable.properties file and then displays
	 * the deciphered keys and values.
	 */
	public Decipher() {
		initComponents();
		setVisible(true);
		getTable();
	}

	/**
	 * The ActionListener implementation
	 * @param event the event.
	 */
	public void actionPerformed(ActionEvent event) {
		String searchText = textField.getText().trim();
		if (searchText.equals("") && !saveAs.isSelected() && (fileLength > 10000000)) {
			textPane.setText("Blank search text is not allowed for large IdTables.");
		}
		else {
			File outputFile = null;
			if (saveAs.isSelected()) {
				outputFile = chooser.getSelectedFile();
				if (outputFile != null) {
					String name = outputFile.getName();
					int k = name.lastIndexOf(".");
					if (k != -1) name = name.substring(0,k);
					name += ".txt";
					File parent = outputFile.getAbsoluteFile().getParentFile();
					outputFile = new File(parent, name);
					chooser.setSelectedFile(outputFile);
				}
				if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) System.exit(0);
				outputFile = chooser.getSelectedFile();
			}
			textPane.setText("");
			Searcher searcher =
					new Searcher(
							searchText,
							event.getSource().equals(searchPHI),
							outputFile);
			searcher.start();
		}
	}

	void getTable() {
		if (chooser == null) {
			File userdir = new File(System.getProperty("user.dir"));
			chooser = new JFileChooser(userdir);
		}
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			file = chooser.getSelectedFile();
			fileLength = file.length();
			setTitle(windowTitle + ": " + file.getAbsolutePath());
			int size = Key.getEncryptionKeySize(this,true);
			key = Key.getEncryptionKey(this,true,size);
			if (key == null) key = defaultKey;
			initCipher();
		}
		else System.exit(0);
	}

	private String[] split(String line) {
		int k = 1;
		while ((k<line.length()) && ((k=line.indexOf("=",k)) != -1)) {
			if (line.charAt(k-1) != '\\') {
				String left = line.substring(0, k).replaceAll("\\\\r\\\\n", "").replaceAll("\\\\", "");
				String right = line.substring(k+1);
				return new String[] { left, right };
			}
			k++;
		}
		return null;
	}

	private String decrypt(String s) {
		try {
			byte[] encrypted = b64Decoder.decodeBuffer(s);
			byte[] decrypted = deCipher.doFinal(encrypted);
			return new String(decrypted);
		}
		catch (Exception ex) { return "error"; }
	}

	void initCipher() {
		try {
			b64Decoder = new BASE64Decoder();
			b64Encoder = new BASE64Encoder();
			Provider sunJce = new com.sun.crypto.provider.SunJCE();
			Security.addProvider(sunJce);
			byte[] raw = b64Decoder.decodeBuffer(key);
			SecretKeySpec skeySpec = new SecretKeySpec(raw,"Blowfish");
			deCipher = Cipher.getInstance("Blowfish");
			deCipher.init(Cipher.DECRYPT_MODE, skeySpec);
			enCipher = Cipher.getInstance("Blowfish");
			enCipher.init(Cipher.ENCRYPT_MODE, skeySpec);
		}
		catch (Exception ex) {
			textPane.setText("Unable to create the cipher");
		}
	}

	void initComponents() {
		JPanel mainPanel = new JPanel(new BorderLayout());
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
		Color bgColor = Color.getHSBColor(0.58f, 0.17f, 0.95f);
		buttonPanel.setBackground(bgColor);
		Border empty = BorderFactory.createEmptyBorder(5, 5, 5, 5);
		buttonPanel.setBorder(empty);

		textField = new JTextField(75);
		buttonPanel.add(textField);

		buttonPanel.add(Box.createHorizontalStrut(10));
		searchPHI = new JButton("Search PHI");
		searchPHI.addActionListener(this);
		buttonPanel.add(searchPHI);

		buttonPanel.add(Box.createHorizontalStrut(10));
		searchTrial = new JButton("Search Trial IDs");
		searchTrial.addActionListener(this);
		buttonPanel.add(searchTrial);

		buttonPanel.add(Box.createHorizontalStrut(20));
		buttonPanel.add(Box.createHorizontalGlue());
		saveAs = new JCheckBox("Save As...");
		saveAs.setBackground(bgColor);
		buttonPanel.add(saveAs);

		mainPanel.add(buttonPanel,BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane();
		textPane = new ColorPane();
		//textPane.setEditable(false);
		scrollPane.setViewportView(textPane);
		mainPanel.add(scrollPane,BorderLayout.CENTER);

		JPanel footerPanel = new JPanel();
		footerPanel.setLayout(new BoxLayout(footerPanel, BoxLayout.X_AXIS));
		footerPanel.setBackground(bgColor);
		message = new JLabel("Ready...");
		footerPanel.add(message);
		mainPanel.add(footerPanel, BorderLayout.SOUTH);

		setTitle(windowTitle);
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent evt) {
				System.exit(0);
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

	class Searcher extends Thread {

		String searchText;
		boolean searchPHI = false;
		File outputFile = null;
		BufferedWriter bw = null;
		long filePosition = 0;
		long percentDone = 0;

		public Searcher(String searchText, boolean searchPHI, File outputFile) {
			this.searchText = searchText;
			this.searchPHI = searchPHI;
			this.outputFile = outputFile;
		}

		public void run() {
			try {
				BufferedReader br = new BufferedReader(
										new InputStreamReader(
											new FileInputStream(file), charset));
				if (outputFile != null) {
					bw = new BufferedWriter(
							new OutputStreamWriter(
								new FileOutputStream(outputFile), charset));
				}
				String line;
				while ((line = br.readLine()) != null) {
					filePosition += line.length() + 2;
					line = line.trim();
					if (!line.startsWith("#")) {
						String[] sides = split(line);
						if ((sides != null) && !sides[0].equals("key")) {

							if (searchPHI) {
								//Search the decrypted PHI for the searchText
								sides[0] = decrypt(sides[0]);
								if (sides[0].indexOf(searchText) != -1) {
									output(sides[0] + " = " + sides[1] + "\n");
								}
							}

							else {
								//Search the trial ID for the searchText
								if (sides[1].indexOf(searchText) != -1) {
									sides[0] = decrypt(sides[0]);
									output(sides[0] + " = " + sides[1] + "\n");
								}
							}
						}
					}
				}
				br.close();
				if (bw != null) {
					bw.flush();
					bw.close();
				}
			}
			catch (Exception e) { append("\n\n"+e.getClass().getName()+": "+e.getMessage()+"\n"); }
			append("\nDone.\n");
			setMessage("Ready...");
		}

		private void output(String string) throws Exception {
			if (bw != null) bw.write(string);
			else append(string);

			//Set the fraction done.
			long pct = (filePosition * 100) / fileLength;
			if (pct != percentDone) {
				percentDone = pct;
				setMessage("Working... ("+pct+"%)");
			}
		}

		private void setMessage(String string) {
			final String s = new String(string);
			Runnable setString = new Runnable() {
				public void run() { message.setText(s); }
			};
			SwingUtilities.invokeLater(setString);
		}

		private void append(String string) {
			final String s = new String(string);
			Runnable appendString = new Runnable() {
				public void run() { textPane.append(s); }
			};
			SwingUtilities.invokeLater(appendString);
		}
	}

	class ColorPane extends JTextPane {

		public int lineHeight;

		public ColorPane() {
			super();
			Font font = new Font("Monospaced",Font.PLAIN,12);
			FontMetrics fm = getFontMetrics(font);
			lineHeight = fm.getHeight();
			setFont(font);
			setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
		}

		public boolean getScrollableTracksViewportWidth() {
			return false;
		}

		public void append(String s) {
			int len = getDocument().getLength(); // same value as getText().length();
			setCaretPosition(len);  // place caret at the end (with no selection)
			replaceSelection(s); // there is no selection, so inserts at caret
		}

		public void append(Color c, String s) {
			StyleContext sc = StyleContext.getDefaultStyleContext();
			AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY,
										StyleConstants.Foreground, c);
			int len = getDocument().getLength();
			setCaretPosition(len);
			setCharacterAttributes(aset, false);
			replaceSelection(s);
		}
	}

}