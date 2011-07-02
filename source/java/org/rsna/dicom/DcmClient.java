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

package org.rsna.dicom;

import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmDecodeParam;
import org.dcm4che.data.DcmEncodeParam;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.DcmParser;
import org.dcm4che.data.DcmParserFactory;
import org.dcm4che.data.FileFormat;
import org.dcm4che.data.FileMetaInfo;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.Association;
import org.dcm4che.net.AAssociateAC;
import org.dcm4che.net.AAssociateRQ;
import org.dcm4che.net.AssociationFactory;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.DimseListener;
import org.dcm4che.net.FutureRSP;
import org.dcm4che.net.PDU;
import org.dcm4che.util.DcmURL;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.nio.ByteOrder;
import java.net.ProtocolException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.security.GeneralSecurityException;

import org.apache.log4j.Logger;

 /**
   * This class is meant to take care of the lower level Dcm CFind
   * and CMove (and CEcho) functionality.  Typically, query Datasets
   * are created at a higher level; this class takes care of the
   * associations, etc. and returns the query results.
   *
   * @author <a href="mailto:maw@wuerl.wustl.edu">Matt Wyczalkowski</a>
   */
public class DcmClient {
    static final Logger log = Logger.getLogger(DcmClient.class);

    private static final String[] DEF_TS = { UIDs.ImplicitVRLittleEndian };
    // The PCID's below are not quite right.  It is OK for them to
    // be static while we have only one type of possible PC; otherwise
    // they need to be unique.
    private static final int PCID_ECHO = 1;
    private static final int PCID_FIND = 1;
    private static final int PCID_MOVE = 1;

    private static final int C_ECHO = 101;
    private static final int C_FIND = 102;
    private static final int C_MOVE = 103;

    public static final int STUDY_LEVEL = 1;
    public static final int SERIES_LEVEL = 2;
    public static final int INSTANCE_LEVEL = 3;

    private static final String SOP_ECHO = UIDs.Verification;
    private static final String SOP_FIND =
        UIDs.StudyRootQueryRetrieveInformationModelFIND;
    private static final String SOP_MOVE =
        UIDs.StudyRootQueryRetrieveInformationModelMOVE;

    private static final AssociationFactory aFact =
        AssociationFactory.getInstance();
    private static final DcmObjectFactory oFact =
        DcmObjectFactory.getInstance();
    private static final DcmParserFactory pFact =
        DcmParserFactory.getInstance();

    private static final DcmEncodeParam preferredEncoding =
        new DcmEncodeParam(java.nio.ByteOrder.LITTLE_ENDIAN,
                false,   /*Explicit VR*/
                false,   /*deflated*/
                false,   /*encapsulated*/
                false,   /*skipGroupLen*/
                false,   /*undefSeqLen*/
                false);  /*undefItemLen*/

    private static int msgID = 0;
    /*
     * messageIDMap is where we keep per-messageID information.
     * Currently, we will store just the level of request (we only
     * care about CFind requests) in the value; this may be adjusted
     * in the future.
     * As we are not implementing asynchronous operations (where more
     * than one request is out at a time), and we delete the map entry
     * once all the requests are in, the size of messageIDMap should
     * never grow larger than one.
     */
    private HashMap messageIDMap = new HashMap(1);
    private static final int priority = 0;

    // Constructors --------------------------------------------------
    DcmClient() {
    }

    /**
      * Creates an association, as specified by the URL, and sends the
      * CFind request as specified by the dataset.
      * @param url The URL to connect to.  An association will be made
      *     and severed at conclusion of this method.
      * @param ds The complete dataset which will be sent.
      * @param listener The listener, if asynchronous transfer is requested.
      *     If listener is null, synchronous transfer is used, otherwise
      *     asynchronous transfer is used.
      * @return If synchronous, a List of Dimse objects; for asynchronous,
      *     null is returned.
      * @throws java.io.IOException Error occured when creating socket or
      *     connection or when reading from it.
      * @throws java.net.ProtocolException CFind not supported or Association
      *     could not be established.
      * @throws java.net.UnknownHostException The IP address of the host could not be
      *     determined.
      * @throws java.security.GeneralSecurityException Secuity manager
      *     disallows this operation.
      * @throws java.lang.InterruptedException Thread interrupted
      */
    public List sendCFind(DcmURL url, Dataset ds, DimseListener listener)
            throws IOException, GeneralSecurityException, InterruptedException {
        ActiveAssociation active =
            openAssoc(createAssociationRQ(C_FIND), url);

        if (active.getAssociation().getAcceptedTransferSyntaxUID(PCID_FIND)
                == null) {
            log.error("CFind Service not supported by remote DICOM node");
            throw new ProtocolException("CFind not supported");
        }
        List result = sendCFind(active, ds, listener);
        active.release(true);
        return result;
    }

