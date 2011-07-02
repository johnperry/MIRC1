<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet version="1.1" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output method="html" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="homestring"/>
<xsl:param name="nextstring"/>
<xsl:param name="prevstring"/>
<xsl:param name="randomize"/>

<xsl:template match="/navigatorurls">
 <html>
  <head>
   <title>MIRC Case Navigator</title>
   <xsl:call-template name="style"/>
   <xsl:call-template name="script"/>
  </head>
  <body scroll="no">
   <xsl:call-template name="navdiv"/>
   <iframe id="MIRCinnerframeID" name="MIRCinnerframe">-</iframe>
  </body>
 </html>
</xsl:template>

<xsl:template name="style">
 <style>
  body {margin:0; padding:0}
  iframe {border:1px #aaccdd solid; width:100%; height:25%}
  .navdiv {width:100%; padding:0; background-color:#c6d8f9;}
  .b {width:90}
  .bq {width:90}
  .name {font-weight:bold; font-family:sans-serif; vertical-align:baseline; font-size:small}
  .left {font-weight:bold; font-family:sans-serif; vertical-align:baseline; font-size:larger}
  .middle {font-weight:bold; font-family:sans-serif; vertical-align:baseline}
 </style>
</xsl:template>

<xsl:template name="navdiv">
 <div id="navdiv" class="navdiv">
  <table width="100%">
   <tr valign="center">
    <td align="left" class="left" width="30%">
     MIRC <span class="name">case navigator</span>
    </td>
    <td align="left" width="50%">
     <input type="button" onclick="load(-1)" class="b" value="Prev Case" />
     &#160;<span id="casenumberdisplay" class="middle">Case 1 of 43</span>&#160;
     <input type="button" onclick="load(+1)" class="b" value="Next Case" />
    </td>
    <td width="30">
     <input type="button" class="b" value="Home Page">
      <xsl:attribute name="onclick">go('<xsl:value-of select="$homestring"/>')</xsl:attribute>
     </input>
    </td>
    <td width="30">
     <input type="button" class="bq" value="Prev Page">
      <xsl:attribute name="onclick">go('<xsl:value-of select="$prevstring"/>')</xsl:attribute>
     </input>
    </td>
    <td  width="30">
     <input type="button" class="bq" value="Next Page">
      <xsl:attribute name="onclick">go('<xsl:value-of select="$nextstring"/>')</xsl:attribute>
     </input>
    </td>
   </tr>
  </table>
 </div>
</xsl:template>

<xsl:template name="script">
 <script>
	var caselist = new Array(<xsl:for-each select="//docref">
		"<xsl:value-of select="."/>"<xsl:if test="position() != last()">,</xsl:if>
	</xsl:for-each>);
	<xsl:if test="$randomize = 'yes'">randomizeList = true;
	</xsl:if>
	<xsl:if test="not($randomize = 'yes')">randomizeList = false;
	</xsl:if>
 </script>
 <xsl:text> </xsl:text>
 <script src="/mirc/CaseNavigatorResult.js">
 </script>
</xsl:template>

</xsl:stylesheet>
