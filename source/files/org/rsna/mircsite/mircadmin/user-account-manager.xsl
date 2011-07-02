<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="username"/>
<xsl:param name="myrsna-username"/>
<xsl:param name="myrsna-password"/>
<xsl:param name="user-is-authenticated"/>
<xsl:param name="user-is-admin"/>
<xsl:param name="groups"/>
<xsl:param name="servers"/>
<xsl:param name="message"/>

<xsl:template match="/mirc">
	<html>
		<head>
			<title>User Account Manager</title>
			<link rel="Stylesheet" type="text/css" media="all"
				href="user-account-manager.css">
			</link>
			<xsl:call-template name="script"/>
		</head>
		<body>
			<div class="closebox">
				<img src="/mirc/images/closebox.gif"
					 onclick="window.open('/mirc/query','_self');"
					 title="Return to the MIRC home page"/>
			</div>

		<xsl:if test="$user-is-authenticated = 'no' and not(accounts/@enabled = 'yes')">
			<h1>Login Please</h1>
			<p class="center">You must log in before accessing your account.</p>
		</xsl:if>

		<xsl:if test="$user-is-authenticated = 'no' and accounts/@enabled = 'yes'">
			<h1>User Account Manager</h1>
			<p class="note">
				If you have an account and wish to access it, please close this page,
				log in on the query page, and then return to this page.
			</p>
			<p class="note">
				If you want to create a new account, enter a username and password in the
				fields below and then click the Submit button. New accounts may take 60
				seconds to be activated.
			</p>
			<center>
				<form action="" method="POST" accept-charset="UTF-8">
					<table border="1">
						<tr>
							<td>Enter the username</td>
							<td><input class="text" type="text" name="newusername"/></td>
						</tr>
						<tr>
							<td>Enter the password</td>
							<td><input class="text" type="password" name="newpassword1"/></td>
						</tr>
						<tr>
							<td>Enter the password again</td>
							<td><input class="text" type="password" name="newpassword2"/></td>
						</tr>
					</table>
					<br/>
					<input type="submit" value="Submit"/>
				</form>
			</center>
		</xsl:if>

		<xsl:if test="$user-is-authenticated = 'yes'">
			<h1>User Account Manager for <xsl:value-of select="$username"/></h1>
			<form action="" method="POST" accept-charset="UTF-8">

			<p class="note">
				If you wish to change the parameters of your account, make the
				entries in the fields below and then click the Submit button at the bottom.
			</p>

			<p class="note">To change your <b>MIRC</b> password, use this table:</p>
			<center>
				<table border="1">
					<tr>
						<td>Enter your new password</td>
						<td><input class="text" type="password" name="password1"/></td>
					</tr>
					<tr>
						<td>Enter your new password again</td>
						<td><input class="text" type="password" name="password2"/></td>
					</tr>
				</table>
			</center>

			<p class="note">
				If you wish to register your usernames and passwords on the servers
				known to this query service, enter them in the table below.
			</p>
			<center>
				<table class="passport" border="1">
					<tr>
						<th>Server</th>
						<th>Username</th>
						<th>Password</th>
					</tr>
					<xsl:for-each select="$servers/servers/server">
						<xsl:variable name="pos" select="position()"/>
						<tr>
							<td>
								<input type="hidden">
									<xsl:attribute name="name">
										<xsl:text>ppurl</xsl:text>
										<xsl:value-of select="$pos"/>
									</xsl:attribute>
									<xsl:attribute name="value">
										<xsl:value-of select="@url"/>
									</xsl:attribute>
								</input>
								<xsl:for-each select="service">
									<xsl:value-of select="."/>
									<xsl:if test="position()!=last()"><br/></xsl:if>
								</xsl:for-each>
							</td>
							<td>
								<input class="passport" type="text">
									<xsl:attribute name="name">
										<xsl:text>ppname</xsl:text>
										<xsl:value-of select="$pos"/>
									</xsl:attribute>
									<xsl:attribute name="value">
										<xsl:value-of select="@username"/>
									</xsl:attribute>
								</input>
							</td>
							<td>
								<input class="passport" type="password">
									<xsl:attribute name="name">
										<xsl:text>ppword</xsl:text>
										<xsl:value-of select="$pos"/>
									</xsl:attribute>
									<xsl:attribute name="value">
										<xsl:value-of select="@password"/>
									</xsl:attribute>
								</input>
							</td>
						</tr>
					</xsl:for-each>
				</table>
			</center>

			<xsl:if test="accounts/groups/@enabled = 'yes'">
				<xsl:if test="$groups/groups/group">
					<p class="note">
						You are a member of the following group(s). If you wish
						to resign from a group, check its box.
					</p>
					<center>
						<table class="groups">
							<xsl:for-each select="$groups/groups/group">
								<tr>
									<td class="groupcb">
										<input type="checkbox" value="yes">
											<xsl:attribute name="name">
												<xsl:text>resign-</xsl:text>
												<xsl:value-of select="."/>
											</xsl:attribute>
										</input>
									</td>
									<td><xsl:value-of select="."/></td>
								</tr>
							</xsl:for-each>
						</table>
					</center>
				</xsl:if>
				<p class="note">
					If you wish to join a group, enter the group's name and
					the password in the table below. You must be
					given these parameters by someone in the group.
				</p>
				<center>
					<table border="1">
						<tr>
							<td>Enter the group's name</td>
							<td><input class="text" type="text" name="groupname"/></td>
						</tr>
						<tr>
							<td>Enter the group's password</td>
							<td><input class="text" type="password" name="grouppassword"/></td>
						</tr>
					</table>
				</center>

				<p class="note">
					If you wish to create a new group, enter the group's name and
					the password in the table below. Be sure to remember them.
					To invite other users into the group, you must
					tell them these these parameters. You will automatically
					be made a member of this group.
				</p>
				<center>
					<table border="1">
						<tr>
							<td>Enter the group's name</td>
							<td><input class="text" type="text" name="newgroupname"/></td>
						</tr>
						<tr>
							<td>Enter the group's password</td>
							<td><input class="text" type="password" name="newgrouppassword1"/></td>
						</tr>
						<tr>
							<td>Enter the group's password again</td>
							<td><input class="text" type="password" name="newgrouppassword2"/></td>
						</tr>
					</table>
				</center>
			</xsl:if>

			<p class="note">
				If you wish to store your <b>myRSNA</b> account information,
				enter the username and password in the table below.
			</p>
			<center>
				<table border="1">
					<tr>
						<td>Enter your myRSNA username</td>
						<td><input class="text" type="text" name="myrsnaname" value="{$myrsna-username}"/></td>
					</tr>
					<tr>
						<td>Enter your myRSNA password</td>
						<td><input class="text" type="password" name="myrsnapw" value="{$myrsna-password}"/></td>
					</tr>
				</table>
			</center>

			<br/>
			<center>
				<input type="submit" value="Submit"/>
				<p class="center">
					Changes will become effective in 60 seconds.
				</p>
			</center>
			</form>
		</xsl:if>

		</body>
	</html>
</xsl:template>

<xsl:template name="script">
	<script>
		var messageText = '<xsl:value-of select="$message"/>';
		function showMessage() {
			if (messageText != "") {
				alert(messageText.replace(/\|/g,"\n"));
			}
		}
		window.onload = showMessage;
	</script>
</xsl:template>

</xsl:stylesheet>