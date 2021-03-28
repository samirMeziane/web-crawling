package webcrawler.mactans.webdocument;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import webcrawler.mactans.webdocument.URL.URLType;

/**
 * Contains code for serializing and parsing serialized WebDocument objects.
 *
 */
public class WebDocumentOperations {

	/**
	 * Filler pattern to be replaced with data.
	 */
	private static final String FILLER = ">_<";

	/**
	 * Pattern for WebDocument variables to be filled with data.
	 */
	private static final String METADATA = "metadata{\n\r>_<};\n\r";

	/**
	 * Pattern for the address field.
	 */
	private static final String ADDRESS = "address=\">_<\",\n\r";

	/**
	 * Pattern for the actualType field.
	 */
	private static final String ACTUAL_TYPE = "actualType=\">_<\",\n\r";

	/**
	 * Pattern for the size field.
	 */
	private static final String SIZE = "size=\">_<\",\n\r";

	/**
	 * Pattern for the type field.
	 */
	private static final String TYPE = "type=\">_<\",\n\r";

	/**
	 * Pattern for URLs field.
	 */
	private static final String URLS = "urls{\n\r>_<};\n\r";

	/**
	 * Pattern for a URL field.
	 */
	private static final String URL = "url=\">_<\",\n\r";

	/**
	 * Pattern for words field.
	 */
	private static final String WORDS = "words{\n\r>_<};\n\r";

	/**
	 * Pattern for a word field.
	 */
	private static final String WORD = "word=\">_<\",\n\r";

	/**
	 * Pattern for WebDocument variables to read off the serialized object.
	 */
	public static final Pattern METADATA_PATTERN = Pattern.compile("metadata\\{(.*?)\\};", Pattern.DOTALL);

	/**
	 * Pattern for the address field to read off the serialized object.
	 */
	private static final Pattern ADDRESS_PATTERN = Pattern.compile("address=\"(.*?)\",");

	/**
	 * Pattern for the actualType field to read off the serialized object.
	 */
	private static final Pattern ACTUAL_TYPE_PATTERN = Pattern.compile("actualType=\"(.*?)\",");

	/**
	 * Pattern for the size field to read off the serialized object.
	 */
	private static final Pattern SIZE_PATTERN = Pattern.compile("size=\"(.*?)\",");

	/**
	 * Pattern for the type field to read off the serialized object.
	 */
	private static final Pattern TYPE_PATTERN = Pattern.compile("type=\"(.*?)\",");

	/**
	 * Pattern for the URLs' list to read off the serialized object.
	 */
	public static final Pattern URLS_PATTERN = Pattern.compile("urls\\{(.*?)\\};", Pattern.DOTALL);

	/**
	 * Pattern for URLs to read off the serialized object.
	 */
	private static final Pattern URL_PATTERN = Pattern.compile("url=\"(.*?)\",");

	/**
	 * Pattern for the words' list to read off the serialized object.
	 */
	public static final Pattern WORDS_PATTERN = Pattern.compile("words\\{(.*?)\\};", Pattern.DOTALL);

	/**
	 * Pattern for words to read off the serialized object.
	 */
	private static final Pattern WORD_PATTERN = Pattern.compile("word=\"(.*?)\",");

	/**
	 * Converts a WebDocument object into a String representation of it.
	 * 
	 * @param webPage the WebDocument object to serialize
	 * @return the WebDocument object serialized in String format
	 */
	public static String webDocumentToString(WebDocument webPage) {
		return metadataToString(webPage) + urlsToString(webPage) + wordsToString(webPage);
	}

	/**
	 * Converts a String representing of a WebDocument into a WebDocument object.
	 * 
	 * @param input the String to be de-serialized
	 * @return the WebDocument object that was constructed off the de-serialized
	 *         String
	 */
	public static WebDocument stringToWebDocument(String input) {
		WebDocument webDocument;
		String metadata = retrieveMetadata(input);
		String address = retrieveAddress(metadata);
		String actualType = retrieveActualType(metadata);
		String size = retrieveSize(metadata);
		URLType type = retrieveType(metadata).trim().equals("HTML") ? URLType.HTML : URLType.OTHER;

		List<String> urls = retrieveURLs(retrieveURLBlock(input));
		List<String> words = retrieveWords(retrieveWordBlock(input));

		webDocument = new WebDocument(address, actualType, size, type);
		webDocument.setViableURLs(urls);
		webDocument.setWords(words);

		return webDocument;
	}

	/**
	 * Parses a WebDocument's fields into String format.
	 * 
	 * @param webPage the WebDocument to be parsed
	 * @return a WebDocument's objects fields parsed as a string
	 */
	private static String metadataToString(WebDocument webPage) {
		String address = WebDocumentOperations.ADDRESS.replace(FILLER, webPage.getAddress());
		String actualType = WebDocumentOperations.ACTUAL_TYPE.replace(FILLER, webPage.getActualType());
		String size = WebDocumentOperations.SIZE.replace(FILLER, webPage.getSize());
		String type = WebDocumentOperations.TYPE.replace(FILLER, webPage.getType());

		return WebDocumentOperations.METADATA.replace(">_<", address + actualType + size + type);
	}

