/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*
*  Modified from code provided by Gunter Zeilinger.
*----------------------------------------------------------------*/

package org.rsna.dicom;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmDecodeParam;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmEncodeParam;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.DcmParseException;
import org.dcm4che.data.DcmParser;
import org.dcm4che.data.DcmParserFactory;
import org.dcm4che.data.FileFormat;
import org.dcm4che.dict.DictionaryFactory;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDDictionary;
import org.dcm4che.dict.UIDs;
import org.dcm4che.dict.VRs;
import org.dcm4che.net.AAssociateAC;
import org.dcm4che.net.AAssociateRQ;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.Association;
import org.dcm4che.net.AssociationFactory;
import org.dcm4che.net.DataSource;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.PDU;
import org.dcm4che.net.PresContext;
import org.dcm4che.util.DcmURL;

/**
 * A sender of DICOM objects via the DICOM protocol.
 */
public class DicomSender {

    private static final String[] DEF_TS = { UIDs.ImplicitVRLittleEndian };

	static final Logger log = Logger.getLogger(DicomSender.class);

    private static final UIDDictionary uidDict =
        DictionaryFactory.getInstance().getDefaultUIDDictionary();
    private static final AssociationFactory aFact =
        AssociationFactory.getInstance();
    private static final DcmObjectFactory oFact =
        DcmObjectFactory.getInstance();
    private static final DcmParserFactory pFact =
        DcmParserFactory.getInstance();

    private DcmURL url = null;
    private int priority = Command.MEDIUM;
    private int acTimeout = 15000;
    private int dimseTimeout = 0;
    private int soCloseDelay = 500;
    private int maxPDULength = 16352;
    private AAssociateRQ assocRQ = aFact.newAAssociateRQ();
    private boolean packPDVs = false;
    private int bufferSize = 2048;
    private byte[] buffer = null;
    private ActiveAssociation activeAssociation = null;

	/**
	 * Class constructor; creates a DICOM sender.
	 * @param host the destination DICOM Storage SCP IP address.
	 * @param port the destination DICOM Storage SCP port.
	 * @param calledAET the AE Title of the destination.
	 * @param callingAET the AE Title of the sender.
	 */
	public DicomSender(String host, int port, String calledAET, String callingAET) {
		this(new DcmURL("dicom",calledAET,callingAET,host,port));
	}

	/**
	 * Class constructor; creates a DICOM sender.
	 * @param url the URL in the form "dicom://calledAET:callingAET@host:port".
	 */
    public DicomSender(DcmURL url) {
        this.url = url;
        buffer = new byte[bufferSize];
        initAssocParam(url);
    }

	/**
	 * Send one file to the URL specified defined in the constructor.
	 * @param file the file to send.
	 */
    public int send(File file) throws Exception {
		return sendFile(file);
    }

    private ActiveAssociation openAssoc()
        throws IOException, GeneralSecurityException {
        Association assoc =
            aFact.newRequestor(newSocket(url.getHost(), url.getPort()));
        assoc.setAcTimeout(acTimeout);
        assoc.setDimseTimeout(dimseTimeout);
        assoc.setSoCloseDelay(soCloseDelay);
        assoc.setPackPDVs(packPDVs);

        PDU assocAC = assoc.connect(assocRQ);
        if (!(assocAC instanceof AAssociateAC)) {
            return null;
        }
        ActiveAssociation retval = aFact.newActiveAssociation(assoc, null);
        retval.start();
        return retval;
    }

    private int sendFile(File file) throws Exception {
        InputStream in = null;
        DcmParser parser = null;
        Dataset ds = null;
        try {
            try {
                in = new BufferedInputStream(new FileInputStream(file));
                parser = pFact.newDcmParser(in);
                FileFormat format = parser.detectFileFormat();
                if (format != null) {
                    ds = oFact.newDataset();
                    parser.setDcmHandler(ds.getDcmHandler());
                    parser.parseDcmFile(format, Tags.PixelData);
                    if (parser.getReadTag() == Tags.PixelData) {
                        if (parser.getStreamPosition() + parser.getReadLength() > file.length()) {
                            throw new EOFException(
                                "Pixel Data Length: " + parser.getReadLength()
                                + " exceeds file length: " + file.length());
                        }
                    }
                }
                else {
                    log.error("Illegal DICOM file format: " + file);
                    return -1;
                }
            } catch (IOException ex) {
                log.error("Unable to read the DICOM file: "+file, ex);
                return -1;
            }
            return sendDataset(file, parser, ds);
        } finally {
            if (in != null) {
                try { in.close(); }
                catch (IOException ignore) { };
            }
        }
    }

