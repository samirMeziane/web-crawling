package webcrawler.mactans.webdocument;

import java.util.LinkedList;
import java.util.List;

/**
 * This class represents a webpage.
 *
 */
public class WebDocument extends URL {

	/**
	 * URLs that were found in a webpage.
	 */
	private List<String> viableURLs;

	/**
	 * Words that were found in a webpage.
	 */
	private List<String> words;

	/**
	 * Constructs a new WebDocument object.
	 * 
	 * @param address address for this webpage
	 * @param actualType content type as written in the HTTP header for this webpage
	 * @param size content size as written in the HTTP header for this webpage
	 * @param type simplified version of actualType, it can either be HTML or OTHER
	 */
	public WebDocument(String address, String actualType, String size, URLType type) {
		super(address, actualType, size, type);

		this.viableURLs = new LinkedList<String>();
		this.words = new LinkedList<String>();
	}

	public List<String> getViableURLs() {
		return viableURLs;
	}

	public List<String> getWords() {
		return words;
	}

	public void setViableURLs(List<String> viableURLs) {
		this.viableURLs = viableURLs;
	}

	public void setWords(List<String> words) {
		this.words = words;
	}

	public void addToURLs(String url) {
		this.viableURLs.add(url);
	}
}
