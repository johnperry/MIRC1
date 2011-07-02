package org.rsna.mircsite.util;

import java.util.HashSet;
import java.util.Set;


/*
 * This object represents a Server credential.  Many servers that show up in the mirc.xml file may share the 
 * same credentials, because they are all essentially on the same tomcat server, this class is a combination 
 * of all servers that share credentials with each other.  The sharedServerNames field contains a list of the name 
 * of all the storage services that share these same credentials.
 */
public class ServerCredential {
	
	private String url;
	private String username;
	private String password;
	private Set<String> sharedServerNames = new HashSet<String>();
	
	public Set<String> getSharedServerNames() {
		return sharedServerNames;
	}
	public void setSharedServerNames(Set<String> sharedServerNames) {
		this.sharedServerNames = sharedServerNames;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	
	public boolean equals(ServerCredential server) {
		return this.getUrl().equals(server.getUrl());
	}

}