    private int sendDataset(
			File file,
			DcmParser parser,
			Dataset ds) throws Exception {

        String sopInstUID = ds.getString(Tags.SOPInstanceUID);
        if (sopInstUID == null) {
            log.error("Missing SOPInstanceUID in " + file);
            return -1;
        }
        String sopClassUID = ds.getString(Tags.SOPClassUID);
        if (sopClassUID == null) {
            log.error("Missing SOPClassUID in " + file);
            return -1;
        }
        initPresContext(sopClassUID);
        PresContext pc = null;

		ActiveAssociation active = openAssoc();
        Association assoc = active.getAssociation();
        String tsuid = ds.getFileMetaInfo().getTransferSyntaxUID();
        pc = assoc.getAcceptedPresContext(sopClassUID, tsuid);
        if (parser.getDcmDecodeParam().encapsulated) {
            if (pc == null) {
				log.error(
					"Unable to negotiate a Presentation Context for"
					+ "\n      "+ uidDict.lookup(sopClassUID)
					+ "\n      "+ uidDict.lookup(tsuid)
					+ "\n      in " + file);
                return -1;
            }
        }
        else {
			if (pc == null)
				pc = assoc.getAcceptedPresContext(sopClassUID, UIDs.ExplicitVRLittleEndian);
			if (pc == null)
				pc = assoc.getAcceptedPresContext(sopClassUID, UIDs.ExplicitVRBigEndian);
			if (pc == null)
				pc = assoc.getAcceptedPresContext(sopClassUID, UIDs.ImplicitVRLittleEndian);
		}
        if (pc == null) {
            log.error(
				"Unable to negotiate a Presentation Context for"
				+ "\n       "+ uidDict.lookup(sopClassUID)
				+ "\n       in " + file);
            return -1;
        }
        Command command = oFact.newCommand();
        command.initCStoreRQ(assoc.nextMsgID(), sopClassUID, sopInstUID, priority);
        Dimse request = aFact.newDimse(pc.pcid(), command, new MyDataSource(parser, ds, buffer));
        Dimse response = active.invoke(request).get();
		active.release(true);
		return response.getCommand().getStatus();
    }

    private final class MyDataSource implements DataSource {
        final DcmParser parser;
        final Dataset ds;
        final byte[] buffer;
        MyDataSource(DcmParser parser, Dataset ds, byte[] buffer) {
            this.parser = parser;
            this.ds = ds;
            this.buffer = buffer;
        }
        public void writeTo(OutputStream out, String tsUID) throws IOException {
            DcmEncodeParam netParam =
                (DcmEncodeParam) DcmDecodeParam.valueOf(tsUID);
			ds.writeDataset(out, netParam);
			DcmDecodeParam fileParam = parser.getDcmDecodeParam();
            if (parser.getReadTag() == Tags.PixelData) {
                ds.writeHeader(
                    out,
                    netParam,
                    parser.getReadTag(),
                    parser.getReadVR(),
                    parser.getReadLength());
                if (netParam.encapsulated) {
                    parser.parseHeader();
                    while (parser.getReadTag() == Tags.Item) {
                        ds.writeHeader(
                            out,
                            netParam,
                            parser.getReadTag(),
                            parser.getReadVR(),
                            parser.getReadLength());
                        writeValueTo(out, false);
                        parser.parseHeader();
                    }
                    if (parser.getReadTag() != Tags.SeqDelimitationItem) {
                        throw new DcmParseException(
                            "Unexpected Tag: " + Tags.toString(parser.getReadTag()));
                    }
                    if (parser.getReadLength() != 0) {
                        throw new DcmParseException(
                            "(fffe,e0dd), Length:" + parser.getReadLength());
                    }
                    ds.writeHeader(
                        out,
                        netParam,
                        Tags.SeqDelimitationItem,
                        VRs.NONE,
                        0);
                } else {
                    boolean swap =
                        fileParam.byteOrder != netParam.byteOrder
                            && parser.getReadVR() == VRs.OW;
                    writeValueTo(out, swap);
                }
				parser.parseHeader(); //get ready for the next element
			}
			//Now do any elements after the pixels one at a time.
			//This is done to allow streaming of large raw data elements
			//that occur above Tags.PixelData.
			boolean swap = fileParam.byteOrder != netParam.byteOrder;
			while (!parser.hasSeenEOF() && parser.getReadTag() != -1) {
				ds.writeHeader(
					out,
					netParam,
					parser.getReadTag(),
					parser.getReadVR(),
					parser.getReadLength());
				writeValueTo(out, swap);
				parser.parseHeader();
			}
        }

