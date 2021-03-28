package webcrawler.mactans.client;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

import webcrawler.mactans.server.Server;
import webcrawler.mactans.webdocument.WebDocument;

/**
 * Class representing a client on the server. Each client has an identifier as
 * well as information and data about an exploration.
 *
 */
public class Client {

	/**
	 * Represents a client's request. A request can be a word search
	 * request (WORD_REQUEST), an address search request (ADDRESS_REQUEST), or no
	 * request at all (NULL).
	 *
	 */
	public enum RequestType {
		WORD_REQUEST, ADDRESS_REQUEST, NULL
	}

	/**
	 * The number of processed URLs after which the exploration stops for this
	 * client. Initialized with the server's capacity value when the client is first
	 * created.
	 */
	private int capacity;

	/**
	 * URLs that have been discovered, but not yet explored are put in the queue.
	 */
	private int queueCapacity;

	/**
	 * A client is flagged as empty if his queue contains no URLs.
	 */
	private boolean isEmpty = true;

	/**
	 * The request type for this client.
	 */
	private RequestType requestType;

	/**
	 * The ID for this client.
	 */
	private UUID id;

	/**
	 * The address that was submitted by the client.
	 */
	private String requestedAddress;

	/**
	 * The word that was submitted by the client.
	 */
	private String requestedWord = "";

	/**
	 * Addresses that were explored for this client, and thus are now discovered.
	 * (the same as the result list but in string format)
	 */
	private List<String> discoveries = new LinkedList<String>();

	/**
	 * Addresses that were explored for this client, and are discovered, as
	 * WebDocument objects.
	 */
	private List<WebDocument> result = new LinkedList<WebDocument>();

	/**
	 * URLs that were found during an exploration but are yet to be explored.
	 */
	private ArrayBlockingQueue<String> urlsQueue;

	/**
	 * Constructs a client.
	 * 
	 * @param id               identifier for this client
	 * @param requestedAddress the URL that was requested to the server by this
	 *                         client
	 */
	public Client(UUID id, String requestedAddress) {
		this.capacity = Server.capacity;
		this.queueCapacity = capacity * 5;
		this.id = id;
		this.isEmpty = false;
		this.requestType = RequestType.NULL;
		this.requestedAddress = requestedAddress;
		this.urlsQueue = new ArrayBlockingQueue<String>(queueCapacity);
		urlsQueue.add(requestedAddress);
	}

	public int getCapacity() {
		return capacity;
	}

	public int getQueueCapacity() {
		return queueCapacity;
	}

	public String getRequestedWord() {
		return requestedWord;
	}

	public void setRequestedWord(String requestedWord) {
		this.requestedWord = requestedWord;
	}

	public RequestType getRequestType() {
		return requestType;
	}

	public void setRequestType(RequestType requestType) {
		this.requestType = requestType;
	}

	public UUID getId() {
		return id;
	}

	public List<String> getDiscoveries() {
		return discoveries;
	}

	public void setDiscoveries(List<String> discoveries) {
		this.discoveries = discoveries;
	}

	public List<WebDocument> getResult() {
		return result;
	}

	public void setResult(List<WebDocument> result) {
		this.result = result;
	}

	public String getRequestedAddress() {
		return requestedAddress;
	}

	public void addToQueue(String url) throws InterruptedException {
		urlsQueue.put(url);
	}

	public String takeFromQueue() throws InterruptedException {
		return urlsQueue.take();
	}

	public void addToDiscoveries(String url) {
		discoveries.add(url);
	}

	public void addToResult(WebDocument webDocument) {
		result.add(webDocument);
	}

	public ArrayBlockingQueue<String> getUrlsQueue() {
		return urlsQueue;
	}

	public boolean isFull() {
		return result.size() >= capacity;
	}

	public void setEmpty(boolean value) {
		isEmpty = value;
	}

	public boolean isEmpty() {
		return isEmpty;
	}

	public boolean queueIsEmpty() {
		return urlsQueue.isEmpty();
	}

}
