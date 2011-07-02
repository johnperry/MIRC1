//
//        Copyright (C) 2002, RSNA and Washington University
//
//        The MIRC ACQ test tools software and supporting documentation were
//        developed for the Medical Imaging Resource Center project
//        (2002), under the sponsorship of
//        the Radiological Society of North America (RSNA) by:
//                Electronic Radiology Laboratory
//                Mallinckrodt Institute of Radiology
//                Washington University School of Medicine
//                510 S. Kingshighway Blvd.
//                St. Louis, MO 63110
//
//        THIS SOFTWARE IS MADE AVAILABLE, AS IS, AND NEITHER RSNA NOR
//        WASHINGTON UNIVERSITY MAKE ANY WARRANTY ABOUT THE SOFTWARE, ITS
//        PERFORMANCE, ITS MERCHANTABILITY OR FITNESS FOR ANY PARTICULAR
//        USE, FREEDOM FROM ANY DEFECTS OR COMPUTER DISEASES OR ITS CONFORMITY
//        TO ANY SPECIFICATION. THE ENTIRE RISK AS TO QUALITY AND PERFORMANCE OF
//        THE SOFTWARE IS WITH THE USER.
//
//        Copyright of the software and supporting documentation is
//        jointly owned by RSNA and Washington University, and free
//        access is hereby granted as a license to use this software, copy
//        this software and prepare derivative works based upon this software.
//        However, any distribution of this software source code or supporting
//        documentation or derivative works (source code and supporting
//        documentation) must include the three paragraphs of this copyright
//        notice.
//

package org.rsna.dicom;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Set;

import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmDecodeParam;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.DcmParser;
import org.dcm4che.data.DcmParserFactory;
import org.dcm4che.data.FileFormat;
import org.dcm4che.data.FileMetaInfo;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.Tags;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.Dimse;

import org.apache.log4j.Logger;

public class DicomStorageScp extends DcmRcv {
    static final Logger log = Logger.getLogger(DicomStorageScp.class);
    private HashSet listeners = new HashSet();
    protected String aeTitle;
    protected String port;

    // Constructor --------------------------------------------------
    DicomStorageScp(Configuration cfg) {
        super(cfg);
        aeTitle = cfg.getProperty("storage-scp-aet");
        port = cfg.getProperty("port");
    }

    // overrides DcmRcv.doCStore()
    protected void doCStore(ActiveAssociation assoc, Dimse rq, Command rspCmd)
            throws IOException {
        log.debug("doCStore called.");

        super.doCStore(assoc, rq, rspCmd);

        // If dir is not null and we are not writing to DICOM File-Set,
        // then the file has been written to disk with the AffectedSOPInstanceUID
        // as the filename. Sometimes this is a MediaStorageUID, and what we want
        // is for the file to be named by the SOPInstanceUID, so we will rename
        // it here, if necessary, before sending the event.
        if ((dir != null) && !("DICOMDIR".equals(dir.getName()))) {
            String affectedSOPInstanceUID = rq.getCommand().getAffectedSOPInstanceUID();
            File file = new File(dir, affectedSOPInstanceUID);
            String sopInstanceUID = getSOPInstanceUID(file);
            if (!sopInstanceUID.equals(affectedSOPInstanceUID)) {
            	file = changeFilename(file, sopInstanceUID);
			}
            fireDicomEvent(
					rq.getCommand().getCommandField(),
					Status.Success,
					file.getAbsolutePath(),
					assoc.getAssociation().getCallingAET(),
					assoc.getAssociation().getCalledAET() );
        }
        else fireDicomEvent(
					rq.getCommand().getCommandField(),
					Status.Success);
    }

    //Get the SOPInstanceUID for the file just stored.
    private String getSOPInstanceUID(File file) {
		BufferedInputStream in = null;
		String sopInstanceUID = null;
		try {
			in = new BufferedInputStream(new FileInputStream(file));
			DcmParser parser = pFact.newDcmParser(in);
			FileFormat fileFormat = parser.detectFileFormat();
			Dataset dataset = oFact.newDataset();
			parser.setDcmHandler(dataset.getDcmHandler());
			parser.parseDcmFile(fileFormat, Tags.SOPInstanceUID + 1);
			sopInstanceUID = dataset.getString(Tags.SOPInstanceUID);
		}
		catch (Exception ex) { }
		if (in != null) {
			try { in.close(); }
			catch (Exception ignore) { }
		}
		return sopInstanceUID;
	}

	//Rename a file
	private File changeFilename(File file, String newName) {
		File newFile = new File(file.getParentFile(), newName);
		file.renameTo(newFile);
		return newFile;
	}


 ///////////////////////////////////////////////////////////////////
 //   get/set stuff
 ///////////////////////////////////////////////////////////////////

    /**
      * Set the destination directory, where incoming data is written to.
      * @param dir the destination directory.
      */
    public void setDestinationDir(File dir) {
		this.dir = dir;
    }

    /**
      * Get the destination directory.
      * @return the destination directory.
      */
    public File getDestinationDir() {
		return dir;
    }

    /**
      * Obtain the local port that SCP is listening to.
      * @return The port this server is listening on.
      */
    public int getPort() {
		return Integer.parseInt(port);
    }

    /**
      * Obtain the Application Entity Title of this SCP.
      * @return The AETitle.
      */
    public String getAETitle() {
		return aeTitle;
    }

 ///////////////////////////////////////////////////////////////////
 //   Listener stuff
 ///////////////////////////////////////////////////////////////////

    /**
      * Register a DicomEventListener.
      * @param l The listener to register.
      */
    public void addDicomEventListener(DicomEventListener l) {
		listeners.add(l);
    }

    /**
      * Remove the given listener from the listener HashSet.
      * @param l The listener to remove.  No error if listener is not on list.
      */
    public void removeDicomEventListener(DicomEventListener l) {
		listeners.remove(l);
    }

    /**
      * @return Array of DicomEventListeners registered with this object.
      */
    public DicomEventListener[] getDicomEventListeners() {
		DicomEventListener[] ls = new DicomEventListener[listeners.size()];
		int j = 0;
		for (Iterator i = listeners.iterator(); i.hasNext(); )
			ls[j++] = (DicomEventListener) i.next();
		return ls;
    }

    /**
      * Fires a DicomEvent of given service and status.
      * The event is sent in the event dispatch thread,
      * making it safe to use the callback for GUI updates.
      * @param service taken from Command.getCommandField and refers
      *     to constant fields in Command class.
      * @param status taken from Command.getStatus and refers to fields
      *     in Status class.
      */
    public void fireDicomEvent(int service, int status) {
		fireDicomEvent(service, status, "", "", "");
	}
	public void fireDicomEvent(int service, int status, String filename, String callingAET, String calledAET) {
		if (listeners.isEmpty()) return;
		final DicomEvent e = new DicomEvent(this, service, status, filename, callingAET, calledAET);
		final Iterator iter = new ArrayList(listeners).iterator();

		Runnable fireEvents = new Runnable() {
			public void run() {
			    while (iter.hasNext()) {
					DicomEventListener listener = (DicomEventListener)iter.next();
					listener.dicomEventOccurred(e);
				}
			}
		};
		javax.swing.SwingUtilities.invokeLater(fireEvents);
    }
}
