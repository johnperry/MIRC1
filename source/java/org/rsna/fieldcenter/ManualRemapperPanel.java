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
import java.io.*;

import org.rsna.mircsite.anonymizer.LocalRemapper;
import org.rsna.mircsite.anonymizer.Remapper;
import org.rsna.mircsite.anonymizer.RemoteRemapper;
import org.rsna.util.ApplicationProperties;

/**
 * A JPanel to provide a user interface for manually obtaining remapped values.
 */
public class ManualRemapperPanel extends JPanel {

	RemapperPanel remapperPanel;
	UIDFooterPanel uidFooterPanel;
	JScrollPane jsp;

	RMTextField rmUIDOriginal;
	RMTextField rmUIDResult;

	RMTextField siteID;
	RMTextField ptID;
	RMTextField tag;
	RMTextField dateRemapped;
	RMTextField dateBase;
	RMTextField dateOriginal;

	ApplicationProperties props;

    /**
     * Class constructor; creates a user interface.
     */
    public ManualRemapperPanel(ApplicationProperties props) {
		super();
		setLayout(new BorderLayout());

		this.props = props;

		rmUIDResult = new RMTextField("");
		rmUIDOriginal = new RMTextField("");

		siteID = new RMTextField("");
		ptID = new RMTextField("");
		tag = new RMTextField("(0008,0020)");
		dateRemapped = new RMTextField("");
		dateBase = new RMTextField("20000101");
		dateOriginal = new RMTextField("");

		remapperPanel = new RemapperPanel();
		jsp = new JScrollPane();
		jsp.setViewportView(remapperPanel);
		this.add(jsp,BorderLayout.CENTER);
		jsp.getVerticalScrollBar().setUnitIncrement(25);
		jsp.getVerticalScrollBar().setBlockIncrement(25);
    }

    //Panel to collect all the sub-panels
    class RemapperPanel extends JPanel {
		public RemapperPanel() {
			this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			this.add(new UIDRemapperPanel());
			this.add(new DateRemapperPanel());
		}
	}

	//Base class for sub-panels
	class SubPanel extends JPanel {
		public SubPanel() {
			super();
			this.setLayout(new TypeFormattedLayout());
			this.setBorder(
				BorderFactory.createCompoundBorder(
					BorderFactory.createBevelBorder(BevelBorder.LOWERED),
					BorderFactory.createEmptyBorder(5,5,5,5)
				)
			);
		}
	}

	//Panel with entry fields for UIDs.
	class UIDRemapperPanel extends SubPanel {
		public UIDRemapperPanel() {
			super();
			add(new RMFieldHeader("UID"));
			add(new CRLF());
			add(new RMFieldLabel("Original UID:"));
			add(rmUIDOriginal);
			add(new CRLF());
			add(new RMFieldLabel("Remapped UID:"));
			add(rmUIDResult);
			add(new CRLF());
			add(new UIDFooterPanel());
		}
	}

	class UIDFooterPanel extends JPanel implements ActionListener {
		public JButton remap;
		public JButton invert;
		public UIDFooterPanel() {
			super();
			remap = new JButton("Forward Map");
			remap.addActionListener(this);
			invert = new JButton("Inverse Map");
			invert.addActionListener(this);
			this.add(remap);
			this.add(Box.createHorizontalStrut(20));
			this.add(invert);
		}
		public void actionPerformed(ActionEvent event) {
			Remapper remapper = getRemapper();
			if (event.getSource().equals(remap)) {
				remapper.getRemappedUID(1, rmUIDOriginal.getText());
				try {
					Hashtable results = remapper.getRemappedValues();
					rmUIDResult.setText((String)results.get("1"));
				}
				catch (Exception ex) {
					rmUIDResult.setText("Error");
				}
			}
			else {
				remapper.getOriginalUID( 1, rmUIDResult.getText());
				try {
					Hashtable results = remapper.getRemappedValues();
					rmUIDOriginal.setText((String)results.get("1"));
				}
				catch (Exception ex) {
					rmUIDOriginal.setText("Error");
				}
			}
		}
	}

	class DateRemapperPanel extends SubPanel {
		public DateRemapperPanel() {
			super();
			add(new RMFieldHeader("Date"));
			add(new CRLF());
			add(new RMFieldLabel("Site ID:"));
			add(siteID);
			add(new CRLF());
			add(new RMFieldLabel("Patient ID:"));
			add(ptID);
			add(new CRLF());
			add(new RMFieldLabel("DICOM tag:"));
			add(tag);
			add(new CRLF());
			add(new RMFieldLabel("Remapped date:"));
			add(dateRemapped);
			add(new CRLF());
			add(new RMFieldLabel("Base date:"));
			add(dateBase);
			add(new CRLF());
			add(new RMFieldLabel("Original date:"));
			add(dateOriginal);
			add(new CRLF());
			add(new DateFooterPanel());
		}
	}

