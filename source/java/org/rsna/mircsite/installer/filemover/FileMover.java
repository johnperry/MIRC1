package org.rsna.mircsite.installer.filemover;


import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JPanel;

/**
 * This is a java applet created to move the key MIRC files from an old version of tomcat (4.x), 
 * to the new version of tomcat (5.x).  It tries to auto-find the old and new directories for you, 
 * but allows you to change them.
 * @author RBoden
 *
 */
public class FileMover extends JFrame {
	
	private static final long serialVersionUID = 1231234123124123l;	
	
	private static final String WINDOW_TITLE = "MIRC File Mover";
	private static CardLayout cardLayout = null;
	private static JPanel mainPanel = null;
	protected static Color background = null;
	private static BasePage[] componentList = null;

	public static void main(String args[]) {
		new FileMover();
	}
	
	
	/**
	 * Class constructor; creates a new installer and loads all the pages.
	 */
	public FileMover() {
		this.getContentPane().setLayout(new BorderLayout());
		setTitle(WINDOW_TITLE);
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent evt) {System.exit(0);} });
		cardLayout = new CardLayout();
		mainPanel = new JPanel(cardLayout);
		this.getContentPane().add(mainPanel, BorderLayout.CENTER);
		background = new Color(0xEBEBEB);

		componentList = new BasePage[] {
			new WelcomePage()
		};

		for (int i=0; i<componentList.length; i++) {
			mainPanel.add(componentList[i], componentList[i].id);
		}
     	pack();
		// center the frame
		Toolkit t = getToolkit();
		Dimension scr = t.getScreenSize ();
		setSize(scr.width/2, scr.height/2);
		int x = (scr.width-getSize().width)/2;
		int y = (scr.height-getSize().height)/2;
		setLocation(new Point (x,y));
		setVisible(true);
	}
	

	

	
	
}
