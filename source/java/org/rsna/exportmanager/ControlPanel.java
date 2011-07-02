/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.exportmanager;

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
 * A JPanel to provide a user interface for the ExportManager
 * application's properties.
 */
public class ControlPanel extends JPanel implements ActionListener {

	private ConfiguratorPanel configuratorPanel;
	private FooterPanel footerPanel;
	ApplicationProperties props;
	EventListenerList listenerList;

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
		footerPanel.setBackground(ExportManager.background);
		footerPanel.save.addActionListener(this);
		footerPanel.reset.addActionListener(this);
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
		}
		else if (event.getSource().equals(footerPanel.reset)) {
			this.remove(configuratorPanel);
			configuratorPanel = new ConfiguratorPanel(props);
			this.add(configuratorPanel,BorderLayout.CENTER);
			this.revalidate();
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
			this.addTab("Title",new MainPane(props));
			this.addTab("Event Log",new EventLogPane(props));
			this.addTab("Store SCP",new StoreScpPane(props));
			this.addTab("Q/R SCU & SCP",new QRPane(props));
			this.addTab("Anonymizer",new AnonPane(props));
			this.addTab("Export Service",new ExportPane(props));
			this.addTab("HTTP Server",new HttpPane(props));
			this.addTab("Proxy Server",new ProxyPane(props));
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

	//The PropScrollPane subclass for the Program props
	class MainPane extends PropScrollPane {
		public MainPane(ApplicationProperties props) {
			super();
			panel.add(new TextFieldProp(
				"Title: ","title",props));
		}
	}

	//The PropScrollPane subclass for the Event Log props
	class EventLogPane extends PropScrollPane {
		public EventLogPane(ApplicationProperties props) {
			super();
			panel.add(new TextFieldProp(
				"Event log depth: ","log-depth",props));
		}
	}

	//The PropScrollPane subclass for the DICOM Storage SCP props
	class StoreScpPane extends PropScrollPane {
		public StoreScpPane(ApplicationProperties props) {
			super();
			panel.add(new CheckBoxProp(
				"IP address autodetect: ","ipaddress-autodetect",props));
			panel.add(new TextFieldProp(
				"DICOM Storage SCP IP Address: ","scp-ipaddress",props));
			panel.add(new TextFieldProp(
				"DICOM Storage SCP Port: ","scp-port",props));
			panel.add(new TextFieldProp(
				"DICOM Storage SCP AE Title: ","scp-aetitle",props));
			panel.add(new TextFieldProp(
				"Manifest timeout (minutes): ","timeout",props));
		}
	}

	//The PropScrollPane subclass for the DICOM Q/R SCP & SCUprops
	class QRPane extends PropScrollPane {
		public QRPane(ApplicationProperties props) {
			super();
			panel.add(new CheckBoxProp(
				"Q/R for instances: ","qr-instances",props));
			panel.add(new TextFieldProp(
				"DICOM Q/R SCU AE Title: ","qr-scu-aetitle",props));
			panel.add(new TextFieldProp(
				"DICOM Q/R SCP IP Address: ","qr-scp-ipaddress",props));
			panel.add(new TextFieldProp(
				"DICOM Q/R SCP Port: ","qr-scp-port",props));
			panel.add(new TextFieldProp(
				"DICOM Q/R SCP AE Title: ","qr-scp-aetitle",props));
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

	//The PropScrollPane subclass for the Export Service props
	class ExportPane extends PropScrollPane {
		public ExportPane(ApplicationProperties props) {
			super();
			panel.add(new CheckBoxProp(
				"Export enabled: ","export-enabled",props));
			panel.add(new TextFieldProp(
				"Destination URL: ","destination",props));
			panel.add(new RadioButtonProp(
				"Transmit manifest: ","send-manifest-first",props,"First","Last"));
		}
	}

	//The PropScrollPane subclass for the HTTP Server props
	class HttpPane extends PropScrollPane {
		public HttpPane(ApplicationProperties props) {
			super();
			panel.add(new CheckBoxProp(
				"HTTP Server enabled: ","http-enabled",props));
			panel.add(new TextFieldProp(
				"HTTP Server Port: ","http-port",props));
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

	//The Prop class version that handles a radio button property
	class RadioButtonProp extends ComponentProp implements Prop {
		JRadioButton b1;
		JRadioButton b2;
		ButtonGroup bg;
		public RadioButtonProp(
				String labeltext,
				String name,
				Properties props,
				String option1,
				String option2) {
			super(labeltext,name);
			b1 = new JRadioButton(option1);
			b2 = new JRadioButton(option2);
			bg = new ButtonGroup();
			bg.add(b1);
			bg.add(b2);
			String selected = props.getProperty(name);
			if (selected == null) selected = "";
			if (!selected.equals("false")) b1.setSelected(true);
			else b2.setSelected(true);
			JPanel jp = new JPanel();
			jp.add(b1);
			jp.add(b2);
			addComponents(jp);
		}
		public String getValue() { return (b1.isSelected() ? "true" : "false"); }
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
			d.width = 200;
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
		}
	}

}
