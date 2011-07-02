package org.rsna.dicom;

/*
 * This module was modified for MIRC from the TIANI original in the following ways:
 * - add the module to the org.rsna.dicom package
 * - remove the main method
 * - remove the LONG_OPTS array
 * - remove the listConfiguration method
 * - remove the messages ResourceBundle (since there is no main to use it)
 * - change several private data and method declarations to non-private
 *     -- these data elements are identified by preceding empty comments
 * - change the exit method to log rather than write directly to stderr
 */

/*                                                                           *
 *  Copyright (c) 2002, 2003 by TIANI MEDGRAPH AG                            *
 *                                                                           *
 *  This file is part of dcm4che.                                            *
 *                                                                           *
 *  This library is free software; you can redistribute it and/or modify it  *
 *  under the terms of the GNU Lesser General Public License as published    *
 *  by the Free Software Foundation; either version 2 of the License, or     *
 *  (at your option) any later version.                                      *
 *                                                                           *
 *  This library is distributed in the hope that it will be useful, but      *
 *  WITHOUT ANY WARRANTY; without even the implied warranty of               *
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU        *
 *  Lesser General Public License for more details.                          *
 *                                                                           *
 *  You should have received a copy of the GNU Lesser General Public         *
 *  License along with this library; if not, write to the Free Software      *
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA  *
 */

import java.io.BufferedOutputStream;

import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import org.apache.log4j.Logger;
import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmDecodeParam;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmEncodeParam;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.DcmParser;
import org.dcm4che.data.DcmParserFactory;
import org.dcm4che.data.FileMetaInfo;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.dict.VRs;
import org.dcm4che.net.AcceptorPolicy;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.AssociationFactory;
import org.dcm4che.net.DcmServiceBase;
import org.dcm4che.net.DcmServiceRegistry;
import org.dcm4che.net.Dimse;
import org.dcm4che.server.DcmHandler;
import org.dcm4che.server.Server;
import org.dcm4che.server.ServerFactory;
import org.dcm4che.util.DcmProtocol;
import org.dcm4che.util.SSLContextAdapter;

import org.dcm4cheri.data.DcmObjectFactoryImpl;

/**
 * <description>
 *
 * @author     <a href="mailto:gunter@tiani.com">gunter zeilinger</a>
 * @version    $Revision: 1.3 $ $Date: 2010/01/24 18:05:05 $
 */
public class DcmRcv extends DcmServiceBase
{
    // Constants -----------------------------------------------------
    final static Logger log = Logger.getLogger(DcmRcv.class);

    // Attributes ----------------------------------------------------
    private final static ServerFactory srvFact = ServerFactory.getInstance();
    private final static AssociationFactory fact = AssociationFactory.getInstance();
/**/final static DcmParserFactory pFact = DcmParserFactory.getInstance();
/**/final static DcmObjectFactory oFact = DcmObjectFactory.getInstance();

    private SSLContextAdapter tls = null;
    private DcmProtocol protocol = DcmProtocol.DICOM;

    private Dataset overwrite = oFact.newDataset();
    private AcceptorPolicy policy = fact.newAcceptorPolicy();
    private DcmServiceRegistry services = fact.newDcmServiceRegistry();
    private DcmHandler handler = srvFact.newDcmHandler(policy, services);
    private Server server = srvFact.newServer(handler);
    private int bufferSize = 512;
/**/File dir = null;
    private DcmRcvFSU fsu = null;
    private long rspDelay = 0L;

    private static void set(Configuration cfg, String s)
    {
        int pos = s.indexOf(':');
        if (pos == -1) {
            cfg.put("set." + s, "");
        } else {
            cfg.put("set." + s.substring(0, pos), s.substring(pos + 1));
        }
    }

    // Constructors --------------------------------------------------
    DcmRcv(Configuration cfg)
    {
        rspDelay = Integer.parseInt(cfg.getProperty("rsp-delay", "0")) * 1000L;
        bufferSize = Integer.parseInt(
                cfg.getProperty("buf-len", "2048")) & 0xfffffffe;
        initServer(cfg);
        initDest(cfg);
        initTLS(cfg);
        initPolicy(cfg);
        initOverwrite(cfg);
    }

    // Public --------------------------------------------------------
    /**
     *  Description of the Method
     *
     * @exception  IOException               Description of the Exception
     */
    public void start() throws IOException
    {
        if (fsu != null) {
            new Thread(fsu).start();
        }
        server.start();
    }

    public void stop()
    {
				server.stop();
		}

