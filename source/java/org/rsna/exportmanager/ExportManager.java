/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.exportmanager;

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
import org.rsna.mircsite.util.DicomObject;
import org.rsna.util.AnonymizerPanel;
import org.rsna.util.ApplicationProperties;
import org.rsna.util.DicomTextPanel;
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
 * The ExportManager program provides a DICOM Storage SCP that
 * receives objects from IHE TFCTE Export Selectors, anonymizes
 * them, and sends them to an IHE TFCTE Export Receiver.
 * This program supports the DICOM, HTTP, and HTTPS protocols
 * for export, although TFCTE is restricted to DICOM.
 */
public class ExportManager extends JFrame
							implements TransferListener, FileListener, PropertyListener {

    private StatusPanel				statusPanel;
    private ParamsPanel				paramsPanel;
    private ControlPanel			controlPanel;
    private	QueuesPanel				queuesPanel;
    private LogPanel				logPanel;
    private AnonymizerPanel 		anonymizerPanel;
    private DicomTextPanel 			quarantinePanel;
    private HtmlJPanel 				helpPanel;
    private ApplicationProperties	programProperties;
    private StorageSCP				storageSCP;
    private Store					store;
	private ObjectProcessor			objectProcessor = null;
	private ExportService			exportService = null;
	private HttpServer				httpServer = null;

	File dicomStoreDir;
	File dicomImportDir;
	File exportDir;
	File anQuarDir;
	File expQuarDir;
	File dicomAnonFile;
	File lookupTableFile;

    private static final String	windowTitle = "MIRC IHE TFCTE Export Manager";
    private static final int version = 6;

    public static final String idtableFN 	= "idtable.properties";
    public static final String propertiesFN = "exportmanager.properties";
    public static final String log4jFN 		= "log4j.properties";
    public static final String dicomAnonymizerFN = "dicom-anonymizer.properties";
    public static final String lookupTableFN = "lookup-table.properties";

    public static final String scpFN		= "scp";
    public static final String storeFN		= "store";
    public static final String exportFN 	= "export";
    public static final String anQuarFN 	= "anonymizer-quarantine";
    public static final String expQuarFN 	= "export-quarantine";

    public static final String logsFN 		= "logs";
    public static final String helpFN 		= "help.html";

    public static Color background = Color.getHSBColor(0.5833f, 0.17f, 0.95f);

    public static void main(String args[]) {
        new ExportManager();
    }

	/**
	 * Class constructor; creates the program main class, displays
	 * the GUI, and checks the database, obtaining the key from
	 * the user if necessary.
	 */
    public ExportManager() {
		programProperties = new ApplicationProperties(propertiesFN);
		programProperties.addPropertyListener(this);
		setLogDepth();

		File logs = new File(logsFN);
		logs.mkdirs();
		initializeLog4J(log4jFN);
		setIPAddress();
		setWindowTitle();
		addWindowListener(new WindowCloser(this));

		dicomStoreDir = new File(scpFN);
		dicomImportDir = new File(storeFN);
		exportDir = new File(exportFN);
		anQuarDir = new File(anQuarFN);
		expQuarDir = new File(expQuarFN);
		dicomAnonFile = new File(dicomAnonymizerFN);
		lookupTableFile = new File(lookupTableFN);

		//Set up the IdTable for the anonymizer.
		//Get the key, initialize the table, and
		//set the table not to store updates as they occur.
		boolean prompt = IdTable.requiresPrompt(idtableFN);
		String key = Key.getEncryptionKey(this,prompt);
		if (!IdTable.initialize(idtableFN,key)) {
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

		//Create the main processing elements.
		store =
			new Store(
					programProperties,
					dicomImportDir);
		store.addTransferListener(this);
		storageSCP =
			new StorageSCP(
					programProperties,
					dicomStoreDir);
		storageSCP.addDicomListener(store);
		objectProcessor =
			new ObjectProcessor(
					programProperties,
					store,
					dicomAnonFile,
					lookupTableFile,
					exportDir,
					anQuarDir);
		objectProcessor.addTransferListener(this);
		exportService =
			new ExportService(
					programProperties,
					exportDir,
					expQuarDir);
		exportService.addTransferListener(this);

		//Create the UI.
		statusPanel = new StatusPanel();
		paramsPanel = new ParamsPanel(programProperties);
		controlPanel = new ControlPanel(programProperties);
		logPanel = new LogPanel();
		queuesPanel = new QueuesPanel();
		anonymizerPanel = new AnonymizerPanel(programProperties,dicomAnonymizerFN);
		quarantinePanel = new DicomTextPanel();
		helpPanel = new HtmlJPanel(new File(helpFN));
		MainPanel mainPanel =
			new MainPanel (
					statusPanel,
					paramsPanel,
					controlPanel,
					logPanel,
					queuesPanel,
					anonymizerPanel,
					quarantinePanel,
					helpPanel);

		paramsPanel.displayParams(programProperties);

		getContentPane().add(mainPanel, BorderLayout.CENTER);
		pack();
		centerFrame();
		setVisible(true);

		//Start the subordinate threads.
		storageSCP.restartSCP();
		objectProcessor.start();
		exportService.start();
		setHttpServer();
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

	/**
	 * The PropertyListener implementation; listens for a change in
	 * the application properties object.
	 * @param event the event indicating that the properties have changed.
	 */
	public void propertyChanged(PropertyEvent event) {
		setWindowTitle();
		setLogDepth();
		setHttpServer();
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
				IdTable.storeNow(false); //only store if the table is dirty
				System.exit(0);
			}
		}
    }

	//Set the Log depth
	private void setLogDepth() {
		String logDepthName = "log-depth";
		String logDepth = programProperties.getProperty(logDepthName);
		int depth = Log.getDepth();
		try { depth = Integer.parseInt(logDepth); }
		catch (Exception ex) { programProperties.setProperty(logDepthName,""+depth); }
		Log.setDepth(depth);
	}

    //Set the window title
    private void setWindowTitle() {
		String title = programProperties.getProperty("title");
		if ((title == null) || title.trim().equals(""))
			title = windowTitle;
		setTitle(title + " - version " + version);
	}

	//Set up the HTTP Server
	private void setHttpServer() {
		String enbProp = programProperties.getProperty("http-enabled");
		boolean enb = (enbProp != null) && enbProp.equals("true");
		String portProp = programProperties.getProperty("http-port");
		int port = -1;
		try { port = Integer.parseInt(portProp); }
		catch (Exception ignore) { }
		if (enb && (port != -1)) {
			if (httpServer != null) {
				int prevPort = httpServer.getPort();
				if (port == prevPort) return;
				httpServer.stopServer();
				Log.message("HTTP Server stopped on port "+prevPort);
			}
			try {
				httpServer = new HttpServer(port,logPanel,queuesPanel);
				httpServer.start();
				Log.message("HTTP Server started on port "+port);
			}
			catch (Exception ex) {
				Log.message("Unable to create the HTTP Server on port "+port);
			}
		}
		else {
			if (httpServer != null) {
				int prevPort = httpServer.getPort();
				httpServer.stopServer();
				Log.message("HTTP Server stopped on port "+prevPort);
				httpServer = null;
			}
		}

	}

	//Configure Log4J from the properties file shipped with the program.
	private void initializeLog4J(String FN) {
		File file = new File(FN);
		if (file.exists()) PropertyConfigurator.configure(FN);
	}

	//Set the scp-ipaddress property in the application properties
	private void setIPAddress() {
		String detect = programProperties.getProperty("ipaddress-autodetect");
		if (detect == null) detect = "";
		if (!detect.trim().equals("false")) {
			String ipAddress = getIPAddress();
			programProperties.setProperty("scp-ipaddress",ipAddress);
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
						 JPanel anon,
						 JPanel quarantine,
						 JPanel help) {
			super();
			this.setLayout(new BorderLayout());
			this.add(status,BorderLayout.SOUTH);
			this.log = log;
			this.queues = queues;
			tabbedPane = new JTabbedPane(JTabbedPane.TOP);
			JScrollPane paramScrollPane = new JScrollPane();
			paramScrollPane.setViewportView(params);
			tabbedPane.addTab("Parameters",paramScrollPane);
			tabbedPane.addTab("Control Panel",controls);
			tabbedPane.addTab("Event Log",log);
			queuesScrollPane = new JScrollPane();
			queuesScrollPane.setViewportView(queues);
			tabbedPane.addTab("Queue Status",queuesScrollPane);
			tabbedPane.addTab("Anonymizer",anon);
			tabbedPane.addTab("Quarantine",quarantine);
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

	class QueuesPanel extends JPanel implements ActionListener, HtmlSource {
		HtmlJPanel textPanel;
		JButton clearButton;
		boolean displayed = false;
		public QueuesPanel() {
			super();
			this.setLayout(new BorderLayout());
			textPanel = new HtmlJPanel();
			textPanel.editor.setBackground(Color.lightGray);
			JScrollPane jsp = new JScrollPane();
			jsp.setViewportView(textPanel);
			JPanel footer = new JPanel();
			footer.setBackground(background);
			clearButton = new JButton("Clear All Queues");
			clearButton.addActionListener(this);
			footer.add(clearButton);
			this.add(jsp,BorderLayout.CENTER);
			this.add(footer,BorderLayout.SOUTH);
			displayQueues();
		}
		public void displayQueues() {
			if (displayed) {
				String text = "<body bgcolor=white><center><h1>Queue Status</h1>"
							+ getHTML(70)
							+ "</center></body>";
				textPanel.setText(text);
			}
		}
		public String getHTML() {
			return getHTML(-1);
		}
		public String getHTML(int width) {
			int scpCount = storageSCP.getFileCount();
			int manifestCount = store.getManifestCount();
			int instanceCount = store.getInstanceCount();
			int queuedManifestCount = store.getQueuedManifestCount();
			int exportCount = exportService.getFileCount();
			int anQuarCount = countFiles(anQuarDir);
			int expQuarCount = countFiles(expQuarDir);
			StringBuffer sb = new StringBuffer();
			sb.append("<table");
			if (width > 0) sb.append(" border=\"1\" width=\"" + width + "%\"");
			sb.append(">");
			sb.append("<tr><td width=70%>Unparsed Objects</td><td width=30%><center>"
							+ scpCount + "</center></td></tr>");
			sb.append("<tr><td>Stored Manifests</td><td><center>"
							+ manifestCount + "</center></td></tr>");
			sb.append("<tr><td>Stored Instances</td><td><center>"
							+ instanceCount + "</center></td></tr>");
			sb.append("<tr><td>Manifests Queued for Processing</td><td><center>"
							+ queuedManifestCount + "</center></td></tr>");
			sb.append("<tr><td>Objects Queued for Export</td><td><center>"
							+ exportCount + "</center></td></tr>");
			sb.append("<tr><td>Anonymizer Quarantine</td><td><center>"
							+ anQuarCount + "</center></td></tr>");
			sb.append("<tr><td>Export Service Quarantine</td><td><center>"
							+ expQuarCount + "</center></td></tr>");
			sb.append("</table>");
			return sb.toString();
			}
		public void setDisplayed(boolean displayed) {
			this.displayed = displayed;
		}
		private int countFiles(File dir) {
			if (!dir.exists()) return 0;
			File[] files = dir.listFiles();
			return files.length;
		}
		public void actionPerformed(ActionEvent event) {
			int choice =
				JOptionPane.showConfirmDialog(
						this,
						"This will delete everything in\n" +
						"the store, all the queues, and\n" +
						"the quarantines.\n\n" +
						"Are you sure?","Really?",
						JOptionPane.YES_NO_OPTION);
			if (choice == JOptionPane.YES_OPTION) {
				storageSCP.deleteAllFiles();
				store.deleteAllFiles();
				objectProcessor.deleteAllFiles();
				exportService.deleteAllFiles();
				displayQueues();
			}
		}
	}

	class LogPanel extends JPanel implements ActionListener, HtmlSource {
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
		public String getHTML() {
			return Log.getLog();
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
			String title = props.getProperty("title");
			String anonEnb = props.getProperty("anonymizer-enabled");
			String expEnb = props.getProperty("export-enabled");
			String text = "<body bgcolor=white><center><h1>" + title + "</h1>"
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
