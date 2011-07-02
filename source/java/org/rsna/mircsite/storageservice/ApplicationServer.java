/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.storageservice;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.apache.log4j.Logger;
import org.rsna.mircsite.dicomservice.*;
import org.rsna.mircsite.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The Web Start Application Server servlet. This servlet provides access
 * to Java web start applications for viewing images and serves images to
 * them in any of several formats.
 * <p>
 * This servlet responds to HTTP GET.
 * <p>
 * See the <a href="http://mircwiki.rsna.org">MIRC wiki</a> for more more information.
 */
public class ApplicationServer extends HttpServlet {

	static final Logger logger = Logger.getLogger(ApplicationServer.class);

	/**
	 * The servlet method that responds to an HTTP GET. All accesses
	 * to MIRCdocument XML objects are tested for authorization. If
	 * an access requires authentication, the browser is redirected to
	 * the Login servlet with a return path that re-initiates the
	 * access after authentication.
	 * <p>
	 * The servlet responds to two paths:
	 * <ul>
	 * <li>/webstart/app?name=appname&md=mircdocument&img=imagepath
	 * <br>This URL searches for a file called appname.xsl and runs it with
	 * the md and img params. If appname.xsl is not present, it runs default-viewer.xsl
	 * with all three params.
	 * <li>/webstart/get?img=imgpath&fmt=imageformat&zip
	 * <br>This URL downloads the image identified by the specified relative path from the
	 * root of the webapp (e.g., documents/123212321/1.2.3.4.dcm) in the specified format.
	 * Supported formats are jpeg, dcm, jpeg2000, bi (Java BufferedImage). If the optional
	 * fmt prameter is specified, the selected image is converted to the format, within
	 * these constraints: JPEG, TIFF, and DICOM images can be converted to JPEG2000, JPEG or
	 * BufferedImage. If the optional zip parameter is specified, the selected image, after
	 * conversion if necessary, is served in a zip stream.
	 */
	public void doGet(
			HttpServletRequest req,
			HttpServletResponse res
			) throws IOException, ServletException {

		try { req.setCharacterEncoding("UTF-8"); }
		catch (Exception ignore) { }

		//Get the URL of the Server
		String serverURL = req.getRequestURL().toString();
		String contextPath = req.getContextPath();
		serverURL = serverURL.substring(0,serverURL.indexOf(contextPath));

		//Now get rid of the leading slash in the contextPath.
		//This is just done to make the construction of the
		//complete URL clearer in the XSL transform.
		if (contextPath.startsWith("/"))
			contextPath = contextPath.substring(1);

		//Now get the rest of the path information.
		//Note: two paths map to the servlet:
		//    /webstart/app
		//    /webstart/get
		String servletPath = req.getServletPath();

		//See if this is a request to load an application
		if (servletPath.equals("/webstart/app")) {

			//Yes, get the required parameters
			String appName = req.getParameter("name");
			String mdPath = req.getParameter("md");
			String imgPath = req.getParameter("img");
			if ((appName==null) || (mdPath==null) || (imgPath==null)) {
				res.sendError(res.SC_NOT_FOUND);
				return;
			}

			try {
				//Get the transform file
				String appPath = "webstart/" + appName + "/" + appName + ".xsl";
				File xslFile = new File(getServletContext().getRealPath(appPath));

				//Get the MIRCdocument
				File mdFile = new File(getServletContext().getRealPath(mdPath));
				Document mdDoc = XmlUtil.getDocument(mdFile);

				//Verify that the user has access to the document
				if (!XMLServer.userIsAuthorizedTo("read", mdDoc, req)) {
					res.sendError(res.SC_NOT_FOUND);
					return;
				}

				//Okay, now do the transform and get the jnlp document.
				Object[] params = {
					"server-url",	serverURL,
					"context",		contextPath,
					"app-name",		appName,
					"md-path",		mdPath,
					"img-path",		imgPath,
				};
				Document jnlp = XmlUtil.getTransformedDocument(mdDoc, xslFile, params);

				//Send the jnlp
				String contentType = "application/x-java-jnlp-file";
				res.setContentType(contentType + "; charset=\"UTF-8\"");
				PrintWriter out = res.getWriter();
				out.print(XmlUtil.toString(jnlp));
				out.flush();
				out.close();
			}
			catch (Exception e) {
				res.sendError(res.SC_NOT_FOUND);
			}
		}

		//See if this is a request for a resource.
		else if (servletPath.equals("/webstart/get")) {
			res.sendError(res.SC_NOT_FOUND); // temporary *********************
		}

		//Unknown resource request.
		else res.sendError(res.SC_NOT_FOUND);
	}

}