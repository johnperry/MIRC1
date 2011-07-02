/*---------------------------------------------------------------
*  Copyright 2010 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.mircsite.storageservice;

import java.io.*;
import java.util.*;
import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import jdbm.RecordManagerOptions;
import jdbm.btree.BTree;
import jdbm.helper.FastIterator;
import jdbm.helper.Tuple;
import jdbm.helper.TupleBrowser;
import jdbm.htree.HTree;
import org.apache.log4j.Logger;
import org.rsna.mircsite.util.JdbmUtil;
import org.rsna.mircsite.util.MircImage;
import org.rsna.mircsite.util.MircIndexEntry;
import org.rsna.mircsite.util.MircIndexLMDateComparator;
import org.rsna.mircsite.util.MircIndexPubDateComparator;
import org.rsna.mircsite.util.MircIndexTitleComparator;
import org.rsna.mircsite.util.TomcatUser;
import org.rsna.mircsite.util.FileUtil;
import org.rsna.mircsite.util.XmlStringUtil;
import org.rsna.mircsite.util.XmlUtil;
import org.w3c.dom.*;

/**
 * The index of documents on a storage service.
 */
public class MircIndex {

	static final Logger logger = Logger.getLogger(MircIndex.class);

	private static MircIndex mircIndex = null;

	RecordManager recman;
	File indexFile;
	File documentsDir;
	HTree pathToID;
	HTree idToPath;
	HTree idToMIE;
	MircIndexDatabase freetext;
	Hashtable<String,MircIndexDatabase> fields;
	Hashtable<Integer,MircIndexEntry> idToMIEShadow;
	static Unfragmented unfragmented = new Unfragmented();

	/**
	 * Instantiate the index database, creating the database file
	 * if it is missing, but not populating it.
	 * @param documentsDir the path to the storage service's documents directory
	 * @param indexFile the path to the index file (without any extension).
	 */
	protected MircIndex(File documentsDir,
						File indexFile)  throws Exception {
		this.documentsDir = documentsDir;
		this.indexFile = indexFile;
		this.fields = new Hashtable<String,MircIndexDatabase>();
		openIndex();
	}

	/**
	 * Initialize the static instance
	 * @param documentsDir the path to the storage service's documents directory
	 * @param indexFile the path to the index file (without any extension).
	 */
	public static MircIndex init(
						File documentsDir,
						File indexFile)  throws Exception {
		if (mircIndex != null) mircIndex.close();
		mircIndex = new MircIndex(documentsDir, indexFile);
		return mircIndex;
	}

	/**
	 * Get the singleton index object.
	 * @return the index object, or null if the index has not been initialized.
	 */
	public static MircIndex getInstance() {
		return mircIndex;
	}

	//Get the record manager, find all the tables,
	//and instantiate the index databases
	private void openIndex() throws Exception {
		try {
			recman = JdbmUtil.getRecordManager(indexFile.getPath());
			pathToID = JdbmUtil.getHTree(recman, "PathToID");
			idToPath = JdbmUtil.getHTree(recman, "IDToPath");
			idToMIE = JdbmUtil.getHTree(recman, "IDToMIE");
			freetext = new MircIndexDatabase(recman, "freetext", null);

			//build the shadow index
			idToMIEShadow = new Hashtable<Integer,MircIndexEntry>();
			HashSet<Integer> allIDs = freetext.getAllIDs();
			for (Integer id : allIDs) {
				MircIndexEntry mie = (MircIndexEntry)idToMIE.get(id);
				idToMIEShadow.put(id, mie);
			}

			//now open the query field databases
			openDatabase("title");
			openDatabase("author");
			openDatabase("abstract");
			openDatabase("keywords");
			openDatabase("history");
			openDatabase("findings");
			openDatabase("diagnosis");
			openDatabase("differential-diagnosis");
			openDatabase("discussion");
			openDatabase("pathology");
			openDatabase("anatomy");
			openDatabase("organ-system");
			openDatabase("code");
			openDatabase("modality");
			openDatabase("patient");
			openDatabase("document-type");
			openDatabase("category");
			openDatabase("level");
			openDatabase("access");
			openDatabase("peer-review");
			openDatabase("language");
		}
		catch (Exception ex) {
			logger.warn("Unable to create/open the index");
			throw ex;
		}
	}

	private void openDatabase(String name) throws Exception {
		fields.put( name, new MircIndexDatabase(recman, name, unfragmented.get(name)) );
	}

