package org.rsna.tomcat.realm;

import java.io.File;
import java.security.Principal;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.apache.catalina.realm.MemoryRealm;


/**
 * A little smarter implementation of tomcat's MemoryRealm.  This version checks the file periodically
 * for changes to user information, and incorperates those changes without requiring a restart of tomcat.  There
 * is a slight (about 1 minute) delay between the time you save a change, and the time that it will be recognized.
 *
 *
 * @author RBoden
 *
 */
public class SmartMemoryRealm extends MemoryRealm{

	private static long lastStart = System.currentTimeMillis();
	private static long aWhile = 5000;

	public Principal authenticate(String username, String credentials) {
		synchronized (this) {
			// Get the time a while ago
			long time = System.currentTimeMillis() - aWhile;
			//
			// Restart the realm if it has been more than a while
			// since the realm was restarted and the file has
			// been modified since the last time the realm was
			// started but more than a while ago.
			//
			if (lastStart < time) {
				File file = new File(getPathname());
				long lastModified = file.lastModified();
				if ((lastModified == 0) || ((lastModified > lastStart) && (lastModified < time))) {
					try {
						super.start();
						lastStart = System.currentTimeMillis();
					} catch( Exception ex ) {
						System.out.println("Exception occurred while starting the SmartMemoryRealm");
					}
				}
			}
		}
		return super.authenticate(username, credentials);
	}
}