    /**
      * Creates an association, as specified by the URL, and sends the
      * CMove request as specified by the dataset.
      * @param url The URL to connect to.  An association will be made
      *     and severed at conclusion of this method.
      * @param ds The complete dataset which will be sent.
      * @param listener The listener, if asynchronous transfer is requested.
      *     If listener is null, synchronous transfer is used, otherwise
      *     asynchronous transfer is used.
      * @return If synchronous, a List of Dimse objects; for asynchronous,
      *     null is returned.
      * @throws java.io.IOException Error occured when creating socket or
      *     connection or when reading from it.
      * @throws java.net.ProtocolException Association could not be established.
      * @throws java.net.UnknownHostException The IP address of the host could not be
      *     determined.
      * @throws java.security.GeneralSecurityException Secuity manager
      *     disallows this operation.
      * @throws java.lang.InterruptedException Thread interrupted
      */
    public List sendCMove(DcmURL url, Dataset ds, String moveDest, DimseListener listener)
            throws IOException, GeneralSecurityException, InterruptedException {
        ActiveAssociation active =
            openAssoc(createAssociationRQ(C_MOVE), url);

        if (active == null)
            return null;

        if (active.getAssociation().getAcceptedTransferSyntaxUID(PCID_MOVE)
                == null) {
            log.error("CMove Service not supported by remote DICOM node");
            return null;
        }
        List result = sendCMove(active, ds, moveDest, listener);
        active.release(true);
        return result;
    }


    /**
      * Sends a CFind request specified in the dataset to the open connection.
      * @param active The ActiveAssociation established with the server.
      * @param ds The complete dataset which will be sent.
      * @param listener The listener, if asynchronous transfer is requested.
      *     If listener is null, synchronous transfer is used, otherwise
      *     asynchronous transfer is used.
      * @return If synchronous, a List of Dimse objects; for asynchronous,
      *     null is returned.
      * @throws java.io.IOException Error reading.
      * @throws java.lang.InterruptedException Thread interrupted
      */
    public List sendCFind(ActiveAssociation active, Dataset ds, DimseListener listener)
            throws IOException, InterruptedException {

        Dimse cFindDimse = aFact.newDimse(
                PCID_FIND,
                oFact.newCommand().initCFindRQ(msgID, SOP_FIND, priority),
                ds);
        /*
         * Store the query level in the messageIDMap, and check to make
         * sure that there is no more than one entry in that map (since
         * we are not doing asynchronous transfer).
         */
        messageIDMap.put(new Integer(msgID), ds.getString(Tags.QueryRetrieveLevel));
        msgID++;
        if (messageIDMap.size() > 1)
            log.warn("messageIDMap has more than one entry.");

        if (listener == null)
            return getSynchronousResponse(cFindDimse, active);
        else {
            getAsynchronousResponse(cFindDimse, active, listener);
            return null;
        }
    }

    /**
      * Sends a CMove request specified in the dataset to the open connection
      * @param active The ActiveAssociation established with the server.
      * @param ds The complete dataset which will be sent.
      * @param listener The listener, if asynchronous transfer is requested.
      *     If listener is null, synchronous transfer is used, otherwise
      *     asynchronous transfer is used.
      * @return If synchronous, a List of Dimse objects; for asynchronous,
      *     null is returned.
      * @throws java.io.IOException Error reading.
      * @throws java.lang.InterruptedException Thread interrupted
      */
    public List sendCMove(ActiveAssociation active, Dataset ds,
            String moveDest, DimseListener listener) throws IOException,
            InterruptedException  {
        if (log.isDebugEnabled())
            Utils.writeDataset(ds, "CMove.dcm");

        Command moveCmd = oFact.newCommand().initCMoveRQ(msgID,
                SOP_MOVE, priority, moveDest);

        moveCmd.setMoveOriginator(active.getAssociation().getCallingAET(), msgID);
        msgID++;

        Dimse cMoveDimse = aFact.newDimse(PCID_MOVE, moveCmd, ds);

        if (listener == null)
            return getSynchronousResponse(cMoveDimse, active);
        else {
            getAsynchronousResponse(cMoveDimse, active, listener);
            return null;
        }
    }

