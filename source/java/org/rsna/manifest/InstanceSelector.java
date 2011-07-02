/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.manifest;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import org.rsna.util.DicomTextPanel;

/**
 * A JPanel that provides a text dump of a DicomObject.
 */
public class InstanceSelector extends JPanel implements ActionListener {

	JFileChooser chooser = null;
    ButtonPanel buttonPanel;
    MainPanel mainPanel;
    FooterPanel footerPanel;
    DicomTextPanel dicomTextPanel;
    Color background = Color.getHSBColor(0.58f, 0.17f, 0.95f);

	/**
	 * Class constructor; creates a DicomTextPanel with a button panel.
	 */
    public InstanceSelector() {
		super();
		this.setLayout(new BorderLayout());
		this.setBackground(background);

		JPanel left = new JPanel(new BorderLayout());
		buttonPanel = new ButtonPanel();
		buttonPanel.addActionListener(this);
		left.add(buttonPanel,BorderLayout.NORTH);

		mainPanel = new MainPanel();
		JScrollPane jsp = new JScrollPane();
		jsp.setViewportView(mainPanel);
		jsp.getVerticalScrollBar().setUnitIncrement(25);
		jsp.getHorizontalScrollBar().setUnitIncrement(15);
		left.add(jsp,BorderLayout.CENTER);

		footerPanel = new FooterPanel();
		this.add(footerPanel,BorderLayout.SOUTH);

		dicomTextPanel = new DicomTextPanel(false);

		JSplitPane jSplitPane = new JSplitPane();
		jSplitPane.setResizeWeight(0.0D);
		jSplitPane.setDividerLocation(0.5D);
		jSplitPane.setContinuousLayout(true);
		jSplitPane.setLeftComponent(left);
		jSplitPane.setRightComponent(dicomTextPanel);
		this.add(jSplitPane,BorderLayout.CENTER);
    }