	/**
	 * Parses a WebDocument's URLs' list into String format.
	 * 
	 * @param webPage the WebDocument to be parsed
	 * @return a WebDocument's URLs' list parsed as a string
	 */
	private static String urlsToString(WebDocument webPage) {
		List<String> urls = webPage.getViableURLs();
		String urlsBlock = "";

		for (Iterator<String> iterator = urls.iterator(); iterator.hasNext();) {
			urlsBlock += WebDocumentOperations.URL.replace(">_<", iterator.next());
		}

		return WebDocumentOperations.URLS.replace(">_<", urlsBlock);
	}

	/**
	 * Parses a WebDocument's words' list into String format.
	 * 
	 * @param webPage the WebDocument to be parsed
	 * @return a WebDocument's words' list parsed as a string
	 */
	private static String wordsToString(WebDocument webPage) {
		List<String> words = webPage.getWords();
		String wordsBlock = "";

		for (Iterator<String> iterator = words.iterator(); iterator.hasNext();) {
			wordsBlock += WebDocumentOperations.WORD.replace(">_<", iterator.next());
		}

		return WebDocumentOperations.WORDS.replace(">_<", wordsBlock);
	}

	/**
	 * Retrieves a metadata block from the given input.
	 * 
	 * @param input WebDocument in string format
	 * @return the metadata block
	 */
	private static String retrieveMetadata(String input) {
		String metadata = "";
		Matcher matcher = METADATA_PATTERN.matcher(input);

		while (matcher.find()) {
			metadata = matcher.group(1);
		}

		return metadata;
	}

	/**
	 * Retrieves the address field from a metadata block.
	 * 
	 * @param input metadata block in string format
	 * @return the address field
	 */
	private static String retrieveAddress(String input) {
		String address = "";
		Matcher matcher = ADDRESS_PATTERN.matcher(input);

		while (matcher.find()) {
			address = matcher.group(1);
		}

		return address;
	}

	/**
	 * Retrieves the actualType field from a metadata block.
	 * 
	 * @param input metadata block in string format
	 * @return the actualType field
	 */
	private static String retrieveActualType(String input) {
		String actualType = "";
		Matcher matcher = ACTUAL_TYPE_PATTERN.matcher(input);

		while (matcher.find()) {
			actualType = matcher.group(1);
		}

		return actualType;
	}

	/**
	 * Retrieves the size field from a metadata block.
	 * 
	 * @param input metadata block in string format
	 * @return the size field
	 */
	private static String retrieveSize(String input) {
		String size = "";
		Matcher matcher = SIZE_PATTERN.matcher(input);

		while (matcher.find()) {
			size = matcher.group(1);
		}

		return size;
	}

	/**
	 * Retrieves the type field from a metadata block.
	 * 
	 * @param input metadata block in string format
	 * @return the type field
	 */
	private static String retrieveType(String input) {
		String type = "";
		Matcher matcher = TYPE_PATTERN.matcher(input);

		while (matcher.find()) {
			type = matcher.group(1);
		}

		return type;
	}

	/**
	 * Retrieves the URLs block from a serialized WebDocument object string.
	 * 
	 * @param input serialized WebDocument in string format
	 * @return the URLs block
	 */
	private static String retrieveURLBlock(String input) {
		String urlsBlock = "";
		Matcher matcher = URLS_PATTERN.matcher(input);

		while (matcher.find()) {
			urlsBlock = matcher.group(1);
		}

		return urlsBlock;
	}

	/**
	 * Retrieves the list of URLs from a URLs block.
	 * 
	 * @param input URLs block in string format
	 * @return the list of URLs
	 */
	private static List<String> retrieveURLs(String input) {
		List<String> urls = new LinkedList<String>();
		Matcher matcher = URL_PATTERN.matcher(input);

		while (matcher.find()) {
			urls.add(matcher.group(1));
		}

		return urls;
	}

	/**
	 * Retrieves the words block from a serialized WebDocument object string.
	 * 
	 * @param input serialized WebDocument in string format
	 * @return the words block
	 */
	private static String retrieveWordBlock(String input) {
		String wordsBlock = "";
		Matcher matcher = WORDS_PATTERN.matcher(input);

		while (matcher.find()) {
			wordsBlock = matcher.group(1);
		}

		return wordsBlock;
	}

	/**
	 * Retrieves the list of words from a words block.
	 * 
	 * @param input words block in string format
	 * @return the list of words
	 */
	private static List<String> retrieveWords(String input) {
		List<String> words = new LinkedList<String>();
		Matcher matcher = WORD_PATTERN.matcher(input);

		while (matcher.find()) {
			words.add(matcher.group(1));
		}

		return words;
	}

}
