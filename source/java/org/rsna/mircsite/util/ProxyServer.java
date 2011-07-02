/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.util;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.BorderFactory;
import javax.swing.border.*;
import sun.misc.*;

/**
 * A class for managing proxy server parameters. This class provides
 * methods for enabling and disabling the use of a proxy server,
 * for setting the IP address and port of the proxy server, and for
 * specifying the authentication credentials, if any, required by the
 * proxy server.
 */
public class ProxyServer {

	Properties props;

	/** The property name of the flag indicating whether the Proxy Server is enabled. */
	public static final String proxyEnabledProp = "proxy-enabled";
	/** The property name of the Proxy Server IP address. */
	public static final String proxyIPAddressProp = "proxy-ipaddress";
	/** The property name of the Proxy Server port. */
	public static final String proxyPortProp = "proxy-port";
	/** The property name of the Proxy Server username. */
	public static final String proxyUsernameProp = "proxy-username";
	/** The property name of the Proxy Server password. */
	public static final String proxyPasswordProp = "proxy-password";

	/**
	 * Class constructor; creates an instance of the ProxyServer class
	 * from a properties object, using the defined names for the properties.
	 */
	public ProxyServer(Properties props) {
		if (props == null)
			this.props = new Properties();
		else
			this.props = props;
	}

	/**
	 * Class constructor; creates an instance of the ProxyServer class
	 * from individual parameters.
	 * @param proxyEnabled true if the proxy server is enabled; false otherwise.
	 * @param proxyIPAddress the IP address of the proxy server (nnn.nnn.nnn.nnn).
	 * @param proxyPort the port of the proxy server.
	 * @param proxyUsername the username for authentication on the proxy server.
	 * @param proxyPassword the password for authentication on the proxy server.
	 */
	public ProxyServer(
				boolean proxyEnabled,
				String proxyIPAddress,
				String proxyPort,
				String proxyUsername,
				String proxyPassword) {
		this.props = new Properties();
		props.setProperty(proxyEnabledProp, (proxyEnabled ? "true" : "false"));
		props.setProperty(proxyIPAddressProp, proxyIPAddress);
		props.setProperty(proxyPortProp, proxyPort);
		props.setProperty(proxyUsernameProp, ((proxyUsername != null) ? proxyUsername : ""));
		props.setProperty(proxyPasswordProp, ((proxyPassword != null) ? proxyPassword : ""));
	}

	/**
	 * Get the status of the proxy server checkbox.
	 * @return true if the proxy server is enabled; false otherwise.
	 */
	public boolean getProxyEnabled() {
		String enb = props.getProperty(proxyEnabledProp);
		return (enb != null) && enb.equals("true");
	}

	/**
	 * Get the proxy server IP address.
	 * @return the contents of the proxy server IP address property.
	 */
	public String getProxyIP() {
		String ip = props.getProperty(proxyIPAddressProp);
		return (ip != null) ? ip.trim() : "";
	}

	/**
	 * Get the contents of the proxy server Port text field.
	 * @return the contents of the proxy server Port text field.
	 */
	public String getProxyPort() {
		String port = props.getProperty(proxyPortProp);
		return (port != null) ? port.trim() : "";
	}

	/**
	 * Get the contents of the proxy server User text field.
	 * @return the contents of the proxy server User text field.
	 */
	public String getProxyUser() {
		String user = props.getProperty(proxyUsernameProp);
		return (user != null) ? user.trim() : "";
	}

	/**
	 * Get the contents of the proxy server Password text field.
	 * @return the contents of the proxy server Password text field.
	 */
	public String getProxyPassword() {
		String password = props.getProperty(proxyPasswordProp);
		return (password != null) ? password.trim() : "";
	}

	/**
	 * Set the Java System properties that apply to the proxy server.
	 * The properties set are:
	 * <ul>
	 *  <li>proxySet = true</li>
	 *  <li>http.proxyHost = getProxyIP()</li>
	 *  <li>http.proxyPort = getProxyPort()</li>
	 * </ul>
	 */
	public void setProxyProperties() {
		System.setProperty("proxySet","true");
		System.setProperty("http.proxyHost",getProxyIP());
		System.setProperty("http.proxyPort",getProxyPort());
	}

	/**
	 * Clear the Java System properties that apply to the proxy server.
	 * The properties cleared are:
	 * <ul>
	 *  <li>proxySet</li>
	 *  <li>http.proxyHost</li>
	 *  <li>http.proxyPort</li>
	 * </ul>
	 */
	public void clearProxyProperties() {
		Properties sys = System.getProperties();
		sys.remove("proxySet");
		sys.remove("http.proxyHost");
		sys.remove("http.proxyPort");
	}

	/**
	 * Determine whether the current values in the properties indicate
	 * that proxy user authentication is to be used.
	 * @return true if both Proxy User and Proxy Password are not blank; false otherwise.
	 */
	public boolean authenticate() {
		if (!getProxyUser().equals("") && !getProxyPassword().equals("")) return true;
		return false;
	}

	/**
	 * Get the base-64 encoded value of the proxy user authentication credentials
	 * in the form required for an HTTP Proxy-Authorization header:
	 * <ul><li>
	 * encode((getProxyUser() + ":" + getProxyPassword()).getBytes())
	 * </li></ul>
	 * @return the user authentication credentials from the user interface.
	 */
	public String getEncodedProxyCredentials() {
		BASE64Encoder encoder = new BASE64Encoder();
		return encoder.encode((getProxyUser() + ":" + getProxyPassword()).getBytes());
	}

}
