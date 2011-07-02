package org.rsna.mircsite.queryservice.news;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a "channel" in RSS terms, but in real terms, it's a list of news items.
 * @author RBoden
 *
 */
public class ItemCollection {

	private List <Item> itemCollection = new ArrayList<Item>();
	private String link;
	private String imageLink;
	/**
	 * The image associated with the most recent news item
	 */
	public String getImageLink() {
		return imageLink;
	}
	/**
	 * The image associated with the most recent news item
	 */
	public void setImageLink(String imageLink) {
		this.imageLink = imageLink;
	}

	/**
	 * this is the link to your query service
	 */
	public String getLink() {
		return link;
	}

	/**
	 * this is the link to your query service
	 * @param link
	 */
	public void setLink(String link) {
		this.link = link;
	}

	public List <Item> getItemCollection() {
		return itemCollection;
	}

	public void setItemCollection(List <Item> itemCollection) {
		this.itemCollection = itemCollection;
	}

	public String toString() {
		StringBuffer outStr = new StringBuffer();
		Iterator <Item> iter = getItemCollection().iterator();
		for( int i=0; iter.hasNext() ; i++) {
			Item myItem = iter.next();
			outStr.append("Item Number: "+i+"\n\n");
			outStr.append(myItem.toString());
		}
		return outStr.toString();
	}

}
