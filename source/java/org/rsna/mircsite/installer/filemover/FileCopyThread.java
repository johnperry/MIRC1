package org.rsna.mircsite.installer.filemover;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

/**
 * This class runs as a seperate thread, and performs the copying of files from the old version 
 * of tomcat, to the new version.  It sends updates back to the caller via the drawNewFile() method 
 * on the WelcomePage object that is passed into this thread upon instantiation to let the caller know 
 * whenever a new file is copied, and it then sets the isComplete property on the caller to true, once 
 * the copy is complete.
 * @author RBoden
 *
 */
public class FileCopyThread extends Thread {

	private String source;
	private String destination;
	private WelcomePage page;
	/**
	 * @param source path to the old version of tomcat
	 * @param destination path to the new version of tomcat
	 */
	public FileCopyThread(String source, String destination, WelcomePage page) {
		this.source = source;
		this.destination = destination;
		this.page = page;
	}
	
	/**
	 * kicks off the thread
	 */
	public void run() {
		try {
		// for testing lets just copy the webapps folder over
		File sourceWebapps = new File(source+"webapps"+File.separator);
		File destinationWebapps = new File(destination+"webapps"+File.separator);
		
		// copy over all the directories in webapps except for excluded ones
		String[] acceptableSubDirs = sourceWebapps.list(dirFilter);
		for (int i = 0; i < acceptableSubDirs.length; i++) {
			File sourceDir = new File(sourceWebapps, acceptableSubDirs[i]);
			File destDir = new File(destinationWebapps, acceptableSubDirs[i]);
			copyDirectory(sourceDir, destDir, page);
		}
		// copy over the tomcat-users.xml
		File sourceUsersXml = new File(source+"conf"+File.separator+"tomcat-users.xml");
		File destUsersXml = new File(destination+"conf"+File.separator+"tomcat-users.xml");
		
		copyFile(sourceUsersXml, destUsersXml, page);
		} catch( IOException ioe ){
			ioe.printStackTrace(System.err);
		}
		page.isComplete = true;
		triggerNewFileEvent(page, null);
		
	}
	
    // Copies all files under srcDir to dstDir.
    // If dstDir does not exist, it will be created.
    private  void copyDirectory(File srcDir, File dstDir, WelcomePage welcome) throws IOException {
        if (srcDir.isDirectory()) {
            if (!dstDir.exists()) {
                dstDir.mkdir();
            }
    
            String[] children = srcDir.list();
            for (int i=0; i<children.length; i++) {
                copyDirectory(new File(srcDir, children[i]),
                                     new File(dstDir, children[i]), welcome);
            }
        } else {
            // This method is implemented in e1071 Copying a File
            copyFile(srcDir, dstDir, welcome);
        }
    }
    
    // Copies src file to dst file.
    // If the dst file does not exist, it is created
    private  void copyFile(File src, File dst, WelcomePage welcome) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);
        triggerNewFileEvent(welcome, src.toString());
        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }
    
	/**
	 * inner class that will find elligble directories under the /webapps directory for 
	 * being copied over.
	 */
	private  FilenameFilter dirFilter = new FilenameFilter() {
		private List<String> excludeNames = new ArrayList<String>();

		public boolean accept(File dir, String name) {
			excludeNames.add("tomcat-docs");
			excludeNames.add("ROOT");
			File testFile = new File(dir, name);
			if( testFile.isDirectory() && !excludeNames.contains(name)) {
				return true;
			}
			return false;
		}
	};
	
	private void triggerNewFileEvent(WelcomePage welcome, String fileName) {
		final WelcomePage welcomeFinal = welcome;
		final String fileNameFinal = fileName;
		Runnable fireEvents = new Runnable() {
			public void run() {
					welcomeFinal.drawNewFile(fileNameFinal);
			}
		};
		SwingUtilities.invokeLater(fireEvents);		
	}
	
}