    /**
      * Sends the request, then blocks until response comes back.
      * Returns a List of Dimse elements of the response.
      * @throws java.IO.IOException Error reading over network or something.
      * @throws java.lang.InterruptedException Thread interrupted
      */
    private List getSynchronousResponse(Dimse request,
            ActiveAssociation active) throws IOException, InterruptedException {

        log.debug("Requesting synchronous response.");
        // invoke() and get() both throw InterruptedException, IOException
        FutureRSP future = active.invoke(request);
        Dimse finalRsp = future.get(); // will block until after final CFindRSP
        // finalRsp is discarded...

        return future.listPending();
    }

    /**
      * Sends the request, and returns immediately.  Messages are sent to
      * given listener.
      * @param request The object describing the request.
      * @param active The Active association will be sending over.
      * @param l The DimseListener which will be informed of Dimse callbacks.
      * @throws java.IO.IOException Error reading over network or something.
      * @throws java.lang.InterruptedException Thread interrupted
      */
    private void getAsynchronousResponse(Dimse request,
            ActiveAssociation active, DimseListener l) throws IOException,
            InterruptedException {
        log.debug("Requesting asynchronous response.");
        active.invoke(request, l);
    }

    /**
      * Return the object associated with this message ID.
      * Returns the object (typically a string indicating the level of the
      * query) that was associated with the command of this message ID
      * at the time it was created.
      * @param messageID The ID of the message.
      * @return Object associated with the command, or null if nothing associated
      *     with this message ID.
      */
    protected Object getMessageInfo(int messageID) {
        Integer mid = new Integer(messageID);
        if (!messageIDMap.containsKey(mid)) {
            log.warn("No message associated with ID "+messageID);
            return null;
        }
        return messageIDMap.get(mid);
    }

    /**
      * Deletes the message entry associated with this messageID.
      * This should be done once all of the responses for this particular
      * command have come in.  If there is no message associated with this
      * ID then a warning is issued.
      * @param messageID the ID of the message
      */
    protected void removeMessageInfo(int messageID) {
        Integer mid = new Integer(messageID);
        if (!messageIDMap.containsKey(mid))
            log.warn("No message associated with ID "+messageID);
        else
            messageIDMap.remove(mid);
    }

    /**
      * Creates an active association with the given Association Request to the
      * host and port given by the Url.
      * @param assocRQ The association Request.
      * @param url The URL of the destination host.
      * @throws java.io.IOException Error occured when creating socket or connection.
      * @throws java.net.ProtocolException Association could not be established.
      * @throws java.net.UnknownHostException The IP address of the host could not be
      *     determined.
      * @throws java.security.SecurityException If Security Manager exists and
      *     doesn't allow Connect operation
      * @return The ActiveAssociation object.  This should not be null even if there is
      *      an error (exceptions thrown instead).
      */
    ActiveAssociation openAssoc(AAssociateRQ assocRQ, DcmURL url)
            throws IOException, GeneralSecurityException {

        if (assocRQ == null) {
            log.error("Active Association Request is null.");
            throw new IllegalArgumentException("Active Association is null");
        }

        assocRQ.setCalledAET(url.getCalledAET());
        assocRQ.setCallingAET(url.getCallingAET());

        Association assoc = null;
        if (log.isDebugEnabled())
            log.debug("Opening Association to "+url);
        /* Socket constructor may throw:
           UnknownHostException - if the IP address of the host could not be determined.
                (IOException subclass)
           IOException - if an I/O error occurs when creating the socket.
           SecurityException - if a security manager exists and its checkConnect
                method doesn't allow the operation.
        */
        assoc = aFact.newRequestor(
            new Socket(url.getHost(), url.getPort()));

        PDU assocAC = assoc.connect(assocRQ); // Throws IOException
        if (!(assocAC instanceof AAssociateAC)) {
            log.warn("Association not accepted.");
            // subclass of IOException
            throw new ProtocolException("Association Not Accepted.");
        }
        ActiveAssociation activeAssoc = aFact.newActiveAssociation(assoc, null);
        activeAssoc.start();
        return activeAssoc;
    }

