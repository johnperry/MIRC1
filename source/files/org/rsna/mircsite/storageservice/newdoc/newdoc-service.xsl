<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="name"/>
<xsl:param name="affiliation"/>
<xsl:param name="contact"/>
<xsl:param name="username"/>
<xsl:param name="groups"/>
<xsl:param name="textext"/>
<xsl:param name="template"/>

<xsl:template match="/storage">
<html>
<head>
	<title>New Document Service: <xsl:value-of select="sitename"/></title>
	<link href="service.css" rel="Stylesheet" type="text/css" media="all"/>
	<script src="newdoc/newdoc-service.js">;</script>
</head>
<body>
<div class="closebox">
	<img src="/mirc/images/closebox.gif"
		 onclick="window.open('/mirc/query','_self');"
		 title="Return to the MIRC home page"/>
</div>

<form target="_blank" method="post" accept-charset="UTF-8" enctype="multipart/form-data" id="fileform">
<table border="0" align="center" width="80%">
	<tr>
		<td colspan="2">
			<h2>Basic Authoring Tool for <xsl:value-of select="sitename"/></h2>
		</td>
	</tr>
	<tr><td colspan="2"><hr/></td></tr>
	<tr><td colspan="2"><p><b><u>Step One</u>:</b> Choose a title:</p></td></tr>
	<tr>
		<td class="text-label">Title:</td>
		<td class="text-field">
			<input class="input-length" name="title" id="title">
				<xsl:attribute name="value">Untitled</xsl:attribute>
			</input>
		</td>
	</tr>
	<tr><td colspan="2"><hr/></td></tr>
	<tr><td colspan="2"><p><b><u>Step Two</u>:</b> Add author and owner information:</p></td></tr>
	<tr>
		<td class="text-label">Author's name:</td>
		<td class="text-field">
			<input class="input-length" name="name" id="name">
				<xsl:attribute name="value"><xsl:value-of select="$name"/></xsl:attribute>
			</input>
		</td>
	</tr>
	<tr>
		<td class="text-label">Author's affiliation:</td>
		<td class="text-field">
			<input class="input-length" name="affiliation" id="affiliation">
				<xsl:attribute name="value"><xsl:value-of select="$affiliation"/></xsl:attribute>
			</input>
		</td>
	</tr>
	<tr>
		<td class="text-label">Author's phone or email:</td>
		<td class="text-field">
			<input class="input-length" name="contact" id="contact">
				<xsl:attribute name="value"><xsl:value-of select="$contact"/></xsl:attribute>
			</input>
		</td>
	</tr>
	<tr>
		<td class="text-label">Owner's username:</td>
		<td class="text-field">
			<input class="input-length" name="username" id="username">
				<xsl:attribute name="value"><xsl:value-of select="$username"/></xsl:attribute>
			</input>
		</td>
	</tr>
	<tr><td colspan="2"><hr/></td></tr>
	<tr><td colspan="2"><p><b><u>Step Three</u>:</b> Fill in any desired section text:</p></td></tr>
	<tr>
		<td class="text-label" valign="top">Abstract:</td>
		<td class="text-field">
			<textarea class="input-length" name="abstract" id="abstract">
				<xsl:text> </xsl:text>
			</textarea>
			<input type="hidden" name="abstext" id="abstext"></input>
		</td>
	</tr>
	<xsl:for-each select="$template/MIRCdocument/section">
		<xsl:if test="(@heading != 'Files') and (@heading != 'Notes')">
			<tr>
				<td class="text-label" valign="top"><xsl:value-of select="@heading"/>:</td>
				<td class="text-field">
					<textarea class="input-length" name="section{position()}" id="section{position()}">
						<xsl:text> </xsl:text>
					</textarea>
					<input type="hidden" name="sectext{position()}" id="sectext{position()}">
					</input>
					<input type="hidden" name="secname{position()}" id="secname{position()}" value="{@heading}">
					</input>
				</td>
			</tr>
		</xsl:if>
	</xsl:for-each>
	<tr><td colspan="2"><hr/></td></tr>
	<tr><td colspan="2"><p><b><u>Step Four</u>:</b> Decide who can access the document:</p></td></tr>
	<tr>
		<td class="text-label" valign="top">Read privilege:</td>
		<td>
			<input type="radio" name="read" value="" checked="true">Private</input>
			<xsl:text>&#160;&#160;&#160;&#160;&#160;&#160;</xsl:text>
			<input type="radio" name="read" value="*">Public</input>
		</td>
	</tr>
	<tr><td colspan="2"><hr/></td></tr>
	<tr><td colspan="2"><p class="instruction"><b><u>Last Step</u>:</b> Insert any desired images and files:</p></td></tr>
	<tr>
		<td class="text-label" id="filelabel" valign="bottom">Include a file:</td>
		<td class="text-field">
			<input onchange="captureFile();" size="75" name="selectedfile0" id="selectedfile0" type="file"/>
		</td>
	</tr>
	<tr>
		<td colspan="2" align="center">
			<br/>
			<input NAME="Button1" ID="Button1" value="Create Teaching File Document"
					onclick="submitForm();" type="button"/>
		</td>
	</tr>

</table>
</form>
</body>
</html>
</xsl:template>

</xsl:stylesheet>