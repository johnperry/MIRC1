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
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import org.rsna.mircsite.log.Log;
import org.rsna.mircsite.anonymizer.IdTable;
import org.rsna.util.AnonymizerPanel;
import org.rsna.util.ApplicationProperties;
import org.rsna.util.DicomViewer;
import org.rsna.util.EditChange;
import org.rsna.util.FileEvent;
import org.rsna.util.FileListener;
import org.rsna.util.FileUtil;
import org.rsna.util.HtmlJPanel;
import org.rsna.util.Key;
import org.rsna.util.PropertyEvent;
import org.rsna.util.PropertyListener;
import org.rsna.util.TransferEvent;
import org.rsna.util.TransferListener;

import org.apache.log4j.PropertyConfigurator;

/**
 * The FieldCenter program provides a DICOM Storage SCP that
 * receives images from DICOM SCUs (e.g., modalities, PACS, etc.),
 * anonymizes them, and sends them to a principal investigator
 * MIRC site using the HTTP or HTTPS protocol. It also provides
 * an image editor capable of changing the contents of DICOM
 * text elements. The anonymizer includes a re-identification
 * mechanism with an encrypted database.
 */
public class FieldCenter extends JFrame implements TransferListener, FileListener {

    private StatusPanel				statusPanel;
    private ParamsPanel				paramsPanel;
    private ControlPanel			controlPanel;
    private	QueuesPanel				queuesPanel;
    private LogPanel				logPanel;
    private AnonymizerPanel 		anonymizerPanel;
    private DicomViewer 			quarantinePanel;
    private ManualSelectionPanel	manualSelectionPanel;
    private ManualRemapperPanel 	manualRemapperPanel;
    private HtmlJPanel 				helpPanel;
    private ApplicationProperties	fieldCenterProperties;
	private ObjectProcessor			objectProcessor = null;
	private ExportService			exportService = null;

    private static final String	windowTitle 		= "MIRC Field Center Transfer Service - version 33";

    public static final String idtableFilename 		= "idtable.properties";
    public static final String fieldcenterFilename 	= "fieldcenter.properties";
    public static final String log4jFilename 		= "log4j.properties";
    public static final String dicomAnonymizerFilename	= "dicom-anonymizer.properties";
    public static final String lookupTableFilename		= "lookup-table.properties";
    public static final String xmlAnonymizerFilename	= "xml-anonymizer.script";

    public static final String dicomstoreFilename	= "dicom-store";
    public static final String dicomimportFilename	= "dicom-import";
    public static final String httpstoreFilename	= "http-store";
    public static final String httpimportFilename	= "http-import";
    public static final String objectlogFilename	= "object-log";
    public static final String exportFilename 		= "export";
    public static final String anonQuarFilename 	= "quarantines/anonymizer";
    public static final String exportQuarFilename 	= "quarantines/export";

    public static final String logsFilename 		= "logs/FieldCenter";
    public static final String helpFilename 		= "help.html";

    public static Color background = Color.getHSBColor(0.5833f, 0.17f, 0.95f);

    public static void main(String args[]) {
        new FieldCenter();
    }

