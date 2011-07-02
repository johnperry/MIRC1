<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="mode" select="/mirc/@mode"/>
<xsl:param name="options"/>
<xsl:param name="species"/>
<xsl:param name="username"/>
<xsl:param name="localUser"/>
<xsl:param name="localServices"/>
<xsl:param name="news"/>

<xsl:template match="*|@*|text()">
	<xsl:copy>
		<xsl:apply-templates select="*|@*|text()"/>
	</xsl:copy>
</xsl:template>

<xsl:template match="/mirc">
<html>

<head>
	<title>MIRC Query Service</title>
	<link rel="stylesheet" href="mirc.css" type="text/css"/>
	<script language="JavaScript" type="text/javascript" src="mirc.js">;</script>
	<xsl:call-template name="params"/>
	<xsl:call-template name="vet-data"/>
</head>

<body>
	<xsl:call-template name="header"/>
	<xsl:call-template name="menubar"/>
	<div class="main">
		<xsl:call-template name="login"/>
		<div id="pagetitle" class="pagetitle">Search MIRC</div>
		<div class="selectlibraries">
			<input type="button"
				   onclick="showServersPopup();"
				   value="Select Libraries to Search"/>
		</div>
		<form name="queryform" action="" method="POST" target="_self" accept-charset="UTF-8">
			<xsl:call-template name="maincontent"/>
			<xsl:call-template name="altcontent"/>
			<div class="search">
				<input id="go" type="submit" value="Go"
						onclick="setStatusLine('Searching...');setCookies();">
				</input>
			</div>
			<xsl:call-template name="serversPopup"/>
			<input type="hidden" id="querypage" name="querypage" value="altcontent"/>
		</form>
		<xsl:call-template name="news"/>
	</div>

	<xsl:call-template name="menus"/>
	<xsl:call-template name="popups"/>
	<xsl:call-template name="searchforms"/>
</body>

</html>
</xsl:template>

<xsl:template name="header">
<div class="header" style="background:url({@masthead}); background-repeat: no-repeat;">
	<xsl:if test="@showsitename='yes'">
		<div class="sitename">
			<span><xsl:value-of select="@sitename"/></span>
		</div>
	</xsl:if>
	<div class="sitelogo" style="height:{@mastheadheight}">&#160;</div>
</div>
</xsl:template>

<xsl:template name="menubar">
<div id="menubar" class="menubar" onmouseout="checkHideMenus(event);">
	<div class="links">
		<xsl:choose>
			<xsl:when test="$username">You are logged in as...</xsl:when>
			<xsl:otherwise>
				<xsl:if test="not(@showlogin='no')">
					<xsl:text>Login to Access All Features</xsl:text>
				</xsl:if>
				<xsl:if test="@showlogin='no'">&#160;</xsl:if>
			</xsl:otherwise>
		</xsl:choose>
	</div>
	<div id="menuDiv">
		<xsl:call-template name="menu-titles"/>
	</div>
</div>
</xsl:template>

<xsl:template name="login">
<div id="login" class="login">
	<xsl:choose>
		<xsl:when test="$username"><xsl:value-of select="$username"/></xsl:when>
		<xsl:otherwise>
			<xsl:if test="not(@showlogin='no')">
				<input class="login" type="button" value="Login"
					onclick="login('{queryservice/@address}');" />
			</xsl:if>
			<xsl:if test="@showlogin='no'">&#160;</xsl:if>
		</xsl:otherwise>
	</xsl:choose>
</div>
</xsl:template>

<xsl:template name="news">
	<div class="news" id="news">
		<xsl:choose>
			<xsl:when test="string-length($news/news/image) != 0">
				<a href="{$news/news/url}" target="cod">
					<img src="{$news/news/image}" width="128"/>
				</a>
				<xsl:text> </xsl:text>
				<a href="{$news/news/url}" target="cod">Case of the Day</a>
			</xsl:when>
			<xsl:when test="$news/news/title">
				<br/>Today's Interesting Document:<br/>
				<a href="{$news/news/url}" target="cod">
					<xsl:value-of select="$news/news/title"/>
				</a>
			</xsl:when>
		</xsl:choose>
	</div>
