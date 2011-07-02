/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.manifest;


import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;
import org.rsna.util.DicomTextPanel;

public class ManifestGenerator extends JFrame {

    String windowTitle = "Manifest Generator - version 2";
    ContentPanel contentPanel;
    InstanceSelector instanceSelector;

    public static void main(String args[]) {
        new ManifestGenerator();
    }

    public ManifestGenerator() {
		JTabbedPane tabbedPane = new JTabbedPane();

		instanceSelector = new InstanceSelector();
		contentPanel = new ContentPanel(instanceSelector);

		tabbedPane.addTab("Instance Selector",instanceSelector);
		tabbedPane.addTab("Manifest Constructor",contentPanel);

        getContentPane().add(tabbedPane, BorderLayout.CENTER);

        setTitle(windowTitle);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent evt) {
                exitForm(evt);
            }
        });

        pack();
        centerFrame();
        setVisible(true);
	}

    private void centerFrame() {
        Toolkit t = getToolkit();
        Dimension scr = t.getScreenSize ();
        setSize(scr.width*3/4, scr.height/2);
        setLocation (new Point ((scr.width-getSize().width)/2,
                                (scr.height-getSize().height)/2));
    }

    private void exitForm(java.awt.event.WindowEvent evt) {
        System.exit(0);
    }

}