        private void writeValueTo(OutputStream out, boolean swap)
            throws IOException {
            InputStream in = parser.getInputStream();
            int len = parser.getReadLength();
            if (swap && (len & 1) != 0) {
                throw new DcmParseException(
                    "Illegal length for swapping value bytes: " + len);
            }
            if (buffer == null) {
                if (swap) {
                    int tmp;
                    for (int i = 0; i < len; ++i, ++i) {
                        tmp = in.read();
                        out.write(in.read());
                        out.write(tmp);
                    }
                } else {
                    for (int i = 0; i < len; ++i) {
                        out.write(in.read());
                    }
                }
            } else {
                byte tmp;
                int c, remain = len;
                while (remain > 0) {
                    c = in.read(buffer, 0, Math.min(buffer.length, remain));
                    if (c == -1) {
                        throw new EOFException("EOF while reading element value");
                    }
                    if (swap) {
                        if ((c & 1) != 0) {
                            buffer[c++] = (byte) in.read();
                        }
                        for (int i = 0; i < c; ++i, ++i) {
                            tmp = buffer[i];
                            buffer[i] = buffer[i + 1];
                            buffer[i + 1] = tmp;
                        }
                    }
                    out.write(buffer, 0, c);
                    remain -= c;
                }
            }
            parser.setStreamPosition(parser.getStreamPosition() + len);
        }
    }

    private Socket newSocket(String host, int port)
        throws IOException, GeneralSecurityException {
		return new Socket(host, port);
    }

    private static String maskNull(String aet) {
        return aet != null ? aet : "DCMSND";
    }

    private final void initAssocParam(DcmURL url) {
        assocRQ.setCalledAET(url.getCalledAET());
        assocRQ.setCallingAET(maskNull(url.getCallingAET()));
        assocRQ.setMaxPDULength(maxPDULength);
        assocRQ.setAsyncOpsWindow(aFact.newAsyncOpsWindow(0,1));
    }

    private final void initPresContext(String asUID) {
		try {
    		List tsList = pcTable.get(asUID);
			if (tsList != null) {
				int pcid = 1;
				Iterator it = tsList.iterator();
				String skip = (String) it.next();
				while (it.hasNext()) {
					String tsName = (String) it.next();
					String[] tsUID = new String[1];
					tsUID[0] = UIDs.forName(tsName);
					assocRQ.addPresContext(aFact.newPresContext(pcid, asUID, tsUID));
					pcid += 2;
				}
			}
		}
		catch (Exception ex) { }
    }

    static class PCTable extends Hashtable<String,LinkedList> {
		public PCTable() {
			super();
			for (int i=0; i<pcs.length; i++) {
				this.put(UIDs.forName(pcs[i].asName),pcs[i].list);
			}
		}
	}

	static class PC {
		public String asName;
		public LinkedList list;
		public PC(int pcid, String listString) {
			this.list = tokenize(listString, new LinkedList());
			this.asName = (String)list.get(0);
		}
		private static LinkedList tokenize(String s, LinkedList list) {
			StringTokenizer stk = new StringTokenizer(s, ", ");
			while (stk.hasMoreTokens()) {
				String tk = stk.nextToken();
				if (tk.equals("$ts-native")) tokenize(tsNative, list);
				else if (tk.equals("$ts-jpeglossless")) tokenize(tsJPEGLossless, list);
				else if (tk.equals("$ts-epd")) tokenize(tsEPD, list);
				else list.add(tk);
			}
			return list;
		}
	}

	static String tsJPEGLossless =
			"JPEGLossless,"+
			"JPEGLossless14";
	static String tsEPD =
			"$ts-jpeglossless,"+
			"JPEG2000Lossless,"+
			"JPEG2000Lossy,"+
			"JPEGExtended,"+
			"JPEGLSLossy,"+
			"RLELossless,"+
			"JPEGBaseline";
	static String tsNative =
			"ExplicitVRLittleEndian,"+
			"ImplicitVRLittleEndian";

