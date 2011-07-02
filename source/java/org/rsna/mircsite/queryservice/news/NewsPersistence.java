package org.rsna.mircsite.queryservice.news;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

import org.rsna.mircsite.util.FileUtil;
import org.rsna.mircsite.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * This class deals with the persistence and retrieval of news items
 * from the XML file.
 * @author RBoden
 */
public class NewsPersistence {

	private static final SimpleDateFormat rssDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");

	/**
	 * Generate a collection directly from the XML file.  Note: this doesn't currently
	 * retrieve the channel's publication date, nor does it retrieve the channel's
	 * link element.
	 */
	ItemCollection generateItemCollection(String xmlPath) throws IOException {
		ItemCollection collection = new ItemCollection();
		// generate the dom from the file
		Document dom = null;
		try {
			dom = XmlUtil.getDocument(xmlPath);
		} catch( Exception ex ) {
			ex.printStackTrace(System.err);
			throw new IOException("Error generating Document object from the XML path provided: "+xmlPath);
		}
		// traverse the tree to find the elements
		Element rss = (Element)dom.getElementsByTagName("rss").item(0);
		Element channel = (Element)rss.getElementsByTagName("channel").item(0);
		Element image = (Element)channel.getElementsByTagName("image").item(0);
		if( image != null ) {
			collection.setImageLink(getElementText(image, "url"));
		}
		NodeList itemList = channel.getElementsByTagName("item");
		for( int i=0; i < itemList.getLength(); i++ ) {
			Element item = (Element)itemList.item(i);
			// convert the element into a case
			collection.getItemCollection().add(createItem(item));
		}
		return collection;
	}

	private Item createItem(Element item) {
		Item myItem = new Item();
		myItem.setTitle(getElementText(item, "title"));
		myItem.setDescription(getElementText(item, "description"));
		myItem.setImageLink(getElementText(item, "image"));
		myItem.setLink(getElementText(item, "link"));
		Date pubDate = null;
		try {
			pubDate = rssDateFormat.parse(getElementText(item, "pubDate"));
		} catch( Exception ex ){
			// must have been a problem parsing the publication date, so we'll just
			// leave it null
		}
		myItem.setPubDate(pubDate);
		return myItem;
	}

	/**
	 * Simple XML utility that gets the text from an element.
	 */
	private String getElementText(Element parentElement, String childElementName) {
		String result = "";
		NodeList list = parentElement.getElementsByTagName(childElementName);
		if( list.getLength() > 0 ) {
			Element childElement = (Element)list.item(0);
			result = childElement.getTextContent();
		}
		return result;

	}

	/**
	 * This method simply persists the collection as XML, it assumes the collection
	 * is already in the exact form and order that it is to be persisted in.  Please make
	 * sure you have set the collections link attribute before calling this method, it is
	 * persisted new each time (because initially it will be set to garbage).
	 */
	synchronized void persistItemCollection(ItemCollection collection, String xmlPath) throws IOException {
		// create a new temporary file, and write to that
		File tmpFile = new File(xmlPath+".tmp");
		writeXMLToFile(collection, tmpFile);
		// delete the old file
		File realFile = new File(xmlPath);
		realFile.delete();
		// rename the new file
		tmpFile.renameTo(realFile);
	}

	private void writeXMLToFile(ItemCollection collection, File file) {
		StringBuffer sb = new StringBuffer();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<rss version=\"2.0\">\n");
		sb.append("	<channel>\n");
		sb.append("		<title>Case of the Day</title>\n");
		sb.append("		<link>"+collection.getLink()+"</link>\n");
		sb.append("		<description>A radiological case of the day feed.</description>\n");
		sb.append("		<language>en-us</language>\n");
		if( collection.getImageLink() != null && collection.getImageLink().trim().length() > 0 ) {
			sb.append("		<image><url>"+collection.getImageLink()+"</url></image>\n");
		}
		sb.append("		<pubDate>"+rssDateFormat.format(new Date())+"</pubDate>\n");
		Iterator <Item> iter = collection.getItemCollection().iterator();
		while( iter.hasNext() ) {
			Item myItem = iter.next();
			sb.append("		<item>\n");
			sb.append("			<title>"+myItem.getTitle()+"</title>\n");
			sb.append("			<description>"+myItem.getDescription()+"</description>\n");
			sb.append("			<pubDate>"+rssDateFormat.format(myItem.getPubDate())+"</pubDate>\n");
			sb.append("			<link>"+myItem.getLink()+"</link>\n");
			sb.append("			<image>"+myItem.getImageLink()+"</image>\n");
			sb.append("		</item>\n");
		}
		sb.append("	</channel>\n");
		sb.append("</rss>\n");
		FileUtil.setFileText(file, FileUtil.utf8, sb.toString());
	}
}
