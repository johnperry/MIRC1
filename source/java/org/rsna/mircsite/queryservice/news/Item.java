package org.rsna.mircsite.queryservice.news;

import java.util.Date;

/**
 * This represents a case of the day, or in RSS terms a "line"
 * @author RBoden
 */

public class Item {

	private String title;
	private String link;
	private String description;
	private Date pubDate;
	private String imageLink;
	
	/**
	 * This is only used when passing an image in to be persisted, the images come OUT of the rss
	 * at the collection level (only 1 image is allowed)
	 */
	public String getImageLink() {
		return imageLink;
	}
	/**
	 * This is only used when passing an image in to be persisted, the images come OUT of the rss
	 * at the collection level (only 1 image is allowed)
	 */	
	public void setImageLink(String imageLink) {
		this.imageLink = imageLink;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getLink() {
		return link;
	}
	public void setLink(String link) {
		this.link = link;
	}

	/**
	 * Get the date an item is published.
	 */
	public Date getPubDate() {
		return pubDate;
	}
	/**
	 * Set the date an item is published.
	 */
	public void setPubDate(Date pubDate) {
		this.pubDate = pubDate;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}

	public String toString() {
		StringBuffer outStr = new StringBuffer();
		outStr.append("Title: "+getTitle()+"\n");
		outStr.append("Description: "+getDescription()+"\n");
		outStr.append("Link: "+getLink()+"\n");
		outStr.append("PubDate: "+getPubDate()+"\n");
		return outStr.toString();
	}


}
