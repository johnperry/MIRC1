/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.httpreceiver;

import java.io.*;
import java.net.Socket;
import java.util.Enumeration;
import java.util.Hashtable;

/**
 * A handler for one connection to the HTTP receiver.
 */
public class Handler extends Thread {

	Socket socket;
	File dir;
	File temp;

	/**
	 * Class constructor; creates a handler for one HTTP connection.
	 * @param dir the store directory into which to write the received object.
	 * @param temp the temporary store directory - must be in the same file
	 * system as dir.
	 * @param socket the client socket on which to receive the object.
	 */
	public Handler(File dir, File temp, Socket socket) {
		this.socket = socket;
		this.dir = dir;
		this.temp = temp;
	}

	/**
	 * The thread's run implementation
	 */
	public void run() {
		BufferedInputStream inStream = null;
		OutputStream outStream = null;
		Response response = new Response();
		try {
			//Get the socket streams
			inStream = new BufferedInputStream(socket.getInputStream());
			outStream = socket.getOutputStream();

			//Create a file and a stream to receive the object
			File file = File.createTempFile("HR-","",temp);
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
				System.out.println(
					"Error: input stream reading complete; count = "+count);

			outFile.flush();
			outFile.close();

			//Now rename the file into dir. The reason for doing it this way
			//is to prevent any asynchronous process from seeing the file until
			//it is completely there.
			File dirFile = File.createTempFile("HR-","",dir);
			dirFile.delete();
			file.renameTo(dirFile);

			//Send a success response and close the streams
			response.write("OK");
			response.send(outStream);
			outStream.flush();
			outStream.close();
			inStream.close();
		}
		catch (Exception ex) {
			System.out.println("Exception while receiving an object\n"+ex);
			if (outStream != null) {
				try {
					response.write("Unable to receive the object.");
					response.send(outStream);
					outStream.flush();
					outStream.close();
				}
				catch (Exception e) {
					System.out.println("Exception while reporting an error to the client\n"+e);
				}
			}
		}
		try { socket.close(); }
		catch (Exception ex) {
			System.out.println("Error closing the client socket\n"+ex);
		}
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
		catch (Exception ex) {
			System.out.println("Exception in Handler.getLine:\n"+ex);
			return "";
		}
	}

}