</xsl:template>

<xsl:template name="maincontent">
<div class="maincontent hide" id="maincontent">
	<table width="90%">
		<tr>
			<td align="center" valign="top" style="height:30px; width:75%;">
				<xsl:call-template name="page-buttons"/>
			</td>
			<td valign="top" rowspan="2"
				style="font-family:sans-serif;font-size:10pt; padding-left:10px;padding-top:25;">
				<xsl:call-template name="query-modifiers"/>
			</td>
		</tr>
		<tr>
			<td valign="top"><xsl:call-template name="query-pages"/></td>
			<td/>
		</tr>
	</table>
</div>
</xsl:template>

<xsl:template name="altcontent">
<div class="maincontent hide" id="altcontent">
	<p style="width:60%; font-size:9pt; font-family:sans-serif; text-align:left;
	padding:0; margin:0 auto; margin-top: 10px; margin-bottom:0px;">
	<span style="font-weight:bold; font-size:18; color:blue;">W</span>elcome
	to the Medical Imaging Resource Center search page.
	Enter search criteria in the field below and click the
	<span style="color:blue; font-weight:bold;">Go</span> button.</p>

	<input style="width:60%" name="altdocument" id="altfreetext"></input>

	<p style="width:60%; font-size:9pt; font-family:sans-serif; text-align:right;
	padding:0; margin:0 auto; margin-top:0px; margin-bottom:0px;">
	<a style="text-decoration:underline; color:black;" href="javascript:showDiv('maincontent');">
		more search fields...
	</a>
	</p>
</div>
</xsl:template>

<xsl:template name="query-modifiers">
	<input type="checkbox" name="unknown" id="unknown" value="yes"
		title="Format documents as unknown cases">Display as unknowns</input>
	<br />
	<input type="checkbox" name="showimages" id="showimages" value="yes"
		title="Show images in query results">Show images in results</input>
	<br />
	<input type="checkbox" name="casenavigator" id="casenavigator" value="yes"
		title="Display results in the Case Navigator">Case navigator</input>
	<br />
	<input type="checkbox" name="randomize" id="randomize" value="yes"
		title="Randomly order results in the Case Navigator">Randomize results</input>
	<br />
	<input type="checkbox" name="icons" id="icons" value="no"
		title="Hide the icon images in MSTF and Tab displays">Suppress icon images</input>
	<br/>
	<div class="formatselect">
		<select name="maxresults" id="maxresults" title="Choose the maximum number of results per site">
			<option value="10">10 results/site</option>
			<option value="25" selected="">25 results/site</option>
			<option value="50">50 results/site</option>
			<option value="100">100 results/site</option>
			<option value="500">500 results/site</option>
		</select>
	</div>
	<div class="formatselect">
		<select name="display" id="display" title="Choose the format in which documents will be displayed">
			<option value="" selected="">Document format</option>
			<option value="page">Page format</option>
			<option value="tab">Tab format</option>
			<option value="mstf">MSTF format</option>
		</select>
	</div>
	<div class="formatselect">
		<select name="bgcolor" id="bgcolor" title="Choose the background shade for display">
			<option value="" selected="">Document background</option>
			<option value="light">Light background</option>
			<option value="dark">Dark background</option>
		</select>
	</div>
</xsl:template>

<xsl:template name="page-buttons">
	<input class="tab" type="button"
						onclick="bclick('div1',event)" value="Basic" id="page1tab"/>
	<input class="tab" type="button"
						onclick="bclick('div2',event)" value="Document" id="page2tab"/>
	<input class="tab" type="button"
						onclick="bclick('div3',event)" value="Content" id="page3tab"/>
	<input class="tab" type="button"
						onclick="bclick('div4',event)" value="Clinical" id="page4tab"/>
	<input class="tab" type="button"
						onclick="bclick('div5',event)" value="Image" id="page5tab"/>
	<input class="tab" type="button"
						onclick="bclick('div6',event)" value="Patient" id="page6tab"/>
