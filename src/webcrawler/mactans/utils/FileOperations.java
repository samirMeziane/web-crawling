package webcrawler.mactans.utils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import webcrawler.mactans.webdocument.WebDocument;
import webcrawler.mactans.webdocument.WebDocumentOperations;

/**
 * This class contains various methods for file manipulation.
 *
 */
public class FileOperations {

	/**
	 * href component's pattern in an HTML file.
	 */
	private static final Pattern HREF_PATTERN = Pattern.compile("href=\"(.*?)\"|HREF=\"(.*?)\"", Pattern.DOTALL);

	/**
	 * WebDocument string pattern when it's in a save file.
	 */
	private static final Pattern WEBDOCUMENT_LOAD_PATTERN = Pattern.compile("webdocument\\[(.*?)\\].", Pattern.DOTALL);

	/**
	 * Template pattern to fill a WebDocument's String data in.
	 */
	private static final String WEBDOCUMENT_WRITE_PATTERN = "webdocument[\n>_data_<].";

	/**
	 * Pattern representing data to be filled.
	 */
	private static final String DATA_PATTERN = ">_data_<";

	/**
	 * The directory where the program is executed.
	 */
	public static final String CURRENT_DIRECTORY = System.getProperty("user.dir");

	/**
	 * The directory where the HTML template files are stored.
	 */
	public static final String HTML_RESOURCES_DIRECTORY = FileOperations.CURRENT_DIRECTORY + "/data/html";

	/**
	 * The directory where the saves file is stored.
	 */
	public static final String INDEX_FILE_DIRECTORY = FileOperations.CURRENT_DIRECTORY + "/data/index";

	/**
	 * The saves file.
	 */
	public static final String BACK_UP_FILE = INDEX_FILE_DIRECTORY + "/backup";

	/**
	 * The homepage HTML file.
	 */
	public static final String HOMEPAGE_PATH = HTML_RESOURCES_DIRECTORY + "/homepage.html";

	/**
	 * The homepage with a disconnection message HTML file.
	 */
	public static final String HOMEPAGE_DISCONNECTED_PATH = HTML_RESOURCES_DIRECTORY + "/homepage-disconnected.html";

	/**
	 * The HTML file for exploration results.
	 */
	public static final String RESULT_PAGE_PATH = HTML_RESOURCES_DIRECTORY + "/address-result-page.html";

	/**
	 * Word search result page's HTML file.
	 */
	public static final String WORD_SEARCH_RESULT_PAGE = HTML_RESOURCES_DIRECTORY + "/word-search-result-page.html";

	/**
	 * Reads the local saves file.
	 * 
	 * @return the list of WebDocument objects read from the backup
	 * @throws IOException
	 */
	public static final List<WebDocument> readFromBackUpFile() throws IOException {
		System.out.println("info: reading from local backup...");

		List<WebDocument> storedData = new LinkedList<WebDocument>();

		File directory = new File(INDEX_FILE_DIRECTORY);
		File backupFile = new File(BACK_UP_FILE);
		backupFile.setWritable(true);

		if (!directory.mkdir()) {
			if (backupFile.isFile()) {
				String data = Files.readString(backupFile.toPath()).strip();

				Matcher matcher = WEBDOCUMENT_LOAD_PATTERN.matcher(data);

				while (matcher.find()) {
					storedData.add(
							WebDocumentOperations.stringToWebDocument(data.substring(matcher.start(), matcher.end())));
				}

			} else {
				backupFile.createNewFile();
			}
		} else {
			backupFile.createNewFile();
		}

		System.out.println("info: done reading local backup");

		return storedData;
	}

	/**
	 * Writes WebDocument objects to the saves file.
	 * 
	 * @param data WebDocument objects to be written
	 * @throws IOException
	 */
	public static final void writeToBackupFile(List<WebDocument> data) throws IOException {
		System.out.println("info: writing to save file...");

		File directory = new File(INDEX_FILE_DIRECTORY);
		File backupFile = new File(BACK_UP_FILE);

		directory.mkdir();
		backupFile.createNewFile();
		backupFile.setWritable(true);

		List<WebDocument> backup = readFromBackUpFile();
		List<String> outputData = new LinkedList<String>();

		try {
			outputData = data.stream()
					.filter(webdocument -> !addressIsAlreadyExplored(webdocument.getAddress(), backup))
					.map(page -> WebDocumentOperations.webDocumentToString(page))
					.map(webDocument -> WEBDOCUMENT_WRITE_PATTERN.replace(DATA_PATTERN, webDocument))
					.collect(Collectors.toList());
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}

		try {
			Files.write(backupFile.toPath(), outputData, StandardOpenOption.APPEND);
		} catch (IOException e) {
			System.out.println(e.getMessage());
		}
		System.out.println("info: wrote to save file");
	}

