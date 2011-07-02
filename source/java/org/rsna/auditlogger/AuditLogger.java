/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.auditlogger;

import java.io.*;
import java.net.*;

/**
 * A simple auditlogger for receiving messages from a MIRCsite
 * audit message exporter and printing them on the console.
 * While this program is a test tool, it serves as an example
 * of the MIRC interface and might be used to build a version
 * that transfers audit messages on to a real audit logger.
 * All the code in this program runs in the main function.
 */
public class AuditLogger {

    public static void main(String args[]) {
		int port = 8181;
		if (args.length > 0) {
			try { port = Integer.parseInt(args[0]); }
			catch (Exception ignore) { }
		}

		ServerSocket serverSocket = null;
		try {
			serverSocket = new ServerSocket(port);
			serverSocket.setReuseAddress(true);
		}
		catch (Exception ex) {
			System.out.println("Could not create a ServerSocket on port " + port);
			System.exit(1);
		}

		System.out.println("The server is up on port " + port);

		String line;
		String address;
		Socket clientSocket = null;
		while (true) {
			try {
				clientSocket = serverSocket.accept();

				SocketAddress sa = clientSocket.getRemoteSocketAddress();
				if (sa instanceof InetSocketAddress)
					address = ((InetSocketAddress)sa).getAddress().getHostAddress();
				else
					address = "unknown";
				System.out.println("\nConnection from " + address);

				BufferedReader in = new BufferedReader(
										new InputStreamReader(clientSocket.getInputStream()));

				while ((line = in.readLine()) != null)
					System.out.println(line);

				in.close();
				clientSocket.close();
			}
			catch (Exception ex) { System.out.println(ex.toString()); }
		}
    }

}