	static PC[] pcs = {
		new PC(  1,"AmbulatoryECGWaveformStorage,$ts-native"),
		new PC(  3,"BasicStudyContentNotification,$ts-native"),
		new PC(  5,"BasicTextSR,$ts-native"),
		new PC(  7,"BasicVoiceAudioWaveformStorage,$ts-native"),
		new PC(  9,"CTImageStorage,$ts-epd,$ts-native"),
		new PC( 11,"CardiacElectrophysiologyWaveformStorage,$ts-native"),
		new PC( 13,"ComprehensiveSR,$ts-native"),
		new PC( 15,"ComputedRadiographyImageStorage,$ts-epd,$ts-native"),
		new PC( 17,"DigitalIntraoralXRayImageStorageForPresentation,$ts-jpeglossless,$ts-native"),
		new PC( 19,"DigitalIntraoralXRayImageStorageForProcessing,$ts-jpeglossless,$ts-native"),
		new PC( 21,"DigitalMammographyXRayImageStorageForPresentation,$ts-jpeglossless,$ts-native"),
		new PC( 23,"DigitalMammographyXRayImageStorageForProcessing,$ts-jpeglossless,$ts-native"),
		new PC( 25,"DigitalXRayImageStorageForPresentation,$ts-jpeglossless,$ts-native"),
		new PC( 27,"DigitalXRayImageStorageForProcessing,$ts-jpeglossless,$ts-native"),
		new PC( 29,"EncapsulatedPDFStorage,$ts-native"),
		new PC( 31,"EnhancedMRImageStorage,$ts-jpeglossless,$ts-native"),
		new PC( 33,"EnhancedSR,$ts-native"),
		new PC( 35,"GeneralECGWaveformStorage,$ts-native"),
		new PC( 37,"GrayscaleSoftcopyPresentationStateStorage,$ts-native"),
		new PC( 39,"HangingProtocolStorage,$ts-native"),
		new PC( 41,"HardcopyColorImageStorage,$ts-native"),
		new PC( 43,"HardcopyGrayscaleImageStorage,$ts-native"),
		new PC( 45,"HemodynamicWaveformStorage,$ts-native"),
		new PC( 47,"KeyObjectSelectionDocument,$ts-native"),
		new PC( 49,"MRImageStorage,$ts-epd,$ts-native"),
		new PC( 51,"MammographyCADSR,$ts-native"),
		new PC( 53,"MultiframeColorSecondaryCaptureImageStorage,$ts-jpeglossless,JPEGBaseline,$ts-native"),
		new PC( 55,"MultiframeGrayscaleByteSecondaryCaptureImageStorage,$ts-jpeglossless,JPEGBaseline,$ts-native"),
		new PC( 57,"MultiframeGrayscaleWordSecondaryCaptureImageStorage,$ts-jpeglossless,$ts-native"),
		new PC( 59,"MultiframeSingleBitSecondaryCaptureImageStorage,$ts-native"),
		new PC( 61,"NuclearMedicineImageStorage,$ts-jpeglossless,$ts-native"),
		new PC( 63,"NuclearMedicineImageStorageRetired,$ts-native"),
		new PC( 65,"PositronEmissionTomographyImageStorage,$ts-jpeglossless,$ts-native"),
		new PC( 67,"RTBeamsTreatmentRecordStorage,$ts-native"),
		new PC( 69,"RTBrachyTreatmentRecordStorage,$ts-native"),
		new PC( 71,"RTDoseStorage,$ts-native"),
		new PC( 73,"RTImageStorage,$ts-jpeglossless,$ts-native"),
		new PC( 75,"RTPlanStorage,$ts-native"),
		new PC( 77,"RTStructureSetStorage,$ts-native"),
		new PC( 79,"RTTreatmentSummaryRecordStorage,$ts-native"),
		new PC( 81,"RawDataStorage,$ts-native"),
		new PC( 83,"SecondaryCaptureImageStorage,$ts-epd,$ts-native"),
		new PC( 85,"TwelveLeadECGWaveformStorage,$ts-native"),
		new PC( 87,"UltrasoundImageStorage,$ts-epd,$ts-native"),
		new PC( 89,"UltrasoundImageStorageRetired,$ts-native"),
		new PC( 91,"UltrasoundMultiframeImageStorage,$ts-epd,$ts-native"),
		new PC( 93,"UltrasoundMultiframeImageStorageRetired,$ts-native"),
		new PC( 95,"VLEndoscopicImageStorage,$ts-jpeglossless,$ts-native"),
		new PC( 97,"VLImageStorageRetired,$ts-native"),
		new PC( 99,"VLMicroscopicImageStorage,$ts-jpeglossless,$ts-native"),
		new PC(101,"VLMultiframeImageStorageRetired,$ts-native"),
		new PC(103,"VLPhotographicImageStorage,$ts-jpeglossless,$ts-native"),
		new PC(105,"VLSlideCoordinatesMicroscopicImageStorage,$ts-jpeglossless,$ts-native"),
		new PC(107,"VideoEndoscopicImageStorage,MPEG2"),
		new PC(109,"VideoMicroscopicImageStorage,MPEG2"),
		new PC(111,"VideoPhotographicImageStorage,MPEG2"),
		new PC(113,"XRayAngiographicBiPlaneImageStorageRetired,$ts-native"),
		new PC(115,"XRayAngiographicImageStorage,$ts-jpeglossless,JPEGBaseline,$ts-native"),
		new PC(117,"XRayRadiofluoroscopicImageStorage,$ts-jpeglossless,$ts-native"),

		new PC(119,"SiemensCSANonImageStorage,$ts-native")
	};

	static PCTable pcTable = new PCTable();

}