</xsl:template>

<xsl:template name="query-pages">
	<div id="div1" style="visibility:visible;display:block">
		<xsl:call-template name="basic"/>
	</div>
	<div id="div2" style="visibility:hidden;display:none">
		<xsl:call-template name="document"/>
	</div>
	<div id="div3" style="visibility:hidden;display:none">
		<xsl:call-template name="content"/>
	</div>
	<div id="div4" style="visibility:hidden;display:none">
		<xsl:call-template name="clinical"/>
	</div>
	<div id="div5" style="visibility:hidden;display:none">
		<xsl:call-template name="image"/>
	</div>
	<div id="div6" style="visibility:hidden;display:none">
		<xsl:call-template name="patient"/>
	</div>
</xsl:template>

<xsl:template name="basic">
	<table width="100%" border="1">
		<tr>
			<td width="25%">Free Text Search:</td>
			<td><input style="width:100%" name="document" id="freetext"></input></td>
		</tr>
		<tr>
			<td>Title:</td>
			<td><input style="width:100%" name="title"></input></td>
		</tr>
		<tr>
			<td>Author:</td>
			<td><input style="width:100%" name="author"></input></td>
		</tr>
		<tr>
			<td>Abstract:</td>
			<td><input style="width:100%" name="abstract"></input></td>
		</tr>
		<tr>
			<td>Keywords:</td>
			<td><input style="width:100%" name="keywords"></input></td>
		</tr>
	</table>
</xsl:template>

<xsl:template name="document">
	<table width="100%" border="1">
		<tr>
			<td width="25%">Document type:</td>
			<td>
				<select style="width:100%" name="document-type">
					<xsl:copy-of select="$options/enumerated-values/document-type/option"/>
				</select>
			</td>
		</tr>
		<tr>
			<td>Category:</td>
			<td>
				<select style="width:100%" name="category">
					<xsl:copy-of select="$options/enumerated-values/category/option"/>
				</select>
			</td>
		</tr>
		<tr>
			<td>Level:</td>
			<td>
				<select style="width:100%" name="level">
					<xsl:copy-of select="$options/enumerated-values/level/option"/>
				</select>
			</td>
		</tr>
		<tr>
			<td>Access:</td>
			<td>
				<select style="width:100%" name="access">
					<xsl:copy-of select="$options/enumerated-values/access/option"/>
				</select>
			</td>
		</tr>
		<tr>
			<td>Language:</td>
			<td><input style="width:100%" name="language"></input></td>
		</tr>
		<tr>
			<td>Peer-review:</td>
			<td>
				<input type="checkbox" name="peer-review" value="yes">Peer-reviewed documents only</input>
			</td>
		</tr>
	</table>
</xsl:template>

<xsl:template name="content">
	<table width="100%" border="1">
		<tr>
			<td width="25%">History:</td>
			<td><input style="width:100%" name="history"></input></td>
		</tr>
		<tr>
			<td>Findings:</td>
			<td><input style="width:100%" name="findings"></input></td>
		</tr>
		<tr>
			<td>Diagnosis:</td>
			<td><input style="width:100%" name="diagnosis"></input></td>
		</tr>
		<tr>
			<td>Differential Diagnosis:</td>
			<td><input style="width:100%" name="differential-diagnosis"></input></td>
		</tr>
		<tr>
			<td>Discussion:</td>
			<td><input style="width:100%" name="discussion"></input></td>
		</tr>
	</table>
</xsl:template>

