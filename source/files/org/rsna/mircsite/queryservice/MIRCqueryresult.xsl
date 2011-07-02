<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet version="1.1"	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="servername"/>
<xsl:param name="urlstring"/>
<xsl:param name="liststart"/>
<xsl:param name="showimages"/>
<xsl:param name="maxabs">1000</xsl:param>

<xsl:template match="*">
	<xsl:param name="dr"/>
	<xsl:copy>
		<xsl:apply-templates select="*|@*|text()">
			<xsl:with-param name="dr" select="string($dr)"/>
		</xsl:apply-templates>
	</xsl:copy>
</xsl:template>

<xsl:template match="@*">
	<xsl:copy-of select="."/>
</xsl:template>

<xsl:template match="/MIRCqueryresult">

	<xsl:element name="font">
		<xsl:choose>
			<xsl:when test="contains($servername,'RSNA') or contains($servername,'InteractEd')">
				<xsl:attribute name="color">teal</xsl:attribute>
			</xsl:when>
		<xsl:otherwise>
			<xsl:attribute name="color">black</xsl:attribute>
		</xsl:otherwise>
	</xsl:choose>

	<h3>
		<xsl:value-of select="$servername"/>
		<xsl:if test="$showimages = 'yes'">
			<span style="font-size:xx-small">
				<xsl:text> - Click images to enlarge</xsl:text>
			</span>
		</xsl:if>
	</h3>

	<xsl:if test="error">
		<ul>
			<xsl:for-each select="error">
				<li style="color:red">
					<b><xsl:value-of select="."/></b>
				</li>
			</xsl:for-each>
		</ul>
	</xsl:if>

	<xsl:apply-templates select="preamble"/>

	<xsl:variable name="list" select="MIRCdocument"/>

	<xsl:if test="$list">
		<ol style="margin-left:30; padding-left:30" start="{$liststart}">
			<xsl:for-each select="$list">
				<xsl:variable name="dirPath">
					<xsl:call-template name="dirPath">
						<xsl:with-param name="path" select="@docref" />
					</xsl:call-template>
				</xsl:variable>

				<li>
					<a href="{@docref}"><xsl:value-of select="title"/></a>
					<br/>
					<xsl:for-each select="author">
						<xsl:choose>
							<xsl:when test="normalize-space(name)">
								<xsl:value-of select="name"/><br/>
							</xsl:when>
							<xsl:otherwise>
								<xsl:if test="normalize-space(.)">
									<xsl:value-of select="."/><br/>
								</xsl:if>
							</xsl:otherwise>
						</xsl:choose>
					</xsl:for-each>

					<xsl:if test="$showimages = 'yes'">
						<p>
							<xsl:for-each select="images/image[position()&lt;7]">
								<xsl:sort select="@an" order="descending"/>
								<img style="margin:3px;" width="96px" src="{$dirPath}{@src}">
									<xsl:attribute name="onclick">if (this.width==320){this.width=96;}else{this.width=320;}; return false;</xsl:attribute>
								</img>
							</xsl:for-each>
						</p>
					</xsl:if>

					<xsl:apply-templates select="abstract">
						<xsl:with-param name="dr" select="string(@docref)"/>
					</xsl:apply-templates>
					<xsl:apply-templates select="access"/>
					<xsl:apply-templates select="level"/>
					<xsl:apply-templates select="peer-review"/>
					</li>
				<br/>
			</xsl:for-each>
		</ol>
	</xsl:if>
	<hr/>

	</xsl:element>

</xsl:template>

<xsl:template match="abstract">
	<xsl:param name="dr"/>
	<xsl:param name="abs"><xsl:value-of select="."/></xsl:param>
	<xsl:choose>
		<xsl:when test="string-length($abs) &lt; $maxabs">
			<p>
				<xsl:apply-templates>
					<xsl:with-param name="dr" select="string($dr)"/>
				</xsl:apply-templates>
			</p>
		</xsl:when>
		<xsl:otherwise>
			<p><xsl:value-of select="substring($abs,1,$maxabs)"/>...</p>
		</xsl:otherwise>
	</xsl:choose>
</xsl:template>

<xsl:template name="dirPath">
	<xsl:param name="path"/>
	<xsl:if test="contains($path,'/')">
		<xsl:value-of select="substring-before($path,'/')"/>
		<xsl:value-of select="string('/')"/>
		<xsl:call-template name="dirPath">
			<xsl:with-param name="path" select="substring-after($path,'/')"/>
		</xsl:call-template>
	</xsl:if>
</xsl:template>

<xsl:template match="@href">
	<xsl:param name="dr"/>
	<xsl:attribute name="href">
		<xsl:choose>
			<xsl:when test="contains(string(.),'://')">
				<xsl:value-of select="."/>
			</xsl:when>
			<xsl:when test="starts-with(string(.),'/')">
				<xsl:value-of select="$urlstring"/>
				<xsl:value-of select="."/>
			</xsl:when>
			<xsl:otherwise>
				<xsl:call-template name="dirPath">
					<xsl:with-param name="path" select="string($dr)"/>
				</xsl:call-template>
				<xsl:value-of select="."/>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:attribute>
</xsl:template>

<xsl:template match="link | a">
	<xsl:param name="dr"/>
  <xsl:element name="a">
  	<xsl:apply-templates select="@*">
  		<xsl:with-param name="dr" select="string($dr)"/>
  	</xsl:apply-templates>
  	<xsl:apply-templates/>
  </xsl:element>
</xsl:template>

<xsl:template match="image"/>
<xsl:template match="patient"/>
<xsl:template match="diagnosis"/>
<xsl:template match="code"/>

<xsl:template match="access">
	<xsl:if test="string-length(normalize-space(.))&gt;1">
		<p>Access: <xsl:value-of select="."/></p>
	</xsl:if>
</xsl:template>

<xsl:template match="peer-review">
	<p>Peer-reviewed</p>
</xsl:template>

<xsl:template match="level">
	<xsl:if test="string-length(normalize-space(.))&gt;1">
		<p>Level: <xsl:value-of select="."/></p>
	</xsl:if>
</xsl:template>

<xsl:template match="preamble">
	<p class="preamble">
		<xsl:copy-of select="*|text()" />
	</p>
</xsl:template>

</xsl:stylesheet>
