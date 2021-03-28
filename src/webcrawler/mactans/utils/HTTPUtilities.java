package webcrawler.mactans.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import webcrawler.mactans.webdocument.WebDocument;

/**
 * This class contains code for HTTP operations.
 *
 */
public class HTTPUtilities {

	/**
	 * HTTP status codes.
	 * 
	 * @author 675lt
	 *
	 */
	public enum HTTPStatusCodes {
		OK, NOT_FOUND, BAD_REQUEST, MOVED_PERMANENTLY, FOUND, DOWN
	}

	/**
	 * HTTP get request pattern.
	 */
	public static final String HTTP_GET_REQUEST_PATTERN = "GET / HTTP/1.1";

	/**
	 * HTTP post request pattern.
	 */
	public static final String HTTP_POST_REQUEST_PATTERN = "POST / HTTP/1.1";

	/**
	 * HTTP post request address parameter pattern.
	 */
	public static final String HTTP_POST_REQUEST_ADDRESS_PARAMETER_PATTERN = "address=";

	/**
	 * HTTP post request word parameter pattern.
	 */
	public static final String HTTP_POST_REQUEST_WORD_PARAMETER_PATTERN = "word=";

	/**
	 * The maximum size an HTTP header can have.
	 */
	public static final int MAXIMUM_HEADER_SIZE = 2 * 4096;

	/**
	 * Pattern to be replaced by the word search result message.
	 */
	private static final String SEARCH_MESSAGE_PATTERN = ">_message_<";

	/**
	 * Heading pattern for when a word search is positive.
	 */
	private static final String POSITIVE_MESSAGE = "<h3 id=\"info2\">Here's where we found \">_word_<\":</h3>";

	/**
	 * Heading pattern for when a word search is negative.
	 */
	private static final String NEGATIVE_MESSAGE = "<h3 id=\"info2\">We couldn't find \">_word_<\".</h3>";

	/**
	 * Pattern to be replaced by a URL.
	 */
	private static final String ADDRESS_PATTERN = ">_address_<";

	/**
	 * Pattern to be replaced by a word.
	 */
	private static final String WORD_PATTERN = ">_word_<";

	/**
	 * Pattern to be replaced by an ID.
	 */
	private static final String ID_PATTERN = ">_id_<";

	/**
	 * List entry pattern for a given address.
	 */
	private static final String ADDRESS_LIST_ENTRY_PATTERN = "<li><a href=\">_address_<\" target=\"_blank\">>_address_<</a></li>";

	/**
	 * Table cell pattern for a given address.
	 */
	private static final String ADDRESS_CELL_PATTERN = "<td><a href=\">_data_<\" target=\"_blank\">>_data_<</a></td>";

	/**
	 * Table cell pattern for a non address element.
	 */
	private static final String NORMAL_CELL_PATTERN = "<td>>_data_<</td>";

	/**
	 * Table line pattern.
	 */
	private static final String TABLE_LINE_PATTERN = "<tr>>_data_<</tr>";

	/**
	 * Pattern to be replaced by data.
	 */
	private static final String DATA_CONTENT_PATTERN = ">_data_<";

	/**
	 * Generic HTTP response header to be sent to the client.
	 */
	public static final String GENERIC_RESPONSE_HEADER = "HTTP/1.1 200 OK\r\n"
			+ "Date: Mon, 01 Jan 2020 12:12:12 GMT\r\n" + "Server: Apache/2.4.43 (Win64)\r\n"
			+ "Last-Modified: Mon, 01 Jan 2020 12:12:12 GMT\r\n" + "Content-Type: text/html\r\n"
			+ "Connection: Closed\r\n";