	/**
	 * Commit any changes that have been made to the index database.
	 */
	public synchronized void commit() {
		if (recman != null) {
			try { recman.commit(); }
			catch (Exception ignore) { }
		}
	}

	/**
	 * Commit any changes that have been made to the index database
	 * and then close the database. This copies the database log
	 * into the database itself.
	 */
	public synchronized void close() {
		if (recman != null) {
			try { recman.commit(); recman.close(); recman = null; }
			catch (Exception ignore) { }
		}
	}

	//Delete the database files to that they can be rebuilt.
	private synchronized void delete() {
		File parent = indexFile.getParentFile();
		String indexName = indexFile.getName();
		(new File(parent, indexName + ".db")).delete();
		(new File(parent, indexName + ".lg")).delete();
	}

	/**
	 * Close the current index if it is open, delete it, and then
	 * rebuild the index by walking the documents directory tree and
	 * finding all the MIRCdocuments.
	 * @return true if the index was rebuilt; false otherwise. If
	 * the operation failed in any way, the index is left in an
	 * indeterminate state.
	 */
	public synchronized boolean rebuildIndex() {
		try {
			close();
			delete();
			openIndex();
			indexDirectory(documentsDir, 0);
			recman.commit();
			return true;
		}
		catch (Exception ex) {
			logger.warn("Unable to rebuild the index: "+indexFile+".", ex);
			return false;
		}
	}

	//Walk a directory tree and add all the MIRCdocuments to the index.
	private int indexDirectory(File dir, int count) throws Exception {
		File[] files = dir.listFiles();
		for (File file : files) {
			if (file.isFile() && file.getName().toLowerCase().endsWith(".xml")) {
				logger.info("Indexing: "+file);
				indexDocument(file);
				if ((count % 10) == 0) {
					System.gc();
					recman.commit();
				}
				count++;
			}
			else if (file.isDirectory()) count = indexDirectory(file, count);
		}
		return count;
	}

	//Check whether a file parses as a MIRCdocument
	//and add it to the index if it does.
	private void indexDocument(File file) {
		try {
			Document doc = XmlUtil.getDocument(file);
			if (doc.getDocumentElement().getTagName().equals("MIRCdocument")) {
				String path = file.getPath();
				path = path.substring(path.indexOf(documentsDir.getName()));
				addDocument(file, path, doc);
			}
		}
		catch (Exception skip) {
			logger.warn("\nException caught while parsing " + file + "\n", skip);
		}
	}

	/**
	 * Get the number of documents in the index.
	 */
	public int getIndexSize() {
		return idToMIEShadow.size();
	}

	/**
	 * Get the number of words and word fragments in all the documents
	 * in the index.
	 */
	public int getNumberOfWords() {
		return freetext.getNumberOfWords();
	}

	/**
	 * Get the MircIndexEntry for a specified path.
	 * @param path the path by which the MIRCdocument has
	 * been indexed. The path starts at the documents
	 * subdirectory of the root directory of the storage service.
	 * @return the MircIndexEntry corresponding to the path,
	 * or null if no entry has been indexed for that path.
	 */
	public MircIndexEntry getMircIndexEntry(String path) {
		try {
			Integer id = (Integer)pathToID.get(fixPath(path));
			return (MircIndexEntry)idToMIEShadow.get(id);
		}
		catch (Exception ex) { return null; }
	}

	/**
	 * Get the (unsorted) array of MircIndexEntry objects
	 * for MIRCdocuments which match a specified query.
	 * This method does a freetext search only.
	 * It does not restrict the results in any other way
	 * even on a storage service that is operating in
	 * restricted mode. This query should only be used
	 * by admin functions.
	 * @param freetext the freetext query string.
	 */
	public MircIndexEntry[] query(String freetext) {
		return query(new MircQuery(freetext), true, null);
	}

