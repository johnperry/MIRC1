/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.fieldcenter;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;
import javax.swing.event.*;
import javax.swing.SwingUtilities;
import java.util.EventListener;
import org.apache.log4j.Logger;
import org.rsna.util.ApplicationProperties;

/**
 * A simple receiver for DICOM objects transmitted using the HTTP(S) protocol.
 */
public class HttpReceiver extends Thread implements HttpFileEventListener {

	static final Logger logger = Logger.getLogger(HttpReceiver.class);

	File dir;
	String protocol;
	int port;
	File keystore;
	String password;
	ServerSocket serverSocket;
	EventListenerList listenerList;

	/**
	 * Class constructor; creates a new instance of the HttpReceiver.
	 * @param dir the directory into which to write received files.
	 * @param protocol the protocol to use ("http" or "https").
	 * @param port the port on which to listen for file transfers.
	 * @param keystore the https keystore file, or null. This value is used to
	 * overwrite the javax.net.ssl.keyStore value only if the javax.net.ssl.keyStore value
	 * is null.
	 * @param password the https keystore password, or null. This value is used to
	 * overwrite the javax.net.ssl.keyStorePassword value only if the javax.net.ssl.keyStorePassword
	 * value is null.
	 */
    public HttpReceiver (
					File dir,
					String protocol,
					int port,
					File keystore,
					String password) throws Exception {

		listenerList = new EventListenerList();
		this.dir = dir;
		this.protocol = protocol;
		this.port = port;
		this.keystore = keystore;
		this.password = password;

		if (!protocol.equals("http") && !protocol.equals("https")) protocol = "http";

		//Set the default keystore parameters only if the protocol is "https" and
		//the parameters are not already set.
		if (protocol.equals("https") && (keystore != null) && (password != null) &&
			System.getProperty("javax.net.ssl.keyStore") == null) {

			System.setProperty("javax.net.ssl.keyStore",keystore.getAbsolutePath());
			System.setProperty("javax.net.ssl.keyStorePassword",password);
		}

		//Now get the Server Socket with the specified protocol on the specified port.
		//Note: this will throw an Exception if it fails.
		ServerSocketFactory serverSocketFactory = null;
		if (protocol.equals("http"))
			serverSocketFactory = ServerSocketFactory.getDefault();
		else
			serverSocketFactory = SSLServerSocketFactory.getDefault();
		serverSocket = serverSocketFactory.createServerSocket(port);
	}

	/**
	 * Accept connections and receive files, sending HttpFileEvents when
	 * files have been placed in the directory.
	 */
	public void run() {

		//Accept connections and handle them
		while (!this.isInterrupted()) {
			try {
				//Wait for a connection
				Socket socket = serverSocket.accept();

				//Create a thread to handle the connection
				HttpHandler handler = new HttpHandler(dir,socket);
				handler.addHttpFileEventListener(this);
				handler.start();
			}
			catch (Exception ex) {
				logger.warn("HttpReceiver interrupted\n"+ex.getMessage());
				break;
			}
		}
	}

	/**
	 * Stop the HttpReceiver.
	 */
	public void stopReceiver() {
		this.interrupt();
	}

	/**
	 * Get the protocol for which the HttpReceiver was initialized.
	 * @return the protocol
	 */
	public String getProtocol() {
		return protocol;
	}

	/**
	 * Get the port on which the HttpReceiver was initialized.
	 * @return the port
	 */
	public int getPort() {
		return port;
	}

	/**
	 * Forward events from the subordinate threads that receive files
	 * to the HttpEventListeners that are registered with the HttpReceiver.
	 * @param event the event that identifying the file that was received.
	 */
	public void httpFileEventOccurred(HttpFileEvent event) {
		sendHttpFileEvent(this, event.status, event.file, event.message);
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