	/**
	 * Class constructor; creates the program main class, displays
	 * the GUI, and checks the database, obtaining the key from
	 * the user if necessary.
	 */
    public FieldCenter() {
		File logs = new File(logsFilename).getParentFile();
		logs.mkdirs();
		initializeLog4J(log4jFilename);
		fieldCenterProperties = new ApplicationProperties(fieldcenterFilename);
		setIPAddress();
		setTitle(windowTitle);
		addWindowListener(new WindowCloser(this));
		statusPanel = new StatusPanel();
		paramsPanel = new ParamsPanel(fieldCenterProperties);
		paramsPanel.displayParams(fieldCenterProperties);
		controlPanel = new ControlPanel(fieldCenterProperties);
		logPanel = new LogPanel();
		queuesPanel = new QueuesPanel();
		String saveEnabled = fieldCenterProperties.getProperty("save-enabled");
		boolean enabled = (saveEnabled != null) && saveEnabled.equals("true");
		anonymizerPanel = new AnonymizerPanel(dicomAnonymizerFilename,fieldCenterProperties);
		quarantinePanel = new DicomViewer(fieldCenterProperties,dicomAnonymizerFilename);
		quarantinePanel.addFileListener(this);
		manualSelectionPanel = new ManualSelectionPanel(fieldCenterProperties);
		manualRemapperPanel = new ManualRemapperPanel(fieldCenterProperties);
		helpPanel = new HtmlJPanel(new File(helpFilename));
		SpecialPanel specialPanel =
			new SpecialPanel(
					anonymizerPanel,
					quarantinePanel,
					manualSelectionPanel,
					manualRemapperPanel);
		MainPanel mainPanel =
			new MainPanel (
					statusPanel,
					paramsPanel,
					controlPanel,
					logPanel,
					queuesPanel,
					specialPanel,
					helpPanel);
		getContentPane().add(mainPanel, BorderLayout.CENTER);
		pack();
		centerFrame();
		setVisible(true);
		boolean prompt = IdTable.requiresPrompt(idtableFilename);
		String key = Key.getEncryptionKey(this,prompt);
		if (!IdTable.initialize(idtableFilename,key)) {
			JOptionPane.showMessageDialog(
				this,
				  "The key did not match the IdTable.\n"
				+ "Restart the program to try again.\n");
			System.exit(0);
		}
		//Disable storeLater because we catch it in the WindowCloser.
		IdTable.setStoreLaterEnable(false);
		//Check the version of the IdTable
		int idVersion = IdTable.getVersion();
		if (idVersion != 1) {
			String siteid = JOptionPane.showInputDialog(
				this,
				"The remapping table (version "+idVersion+") is not\n"+
				"the latest version. Enter the Site ID parameter below\n"+
				"to upgrade the table, or click Cancel to exit.");
			if (siteid != null) IdTable.convert(siteid);
			else System.exit(0);
		}
		//Create the subordinate threads and start them.
		objectProcessor = new ObjectProcessor(fieldCenterProperties);
		objectProcessor.addTransferListener(this);
		exportService = new ExportService(fieldCenterProperties);
		exportService.addTransferListener(this);
		objectProcessor.start();
		exportService.start();
    }

    /**
     * The TransferListener implementation; listens for messages
     * from the Transfer Service, displays them in the footer panel,
     * and updates the queues display.
     * @param event the event indicating which button was clicked.
     */
    public void attention(TransferEvent event) {
		String message = event.message;
		if (message != null) statusPanel.status.setText(message);
		logPanel.displayLog();
		queuesPanel.displayQueues();
	}

    /**
     * The FileListener implementation; listens for FileEvents
     * from the DicomViewer and updates the queues display.
     * @param event the event.
     */
    public void fileEventOccurred(FileEvent event) {
		queuesPanel.displayQueues();
	}

    //Class to capture a window close event and give the
    //user a chance to change his mind.
    class WindowCloser extends WindowAdapter {
		private Component parent;
		public WindowCloser(JFrame parent) {
			this.parent = parent;
			parent.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		}
		public void windowClosing(WindowEvent evt) {
			int response = JOptionPane.showConfirmDialog(
							parent,
							"Are you sure you want to exit the program?",
							"Are you sure?",
							JOptionPane.YES_NO_OPTION);
			if (response == JOptionPane.YES_OPTION) {
				fieldCenterProperties.store(); //save the properties in case they changed
				IdTable.storeNow(false); //only store if the table is dirty
				System.exit(0);
			}
		}
    }

	//Configure Log4J from the properties file shipped with the program.
	private void initializeLog4J(String filename) {
		File file = new File(filename);
		if (file.exists()) PropertyConfigurator.configure(filename);
	}

	//Set the scp-ipaddress property in the application properties
	private void setIPAddress() {
		String detect = fieldCenterProperties.getProperty("ipaddress-autodetect");
		if (detect == null) detect = "";
		if (!detect.trim().equals("false")) {
			String ipAddress = getIPAddress();
			fieldCenterProperties.setProperty("scp-ipaddress",ipAddress);
		}
	}

