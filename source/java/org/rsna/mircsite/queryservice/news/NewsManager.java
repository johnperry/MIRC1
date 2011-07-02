package org.rsna.mircsite.queryservice.news;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NewsManager {

	public static final String RSS_FILENAME = "news/news.rss";

	/**
	 * This method will add a new case to the caseoftheday RSS feed XML file.
	 * Only 10 cases are kept at a time in the que, so if this if there are more
	 * then 10, the oldest one will be bumped off.
	 * @param myItem the item to be persisted
	 * @param xmlPath the fully qualified path on the file system to the RSS xml file
	 * @param serverPath the URL of the mirc server (to go in the rss channel's link field)
	 */
	public void addItem(Item myItem, String xmlPath, String serverPath) throws IOException {
		NewsPersistence persistence = new NewsPersistence();
		// first get the existing collection
		ItemCollection collection = persistence.generateItemCollection(xmlPath);
		collection.setLink(serverPath);
		// set the collection's image link to the most recently added item
		collection.setImageLink(myItem.getImageLink());
		// if more then 10 items, strip off the oldest
		if( collection.getItemCollection().size() == 10 ) {
			collection.getItemCollection().remove(9);
		}
		List<Item> newList = new ArrayList<Item>();
		newList.add(myItem);
		newList.addAll(collection.getItemCollection());
		collection.setItemCollection(newList);
		persistence.persistItemCollection(collection, xmlPath);
	}

	/*
	 * Get the most recently added item. If there are no items, null is returned.
	 * @param xmlPath the fully qualified path on the file system to the RSS xml file.
	 */
	public Item getNewestItem(String xmlPath) throws IOException {
		ItemCollection collection = new NewsPersistence().generateItemCollection(xmlPath);
		if( collection.getItemCollection().size() > 0 ) {
			return collection.getItemCollection().get(0);
		} else {
			return null;
		}

	}

	public ItemCollection getAllItems(String xmlPath) throws IOException {
		return new NewsPersistence().generateItemCollection(xmlPath);
	}

	/**
	 * Checks to see if an item exists corresponding to the link, and remove it if it does.
	 * @param link the full Link to the item to be removed
	 * @param xmlPath the fully qualified path on the file system to the RSS xml file
	 */
	public void removeItem(String link, String xmlPath, String serverPath) throws IOException {
		NewsPersistence persistence = new NewsPersistence();
		ItemCollection collection = persistence.generateItemCollection(xmlPath);
		List <Item>removalList = new ArrayList<Item>();
		Iterator <Item> iter = collection.getItemCollection().iterator();
		while (iter.hasNext()) {
			Item theItem = iter.next();
			if( theItem.getLink().equals(link) ) {
				removalList.add(theItem);
			}
		}
		// now remove all the ones we found to be removed
		Iterator <Item> iter2 = removalList.iterator();
		while (iter2.hasNext()) {
			Item removalItem = iter2.next();
			collection.getItemCollection().remove(removalItem);
		}
		collection.setLink(serverPath);
		persistence.persistItemCollection(collection, xmlPath);
	}


}
