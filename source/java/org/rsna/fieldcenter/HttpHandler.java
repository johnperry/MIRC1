/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.fieldcenter;

import java.io.*;
import java.net.Socket;
import java.util.EventListener;
import java.util.Hashtable;
import javax.swing.event.*;
import javax.swing.SwingUtilities;

/**
 * A handler for one connection to the HTTP receiver.
 */
public class HttpHandler extends Thread {

	Socket socket;
	File dir;
	EventListenerList listenerList;

	/**
	 * Class constructor; creates a handler for one HTTP connection.
	 * @param dir the store directory into which to write the received object.
	 * @param socket the client socket on which to receive the object.
	 */
	public HttpHandler(File dir, Socket socket) {
		listenerList = new EventListenerList();
		this.socket = socket;
		this.dir = dir;
	}

	/**
	 * The thread's run implementation
	 */
	public void run() {
		BufferedInputStream inStream = null;
		OutputStream outStream = null;
		HttpResponse response = new HttpResponse();
		try {
			//Get the socket streams
			inStream = new BufferedInputStream(socket.getInputStream());
			outStream = socket.getOutputStream();

			//Create a file and a stream to receive the object
			File file = File.createTempFile("HR-",".md",dir);
			BufferedOutputStream outFile =
				new BufferedOutputStream(
					new FileOutputStream(file));

			//Get the headers
			Hashtable<String,String> headers = getHeaders(inStream);
			String contentLength = headers.get("content-length");

			int count = 0;
			try { count = Integer.parseInt(contentLength); }
			catch (Exception ignore) { }

			//Write the input into the file
			int n;
			int len = 4096;
			byte[] buf = new byte[len];
			while ((count > 0) && ((n=inStream.read(buf,0,len)) != -1)) {
				outFile.write(buf,0,n);
				count -= n;
			}

			if (count != 0)
				throw new Exception("Error: input stream reading complete; count = "+count);

			outFile.flush();
			outFile.close();

			//Send a success response and close the streams
			response.write("OK");
			response.send(outStream);
			outStream.flush();
			outStream.close();
			inStream.close();

			//Send an event to any listeners
			sendHttpFileEvent(this, HttpFileEvent.RECEIVED, file, null);
		}

		catch (Exception ex) {
			sendHttpFileEvent(this, HttpFileEvent.ERROR, null,
				"HttpReceiver: An error occurred while receiving a file.");
			if (outStream != null) {
				try {
					response.write("Unable to receive the object.");
					response.send(outStream);
					outStream.flush();
					outStream.close();
				}
				catch (Exception ignore) { }
			}
		}
		try { socket.close(); }
		catch (Exception ignore) { }
	}

	//Construct a hashtable containing the HTTP headers,
	//positioning the stream to the beginning of the data.
	private Hashtable<String,String> getHeaders(BufferedInputStream in) {
		Hashtable<String,String> headers = new Hashtable<String,String>();
		String line;
		while (!((line=getLine(in)).equals(""))) {
			int k = line.indexOf(":");
			if (k != -1)
				headers.put(
					line.substring(0,k).trim().toLowerCase(),
					line.substring(k+1).trim() );
		}
		return headers;
	}

	//Get one header line from the stream,
	//using \r\n as the delimiter.
	private String getLine(BufferedInputStream in) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		boolean rFlag = false;
		int b;
		try {
			while ((b=in.read()) != -1) {
				baos.write(b);
				if (rFlag && (b == 10)) break;
				rFlag = (b == 13);
			}
			return baos.toString().trim();
		}
		catch (Exception ex) { }
		return "";
	}

	/**
	 * Add an HttpFileEventListener to the listener list.
	 * @param listener the HttpFileEventListener.
	 */
	public void addHttpFileEventListener(HttpFileEventListener listener) {
		listenerList.add(HttpFileEventListener.class, listener);
	}

	/**
	 * Remove an HttpFileEventListener from the listener list.
	 * @param listener the HttpFileEventListener.
	 */
	public void removeHttpFileEventListener(HttpFileEventListener listener) {
		listenerList.remove(HttpFileEventListener.class, listener);
	}

	//Send an HttpFileEvent to all HttpFileEventListeners.
	//The event is sent in the event thread to make it safe for
	//GUI components.
	private void sendHttpFileEvent(Object object, int status, File file, String message) {
		final EventListener[] listeners = listenerList.getListeners(HttpFileEventListener.class);
		if (listeners.length > 0) {
			final HttpFileEvent event = new HttpFileEvent(object, status, file, message);
			Runnable fireEvents = new Runnable() {
				public void run() {
					for (int i=0; i<listeners.length; i++) {
						((HttpFileEventListener)listeners[i]).httpFileEventOccurred(event);
					}
				}
			};
			SwingUtilities.invokeLater(fireEvents);
		}
	}

}
