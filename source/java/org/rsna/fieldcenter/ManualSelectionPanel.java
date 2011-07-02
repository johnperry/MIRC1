/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.fieldcenter;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import org.rsna.util.*;

/**
 * A JPanel to display a DirectoryPane and accompanying GUI components
 * to support manual file selection and queuing for processing.
 */
public class ManualSelectionPanel extends JPanel {

	public ManualSelectionPanel(ApplicationProperties properties) {
		super(new BorderLayout());
		SourcePanel sourcePanel =
			new SourcePanel(properties);
		RightPanel rightPanel =
			new RightPanel(properties,sourcePanel);
		JSplitPane jSplitPane =
			new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,sourcePanel,rightPanel);
		jSplitPane.setResizeWeight(0.8D);
		jSplitPane.setContinuousLayout(true);
		this.add(jSplitPane, BorderLayout.CENTER);
	}

}
