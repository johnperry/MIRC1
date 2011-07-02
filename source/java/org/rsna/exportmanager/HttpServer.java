/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.exportmanager;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Hashtable;
import javax.net.ServerSocketFactory;
import org.apache.log4j.Logger;
import org.rsna.mircsite.log.Log;

/**
 * A simple receiver for DICOM objects transmitted using the HTTP(S) protocol.
 */
public class HttpServer extends Thread {

	static final Logger logger = Logger.getLogger(HttpServer.class);

	int port;
	HtmlSource logSource;
	HtmlSource queuesSource;
	ServerSocket serverSocket;

	/**
	 * Class constructor; creates a new instance of the HttpServer.
	 * This server just returns an HTML page containing the status.
	 * If the URL path is "/log", it returns the log page.
	 * If the URL path is "/queues", it returns the queues page.
	 * Any other URL path returns a frameset page showing both pages.
	 * @param port the port on which to listen for file transfers.
	 * value is null.
	 */
    public HttpServer(int port, HtmlSource logSource, HtmlSource queuesSource) throws Exception {

		this.port = port;
		this.logSource = logSource;
		this.queuesSource = queuesSource;

		//Get the Server Socket on the specified port.
		//Note: this will throw an Exception if it fails.
		ServerSocketFactory serverSocketFactory = ServerSocketFactory.getDefault();
		serverSocket = serverSocketFactory.createServerSocket(port);
	}

	/**
	 * Accept connections and return the current log.
	 */
	public void run() {

		//Accept connections and handle them
		while (!this.isInterrupted()) {
			try {
				//Wait for a connection
				Socket socket = serverSocket.accept();

				//Handle the connection in a separate thread
				HttpHandler handler = new HttpHandler(socket);
				handler.start();
			}
			catch (Exception ex) {
				logger.warn("HttpServer interrupted\n"+ex.getMessage());
				break;
			}
		}
		try { serverSocket.close(); }
		catch (Exception ignore) { }
		serverSocket = null;
	}

	/**
	 * Stop the HttpServer.
	 */
	public void stopServer() {
		this.interrupt();
	}

	/**
	 * Get the port on which the HttpReceiver was initialized.
	 * @return the port
	 */
	public int getPort() {
		return port;
	}

	// The thread that handles an individual connection.

	class HttpHandler extends Thread {

		Socket socket;

		public HttpHandler(Socket socket) {
			this.socket = socket;
		}

		public void run() {
			BufferedInputStream inStream = null;
			OutputStream outStream = null;
			HttpResponse response = new HttpResponse();
			try {
				//Get the socket streams
				inStream = new BufferedInputStream(socket.getInputStream());
				outStream = socket.getOutputStream();

				//Get the Request
				String request = getLine(inStream);

				//Get the headers
				Hashtable<String,String> headers = getHeaders(inStream);

				//Get the requested page
				if (request.indexOf("/queues") != -1)
					response.write(queuesSource.getHTML());
				else if (request.indexOf("/log") != -1) {
					response.write("<html><head>");
					response.write(getScript());
					response.write("</head><body>");
					response.write(logSource.getHTML());
					response.write("</body></html>");
				}
				else
					response.write(getFrames());

				//Send the response  and close the streams
				response.send(outStream);
				outStream.flush();
				outStream.close();
				inStream.close();
			}
			catch (Exception ex) {
				if (outStream != null) {
					try {
						response.write("Unable to process the request.");
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

		private String getFrames() {
			StringBuffer sb = new StringBuffer();
			sb.append("<html>");
			sb.append("	<head>");
			sb.append("		<title>Export Manager Status</title>");
			sb.append("	</head>");
			sb.append("	<frameset rows=\"*,192\">");
			sb.append("		<frame src=\"/log\">");
			sb.append("		<frame src=\"/queues\">");
			sb.append("	</frameset>");
			sb.append("</html>");
			return sb.toString();
		}

		private String getScript() {
			StringBuffer sb = new StringBuffer();
			sb.append("<script>");
			sb.append("function showLast() {\n");
			sb.append("	var rowList = document.body.getElementsByTagName('TR');\n");
			sb.append("	if (rowList.length != 0) rowList[rowList.length - 1].scrollIntoView(false);");
			sb.append("}\n");
			sb.append("onload = showLast;\n");
			sb.append("</script>");
			return sb.toString();
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
	}

	// A simple HTTP text response.
	class HttpResponse {

		StringBuffer response = null;

		public HttpResponse() {
			response = new StringBuffer();
		}

		public void write(String string) {
			response.append(string);
		}

		public void send(OutputStream stream) {
			String headers =
				"HTTP/1.0 200 OK\r\n" +
				"Content-Type: text/html\r\n" +
				"Expires: Thu, 16 Mar 2000 11:00:00 GMT\r\n" +
				"Pragma: No-cache\r\n" +
				"Cache-Control: no-cache\r\n" +
				"Content-Length: " + response.length() + "\r\n\r\n";

			try {
				stream.write(headers.getBytes());
				stream.write(response.toString().getBytes());
			}
			catch (Exception ex) {
				Log.message("HTTP Server Exception:\n"+ex);
			}
		}
	}

}