    /**
      * Create an association request for given service.
      * @param service One of C_ECHO, C_FIND or C_MOVE.
      * @return The Association Request object.  If service is unknown,
      *     IllegalArgumentException (a Runtime exception) is thrown.
      */
    AAssociateRQ createAssociationRQ(int service) {
        AAssociateRQ assocRQ = aFact.newAAssociateRQ();

        // The values below, and others, should ultimately be specified
        // by the configuration.
        assocRQ.setMaxPDULength(16352);
        assocRQ.setAsyncOpsWindow(null);
        switch (service) {
            case C_ECHO:
                assocRQ.addPresContext(
                    aFact.newPresContext(PCID_ECHO, SOP_ECHO, DEF_TS));
                return assocRQ;
            case C_FIND:
                assocRQ.addPresContext(
                    aFact.newPresContext(PCID_FIND, SOP_FIND, DEF_TS));
                return assocRQ;
            case C_MOVE:
                assocRQ.addPresContext(
                    aFact.newPresContext(PCID_MOVE, SOP_MOVE, DEF_TS));
                return assocRQ;
            default: log.error("Unknown service "+service);
        }
        throw new IllegalArgumentException("Unknown service");
    }

    /**
      * Utility functions for reading and writing datasets to disk.
      */
    public static class Utils {
        protected static final DcmEncodeParam preferredEncoding =
                DcmDecodeParam.EVR_LE;
        protected static final String prefEncodingUID = UIDs.ExplicitVRLittleEndian;

        /**
          * Read a dataset on disk.
          * @param filename The name of the file to read.
          * @return The Dataset contained in the file.
          * @throws java.io.IOException Thrown if there are errors reading file,
          *     or if file is in unrecognized data format (not Dicom).
          */
        public static Dataset loadDataset(String filename) throws IOException {
            File src = new File(filename);
            BufferedInputStream in =
                    new BufferedInputStream(new FileInputStream(src));

            DcmParser parser = pFact.newDcmParser(in);
            FileFormat fileFormat = parser.detectFileFormat();
            if (fileFormat == null) {
                throw new IOException("Unrecognized file format of file "+src);
            }
            Dataset ds = oFact.newDataset();
            parser.setDcmHandler(ds.getDcmHandler());
            parser.parseDcmFile(fileFormat, -1);
            in.close();
            return ds;
        }

        /**
          * Write the dataset to file of given name.
          * The encoding is Explicit VR Little Endian.
          * @param ds Dataset to write.
          * @param filename The name of the file to write to.
          * @throws java.io.IOException Error writing file.
          */
        public static void writeDataset(Dataset ds, String filename)
                throws IOException {
            File f = new File(filename);
            writeDataset(ds, f);
        }

        /**
          * Write the dataset to given file.
          * The encoding is Explicit VR Little Endian.
          * @param ds Dataset to write.
          * @param file The file to write to.
          * @throws java.io.IOException Error writing file.
          */
        public static void writeDataset(Dataset ds, File file)
                throws IOException {
            FileOutputStream fos = new FileOutputStream(file);

            // We will be writing according to the preferred encoding.
            // To do that, we make sure that the file metainfo reflects
            // the correct encoding.
            FileMetaInfo fmi = ds.getFileMetaInfo();
            if (fmi != null)
                fmi.putXX(Tags.TransferSyntaxUID, prefEncodingUID);
            else
                fmi = oFact.newFileMetaInfo(ds, prefEncodingUID);

            ds.setFileMetaInfo(fmi);

            log.debug("Writing "+file.getName()+" ("+preferredEncoding+")");
            ds.writeFile(fos, preferredEncoding);
            fos.close();
        }
    }

}