<xsl:template name="clinical">
	<table width="100%" border="1">
		<tr>
			<td width="25%">Anatomy:</td>
			<td><input style="width:100%" name="anatomy"></input></td>
		</tr>
		<tr>
			<td>Pathology:</td>
			<td><input style="width:100%" name="pathology"></input></td>
		</tr>
		<tr>
			<td>Organ system:</td>
			<td><input style="width:100%" name="organ-system"></input></td>
		</tr>
		<tr>
			<td>Modalities:</td>
			<td><input style="width:100%" name="modality"></input></td>
		</tr>
		<tr>
			<td>Code:</td>
			<td><input style="width:100%" name="code"></input></td>
		</tr>
	</table>
</xsl:template>

<xsl:template name="image">
	<table width="100%" border="1">
		<tr>
			<td width="25%">Format:</td>
			<td>
				<select style="width:100%" NAME="imageformat">
					<xsl:copy-of select="$options/enumerated-values/imageformat/option"/>
				</select>
			</td>
		</tr>
		<tr>
			<td>Compression:</td>
			<td>
				<select style="width:100%" NAME="imagecompression">
					<xsl:copy-of select="$options/enumerated-values/imagecompression/option"/>
				</select>
			</td>
		</tr>
		<tr>
			<td>Modality:</td>
			<td><input style="width:100%" name="imagemodality"></input></td>
		</tr>
		<tr>
			<td>Anatomy:</td>
			<td><input style="width:100%" name="imageanatomy"></input></td>
		</tr>
		<tr>
			<td>Pathology:</td>
			<td><input style="width:100%" name="imagepathology"></input></td>
		</tr>
	</table>
</xsl:template>

<xsl:template name="patient">
	<table width="100%" border="1">
		<xsl:if test="not(@showptids='no')">
			<tr>
				<td width="25%">Name:</td>
				<td><input style="width:100%" name="pt-name"></input></td>
			</tr>
			<tr>
				<td>ID:</td>
				<td><input style="width:100%" name="pt-id"></input></td>
			</tr>
			<tr>
				<td>MRN:</td>
				<td><input style="width:100%" name="pt-mrn"></input></td>
			</tr>
		</xsl:if>
		<tr>
			<td>Age (min [-max]):</td>
			<td>
				<input style="width:12%" name="years"></input>Years;
				<input style="width:12%" name="months"></input>Months;
				<input style="width:12%" name="weeks"></input>Weeks;
				<input style="width:12%" name="days"></input>Days
			</td>
		</tr>
		<tr>
			<td>Sex:</td>
			<td>
				<select style="width:100%" NAME="pt-sex">
					<xsl:if test="$mode='rad' or $mode=''">
						<xsl:copy-of select="$options/enumerated-values/rad-pt-sex/option"/>
					</xsl:if>
					<xsl:if test="$mode='vet'">
						<xsl:copy-of select="$options/enumerated-values/vet-pt-sex/option"/>
					</xsl:if>
				</select>
			</td>
		</tr>
		<xsl:if test="$mode='rad' or $mode=''">
			<tr>
				<td>Race:</td>
				<td><input style="width:100%" name="pt-race"></input></td>
			</tr>
		</xsl:if>
		<xsl:if test="$mode='vet'">
			<tr>
				<td>Species:</td>
				<td>
					<select style="width:100%" name="pt-species" id="pt-species" onchange="setBreedList()">
						<option value=""/>
						<xsl:for-each select="$species/vet/species">
							<option value="{@name}">
								<xsl:value-of select="@name"/>
							</option>
						</xsl:for-each>
					</select>
				</td>
			</tr>
			<tr>
				<td>Breed:</td>
				<td>
					<select style="width:100%" id="pt-breed" name="pt-breed">
						<option value=""></option>
					</select>
				</td>
			</tr>
		</xsl:if>
	</table>
</xsl:template>

