<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.1">

<xsl:param name="input-type">button</xsl:param>
<xsl:param name="unknown">no</xsl:param>
<xsl:param name="context-path"/>
<xsl:param name="dir-path"/>
<xsl:param name="doc-path"/>
<xsl:param name="preview">no</xsl:param>
<xsl:param name="export-url"/>
<xsl:param name="edit-url"/>
<xsl:param name="add-url"/>
<xsl:param name="publish-url"/>
<xsl:param name="delete-url"/>
<xsl:param name="user-is-authenticated"/>
<xsl:param name="user-is-owner"/>
<xsl:param name="user-is-admin"/>
<xsl:param name="user-has-myrsna-acct"/>
<xsl:param name="filecabinet-url"/>
<xsl:param name="doc-url"/>
<xsl:param name="server-url"/>
<xsl:param name="dicom-export-url"/>
<xsl:param name="database-export-url"/>
<xsl:param name="bgcolor"/>
<xsl:param name="display"/>
<xsl:param name="icons"/>
<xsl:param name="today"/>

<xsl:template match="*|@*|text()">
	<xsl:copy>
		<xsl:apply-templates select="*|@*|text()" />
	</xsl:copy>
</xsl:template>

<xsl:template match="/MIRCdocument">
	<html>
		<head>
			<link rel="Stylesheet" type="text/css" media="all" href="/mirc/JSPopup.css"></link>
			<link rel="Stylesheet" type="text/css" media="all" href="/mirc/JSTree.css"> </link>
			<link rel="Stylesheet" type="text/css" media="all" href="{$context-path}/MIRCdocument.css"></link>
			<script src="/mirc/JSUtil.js">;</script>
			<script src="/mirc/JSAJAX.js">;</script>
			<script src="{$context-path}/MIRCimage.js">;</script>
			<xsl:call-template name="script-init"/>
			<script src="/mirc/JSPopup.js">;</script>
			<script src="/mirc/JSTree.js">;</script>
			<script src="/mirc/JSSplitPane.js">;</script>
			<script src="{$context-path}/MIRCdocument.js">;</script>
			<title>MIRC document</title>
		</head>
		<xsl:choose>
			<xsl:when test="$display='tab'">
				<xsl:call-template name="tab-body"/>
			</xsl:when>
			<xsl:when test="$display='mstf' or $display='mirctf'">
				<xsl:call-template name="mstf-body"/>
			</xsl:when>
			<xsl:otherwise>
				<xsl:call-template name="page-body"/>
			</xsl:otherwise>
		</xsl:choose>
	</html>
</xsl:template>

<xsl:template name="page-body">
	<body class="page">
		<xsl:call-template name="page-title"/>
		<xsl:apply-templates select="author"/>
		<xsl:call-template name="page-abstract"/>
		<xsl:if test="not($unknown='yes')">
			<xsl:apply-templates select="keywords"/>
			<xsl:call-template name="radlex"/>
		</xsl:if>
		<xsl:apply-templates select="section[
			((@visible='owner') and ($user-is-owner='yes')) or
			(
			 not(@visible='no') and not((@visible='owner') and
			 not($user-is-owner='yes')) and
			 (not(@after) or (number($today) &gt; number(@after)))
			)] | image-section"/>
		<xsl:apply-templates select="references"/>
		<xsl:call-template name="document-footer"/>
	</body>
</xsl:template>

<xsl:template name="tab-body">
	<xsl:variable name="sectionlist" select="section[
			((@visible='owner') and ($user-is-owner='yes')) or
			(
			 not(@visible='no') and
			 not((@visible='owner') and not($user-is-owner='yes')) and
			 (not(@after) or (number($today) &gt; number(@after)))
			)] | image-section"/>
	<xsl:variable name="referenceslist" select="references"/>
	<body class="tab">
		<xsl:call-template name="make-tabs">
			<xsl:with-param name="sectionlist" select="$sectionlist"/>
			<xsl:with-param name="referenceslist" select="$referenceslist"/>
		</xsl:call-template>
		<div id="maindiv" class="maindiv">
			<xsl:call-template name="make-section-divs">
				<xsl:with-param name="sectionlist" select="$sectionlist"/>
				<xsl:with-param name="referenceslist" select="$referenceslist"/>
			</xsl:call-template>
		</div>
	</body>
</xsl:template>

<xsl:template name="mstf-body">
	<xsl:variable name="sectionlist" select="section[
			((@visible='owner') and ($user-is-owner='yes')) or
			(
			 not(@visible='no') and
			 not((@visible='owner') and not($user-is-owner='yes')) and
			 (not(@after) or (number($today) &gt; number(@after)))
			)]"/>
	<xsl:variable name="referenceslist" select="references"/>
	<body class="mstf">
		<xsl:call-template name="make-tabs">
			<xsl:with-param name="sectionlist" select="$sectionlist"/>
			<xsl:with-param name="referenceslist" select="$referenceslist"/>
		</xsl:call-template>
		<div id="maindiv" class="maindiv">
			<div id="leftside" class="leftside">
				<div id="uldiv" class="left">
					<xsl:call-template name="make-section-divs">
					<xsl:with-param name="sectionlist" select="$sectionlist"/>
					<xsl:with-param name="referenceslist" select="$referenceslist"/>
					</xsl:call-template>
				</div>
				<xsl:if test="not(//image-section/@icons='no') and not($icons='no')">
					<div id="sldiv" class="VSdivider"></div>
					<div id="lldiv" class="left" style="color:white;background:#111111">
						<xsl:call-template name="make-token-buttons"/>
					</div>
				</xsl:if>
			</div>
			<div id="divider" class="HSdivider"></div>
			<xsl:call-template name="make-right-div"/>
		</div>
	</body>
</xsl:template>

<xsl:template name="make-token-div">
	<xsl:if test="not(//image-section/@icons='no') and not($icons='no')">
		<div id="lldiv" class="left" style="color:white;background:#111111">
			<xsl:call-template name="make-token-buttons"/>
		</div>
	</xsl:if>
