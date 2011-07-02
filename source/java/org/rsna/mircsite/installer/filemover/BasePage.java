package org.rsna.mircsite.installer.filemover;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.Border;


public class BasePage extends JPanel {
	
	private static final long serialVersionUID = 1231234123124123l;	

	protected JPanel	buttonPanel;
	
	protected JEditorPane htmlPane;

	
	protected String id;
	
	public BasePage(){
		Border etchedBorder = BorderFactory.createEtchedBorder(Color.black,Color.white);
		this.setBorder(etchedBorder);
		this.setLayout(new BorderLayout());
		this.setBackground(FileMover.background);
		buttonPanel = new JPanel();
		buttonPanel.setBackground(Color.gray);
		buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		this.add(buttonPanel,BorderLayout.SOUTH);
		
		JScrollPane scrollPane = new JScrollPane();
		htmlPane = new JEditorPane();
		htmlPane.setEditable(false);
		htmlPane.setContentType("text/html");
		htmlPane.setBackground(FileMover.background);
		scrollPane.setViewportView(htmlPane);
		this.add(scrollPane,BorderLayout.CENTER);


		
	}

}
