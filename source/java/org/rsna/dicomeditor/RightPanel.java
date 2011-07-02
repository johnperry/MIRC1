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
import org.rsna.mircsite.anonymizer.DicomAnonymizer;
import org.rsna.mircsite.anonymizer.IdTable;
import org.rsna.mircsite.anonymizer.LocalRemapper;
import org.rsna.mircsite.anonymizer.Remapper;
import org.rsna.mircsite.anonymizer.RemoteRemapper;
import org.rsna.mircsite.anonymizer.XmlAnonymizer;
import org.rsna.util.ApplicationProperties;
import org.rsna.util.FileEvent;
import org.rsna.util.FileListener;
import org.rsna.util.FileUtil;
import org.rsna.util.GeneralFileFilter;
import org.rsna.util.SourcePanel;

/**
 * A JPanel that provides a user interface for the active part of
 * the DicomEditor program, including starting the anonymization
 * process and logging the results.
 */
public class RightPanel extends JPanel
						implements FileListener, ActionListener  {

	HeaderPanel headerPanel;
	JPanel centerPanel;
	FooterPanel footerPanel;
	ApplicationProperties properties;
	SourcePanel sourcePanel;
	ResultsScrollPane resultsPane;

	File currentSelection = null;
	boolean subdirectories = false;
	boolean changeNames = false;
	boolean forceIVRLE = false;
	boolean renameToSOPIUID = false;
	Properties dicomScript = null;
	Properties lookupTable = null;
	String xmlScript = null;
	String dicomScriptFile = null;
	String lookupTableFile = null;
	String xmlScriptFile = null;
	GeneralFileFilter filter = null;
	Color background = Color.getHSBColor(0.58f, 0.17f, 0.95f);

	/**
	 * Class constructor; creates an instance of the RightPanel and
	 * initializes the user interface for it.
	 * @param properties the properties object that is to be used
	 * and updated as GUI components change.
	 * @param sourcePanel the panel that contains file or directory
	 * selected for anonymization.
	 * @param dicomScriptFile the file path to the dicom anonymizer
	 * file that contains all the scripts.
	 * @param xmlScriptFile the file path to the xml anonymizer
	 * file that contains all the scripts.
	 * @param background the background color.
	 */
	public RightPanel(
			ApplicationProperties properties,
			SourcePanel sourcePanel,
			String dicomScriptFile,
			String lookupTableFile,
			String xmlScriptFile,
			Color background) {
		super();
		this.properties = properties;
		this.sourcePanel = sourcePanel;
		this.dicomScriptFile = dicomScriptFile;
		this.lookupTableFile = lookupTableFile;
		this.xmlScriptFile = xmlScriptFile;
		this.background = background;
		this.setLayout(new BorderLayout());
		headerPanel = new HeaderPanel();
		this.add(headerPanel,BorderLayout.NORTH);
		resultsPane = new ResultsScrollPane();
		this.add(resultsPane,BorderLayout.CENTER);
		footerPanel = new FooterPanel();
		this.add(footerPanel,BorderLayout.SOUTH);

		sourcePanel.getDirectoryPane().addFileListener(this);
		footerPanel.anonymize.addActionListener(this);
		footerPanel.fixVRs.addActionListener(this);
		footerPanel.fixPart10.addActionListener(this);
		footerPanel.clearPreamble.addActionListener(this);
	}

	/**
	 * The FileListener implementation.
	 * This method captures the current file selection when
	 * it changes in the sourcePanel.
	 * @param event the event containing the File currently selected.
	 */
	public void fileEventOccurred(FileEvent event) {
		if (event.type == FileEvent.SELECT) {
			currentSelection = event.after;
		}
	}

	/**
	 * The ActionListener for the footer's action buttons.
	 * This method starts the anonymization/VR correction process.
	 * @param event the event
	 */
	public void actionPerformed(ActionEvent event) {
		Object source = event.getSource();
		if (currentSelection != null) {
			subdirectories = sourcePanel.getSubdirectories();
			changeNames = footerPanel.changeNameBox.isSelected();
			forceIVRLE = footerPanel.forceIVRLEBox.isSelected();
			renameToSOPIUID = footerPanel.renameToSOPIUIDBox.isSelected();
			dicomScript = new ApplicationProperties(dicomScriptFile);
			lookupTable = new ApplicationProperties(lookupTableFile);
			xmlScript = FileUtil.getFileText(xmlScriptFile);
			filter = sourcePanel.getFileFilter();
			resultsPane.clear();
			resultsPane.append("<ol>");
			if (source.equals(footerPanel.anonymize)) anonymize(currentSelection);
			else if (source.equals(footerPanel.fixVRs)) fixVRs(currentSelection);
			else if (source.equals(footerPanel.clearPreamble)) clearPreamble(currentSelection);
			else if (source.equals(footerPanel.fixPart10)) fixIllegalPart10Files(currentSelection);
			IdTable.storeNow(false);
			resultsPane.append("</ol><b>Done.</b>");
			resultsPane.showText();
		}
		else Toolkit.getDefaultToolkit().beep();
	}

	// Anonymize the selected file(s).
	private void anonymize(File file) {
		if (file.isFile()) {
			File copy = file;
			if (changeNames) {
				String name = file.getName();
				int k = name.length();
				if (!name.matches("[\\d\\.]+")) {
					k = name.lastIndexOf(".");
					if (k == -1) k = name.length();
					if (name.substring(0,k).endsWith("-no-phi")) return;
				}
				name = name.substring(0,k) + "-no-phi" + name.substring(k);
				copy = new File(file.getParentFile(),name);
			}
			resultsPane.newItem("<li>Anonymizing: "+file.getName());

			//If the filename ends in ".xml", do an XML anonymization;
			//otherwise, do a DICOM anonymization.
			String result = "";
			if (file.getName().toLowerCase().endsWith(".xml"))
				result =
					XmlAnonymizer.anonymize(
						file,copy,xmlScript,getRemapper(properties)) ? "" : "failed";
			else
				result =
					DicomAnonymizer.anonymize(
						file, copy,
						dicomScript, lookupTable,
						getRemapper(properties),
						forceIVRLE, changeNames && renameToSOPIUID);

			//Report the results
			if (result.equals("")) {
				resultsPane.appendItem("<br><b>OK</b></li>");
				System.out.println(file+": OK");
			}
			else {
				resultsPane.appendItem("<br><font color=red><b>Exceptions:</b></font><br>"
					+ result + "</li>");
				System.out.println(file+":\n      EXCEPTIONS:\n      "+result);
			}
			return;
		}
		File[] files = file.listFiles(filter);
		for (int i=0; i<files.length; i++) {
			if (files[i].isFile() || subdirectories) anonymize(files[i]);
		}
	}

	// Clear the preamble on the selected file(s) - only if they are DICOM files.
	private void clearPreamble(File file) {
		if (file.isFile()) {
			resultsPane.newItem("<li>Clearing preamble: "+file.getName());
			String result = clearDicomPreamble(file);

			//Report the results
			if (result.equals(""))
				resultsPane.appendItem("<br><b>OK</b></li>");
			else
				resultsPane.appendItem("<br><font color=red><b>Exceptions:</b></font><br>"
					+ result + "</li>");
			return;
		}
		File[] files = file.listFiles(filter);
		for (int i=0; i<files.length; i++) {
			if (files[i].isFile() || subdirectories) clearPreamble(files[i]);
		}
	}

	//Clear the DICOM preamble on one file
	private String clearDicomPreamble(File file) {
		try {
			RandomAccessFile raf = new RandomAccessFile(file,"rw");
			raf.seek(128L);
			byte[] type = new byte[4];
			raf.read(type);
			if ((type[0] != 0x44) || (type[1] != 0x49) ||
				(type[2] != 0x43) || (type[3] != 0x4D)) return "Not a DICOM Part 10 file";
			byte[] bytes = new byte[128];
			for (int i=0; i<bytes.length; i++) bytes[i] = 0;
			raf.seek(0L);
			raf.write(bytes);
			raf.close();
			return "";
		}
		catch (Exception ex) {
			return ex.toString();
		}

	}

	// This method tries to correct illegal part 10 fils in two ways.
	//
	// Remove the preambles of files which have preambles and the DICM identifier
	// but which do not have a file meta-info group. This will make them start with
	// the first group in the first byte of the file. Such files will have to be
	// IVRLE to have any hope of working.
	//
	// Insert element (0002,0000) in files which have preambles and the DICM identifier
	// and a file meta-info group but which have no (0002,0000) element as required by part 10.
	private void fixIllegalPart10Files(File file) {
		if (file.isFile()) {
			resultsPane.newItem("<li>Checking: "+file.getName());
			String result = fixPart10File(file);

			//Report the results
			if (result.equals(""))
				resultsPane.appendItem("<br><b>OK</b></li>");
			else
				resultsPane.appendItem("<br><font color=red><b>Exceptions:</b></font><br>"
					+ result + "</li>");
			return;
		}
		File[] files = file.listFiles(filter);
		for (int i=0; i<files.length; i++) {
			if (files[i].isFile() || subdirectories) fixIllegalPart10Files(files[i]);
		}
	}

	// Remove the DICOM preamble and the DICOM identifier on one
	// file if that file does not have a file meta-info group.
	// Note: this method always overwrites the file,
	// even if the Change names box is checked.
	private String fixPart10File(File file) {
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			byte[] buffer = new byte[4096];
			int n = fis.read(buffer);
			if (n < 0x88) return "";
			int type = 0x80;

			// First verify that this has the format of a Part 10 file.
			if ((buffer[type] != 0x44) || (buffer[type+1] != 0x49) ||
				(buffer[type+2] != 0x43) || (buffer[type+3] != 0x4D)) {
					fis.close();
					return "";
			}

			// Now see if there is a group 2.
			if (buffer[type+4] != 0x02) {

				// It looks like a Part 10 file, but there is no file meta-info.
				// Copy everything into a new temp file and then rename it to the original.
				File temp = File.createTempFile("temp-",".tmp",file.getParentFile());
				FileOutputStream fos = new FileOutputStream(temp);
				int offset = 0x84;
				fos.write(buffer,offset,n-offset);
				while ((n = fis.read(buffer)) != -1) {
					fos.write(buffer,0,n);
				}
				fis.close();
				fos.flush();
				fos.close();
				file.delete();
				temp.renameTo(file);
				return "";
			}

			//The first element is in group 2, see if it is element (0002,0000);
			if ((buffer[type+4] == 0x02) && (buffer[type+5] == 0x00) &&
				((buffer[type+1] != 0x02) || (buffer[type+4] != 0x00))) {

				//It looks like a Part 10 file, but the length element is missing.
				//Put in a length element and hope that the rest of the file is okay.
				//Copy everything into a new temp file and then rename it to the original.
				File temp = File.createTempFile("temp-",".tmp",file.getParentFile());
				FileOutputStream fos = new FileOutputStream(temp);

				//First write everything up to the place for (0002,0000).
				int offset = 0x84;
				fos.write(buffer,0,offset);

				//Now put in a length element.
				byte[] lengthElement = new byte[]
					{ 0x02, 0x00, 0x00, 0x00, 0x55, 0x4C, 0x04, 0x00 };
				byte[] lengthValue = getGroup2Length(buffer,offset);
				fos.write(lengthElement);
				fos.write(lengthValue);

				//Now write the rest of the file
				fos.write(buffer,offset,n-offset);
				while ((n = fis.read(buffer)) != -1) {
					fos.write(buffer,0,n);
				}
				fis.close();
				fos.flush();
				fos.close();
				boolean deleteResult = file.delete();
				boolean renameResult = temp.renameTo(file);
				if (deleteResult && renameResult) return "";
				return "deleteResult = "+deleteResult + "<br>renameResult = "+renameResult;
			}
		}
		catch (Exception ex) {
			try { if (fis != null) fis.close(); }
			catch (Exception ignore) { }
			return ex.toString();
		}
		try { if (fis != null) fis.close(); }
		catch (Exception ignore) { }
		return "";
	}

	//Find the length of group 2.
	private byte[] getGroup2Length(byte[] b, int p) {
		byte[] lenbytes = new byte[4];
		int len = 0;
		while ((b[p] == 0x02) && (b[p+1] == 0x00)) {
			//Jump over the element identifier and the VR.
			//If VR is OB, it's 4 bytes, else it's 2.
			boolean vrob = (b[p+4] == 0x4f) && (b[p+5] == 0x42);
			if (vrob) p += 8;
			else p += 6;
			//Get the length of the data field.
			int lendata =  getIntValue(b, p, (vrob ? 4 : 2));
			p += (vrob ? 4 : 2) + lendata;
			len += 4 + 2*(vrob ? 4 : 2) + lendata;
		}
		for (int i=0; i<4; i++) {
			lenbytes[i] = (byte)(len & 0xFF);
			len = (len >> 8);
		}
		return lenbytes;
	}

	private int getIntValue(byte[] b, int p, int n) {
		int v = 0;
		int k;
		for (int i=n-1; i>=0; i--) {
			k = b[p + i];
			k = k & 0xFF;
			v = (v << 8) | k;
		}
		return v;
	}

	//Get a remapper based on the current properties.
	private Remapper getRemapper(Properties props) {
		String enabled = props.getProperty("remapper-enabled");
		if ((enabled != null) && enabled.trim().equals("true")) {
			String url = props.getProperty("remapper-url");
			if ((url != null) && !url.trim().equals("")) {
				try { return new RemoteRemapper(url.trim(),props); }
				catch (Exception useLocalRemapperInstead) { }
			}
		}
		return new LocalRemapper();
	}

	// Fix the VRs in the selected file(s).
	// Note: this method always overwrites the file,
	// even if the Change names box is checked.
	private void fixVRs(File file) {
		if (file.isFile()) {
			resultsPane.newItem("<li>Correcting: "+file.getName());
			String result = Corrector.fixVRs(file,forceIVRLE);
			if (result.equals(""))
				resultsPane.appendItem("<br><b>OK</b></li>");
			else
				resultsPane.appendItem("<br><font color=red><b>Exceptions:</b></font><br>"
					+ result + "</li>");

			return;
		}
		File[] files = file.listFiles(filter);
		for (int i=0; i<files.length; i++) {
			if (files[i].isFile() || subdirectories) fixVRs(files[i]);
		}
	}

	//Class to display the results of the processing
	class ResultsScrollPane extends JScrollPane {
		JEditorPane text;
		StringBuffer sb;
		int count;
		String item;
		public ResultsScrollPane() {
			super();
			sb = new StringBuffer();
			text = new JEditorPane();
			text.setContentType("text/html");
			text.setEditable(false);
			setViewportView(text);
			count = 0;
			item = "";
		}
		public void clear() {
			count = 0;
			item = "";
			sb = new StringBuffer();
			text.setText("");
		}
		public void newItem(String s) {
			count++;
			item = "<ol start=\""+count+"\">"+s;
			text.setText(item);
			sb.append(s);
		}
		public void appendItem(String s) {
			item += s;
			text.setText(item);
			sb.append(s);
		}
		public void append(String s) {
			sb.append(s);
		}
		public void showText() {
			text.setText(sb.toString());
		}
	}

	//Class to display the heading in the proper place
	class HeaderPanel extends JPanel {
		public HeaderPanel() {
			super();
			this.setLayout(new BoxLayout(this,BoxLayout.X_AXIS));
			JLabel panelLabel = new JLabel(" Results",SwingConstants.LEFT);
			this.setBackground(background);
			Font labelFont = new Font("Dialog", Font.BOLD, 18);
			panelLabel.setFont(labelFont);
			this.add(panelLabel);
			this.add(Box.createHorizontalGlue());
			this.add(Box.createHorizontalStrut(17));
		}
	}

	//Class to display the footer with the action buttons and
	//the checkbox for changing the names of processed files.
	class FooterPanel extends JPanel implements ActionListener {
		public JButton anonymize;
		public JButton fixVRs;
		public JButton fixPart10;
		public JButton clearPreamble;
		public JButton setURL;
		public JCheckBox changeNameBox;
		public JCheckBox forceIVRLEBox;
		public JCheckBox renameToSOPIUIDBox;
		public JCheckBox remapperBox;
		public String remapperURL;
		public FooterPanel() {
			super();
			this.setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
			this.setBackground(background);

			String changeName = (String)properties.getProperty("change-name");
			if (changeName == null) {
				changeName = "yes";
				properties.setProperty("change-name",changeName);
			}
			changeNameBox = new JCheckBox("Change names of modified files",changeName.equals("yes"));
			changeNameBox.setBackground(background);
			changeNameBox.addActionListener(this);

			String useSOPIUID = (String)properties.getProperty("use-sopiuid");
			if (useSOPIUID == null) {
				useSOPIUID = "no";
				properties.setProperty("use-sopiuid",useSOPIUID);
			}

			renameToSOPIUIDBox = new JCheckBox("Use SOPIUID for name",useSOPIUID.equals("yes"));
			renameToSOPIUIDBox.setBackground(background);
			renameToSOPIUIDBox.addActionListener(this);

			forceIVRLEBox = new JCheckBox("Force IVR-LE syntax",false);
			forceIVRLEBox.setBackground(background);

			String remapperEnabled = (String)properties.getProperty("remapper-enabled");
			remapperURL = (String)properties.getProperty("remapper-url");
			if (remapperEnabled == null) remapperEnabled = "false";
			if (remapperURL == null) {
				remapperURL = "";
				remapperEnabled = "false";
			}
			properties.setProperty("remapper-enabled",remapperEnabled);
			properties.setProperty("remapper-url",remapperURL);

			remapperBox = new JCheckBox("Enable remote remapper",remapperEnabled.equals("true"));
			remapperBox.setBackground(background);
			remapperBox.addActionListener(this);

			anonymize = new JButton("Anonymize");
			fixPart10 = new JButton("Fix part 10 files");
			fixVRs = new JButton("Fix VRs");
			clearPreamble = new JButton("Clear preamble");
			setURL = new JButton("Set remapper URL");
			setURL.addActionListener(this);

			Box rowE = new Box(BoxLayout.X_AXIS);
			rowE.add(Box.createHorizontalGlue());
			rowE.add(fixPart10);
			rowE.add(Box.createHorizontalStrut(17));

			Box rowD = new Box(BoxLayout.X_AXIS);
			rowD.add(remapperBox);
			rowD.add(Box.createHorizontalGlue());
			rowD.add(setURL);
			rowD.add(Box.createHorizontalStrut(17));

			Box rowC = new Box(BoxLayout.X_AXIS);
			rowC.add(forceIVRLEBox);
			rowC.add(Box.createHorizontalGlue());
			rowC.add(anonymize);
			rowC.add(Box.createHorizontalStrut(17));

			Box rowA = new Box(BoxLayout.X_AXIS);
			rowA.add(changeNameBox);
			rowA.add(Box.createHorizontalGlue());
			rowA.add(fixVRs);
			rowA.add(Box.createHorizontalStrut(17));

			Box rowB = new Box(BoxLayout.X_AXIS);
			rowB.add(renameToSOPIUIDBox);
			rowB.add(Box.createHorizontalGlue());
			rowB.add(clearPreamble);
			rowB.add(Box.createHorizontalStrut(17));

			Dimension anSize = anonymize.getPreferredSize();
			Dimension fixSize = fixVRs.getPreferredSize();
			Dimension fix2Size = fixPart10.getPreferredSize();
			Dimension cpSize = clearPreamble.getPreferredSize();
			Dimension remapSize = setURL.getPreferredSize();
			int maxWidth =
				Math.max(
					anSize.width,
					Math.max(
						fix2Size.width,
						Math.max(
							fixSize.width,
							Math.max(
								cpSize.width,
								remapSize.width))));
			anSize.width = maxWidth;
			anonymize.setPreferredSize(anSize);
			fixPart10.setPreferredSize(anSize);
			fixVRs.setPreferredSize(anSize);
			clearPreamble.setPreferredSize(anSize);
			setURL.setPreferredSize(anSize);

			this.add(rowE);
			this.add(rowD);
			this.add(rowC);
			this.add(rowA);
			this.add(rowB);
		}
		public void actionPerformed(ActionEvent evt) {
			if (evt.getSource().equals(setURL)) {
				String url = JOptionPane.showInputDialog(
					"Enter the URL of the central remapping servlet.\n"+
					"The URL is typically of the form:\n\n"+
					"http[s]://[server IP address]:[8080 or 8443]/[trial-name]/remap    \n\n",
					remapperURL);
				url = (url != null) ? url.trim() : "";
				if (!url.equals("")) {
					remapperURL = url;
					properties.setProperty("remapper-url",remapperURL);
				}
			}
			properties.setProperty("change-name",(changeNameBox.isSelected() ? "yes" : "no"));
			properties.setProperty("use-sopiuid",(renameToSOPIUIDBox.isSelected() ? "yes" : "no"));
			boolean enb = remapperBox.isSelected() && !remapperURL.equals("");
			properties.setProperty("remapper-enabled", (enb ? "true" : "false"));
			remapperBox.setSelected(enb);
		}
	}

}
