/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Enumeration;
import org.apache.log4j.Logger;

/**
 * Encapsulates a static method for getting the IP address.
 */
public class IPUtil {

	static final Logger logger = Logger.getLogger(IPUtil.class);

	static final String def = "127.0.0.1";

	/**
	 * Obtains the IP address of the host computer.
	 * Returns the localhost address if the operation fails.
	 * @return the IP Address string.
	 */
	public static String getIPAddress() {
		try {
			//Get all the network interfaces
			Enumeration<NetworkInterface> nwEnum = NetworkInterface.getNetworkInterfaces();

			//Return the first IPv4 address that is not a loopback address.
			while (nwEnum.hasMoreElements()) {
				NetworkInterface nw = nwEnum.nextElement();
				Enumeration<InetAddress> ipEnum = nw.getInetAddresses();
				while (ipEnum.hasMoreElements()) {
					InetAddress ina = ipEnum.nextElement();
					if ((ina instanceof Inet4Address) && !ina.isLoopbackAddress()) {
						return ina.getHostAddress();
					}
				}
			}
			logger.error("Unable to find a non-loopback address, returning "+def);
		}
		catch (Exception e) {
			logger.error("Unable to obtain the IP address, returning "+def,e);
		}
		return def;
	}

}