	/**
	 * Scans for URLs in HTML data.
	 * 
	 * @param address URL who's page is being searched
	 * @param file    HTML data
	 * @return the list of URLs that were found during the scan
	 */
	public static final List<String> scanForURLs(String address, String file) {
		List<String> urlsList = new LinkedList<String>();

		String webAddress = address.strip();

		Matcher matcher = HREF_PATTERN.matcher(file.strip().trim());
		matcher.matches();

		String url = " ";

		while (matcher.find()) {
			url = matcher.group(0);

			if (url != null) {
				url = url.strip();
				url = url.substring(6, url.length() - 1);

				if (!url.toLowerCase().startsWith("https://") && !url.toLowerCase().startsWith("ftp:")
						&& !url.toLowerCase().startsWith("mailto:") && !url.toLowerCase().startsWith("file:")
						&& !url.toLowerCase().startsWith("data:") && !url.toLowerCase().startsWith("irc:")) {

					if (url.toLowerCase().startsWith("http://")) {
						urlsList.add(url);

					} else {

						if (url.startsWith("//")) {
							urlsList.add("http:" + url);

						} else {

							try {
								URL parentURL = new URL(webAddress);
								URL childURL = new URL(parentURL, url);
								URI fullURL = new URI(childURL.getProtocol(), childURL.getUserInfo(),
										childURL.getHost(), childURL.getPort(), childURL.getPath(), childURL.getQuery(),
										childURL.getRef());

								urlsList.add(fullURL.toString());
							} catch (MalformedURLException | URISyntaxException e) {
								continue;
							}
						}
					}
				}
			}
		}

		return urlsList;
	}

	/**
	 * Extracts HTTP's content type from a header.
	 * 
	 * @param input the header to be scanned for content
	 * @return content type
	 */
	public static String snipContentType(String input) {
		int startIndex = input.indexOf("content-type");
		int endIndex = input.indexOf("\n", startIndex);
		return input.substring(startIndex + "Content-Type".length(), endIndex).replace(":", "").trim();
	}

	/**
	 * Extracts HTTP's content size from a header.
	 * 
	 * @param input the header to be scanned for content
	 * @return content size
	 */
	public static String snipContentSize(String input) {
		int startIndex = input.indexOf("content-length");
		int endIndex = input.indexOf("\n", startIndex);
		return input.substring(startIndex + "Content-Length".length(), endIndex).replace(":", "").trim();
	}

	/**
	 * Extracts HTTP's content location from a header.
	 * 
	 * @param input the header to be scanned for content
	 * @return content location
	 */
	public static String snipContentLocation(String input) {
		int startIndex = input.indexOf("location");
		int endIndex = input.indexOf("\n", startIndex);
		return input.substring(startIndex + "Location".length(), endIndex).replaceFirst(":", "").trim();
	}

	/**
	 * Extracts HTTP's content encoding from a header.
	 * 
	 * @param input the header to be scanned for content
	 * @return content encoding
	 */
	public static String snipContentEncoding(String input) {
		int startIndex = input.indexOf("charset=");
		int endIndex = input.indexOf("\n", startIndex);
		return input.substring(startIndex + "charset=".length(), endIndex).trim();
	}

	/**
	 * Returns whether an address has been explored and exists in the saves file or not.
	 * 
	 * @param address the URL to be searched for
	 * @param data    the list of WebDocuments that exist in the backup file
	 * @return true if the address have already been explored and exists in the
	 *         saves file, false otherwise
	 */
	public static final boolean addressIsAlreadyExplored(String address, List<WebDocument> data) {
		return data.stream().anyMatch(webdocument -> webdocument.getAddress().equals(address));
	}

	/**
	 * Gets the WebDocument object from the saves file for the address passed as
	 * parameter.
	 * 
	 * @param address URL for the WebDocument to retrieve
	 * @param data saves file data
	 * @return the WebDocument object that was found
	 */
	public static final WebDocument getAlreadyExploredAddress(String address, List<WebDocument> data) {
		return data.stream().filter(webdocument -> webdocument.getAddress().equals(address))
				.collect(Collectors.toList()).get(0);

	}
}