    // DcmServiceBase overrides --------------------------------------
    /**
     *  Description of the Method
     *
     * @param  assoc            Description of the Parameter
     * @param  rq               Description of the Parameter
     * @param  rspCmd           Description of the Parameter
     * @exception  IOException  Description of the Exception
     */
    protected void doCStore(ActiveAssociation assoc, Dimse rq, Command rspCmd)
        throws IOException
    {
        InputStream in = rq.getDataAsStream();
        try {
            if (dir != null) {
                Command rqCmd = rq.getCommand();
                FileMetaInfo fmi = ((DcmObjectFactoryImpl)oFact).newFileMetaInfo(
                        rqCmd.getAffectedSOPClassUID(),
                        rqCmd.getAffectedSOPInstanceUID(),
                        rq.getTransferSyntaxUID());
                if (fsu == null) {
                    storeToDir(in, fmi);
                } else {
                    storeToFileset(in, fmi);
                }
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            in.close();
        }
        if (rspDelay > 0L) {
            try {
                Thread.sleep(rspDelay);
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
        }
        rspCmd.putUS(Tags.Status, Status.Success);
    }


    private OutputStream openOutputStream(File file)
        throws IOException
    {
        File parent = file.getParentFile();
        if (!parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IOException("Could not create " + parent);
            }
            log.debug("M-WRITE " + parent);
        }
        log.debug("M-WRITE " + file);
        return new BufferedOutputStream(new FileOutputStream(file));
    }


    private void storeToDir(InputStream in, FileMetaInfo fmi)
        throws IOException
    {
        OutputStream out = openOutputStream(
                new File(dir, fmi.getMediaStorageSOPInstanceUID()));
        try {
            fmi.write(out);
            copy(in, out, -1);
        } finally {
            try {
                out.close();
            } catch (IOException ignore) {}
        }
    }


    private void storeToFileset(InputStream in, FileMetaInfo fmi)
        throws IOException
    {
        Dataset ds = oFact.newDataset();
        DcmParser parser = pFact.newDcmParser(in);
        parser.setDcmHandler(ds.getDcmHandler());
        DcmDecodeParam decParam =
                DcmDecodeParam.valueOf(fmi.getTransferSyntaxUID());
        DcmEncodeParam encParam = (DcmEncodeParam) decParam;
        parser.parseDataset(decParam, Tags.PixelData);
        doOverwrite(ds);
        File file = fsu.toFile(ds);
        OutputStream out = openOutputStream(file);
        try {
            ds.setFileMetaInfo(fmi);
            ds.writeFile(out, (DcmEncodeParam) decParam);
            if (parser.getReadTag() < Tags.PixelData) {
                return;
            }
            int len = parser.getReadLength();
            ds.writeHeader(out, encParam,
                    parser.getReadTag(),
                    parser.getReadVR(),
                    len);
            if (len == -1) {
                parser.parseHeader();
                while (parser.getReadTag() == Tags.Item) {
                    len = parser.getReadLength();
                    ds.writeHeader(out, encParam, Tags.Item, VRs.NONE, len);
                    copy(in, out, len);
                    parser.parseHeader();
                }
                ds.writeHeader(
                        out,
                        encParam,
                        Tags.SeqDelimitationItem,
                        VRs.NONE,
                        0);
            } else {
                copy(in, out, len);
            }
            ds.clear();
            parser.parseDataset(decParam, -1);
            ds.writeDataset(out, encParam);
        } finally {
            try {
                out.close();
            } catch (IOException ignore) {}
        }
        fsu.schedule(file, ds);
    }

/**/void doOverwrite(Dataset ds)
    {
		Dataset overwrite = this.overwrite;
        for (Iterator it = overwrite.iterator(); it.hasNext(); ) {
            DcmElement el = (DcmElement) it.next();
            ds.putXX(el.tag(), el.vr(), el.getByteBuffer());
        }
    }

    // Package protected ---------------------------------------------

    // Protected -----------------------------------------------------

    // Private -------------------------------------------------------
    private void copy(InputStream in, OutputStream out, int totLen)
        throws IOException
    {
        int toRead = totLen == -1 ? Integer.MAX_VALUE : totLen;
        if (bufferSize > 0) {
            byte[] buffer = new byte[bufferSize];
            for (int len; toRead > 0; toRead -= len) {
                len = in.read(buffer, 0, Math.min(toRead, buffer.length));
                if (len == -1) {
                    if (totLen == -1) {
                        return;
                    }
                    throw new EOFException();
                }
                out.write(buffer, 0, len);
            }
        } else {
            for (int ch; toRead > 0; --toRead) {
                ch = in.read();
                if (ch == -1) {
                    if (totLen == -1) {
                        return;
                    }
                    throw new EOFException();
                }
                out.write(ch);
            }
        }
    }


    private static void exit(String prompt, boolean error)
    {
        if (prompt != null) log.info(prompt);
        if (error) log.error("exit");
        System.exit(1);
    }


    private final void initDest(Configuration cfg)
    {
        String dest = cfg.getProperty("dest", "", "<none>", "");
        if (dest.length() == 0) {
            return;
        }

        this.dir = new File(dest);
        if ("DICOMDIR".equals(dir.getName())) {
            this.fsu = new DcmRcvFSU(dir, cfg);
            handler.addAssociationListener(fsu);
            dir = dir.getParentFile();
        } else {
            if (!dir.exists()) {
                if (dir.mkdirs()) {
                    log.info("Created new directory: " +dir);
                } else {
                    exit("Failed to create directory: "+dest, true);
                }
            } else {
                if (!dir.isDirectory()) {
                    exit("Not a valid directory: "+dest, true);
                }
            }
        }
    }


    private void initServer(Configuration cfg)
    {
        server.setPort(
                Integer.parseInt(cfg.getProperty("port")));
        server.setMaxClients(
                Integer.parseInt(cfg.getProperty("max-clients", "10")));
        handler.setRqTimeout(
                Integer.parseInt(cfg.getProperty("rq-timeout", "5000")));
        handler.setDimseTimeout(
                Integer.parseInt(cfg.getProperty("dimse-timeout", "0")));
        handler.setSoCloseDelay(
                Integer.parseInt(cfg.getProperty("so-close-delay", "500")));
        handler.setPackPDVs(
                "true".equalsIgnoreCase(cfg.getProperty("pack-pdvs", "false")));
    }


    private void initPolicy(Configuration cfg)
    {
        policy.setCalledAETs(cfg.tokenize(
                cfg.getProperty("called-aets", null, "<any>", null)));
        policy.setCallingAETs(cfg.tokenize(
                cfg.getProperty("calling-aets", null, "<any>", null)));
        policy.setMaxPDULength(
                Integer.parseInt(cfg.getProperty("max-pdu-len", "16352")));
        policy.setAsyncOpsWindow(
                Integer.parseInt(cfg.getProperty("max-op-invoked", "0")), 1);
        for (Enumeration it = cfg.keys(); it.hasMoreElements(); ) {
            String key = (String) it.nextElement();
            if (key.startsWith("pc.")) {
                initPresContext(key.substring(3),
                        cfg.tokenize(cfg.getProperty(key)));
            }
        }
    }


    private void initPresContext(String asName, String[] tsNames)
    {
        String as = UIDs.forName(asName);
        String[] tsUIDs = new String[tsNames.length];
        for (int i = 0; i < tsUIDs.length; ++i) {
            tsUIDs[i] = UIDs.forName(tsNames[i]);
        }
        policy.putPresContext(as, tsUIDs);
        services.bind(as, this);
    }


/**/public void initOverwrite(Properties cfg)
    {
		Dataset overwrite = oFact.newDataset();
        for (Enumeration it = cfg.keys(); it.hasMoreElements(); ) {
            String key = (String) it.nextElement();
            if (key.startsWith("set.")) {
                try {
                    overwrite.putXX(Tags.forName(key.substring(4)),
                            cfg.getProperty(key));
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "Illegal entry in anonymization properties - "
                             + key + "=" + cfg.getProperty(key));
                }
            }
        }
        this.overwrite = overwrite;
    }


    private void initTLS(Configuration cfg)
    {
        try {
            this.protocol = DcmProtocol.valueOf(
                    cfg.getProperty("protocol", "dicom"));
            if (!protocol.isTLS()) {
                return;
            }

            tls = SSLContextAdapter.getInstance();
            char[] keypasswd = cfg.getProperty("tls-key-passwd", "passwd").toCharArray();
            tls.setKey(
                    tls.loadKeyStore(
                    DcmRcv.class.getResource(cfg.getProperty("tls-key", "identity.p12")),
                    keypasswd),
                    keypasswd);
            tls.setTrust(tls.loadKeyStore(
                    DcmRcv.class.getResource(cfg.getProperty("tls-cacerts", "cacerts.jks")),
                    cfg.getProperty("tls-cacerts-passwd", "passwd").toCharArray()));
            this.server.setServerSocketFactory(
                    tls.getServerSocketFactory(protocol.getCipherSuites()));
        } catch (Exception ex) {
            throw new RuntimeException("Could not initalize TLS configuration: ", ex);
        }
    }
}

