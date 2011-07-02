/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.fieldcenter;

import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;
import org.rsna.util.*;

/**
 * A JPanel to provide a user interface for the FieldCenter
 * application's properties.
 */
public class ControlPanel extends JPanel implements ActionListener {

	private ConfiguratorPanel configuratorPanel;
	private FooterPanel footerPanel;
	ApplicationProperties props;
	EventListenerList listenerList;
	String[] fileList = {
		"FieldCenter.jar",
		"anonymizer.jar",
		"dcm4che.jar",
		"dicom.jar",
		"getopt.jar",
		"log4j.jar",
		"mirclog.jar",
		"rsnautil.jar",
		"help.html"
	};

    /**
     * Class constructor; creates a user interface and loads it with the
     * application property values.
     * @param props the properties object.
     */
    public ControlPanel(ApplicationProperties props) {
		super();
		setLayout(new BorderLayout());
		this.props = props;
		listenerList = new EventListenerList();
		configuratorPanel = new ConfiguratorPanel(props);
		footerPanel = new FooterPanel();
		footerPanel.setBackground(FieldCenter.background);
		footerPanel.save.addActionListener(this);
		footerPanel.reset.addActionListener(this);
		footerPanel.swupdate.addActionListener(this);
		this.add(footerPanel,BorderLayout.SOUTH);
		this.add(configuratorPanel,BorderLayout.CENTER);
    }

    /**
     * The ActionListener implementation; listens for the buttons
     * at the bottom of the panel and either saves or resets all
     * the application properties.
     * @param event the event indicating which button was clicked.
     */
    public void actionPerformed(ActionEvent event) {
		if (event.getSource().equals(footerPanel.save)) {
			configuratorPanel.save();
			//If the update server is enabled, send the file to the server.
			UpdateUtil.postFile(new File(props.getFilename()),props);
		}
		else if (event.getSource().equals(footerPanel.reset)) {
			//If the update server is enabled, get the file from the server.
			UpdateUtil.getFile(new File(props.getFilename()),props);
			//Now redisplay the panel
			this.remove(configuratorPanel);
			configuratorPanel = new ConfiguratorPanel(props);
			this.add(configuratorPanel,BorderLayout.CENTER);
			this.revalidate();
		}
		else if (event.getSource().equals(footerPanel.swupdate)) {
			//If the update server is enabled,
			//Get all the software from the server
			boolean found = false;
			String names = "";
			for (int i=0; i<fileList.length; i++) {
				File file = new File(fileList[i]);
				if (UpdateUtil.getFile(file,true,props)) {
					found = true;
					names += "   " + fileList[i] + "\n";
				}
			}
			if (found)
				JOptionPane.showMessageDialog(
					this,
					"New software has been installed:\n" + names +
					"You must exit the application and\n" +
					"restart it for the changes to take\n" +
					"effect.");
			else
				JOptionPane.showMessageDialog(
					this,
					"No software updates were installed.");
		}
	}

	//An interface for a generic property object.
	interface Prop {
		public String getName();
		public String getValue();
	}

	//The main panel of the interface; displays the application's
	//properties and provides the save method.
	class ConfiguratorPanel extends JTabbedPane {
		ApplicationProperties props;
		public ConfiguratorPanel(ApplicationProperties props) {
			super(JTabbedPane.TOP);
			this.props = props;
			this.addTab("Trial",new TrialPane(props));
			this.addTab("Object Log",new LogPane(props));
			this.addTab("DICOM SCP",new ScpPane(props));
			this.addTab("HTTP Receiver",new HttpPane(props));
			this.addTab("Anonymizer",new AnonPane(props));
			this.addTab("Proxy",new ProxyPane(props));
			this.addTab("Update",new UpdatePane(props));
		}
		public void save() {
			int tabCount = this.getTabCount();
			for (int i=0; i<tabCount; i++) {
				Component c = this.getComponentAt(i);
				if (c instanceof PropScrollPane) {
					((PropScrollPane)c).save(props);
				}
			}
			props.store();
			props.notifyListeners();
		}
	}

	//A general JScrollPane for one category of props
	class PropScrollPane extends JScrollPane {
		Box panel;
		public PropScrollPane() {
			super();
			JPanel mainPanel = new JPanel();
			mainPanel.setLayout(new BorderLayout());
			panel = Box.createVerticalBox();
			panel.add(Box.createVerticalStrut(10));
			mainPanel.add(panel,BorderLayout.NORTH);
			this.setViewportView(mainPanel);
		}
		public void save(ApplicationProperties props) {
			Component[] components = panel.getComponents();
			for (int i=0; i<components.length; i++) {
				if (components[i] instanceof Prop) {
					Prop prop = (Prop)components[i];
					props.setProperty(prop.getName(),prop.getValue());
				}
			}
		}
	}

	//The PropScrollPane subclass for the Trial props
	class TrialPane extends PropScrollPane {
		public TrialPane(ApplicationProperties props) {
			super();
			panel.add(new TextFieldProp(
				"Trial name: ","trial",props));
			panel.add(new TextFieldProp(
				"Destination URL: ","destination",props));
			panel.add(new CheckBoxProp(
				"Export enabled: ","export-enabled",props));
		}
	}

	//The LogPane subclass for the Log props
	class LogPane extends PropScrollPane {
		public LogPane(ApplicationProperties props) {
			super();
			panel.add(new CheckBoxProp(
				"Save received objects: ","save-received-objects",props));
			panel.add(new CheckBoxProp(
				"Save transmitted objects: ","save-transmitted-objects",props));
		}
	}

