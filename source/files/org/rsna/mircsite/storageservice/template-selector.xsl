<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="name"/>
<xsl:param name="affiliation"/>
<xsl:param name="contact"/>

 <xsl:template match="*|@*|text()">
  <xsl:copy>
   <xsl:apply-templates select="*|@*|text()"/>
  </xsl:copy>
 </xsl:template>

 <xsl:template match="/storage">
  <xsl:choose>
   <xsl:when test="author-service/@enabled = 'yes'">

 <html>
 <head>
  <title>MIRCdocument Template Selection</title>
	<link href="service.css" rel="Stylesheet" type="text/css" media="all"/>
 </head>

 <body>
	<div class="closebox">
		<img src="/mirc/images/closebox.gif"
			 onclick="window.open('/mirc/query','_self');"
			 title="Return to the MIRC home page"/>
	</div>

  <form action="" method="POST" target="author" style="margin-top:0" accept-charset="UTF-8">
	<table width="80%" align="center">
		<tr>
			<td colspan="2">
				<h2>
					Advanced Authoring Tool for <xsl:value-of select="sitename"/>
				</h2>
			</td>
		</tr>
		<tr>
			<td colspan="2">
				<p><i>Use this page to update your personal information and to select a MIRCdocument template to begin writing a new document.</i></p>
			</td>
		</tr>
		<tr><td colspan="2"><hr/></td></tr>
		<tr>
			<td colspan="2">
				<p>Insert your name, affiliation, and contact information as they
				should appear under the title of the document.</p>
			</td>
		</tr>
		<tr>
			<td class="text-label">Author's name:</td>
			<td class="text-field">
			 <input class="input-length" name="name">
			  <xsl:attribute name="value"><xsl:value-of select="$name"/></xsl:attribute>
			 </input>
			</td>
		</tr>
		<tr>
			<td class="text-label">Author's affiliation:</td>
			<td class="text-field">
			 <input class="input-length" name="affiliation">
			  <xsl:attribute name="value"><xsl:value-of select="$affiliation"/></xsl:attribute>
			 </input>
			</td>
		</tr>
		<tr>
			<td class="text-label">Author's phone or email:</td>
			<td class="text-field">
			 <input class="input-length" name="contact">
			  <xsl:attribute name="value"><xsl:value-of select="$contact"/></xsl:attribute>
			 </input>
			</td>
		</tr>
		<tr><td colspan="2"><hr/></td></tr>
		<tr>
			<td colspan="2">
				<p>Select the template file to use for creating the new MIRCdocument.</p>
			</td>
		</tr>
		<tr>
			<td class="text-label">Template file:</td>
			<td class="text-field">
			   <select class="input-length"  name="template">
			    <xsl:for-each select="author-service/template">
			     <option>
			      <xsl:attribute name="value"><xsl:value-of select="@name"/></xsl:attribute>
			      <xsl:if test="position()=1">
			       <xsl:attribute name="selected"></xsl:attribute>
			      </xsl:if>
			      <xsl:value-of select="."/>
			     </option>
			    </xsl:for-each>
			   </select>
			</td>
		</tr>
		<tr>
			<td colspan="2" align="center">
				<br/>
				<input type="submit" value="Create the document and open it in the editor">
				</input>
			</td>
		</tr>
	</table>
  </form>
 </body>
 </html>

   </xsl:when>
   <xsl:otherwise>

 <html>
 <head>
  <title><xsl:value-of select="sitename"/></title>
 </head>
 <body style="margin:0;padding:8" scroll="no">
  <xsl:apply-templates select="author-service/pageheader"/>
  <h1><xsl:value-of select="sitename"/></h1>
  <p>The Web Authoring Tool is not enabled on this site.</p>
 </body>
 </html>

   </xsl:otherwise>
  </xsl:choose>
 </xsl:template>

 <xsl:template match="pageheader">
  <xsl:apply-templates select="*|@*|text()"/>
 </xsl:template>

</xsl:stylesheet>