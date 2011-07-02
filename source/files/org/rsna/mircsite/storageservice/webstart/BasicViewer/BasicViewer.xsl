<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="server-url"/>
<xsl:param name="context"/>
<xsl:param name="app-name"/>
<xsl:param name="md-path"/>
<xsl:param name="img-path"/>

<xsl:template match="/MIRCdocument">
<jnlp codebase="{$server-url}/{$context}/webstart/{$app-name}/">
	<information>
		<title>MIRC Basic Viewer</title>
		<vendor>RSNA</vendor>
	</information>
	<resources>
		<j2se href="http://java.sun.com/products/autodl/j2se"
			  initial-heap-size="64m"
			  max-heap-size="512m"
			  version="1.6+"/>
		<jar href="HelloWorld.jar" main="true"/>
	</resources>
	<application-desc main-class="org.jp.hello.HelloWorld">
		<argument>token=123</argument>
		<argument>codebase=http://localhost:8080/test</argument>
		<argument>studyUid=987</argument>
	</application-desc>
</jnlp>
</xsl:template>

</xsl:stylesheet>