<xsl:template name="menu-titles">
	<span class="show" id="MyStuffMenu">
		<xsl:text>&#160;&#160;</xsl:text>
		<span id="myStuffTitle">
			<a href="javascript:showMenu('myStuffTitle','myStuff');">My Stuff</a>
		</span>
		<xsl:text>&#160;&#160;&#160;|&#160;</xsl:text>
	</span>
	<span class="show" id="SearchMenu">
		<xsl:text>&#160;&#160;</xsl:text>
		<span id="queryTitle">
			<a href="javascript:showMenu('queryTitle','query');">Search</a>
		</span>
		<xsl:text>&#160;&#160;&#160;|&#160;</xsl:text>
	</span>
	<span class="show" id="ToolsMenu">
		<xsl:text>&#160;&#160;</xsl:text>
		<span id="toolsTitle">
			<a href="javascript:showMenu('toolsTitle','tools');">Author</a>
		</span>
		<xsl:text>&#160;&#160;&#160;|&#160;</xsl:text>
	</span>
	<span class="show" id="AdminMenu">
		<xsl:text>&#160;&#160;</xsl:text>
		<span id="adminTitle">
			<a href="javascript:showMenu('adminTitle','admin');">Admin</a>
		</span>
		<xsl:text>&#160;&#160;&#160;|&#160;</xsl:text>
	</span>
	<span class="show" id="HelpMenu">
		<xsl:text>&#160;&#160;</xsl:text>
		<span id="helpTitle">
			<a href="javascript:showMenu('helpTitle','help');">Help</a>
		</span>
	</span>
</xsl:template>

<xsl:template name="menus">
	<xsl:call-template name="myStuff"/>
	<xsl:call-template name="query"/>
	<xsl:call-template name="tools"/>
	<xsl:call-template name="admin"/>
	<xsl:call-template name="help"/>
	<xsl:call-template name="storedQueries"/>
	<xsl:call-template name="localServices"/>
	<xsl:call-template name="localAdmins"/>
</xsl:template>

<xsl:template name="myStuff">
<div class="menu" id="myStuff" onmouseout="checkHideMenus(event);">
	<xsl:if test="$username != ''">
		<a href="/file/service" onclick="hideMenus();">My Files</a><br/>
		<span class="hide">
			<a href="javascript:menuItemSelected();" onclick="hideMenus();">My Queries</a><br/>
		</span>
	</xsl:if>
	<xsl:if test="($username != '') or (accounts/@enabled = 'yes')">
		<a href="/mircadmin/user" onclick="hideMenus();">My Account</a><br/>
	</xsl:if>
	<xsl:if test="$username = ''">&#160;</xsl:if>
</div>
</xsl:template>

<xsl:template name="query">
<div class="menu" id="query" onmouseout="checkHideMenus(event);">
	<a href="javascript:showDiv('altcontent');" onclick="hideMenus();">Show Primary Search Page</a><br/>
	<a href="javascript:showDiv('maincontent');" onclick="hideMenus();">Show All Search Fields</a><br/>
	<hr/>
	<a href="javascript:clearQueryFields();" onclick="hideMenus();">Erase All Search Fields</a><br/>
	<hr/>
	<span class="hide">
		<a href="javascript:selectAllServers();" onclick="hideMenus();">Select All Libraries</a><br/>
		<a id="sTitle" href="javascript:showSubmenu('sTitle','servers',1);">Select Libraries</a><br/>
		<hr/>
		<a href="javascript:menuItemSelected();" onclick="hideMenus();">Save Current Search</a><br/>
		<a id="qsTitle" href="javascript:menuItemSelected();" onclick="hideMenus();">Select Stored Search</a><br/>
		<hr/>
	</span>
	<a href="javascript:searchExternalEngine('searchGoldminer','query');" onclick="hideMenus();">Search Goldminer</a><br/>
	<a href="javascript:searchExternalEngine('searchGoogleScholar','q');" onclick="hideMenus();">Search Google Scholar</a><br/>
	<a href="javascript:searchExternalEngine('searchYottaLook','q');" onclick="hideMenus();">Search YottaLook</a><br/>
	<a href="javascript:searchExternalEngine('searchYottaLookImages','q');" onclick="hideMenus();">Search YottaLook Images</a><br/>
