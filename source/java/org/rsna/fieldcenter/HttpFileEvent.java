/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.fieldcenter;

import java.awt.AWTEvent;
import java.io.File;

/**
 * The event that passes a file reception to HttpFileEventListeners.
 */
public class HttpFileEvent extends AWTEvent {

	public static final int FILE_EVENT = AWTEvent.RESERVED_ID_MAX + 4270;
	public static final int RECEIVED = 0;
	public static final int ERROR = -1;

	public int status;
	public File file;
	public String message;

	/**
	 * Class constructor capturing an HttpFileEvent for a successful file reception.
	 * @param object the source of the event.
	 * @param file the file on which the event occurred.
	 */
	public HttpFileEvent(Object object, File file) {
		this(object, RECEIVED, file, null);
	}

	/**
	 * Class constructor capturing a general HttpFileEvent.
	 * @param object the source of the event.
	 * @param status the type of file event.
	 * @param file the file on which the event occurred.
	 * @param message a text message describing the event.
	 */
	public HttpFileEvent(Object object, int status, File file, String message) {
		super(object, FILE_EVENT);
		this.status = status;
		this.file = file;
		this.message = message;
	}

}
