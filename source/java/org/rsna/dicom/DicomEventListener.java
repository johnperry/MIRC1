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

/**
  * A listener class to be implemented by anyone interested in receiving DicomEvents.
  *
  * @author <a href="mailto:maw@wuerl.wustl.edu">Matt Wyczalkowski</a>
  */
public interface DicomEventListener extends java.util.EventListener {
    public void dicomEventOccurred(DicomEvent event);
}