</div>
</xsl:template>

<xsl:template name="tools">
<div class="menu" id="tools" onmouseout="checkHideMenus(event);">
	<span id="currentservice">x</span><br/>
	<a id="lsTitle" href="javascript:showSubmenu('lsTitle','localServices',1);">Select Local Library</a><br/>
	<span class="show" id="AuthorServices">
		<hr/>
		<a href="javascript:gotoLocalService('/newdoc');" onclick="hideMenus();">Basic Author Tool</a><br/>
		<a href="javascript:gotoLocalService('/abrdoc');" onclick="hideMenus();">ABR Author Tool</a><br/>
		<a href="javascript:gotoLocalService('/author');" onclick="hideMenus();">Advanced Author Tool</a><br/>
		<a href="javascript:gotoLocalService('/zip');" onclick="hideMenus();">Zip Service</a><br/>
		<a href="javascript:gotoLocalService('/submit/doc');" onclick="hideMenus();">Submit Service</a><br/>
		<a href="javascript:gotoLocalService('/summary');" onclick="hideMenus();">Author Summary Report</a><br/>
		<span class="show" id="InputQueueViewerItem">
			<a href="javascript:gotoLocalService('/inputqueueviewer');" onclick="hideMenus();">
				<xsl:text>Input Queue Viewer</xsl:text>
			</a>
			<br/>
		</span>
	</span>
</div>
</xsl:template>

<xsl:template name="admin">
<div class="menu" id="admin" onmouseout="checkHideMenus(event);">
	<a href="/mircadmin/logviewer" onclick="hideMenus();">Log Viewer</a><br/>
	<a href="/mircadmin/controller" onclick="hideMenus();">Controller</a><br/>
	<a href="/mircadmin/sysprops" onclick="hideMenus();">System Properties</a><br/>
	<a href="/mircadmin/users" onclick="hideMenus();">User Role Manager</a><br/>
	<a href="/mirc/admin" onclick="hideMenus();">Query Service Admin</a><br/>
	<a href="/file/admin" onclick="hideMenus();">File Service Admin</a><br/>
	<a id="aTitle" href="javascript:showSubmenu('aTitle','localAdmins',1);">Storage Service Admin</a><br/>
</div>
</xsl:template>

<xsl:template name="help">
<div class="menu" id="help" onmouseout="checkHideMenus(event);">
	<a href="javascript:showPopupFrame('helpPopup',400,400,'helpPage','/mirc/MIRChelp.html');" onclick="hideMenus();">Basic Instructions</a><br/>
	<a href="http://mircwiki.rsna.org" onclick="hideMenus();">RSNA MIRC Wiki</a><br/>
	<a href="http://forums.rsna.org/forumdisplay.php?forumid=9" onclick="hideMenus();">RSNA MIRC Forum</a><br/>
	<a href="http://mircwiki.rsna.org/index.php?title=Downloads" onclick="hideMenus();">RSNA MIRC Software</a><br/>
	<xsl:if test="normalize-space(@disclaimerurl) != ''">
		<a href="{@disclaimerurl}" target="_blank" onclick="hideMenus();">Disclaimer</a><br/>
	</xsl:if>
	<a href="javascript:showPopup('aboutPopup',300,200);" onclick="hideMenus();">About MIRC</a><br/>
</div>
</xsl:template>

<xsl:template name="storedQueries">
<div class="menuSubmenu" id="storedQueries" onmouseout="checkHideMenus(event);">
	<xsl:for-each select="server">
		<xsl:if test="position()=1"><span class="larr">&#171;</span></xsl:if>
		<xsl:if test="position()!=1"><span class="larr">&#160;</span></xsl:if>
		<a href="javascript:menuItemSelected();" onclick="hideMenus();">
			<xsl:value-of select="."/>
		</a>
		<br/>
	</xsl:for-each>
</div>
</xsl:template>

