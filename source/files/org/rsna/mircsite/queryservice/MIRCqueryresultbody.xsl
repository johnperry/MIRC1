<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet version="1.1" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="homestring"/>
<xsl:param name="nextstring"/>
<xsl:param name="prevstring"/>

<xsl:template match="*|@*|text()">
	<xsl:copy>
		<xsl:apply-templates select="*|@*|text()"/>
	</xsl:copy>
</xsl:template>

<xsl:template match="/">
	<xsl:call-template name="header"/>
	<xsl:call-template name="buttonbar"/>
</xsl:template>

<xsl:template name="header">
<div class="header">
	<div class="sitename">
		<span>Search Results</span>
	</div>
	<div class="sitelogo" style="height:{@mastheadheight}">&#160;</div>
</div>
</xsl:template>

<xsl:template name="buttonbar">
<div id="menubar" class="menubar">
	<div class="links">
		<input type="button" style="width:60" value="Home"
				onclick="window.open('{$homestring}','_self')"/>
		<input type="button" style="width:60" value="Prev"
				onclick="window.open('{$prevstring}','_self')"/>
		<input type="button" style="width:60" value="Next"
				onclick="window.open('{$nextstring}','_self')"/>
	</div>
	<div id="menuDiv">
		&#160;
	</div>
</div>
</xsl:template>

</xsl:stylesheet>
