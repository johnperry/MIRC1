/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import java.util.EventListener;

/**
 * The interface for listeners to TransferEvents.
 */
public interface TransferListener extends EventListener {

	public void attention(TransferEvent event);

}
