/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import org.apache.log4j.*;
import org.rsna.util.ApplicationProperties;
import org.rsna.util.DicomImage;
import org.rsna.util.EditChange;
import org.rsna.util.FileUtil;
import org.rsna.util.PropertyEvent;
import org.rsna.util.PropertyListener;

/**
 * A JPanel that provides a DICOM viewer and element editor.
 * It also provides a configurable array of buttons that can allow
 * saving of images, propagation of modifications into other
 * images with the same StudyInstanceUID, and moving an image or
 * all images of the same study into an export directory.
 */
public class DicomViewer extends JPanel
						 implements ActionListener,
						 			PropertyListener,
						 			FileListener {

	JFileChooser 	openChooser = null;
	JFileChooser 	saveAsChooser = null;
	int				jpegQuality = -1;
	DicomImage 		dicomImage;
	BufferedImage 	bufferedImage;
	LinkedList		changeList;

    JSplitPane		mainSplitPane;
    JSplitPane		leftSplitPane;
    ButtonPanel		buttonPanel;
    TextPanel		colorPanel;
    DicomElementsTextPanel textPanel;
    ImagePanel 		imagePanel;

    ApplicationProperties props;
    String anonymizerFilename;
    File currentSelection = null;
    File currentFile = null;
	EventListenerList listenerList;
	Color background;

	static final int imagePanelSize = 300;
	static final Logger logger = Logger.getLogger(DicomViewer.class);

	/**
	 * Class constructor; creates a DicomViewer JPanel.
	 * @param props the application properties object or null if exporting is not allowed.
	 * @param anonymizerFilename the name of the properties file containing the anonymizer
	 * scripts or null if there is no anonymizer.
	 */
    public DicomViewer(ApplicationProperties props, String anonymizerFilename) {
		this(props,anonymizerFilename,null);
	}

	/**
	 * Class constructor; creates a DicomViewer JPanel.
	 * @param props the application properties object or null if exporting is not allowed.
	 * @param anonymizerFilename the name of the properties file containing the anonymizer
	 * scripts or null if there is no anonymizer.
	 * @param background the background color or null if the default is to be used.
	 */
    public DicomViewer(
			ApplicationProperties props,
			String anonymizerFilename,
			Color background) {
		super();
		this.setLayout(new BorderLayout());
		this.props = props;
		this.anonymizerFilename = anonymizerFilename;
		if (background == null)
			this.background = Color.getHSBColor(0.58f, 0.17f, 0.95f);
		else
			this.background = background;

		//Construct the EventListenerList
		listenerList = new EventListenerList();

		//If the props object was supplied, allow the
		//send and sendAll buttons to be shown.
		buttonPanel = new ButtonPanel( (props != null) );
		colorPanel = new TextPanel();
		textPanel = new DicomElementsTextPanel(this);
		imagePanel = new ImagePanel();

		setEnables();
		buttonPanel.addActionListeners(this);
		this.add(buttonPanel,BorderLayout.NORTH);
		this.setBackground(background);

		mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		mainSplitPane.setResizeWeight(0.0D);
		mainSplitPane.setDividerLocation(imagePanelSize);

		JScrollPane rightScrollPane = new JScrollPane();
		rightScrollPane.setViewportView(textPanel);
		rightScrollPane.getVerticalScrollBar().setUnitIncrement(25);
		rightScrollPane.getHorizontalScrollBar().setUnitIncrement(15);

		mainSplitPane.setRightComponent(rightScrollPane);

		leftSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		leftSplitPane.setResizeWeight(0.0D);
		leftSplitPane.setDividerLocation(imagePanelSize);

		JScrollPane upperScrollPane = new JScrollPane();
		upperScrollPane.setViewportView(imagePanel);
		leftSplitPane.setTopComponent(upperScrollPane);
		JScrollPane lowerScrollPane = new JScrollPane();
		lowerScrollPane.getVerticalScrollBar().setUnitIncrement(25);
		lowerScrollPane.setViewportView(colorPanel);

		leftSplitPane.setBottomComponent(lowerScrollPane);
		mainSplitPane.setLeftComponent(leftSplitPane);
		mainSplitPane.setVisible(false);
		this.add(mainSplitPane,BorderLayout.CENTER);

		//If the props object was supplied, listen
		//for changes in the export-enabled flag.
		if (props != null) props.addPropertyListener(this);
    }

	/**
	 * The FileListener implementation; tracks the current
	 * selection from any file selectors with which the object is
	 * registered. Note: this class does not register itself with
	 * a file selector; it is up to the parent class to do it.
	 * @param event the event containing the current file selection.
	 */
	public void fileEventOccurred(FileEvent event) {
		if (event.type == FileEvent.SELECT)
			currentSelection = event.after;
	}

    /**
     * The ActionListener implementation; listens for all the buttons on
     * the ButtonPanel.
     * @param event the event indicating which button was clicked.
     */
    public void actionPerformed(ActionEvent event) {
		if (event.getSource().equals(buttonPanel.open)) openImage();
		else if (event.getSource().equals(buttonPanel.save)) saveImage();
		else if (event.getSource().equals(buttonPanel.saveAll)) saveAll();
		else if (event.getSource().equals(buttonPanel.send)) sendImage();
		else if (event.getSource().equals(buttonPanel.sendAll)) sendAll();
		else if (event.getSource().equals(buttonPanel.close)) closeImage();
		else if (event.getSource().equals(buttonPanel.delete)) deleteImage();
		else if (event.getSource().equals(buttonPanel.deleteAll)) deleteAll();
		else if (event.getSource().equals(buttonPanel.saveAsJPEG)) saveAsJPEG();
	}

	/**
	 * Get the file that is currently open, or null if no file is currently displayed.
	 * @return the file that is currently open, or null.
	 */
	public File getCurrentFile () {
		return currentFile;
	}

	/**
	 * The PropertyListener implementation; listens for a change in
	 * the application properties object.
	 * @param event the event indicating that the properties have changed.
	 */
	public void propertyChanged (PropertyEvent event) {
		setEnables();
	}

	//Set the enables on all the buttons.
	private void setEnables () {
		buttonPanel.setEnables((dicomImage!=null),getExportEnabled(),getChangesAvailable());
	}

	//Determine whether exporting is enabled.
	private boolean getExportEnabled() {
		if (props == null) return false;
		String expEnb = props.getProperty("export-enabled");
		return !((expEnb != null) && expEnb.equals("false"));
	}

	//Determine whether any changes have been made to the
	//elements in the current image.
	private boolean getChangesAvailable() {
		return ((changeList != null) && (changeList.size() != 0));
	}

	//Open an image file. If necessary, provide a chooser
	//and receive the user's selection. This function is called from
	//the ActionListener interface.
	private void openImage() {
		if (dicomImage == null) openImage(currentSelection);
		else openImage(dicomImage.imageFile.getParentFile());
	}

	/**
	 * Open an image. If the supplied file is null, does not exist, or is a
	 * directory, provide a chooser to allow the user to select the file;
	 * otherwise, open the supplied file.
	 * @param file the file or directory to open.
	 */
	public void openImage(File file) {
		boolean missing = (file == null) || !file.exists();
		boolean directory = (file != null) && file.isDirectory();
		if (missing || directory) {
			if ( missing ) {
				if (openChooser == null) {
					File here = new File(System.getProperty("user.dir"));
					//open the first time in the quarantine, if it exists
					File quarantine = new File(here,"quarantine");
					if (quarantine.exists()) here = quarantine;
					openChooser = new JFileChooser(here);
				}
			}
			if ( directory ) {
				if (openChooser == null)
					openChooser = new JFileChooser(file);
				else
					openChooser.setCurrentDirectory(file);
			}
			if (openChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				openImageFile(openChooser.getSelectedFile());
			}
		}
		else openImageFile(file);
	}

	//Open an image, given the file.
	private void openImageFile(File file) {
		try {
			dicomImage = new DicomImage(file);
			currentFile = file;
			dicomImage.setTestedElements(getTestedElements());

			//Load the image, but continue on if we don't succeed
			try {
				bufferedImage = dicomImage.getBufferedImage(imagePanelSize);
				imagePanel.setImage(bufferedImage);
				colorPanel.editor.setText(dicomImage.getColorString());
				colorPanel.editor.setCaretPosition(0);
			}
			catch (Exception e) {
				logger.warn("Exception while getting the BufferedImage",e);
				JOptionPane.showMessageDialog(this,"Exception:\n\n"+e.getMessage());
			}

			textPanel.editor.setText(dicomImage.getElementList());
			textPanel.editor.setCaretPosition(0);

			changeList = new LinkedList();
			mainSplitPane.setVisible(true);
			this.validate();
			setEnables();
		}
		catch (Exception e) {
			logger.warn("Exception while getting the BufferedImage",e);
			JOptionPane.showMessageDialog(this,"Exception:\n\n"+e.getMessage());
		}
	}

	//Save the currently open image, including any
	//editing changes made since it was opened.
	private void saveImage() {
		dicomImage.saveDicomImage();
	}

	//Save all the images belonging to the same StudyInstanceUID
	//as the currently open image (and which are in the same directory as the
	//currently open image), applying the same editing changes to those images
	//that were done on the currently open image.
	private void saveAll() {
		File file = dicomImage.imageFile;
		File dir = file.getParentFile();
		File[] files = dir.listFiles();
		for (int i=0; i<files.length; i++) {
			try {
				DicomImage image = new DicomImage(files[i]);
				image.applyChanges(changeList);
				image.saveDicomImage();
			}
			catch (Exception ignore) { }
		}
	}

	//See if a file is located in a quarantine directory
	//or a directory from which sending a file is allowed.
	private boolean isSendable(File file) {
		File dir = new File(file.getAbsolutePath());
		dir = file.getParentFile();
		File parentDir = dir.getParentFile();
		boolean sendable =
					parentDir.getName().equals("quarantines") ||
					dir.getName().equals("quarantine") ||
					dir.getName().equals("store") ||
					dir.getName().equals("dicom-store");
		return sendable;
	}

	//Get the appropriate directory for sending a file.
	//If sending is allowed, choose the output directory
	//based on whether the file's directory starts
	//with "export".
	private File getSendDirectory(File file) {
		File dir = new File(file.getAbsolutePath());
		dir = file.getParentFile();
		if (dir.getName().equals("export"))
			return new File("export");
		else
			return new File("dicom-import");
	}

	//Export the currently open image.
	private void sendImage() {
		File file = dicomImage.imageFile;
		if (isSendable(file)) {
			File sendDir = getSendDirectory(file);
			File sendFile = new File(sendDir,file.getName());
			file.renameTo(sendFile);
			sendMoveFileEvent(file,sendFile);
		}
	}

	//Export all the images belonging to the same StudyInstanceUID
	//as the currently open image (and which are in the same directory as the
	//currently open image).
	private void sendAll() {
		String siUID = dicomImage.siUID;
		File file = dicomImage.imageFile;
		File dir = file.getParentFile();
		if (isSendable(file)) {
			File sendDir = getSendDirectory(file);
			sendDir.mkdirs();
			File[] files = dir.listFiles();
			for (int i=0; i<files.length; i++) {
				try {
					DicomImage image = new DicomImage(files[i]);
					if (image.siUID.equals(siUID)) {
						File sendFile = new File(sendDir,image.imageFile.getName());
						image.imageFile.renameTo(sendFile);
						sendMoveFileEvent(image.imageFile,sendFile);
					}
				}
				catch (Exception ignore) { }
			}
		}
	}

	//Close the currently open image.
	private void closeImage() {
		dicomImage = null;
		setEnables();
		mainSplitPane.setVisible(false);
	}

	//Delete the currently open image.
	private void deleteImage() {
		int option = JOptionPane.showConfirmDialog(this,
						"Are you sure you want to\ndelete the current image?",
						"Are you sure?",
						JOptionPane.YES_NO_OPTION,
						JOptionPane.INFORMATION_MESSAGE);
		if (option == JOptionPane.YES_OPTION) {
			File file = dicomImage.imageFile;
			file.delete();
			dicomImage = null;
			setEnables();
			mainSplitPane.setVisible(false);
			sendDeleteFileEvent(file);
		}
	}

	//Delete all the images belonging to the same StudyInstanceUID
	//as the currently open image (and which are in the same directory as the
	//currently open image).
	private void deleteAll() {
		int option = JOptionPane.showConfirmDialog(this,
						"Are you sure you want to\ndelete all images from\nthe current study?",
						"Are you sure?",
						JOptionPane.YES_NO_OPTION,
						JOptionPane.INFORMATION_MESSAGE);
		if (option == JOptionPane.YES_OPTION) {
			String siUID = dicomImage.siUID;
			File file = dicomImage.imageFile;
			File dir = file.getParentFile();
			if (dir.getName().equals("quarantine")) {
				File[] files = dir.listFiles();
				for (int i=0; i<files.length; i++) {
					try {
						DicomImage image = new DicomImage(files[i]);
						if (image.siUID.equals(siUID)) {
							image.imageFile.delete();
							sendDeleteFileEvent(image.imageFile);
						}
					}
					catch (Exception ignore) { }
				}
			}
		}
	}

	//Create a JPEG image from the currently open DICOM image.
	private void saveAsJPEG() {
		String inputValue = JOptionPane.showInputDialog(
			"Specify the maximum width for the saved image:",
			Integer.toString(dicomImage.imageWidth));
		if (inputValue == null) return;
		String qualityValue = JOptionPane.showInputDialog(
			"Specify the JPEG quality for the saved image.\n"+
			"Enter an integer from 1 to 100, or -1 for the default.",
			Integer.toString(jpegQuality));
		if (qualityValue == null) return;
		try {
			int width = Integer.parseInt(inputValue);
			jpegQuality = Integer.parseInt(qualityValue);
			String name = dicomImage.imageFile.getName();
			if (name.toLowerCase().endsWith(".dcm")) name = name.substring(0,name.length()-4);
			name += ".jpeg";
			File file;
			if (saveAsChooser == null) {
				saveAsChooser = new JFileChooser();
				file = new File(dicomImage.imageFile.getParentFile(),name);
			}
			else {
				File dir = saveAsChooser.getCurrentDirectory();
				file = new File(dir,name);
			}
			saveAsChooser.setSelectedFile(file);
			if (saveAsChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
				file = saveAsChooser.getSelectedFile();
				name = file.getName();
				if (!name.toLowerCase().endsWith(".jpeg")) name += ".jpeg";
				file = new File(file.getParentFile(),name);
				dicomImage.saveAsJPEG(width,file,jpegQuality);
				JOptionPane.showMessageDialog(this,"Success");
			}
		}
		catch (Exception e) {
			JOptionPane.showMessageDialog(this,"Error:\n"+e.getMessage());
		}
	}

	//Create an array of element tag strings (gggg,eeee) corresponding
	//to elements that could generate a quarantine call in the anonymizer. This is
	//used to determine which elements should be displayed in red to highlight the
	//elements that are possibly causing problems in the current image.
	private String[] getTestedElements() {
		//If no anonymizerFilename was supplied, just return an empty array.
		if (anonymizerFilename == null) return new String[0];

		//Okay, a list was supplied, get it.
		File anonFile = new File(anonymizerFilename);
		if (!anonFile.exists()) return new String[0];
		String anonString = FileUtil.getFileText(anonFile);

		//Search the list for all quarantine calls that appear in conditional
		//clauses of if statements.
		LinkedList teList = new LinkedList();
		try {
			BufferedReader br = new BufferedReader(new StringReader(anonString));
			String line;
			String elementName;
			int q;
			int k;
			int kk;
			while ((line = br.readLine()) != null) {
				while ((q=line.indexOf("@quarantine")) != -1) {
					k = line.substring(0,q).lastIndexOf("@if");
					if (k == -1) break;
					k = line.indexOf("(",k);
					if (k == -1) break;
					kk = line.indexOf(",",k);
					if (kk == -1) break;
					elementName = line.substring(k+1,kk).trim();
					if (elementName.indexOf(" ") == -1) teList.add(elementName);
					line = line.substring(q+10);
				}
			}
			br.close();
			String[] teStrings = new String[teList.size()];
			Iterator it = teList.iterator();
			for (int i=0; i<teStrings.length; i++) teStrings[i] = (String)it.next();
			return teStrings;
		}
		catch (Exception ignore) { }
		return new String[0];
	}

	//Miscellaneous GUI Panels

	class DicomElementsTextPanel extends TextPanel implements MouseListener {
		Component parent;
		public DicomElementsTextPanel(Component parent) {
			super();
			this.parent = parent;
			editor.addMouseListener(this);
		}
		public void mouseClicked(MouseEvent e) { }
		public void mouseEntered(MouseEvent e) { }
		public void mouseExited(MouseEvent e) { }
		public void mousePressed(MouseEvent e) { }

		public void mouseReleased(MouseEvent e) {
			int dot = editor.getCaretPosition();
			if (dot < 20) return;
			try {
				String text = editor.getText(dot-15,30);
				int tag = findTag(text);
				if (tag != -1) {
					EditChange change = dicomImage.edit(parent,tag);
					if (change != null) {
						editor.setText(dicomImage.getElementList());
						editor.setCaretPosition(dot);
						changeList.add(change);
						setEnables();
					}
				}
			}
			catch (Exception ignore) { }
		}

		private int findTag(String s) {
			try {
				int k;
				for (k=s.length()/2; k>0 && (s.charAt(k)!='('); k--) ;
				int kk;
				for (kk=s.length()/2; kk<s.length()-1 && (s.charAt(kk)!=')'); kk++) ;
				if ((s.charAt(k) == '(') && (s.charAt(kk) == ')')) {
					s = s.substring(k,kk+1);
					int group = Integer.parseInt(s.substring(1,5),16);
					int element = Integer.parseInt(s.substring(6,10),16);
					return (group << 16) | element;
				}
			} catch (Exception ignore) { }
			return -1;
		}
	}

	class TextPanel extends JPanel {
		public JEditorPane editor;
		public TextPanel() {
			editor = new JEditorPane("text/html","");
			editor.setEditable(false);
			this.setLayout(new BorderLayout());
			this.add(editor,BorderLayout.CENTER);
		}
	}

	class ImagePanel extends JPanel {
		BufferedImage bufferedImage;
		public ImagePanel() {
			super();
		}
		public void setImage(BufferedImage bufferedImage) {
			this.bufferedImage = bufferedImage;
			repaint();
		}
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			if (bufferedImage != null) {
				g.drawImage(bufferedImage,0,0,null);
			}
		}
	}

	class ButtonPanel extends JPanel {
		public JButton open;
		public JButton save;
		public JButton saveAll;
		public JButton send;
		public JButton sendAll;
		public JButton close;
		public JButton delete;
		public JButton deleteAll;
		public JButton saveAsJPEG;
		private boolean exportAllowed = false;
		private Box box;
		public ButtonPanel(boolean exportAllowed) {
			super();
			this.exportAllowed = exportAllowed;
			this.setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
			this.setBackground(background);
			makeButtons();
			setToolTipText();
			setEnables(false,false,false);
			box = new Box(BoxLayout.X_AXIS);
			box.setBackground(background);
			addButtons(box);
			this.add(Box.createVerticalStrut(3));
			this.add(box);
			this.add(Box.createVerticalStrut(3));
		}
		private void makeButtons() {
			open = new JButton("Open");
			save = new JButton("Save");
			saveAll = new JButton("SaveAll");
			send = new JButton("Send");
			sendAll = new JButton("Send All");
			close = new JButton("Close");
			delete = new JButton("Delete");
			deleteAll = new JButton("Delete All");
			saveAsJPEG = new JButton("Save As JPEG");
		}
		private void setToolTipText() {
			open.setToolTipText("Open an image file");
			save.setToolTipText("Apply changes to this image");
			saveAll.setToolTipText("Apply changes to all images of the current study");
			send.setToolTipText("Send this image");
			sendAll.setToolTipText("Send all images of the current study");
			close.setToolTipText("Close this image");
			delete.setToolTipText("Delete the current image from the quarantine");
			deleteAll.setToolTipText("Delete all images of the current study");
			saveAsJPEG.setToolTipText("Make a JPEG copy of the current image");
		}
		private void addButtons(Box box) {
			box.add(Box.createHorizontalStrut(5));
			box.add(open);
			box.add(Box.createHorizontalStrut(5));
			box.add(save);
			box.add(Box.createHorizontalStrut(5));
			box.add(saveAll);

			//Only add the send and sendAll buttons if
			//export is allowed.
			if (exportAllowed) {
			box.add(Box.createHorizontalStrut(5));
			box.add(send);
			box.add(Box.createHorizontalStrut(5));
			box.add(sendAll);
			}

			box.add(Box.createHorizontalGlue());
			box.add(close);
			box.add(Box.createHorizontalStrut(5));
			box.add(delete);
			box.add(Box.createHorizontalStrut(5));
			box.add(deleteAll);
			box.add(Box.createHorizontalStrut(5));
			box.add(saveAsJPEG);
			box.add(Box.createHorizontalStrut(5));
		}
		public void setEnables(boolean imageOpen, boolean exportEnabled, boolean changesAvailable) {
			save.setEnabled(imageOpen && changesAvailable);
			saveAll.setEnabled(imageOpen && changesAvailable);
			send.setEnabled(imageOpen && exportEnabled);
			sendAll.setEnabled(imageOpen && exportEnabled);
			close.setEnabled(imageOpen);
			delete.setEnabled(imageOpen);
			deleteAll.setEnabled(imageOpen);
			saveAsJPEG.setEnabled(imageOpen);
		}
		public void addActionListeners(ActionListener listener) {
			open.addActionListener(listener);
			save.addActionListener(listener);
			saveAll.addActionListener(listener);
			send.addActionListener(listener);
			sendAll.addActionListener(listener);
			close.addActionListener(listener);
			delete.addActionListener(listener);
			deleteAll.addActionListener(listener);
			saveAsJPEG.addActionListener(listener);
		}
	}

	/**
	 * Add a FileListener to the listener list.
	 * @param listener the FileListener.
	 */
	public void addFileListener(FileListener listener) {
		listenerList.add(FileListener.class, listener);
	}

	/**
	 * Remove a FileListener from the listener list.
	 * @param listener the FileListener.
	 */
	public void removeFileListener(FileListener listener) {
		listenerList.remove(FileListener.class, listener);
	}

	//Send a FileEvent of type DELETE to all FileListeners.
	private void sendDeleteFileEvent(File file) {
		sendFileEvent(new FileEvent(this,FileEvent.DELETE,file,null));
	}

	//Send a FileEvent of type MOVE to all FileListeners.
	private void sendMoveFileEvent(File before, File after) {
		sendFileEvent(new FileEvent(this,FileEvent.MOVE,before,after));
	}

	//Send a FileEvent to all FileEvent listeners.
	//The event is sent in the calling thread because all deletions
	//or moves generated in this class occur in the event thread
	//already, making this method safe for GUI updates.
	private void sendFileEvent(FileEvent event) {
		EventListener[] listeners = listenerList.getListeners(FileListener.class);
		for (int i=0; i<listeners.length; i++) {
			((FileListener)listeners[i]).fileEventOccurred(event);
		}
	}

}
