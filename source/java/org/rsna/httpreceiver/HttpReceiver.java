/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.httpreceiver;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;

/**
 * A simple receiver for DICOM objects transmitted by MIRC
 * sites or FieldCenter applications using the HTTP(S) protocol.
 */
public class HttpReceiver {

	/**
	 * The main function. All the code of the HttpReceiver
	 * runs in this function. HttpReceiver is a server that
	 * receives files via HTTP(S) and writes them into a
	 * directory. It is intended for use by ACRIN in receiving 
	 * transmissions from the FieldCenter application (or 
	 * from the HttpExportService of MIRC sites),
	 * @param args command line arguments: 
	 * <ol>
	 * <li>protocol [https]</li>
	 * <li>port [8444]</li>
	 * <li>dir [{user.dir}/store]</li>
	 * <li>keystore [{user.dir}/keystore]</li>
	 * <li>password [httpreceiver]</li>
	 * </ol>
	 */
    public static void main(String args[]) {
		//Set the default port to an SSL-like value that
		//won't collide with Tomcat or FieldCenter, in 
		//case they are running.
		int port = 8444;
		
		//Set the default protocol
		String protocol = "https";
		
		//Set the default directories
		File userdir = new File(System.getProperty("user.dir"));
		File dir = new File(userdir,"store");
		File temp;

		//Set the default keystore parameters only if they are not already set.
		File keystore = new File(userdir,"keystore");
		String password = "httpreceiver";
		if (System.getProperty("javax.net.ssl.keyStore") == null) {
			System.setProperty("javax.net.ssl.keyStore",keystore.getAbsolutePath());
			System.setProperty("javax.net.ssl.keyStorePassword",password);
		}
		
		//Check for arguments
		if (args.length > 0) {
			//If it's a request for help, display the instructions
			if (args[0].toLowerCase().equals("help") || args[0].equals("?")) {
				System.out.println(helpText);
				System.exit(0);
			}
			
			//Get the protocol
			protocol = args[0].toLowerCase();
			
			//Get the port value if it is there
			try { port = Integer.parseInt(args[1]); }
			catch (Exception ignore) { }
			
			//Get the directory if it is there
			if (args.length > 2) dir = new File(args[2]);

			//Get the keystore if it is there
			if (args.length > 3) {
				keystore = new File(args[3]);
				System.setProperty("javax.net.ssl.keyStore",keystore.getAbsolutePath());
			}
			
			//Get the password if it is there
			if (args.length > 4) {
				System.setProperty("javax.net.ssl.keyStorePassword",args[4]);
			}
		}
		
		//Make sure the directory is there
		boolean ok = false;
		try {
			dir.mkdirs();
			ok = dir.exists();
		} catch (Exception e) { ok = false; }
		if (!ok) {
			System.out.println("Unable to access the store directory: " + dir);
			System.exit(0);
		}
		
		//Make a temp directory sibling to dir
		temp = new File(dir.getParentFile(),"HR-temp");	
		try {
			temp.mkdirs();
			ok = temp.exists();
		} catch (Exception e) { ok = false; }
		if (!ok) {
			System.out.println("Unable to access the temp directory: " + temp);
			System.exit(0);
		}
		
		//Now get the Server Socket with the specified protocol on the specified port
		ServerSocket serverSocket = null;
		ServerSocketFactory serverSocketFactory = null;
		try { 
			if (protocol.equals("http"))
				serverSocketFactory = ServerSocketFactory.getDefault();
			else
				serverSocketFactory = SSLServerSocketFactory.getDefault();
			serverSocket = serverSocketFactory.createServerSocket(port);
		}
		catch (Exception ex) {
			System.out.println("Unable to create ServerSocket ("+protocol+") on port " + port);
			System.out.println(ex);
			System.exit(0);
		}
			
		System.out.println("HttpReceiver ("+protocol+") is up on port " + port);

		//Accept connections and handle them
		while (true) {
			try {
				//Wait for a connection
				Socket socket = serverSocket.accept();

				//Log the connection
				System.out.println(
					"\nConnection from " + socket.getInetAddress() + ":" + socket.getPort());
				
				//Create a thread to handle the connection
				Handler handler = new Handler(dir,temp,socket);
				handler.start();
			}
			catch (Exception ex) { System.out.println(ex); }
		}
	}
	
    static String helpText = 
    	"HttpReceiver:\n" +
    	"  java -jar HttpReceiver protocol port dir keystore password\n" +
    	"\n" +
    	"where:\n" +
    	"  protocol is http or https (default = https)\n" +
    	"  port is the SSL port (default = 8444)\n" +
    	"  dir is the full path to the storage directory (default = {user.dir}/store)\n" +
    	"  keystore is the full path to the keystore (default = {user.dir}/keystore)\n" +
    	"  password is the keystore password (default = httpreceiver)\n";

}
