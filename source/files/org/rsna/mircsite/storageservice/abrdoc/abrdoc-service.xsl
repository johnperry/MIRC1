<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="context"/>
<xsl:param name="name"/>
<xsl:param name="affiliation"/>
<xsl:param name="contact"/>
<xsl:param name="username"/>
<xsl:param name="groups"/>
<xsl:param name="textext"/>
<xsl:param name="template"/>
<xsl:param name="templates"/>

<xsl:template match="/storage">
<html>
<head>
	<title>ABR Author Tool: <xsl:value-of select="sitename"/></title>
	<link href="service.css" rel="Stylesheet" type="text/css" media="all"/>
	<script src="abrdoc/abrdoc-service.js">;</script>
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
			<h2>ABR Author Tool for <xsl:value-of select="sitename"/></h2>
		</td>
	</tr>
	<tr><td colspan="2"><hr/></td></tr>
	<tr>
		<td colspan="2">
			<p>If you wish to switch to a different template, select it here:</p>
		</td>
	</tr>
	<tr>
		<td class="text-label">Template:</td>
		<td class="text-field">
			<select class="input-length" name="templatename" id="templatename"
					onchange="newTemplate('{$context}');">
				<xsl:for-each select="$templates/templates/template">
					<option value="{file}">
						<xsl:if test="title = $template/MIRCdocument/title">
							<xsl:attribute name="selected">true</xsl:attribute>
						</xsl:if>
						<xsl:value-of select="title"/>
					</option>
				</xsl:for-each>
			</select>
		</td>
	</tr>
	<tr><td colspan="2"><hr/></td></tr>
	<tr><td colspan="2"><p><b><u>Step One</u>:</b> Compose a title, consider a phrase that uses the diagnosis and minimal history.</p></td></tr>
	<tr>
		<td class="text-label">Title:</td>
		<td class="text-field">
			<input class="input-length" name="title" id="title"
				   value="{$template/MIRCdocument/title}"/>
		</td>
	</tr>
	<tr><td colspan="2"><hr/></td></tr>
	<tr><td colspan="2"><p><b><u>Step Two</u>:</b> Add author and owner information:</p></td></tr>
	<tr>
		<td class="text-label">Author's name:</td>
		<td class="text-field">
			<input class="input-length" name="name" id="name" value="{$name}"/>
		</td>
	</tr>
	<tr>
		<td class="text-label">Author's affiliation:</td>
		<td class="text-field">
			<input class="input-length" name="affiliation" id="affiliation" value="{$affiliation}"/>
		</td>
	</tr>
	<tr>
		<td class="text-label">Author's phone or email:</td>
		<td class="text-field">
			<input class="input-length" name="contact" id="contact" value="{$contact}"/>
		</td>
	</tr>
	<tr>
		<td class="text-label">Owner's username:</td>
		<td class="text-field">
			<input class="input-length" name="username" id="username" value="{$username}"/>
		</td>
	</tr>
	<tr><td colspan="2"><hr/></td></tr>
	<tr><td colspan="2"><p><b><u>Step Three</u>:</b> Enter any desired section information:</p></td></tr>
	<xsl:if test="$template/MIRCdocument/abstract">
		<tr>
			<td class="text-label" valign="top">Abstract:</td>
			<td class="text-field">
				<textarea class="input-length" name="abstract" id="abstract">
					<xsl:value-of select="$template/MIRCdocument/abstract"/>
				</textarea>
				<input type="hidden" name="abstract-text" id="abstract-text"></input>
			</td>
		</tr>
	</xsl:if>
	<xsl:for-each select="$template/MIRCdocument/section">
		<xsl:if test="(@heading != 'Files') and (@heading != 'Notes')">
			<tr>
				<td class="text-label space-above" valign="top">
					<xsl:value-of select="@heading"/>
					<xsl:text>:</xsl:text>
				</td>
				<td class="text-field space-above">
					<xsl:apply-templates select="p | textblock"/>
				</td>
			</tr>
		</xsl:if>
	</xsl:for-each>
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
					onclick="process();" type="button"/>
		</td>
	</tr>

</table>
</form>
</body>
</html>
</xsl:template>

<xsl:template match="p">
	<xsl:apply-templates/>
</xsl:template>

<xsl:template match="textblock">
	<textarea class="input-length tall" name="{@name}" id="{@name}">
		<xsl:text> </xsl:text>
	</textarea>
	<input type="hidden" name="{@name}-text" id="{@name}-text"/>
</xsl:template>

<xsl:template match="ul">
	<ul class="no-bullet"><xsl:apply-templates select="checkbox"/></ul>
</xsl:template>

<xsl:template match="ol">
	<ol><xsl:apply-templates select="checkbox"/></ol>
</xsl:template>

<xsl:template match="checkbox">
	<li>
		<input type="checkbox" name="{@name}" value="yes">
			<xsl:value-of select="."/>
		</input>
	</li>
</xsl:template>

<xsl:template match="select">
	<select name="{@name}" class="input-length">
		<xsl:apply-templates select="option"/>
	</select>
</xsl:template>

<xsl:template match="option">
	<option><xsl:value-of select="."/></option>
</xsl:template>

<xsl:template match="br | text()">
	<xsl:copy/>
</xsl:template>

</xsl:stylesheet>