	//Get the computer's IP address from the OS.
	private String getIPAddress() {
		InetAddress localHost;
		try { localHost = InetAddress.getLocalHost(); }
		catch (Exception e) { return "unknown"; }
		return localHost.getHostAddress();
	}

    //Position and size the application's JFrame on startup.
    private void centerFrame() {
		Toolkit t = getToolkit();
		Dimension scr = t.getScreenSize ();
		setSize(scr.width*3/5, scr.height/2);
		setLocation (
			new Point (
				(scr.width-getSize().width)/2,
				(scr.height-getSize().height)/2));
    }

	//GUI panels

	class MainPanel extends JPanel implements ChangeListener {
		JTabbedPane tabbedPane;
		LogPanel log;
		QueuesPanel queues;
		JScrollPane queuesScrollPane;
		public MainPanel(JPanel status,
						 JPanel params,
						 ControlPanel controls,
						 LogPanel log,
						 QueuesPanel queues,
						 JPanel special,
						 JPanel help) {
			super();
			this.setLayout(new BorderLayout());
			this.add(status,BorderLayout.SOUTH);
			this.log = log;
			this.queues = queues;
			tabbedPane = new JTabbedPane(JTabbedPane.TOP);
			this.add(tabbedPane,BorderLayout.CENTER);
			JScrollPane paramScrollPane = new JScrollPane();
			paramScrollPane.setViewportView(params);
			tabbedPane.addTab("Parameters",paramScrollPane);
			tabbedPane.addTab("Control Panel",controls);
			tabbedPane.addTab("Event Log",log);
			queuesScrollPane = new JScrollPane();
			queuesScrollPane.setViewportView(queues);
			tabbedPane.addTab("Queue Status",queuesScrollPane);
			tabbedPane.addTab("Special",special);
			tabbedPane.addTab("Help",help);
			this.add(tabbedPane,BorderLayout.CENTER);
			tabbedPane.addChangeListener(this);
		}
		public void stateChanged(ChangeEvent event) {
			Component c = tabbedPane.getSelectedComponent();
			if (c.equals(log)) {
				log.setDisplayed(true);
				queues.setDisplayed(false);
				log.displayLog();
			}
			else if (c.equals(queuesScrollPane)) {
				log.setDisplayed(false);
				queues.setDisplayed(true);
				queues.displayQueues();
			}
			else {
				log.setDisplayed(false);
				queues.setDisplayed(false);
			}
		}
	}

	class SpecialPanel extends JPanel {
		JTabbedPane tabbedPane;
		public SpecialPanel(JPanel anon,
						 JPanel quarantine,
						 JPanel manualSelector,
						 JPanel manualRemapper) {
			super();
			this.setLayout(new BorderLayout());
			tabbedPane = new JTabbedPane(JTabbedPane.TOP);
			this.add(tabbedPane,BorderLayout.CENTER);
			tabbedPane.addTab("Anonymizer",anon);
			tabbedPane.addTab("Quarantine",quarantine);
			tabbedPane.addTab("Selector",manualSelector);
			tabbedPane.addTab("Remapper",manualRemapper);
			this.add(tabbedPane,BorderLayout.CENTER);
		}
	}

	class QueuesPanel extends HtmlJPanel {
		boolean displayed = false;
		public QueuesPanel() {
			super();
			editor.setBackground(Color.lightGray);
			displayQueues();
		}
		public void displayQueues() {
			if (displayed) {
				int storeFiles = countFiles(dicomstoreFilename);
				int importFiles = countFiles(dicomimportFilename);
				int exportFiles = countFiles(exportFilename);
				int anonQuarFiles = countFiles(anonQuarFilename);
				int exportQuarFiles = countFiles(exportQuarFilename);
				String text = "<body bgcolor=white><center><h1>Queue Status</h1>"
					+ "<table border=1 width=\"50%\">"
					+ "<tr><td width=\"50%\">DICOM Store</td><td width=\"50%\"><center>" + storeFiles + "</center></td></tr>"
					+ "<tr><td>Awaiting processing</td><td><center>" + importFiles + "</center></td></tr>"
					+ "<tr><td>Awaiting export</td><td><center>" + exportFiles + "</center></td></tr>"
					+ "<tr><td>Anonymizer Quarantine</td><td><center>" + anonQuarFiles + "</center></td></tr>"
					+ "<tr><td>Export Quarantine</td><td><center>" + exportQuarFiles + "</center></td></tr>"
					+ "</table></center></body>";
				setText(text);
			}
		}
		public void setDisplayed(boolean displayed) {
			this.displayed = displayed;
		}
		private int countFiles(String name) {
			File file = new File(name);
			if (!file.exists()) return 0;
			File[] files = file.listFiles();
			return files.length;
		}
	}

