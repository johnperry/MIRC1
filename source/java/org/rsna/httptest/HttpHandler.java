/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.httptest;

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
	boolean sendResponse;
	EventListenerList listenerList;

	/**
	 * Class constructor; creates a handler for one HTTP connection.
	 * @param socket the client socket on which to receive the object.
	 * @param sendResponse true if the server is to send a report response
	 * to the client when a connection is received, false otherwise.
	 */
	public HttpHandler(Socket socket, boolean sendResponse) {
		listenerList = new EventListenerList();
		this.socket = socket;
		this.sendResponse = sendResponse;
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

			//Get the data from the connection
			Hashtable<String,String> headerHashtable = new Hashtable<String,String>();
			String headers = getHeaders(inStream,headerHashtable);
			String content = getContent(inStream,headerHashtable);

			//Make a report
			StringBuffer sb = new StringBuffer();
			sb.append("Headers received by the server:\n");
			sb.append(headers);
			sb.append("Content received by the server:\n");
			sb.append(content);
			if (content.length() == 0) sb.append("[none]");
			String report = sb.toString();

			//If enabled, send a response
			if (sendResponse) {
				response.write(
					"The server reported this information about the connection it received:\n\n");
				response.write(report);
				response.send(outStream);
			}
			outStream.flush();
			outStream.close();
			inStream.close();

			//Send an event to any listeners
			sendHttpConnectionEvent(this, HttpConnectionEvent.RECEIVED, report);
		}

		catch (Exception ex) {
			sendHttpConnectionEvent(this, HttpConnectionEvent.ERROR,
				"HttpReceiver: An error occurred during the connection.");
		}
		try { socket.close(); }
		catch (Exception ignore) { }
	}

	//Construct a hashtable containing the HTTP headers,
	//positioning the stream to the beginning of the data.
	private String getHeaders(BufferedInputStream in, Hashtable<String,String> headers) {
		StringBuffer sb = new StringBuffer();
		String line;
		while (!((line=getLine(in)).equals(""))) {
			sb.append(line + "\n");
			int k = line.indexOf(":");
			if (k != -1)
				headers.put(
					line.substring(0,k).trim().toLowerCase(),
					line.substring(k+1).trim() );
		}
		return sb.append("\n").toString();
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
		}
		catch (Exception ex) { }
		return baos.toString().trim();
	}

	//Get the content passed in the connection as a String.
	private String getContent(BufferedInputStream is, Hashtable<String,String> headers) {
		String contentLength = headers.get("content-length");
		int count = 0;
		try { count = Integer.parseInt(contentLength); }
		catch (Exception ignore) { }

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int b;
		try {
			while ((count > 0) && (b=is.read()) != -1) {
				baos.write(b);
				count--;
			}
			return baos.toString();
		}
		catch (Exception e) {
			return
				"Error reading the input stream\nException message:\n"
				+ e.getMessage() + "\n\n"
				+ baos.toString();
		}
	}

	/**
	 * Add an HttpConnectionEventListener to the listener list.
	 * @param listener the HttpConnectionEventListener.
	 */
	public void addHttpConnectionEventListener(HttpConnectionEventListener listener) {
		listenerList.add(HttpConnectionEventListener.class, listener);
	}

	/**
	 * Remove an HttpFileEventListener from the listener list.
	 * @param listener the HttpFileEventListener.
	 */
	public void removeHttpConnectionEventListener(HttpConnectionEventListener listener) {
		listenerList.remove(HttpConnectionEventListener.class, listener);
	}

	//Send an HttpConnectionEvent to all HttpConnectionEventListeners.
	//The event is sent in the event thread to make it safe for GUI components.
	private void sendHttpConnectionEvent(Object object, int status, String message) {
		final EventListener[] listeners = listenerList.getListeners(HttpConnectionEventListener.class);
		if (listeners.length > 0) {
			final HttpConnectionEvent event = new HttpConnectionEvent(object, status, message);
			Runnable fireEvents = new Runnable() {
				public void run() {
					for (int i=0; i<listeners.length; i++) {
						((HttpConnectionEventListener)listeners[i]).httpConnectionEventOccurred(event);
					}
				}
			};
			SwingUtilities.invokeLater(fireEvents);
		}
	}

}
