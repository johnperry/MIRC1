<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="docpath"/>
<xsl:param name="context"/>
<xsl:param name="servlet"/>
<xsl:param name="extrapath"/>

<xsl:template match="/MIRCdocument">
<html>
<head>
	<title>Author Service: Add Files</title>
	<link href="{$context}/add-files.css" rel="Stylesheet" type="text/css" media="all"/>
	<script src="{$context}/add-files.js">;</script>
</head>
<body>

<h1><xsl:value-of select="title"/></h1>
<xsl:apply-templates select="author/name"/>
<xsl:if test="author/name"><br/></xsl:if>
<p class="note"><b>Select images and files to add:</b></p>

<form target="_self"
		method="post"
		accept-charset="UTF-8"
		enctype="multipart/form-data"
		id="fileform">

<table border="0" align="center" width="80%">
	<tr>
		<td align="center">
			<input onchange="captureFile();"
					size="75%"
					name="selectedfile0"
					id="selectedfile0"
					type="file"/>
		</td>
	</tr>
	<tr>
		<td align="center">
			<input NAME="Button1" ID="Button1"
				value="Add these files to the document"
				onclick="submitForm();" type="button"/>
		</td>
	</tr>
	<tr><td align="center">
		<a href="#" onClick="javascript:window.close();">Close This Window</a>
	</td></tr>
</table>
<input type="hidden" name="doc" value="{$docpath}"/>
</form>
</body>
</html>
</xsl:template>

<xsl:template match="name">
	<p class="author">
		<xsl:value-of select="."/>
	</p>
</xsl:template>

</xsl:stylesheet>