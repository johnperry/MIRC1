/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.mircadmin;

import org.apache.log4j.Logger;

/**
 * An agressive garbage collector.
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class GarbageCollector {

	static final Logger logger = Logger.getLogger(GarbageCollector.class);

	/**
	 * Runs the system garbage collector up to 100 times until the used memory
	 * stabilizes. If the logGC boolean in the Controller is true, it logs
	 * the used memory at the beginning and the end.
	 *<p>
	 * This method was implemented to ensure that when the memory-resident index
	 * of MIRCdocuments is loaded, there is the maximum amount of memory available.
	 */
	public static void collect() {
		int i;
		long usedMemory1 = usedMemory();
		long usedMemory2 = Long.MAX_VALUE;

		if (Controller.logGC) logger.info("UsedMemory at start = "+usedMemory1);

		for (i=0; (usedMemory1 < usedMemory2) && (i < 100); i++) {
			runtime.runFinalization();
			runtime.gc();
			Thread.currentThread().yield();
			usedMemory2 = usedMemory1;
			usedMemory1 = usedMemory();
		}

		if (Controller.logGC) logger.info("UsedMemory at end  = "+usedMemory1 + " ["+i+" runs]");
	}

	private static final Runtime runtime = Runtime.getRuntime();

	private static long usedMemory() {
		return runtime.totalMemory() - runtime.freeMemory();
	}

}