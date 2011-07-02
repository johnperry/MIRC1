/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

/**
 * An object encapsulating one change to a DICOM text element.
 */
public class EditChange {
	
	/** The tag of the DICOM element, in the form 0xggggeeee. */
	public int tag;
	/** The old value of the DICOM element */
	public String oldValue;
	/** The new value of the DICOM element */
	public String newValue;
	/** The StudyInstanceUID of the DICOM object in which the change occurred. */
	public String siUID;
	
	/**
	 * Class constructor capturing information about the element change.
	 * @param tag the DICOM element tag, in the form 0xggggeeee.
	 * @param oldValue the previous value of the element.
	 * @param newValue the new value of the element.
	 * @param siUID the StudyInstanceUID of the DICOM object in which the change occurred,
	 * provided to allow applications to ensure that element changes are propagated only 
	 * to DICOM objects in the same study.
	 */
	public EditChange(int tag, String oldValue, String newValue, String siUID) {
		this.tag = tag;
		this.oldValue = oldValue;
		this.newValue = newValue;
		this.siUID = siUID;
	}
}

