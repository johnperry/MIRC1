//
//        Portions Copyright (C) 2002, RSNA and Washington University
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

/*****************************************************************************
 *                                                                           *
 *  Portions Copyright (c) 2002 by TIANI MEDGRAPH AG                         *
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
 *                                                                           *
 *****************************************************************************/
package org.rsna.dicom;

import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * This class is borrowed from the dcm4che class of the same name.
 *
 * @author  <a href="mailto:gunter@tiani.com">gunter zeilinger</a>
 */
class Configuration extends Properties
{
   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------
   private static String replace(String val, String from, String to) {
      return from.equals(val) ? to : val;
   }

   // Constructors --------------------------------------------------
   public Configuration(URL url) {
      InputStream in = null;
      try {
         load(in = url.openStream());
      } catch (Exception e) {
         throw new RuntimeException("Could not load configuration from "
               + url, e);
      } finally {
         if (in != null) {
            try { in.close(); } catch (IOException ignore) {}
         }
      }
   }

   public Configuration() {
       super();
   }

   // Public --------------------------------------------------------
   public String getProperty(String key, String defaultValue,
                             String replace, String to) {
      return replace(getProperty(key, defaultValue), replace, to);
   }

   public List tokenize(String s, List result) {
      StringTokenizer stk = new StringTokenizer(s, ", ");
      while (stk.hasMoreTokens()) {
         String tk = stk.nextToken();
         if (tk.startsWith("$")) {
            tokenize(getProperty(tk.substring(1),""), result);
         } else {
            result.add(tk);
         }
      }
      return result;
   }

   public String[] tokenize(String s) {
      if (s == null)
         return null;

      List l = tokenize(s, new LinkedList());
      return (String[])l.toArray(new String[l.size()]);
   }
}