</xsl:template>

<xsl:template name="make-right-div">
	<div id="rightside" class="rightside">
		<div id="rbuttons" class="rbuttons">
			<span id="imagenav" class="imagenav">
				<input id="previmg" type="button" value="&lt;&lt;&lt;" disabled="true" onclick="prevImage()"/>
				&#160;
				<span id="imagenumber" class="imagenumber"></span>
				&#160;
				<input id="nextimg" type="button" value="&gt;&gt;&gt;" disabled="true" onclick="nextImage()"/>
			</span>
			<span id="selbuttons" class="selbuttons">
				<input id="annbtn" type="button" value="Annotations" disabled="true" onclick="displayAnnotation()"/>
				<input id="orgbtn" type="button" value="Original Size" disabled="true" onclick="fetchOriginal()"/>
				<input id="dcmbtn" type="button" value="Original Format" disabled="true" onclick="fetchModality(event)"/>
			</span>
		</div>
		<div id="captions" class="captions"/>
		<div id="rimage" class="rimage">
			<center id="rimagecenter">
				<!-- primary image goes here -->
			</center>
		</div>
	</div>
</xsl:template>

<xsl:template name="make-tabs">
	<xsl:param name="sectionlist"/>
	<xsl:param name="referenceslist"/>
	<div class="tabs" id="tabs">
		<xsl:call-template name="make-button">
			<xsl:with-param name="n">1</xsl:with-param>
			<xsl:with-param name="tabtitle" select="'Document'"/>
		</xsl:call-template>
		<xsl:call-template name="make-tab-buttons">
			<xsl:with-param name="n">2</xsl:with-param>
			<xsl:with-param name="nodelist" select="$sectionlist"/>
		</xsl:call-template>
		<xsl:if test="$referenceslist">
			<xsl:call-template name="make-button">
				<xsl:with-param name="n" select="2+count($sectionlist)"/>
				<xsl:with-param name="tabtitle" select="'References'"/>
			</xsl:call-template>
		</xsl:if>
	</div>
</xsl:template>

<xsl:template name="make-tab-buttons">
	<xsl:param name="n"/>
	<xsl:param name="nodelist"/>
	<xsl:if test="$nodelist">
		<xsl:call-template name="make-button">
			<xsl:with-param name="n" select="$n"/>
			<xsl:with-param name="tabtitle" select="$nodelist[position()=1]/@heading"/>
		</xsl:call-template>
		<xsl:call-template name="make-tab-buttons">
			<xsl:with-param name="n" select="$n+1"/>
			<xsl:with-param name="nodelist" select="$nodelist[position()!=1]"/>
		</xsl:call-template>
	</xsl:if>
</xsl:template>