	class LogPanel extends JPanel implements ActionListener {
		public HtmlJPanel text;
		boolean displayed = true;
		public LogPanel() {
			super();
			this.setLayout(new BorderLayout());
			text = new HtmlJPanel();
			this.add(text,BorderLayout.CENTER);
			JPanel footer = new JPanel();
			footer.setBackground(background);
			footer.setLayout(new FlowLayout());
			JButton clear = new JButton("Clear Log");
			footer.add(clear);
			this.add(footer,BorderLayout.SOUTH);
			clear.addActionListener(this);
		}
		public void actionPerformed(ActionEvent event) {
			Log.clearLog();
			text.setText("");
		}
		public void setDisplayed(boolean displayed) {
			this.displayed = displayed;
		}
		public void displayLog() {
			if (displayed) {
				text.setText(Log.getLog());
				text.scrollToBottom();
			}
		}
	}

	//This panel listens for property changes that occur in the ControlPanel
	//and updates itself accordingly.
	class ParamsPanel extends HtmlJPanel implements PropertyListener {
		ApplicationProperties props;
		public ParamsPanel(ApplicationProperties props) {
			super();
			this.props = props;
			props.addPropertyListener(this);
			editor.setBackground(Color.lightGray);
		}
		public void propertyChanged(PropertyEvent event) {
			displayParams(props);
		}
		public void displayParams(ApplicationProperties props) {
			this.props = props;
			String ip = props.getProperty("scp-ipaddress");
			String port = props.getProperty("scp-port");
			String aetitle = props.getProperty("scp-aetitle");
			String destination = props.getProperty("destination");
			String trial = props.getProperty("trial");
			String anonEnb = props.getProperty("anonymizer-enabled");
			String expEnb = props.getProperty("export-enabled");
			String text = "<body bgcolor=white><center><h1>" + trial + "</h1>"
					+ "<h2>Local DICOM Store</h2>"
					+ "<table border=1 width=\"50%\">"
					+ "<tr><td>IP address</td><td><code><font size=5>" + ip + "</font></code></td></tr>"
					+ "<tr><td>Port</td><td><code><font size=5>" + port + "</font></code></td></tr>"
					+ "<tr><td>AE Title</td><td><code><font size=5>" + aetitle + "</font></code></td></tr>"
					+ "</table>"
					+ "<h2>Destination</h2>"
					+ "<code><font size=5>" + destination + "</font></code>"
					+ "<h2>Anonymizer: "
					+ "<code><font size=5>"
					+ (((anonEnb != null) && anonEnb.equals("false")) ?
								"<font color=red><b>disabled</b></font>" : "enabled")
					+ "</font></code></h2><h2>Export: "
					+ "<code><font size=5>"
					+ (((expEnb != null) && expEnb.equals("false")) ?
								"<font color=red><b>disabled</b></font>" : "enabled")
					+ "</font></code></h2></center></body>";
			setText(text);
		}
	}

	class StatusPanel extends JPanel {
		public JLabel status;
		public StatusPanel() {
			super();
			this.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
			this.setLayout(new FlowLayout(FlowLayout.LEFT));
			this.setBackground(background);
			status = new JLabel("Ready");
			Font font = new Font(status.getFont().getFontName(),Font.BOLD,14);
			status.setFont(font);
			this.add(status);
		}
		public void setText(String text) {
			status.setText(text);
		}
	}

}
