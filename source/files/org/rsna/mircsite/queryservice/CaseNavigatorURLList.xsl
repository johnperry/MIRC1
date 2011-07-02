<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet version="1.1"	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:template match="/MIRCqueryresult">
	<docreflist>
		<xsl:apply-templates select="MIRCdocument"/>
	</docreflist>
</xsl:template>

<xsl:template match="MIRCdocument">
	<xsl:if test="@docref">
		<docref><xsl:value-of select="@docref"/></docref>
	</xsl:if>
</xsl:template>

</xsl:stylesheet>
