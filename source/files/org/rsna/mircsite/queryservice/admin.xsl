<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:template match="/mirc">
	<html>
		<head>
			<title>Query Service Admin</title>
			<link rel="Stylesheet" type="text/css" media="all"
				href="admin.css">
			</link>
		</head>
		<body>
			<div class="closebox">
				<img src="/mirc/images/closebox.gif"
					 onclick="window.open('/mirc/query','_self');"
					 title="Return to the MIRC home page"/>
			</div>

			<h1>Query Service Admin</h1>
			<form action="" method="POST" accept-charset="UTF-8">

			<p class="note">
				The table below controls the primary configuration parameters
				of the system. Changes in the items marked with an asterisk
				require Tomcat to be restarted to become effective.
			</p>

			<center>
				<table border="1">
					<tr>
						<td>System mode:</td>
						<td>
							<input type="radio" name="mode" value="rad">
								<xsl:if test="@mode='rad' or @mode=''">
									<xsl:attribute name="checked"/>
								</xsl:if>
								Radiology
							</input>
							<br/>
							<input type="radio" name="mode" value="vet">
								<xsl:if test="@mode='vet'">
									<xsl:attribute name="checked"/>
								</xsl:if>
								Veterinary Medicine
							</input>
						</td>
					</tr>
					<tr>
						<td>Site name:</td>
						<td><input class="text" type="text" name="sitename" value="{@sitename}"/></td>
					</tr>
					<tr>
						<td>Masthead file name:</td>
						<td><input class="text" type="text" name="masthead" value="{@masthead}"/></td>
					</tr>
					<tr>
						<td>Show site name in masthead:</td>
						<td>
							<input type="radio" name="showsitename" value="yes">
								<xsl:if test="@showsitename='yes'">
									<xsl:attribute name="checked"/>
								</xsl:if>
								yes
							</input>
							<br/>
							<input type="radio" name="showsitename" value="no">
								<xsl:if test="@showsitename='no'">
									<xsl:attribute name="checked"/>
								</xsl:if>
								no
							</input>
						</td>
					</tr>
					<tr>
						<td>Default starting page:</td>
						<td>
							<input type="radio" name="startpage" value="altcontent">
								<xsl:if test="@startpage='altcontent'">
									<xsl:attribute name="checked"/>
								</xsl:if>
								Free-text search field only
							</input>
							<br/>
							<input type="radio" name="startpage" value="maincontent">
								<xsl:if test="@startpage='maincontent'">
									<xsl:attribute name="checked"/>
								</xsl:if>
								All search fields
							</input>
						</td>
					</tr>
					<tr>
						<td>Show login button:</td>
						<td>
							<input type="radio" name="showlogin" value="yes">
								<xsl:if test="not(@showlogin='no')">
									<xsl:attribute name="checked"/>
								</xsl:if>
								yes
							</input>
							<br/>
							<input type="radio" name="showlogin" value="no">
								<xsl:if test="@showlogin='no'">
									<xsl:attribute name="checked"/>
								</xsl:if>
								no
							</input>
						</td>
					</tr>
					<tr>
						<td>Show Patient ID fields:</td>
						<td>
							<input type="radio" name="showptids" value="yes">
								<xsl:if test="not(@showptids='no')">
									<xsl:attribute name="checked"/>
								</xsl:if>
								yes
							</input>
							<br/>
							<input type="radio" name="showptids" value="no">
								<xsl:if test="@showptids='no'">
									<xsl:attribute name="checked"/>
								</xsl:if>
								no
							</input>
						</td>
					</tr>
					<tr>
						<td>Site URL(*):</td>
						<td><input class="text" type="text" name="siteurl" value="{@siteurl}"/></td>
					</tr>
					<tr>
						<td>Address type(*):</td>
						<td>
							<input type="radio" name="addresstype" value="dynamic">
								<xsl:if test="not(@addresstype='static')">
									<xsl:attribute name="checked"/>
								</xsl:if>
								dynamic
							</input>
							<br/>
							<input type="radio" name="addresstype" value="static">
								<xsl:if test="@addresstype='static'">
									<xsl:attribute name="checked"/>
								</xsl:if>
								static
							</input>
						</td>
					</tr>
					<tr>
						<td>Query timeout (in seconds, default=10) :</td>
						<td><input class="text" type="text" name="timeout" value="{@timeout}"/></td>
					</tr>
					<tr>
						<td>Disclaimer URL (blank to disable):</td>
						<td><input class="text" type="text" name="disclaimerurl" value="{@disclaimerurl}"/></td>
					</tr>
				</table>
			</center>

			<p class="note">
				The table below controls the proxy server configuration parameters.
				If the site is behind a proxy server, enter its IP address and port.
				If the proxy server requires authentication, enter the username and
				password. If the site is not behind a proxy server, blank the IP field.
			</p>

			<center>
				<table border="1">
					<tr>
						<td>Proxy server IP address (blank to disable):</td>
						<td><input class="text" type="text" name="proxyip" value="{proxyserver/@ip}"/></td>
					</tr>
					<tr>
						<td>Proxy server port:</td>
						<td><input class="text" type="text" name="proxyport" value="{proxyserver/@port}"/></td>
					</tr>
					<tr>
						<td>Proxy server username (blank if not required):</td>
						<td><input class="text" type="text" name="proxyusername" value="{proxyserver/@username}"/></td>
					</tr>
					<tr>
						<td>Proxy server password:</td>
						<td><input class="text" type="text" name="proxypassword" value="{proxyserver/@password}"/></td>
					</tr>
				</table>
			</center>

			<p class="note">
				The table below controls the account and group creation system.
				To enable account and group creation, check the corresponding
				boxes. To add or change the default roles assigned to all
				new accounts, list the role names separated by spaces or commas.
			</p>

			<center>
				<table border="1">
					<tr>
						<td>Enable account creation:</td>
						<td>
							<input type="checkbox" name="acctenb" value="yes">
								<xsl:if test="accounts/@enabled = 'yes'">
									<xsl:attribute name="checked">
										<xsl:text>true</xsl:text>
									</xsl:attribute>
								</xsl:if>
							</input>
						</td>
					</tr>
					<tr>
						<td>Enable group creation:</td>
						<td>
							<input type="checkbox" name="gpenb" value="yes">
								<xsl:if test="accounts/groups/@enabled = 'yes'">
									<xsl:attribute name="checked">
										<xsl:text>true</xsl:text>
									</xsl:attribute>
								</xsl:if>
							</input>
						</td>
					</tr>
					<tr>
						<td>Default roles:</td>
						<td>
							<input class="text" type="text" name="defroles" value="{accounts/@roles}"/>
						</td>
					</tr>
				</table>
			</center>

			<p class="note">
				The table below controls the storage service list. To enable a
				storage service, check its checkbox in the list. To delete a
				storage service from the list, erase its name in the table.
				To add a new storage service, fill in its parameters in
				the blank fields in the last row.
			</p>

			<center>
				<table border="1">
					<tr>
						<th>Enable</th>
						<th>Storage Service Name</th>
						<th>Storage Service URL</th>
					</tr>
					<xsl:for-each select="server">
						<xsl:variable name="n"><xsl:number/></xsl:variable>
						<tr>
							<td>
								<input type="checkbox" name="enb{$n}" value="yes">
									<xsl:if test="not(@enabled) or @enabled='yes' or @enabled=''">
										<xsl:attribute name="checked"/>
									</xsl:if>
								</input>
							</td>
							<td><input class="svrtext" name="name{$n}" type="text" value="{normalize-space(.)}"/></td>
							<td><input class="svrtext" name="adrs{$n}" type="text" value="{@address}"/></td>
						</tr>
					</xsl:for-each>
					<tr>
						<td><input type="checkbox" name="enb0" value="yes"/></td>
						<td><input class="svrtext" type="text" name="name0" value=""/></td>
						<td><input class="svrtext" type="text" name="adrs0" value=""/></td>
					</tr>
				</table>
			</center>

			<center>
				<br/>
				<input type="submit" value="Update mirc.xml"/>
			</center>
			</form>

		</body>
	</html>
</xsl:template>

</xsl:stylesheet>