<xsl:template name="localServices">
<div class="menuSubmenu" id="localServices" onmouseout="checkHideMenus(event);">
	<xsl:for-each select="$localServices/local-services/storage">
		<xsl:if test="position()=1"><span class="larr">&#171;</span></xsl:if>
		<xsl:if test="position()!=1"><span class="larr">&#160;</span></xsl:if>
		<a href="javascript:localServiceSelected({position()});">
			<xsl:value-of select="."/>
		</a><br/>
	</xsl:for-each>
</div>
</xsl:template>

<xsl:template name="localAdmins">
<div class="menuSubmenu" id="localAdmins" onmouseout="checkHideMenus(event);">
	<xsl:for-each select="$localServices/local-services/storage">
		<xsl:if test="position()=1"><span class="larr">&#171;</span></xsl:if>
		<xsl:if test="position()!=1"><span class="larr">&#160;</span></xsl:if>
		<a href="javascript:gotoLocalAdmin({position()});">
			<xsl:value-of select="."/>
		</a><br/>
	</xsl:for-each>
</div>
</xsl:template>

<xsl:template name="popups">
	<xsl:call-template name="aboutPopup"/>
	<xsl:call-template name="helpPopup"/>
</xsl:template>

<xsl:template name="serversPopup">
<div class="popup" id="serversPopup">
	<div class="titlebar" onmousedown="startDrag(this.parentNode, event);">
		<div class="closebox">
			<img src="/mirc/images/closebox.gif" onclick="hidePopups();" title="Close"/>
		</div>
		<p>Select Libraries</p>
	</div>
	<div class="content">
		<select id="serverselect" name="server" multiple="true">
			<xsl:for-each select="server[not(@enabled) or @enabled='yes']">
				<option>
					<xsl:attribute name="value"><xsl:number/></xsl:attribute>
					<xsl:if test="position()=1">
						<xsl:attribute name="selected"></xsl:attribute>
					</xsl:if>
					<xsl:value-of select="."/>
				</option>
			</xsl:for-each>
		</select>
		<input type="button" class="stdbutton" value="Select All" onclick="selectAllServers();"/>
		<input type="button" class="stdbutton" value="OK" onclick="hidePopups();"/>
	</div>
</div>
</xsl:template>

<xsl:template name="aboutPopup">
<div class="popup" id="aboutPopup">
	<div class="titlebar" onmousedown="startDrag(this.parentNode, event);">
		<div class="closebox">
			<img src="/mirc/images/closebox.gif" onclick="hidePopups();" title="Close"/>
		</div>
		<p>&#160;</p>
	</div>
	<div class="content">
		<h1>RSNA MIRC</h1>
		<p>Version <xsl:value-of select="/mirc/@version"/></p>
		<p><xsl:value-of select="/mirc/@date"/></p>
	</div>
</div>
</xsl:template>

<xsl:template name="helpPopup">
<div class="popup" id="helpPopup">
	<div class="titlebar" onmousedown="startDrag(this.parentNode, event);">
		<div class="closebox">
			<img src="/mirc/images/closebox.gif" onclick="hidePopups();" title="Close"/>
		</div>
		<p>&#160;</p>
	</div>
	<div class="content">
		<iframe id="helpPage" name="helpPage">-</iframe>
	</div>
</div>
</xsl:template>

<xsl:template name="searchforms">
	<xsl:call-template name="searchYottaLook"/>
	<xsl:call-template name="searchYottaLookImages"/>
	<xsl:call-template name="searchGoogleScholar"/>
	<xsl:call-template name="searchGoldminer"/>
</xsl:template>

<xsl:template name="searchYottaLook">
	<div class="hide" id="searchYottaLook">
		<form method="get" action="http://www.yottalook.com/external_app.php" target="_blank">
			<input type="hidden" name="action" value="submitted"/>
			<input type="hidden" name="q" size="35"/>
			<input type="hidden" value="9846010dcaddbf4c0e652a4287badc5c" name="app_id"/>
    	</form>
    </div>