<xsl:template name="make-button">
	<xsl:param name="n"/>
	<xsl:param name="tabtitle"/>
	<input type="button" class="u">
		<xsl:attribute name="id">b<xsl:value-of select="$n"/></xsl:attribute>
		<xsl:attribute name="onclick">
			<xsl:text>bclick('b</xsl:text>
			<xsl:value-of select="$n"/>
			<xsl:text>','tab</xsl:text>
			<xsl:value-of select="$n"/>
			<xsl:text>')</xsl:text>
		</xsl:attribute>
		<xsl:attribute name="value">
			<xsl:value-of select="$tabtitle"/>
		</xsl:attribute>
	</input>
</xsl:template>

<xsl:template name="make-section-divs">
	<xsl:param name="sectionlist"/>
	<xsl:param name="referenceslist"/>
	<div class="hide" id="tab1">
		<xsl:call-template name="page-title"/>
		<xsl:apply-templates select="author"/>
		<xsl:call-template name="page-abstract"/>
		<xsl:apply-templates select="keywords"/>
		<xsl:call-template name="radlex"/>
		<xsl:call-template name="document-footer"/>
	</div>

	<xsl:call-template name="make-sections">
		<xsl:with-param name="n" select="2"/>
		<xsl:with-param name="nodelist" select="$sectionlist"/>
	</xsl:call-template>

	<xsl:if test="$referenceslist">
		<div class="hide">
			<xsl:attribute name="id">
				<xsl:text>tab</xsl:text>
				<xsl:value-of select="2+count($sectionlist)"/>
			</xsl:attribute>
			<xsl:apply-templates select="$referenceslist"/>
		</div>
	</xsl:if>
</xsl:template>

<xsl:template name="make-sections">
	<xsl:param name="n"/>
	<xsl:param name="nodelist"/>
	<xsl:if test="$nodelist">
		<div class="hide">
			<xsl:attribute name="id">tab<xsl:value-of select="$n"/></xsl:attribute>
			<xsl:apply-templates select="$nodelist[position()=1]"/>
		</div>
		<xsl:call-template name="make-sections">
			<xsl:with-param name="n" select="$n+1"/>
			<xsl:with-param name="nodelist" select="$nodelist[position()!=1]"/>
		</xsl:call-template>
	</xsl:if>
</xsl:template>

<xsl:template name="make-token-buttons">
	<xsl:for-each select="//image-section/image">
		<xsl:variable name="n"><xsl:number /></xsl:variable>
		<input type="image" class="tokenbuttonDESEL" width="64">
			<xsl:attribute name="src">
				<xsl:choose>
					<xsl:when test="alternative-image[@role='icon']">
						<xsl:value-of select="alternative-image[@role='icon']/@src"/>
					</xsl:when>
					<xsl:otherwise>
						<xsl:value-of select="@src"/>
					</xsl:otherwise>
				</xsl:choose>
			</xsl:attribute>
			<xsl:attribute name="onclick">
				<xsl:text>loadImage(</xsl:text>
				<xsl:value-of select="$n"/>
				<xsl:text>)</xsl:text>
			</xsl:attribute>
			<xsl:attribute name="title">
				<xsl:value-of select="$n"/>
			</xsl:attribute>
		</input>
		<xsl:text>&#160;</xsl:text>
	</xsl:for-each>
</xsl:template>

<xsl:template name="tab-title">
	<h1><xsl:value-of select="title"/></h1>
</xsl:template>

<xsl:template name="page-title">
	<xsl:choose>
		<xsl:when test="$unknown='yes'">
			<xsl:choose>
				<xsl:when test="alternative-title">
					<h1><xsl:value-of select="alternative-title"/></h1>
				</xsl:when>
				<xsl:otherwise>
					<xsl:choose>
						<xsl:when test="//category">
							<h1>Unknown - <xsl:value-of select="//category"/></h1>
						</xsl:when>
						<xsl:otherwise>
							<h1>Unknown</h1>
						</xsl:otherwise>
					</xsl:choose>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:when>
		<xsl:otherwise>
			<h1><xsl:value-of select="title"/></h1>
		</xsl:otherwise>
	</xsl:choose>
</xsl:template>

<xsl:template match="author">
	<p class="authorname">
		<xsl:apply-templates/>
	</p>
</xsl:template>

<xsl:template match="name">
	<xsl:value-of select="."/>
</xsl:template>

<xsl:template match="affiliation">
	<p class="authorinfo">
	<xsl:value-of select="."/>
	</p>
</xsl:template>

<xsl:template match="contact">
	<p class="authorinfo">
		<xsl:value-of select="."/>
	</p>
</xsl:template>

<xsl:template name="page-abstract">
	<xsl:choose>
		<xsl:when test="$unknown='yes'">
			<xsl:apply-templates select="alternative-abstract"/>
		</xsl:when>
		<xsl:otherwise>
			<xsl:apply-templates select="abstract"/>
		</xsl:otherwise>
	</xsl:choose>
</xsl:template>

<xsl:template match="abstract | alternative-abstract">
	<h2>Abstract</h2>
	<xsl:apply-templates/>
</xsl:template>

<xsl:template match="keywords">
	<xsl:param name="kws"><xsl:value-of select="."/></xsl:param>
	<xsl:if test="string-length(normalize-space($kws)) &gt; 0">
		<h2>Keywords</h2>
		<p>
			<xsl:apply-templates select="term | text()"/>
		</p>
	</xsl:if>
</xsl:template>

<xsl:template match="section">
	<xsl:if test="not(count(*)=0)">
		<h2><xsl:value-of select="@heading"/></h2>
		<xsl:apply-templates/>
	</xsl:if>
</xsl:template>

<xsl:template match="image-section">
	<xsl:if test="$display='tab'">
		<div id="leftside" class="leftside">
			<xsl:call-template name="make-token-div"/>
		</div>
		<div id="divider" class="HSdivider"></div>
		<xsl:call-template name="make-right-div"/>
	</xsl:if>
	<xsl:if test="(($display='page') or ($display='')) and not(count(image) = 0)">
		<h2><xsl:value-of select="@heading"/></h2>
		<center>
			<xsl:for-each select="image">
				<img><xsl:copy-of select="@src"/></img>
				<br/>
				<xsl:choose>
					<xsl:when test="image-caption">
						<xsl:if test="image-caption[not(@display) or @display='always']">
							<p class="centeredcaption">
								<xsl:value-of select="normalize-space(image-caption[not(@display) or @display='always'])"/>
							</p>
						</xsl:if>
						<xsl:if test="image-caption[@display='click']">
							<p class="centeredcaption">
								<xsl:value-of select="normalize-space(image-caption[@display='click'])"/>
							</p>
						</xsl:if>
						<br/>
					</xsl:when>
					<xsl:otherwise>
						<xsl:text>&#160;</xsl:text>
					</xsl:otherwise>
				</xsl:choose>
			</xsl:for-each>
		</center>
	</xsl:if>
</xsl:template>

<xsl:template match="references">
	<h2>References</h2>
	<xsl:if test="not(count(reference) = 0)">
		<ol>
			<xsl:for-each select="reference">
				<li><xsl:apply-templates/></li>
			</xsl:for-each>
		</ol>
	</xsl:if>
</xsl:template>

<xsl:template match="reference">
	<xsl:apply-templates/>
</xsl:template>

<xsl:template name="document-footer">
	<hr class="docfooter" />
	<p class="center">
		<xsl:if test="rights">
			<xsl:value-of select="rights"/>
			<br/>
		</xsl:if>
		<xsl:if test="publication-date">
			Publication Date: <xsl:value-of select="publication-date"/>
			<br/><br/>
		</xsl:if>
		<xsl:choose>
			<xsl:when test="$preview='yes'">
				Document Preview
			</xsl:when>
			<xsl:otherwise>
				<table class="footer" rules="cols">
					<tr>
						<th>Document</th>
						<th>Image Export</th>
					</tr>
					<tr>
						<td><xsl:call-template name="edit-button"/></td>
						<td><xsl:call-template name="zip-phi-button"/></td>
					</tr>
					<tr>
						<td><xsl:call-template name="publish-button"/></td>
						<td><xsl:call-template name="zip-no-phi-button"/></td>
					</tr>
					<tr>
						<td><xsl:call-template name="export-button"/></td>
						<td><xsl:call-template name="dicom-button"/></td>
					</tr>
					<tr>
						<td><xsl:call-template name="delete-button"/></td>
						<td><xsl:call-template name="database-button"/></td>
					</tr>
					<xsl:call-template name="saveimages-button"/>
					<xsl:call-template name="addimages-button"/>
					<xsl:if test="$user-is-admin = 'yes'">
						<tr rules="rows">
							<td colspan="2"><xsl:call-template name="caseoftheday-button"/></td>
						</tr>
					</xsl:if>
					<xsl:call-template name="conferences-button"/>
					<xsl:call-template name="myrsna-button"/>
					<xsl:call-template name="url-button"/>
				</table>
			</xsl:otherwise>
		</xsl:choose>
	</p>
</xsl:template>

<xsl:template name="myrsna-button">
	<xsl:if test="($user-has-myrsna-acct = 'yes') and (string-length($export-url)!=0)">
		<tr rules="rows">
			<td colspan="2">
				<input type="button" value="Export this document to myRSNA Files"
						title="Export this document to myRSNA Files"
						onclick="exportToMyRsnaFiles('{$export-url}');"/>
			</td>
		</tr>
	</xsl:if>
</xsl:template>

<xsl:template name="url-button">
	<tr rules="rows">
		<td colspan="2">
			<input type="button" value="Document URL"
					title="Show the document URL"
					onclick="copyTextToClipboard('{$doc-url}'); alert('{$doc-url}');"/>
		</td>
	</tr>
</xsl:template>

<xsl:template name="conferences-button">
	<xsl:if test="$user-is-authenticated = 'yes'">
		<xsl:variable name="alt" select="normalize-space(alternative-title)"/>
		<xsl:variable name="cat" select="normalize-space(category)"/>
		<xsl:variable name="unk">
			<xsl:choose>
				<xsl:when test="string-length($alt)!=0">
					<xsl:value-of select="$alt"/>
				</xsl:when>
				<xsl:when test="string-length($cat)!=0">
					<xsl:text>Unknown - </xsl:text>
					<xsl:value-of select="$cat"/>
				</xsl:when>
				<xsl:otherwise>
					<xsl:text>Unknown</xsl:text>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:variable>
		<tr rules="rows">
			<td colspan="2">
				<input type="button" value="Add to Conference"
						title="Add the document to a conference"
						onclick="addToConference('{$doc-url}', getTitle(), '{$doc-url}?unknown=yes', '{$unk}', '{author/name}');"/>
			</td>
		</tr>
	</xsl:if>
</xsl:template>

<xsl:template name="saveimages-button">
	<xsl:if test="string-length($filecabinet-url)!=0">
		<tr rules="rows">
			<td colspan="2">
				<input type="button" value="Save Images to File Cabinet"
						title="Save the images in this document to my File Cabinet"
						onclick="saveImages('{$filecabinet-url}?path={$doc-path}');">
				</input>
			</td>
		</tr>
	</xsl:if>
</xsl:template>

<xsl:template name="addimages-button">
	<xsl:if test="$edit-url and (//insert-image or //insert-megasave)">
		<td colspan="2">
			<input type="button" value="Add Images to This Document"
					onclick="openURL('{$add-url}','_self');">
			</input>
		</td>
	</xsl:if>
</xsl:template>

<xsl:template name="caseoftheday-button">
	<xsl:variable name="ttl">
		<xsl:value-of select="normalize-space(title)"/>
	</xsl:variable>
	<xsl:variable name="abs">
		<xsl:value-of select="concat(substring(normalize-space(abstract),1,200),'...')"/>
	</xsl:variable>
	<xsl:variable name="remove">"'</xsl:variable>
	<xsl:variable name="image-ref">
		<xsl:value-of select="//image/@src"/>
	</xsl:variable>
	<input type="button"
		value="Case of the Day"
		title="Install this document as the Case of the Day"
		onclick="setCaseOfTheDay();"/>

			<input id="codurl" type="hidden" value="{$server-url}/mirc/news/add"/>
			<input id="codtitle" type="hidden" value="{translate($ttl,$remove,'')}"/>
			<input id="coddesc" type="hidden" value="{translate($abs,$remove,'')}"/>
			<input id="codlink" type="hidden" value="{$doc-url}"/>
			<input id="codimage" type="hidden">
				<xsl:attribute name="value">
					<xsl:if test="not(string-length($image-ref) = 0)">
						<xsl:value-of select="$server-url"/>
						<xsl:value-of select="$context-path"/>
						<xsl:value-of select="$dir-path"/>
						<xsl:value-of select="$image-ref"/>
					</xsl:if>
				</xsl:attribute>
			</input>

</xsl:template>

<xsl:template name="export-button">
	<input type="button" value="Export" title="Export this document">
		<xsl:choose>
			<xsl:when test="string-length($export-url)!=0">
				<xsl:attribute name="onclick">
					<xsl:text>exportZipFile('</xsl:text>
					<xsl:value-of select="$export-url"/>
					<xsl:text>','_self', event);</xsl:text>
				</xsl:attribute>
			</xsl:when>
			<xsl:otherwise>
				<xsl:attribute name="disabled">true</xsl:attribute>
			</xsl:otherwise>
		</xsl:choose>
	</input>
</xsl:template>

<xsl:template name="zip-phi-button">
	<input type="button" value="Zip (with PHI)"
		title="Export the identified study dataset">
		<xsl:choose>
			<xsl:when test="//insert-dataset[@phi='yes']">
				<xsl:attribute name="onclick">
					<xsl:text>openURL('</xsl:text>
					<xsl:value-of select="$export-url"/>
					<xsl:text>=phi</xsl:text>
					<xsl:text>','_self');</xsl:text>
				</xsl:attribute>
			</xsl:when>
			<xsl:otherwise>
				<xsl:attribute name="disabled">true</xsl:attribute>
			</xsl:otherwise>
		</xsl:choose>
	</input>
</xsl:template>

<xsl:template name="edit-button">
	<input type="button" value="Edit" title="Edit this document">
		<xsl:choose>
			<xsl:when test="string-length($edit-url)!=0">
				<xsl:attribute name="onclick">
					<xsl:text>openURL('</xsl:text>
					<xsl:value-of select="$edit-url"/>
					<xsl:text>','_blank');</xsl:text>
				</xsl:attribute>
			</xsl:when>
			<xsl:otherwise>
				<xsl:attribute name="disabled">true</xsl:attribute>
			</xsl:otherwise>
		</xsl:choose>
	</input>
</xsl:template>

<xsl:template name="zip-no-phi-button">
	<input type="button" value="Zip (without PHI)"
		title="Export the de-identified study dataset">
		<xsl:choose>
			<xsl:when test="//insert-dataset[not(@phi='yes')]">
				<xsl:attribute name="onclick">
					<xsl:text>openURL('</xsl:text>
					<xsl:value-of select="$export-url"/>
					<xsl:text>=no-phi</xsl:text>
					<xsl:text>','_self');</xsl:text>
				</xsl:attribute>
			</xsl:when>
			<xsl:otherwise>
				<xsl:attribute name="disabled">true</xsl:attribute>
			</xsl:otherwise>
		</xsl:choose>
	</input>
</xsl:template>

<xsl:template name="publish-button">
	<input type="button" value="Publish" title="Publish this document">
		<xsl:choose>
			<xsl:when test="string-length($publish-url)!=0 and
							not(contains(authorization/read,'*'))">
				<xsl:attribute name="onclick">
					<xsl:text>openURL('</xsl:text>
					<xsl:value-of select="$publish-url"/>
					<xsl:text>','_blank');</xsl:text>
				</xsl:attribute>
			</xsl:when>
			<xsl:otherwise>
				<xsl:attribute name="disabled">true</xsl:attribute>
			</xsl:otherwise>
		</xsl:choose>
	</input>
</xsl:template>

<xsl:template name="dicom-button">
	<input type="button" value="To DICOM SCP"
		title="Queue the DICOM objects from this document for the DicomExportService">
		<xsl:choose>
			<xsl:when test="string-length($dicom-export-url)!=0">
				<xsl:attribute name="onclick">
					<xsl:text>openURL('</xsl:text>
					<xsl:value-of select="$dicom-export-url"/>
					<xsl:text>','_blank');</xsl:text>
				</xsl:attribute>
			</xsl:when>
			<xsl:otherwise>
				<xsl:attribute name="disabled">true</xsl:attribute>
			</xsl:otherwise>
		</xsl:choose>
	</input>
</xsl:template>

<xsl:template name="delete-button">
	<input type="button" value="Delete"
		title="Delete this document from the server">
		<xsl:choose>
			<xsl:when test="string-length($delete-url)!=0">
				<xsl:attribute name="onclick">
					<xsl:text>deleteDocument('</xsl:text>
					<xsl:value-of select="$delete-url"/>
					<xsl:text>');</xsl:text>
				</xsl:attribute>
			</xsl:when>
			<xsl:otherwise>
				<xsl:attribute name="disabled">true</xsl:attribute>
			</xsl:otherwise>
		</xsl:choose>
	</input>
</xsl:template>

<xsl:template name="database-button">
	<input type="button" value="To Database"
		title="Queue the DICOM objects from this document for the DatabaseExportService">
		<xsl:choose>
			<xsl:when test="string-length($database-export-url)!=0">
				<xsl:attribute name="onclick">
					<xsl:text>openURL('</xsl:text>
					<xsl:value-of select="$database-export-url"/>
					<xsl:text>','_blank');</xsl:text>
				</xsl:attribute>
			</xsl:when>
			<xsl:otherwise>
				<xsl:attribute name="disabled"></xsl:attribute>
			</xsl:otherwise>
		</xsl:choose>
	</input>

</xsl:template>

<xsl:template match="a">
	<xsl:element name="a">
		<xsl:if test="href">
			<xsl:attribute name="href">
				<xsl:call-template name="escape">
					<xsl:with-param name="text" select="normalize-space(href)"/>
				</xsl:call-template>
			</xsl:attribute>
		</xsl:if>
		<xsl:apply-templates select="*[not('href')] | @* | text()"/>
	</xsl:element>
</xsl:template>

<xsl:template match="iframe">
	<xsl:element name="iframe">
		<xsl:choose>
			<xsl:when test="$display='page'">
				<xsl:if test="src">
					<xsl:attribute name="src">
						<xsl:call-template name="escape">
							<xsl:with-param name="text" select="normalize-space(src)"/>
						</xsl:call-template>
					</xsl:attribute>
					<xsl:apply-templates select="*[not('src')] | @* | text()"/>
				</xsl:if>
				<xsl:if test="not(src)">
					<xsl:apply-templates select="* | @* | text()"/>
				</xsl:if>
			</xsl:when>
			<xsl:otherwise>
				<xsl:apply-templates select="@*[not(name()='src')]"/>
				<xsl:attribute name="src"></xsl:attribute>
				<xsl:if test="src">
					<xsl:attribute name="longdesc">
						<xsl:call-template name="escape">
							<xsl:with-param name="text" select="normalize-space(src)"/>
						</xsl:call-template>
					</xsl:attribute>
				</xsl:if>
				<xsl:if test="not(src)">
					<xsl:attribute name="longdesc"><xsl:value-of select="@src"/></xsl:attribute>
				</xsl:if>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:element>
</xsl:template>

<xsl:template name="escape">
	<xsl:param name="text"/>
	<xsl:choose>
		<xsl:when test="contains($text,'|')">
			<xsl:value-of select="substring-before($text,'|')"/>
			<xsl:text>&amp;</xsl:text>
			<xsl:call-template name="escape">
				<xsl:with-param name="text" select="substring-after($text,'|')"/>
			</xsl:call-template>
		</xsl:when>
		<xsl:otherwise>
			<xsl:value-of select="$text"/>
		</xsl:otherwise>
	</xsl:choose>
</xsl:template>

<xsl:template match="image">
	<xsl:if test="@src">
		<xsl:element name="img">
			<xsl:apply-templates select="@src | @width | @height"/>
		</xsl:element>
	</xsl:if>
	<xsl:if test="@href">
		<xsl:element name="a">
			<xsl:apply-templates select="@href"/>
			<xsl:apply-templates/></xsl:element>
	</xsl:if>
</xsl:template>

<xsl:template match="show">
	<xsl:if test="$display='mstf' or $display='mirctf'">
		<xsl:if test="@image | @annotation">
			<xsl:choose>
				<xsl:when test="@image">
					<input type="button" value="&gt;">
						<xsl:attribute name="onclick">
							<xsl:text>loadImage(</xsl:text>
							<xsl:value-of select="@image"/>
							<xsl:text>)</xsl:text>
						</xsl:attribute>
					</input>
				</xsl:when>
				<xsl:when test="@annotation">
					<input type="button" value="&gt;">
						<xsl:attribute name="onclick">
							<xsl:text>loadannotation(</xsl:text>
							<xsl:value-of select="@annotation"/>
							<xsl:text>)</xsl:text>
							</xsl:attribute>
					</input>
				</xsl:when>
			</xsl:choose>
		</xsl:if>
	</xsl:if>
</xsl:template>

<xsl:template match="text">
	<xsl:if test="not(@visible='no') and not((@visible='owner') and not($user-is-owner='yes'))">
		<xsl:apply-templates/>
	</xsl:if>
</xsl:template>

<xsl:template match="text-caption">
	<xsl:if test="not(@visible='no')">
	<xsl:variable name="text-to-show" select="not(string-length(normalize-space(.))=0)"/>
		<xsl:if test="$text-to-show or @display='always'">
			<xsl:if test="$text-to-show and not(@jump-buttons='yes')">
				<br/><xsl:apply-templates/><br/>
			</xsl:if>
			<xsl:if test="@jump-buttons='yes'">
				<br/>
				<div>
					<xsl:if test="$text-to-show and @show-button='yes'">
						<xsl:attribute name="class">
							<xsl:text>hide</xsl:text>
						</xsl:attribute>
					</xsl:if>
					<xsl:if test="$text-to-show">
						<xsl:apply-templates/>
					</xsl:if>
				</div>
				<br/>
				<input onclick="jumpButton(-1, event);">
					<xsl:attribute name="type">
						<xsl:value-of select="$input-type"/>
					</xsl:attribute>
					<xsl:if test="$input-type='image'">
						<xsl:attribute name="src">
							<xsl:value-of select="$context-path"/>
							<xsl:text>/buttons/back.png</xsl:text>
						</xsl:attribute>
					</xsl:if>
					<xsl:if test="$input-type='button'">
						<xsl:attribute name="value">
							<xsl:text>&lt;&lt;&lt;</xsl:text>
						</xsl:attribute>
					</xsl:if>
				</input>
				<xsl:if test="$text-to-show and @show-button='yes'">
					<input onclick="showButton(event);">
						<xsl:attribute name="type">
							<xsl:value-of select="$input-type"/>
						</xsl:attribute>
						<xsl:if test="$input-type='image'">
							<xsl:attribute name="src">
								<xsl:value-of select="$context-path"/>
								<xsl:text>/buttons/showcaption.png</xsl:text>
							</xsl:attribute>
						</xsl:if>
						<xsl:if test="$input-type='button'">
							<xsl:attribute name="value">
								<xsl:text>Show Caption</xsl:text>
							</xsl:attribute>
						</xsl:if>
					</input>
				</xsl:if>
				<input onclick="jumpButton(1, event);">
					<xsl:attribute name="type">
						<xsl:value-of select="$input-type"/>
					</xsl:attribute>
					<xsl:if test="$input-type='image'">
						<xsl:attribute name="src">
							<xsl:value-of select="$context-path"/>
							<xsl:text>/buttons/forward.png</xsl:text>
						</xsl:attribute>
					</xsl:if>
					<xsl:if test="$input-type='button'">
						<xsl:attribute name="value">
							<xsl:text>&gt;&gt;&gt;</xsl:text>
						</xsl:attribute>
					</xsl:if>
				</input>
				<br/>
			</xsl:if>
			<br/>
		</xsl:if>
	</xsl:if>
</xsl:template>

<xsl:template match="insert-megasave | insert-image | insert-kin |
                     insert-document-reference | insert-note | insert-user |
                     insert-date | insert-time | phi | block" />

<xsl:template match="ATFI-abstract | ATFI-keywords | ATFI-history | ATFI-findings |
                     ATFI-discussion | ATFI-differential-diagnosis | ATFI-diagnosis |
                     ATFI-anatomy | ATFI-pathology | ATFI-organ-system |
                     ATFI-modality | ATFI-category | ATFI-level" />

<xsl:template match="history | findings | diagnosis | confirmation |
                     differential-diagnosis | discussion |
                     pathology | anatomy | organ-system | modality | rights |
                     publication-date | format | compression |
                     document-type | category | level | lexicon | access |
                     language | creator | document-id">
	<xsl:if test="not(@visible='no')">
		<xsl:apply-templates/>
	</xsl:if>
</xsl:template>

<xsl:template match="years">
	<xsl:apply-templates/>
	<xsl:text> years</xsl:text>
</xsl:template>

<xsl:template match="months">
	<xsl:apply-templates/>
	<xsl:text> months</xsl:text>
</xsl:template>

<xsl:template match="weeks">
	<xsl:apply-templates/>
	<xsl:text> weeks</xsl:text>
</xsl:template>

<xsl:template match="days">
	<xsl:apply-templates/>
	<xsl:text> days</xsl:text>
</xsl:template>

<xsl:template match="code">
	<xsl:if test="not(@visible='no')">
		<xsl:if test="@coding-system">
			<xsl:value-of select="@coding-system"/>
			<xsl:text>:</xsl:text>
		</xsl:if>
		<xsl:value-of select="text()"/>
		<xsl:apply-templates select="meaning"/>
	</xsl:if>
</xsl:template>

<xsl:template match="meaning">
	<xsl:if test="not(@visible='no')">
		(<xsl:apply-templates/>)
	</xsl:if>
</xsl:template>

<xsl:template match="term">
	<xsl:choose>
		<xsl:when test="@lexicon='RadLex'">
			<a class="term" href="http://www.radlex.org/RID/{@id}" target="radlex">
				<xsl:apply-templates/>
			</a>
		</xsl:when>
		<xsl:otherwise>
			<xsl:apply-templates/>
		</xsl:otherwise>
	</xsl:choose>
</xsl:template>

<xsl:template match="revision-history">
	<xsl:if test="not(@visible='no')">
		<center>
			<table border="1" width="80%">
				<thead>
					<td><b>Author</b></td>
					<td><b>Date</b></td>
					<td width="60%"><b>Description</b></td>
				</thead>
				<xsl:apply-templates select="revision"/>
			</table>
		</center>
	</xsl:if>
</xsl:template>

<xsl:template match="revision">
	<tr>
		<td><xsl:apply-templates select="revision-author"/></td>
		<td><xsl:apply-templates select="revision-date"/></td>
		<td><xsl:apply-templates select="revision-description"/></td>
	</tr>
</xsl:template>

<xsl:template match="peer-review">
	<xsl:if test="not(@visible='no')">
		<p>
			<b>Peer Review Status</b><br />
			<table>
				<tr><td>Approval date:</td><td><xsl:apply-templates select="approval-date"/></td></tr>
				<tr><td>Expiration date:</td><td><xsl:apply-templates select="expiration-date"/></td></tr>
				<tr><td>Reviewing authority:</td><td><xsl:apply-templates select="reviewing-authority"/></td></tr>
				<tr><td>Reviewer:</td><td><xsl:apply-templates select="reviewer"/></td></tr>
			</table>
		</p>
	</xsl:if>
</xsl:template>

<xsl:template match="patient">
	<xsl:variable name="show-phi"
				  select="(@visible!='phi-restricted') or ($user-is-owner='yes') or ($user-is-admin='yes')"/>
	<table class="pttable" align="center">
		<xsl:if test="pt-name and $show-phi">
			<tr>
				<td class="ptlabel">Name:</td>
				<td><xsl:value-of select="normalize-space(pt-name)"/></td>
			</tr>
		</xsl:if>
		<xsl:if test="pt-id and $show-phi">
			<tr>
				<td class="ptlabel">ID:</td>
				<td><xsl:value-of select="normalize-space(pt-id)"/></td>
			</tr>
		</xsl:if>
		<xsl:if test="pt-mrn and $show-phi">
			<tr>
				<td class="ptlabel">MRN:</td>
				<td><xsl:value-of select="normalize-space(pt-mrn)"/></td>
			</tr>
		</xsl:if>
		<xsl:if test="pt-age">
			<tr>
				<td class="ptlabel">Age:</td>
				<td>
					<xsl:if test="pt-age/years">
						<xsl:value-of select="pt-age/years"/>
						<xsl:text>&#160;years </xsl:text>
					</xsl:if>
					<xsl:if test="pt-age/months">
						<xsl:value-of select="pt-age/months"/>
						<xsl:text>&#160;months </xsl:text>
					</xsl:if>
					<xsl:if test="pt-age/weeks">
						<xsl:value-of select="pt-age/weeks"/>
						<xsl:text>&#160;weeks </xsl:text>
					</xsl:if>
					<xsl:if test="pt-age/days">
						<xsl:value-of select="pt-age/days"/>
						<xsl:text>&#160;days </xsl:text>
					</xsl:if>
				</td>
			</tr>
		</xsl:if>
		<xsl:if test="pt-sex">
			<tr>
				<td class="ptlabel">Sex:</td>
				<td><xsl:value-of select="normalize-space(pt-sex)"/></td>
			</tr>
		</xsl:if>
		<xsl:if test="pt-race">
			<tr>
				<td class="ptlabel">Race:</td>
				<td><xsl:value-of select="normalize-space(pt-race)"/></td>
			</tr>
		</xsl:if>
		<xsl:if test="pt-species">
			<tr>
				<td class="ptlabel">Species:</td>
				<td><xsl:value-of select="normalize-space(pt-species)"/></td>
			</tr>
		</xsl:if>
		<xsl:if test="pt-breed">
			<tr>
				<td class="ptlabel">Breed:</td>
				<td><xsl:value-of select="normalize-space(pt-breed)"/></td>
			</tr>
		</xsl:if>
	</table>
</xsl:template>

<xsl:template match="metadata-refs">
	<xsl:if test="metadata">
		<table class="mdtable">
			<xsl:apply-templates select="metadata"/>
		</table>
	</xsl:if>
</xsl:template>

<xsl:template match="metadata">
	<tr>
		<td>
			<input type="button">
				<xsl:attribute name="value">
					<xsl:value-of select="type"/>
				</xsl:attribute>
				<xsl:attribute name="onclick">
					<xsl:text>fetchFile('</xsl:text>
					<xsl:value-of select="@href"/>
					<xsl:text>', event);</xsl:text>
				</xsl:attribute>
			</input>
			<br/>
			<xsl:value-of select="date"/>
		</td>
		<td><xsl:value-of select="desc"/></td>
	</tr>
</xsl:template>

<xsl:template match="quiz">
	<p><xsl:apply-templates select="quiz-context"/></p>
	<ol>
		<xsl:apply-templates select="question"/>
	</ol>
</xsl:template>

<xsl:template match="quiz-context | question-body">
	<xsl:apply-templates/>
</xsl:template>

<xsl:template match="question">
	<li>
		<xsl:apply-templates select="question-body"/>
		<p>
			<table border="1" width="80%">
				<xsl:apply-templates select="answer"/>
			</table>
		</p>
	</li>
</xsl:template>

<xsl:template match="answer">
	<xsl:param name="a">RspDiv<xsl:number format="1" level="any"/></xsl:param>
	<tr>
		<td align="center" valign="top" width="10%">
			<button style="width:40">
				<xsl:attribute name="onclick">
					<xsl:text>toggleResponse('</xsl:text>
					<xsl:value-of select="$a"/>
					<xsl:text>')</xsl:text>
				</xsl:attribute>
				<xsl:number format="a"/>
			</button>
		</td>
		<td>
			<xsl:apply-templates select="answer-body"/>
			<xsl:apply-templates select="response">
				<xsl:with-param name="a" select="$a"/>
			</xsl:apply-templates>
		</td>
	</tr>
</xsl:template>

<xsl:template match="answer-body">
	<div>
		<xsl:apply-templates/>
	</div>
</xsl:template>

<xsl:template match="response">
	<xsl:param name="a"/>
	<div class="hide">
		<xsl:attribute name="id">
			<xsl:value-of select="$a"/>
		</xsl:attribute>
		<hr/>
		<xsl:apply-templates/>
	</div>
</xsl:template>

<xsl:template name="radlex">
	<xsl:if test="//term">
		<center>
			<p>
				<input type="button" value="New Search" onclick="newSearch('{$doc-path}');"/>
			</p>
		</center>
	</xsl:if>
</xsl:template>

<xsl:template name="script-init">
	<script>
		var contextPath = '<xsl:value-of select="$context-path"/>';
		var dirPath = '<xsl:value-of select="$dir-path"/>';
		var display = '<xsl:value-of select="$display"/>';
		var inputType = '<xsl:value-of select="$input-type"/>';
		var firstTab = <xsl:choose>
			<xsl:when test="@first-tab"><xsl:value-of select="@first-tab"/></xsl:when>
			<xsl:otherwise>2</xsl:otherwise>
		</xsl:choose>;
		var showEmptyTabs = '<xsl:value-of select="@show-empty-tabs"/>';
		var background = '<xsl:value-of select="$bgcolor"/>';
		var title = "<xsl:value-of select="normalize-space(/MIRCdocument/title)"/>";

		<xsl:if test="//image-section">
			var imageSection = 'yes';

			var imagePaneWidth = <xsl:choose>
				<xsl:when test="//image-section/@image-pane-width">
					<xsl:value-of select="//image-section/@image-pane-width"/>;
				</xsl:when>
				<xsl:otherwise>700;</xsl:otherwise>
			</xsl:choose>

			var IMAGES = new IMAGESECTION();
			<xsl:for-each select="//image-section/image">
				var temp = new IMAGESET();
				<xsl:call-template name="addIMAGE">
					<xsl:with-param name="image" select="."/>
					<xsl:with-param name="type">pImage</xsl:with-param>
				</xsl:call-template>
				<xsl:variable name="sImage" select="alternative-image[@role='annotation' and (@type='image' or (not(@type) and not(contains(@src,'.svg'))))]"/>
				<xsl:if test="$sImage">
					<xsl:call-template name="addIMAGE">
						<xsl:with-param name="image" select="$sImage"/>
						<xsl:with-param name="type">sImage</xsl:with-param>
					</xsl:call-template>
				</xsl:if>
				<xsl:variable name="aImage" select="alternative-image[@role='annotation' and (@type='image' or (not(@type) and not(contains(@src,'.svg'))))]"/>
				<xsl:if test="$aImage">
					<xsl:call-template name="addIMAGE">
						<xsl:with-param name="image" select="$aImage"/>
						<xsl:with-param name="type">aImage</xsl:with-param>
					</xsl:call-template>
				</xsl:if>
				<xsl:variable name="osImage" select="alternative-image[@role='original-dimensions']"/>
				<xsl:if test="$osImage">
					<xsl:call-template name="addIMAGE">
						<xsl:with-param name="image" select="$osImage"/>
						<xsl:with-param name="type">osImage</xsl:with-param>
					</xsl:call-template>
				</xsl:if>
				<xsl:variable name="ofImage" select="alternative-image[@role='original-format']"/>
				<xsl:if test="$ofImage">
					<xsl:call-template name="addIMAGE">
						<xsl:with-param name="image" select="$ofImage"/>
						<xsl:with-param name="type">ofImage</xsl:with-param>
					</xsl:call-template>
				</xsl:if>
				<xsl:variable name="aCaption" select="normalize-space(image-caption[not(@display) or @display='always'])"/>
				<xsl:if test="$aCaption">
					temp.addCAPTION('aCaption', "<xsl:value-of select="$aCaption"/>");
				</xsl:if>
				<xsl:variable name="cCaption" select="normalize-space(image-caption[@display='click'])"/>
				<xsl:if test="$aCaption">
					temp.addCAPTION('cCaption', "<xsl:value-of select="$cCaption"/>");
				</xsl:if>
				IMAGES.addIMAGESET(temp);
			</xsl:for-each>
			IMAGES.load();
		</xsl:if>

		<xsl:if test="not(//image-section)">var imageSection = 'no';
		</xsl:if>

		var radlexTerms = new Array(<xsl:for-each select="//term[not(.=preceding::term)]">
		<xsl:sort select="."/>
			"<xsl:value-of select="."/>"<xsl:if test="position()!=last()">,</xsl:if>
		</xsl:for-each>);

	</script>
	<xsl:text>
</xsl:text>
</xsl:template>

<xsl:template name="addIMAGE">
	<xsl:param name="image"/>
	<xsl:param name="type"/>
	<xsl:variable name="w">
		<xsl:choose>
			<xsl:when test="$image/@w"><xsl:value-of select="$image/@w"/></xsl:when>
			<xsl:otherwise>0</xsl:otherwise>
		</xsl:choose>
	</xsl:variable>
	<xsl:variable name="h">
		<xsl:choose>
			<xsl:when test="$image/@h"><xsl:value-of select="$image/@h"/></xsl:when>
			<xsl:otherwise>0</xsl:otherwise>
		</xsl:choose>
	</xsl:variable>
	temp.addIMAGE('<xsl:value-of select="$type"/>','<xsl:value-of select="$image/@src"/>', <xsl:value-of select="$w"/>, <xsl:value-of select="$h"/>);
</xsl:template>

</xsl:stylesheet>