    /**
     * The ActionListener implementation.
     * @param event the event indicating which button was clicked.
     */
    public void actionPerformed(ActionEvent event) {
		Object source = event.getSource();
		if (source instanceof JCheckBox) {
			JCheckBox jcb = (JCheckBox)source;
			if (jcb.isSelected()) {
				String name = jcb.getText();
				File dir = new File(footerPanel.getDir());
				dicomTextPanel.openFile(new File(dir,name));
			}
		}
		else if (event.getActionCommand().equals("Open Directory")) {
			if (chooser == null) {
				File here = new File(System.getProperty("user.dir"));
				chooser = new JFileChooser(here);
				chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			}
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				File dir = chooser.getSelectedFile();
				mainPanel.setDir(dir);
				footerPanel.setDir(dir);
				buttonPanel.setEnables(true);
				this.validate();
				this.repaint();
				mainPanel.addActionListener(this);
			}
		}
		else if (event.getActionCommand().equals("Select All")) {
			mainPanel.setSelected(true);
		}
		else if (event.getActionCommand().equals("Deselect All")) {
			mainPanel.setSelected(false);
		}
	}

    /**
     * Get the selected files from the mainPanel.
     * @return the files whose checkboxes have been checked.
     */
    public File[] getSelectedFiles() {
		return mainPanel.getSelectedFiles();
	}

    /**
     * Get the currently open directory containing the selected instances.
     * @return the currently open directory.
     */
    public File getCurrentDirectory() {
		return new File(footerPanel.getDir());
	}

	//Miscellaneous GUI Panels

	class MainPanel extends JPanel {
		public MainPanel() {
			super();
			this.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
			this.setLayout(new ColumnLayout(3));
		}
		public void setDir(File dir) {
			if (!dir.isDirectory()) {
				JOptionPane.showMessageDialog(this,"Program Error: Not a directory");
				return;
			}
			//Remove all the components in the panel
			this.removeAll();
			//Put in all the files
			File[] files = dir.listFiles();
			for (int i=0; i<files.length; i++) {
				if (files[i].isFile()) {
					JCheckBox jcb = new JCheckBox(files[i].getName());
					this.add(jcb);
				}
			}
		}
		public void addActionListener(ActionListener listener) {
			Component[] components = this.getComponents();
			for (int i=0; i<components.length; i++) {
				if (components[i] instanceof JCheckBox) {
					((JCheckBox)components[i]).addActionListener(listener);
				}
			}
		}
		public void setSelected(boolean state) {
			Component[] components = this.getComponents();
			for (int i=0; i<components.length; i++) {
				if (components[i] instanceof JCheckBox) {
					((JCheckBox)components[i]).setSelected(state);
				}
			}
		}
		public File[] getSelectedFiles() {
			ArrayList<File> files = new ArrayList<File>();
			File dir = new File(footerPanel.getDir());
			Component[] components = this.getComponents();
			for (int i=0; i<components.length; i++) {
				if (components[i] instanceof JCheckBox) {
					JCheckBox jcb = (JCheckBox)components[i];
					if (jcb.isSelected()) {
						files.add(new File(dir,jcb.getText()));
					}
				}
			}
			File[] result = new File[files.size()];
			return files.toArray(result);
		}
	}

	class ButtonPanel extends JPanel {
		public JButton openDirectory;
		public JButton selectAll;
		public JButton deselectAll;
		public ButtonPanel() {
			super();
			this.setLayout(new FlowLayout(FlowLayout.LEFT));
			this.setBackground(background);
			openDirectory = new JButton("Open Directory");
			openDirectory.setToolTipText("Open a directory to select instances");
			this.add(openDirectory);
			selectAll = new JButton("Select All");
			selectAll.setToolTipText("Check all the files");
			this.add(selectAll);
			deselectAll = new JButton("Deselect All");
			deselectAll.setToolTipText("Uncheck all the files");
			this.add(deselectAll);
			setEnables(false);
		}
		public void addActionListener(ActionListener listener) {
			openDirectory.addActionListener(listener);
			selectAll.addActionListener(listener);
			deselectAll.addActionListener(listener);
		}
		public void setEnables(boolean state) {
			selectAll.setEnabled(state);
			deselectAll.setEnabled(state);
		}
	}

	class FooterPanel extends JPanel {
		JLabel dirName;
		public FooterPanel() {
			super();
			this.setLayout(new FlowLayout(FlowLayout.LEFT));
			this.setBackground(background);
			dirName = new JLabel(" ");
			this.add(dirName);
		}
		public void setDir(File dir) {
			dirName.setText(dir.getAbsolutePath());
		}
		public String getDir() {
			return dirName.getText();
		}
	}

	//Layout Manager for the main panel.
	//This manager puts each component on its own line.
	class ColumnLayout implements LayoutManager {
		private int verticalGap;

		public ColumnLayout(int verticalGap) {
			this.verticalGap = verticalGap;
		}

		public void addLayoutComponent(String name,Component component) { }
		public void removeLayoutComponent(Component component) { }

		public Dimension preferredLayoutSize(Container parent) {
			return getLayoutSize(parent,verticalGap,false);
		}

		public Dimension minimumLayoutSize(Container parent) {
			return getLayoutSize(parent,verticalGap,false);
		}

		public void layoutContainer(Container parent) {
			getLayoutSize(parent,verticalGap,true);
		}

		private Dimension getLayoutSize(Container parent, int vGap, boolean layout) {
			Component[] components = parent.getComponents();
			Insets insets = parent.getInsets();
			int currentY = insets.top;
			int width = 0;
			Dimension d;
			for (int i=0; i<components.length; i++) {
				d = components[i].getPreferredSize();
				if (layout) components[i].setBounds(insets.left, currentY, d.width, d.height);
				currentY += d.height + vGap;
				width = Math.max(width,d.width);
			}
			return new Dimension(width+insets.left+insets.right, currentY+insets.bottom);
		}
	}
}