	/**
	 * Get the (unsorted) array of MircIndexEntry objects
	 * for MIRCdocuments which match a specified MircQuery.
	 * @param mq the query object containing all the
	 * query fields.
	 */
	public MircIndexEntry[] query(MircQuery mq, boolean isOpen, TomcatUser tcUser) {
		if (mq.isBlankQuery && !mq.containsNonFreetextQueries) {

			//Handle this case separately because it can be very fast.
			HashSet<MircIndexEntry> set = new HashSet<MircIndexEntry>( idToMIEShadow.values() );
			if (!isOpen) set = filterOnAccess(set, tcUser);
			if (mq.containsAgeQuery) set = filterOnAge(set, mq);
			return set.toArray(new MircIndexEntry[set.size()]);

		}

		//Okay, it's not a simple query; do everything but the age
		HashSet<Integer> ids = null;
		if (!mq.isBlankQuery) ids = freetext.getIDsForQueryString(mq.get("freetext"));
		for (String name : mq.keySet()) {
			if (!name.equals("freetext")) {
				MircIndexDatabase db = fields.get(name);

				//If there is a field in the MircQuery, then
				//it must be non-blank, and if there is no
				//corresponding MircIndexDatabase, then
				//we must return zero results
				if (db == null) return new MircIndexEntry[0];

				//Okay, we have a query field and the corresponding
				//MircIndexDatabase; do the query.
				HashSet<Integer> temp = db.getIDsForQueryString(mq.get(name));

				//If we got no matches on this field, then the final
				//result will have no matches, so we can bail out now.
				if (temp.size() == 0) return new MircIndexEntry[0];

				//Okay, we got some responses; use them to filter
				//what we have found so far.
				if (ids == null) ids = temp;
				else ids = MircIndexDatabase.intersection(ids, temp);
			}
		}

		//Now apply the access and age filters, if necessary.
		HashSet<MircIndexEntry> set = getMIESet(ids);
		boolean isAdmin = (tcUser != null) && (tcUser.isAdmin);
		if (!isOpen && !isAdmin) set = filterOnAccess(set, tcUser);
		if (mq.containsAgeQuery) set = filterOnAge(set, mq);
		return set.toArray( new MircIndexEntry[ set.size() ] );
	}

	private HashSet<MircIndexEntry> filterOnAccess( HashSet<MircIndexEntry> mieSet, TomcatUser tcUser ) {
		HashSet<MircIndexEntry> set = new HashSet<MircIndexEntry>();
		for (MircIndexEntry mie : mieSet) {
			if (mie.allows(tcUser)) set.add(mie);
		}
		return set;
	}

	private HashSet<MircIndexEntry> filterOnAge( HashSet<MircIndexEntry> mieSet, MircQuery mq ) {
		HashSet<MircIndexEntry> set = new HashSet<MircIndexEntry>();
		for (MircIndexEntry mie : mieSet) {
			if (mie.hasPatientInAgeRange(mq.minAge, mq.maxAge)) set.add(mie);
		}
		return set;
	}

	/**
	 * Get an (unsorted) array of MircIndexEntry objects
	 * from a HashSet of document ID objects. Document
	 * IDs are Integer objects which are automatically assigned
	 * when documents are indexed. They are reassigned when the
	 * index is rebuilt, so they should not be used for permanent
	 * identification of documents. (The path string is the
	 * preferred identifier for permanent reference.) In the
	 * returned array, any ID values from the HashSet which do
	 * not appear in the index are skipped.
	 */
	private HashSet<MircIndexEntry> getMIESet(HashSet<Integer> ids) {
		HashSet<MircIndexEntry> set = new HashSet<MircIndexEntry>();
		for (Integer id : ids) {
			try {
				MircIndexEntry mie = (MircIndexEntry)idToMIEShadow.get(id);
				set.add( mie );
			}
			catch (Exception skipThisOne) { }
		}
		return set;
	}

	/**
	 * Sort an array of MircIndexEntry objects in
	 * alphabetical order by title.
	 */
	public static void sortByTitle(MircIndexEntry[] mies) {
		Arrays.sort(mies, new MircIndexTitleComparator());
	}

	/**
	 * Sort an array of MircIndexEntry objects in
	 * reverse chronological order by last modified date.
	 */
	public static void sortByLMDate(MircIndexEntry[] mies) {
		Arrays.sort(mies, new MircIndexLMDateComparator());
	}

	/**
	 * Sort an array of MircIndexEntry objects in
	 * chronological order by publication date.
	 */
	public static void sortByPubDate(MircIndexEntry[] mies) {
		Arrays.sort(mies, new MircIndexPubDateComparator());
	}

	/**
	 * Insert a MIRCdocument in the index.
	 * @param path to the document in the form of the relative path
	 * from the parent of the storage services' documents directory
	 * to the MIRCdocument XML file.
	 * @return true if the document was entered into the index; false otherwise.
	 */
	public synchronized boolean insertDocument(String path) {
		try {
			path = fixPath(path);
			removeDocument(path);
			File file = new File(documentsDir.getParentFile(),
								 path.replace("/", File.separator));
			Document doc = XmlUtil.getDocument(file);
			addDocument(file, path, doc);
			recman.commit();
			return true;
		}
		catch (Exception ex) { return false; }
	}

