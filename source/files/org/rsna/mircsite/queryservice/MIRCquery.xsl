<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet version="1.1" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="no" />

<xsl:param name="queryUID"/>

<xsl:template match="/formdata">

	<MIRCquery>
		<xsl:call-template name="queryattributes" />
		<xsl:call-template name="queryidstring"/>

		<xsl:if test="querypage = 'altcontent'">
			<xsl:value-of select="altdocument"/>
		</xsl:if>
		<xsl:if test="querypage = 'maincontent'">

		<xsl:apply-templates/>
			<xsl:if test="pt-name | pt-id | pt-mrn | pt-sex |
						  pt-race | pt-species | pt-breed |
						  years | months | weeks | days">
				<xsl:call-template name="patientelements" />
			</xsl:if>
			<xsl:if test="imagemodality | imageformat | imagecompression |
						  imageanatomy | imagepathology " >
				<xsl:call-template name="imageelements" />
			</xsl:if>
		</xsl:if>
	</MIRCquery>
</xsl:template>

<xsl:template name="queryattributes" >
	<xsl:for-each select="firstresult|maxresults|unknown|bgcolor|display|icons" >
		<xsl:attribute name="{local-name()}" namespace="{namespace-uri()}" >
			<xsl:value-of select="."/>
		</xsl:attribute>
	</xsl:for-each>
</xsl:template>

<xsl:template name="queryidstring" >
	<xsl:if test = "$queryUID" >
		<xsl:attribute name="queryUID" namespace="{namespace-uri()}" >
			<xsl:value-of select="$queryUID"/>
		</xsl:attribute>
	</xsl:if>
</xsl:template>

<xsl:template match="document" >
	<xsl:value-of select="."/>
</xsl:template>

<xsl:template match="node()" >
	<xsl:element name="{local-name()}" namespace="{namespace-uri()}" >
		<xsl:value-of select="."/>
	</xsl:element>
</xsl:template>


<xsl:template match="querypage|altdocument|bgcolor|display|icons"/>
<xsl:template match="firstresult|maxresults|queryUID|unknown|showimages|casenavigator|randomize|server" />
<xsl:template match="imagemodality|imageanatomy|imageformat|imagecompression|imagepathology" />
<xsl:template match="pt-name|pt-id|pt-mrn|pt-sex|pt-race|pt-species|pt-breed" />
<xsl:template match="years|months|weeks|days" />

<xsl:template name="patientelements" >
	<patient>
		<xsl:for-each select = "pt-name | pt-id | pt-mrn | pt-sex | pt-race | pt-species | pt-breed" >
			<xsl:element name="{local-name()}" namespace="{namespace-uri()}" >
				<xsl:value-of select="."/>
			</xsl:element>
		</xsl:for-each>
		<xsl:if test= "years | months | weeks | days">
			<pt-age>
				<xsl:for-each select = "years | months | weeks | days" >
					<xsl:element name="{local-name()}" namespace="{namespace-uri()}" >
						<xsl:value-of select="."/>
					</xsl:element>
				</xsl:for-each>
			</pt-age>
		</xsl:if>
	</patient>
</xsl:template>

<xsl:template name="imageelements" >
	<image>
		<xsl:for-each select="imagemodality | imageformat | imagecompression |
							  imageanatomy | imagepathology" >
			<xsl:element name="{substring(local-name(),6)}" namespace="{namespace-uri()}" >
				<xsl:value-of select="."/>
			</xsl:element>
		</xsl:for-each>
	</image>
</xsl:template>

</xsl:stylesheet>