	//The PropScrollPane subclass for the DICOM SCP props
	class ScpPane extends PropScrollPane {
		public ScpPane(ApplicationProperties props) {
			super();
			panel.add(new CheckBoxProp(
				"IP address autodetect: ","ipaddress-autodetect",props));
			panel.add(new TextFieldProp(
				"DICOM SCP IP Address: ","scp-ipaddress",props));
			panel.add(new TextFieldProp(
				"DICOM SCP Port: ","scp-port",props));
			panel.add(new TextFieldProp(
				"DICOM SCP AE Title: ","scp-aetitle",props));
		}
	}

	//The PropScrollPane subclass for the HttpReceiver props
	class HttpPane extends PropScrollPane {
		public HttpPane(ApplicationProperties props) {
			super();
			panel.add(new TextFieldProp(
				"Protocol (http or https): ","http-protocol",props));
			panel.add(new TextFieldProp(
				"Port: ","http-port",props));
		}
	}

	//The PropScrollPane subclass for the DicomAnonymizer props
	class AnonPane extends PropScrollPane {
		public AnonPane(ApplicationProperties props) {
			super();
			panel.add(new CheckBoxProp(
				"Anonymizer enabled: ","anonymizer-enabled",props));
			panel.add(new CheckBoxProp(
				"Central Remapper enabled: ","remapper-enabled",props, false));
			panel.add(new TextFieldProp(
				"Central Remapper URL: ","remapper-url",props));
			panel.add(new CheckBoxProp(
				"Anonymizer force IVRLE: ","forceIVRLE",props));
			panel.add(new CheckBoxProp(
				"Anonymizer script saving enabled: ","save-enabled",props));
		}
	}

	//The PropScrollPane subclass for the Proxy Server props
	class ProxyPane extends PropScrollPane {
		public ProxyPane(ApplicationProperties props) {
			super();
			panel.add(new CheckBoxProp(
				"Proxy Server enabled: ","proxy-enabled",props));
			panel.add(new TextFieldProp(
				"Proxy Server IP Address: ","proxy-ipaddress",props));
			panel.add(new TextFieldProp(
				"Proxy Server Port: ","proxy-port",props));
			panel.add(new TextFieldProp(
				"Proxy Server Username: ","proxy-username",props));
			panel.add(new TextFieldProp(
				"Proxy Server Password: ","proxy-password",props));
		}
	}

	//The PropScrollPane subclass for the Update props
	class UpdatePane extends PropScrollPane {
		public UpdatePane(ApplicationProperties props) {
			super();
			if (props.getProperty("update-enabled") == null)
				props.setProperty("update-enabled","false");
			panel.add(new CheckBoxProp(
				"Update Server enabled: ","update-enabled",props));
			panel.add(new TextFieldProp(
				"Update Server URL: ","update-url",props));
			panel.add(new TextFieldProp(
				"Update Server Username: ","update-username",props));
			panel.add(new TextFieldProp(
				"Update Server Password: ","update-password",props));
		}
	}

	//The Prop class version that handles a text property.
	class TextFieldProp extends ComponentProp implements Prop {
		JTextField value;
		public TextFieldProp(String labeltext, String name, Properties props) {
			super(labeltext,name);
			value = new JTextField(40);
			String valuetext = props.getProperty(name);
			if (valuetext == null) valuetext = "";
			value.setText(valuetext);
			addComponents(value);
		}
		public String getValue() { return value.getText(); }
	}

	//The Prop class version that handles a true/false property
	class CheckBoxProp extends ComponentProp implements Prop {
		JCheckBox cb;
		public CheckBoxProp(String labeltext, String name, Properties props) {
			this(labeltext,name,props,true);
		}
		public CheckBoxProp(String labeltext, String name, Properties props, boolean defaultValue) {
			super(labeltext,name);
			cb = new JCheckBox();
			String selected = props.getProperty(name);
			if (selected == null)
				cb.setSelected(defaultValue);
			else
				cb.setSelected(!selected.equals("false"));
			addComponents(cb);
		}
		public String getValue() { return (cb.isSelected() ? "true" : "false"); }
	}

	//The root class for Props
	class ComponentProp extends JPanel {
		JLabel label;
		JCheckBox cb;
		String name;
		public ComponentProp(String labeltext, String name) {
			super();
			setLayout(new FlowLayout(FlowLayout.LEFT));
			this.name = name;
			label = new JLabel(labeltext,SwingConstants.RIGHT);
			Dimension d = label.getPreferredSize();
			d.width = 250;
			label.setPreferredSize(d);
		}
		public void addComponents(Component component) {
			this.add(Box.createHorizontalStrut(20));
			this.add(label);
			this.add(Box.createHorizontalStrut(3));
			this.add(component);
			this.add(Box.createHorizontalGlue());
		}
		public String getName() { return name; }
	}

	//The bottom panel with the save and reset buttons
	class FooterPanel extends JPanel {
		public JButton save;
		public JButton reset;
		public JButton swupdate;
		public FooterPanel() {
			super();
			this.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
			this.setLayout(new FlowLayout());
			this.setBackground(Color.lightGray);
			save = new JButton("Save Changes");
			this.add(save);
			this.add(Box.createHorizontalStrut(20));
			reset = new JButton("Reset");
			this.add(reset);
			this.add(Box.createHorizontalStrut(20));
			swupdate = new JButton("Software Update");
			this.add(swupdate);
		}
	}

}