	class DateFooterPanel extends JPanel implements ActionListener {
		public JButton invert;
		public DateFooterPanel() {
			super();
			invert = new JButton("Inverse Map");
			invert.addActionListener(this);
			this.add(invert);
		}
		public void actionPerformed(ActionEvent event) {
			Remapper remapper = getRemapper();
			remapper.getOriginalDate(
						1,
						siteID.getText(),
						ptID.getText(),
						tag.getText(),
						dateRemapped.getText(),
						dateBase.getText());
			try {
				Hashtable results = remapper.getRemappedValues();
				dateOriginal.setText((String)results.get("1"));
			}
			catch (Exception ex) {
				dateOriginal.setText("Error");
			}
		}
	}

	//Get a remapper based on the current properties.
	private Remapper getRemapper() {
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

	Font headerLabelFont = new Font("Dialog", Font.BOLD, 18);
	class RMFieldHeader extends JLabel {
		public RMFieldHeader(String text) {
			super(text);
			this.setForeground(Color.blue);
			this.setFont(headerLabelFont);
		}
	}

	Font fieldLabelFont = new Font("Dialog", Font.BOLD, 14);
	class RMFieldLabel extends JLabel {
		public RMFieldLabel(String text) {
			super(text);
			this.setForeground(Color.black);
			this.setFont(fieldLabelFont);
			setWidth(this, 150);
		}
	}

	Font textFieldFont = new Font("Monospaced", Font.BOLD, 14);
	class RMTextField extends JTextField {
		public RMTextField(String text) {
			super(text);
			this.setForeground(Color.black);
			this.setFont(textFieldFont);
			setWidth(this, 500);
		}
	}

	class CRLF extends JComponent {
		public CRLF() {
			super();
			setVisible(false);
		}
	}

	void setWidth(Component c, int width) {
		Dimension d = c.getPreferredSize();
		d.width = width;
		c.setPreferredSize(d);
	}

	//Layout Manager for the remapper panel.
	class TypeFormattedLayout implements LayoutManager {
		private int topIndent = 10;
		private int vGap = 4;
		private int leftIndent = 20;

		public TypeFormattedLayout() { }

		public void addLayoutComponent(String name,Component component) { }
		public void removeLayoutComponent(Component component) { }

		public Dimension preferredLayoutSize(Container parent) {
			return getLayoutSize(parent,false);
		}

		public Dimension minimumLayoutSize(Container parent) {
			return getLayoutSize(parent,false);
		}

		public void layoutContainer(Container parent) {
			getLayoutSize(parent,true);
		}

		private Dimension getLayoutSize(Container parent, boolean layout) {
			//Get the width in case we need to center
			Component p = parent.getParent();
			int width;
			if (p instanceof JScrollPane)
				width = ((JScrollPane)p).getViewport().getExtentSize().width;
			else
				width = parent.getSize().width;

			//Set up for the layout
			Insets insets = parent.getInsets();
			int currentY = 0;
			int currentX = 0;
			int maxY = 0;
			int maxX = 0;
			int leftMargin = 0;
			int topMargin = 0;
			Dimension d;
			Component c;
			Component[] components = parent.getComponents();
			for (int i=0; i<components.length; i++) {
				c = components[i];
				if (c instanceof RMFieldHeader) {
					topMargin = topIndent;
					leftMargin = 0;
				}
				else if (c instanceof RMFieldLabel) {
					topMargin = 0;
					leftMargin = leftIndent;
				}
				else if (c instanceof RMTextField) {
					topMargin = 0;
					leftMargin = 0;
				}
				else if (c instanceof JPanel) {
					topMargin = 0;
					d = c.getPreferredSize();
					leftMargin = (width - d.width)/2;
				}
				if (c instanceof CRLF) {
					currentX = 0;
					currentY = maxY;
				}
				else {
					//It's not a CRLF, lay it out.
					d = c.getPreferredSize();
					if (layout) {
						c.setBounds(insets.left + leftMargin + currentX,
									insets.top + topMargin + currentY,
									d.width,
									d.height);
					}
					currentX += leftMargin +  d.width;
					maxX = Math.max(maxX, currentX);
					maxY = Math.max(maxY, currentY + topMargin + d.height + vGap);
				}
			}
			return new Dimension(insets.left + maxX + insets.right,
								 insets.top + maxY + insets.bottom);
		}
	}

}
