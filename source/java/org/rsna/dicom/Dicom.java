/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.dicom;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Enumeration;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

/**
 * Encapsulates static methods for configuring dcm4che
 * and obtaining an instance of a DICOM Storage SCP.
 */
public class Dicom {
    static final Logger log = Logger.getLogger(Dicom.class);
    public static final String configResource = "/Dicom.cfg";
    public static Configuration configuration = null;

    /**
      * Initialize the configuration.
      * @param properties the application properties.
      */
    public static void initialize(Properties properties) {
        Dicom.configuration = getConfiguration(properties);
    }

    /**
      * Retrieve the configuration from the configuration resource
      * and the overwrite it with the specified Properties object.
      * @param properties the application properties.
      * @return the configuration object.
      */
    public static Configuration getConfiguration(Properties properties) {
		Configuration conf =
			new Configuration(Dicom.class.getResource(configResource));
        if (properties != null) {
			String key, value;
			Enumeration e = properties.propertyNames();
			while (e.hasMoreElements()) {
				key = (String)e.nextElement();
				value = properties.getProperty(key);
				conf.setProperty(key,value);
			}
        }
        return conf;
    }

    /**
      * Get an instance of DicomStorageScp with current configuration.
      * Create an instance of the DicomStorageScp configured with the
      * configuration parameters from getConfiguration().
      * @return instance of DicomStorageScp.
      */
    public static DicomStorageScp getStorageScp() {
        return new DicomStorageScp(configuration);
    }

}


