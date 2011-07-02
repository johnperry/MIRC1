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

import java.io.File;
import org.dcm4che.data.Command;
import org.dcm4che.dict.Status;

/**
  * An Event class used to signify Dicom SCP/SCU service events.
  * <p>
  * DicomEvent has two fields: Service and Status.  Both are read from
  * a Command object.
  * <p>
  * The service field is read from Command.getCommandField(), and corresponds
  * to the type of command this is, for instance Command.C_FIND_RSP
  * <p>
  * The status field is read from Command.getStatus(), and corresponds to the
  * current status of the service; for example, Status.SUCCESS
  *
  * @author <a href="mailto:maw@wuerl.wustl.edu">Matt Wyczalkowski</a>
  */

public class DicomEvent extends java.util.EventObject {
    int service;
    int status;
    String filename;
    String callingAET;
    String calledAET;

    /**
      * Creates DicomEvent of given service and status.
      * Service is field taken from Command.getCommandField and refers
      * to fields in Command class.  Status is value taken from
      * Command.getStatus and refers to fields in Status class.
      *
      * @param source The object on which the event initially occured.
      * @param service The type of service we are reporting.
      * @param status The status of the service.
      */
    protected DicomEvent(Object source, int service, int status) {
        super(source);
        this.service = service;
        this.status = status;
        this.filename = "";
        this.callingAET = "";
        this.calledAET = "";
    }

    /**
      * Creates DicomEvent of given service and status.
      * Service is field taken from Command.getCommandField and refers
      * to fields in Command class.  Status is value taken from
      * Command.getStatus and refers to fields in Status class.
      *
      * @param source The object on which the event initially occured.
      * @param service The type of service we are reporting.
      * @param status The status of the service.
      * @param filename The name of the file.
      */
    protected DicomEvent(Object source, int service, int status, String filename) {
        super(source);
        this.service = service;
        this.status = status;
        this.filename = filename;
        this.callingAET = "";
        this.calledAET = "";
    }

    /**
      * Creates DicomEvent of given service and status.
      * Service is field taken from Command.getCommandField and refers
      * to fields in Command class.  Status is value taken from
      * Command.getStatus and refers to fields in Status class.
      *
      * @param source The object on which the event initially occured.
      * @param service The type of service we are reporting.
      * @param status The status of the service.
      * @param filename The name of the file.
      */
    protected DicomEvent(Object source, int service, int status, String filename, String callingAET, String calledAET) {
        super(source);
        this.service = service;
        this.status = status;
        this.filename = filename;
        this.callingAET = callingAET;
        this.calledAET = calledAET;
    }

    /**
      * @return the string representation of this event.
      */
    public String toString() {
        String statusName = Status.toString(status);
        return "DicomEvent:  "+serviceAsString(service)+" ["+ statusName+"] "+filename;
    }

    /**
      * @return the string representation of this event without the file path.
      */
    public String toStringNoPath() {
        String statusName = Status.toString(status);
        String name = filename;
        if (!name.equals("")) name = new File(name).getName();
        return "DicomEvent:  "+serviceAsString(service)+" ["+ statusName+"] "+name;
    }

    /**
      * @return the service field of this event.
      */
    public int getService() {
        return service;
    }

    /**
      * @return the status field of this event.
      */
    public int getStatus() {
        return status;
    }

    /**
      * @return the filename field of this event.
      */
    public String getFilename() {
        return filename;
    }

    /**
      * @return the callingAET field of this event.
      */
    public String getCallingAET() {
        return callingAET;
    }

    /**
      * @return the calledAET field of this event.
      */
    public String getCalledAET() {
        return calledAET;
    }


// taken from CommandImpl.java
    public static String serviceAsString(int service) {
      switch (service) {
         case Command.C_STORE_RQ:
            return "C_STORE_RQ";
         case Command.C_GET_RQ:
            return "C_GET_RQ";
         case Command.C_FIND_RQ:
            return "C_FIND_RQ";
         case Command.C_MOVE_RQ:
            return "C_MOVE_RQ";
         case Command.C_ECHO_RQ:
            return "C_ECHO_RQ";
         case Command.N_EVENT_REPORT_RQ:
            return "N_EVENT_REPORT_RQ";
         case Command.N_GET_RQ:
            return "N_GET_RQ";
         case Command.N_SET_RQ:
            return "N_SET_RQ";
         case Command.N_ACTION_RQ:
            return "N_ACTION_RQ";
         case Command.N_CREATE_RQ:
            return "N_CREATE_RQ";
         case Command.N_DELETE_RQ:
            return "N_DELETE_RQ";
         case Command.C_CANCEL_RQ:
            return "C_CANCEL_RQ";
         case Command.C_STORE_RSP:
            return "C_STORE_RSP";
         case Command.C_GET_RSP:
            return "C_GET_RSP";
         case Command.C_FIND_RSP:
            return "C_FIND_RSP";
         case Command.C_MOVE_RSP:
            return "C_MOVE_RSP";
         case Command.C_ECHO_RSP:
            return "C_ECHO_RSP";
         case Command.N_EVENT_REPORT_RSP:
            return "N_EVENT_REPORT_RSP";
         case Command.N_GET_RSP:
            return "N_GET_RSP";
         case Command.N_SET_RSP:
            return "N_SET_RSP";
         case Command.N_ACTION_RSP:
            return "N_ACTION_RSP";
         case Command.N_CREATE_RSP:
            return "N_CREATE_RSP";
         case Command.N_DELETE_RSP:
            return "N_DELETE_RSP";
      }
      return "cmd:" + Integer.toHexString(service);
    }
}
