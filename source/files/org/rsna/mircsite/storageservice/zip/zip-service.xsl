<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="sitename"/>
<xsl:param name="name"/>
<xsl:param name="affiliation"/>
<xsl:param name="contact"/>
<xsl:param name="username"/>
<xsl:param name="read"/>
<xsl:param name="update"/>
<xsl:param name="export"/>
<xsl:param name="textext"/>
<xsl:param name="skipext"/>
<xsl:param name="skipprefix"/>
<xsl:param name="result"/>

<xsl:template match="/storage">
<html>
<head>
	<title>Zip Service: <xsl:value-of select="sitename"/> - Zip Service</title>
	<xsl:call-template name="script"/>
	<link rel="stylesheet" href="service.css" type="text/css"/>
</head>
<body>
	<div class="closebox">
		<img src="/mirc/images/closebox.gif"
			 onclick="window.open('/mirc/query','_self');"
			 title="Return to the MIRC home page"/>
	</div>

<form target="_self" method="post" accept-charset="UTF-8"
	  enctype="multipart/form-data" id="fileform">

<table width="80%" align="center">
	<tr><td colspan="2"><h2>Zip Service for <xsl:value-of select="sitename"/></h2></td></tr>
	<tr><td colspan="2">
		<p><i>
			Use this page to submit zip files containing directories of
			files for automatic creation of MIRCdocuments on this MIRC site.
		</i></p>
	</td></tr>
	<tr><td colspan="2"><hr/></td></tr>
	<tr><td colspan="2"><p><b><u>Step One</u>:</b> Add author and owner information:</p></td></tr>
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
	<tr>
		<td class="text-label">Owner's username:</td>
		<td class="text-field">
			<input class="input-length" name="username">
				<xsl:attribute name="value"><xsl:value-of select="$username"/></xsl:attribute>
			</input>
		</td>
	</tr>
	<tr><td colspan="2"><hr/></td></tr>
	<tr><td colspan="2"><p><b><u>Step Two</u>:</b> Decide who can view the document:</p></td></tr>
	<tr>
		<td class="text-label">Read privilege:</td>
		<td class="text-field">
			<span class="small-font">Enter * to make a document public or blank to make it private:</span><br/>
			<input class="input-length" name="read">
				<xsl:attribute name="value"><xsl:value-of select="$read"/></xsl:attribute>
			</input>
		</td>
	</tr>
	<tr>
		<td class="text-label">Update privilege:</td>
		<td class="text-field">
			<span class="small-font">Enter * to make a document public or blank to make it private:</span><br/>
			<input class="input-length" name="update">
				<xsl:attribute name="value"><xsl:value-of select="$update"/></xsl:attribute>
			</input>
		</td>
	</tr>
	<tr>
		<td class="text-label">Export privilege:</td>
		<td class="text-field">
			<span class="small-font">Enter * to make a document public or blank to make it private:</span><br/>
			<input class="input-length" name="export">
				<xsl:attribute name="value"><xsl:value-of select="$export"/></xsl:attribute>
			</input>
		</td>
	</tr>
	<tr><td colspan="2"><hr/></td></tr>
	<tr><td colspan="2"><p><b><u>Step Three</u>:</b> Upload the File:</p></td></tr>
	<tr>
		<td class="text-label">Text file extensions:</td>
		<td class="text-field">
			<span class="small-font">Enter a list of extensions designating text files, separated by commas:</span><br/>
			<input class="input-length" name="textext">
				<xsl:attribute name="value"><xsl:value-of select="$textext"/></xsl:attribute>
			</input>
		</td>
	</tr>
	<tr>
		<td class="text-label">Skip file extensions:</td>
		<td class="text-field">
			<span class="small-font">Enter a list of extensions designating files to be skipped, separated by commas:</span><br/>
			<input class="input-length" name="skipext">
				<xsl:attribute name="value"><xsl:value-of select="$skipext"/></xsl:attribute>
			</input>
		</td>
	</tr>
	<tr>
		<td class="text-label">Skip directory prefixes:</td>
		<td class="text-field">
			<span class="small-font">Enter a list of prefixes designating directories to be skipped, separated by commas:</span><br/>
			<input class="input-length" name="skipprefix">
				<xsl:attribute name="value"><xsl:value-of select="$skipprefix"/></xsl:attribute>
			</input>
		</td>
	</tr>
	<tr>
		<td class="text-label">Overwrite template values:</td>
		<td class="text-field">
			<input type="checkbox" name="overwrite" value="overwrite" checked="true"/>
			<span class="small-font">Check this box for normal operation; uncheck it to give preference to the values in the template.</span>
		</td>
	</tr>
	<tr>
		<td class="text-label">Anonymize DICOM Objects:</td>
		<td class="text-field">
			<input type="checkbox" name="anonymize" value="anonymize"/>
			<span class="small-font">Check this box to anonymize DICOM files in the submission.</span>
		</td>
	</tr>
	<tr><td colspan="2">&#160;</td></tr>
	<tr>
		<td class="text-label"><b>Select the zip file:</b></td>
		<td class="text-field">
			<input
				onchange="fileform.filename.value = selectedFile.value;"
				size="75%" name="filecontent" id="selectedFile" type="file"/>
		</td>
	</tr>
	<tr><td colspan="2" align="center">
		<br/>
		<input NAME="Button1" ID="Button1" value="Submit the Zip File" onclick="document.getElementById('fileform').submit();" type="button"/>
		<input ID="Hidden1" name="filename" type="hidden"/>
	</td></tr>
</table>

</form>
</body>
</html>
</xsl:template>

<xsl:template name="script">
	<script>
		var messageText = '<xsl:value-of select="$result"/>';
		function showMessage() {
			if (messageText != "") {
				alert(messageText.replace(/\|/g,"\n"));
			}
		}
		window.onload = showMessage;
	</script>
</xsl:template>

</xsl:stylesheet>