	/**
	 * Returns a string representing the HTML address search response page, filled
	 * with data from the scan.
	 * 
	 * @param requestedAddress the URL for the page that was scanned
	 * @param webDocuments     list of the pages that were found during the scan
	 * @param id               client ID for whom the scan was done
	 * @return the address search result page filled with data
	 * @throws IOException
	 */
	public static final String addressListToHTML(String requestedAddress, List<WebDocument> webDocuments, UUID id)
			throws IOException {
		String resultPage = Files.readString(Paths.get(FileOperations.RESULT_PAGE_PATH));
		String result = "";

		for (WebDocument page : webDocuments) {
			String address = page.getAddress();
			String type = page.getActualType();
			String size = page.getSize();
			result += TABLE_LINE_PATTERN.replace(DATA_CONTENT_PATTERN,
					ADDRESS_CELL_PATTERN.replace(DATA_CONTENT_PATTERN, address)
							+ NORMAL_CELL_PATTERN.replace(DATA_CONTENT_PATTERN, type)
							+ NORMAL_CELL_PATTERN.replace(DATA_CONTENT_PATTERN, size));
		}

		resultPage = resultPage.replace(DATA_CONTENT_PATTERN, result);
		return resultPage.replaceAll(ADDRESS_PATTERN, requestedAddress).replaceAll(ID_PATTERN, id.toString());
	}

	/**
	 * Returns the HTML for the homepage in string format.
	 * 
	 * @param disconnected if the homepage should prompt the client with a
	 *                     disconnection message
	 * @return homepage in string format
	 * @throws IOException
	 */
	public static final String homepage(boolean disconnected) throws IOException {
		String homepage = disconnected ? Files.readString(Paths.get(FileOperations.HOMEPAGE_DISCONNECTED_PATH))
				: Files.readString(Paths.get(FileOperations.HOMEPAGE_PATH));
		return homepage;
	}

	/**
	 * Returns the HTML for the word search result page in string format.
	 * 
	 * @param id    ID of the client who made the search
	 * @param word  word that was searched
	 * @param index the index of the URL scan
	 * @return word search result page in string format filled with data
	 * @throws IOException
	 */
	public static final String wordSearchResult(UUID id, String word, Map<String, LinkedList<String>> index)
			throws IOException {
		String wordSearchResult = Files.readString(Paths.get(FileOperations.WORD_SEARCH_RESULT_PAGE));
		List<String> urls = new LinkedList<String>();
		List<String> resultUrls = new LinkedList<String>();
		String result = "";

		index.forEach((field, list) -> {
			if (field.equalsIgnoreCase(word)) {
				urls.addAll(list);
			}
		});

		resultUrls = urls.stream().distinct().collect(Collectors.toList());

		String message = resultUrls.isEmpty() ? NEGATIVE_MESSAGE : POSITIVE_MESSAGE;

		for (String url : resultUrls) {
			result += ADDRESS_LIST_ENTRY_PATTERN.replaceAll(ADDRESS_PATTERN, url);
		}

		return wordSearchResult.replace(DATA_CONTENT_PATTERN, result).replaceAll(ID_PATTERN, id.toString())
				.replace(SEARCH_MESSAGE_PATTERN, message).replaceAll(WORD_PATTERN, word);
	}

	/**
	 * Fills and returns an HTTP header for a given URL.
	 * 
	 * @param path     URL's path
	 * @param hostname URL's hostname
	 * @return the header for the given URL
	 */
	public static final String getHeaderForAddress(String path, String hostname) {
		return "GET " + path + " HTTP/1.1\r\n" + "Host: " + hostname + "\r\n" + "Connection: close\r\n" + "\r\n";
	}

	/**
	 * Returns HTTP header's content fields for when a page is down.
	 * 
	 * @param httpResponseContent an array of HTTP content's fields 
	 */
	public static final void pageDown(Object[] httpResponseContent) {
		httpResponseContent[0] = HTTPStatusCodes.DOWN;
		httpResponseContent[1] = "Page Down";
		httpResponseContent[2] = "Page Down";
		httpResponseContent[3] = "Page Down";
		httpResponseContent[4] = "Page Down";
	}

}
