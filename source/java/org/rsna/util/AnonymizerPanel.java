/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import java.nio.charset.Charset;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

/**
 * A JPanel that provides a user interface for editing the scripts of
 * the MIRC anonymizer. The anonymizer provides de-identification and
 * re-identification of DICOM objects for clinical trials. Each element
 * as well as certain groups of elements are scriptable. The script
 * language is defined in "How to Configure the Anonymizer for MIRC
 * Clinical Trial Services". The scripts are stored as a properties
 * file. The AnonymizerPanel edits the text of the file rather than
 * the values in the properties hashtable in order to keep the
 * elements in sequence and because elements that are not enabled
 * are not present in the hashtable.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class AnonymizerPanel extends JPanel
							implements ActionListener, PropertyListener {

	private JScrollPane configSP;
	private ConfiguratorPanel configuratorPanel;
	private FooterPanel footerPanel;
	String filename;
	ApplicationProperties fcProps;
	private static Charset charset = Charset.forName("UTF-8");
	Color background;
	boolean showAll = true;

	/**
	 * Class constructor.
	 * @param filename the path to the anonymizer script file (anonymizer.properties)
	 * @param fcProps the properties object containing the save-enabled property, determining
	 * whether the AnonymizerPanel's Save Change button is enabled. This property can change
	 * dynamically, so this object listens for PropertyEvents and queries the fcProps object
	 * when they happen.
	 */
    public AnonymizerPanel(String filename, ApplicationProperties fcProps) {
		this(filename,fcProps,null);
	}

	/**
	 * Class constructor.
	 * @param filename the path to the anonymizer script file (anonymizer.properties)
	 * @param fcProps the properties object containing the save-enabled property, determining
	 * whether the AnonymizerPanel's Save Change button is enabled. This property can change
	 * dynamically, so this object listens for PropertyEvents and queries the fcProps object
	 * when they happen.
	 */
    public AnonymizerPanel(ApplicationProperties fcProps, String filename) {
		this(filename,fcProps,null);
	}

	/**
	 * Class constructor.
	 * @param filename the path to the anonymizer script file (anonymizer.properties)
	 * @param fcProps the properties object containing the save-enabled property, determining
	 * whether the AnonymizerPanel's Save Change button is enabled. This property can change
	 * dynamically, so this object listens for PropertyEvents and queries the fcProps object
	 * when they happen.
	 */
    public AnonymizerPanel(
			String filename,
			ApplicationProperties fcProps,
			Color background) {
		super();
		setLayout(new BorderLayout());
		this.filename = filename;
		this.fcProps = fcProps;
		if (background == null)
			this.background = Color.getHSBColor(0.58f, 0.17f, 0.95f);
		else
			this.background = background;

		configuratorPanel = new ConfiguratorPanel(filename);
		configSP = new JScrollPane();
		configSP.setViewportView(configuratorPanel);
		configSP.getVerticalScrollBar().setUnitIncrement(25);
		configSP.getHorizontalScrollBar().setUnitIncrement(15);
		footerPanel = new FooterPanel();
		footerPanel.showItems.addActionListener(this);
		footerPanel.uncheck.addActionListener(this);
		footerPanel.save.addActionListener(this);
		footerPanel.reset.addActionListener(this);
		this.add(footerPanel,BorderLayout.SOUTH);
		this.add(configSP,BorderLayout.CENTER);
 		fcProps.addPropertyListener(this);
   }

	/**
	 * Implementation of the PropertyListener.
	 * @param event the event.
	 */
    public void propertyChanged(PropertyEvent event) {
		setSaveEnabled();
	}

	// Set the enable of the Save Changes button.
	private void setSaveEnabled() {
		String saveEnabled = fcProps.getProperty("save-enabled");
		boolean enb = !((saveEnabled != null) && saveEnabled.equals("false"));
		footerPanel.save.setEnabled(enb);
	}

	/**
	 * Implementation of the ActionListener for the Save Changes button.
	 * @param event the event.
	 */
    public void actionPerformed(ActionEvent event) {
		if (event.getSource().equals(footerPanel.save)) {
			configuratorPanel.save();
			//Now that the file has been saved locally,
			//send it to the server, if update is enabled.
			UpdateUtil.postFile(new File(filename),fcProps);
		}
		else if (event.getSource().equals(footerPanel.reset)) {
			//On a reset, if update is enabled, fetch the file from the server
			UpdateUtil.getFile(new File(filename),fcProps);
			//Now show the file.
			configuratorPanel = new ConfiguratorPanel(filename);
			configSP.setViewportView(configuratorPanel);
		}
		else if (event.getSource().equals(footerPanel.uncheck)) {
			configuratorPanel.uncheckAll();
		}
		else if (event.getSource().equals(footerPanel.showItems)) {
			showAll = !showAll;
			configuratorPanel.showItems(showAll);
		}
	}

	// JPanel to display the editable fields for the anonymizer scripts
	class ConfiguratorPanel extends JPanel {
		String filename;
		public ConfiguratorPanel(String filename) {
			super();
			setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
			this.filename = filename;
			try {
				BufferedReader br =
					new BufferedReader(
						new InputStreamReader(
							new FileInputStream(filename),charset));
				String line;
				while ((line = br.readLine()) != null) {
					line = line.trim();
					if (line.startsWith("param."))
						this.add(new ParamProp(line));
					else if (line.startsWith("#set.") || line.startsWith("set."))
						this.add(new SetProp(line));
					else if (line.startsWith("#remove.") || line.startsWith("remove."))
						this.add(new RemoveProp(line));
					else if (line.startsWith("#keep.") || line.startsWith("keep."))
						this.add(new KeepProp(line));
				}
				br.close();
			}
			catch (Exception quit) { }
		}
		public void showItems(boolean all) {
			Component[] components = getComponents();
			for (int i=0; i<components.length; i++) {
				if (components[i] instanceof Prop) {
					boolean checked = ((Prop)components[i]).isChecked();
					((Prop)components[i]).makeVisible(checked || all);
				}
			}
		}
		public void uncheckAll() {
			Component[] components = getComponents();
			for (int i=0; i<components.length; i++) {
				if (components[i] instanceof Prop) {
					((Prop)components[i]).uncheck();
				}
			}
		}
		public void save() {
			StringBuffer sb = new StringBuffer();
			Component[] components = getComponents();
			for (int i=0; i<components.length; i++) {
				if (components[i] instanceof Prop) {
					sb.append(((Prop)components[i]).getText());
				}
			}
			try {
				BufferedWriter bw =
					new BufferedWriter(
						new OutputStreamWriter(
							new FileOutputStream(filename),charset));
				bw.write(sb.toString(),0,sb.length());
				bw.flush();
				bw.close();
			}
			catch (Exception e) {
				JOptionPane.showMessageDialog(this,
					"An error has occurred while saving the changes to\n" +
					"the anonymizer configuration. You should stop the\n" +
					"program now and consult IT to ensure that anonymization\n" +
					"has not been damaged in such a way as to allow PHI to\n" +
					"be transmitted on the internet.",
					"Error Saving the Anonymizer Configuration",
					JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	// Interface to provide a call to get the text of a script
	interface Prop {
		public String getText();
		public void uncheck();
		public boolean isChecked();
		public void makeVisible(boolean vis);
	}

	// Prop to handle "param." scripts
	class ParamProp extends JPanel implements Prop {
		JLabel label;
		JTextField value;
		String name;
		public ParamProp(String prop) {
			super();
			setLayout(new FlowLayout(FlowLayout.LEFT));
			prop = prop.trim();
			if (prop.startsWith("param.")) prop = prop.substring(6);
			int k = prop.indexOf("=");
			if (k == -1) k = prop.length();
			name = prop.substring(0,k).trim();
			label = new JLabel(name);
			Dimension d = label.getPreferredSize();
			d.width = 250;
			label.setPreferredSize(d);
			value = new JTextField(50);
			if (k < prop.length()) value.setText(prop.substring(k+1).trim());
			this.add(Box.createHorizontalStrut(39));
			this.add(label);
			this.add(value);
			this.add(Box.createHorizontalGlue());
		}
		public String getText() {
			return "param." + name + "=" + value.getText().trim() + "\n";
		}
		public void uncheck() {
			//do nothing
		}
		public boolean isChecked() {
			return true;
		}
		public void makeVisible(boolean vis) {
			this.setVisible(vis);
		}
	}

	// Prop to handle "set." scripts
	class SetProp extends JPanel implements Prop {
		JCheckBox cb;
		JLabel label;
		JTextField value;
		String name;
		public SetProp(String prop) {
			super();
			setLayout(new FlowLayout(FlowLayout.LEFT));
			prop = prop.trim();
			cb = new JCheckBox();
			if (prop.startsWith("#")) {
				cb.setSelected(false);
				prop = prop.substring(1);
			}
			else cb.setSelected(true);
			if (prop.startsWith("set.")) prop = prop.substring(4);
			int k = prop.indexOf("=");
			if (k == -1) k = prop.length();
			name = prop.substring(0,k).trim();
			label = new JLabel(name.replace("]","] "));
			Dimension d = label.getPreferredSize();
			d.width = 250;
			label.setPreferredSize(d);
			value = new JTextField(50);
			if (k < prop.length()) value.setText(prop.substring(k+1).trim());
			this.add(Box.createHorizontalStrut(5));
			this.add(cb);
			this.add(Box.createHorizontalStrut(3));
			this.add(label);
			this.add(value);
			this.add(Box.createHorizontalGlue());
		}
		public String getText() {
			return (cb.isSelected() ? "" : "#") + "set." + name + "=" + value.getText().trim() + "\n";
		}
		public void uncheck() {
			cb.setSelected(false);
		}
		public boolean isChecked() {
			return cb.isSelected();
		}
		public void makeVisible(boolean vis) {
			this.setVisible(vis);
		}
	}

	// Prop to handle "remove." scripts
	class RemoveProp extends JPanel implements Prop {
		JCheckBox cb;
		JLabel label;
		String name;
		public RemoveProp(String prop) {
			super();
			setLayout(new FlowLayout(FlowLayout.LEFT));
			prop = prop.trim();
			cb = new JCheckBox();
			if (prop.startsWith("#")) {
				cb.setSelected(false);
				prop = prop.substring(1);
			}
			else cb.setSelected(true);
			if (prop.startsWith("remove.")) prop = prop.substring(7);
			int k = prop.indexOf("=");
			if (k == -1) k = prop.length();
			name = prop.substring(0,k).trim();
			String labelText = "";
			if (name.equals("privategroups")) labelText = "Remove private groups [recommended]";
			else if (name.equals("unspecifiedelements")) labelText = "Remove unchecked elements";
			else if (name.equals("overlays")) labelText = "Remove overlays (groups 60xx)";
			label = new JLabel(labelText);
			Dimension d = label.getPreferredSize();
			d.width = 400;
			label.setPreferredSize(d);
			this.add(Box.createHorizontalStrut(5));
			this.add(cb);
			this.add(Box.createHorizontalStrut(3));
			this.add(label);
			this.add(Box.createHorizontalGlue());
		}
		public String getText() {
			return (cb.isSelected() ? "" : "#") + "remove." + name + "=\n";
		}
		public void uncheck() {
			cb.setSelected(false);
		}
		public boolean isChecked() {
			return cb.isSelected();
		}
		public void makeVisible(boolean vis) {
			this.setVisible(vis);
		}
	}

	// Prop to handle "keep." scripts
	class KeepProp extends JPanel implements Prop {
		JCheckBox cb;
		JLabel label;
		String name;
		public KeepProp(String prop) {
			super();
			setLayout(new FlowLayout(FlowLayout.LEFT));
			prop = prop.trim();
			cb = new JCheckBox();
			if (prop.startsWith("#")) {
				cb.setSelected(false);
				prop = prop.substring(1);
			}
			else cb.setSelected(true);
			if (prop.startsWith("keep.")) prop = prop.substring(5);
			int k = prop.indexOf("=");
			if (k == -1) k = prop.length();
			name = prop.substring(0,k).trim();
			String labelText = (k<prop.length()) ? prop.substring(k+1) : "";
			if (labelText.equals("")) {
				if (name.equals("group18")) labelText = "Keep group 18 [recommended]";
				else if (name.equals("group20")) labelText = "Keep group 20 [recommended]";
				else if (name.equals("group28")) labelText = "Keep group 28 [recommended]";
			}
			label = new JLabel(labelText);
			Dimension d = label.getPreferredSize();
			d.width = 400;
			label.setPreferredSize(d);
			this.add(Box.createHorizontalStrut(5));
			this.add(cb);
			this.add(Box.createHorizontalStrut(3));
			this.add(label);
			this.add(Box.createHorizontalGlue());
		}
		public String getText() {
			return (cb.isSelected() ? "" : "#") + "keep." + name + "=" + label.getText() + "\n";
		}
		public void uncheck() {
			cb.setSelected(false);
		}
		public boolean isChecked() {
			return cb.isSelected();
		}
		public void makeVisible(boolean vis) {
			this.setVisible(vis);
		}
	}

	// Class to display a single property script
	class FooterPanel extends JPanel implements ActionListener {
		public JButton save;
		public JButton reset;
		public JButton uncheck;
		public JButton showItems;
		public boolean showAll = true;
		public FooterPanel() {
			super();
			this.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
			this.setLayout(new FlowLayout());
			this.setBackground(background);
			showItems = new JButton("Show Checked Elements Only");
			showItems.addActionListener(this);
			this.add(showItems);
			this.add(Box.createHorizontalStrut(20));
			uncheck = new JButton("Uncheck All");
			this.add(uncheck);
			this.add(Box.createHorizontalStrut(20));
			save = new JButton("Save Changes");
			this.add(save);
			this.add(Box.createHorizontalStrut(20));
			reset = new JButton("Reset");
			this.add(reset);
		}
	    public void actionPerformed(ActionEvent event) {
			showAll = !showAll;
			if (showAll) showItems.setText("Show Checked Elements Only");
			else showItems.setText("Show All Elements");
		}
	}

}
