/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.io.*;

/**
 * A generic file object providing methods for moving, renaming, and copying a file.
 * This is the base class for MIRC data files used for DICOM images and clinical trial
 * metadata (both XML and zip).
 */
public class FileObject {

	File file = null;
	/**
	 * Class constructor; creates the file object.
	 * @param file the file.
	 */
	public FileObject(File file) {
		this.file = file;
	}

	/**
	 * Get the File pointing to the file.
	 * @return the File pointing to the file.
	 */
	public File getFile() {
		return file;
	}

	/**
	 * Replace all occurrences of a target regex with
	 * a replacement string and rename the file.
	 */
	public void filterFilename(String target, String replacement) {
		String name = file.getName();
		name = name.replaceAll(target,replacement);
		File newFile = new File(file.getParentFile(),name);
		file.renameTo(newFile);
		file = newFile;
	}

	/**
	 * Get the file's extension (the last period and the characters after
	 * it in the file name). Thus, the method returns ".dcm" for a file
	 * named "image.dcm".
	 * @return the extension of the object's filename.
	 */
	public String getExtension() {
		String ext = file.getName();
		int k = ext.lastIndexOf(".");
		if (k == -1) return "";
		return ext.substring(k);
	}

	/**
	 * Set the file's extension (the last period and the characters after
	 * it in the file name). If the current extension is ".md", it is replaced
	 * the supplied extension. If the current extension is already equal to
	 * the supplied extension, the file is not renamed. If the current
	 * extension is anything else, the supplied extension is appended  to
	 * the filename.
	 * @return the file after any modification.
	 */
	public File setExtension(String extension) {
		String name = file.getName();
		String nameLC = name.toLowerCase();
		if (nameLC.endsWith(extension.toLowerCase())) return file;
		if (nameLC.endsWith(".md")) {
			name = name.substring(0,name.length()-3);
		}
		File newFile = new File(file.getParentFile(),name+extension);
		file.renameTo(newFile);
		file = newFile;
		return file;
	}

	/**
	 * Set the standard extension. This method is overridden by
	 * FileObject subclasses that have standard extensions.
	 * This method does nothing in the FileObject class.
	 * @return the file without modification.
	 */
	public File setStandardExtension() {
		return file;
	}

	/**
	 * Get the object's class name. This method may be overridden by subclasses
	 * wishing to provide a better description of the type of the object.
	 * @return the type of the object.
	 */
	public String getType() {
		String type = this.getClass().getName();
		return type.substring(type.lastIndexOf(".")+1);
	}

	/**
	 * Get a prefix for a FileObject ("MD-").
	 * This method may be overridden by subclasses wishing
	 * to provide a better prefix for the object.
	 * @return a prefix for FileObjects.
	 */
	public String getTypePrefix() {
		return "MD-";
	}

	/**
	 * Get the date associated with the file. This method may be overridden by subclasses
	 * wishing to provide a more meaningful date associated with the object.
	 * @return the last modified date of the file.
	 */
	public String getDate() {
		return StringUtil.getDateTime(file.lastModified()," ");
	}

	/**
	 * Dummy method returning an empty string for the UID of the object.
	 * This method is overridden by subclasses that can actually determine
	 * a meaningful value.
	 * @return the empty string.
	 */
	public String getUID() {
		return "";
	}

	/**
	 * Get the UID of the object. This method is overridden by the
	 * DicomObject subclass, where the concept of SOPInstanceUID is
	 * well-defined. For other subclasses, this method returns the
	 * value of the getUID() method.
	 * @return the object's unique identifier, if available; otherwise,
	 * the empty string.
	 */
	public String getSOPInstanceUID() {
		return getUID();
	}

	/**
	 * Dummy method returning an empty string for the UID of the study.
	 * This method is overridden by subclasses that can actually determine
	 * a meaningful value.
	 * @return the empty string.
	 */
	public String getStudyUID() {
		return "";
	}

	/**
	 * Get the StudyInstanceUID of the object. This method is overridden
	 * by the DicomObject subclass, where the concept of StudyInstanceUID is
	 * well-defined. For other subclasses, this method returns the value
	 * of the getStudyUID() method.
	 * @return the study's unique identifier, if available; otherwise, the empty string.
	 */
	public String getStudyInstanceUID() {
		return getStudyUID();
	}