</xsl:template>

<xsl:template name="searchYottaLookImages">
	<div class="hide" id="searchYottaLookImages">
		<form method="get" action="http://www.yottalook.com/results_img.php" target="_blank">
			<input type="hidden" name="action" value="submitted"/>
			<input type="hidden" name="q" size="35"/>
			<input type="hidden" name="mod" value="all"/>
			<input type="hidden" value="0" name="modlist"/>
			<input type="hidden" value="y" name="init"/>
			<input type="hidden" name="ys" value="1"/>
			<input type="hidden" name="yl" value="1"/>
			<input type="hidden" name="mode" value="mirc"/>
    	</form>
    </div>
</xsl:template>

<xsl:template name="searchGoogleScholar">
	<div class="hide" id="searchGoogleScholar">
		<form action="http://www.google.com/scholar" target="_blank">
			<input type="hidden" maxlength="256" size="40" name="q" value=""/>
			<input type="hidden" name="hl" value="en"/>
			<input type="hidden" name="lr" value=""/>
		</form>
	</div>
</xsl:template>

<xsl:template name="searchGoldminer">
	<div class="hide" id="searchGoldminer">
		<form action="http://goldminer.arrs.org/search.php" method="POST" target="_blank">
			<input type="hidden" size="60" name="query" value=""/>
		</form>
	</div>
</xsl:template>


<xsl:template name="params">
	<script>
		var	startpage = "<xsl:value-of select="@startpage"/>";
		var serverURL = "<xsl:value-of select="@siteurl"/>";
		var currentservice = 0;
		var tomcat = new Tomcat(
			"<xsl:value-of select="$localServices/local-services/@siteurl"/>",
			"<xsl:value-of select="$localServices/local-services/@admin"/>"
			);
		var query = new QueryService(
			"<xsl:value-of select="$localServices/local-services/query/@admin"/>",
			"<xsl:value-of select="$localServices/local-services/query/@user"/>"
			);
		var file = new FileService(
			"<xsl:value-of select="$localServices/local-services/file/@admin"/>",
			"<xsl:value-of select="$localServices/local-services/file/@user"/>"
			);
		var storage = <xsl:text>new Array(</xsl:text>
			<xsl:for-each select="$localServices/local-services/storage">
				new StorageService(
					"<xsl:value-of select="@context"/>",
					"<xsl:value-of select="@admin"/>",
					"<xsl:value-of select="@author"/>",
					"<xsl:value-of select="@publisher"/>",
					"<xsl:value-of select="@update"/>",
					"<xsl:value-of select="@user"/>",
					"<xsl:value-of select="normalize-space(.)"/>"
				<xsl:text>)</xsl:text>
				<xsl:if test="position() != last()">,</xsl:if>
			</xsl:for-each>
			);
		var username = "<xsl:value-of select="$username"/>";
		var acctenb = "<xsl:value-of select="accounts/@enabled"/>";
		var roles = new Array(
			<xsl:for-each select="$localUser/user/role">
				"<xsl:value-of select="."/>
				<xsl:text>"</xsl:text>
				<xsl:if test="position()!=last()">,</xsl:if>
			</xsl:for-each>
			);
	</script>
</xsl:template>

<xsl:template name="vet-data">
	<xsl:if test="$mode='vet'">
		<script>
		breeds = new Array(
			new Array(""),
			<xsl:for-each select="$species/vet/species">
				new Array(
					<xsl:for-each select="breed">
						<xsl:text>"</xsl:text>
						<xsl:value-of select="@name"/>
						<xsl:text>"</xsl:text>
						<xsl:if test="position()!=last()">,</xsl:if>
					</xsl:for-each>
				)<xsl:if test="position()!=last()">,</xsl:if>
			</xsl:for-each>
		);
		</script>
	</xsl:if>
</xsl:template>

</xsl:stylesheet>