	/**
	 * Insert a MIRCdocument into the index.
	 * @param file the file containing the MIRCdocument
	 * @param path the path by which the document is to be indexed
	 * @param doc the XML DOM object containing the parsed MIRCdocument
	 */
	private synchronized void addDocument(File file, String path, Document doc) throws Exception {
		//Insert the RadLex terms and save the file
		Element root = doc.getDocumentElement();
		MircDocument.insertRadLexTerms(root);
		FileUtil.setFileText(file, XmlUtil.toString(root));

		//Now insert the modified document into the index
		path = fixPath(path);
		Integer id = getIDForPath(path);

		MircIndexEntry mie = new MircIndexEntry( file, path, doc, StorageConfig.indexDocFile );
		idToMIE.put( id, mie );
		idToMIEShadow.put( id, mie );

		//put everything in the freetext database
		freetext.indexString(id, getText(root));

		//index the access from the index entry
		fields.get("access").indexString(id, mie.access);

		//now do all the query fields
		for (String name : fields.keySet()) {
			MircIndexDatabase db = fields.get(name);
			NodeList nl = root.getElementsByTagName(name);
			db.indexString( id, getText( nl ) );
		}

		//make sure the image sizes are in place
		setImageSizes(file, doc);
	}

	//Check that all the image elements have w and h attributes.
	//If the attributes are missing for an image, open it, get the size,
	//and insert the attributes.
	private void setImageSizes(File file, Document doc) {
		try {
			boolean chg = false;
			boolean changed = false;
			File dir = file.getParentFile();
			Element root = doc.getDocumentElement();
			NodeList nl = root.getElementsByTagName("image");
			for (int i=0; i<nl.getLength(); i++) {
				Element image = (Element)nl.item(i);
				chg = setImageSize(dir, image);
				changed |= chg;
				NodeList altnl = image.getElementsByTagName("alternative-image");
				for (int k=0; k<altnl.getLength(); k++) {
					Element alt = (Element)altnl.item(k);
					String role = alt.getAttribute("role");
					String srclc = alt.getAttribute("src").toLowerCase();
					if (role.equals("icon")
							|| (role.equals("annotation") && (srclc.endsWith(".jpg") || srclc.endsWith(".jpeg")))
									|| role.equals("original-dimensions")) {
						chg = setImageSize(dir, alt);
						changed |= chg;
					}
				}
			}
			if (changed) FileUtil.setFileText(file, XmlUtil.toString(doc));
		}
		catch (Exception skip) { }
	}

	//Set the w and h attributes for one image, if necessary.
	private boolean setImageSize(File dir, Element img) {
		if (img.getAttribute("w").trim().equals("") || img.getAttribute("h").trim().equals("")) {
			String src = img.getAttribute("src").trim();
			File imageFile = new File(dir, src);
			String srclc = src.toLowerCase();
			if (!src.equals("") && !srclc.startsWith("http://") && !srclc.startsWith("/") && !srclc.startsWith("\\")) {
				try {
					MircImage m = new MircImage(imageFile);
					img.setAttribute("w", Integer.toString(m.getWidth()));
					img.setAttribute("h", Integer.toString(m.getHeight()));
					return true;
				}
				catch (Exception skip) {
					logger.warn("Unable to set sizes for:\nDirectory: \""+dir+ "\"\n"
								+"Image file: \""+imageFile+"\"\n"
								+"src attribute: \""+src+"\"\n"
								+"image element:\n"
								+XmlStringUtil.makeReadableTagString(XmlUtil.toString(img)), skip);
				}
			}
		}
		return false;
	}

	/**
	 * Remove a MIRCdocument from the index.
	 * @param path the path by which the MIRCdocument was indexed.
	 * @return true if the document was found in the index and
	 * successfully removed; false otherwise.
	 */
	public synchronized boolean removeDocument(String path) {
		path = fixPath(path);
		try {
			Integer id = (Integer)pathToID.get(path);
			if (id != null) {
				boolean ok = freetext.removeDoc(id);
				for (String name : fields.keySet()) {
					MircIndexDatabase db = fields.get(name);
					ok &= db.removeDoc(id);
				}
				pathToID.remove(path);
				idToPath.remove(id);
				idToMIE.remove(id);
				idToMIEShadow.remove(id);

				return ok;
			}
			else { return true; }
		}
		catch (Exception failed) {
			return false;
		}
	}

