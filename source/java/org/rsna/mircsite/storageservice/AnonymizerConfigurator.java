/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.storageservice;

import java.io.File;
import javax.servlet.*;
import javax.servlet.http.*;
import org.rsna.mircsite.dicomservice.TrialConfig;
import org.rsna.mircsite.mircservlets.AnonymizerUtil;

/**
 * The Anonymizer Configurator servlet for the DICOM Service.
 * <p>
 * This servlet simply extends the AnonymizerUtil servlet
 * so it can redirect the location of the anonymizer.properties
 * file.
 */
public class AnonymizerConfigurator extends AnonymizerUtil {

	/**
	 * Get a file pointing to the anonymizer properties file.
	 * This method gets the file from the basepath and
	 * anonymizerFilename in TrialConfig.
	 * @return the anonymizer properties file specified in TrialConfig.
	 */
	public File getPropertiesFile() {
		File root = new File(getServletContext().getRealPath("/"));
		return new File(
				TrialConfig.basepath + TrialConfig.dicomAnonymizerFilename);
	}

}