	/**
	 * Dummy method returning the file name for the Description.
	 * This method is overridden by subclasses that can actually determine
	 * a meaningful value.
	 * @return the empty string.
	 */
	public String getDescription() {
		return file.getName();
	}

	/**
	 * Rename the file. If the supplied pathname is a directory,
	 * the file is renamed into the directory and given the
	 * same filename it had before. If the supplied pathname is not a
	 * directory, the file is renamed to the supplied pathname.
	 * This method also changes the File returned by getFile() so that
	 * it points to the renamed data file. Use this method to move the
	 * file within the same file system.
	 * @param newFile the new pathname of the file, or the pathname of the
	 * directory into which to rename the file.
	 * @return true if the rename was successful, false otherwise.
	 */
	public boolean renameTo(File newFile) {
		if (newFile.isDirectory())
			newFile = new File(newFile,file.getName());
		boolean ok = file.renameTo(newFile);
		if (ok) file = newFile;
		return ok;
	}

	/**
	 * Copy the object's file. The original file is not deleted.
	 * @param destination the File pointing to where the copy is to be written.
	 * @return true if the operation succeeded completely; false otherwise.
	 */
	public boolean copyTo(File destination) {
		if (file == null) return false;
		return FileUtil.copyFile(file,destination);
	}

	/**
	 * Move the object's data file, and remove it from its previous
	 * location. If the supplied pathname is a directory, the file is
	 * moved into the directory and given the same filename it had before.
	 * If the supplied pathname is not a directory, the file is given
	 * the name defined by the pathname. This method will overwrite a
	 * file with the same name in the destination directory. This method
	 * attempts to do the move with a rename, and only copies the file if
	 * the rename fails. This method also changes the File returned by
	 * getFile() so that it points to the data file in its new location.
	 * Use this method to move the file between file systems.
	 * @param newFile the new name of the file.
	 * @return true if the move was successful, false otherwise.
	 */
	public boolean moveTo(File newFile) {
		if (newFile.isDirectory())
			newFile = new File(newFile,file.getName());
		//Don't bother to copy to yourself
		if (file.getAbsolutePath().equals(newFile.getAbsolutePath()))
			return true;
		//Try to do it with a rename
		boolean ok = file.renameTo(newFile);
		if (!ok) {
			//That didn't work; try to do a copy.
			ok = FileUtil.copyFile(file,newFile);
			//If that worked, then delete the original.
			if (ok) file.delete();
		}
		if (ok) file = newFile;
		return ok;
	}

	/**
	 * Move the object's data file into a directory and remove it
	 * from its previous location. This method changes the File
	 * returned by getFile() so that it points to the data file
	 * in its new location. This method creates the directory if
	 * it does not already exist.
	 * @param dir the directory into which to move the data file.
	 * @param overwrite true if the file is to be allowed to overwrite
	 * an existing file with the same name in the destination directory;
	 * false if a new name is to be created to prevent overwriting a
	 * file.
	 * @return true if the move was successful, false otherwise.
	 */
	public boolean moveToDirectory(File dir, boolean overwrite) {
		return moveToDirectory(dir,file.getName(),overwrite);
	}

	/**
	 * Move the object's data file into a directory, creating a new
	 * name for the file if the filename already exists in the directory,
	 * and remove the file from its previous location. This method will
	 * therefore not overwrite an existing file. This method also changes
	 * the File returned by getFile() so that it points to the data file
	 * in its new location. This method creates the directory if it does
	 * not already exist. This method is intended for placing FileObjects
	 * into a holding directory.
	 * @param dir the directory into which to move the data file.
	 * @return true if the move was successful, false otherwise.
	 */
	public boolean moveToDirectory(File dir) {
		return moveToDirectory(dir,file.getName(),false);
	}