	/**
	 * Fix a path which (for backward compatibility) may contain
	 * slashes, backslashes, or exclamation points as path separators.
	 * This method forces all separators to be forward slashes.
	 * @param path a file path.
	 * @return the trimmed path, with backslashes and exclamation
	 * points replaced by forward slashes.
	 */
	public static String fixPath(String path) {
		path = path.replaceAll("[!\\\\]+","/").trim();
		return path;
	}

	//Get an ID for a MIRCdocument identified by a specified path.
	//If no document appears in the index for the path, then create
	//a new ID and update the tables to reflect the new ID.
	private synchronized Integer getIDForPath(String path) throws Exception {
		try {
		path = fixPath(path);
		Integer id = (Integer)pathToID.get(path);
		if (id == null) {
			//get the next available ID
			id = (Integer)pathToID.get("__last");
			if (id == null) id = new Integer(0);
			else id = new Integer( id.intValue() + 1 );
			pathToID.put("__last", id);

			//store the path against the ID
			idToPath.put(id, path);

			//store the id against the path
			pathToID.put(path, id);
		}
		return id;
		}
		catch (Exception ex) { logger.warn("getIDForPath:",ex); throw ex; }
	}

	//Get all the text in a node and its children.
	//Note, to preserve word boundaries, spaces are
	//inserted between text nodes.
	private String getText(Node node) {
		StringBuffer sb = new StringBuffer();
		appendTextNodes(sb, node);
		return sb.toString();
	}

	//Get all the text in a nodelist
	private String getText(NodeList nl) {
		StringBuffer sb = new StringBuffer();
		for (int i=0; i<nl.getLength(); i++) {
			appendTextNodes(sb, nl.item(i));
		}
		return sb.toString();
	}

	//Add the contents of all the text nodes starting at
	//a specified node to a StringBuffer.
	private void appendTextNodes(StringBuffer sb, Node node) {
		if (node.getNodeType() == Node.ELEMENT_NODE) {
			Node child = ((Element)node).getFirstChild();
			while (child != null) {
				appendTextNodes(sb, child);
				child = child.getNextSibling();
			}
		}
		else if (node.getNodeType() == Node.TEXT_NODE) {
			sb.append(" " + node.getTextContent());
		}
	}

	static class Unfragmented extends Hashtable<String, HashSet<String>> {
		public Unfragmented() {
			super();
			this.put("patient", getSet(new String[] {"male", "female"}));
		}
		private HashSet<String> getSet(String[] words) {
			HashSet<String> set = new HashSet<String>();
			for (String word : words) {
				set.add(word);
			}
			return set;
		}
	}

	/**
	 * Log the state of the index.
	 * @param title a heading for the section of the log containing the state.
	 */
	public void logState(String title) {
		logger.warn("===========================================================================");
		logger.warn("MircIndex State: "+title);
		logger.warn("---------------------------------------------------------------------------");
		try {
			logger.warn("pathToID:");
			logger.warn("--------");
			FastIterator fit = pathToID.keys();
			String path;
			while ((path = (String)fit.next()) != null) {
				Integer id = (Integer)pathToID.get(path);
				logger.warn("..."+path+": "+id.toString());
			}
		}
		catch (Exception ex) { logger.warn("!!!Exception caught in reading pathToID", ex); }
		logger.warn("-------------------------------------");
		try {
			logger.warn("idToPath:");
			logger.warn("--------");
			FastIterator fit = idToPath.keys();
			Integer id;
			while ((id = (Integer)fit.next()) != null) {
				String path = (String)idToPath.get(id);
				logger.warn("..."+id.toString()+": "+path);
			}
		}
		catch (Exception ex) { logger.warn("!!!Exception caught in reading idToPath", ex); }

		logger.warn("-------------------------------------");
		try {
			logger.warn("idToMIE:");
			logger.warn("-------");
			FastIterator fit = idToPath.keys();
			Integer id;
			while ((id = (Integer)fit.next()) != null) {
				MircIndexEntry mie = (MircIndexEntry)idToMIE.get(id);
				logger.warn("..."+id.toString()+": "+mie.md.getAttribute("filename"));
			}
		}
		catch (Exception ex) { logger.warn("!!!Exception caught in reading idToMIE", ex); }

		logger.warn("-------------------------------------");
		logger.warn("MircIndexDatabases:");
		logger.warn("------------------");
		logger.warn("...freetext: "+freetext.getNumberOfWords());
		for (String name : fields.keySet()) {
			MircIndexDatabase db = fields.get(name);
			logger.warn("..."+name+": "+db.getNumberOfWords());
		}
		logger.warn("===========================================================================");
	}
}

