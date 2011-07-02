package org.rsna.mircsite.installer.filemover;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for the FileMover applet application.  Some of these things may make sense 
 * to move out to a more global utility class at a later time.
 * @author RBoden
 *
 */
public class FileUtil extends Thread{
	


	
	/** The list of directories which must be present to believe
	that this is an instance of Tomcat's root directory. */
	private static final String[] tomcatDirNames = new String[] {"conf","logs","shared","webapps","work"};
	
	
	/**
	 * Look for directories until you find one with "version" in the directory
	 * indicating that it's version "version" of tomcat.  If we don't find anything 
	 * we'll just use the best that we found.
	 */
	public static String findParticularTomcatVersion(String version, File defDirFile) {
		String tmpResult = "";
		String finalResult = "";
		int numberOfIterations = 0;
		List<String> alreadyFoundTomcats = new ArrayList<String>();
		do{
			numberOfIterations++;
			tmpResult = findInstalledVersion(defDirFile, 3, alreadyFoundTomcats);
			// protect against null
			if ( tmpResult == null) {
				tmpResult = "";
			}
			alreadyFoundTomcats.add(tmpResult);
			// if we didn't get back garbage, lets go ahead and use this as our final result
			if( tmpResult != null && tmpResult.trim().length() > 0) {
				finalResult = tmpResult;
			}
		} while( !tmpResult.contains(version) && numberOfIterations < 3 );
		
		finalResult += File.separator;

		return finalResult;
	}	

	/**	Find the Tomcat instance. Start by looking for it in the Program Files
	directory in case it is a standard installation on a Windows system.
	If it isn't there, then walk a specified number of levels down the
	directory tree on the current root to see if Tomcat is around. */
	private static String findInstalledVersion(File dir, int level, List alreadyFoundTomcats) {
		//Look in c:/Program Files
		File programFiles = new File("C:/Program Files");
		if (programFiles.exists()) {
			String dirPath = walkTree(programFiles,2, alreadyFoundTomcats);
			if (dirPath != null) return dirPath;
		}
		//Okay, no luck in the standard place, so walk the tree under the supplied dir
		return walkTree(dir,level, alreadyFoundTomcats);
	}

	//Walk the tree under a directory and find Tomcat.
	private static String walkTree(File dir, int level, List alreadyFoundTomcats) {
		String dirPath = null;
		if (dir == null) return dirPath;
		if (!dir.isDirectory()) return dirPath;

		//See if this directory is Tomcat.
		String name = dir.getName().toLowerCase();
		if (((name.indexOf("tomcat") >= 0) || (name.indexOf("instance") >= 0))
			&&	contentsCheck(dir,tomcatDirNames)
			&& !alreadyFoundTomcats.contains(dir.getAbsolutePath()))
			return dir.getAbsolutePath();

		//It's not; see if it contains Tomcat
		if (level > 0) {
			File[] files = dir.listFiles();
			//First check all the sub-directories to see if one is Tomcat.
			//Do it this way to find it faster by traversing the width of the
			//tree, rather that the depth of the tree, first.
			if( files != null) {
				for (int i=0; i<files.length; i++) {
					if ((files[i] != null) && files[i].isDirectory()) {
						name = files[i].getName().toLowerCase();
						if (((name.indexOf("tomcat") >= 0) || (name.indexOf("instance") >= 0))
							&&	contentsCheck(files[i],tomcatDirNames)
							&& !alreadyFoundTomcats.contains(files[i].getAbsolutePath()))
							return files[i].getAbsolutePath();
					}
				}
			}
			//No joy, now look at the directories to see if they contain a Tomcat
			if( files != null ) {
				for (int i=0; i<files.length; i++) {
					if ((files[i] != null) && files[i].isDirectory()) {
						dirPath = walkTree(files[i],level-1, alreadyFoundTomcats);
						if (dirPath != null) return dirPath;
					}
				}
			}
		}
		return dirPath;
	}
	
	/**
	 * Checks to see if the file you are passing in is indeed a tomcat directory.
	 * @param dir
	 */
	public static boolean isDirectoryTomcat(File dir) {
		return contentsCheck(dir,tomcatDirNames);
	}
	
	/**
	 * Check the contents of a directory to see if all
	 * the files in an array are present.
	 * @param dir the directory.
	 * @param include the list of filenames
	 * @return true if all the filenames are included in the
	 * directory; false otherwise.
	 */
	private static boolean contentsCheck(File dir, String[] include) {
		return contentsCheck(dir, include, new String[]{});
	}	
	
	/**
	 * Check the contents of a directory to see if all
	 * the files in one array are present and all the files
	 * in another array are not.
	 * @param dir the directory.
	 * @param include the list of filenames that must be present.
	 * @param exclude the list of filenames that must be absent.
	 * @return true if the directory contents meet the requirements;
	 * false otherwise.
	 */
	private static boolean contentsCheck(File dir, String[] include, String[] exclude) {
		if (!dir.isDirectory()) return false;
		File[] files = dir.listFiles();
		if (include != null) {
			for (int i=0; i<include.length; i++) {
				if (!checkForFile(files,include[i])) return false;
			}
		}
		if (exclude != null) {
			for (int i=0; i<exclude.length; i++) {
				if (checkForFile(files,exclude[i])) return false;
			}
		}
		return true;
	}	
	
	/**
	 * See if a file appears in a list of files.
	 * @param files the list of files.
	 * @param name the filename to look for.
	 * @return true if the name appears in the list; false otherwise.
	 */
	private static boolean checkForFile(File[] files, String name) {
		if (files != null) {
			for (int i=0; i<files.length; i++) {
				if (files[i].getName().equals(name)) return true;
			}
		}
		return false;
	}
	


   
		
	


	
	
		
	
}