	/**
	 * Move the object's data file into a directory, using the specified
	 * filename. If the filename already exists in the directory, create
	 * a new name for the file. Remove the file from its previous location.
	 * This method therefore does not overwrite an existing file. This
	 * method also changes the File returned by getFile() so that it points
	 * to the data file in its new location. This method creates the directory
	 * if it does not already exist. This method is intended for placing
	 * FileObjects into a document's directory.
	 * @param dir the directory into which to move the data file.
	 * @param name the name to use for the file in the new directory.
	 * @return true if the move was successful, false otherwise.
	 */
	public boolean moveToDirectory(File dir, String name) {
		return moveToDirectory(dir,name,false);
	}

	//The actual move method. This isn't exposed because
	//the other three methods cover all the use cases.
	private boolean moveToDirectory(File dir, String name, boolean overwrite) {
		if (!dir.exists()) dir.mkdirs();
		if (!dir.isDirectory()) return false;
		File newFile = new File(dir,name);

		//Don't move the file if it is already in place.
		if (file.getAbsolutePath().equals(newFile.getAbsolutePath()))
			return true;

		//Check whether it is okay to overwrite an existing file.
		if (newFile.exists() && !overwrite) {
			//Don't overwrite; make a new name for the file.
			try { newFile = File.createTempFile("ALT-","-"+name,dir); }
			catch (Exception ex) { return false; }
		}

		//Move the file. First try to do it with a rename.
		boolean ok = file.renameTo(newFile);
		if (!ok) {
			//That didn't work; try to do a copy.
			ok = FileUtil.copyFile(file,newFile);
			//If that worked, then delete the original.
			if (ok) file.delete();
		}
		if (ok) file = newFile;
		return ok;
	}

	/**
	 * Set the last modified time of the file to the current time.
	 */
	public void touch() {
		file.setLastModified(System.currentTimeMillis());
	}

	/**
	 * Determine whether this FileObject has an extension matching
	 * one of the entries in an array. The method can exclude FileObject
	 * subclasses (DicomObjects, ZipObject, and XmlObject).
	 * @param textext the array of extensions. Each entry must start
	 * with a period (e.g., ".txt").
	 * @param excludeSubclasses true if subclasses are to be rejected;
	 * false if all FileObjects are to be tested.
	 * @return true if this FileObject has a matching extension; false otherwise.
	 */
	public boolean hasMatchingExtension(String[] textext, boolean excludeSubclasses) {
		if (excludeSubclasses) {
			if (this instanceof DicomObject) return false;
			if (this instanceof XmlObject) return false;
			if (this instanceof ZipObject) return false;
		}
		if (textext == null) return false;
		String ext = getExtension();
		for (int i=0; i<textext.length; i++) {
			if (ext.equals(textext[i])) return true;
		}
		return false;
	}

	/**
	 * Factory to create a FileObject from a File, instantiating the
	 * correct subclass of FileObject.
	 * @param file the file to use to instantiate the FileObject.
	 * @return the instantiated FileObject.
	 */
	public static FileObject getObject(File file) {

		//First try to parse the object based on its extension.
		String name = file.getName().toLowerCase();
		boolean triedDICOM = false;
		boolean triedXML = false;
		boolean triedZip = false;
		FileObject fileObject = null;
		if (name.endsWith(".xml")) {
			fileObject = tryXml(file);
			triedXML = true;
		}
		else if (name.endsWith(".zip")) {
			fileObject = tryZip(file);
			triedZip = true;
		}
		else {
			fileObject = tryDicom(file);
			triedDICOM = true;
		}
		if (fileObject != null) return fileObject;

		//That didn't work, now try the untried possibilities.
		if (!triedDICOM)
			fileObject = tryDicom(file);

		if ((fileObject == null) && !triedZip)
			fileObject = tryZip(file);

		if ((fileObject == null) && !triedXML)
			fileObject = tryXml(file);

		if (fileObject == null)
			fileObject = new FileObject(file);
		return fileObject;
	}

	private static DicomObject tryDicom(File file) {
		try { return new DicomObject(file); }
		catch (Exception ex) { return null; }
	}

	private static XmlObject tryXml(File file) {
		try { return new XmlObject(file); }
		catch (Exception ex) { return null; }
	}

	private static ZipObject tryZip(File file) {
		try { return new ZipObject(file); }
		catch (Exception ex) { return null; }
	}

}
