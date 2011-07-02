<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<html>
<head>
	<title>Submit Service</title>
	<link rel="stylesheet" href="../service.css" type="text/css"/>		
</head>
<body>
	<div class="closebox">
		<img src="/mirc/images/closebox.gif"
			 onclick="window.open('/mirc/query','_self');"
			 title="Return to the MIRC home page"/>
	</div>

<form target="_self" method="post" accept-charset="UTF-8" enctype="multipart/form-data" id="fileform">

<table width="80%" align="center">
	<tr><td colspan="2"><h3>Submit Service for <%=request.getAttribute("siteName")%></h3></td></tr>
	<tr><td colspan="2">
		<p><i>
	    	This page may be used by authors to submit MIRCdocuments to this MIRC site.
    		Materials are required to be encapsulated in zip files as produced by the 
    		MIRC authoring tool.
		</i></p>
	</td></tr>
	<tr><td colspan="2"><hr/></td></tr>
	<tr>
		<td class="text-label">Select the file for submission:</td>
		<td class="text-field">
			<input onchange="fileform.filename.value = selectedFile.value;" 
			    size="50%" name="filecontent" id="selectedFile" type="file">
			<input ID="Hidden1" name="filename" type="hidden">
		</td>
	</tr>

	<tr><td colspan="2" align="right">
			<input NAME="Button1" ID="Button1" value="Submit the File" 
			    onclick="document.getElementById('fileform').submit();" type="button">
	</td></tr>
	<tr><td colspan="2"><hr/></td></tr>
	<% if( request.getAttribute("message") != null ) { %>
		<tr><td colspan="2">
			<h2>Submission Results:</h2>
		</td></tr>
		<tr><td colspan="2">
			<%=request.getAttribute("message")%>
		</td></tr>		
		
	<% } %>
	
	
	
	
	<tr><td colspan="2" align="center">
		<a href="#" onClick="javascript:window.close();">Close This Window</a>
	</td></tr>

</table>
</form>
</body>
</html>
