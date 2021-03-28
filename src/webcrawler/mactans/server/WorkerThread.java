package webcrawler.mactans.server;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;

import webcrawler.mactans.webdocument.WebDocument;

/**
 * Thread for handling word indexing for a client on the side, to relieve some
 * stress off the server thread itself.
 *
 */
public class WorkerThread implements Callable<Map<String, LinkedList<String>>> {

	/**
	 * WebDocuments' list who's words are to be indexed.
	 */
	private ArrayBlockingQueue<WebDocument> todo;

	/**
	 * List of words found during the entire exploration session.
	 */
	private Map<String, LinkedList<String>> words;

	/**
	 * Constructs a new thread and populates it with data to be processed.
	 * 
	 * @param list the list of WebDocument objects found during an exploration, to be
	 *             added to the index
	 */
	public WorkerThread(List<WebDocument> list) {
		this.words = new HashMap<String, LinkedList<String>>();
		this.todo = new ArrayBlockingQueue<WebDocument>(list.size());
		todo.addAll(list);
	}

	/**
	 * Creates the index.
	 */
	@Override
	public Map<String, LinkedList<String>> call() throws Exception {
		while (!todo.isEmpty()) {
			try {
				WebDocument webDocument = todo.take();
				webDocument.getWords().stream().map(word -> word.strip()).filter(word -> !word.equals("")).distinct()
						.forEach(word -> addToIndex(word, webDocument.getAddress()));

			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		return words;
	}

	/**
	 * Adds a word - address tuple to the index.
	 * 
	 * @param word    the word to be added
	 * @param address the address to be added
	 */
	private void addToIndex(String word, String address) {
		if (words.containsKey(word)) {
			words.get(word).add(address);
		} else {
			words.put(word, new LinkedList<String>());
			words.get(word).add(address);
		}
	}

	public Map<String, LinkedList<String>> getIndex() {
		return words;
	}

}
