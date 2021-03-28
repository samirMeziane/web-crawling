package webcrawler.mactans.utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This class contains code for parsing HTML data.
 *
 */
public class HTMLScrambler {

	/**
	 * Regex for punctuation.
	 */
	private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[\\p{Punct}&&[^']]+");

	/**
	 * Regex for HTML tags.
	 */
	private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<(.*?)>", Pattern.DOTALL);

	/**
	 * Regex for HTML entities.
	 */
	private static final Pattern HTML_ENTITY_PATTERN = Pattern.compile("&(.*?);", Pattern.DOTALL);

	/**
	 * Regex for HTML style block.
	 */
	private static final Pattern HTML_STYLE_TAG_PATTERN = Pattern.compile("<style>(.*?)</style>", Pattern.DOTALL);

	/**
	 * Regex for HTML comment block.
	 */
	private static final Pattern HTML_COMMENT_TAG_PATTERN = Pattern.compile("<--(.*?)-->", Pattern.DOTALL);

	/**
	 * Regex for HTML script block.
	 */
	private static final Pattern HTML_SCRIPT_TAG_PATTERN = Pattern.compile("<script[^>]*>(.*?)</script>",
			Pattern.DOTALL);

	/**
	 * Regex for white spaces.
	 */
	private static final Pattern WORD_PATTERN = Pattern.compile("\\p{Space}+", Pattern.DOTALL);

	/**
	 * Parses and removes any HTML data, retains alphabetic words and phrases.
	 * 
	 * @param file HTML data to parse
	 * @return a list of words found during the parsing process
	 * @throws IOException
	 */
	public static List<String> filterHTMLIntoWords(String file) throws IOException {
		String scriptStripped = "";
		String styleStripped = "";
		String commentStripped = "";
		String htmlTagStripped = "";
		String entitiesStripped = "";
		String punctuationStripped = "";

		scriptStripped = removeHTMLScripts(file);
		styleStripped = removeHTMLStyles(scriptStripped);
		commentStripped = removeHTMLComments(styleStripped);
		htmlTagStripped = removeHTMLTags(commentStripped);
		entitiesStripped = removeHTMLEntities(htmlTagStripped);
		punctuationStripped = removePunctuation(entitiesStripped);

		return Arrays.asList(punctuationStripped.strip().trim().split(WORD_PATTERN.pattern()));
	}

	public static String removeHTMLScripts(String content) throws IOException {
		return HTML_SCRIPT_TAG_PATTERN.matcher(content).replaceAll(" ");
	}

	public static String removeHTMLComments(String content) throws IOException {
		return HTML_COMMENT_TAG_PATTERN.matcher(content).replaceAll(" ");
	}

	public static String removeHTMLTags(String content) throws IOException {
		return HTML_TAG_PATTERN.matcher(content).replaceAll(" ");
	}

	public static String removeHTMLStyles(String content) throws IOException {
		return HTML_STYLE_TAG_PATTERN.matcher(content).replaceAll(" ");
	}

	public static String removeHTMLEntities(String content) throws IOException {
		return HTML_ENTITY_PATTERN.matcher(content).replaceAll(" ");
	}

	public static String removePunctuation(String content) throws IOException {
		return PUNCTUATION_PATTERN.matcher(content).replaceAll(" ");
	}

}
