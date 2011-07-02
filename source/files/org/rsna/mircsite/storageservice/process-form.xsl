<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet version="1.1" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="no" indent="no"/>

<xsl:strip-space elements="*"/>

<xsl:param name="formdata" select="/formdata"/>
<xsl:param name="username"/>
<xsl:param name="name"/>
<xsl:param name="affiliation"/>
<xsl:param name="contact"/>
<xsl:param name="path"/>
<xsl:param name="date"/>
<xsl:param name="time"/>

<xsl:template match="/formdata">
  <xsl:apply-templates select="template"/>
</xsl:template>

<xsl:template match="template">
  <xsl:apply-templates select="document(concat($path,.))"/>
</xsl:template>

<xsl:template match="document-name"/>

<xsl:template match="*">
  <xsl:copy>
    <xsl:copy-of select="@*"/>
    <xsl:if test="@name">
      <xsl:variable name="x" select="$formdata/*[name(.)=current()/@name]"/>
      <xsl:choose>
      	<xsl:when test="$x">
      		<xsl:apply-templates select="$x/*|$x/text()"/>
      	</xsl:when>
      	<xsl:otherwise>
      		<xsl:apply-templates select="*|text()"/>
      	</xsl:otherwise>
      </xsl:choose>
    </xsl:if>
    <xsl:if test="not(@name)">
      <xsl:apply-templates select="*|text()"/>
    </xsl:if>
  </xsl:copy>
</xsl:template>

<xsl:template match="author">
  <xsl:if test="string-length(name)!=0">
    <author>
      <xsl:apply-templates/>
    </author>
  </xsl:if>
  <xsl:if test="string-length(name)=0">
    <author>
      <name><xsl:value-of select="$name"/></name>
      <xsl:if test="string-length($affiliation)!=0">
        <affiliation><xsl:value-of select="$affiliation"/></affiliation>
      </xsl:if>
      <xsl:if test="string-length($contact)!=0">
        <contact><xsl:value-of select="$contact"/></contact>
      </xsl:if>
    </author>
  </xsl:if>
</xsl:template>

<xsl:template match="publication-date">
  <xsl:if test="string-length(.)!=0">
    <publication-date><xsl:value-of select="."/></publication-date>
  </xsl:if>
  <xsl:if test="string-length(.)=0">
    <publication-date><xsl:value-of select="$date"/></publication-date>
  </xsl:if>
</xsl:template>

<xsl:template match="insert-note">
	<xsl:variable name="x" select="$formdata/*[name(.)=current()/@name]"/>
	<xsl:if test="string-length($x) != 0">
		<xsl:if test="insert-user | insert-date | insert-time">
			<b><xsl:text>Entry</xsl:text>
			<xsl:if test="insert-user">
				<xsl:text> by </xsl:text>
				<xsl:value-of select="$username"/>
			</xsl:if>
			<xsl:if test="insert-date">
				<xsl:text> on </xsl:text>
				<xsl:value-of select="$date"/>
			</xsl:if>
			<xsl:if test="insert-time">
				<xsl:text> at </xsl:text>
				<xsl:value-of select="$time"/>
			</xsl:if>
			</b>
		</xsl:if>
		<xsl:apply-templates select="$x/*|$x/text()"/>
	</xsl:if>
	<xsl:copy-of select="."/>
</xsl:template>

<xsl:template match="text()">
  <xsl:copy/>
</xsl:template>

</xsl:stylesheet>
