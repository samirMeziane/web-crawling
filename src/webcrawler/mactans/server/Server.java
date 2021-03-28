package webcrawler.mactans.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import webcrawler.mactans.client.Client;
import webcrawler.mactans.client.Client.RequestType;
import webcrawler.mactans.explorer.Explorer;
import webcrawler.mactans.utils.ExplorerFactory;
import webcrawler.mactans.utils.FileOperations;
import webcrawler.mactans.utils.HTTPUtilities;
import webcrawler.mactans.webdocument.WebDocument;
import webcrawler.mactans.webdocument.WebDocumentOperations;

/**
 * Server "thread". Listens for both clients' and explorers' connections.
 * Represents the web server to which clients connect to and get their result
 * web pages from. It is the master of an Explorer.
 * 
 * @see Explorer
 * @see Client
 *
 */
public class Server implements Runnable {

	/**
	 * The maximum number of explored URLs after which an exploration stops.
	 */
	public static final int MAXIMUM_CAPACITY = 100;

	/**
	 * The maximum number of spawned explorers per request.
	 */
	public static final int MAXIMUM_SPAWN_RATE = 10;

	/**
	 * The default number of spawned explorers per request.
	 */
	private static final int DEFAULT_SPAWN_RATE = 5;

	/**
	 * Default client connection timeout.
	 */
	public static final int DEFAULT_TIMEOUT = 600000;

	/**
	 * Character encoding for IO operations.
	 */
	public static final Charset CHARSET = StandardCharsets.UTF_8;

	/**
	 * Server socket channel for explorers' IO.
	 */
	private ServerSocketChannel explorersServerChannel;

	/**
	 * Server socket channel for clients' IO.
	 */
	private ServerSocketChannel clientsServerChannel;

	/**
	 * Monitors the channels for state updates.
	 */
	private Selector channelSelector;

	/**
	 * Port numbers for client's and explorer's server socket channels
	 */
	private int explorersPort, clientsPort;

	/**
	 * Whether the server is running or not.
	 */
	private boolean running = false;

	public static int spawnRate = DEFAULT_SPAWN_RATE;
	public static int capacity = MAXIMUM_CAPACITY;
	private int timeout = DEFAULT_TIMEOUT;

	/**
	 * A list of clients that are connected to the server.
	 */
	private Map<UUID, Client> connectedClients = new HashMap<UUID, Client>();

	/**
	 * A HashMap of indexer threads that were submitted and their clients' IDs.
	 */
	private Map<UUID, Future<Map<String, LinkedList<String>>>> workerThreadsResults = new HashMap<UUID, Future<Map<String, LinkedList<String>>>>();

	/**
	 * Explorers that are connected to the server.
	 */
	private List<Explorer> connectedExplorers = new LinkedList<Explorer>();

	/**
	 * List of already explored WebDocuments read from the back-up file.
	 */
	private List<WebDocument> backUp = new LinkedList<WebDocument>();

	/**
	 * Constructs a new server.
	 * 
	 * @param explorersPort port number for clients' connection
	 * @param clientsPort   port number for explorers' connection
	 */
	public Server(int explorersPort, int clientsPort) {
		running = true;

		try {
			loadSaveFile();
		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			explorersServerChannel = ServerSocketChannel.open();
			explorersServerChannel.socket().bind(new InetSocketAddress(explorersPort));

			clientsServerChannel = ServerSocketChannel.open();
			clientsServerChannel.socket().bind(new InetSocketAddress(clientsPort));

			channelSelector = SelectorProvider.provider().openSelector();
			explorersServerChannel.configureBlocking(false);
			clientsServerChannel.configureBlocking(false);

			clientsServerChannel.register(channelSelector, SelectionKey.OP_ACCEPT);
			explorersServerChannel.register(channelSelector, SelectionKey.OP_ACCEPT);

			this.explorersPort = explorersServerChannel.socket().getLocalPort();
			this.clientsPort = clientsServerChannel.socket().getLocalPort();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Server thread's run method.
	 */
	@Override
	public void run() {
		System.out.println("info: server is running on localhost:" + clientsPort);

		while (running) {
			try {
				channelSelector.select();
				Iterator<SelectionKey> keyIterator = channelSelector.selectedKeys().iterator();

				while (keyIterator.hasNext()) {
					SelectionKey key = (SelectionKey) keyIterator.next();
					keyIterator.remove();

					if (key.isValid()) {

						if (key.isAcceptable()) {
							accept(key);
						} else if (key.isReadable()) {
							read(key);
						} else if (key.isWritable()) {
							write(key);
						}

					} else
						continue;
				}

			} catch (IOException | InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}

		System.out.println("info: server is down");
	}

	/**
	 * Channels registered with the channel selector and that are ready for write operations
	 * are handled here.
	 * 
	 * @param key key whose channel's ready for a write operation
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	private void write(SelectionKey key) throws IOException, InterruptedException, ExecutionException {
		int port = ((SocketChannel) key.channel()).socket().getLocalPort();

		if (port == clientsPort) {
			writeClient(key);
		} else if (port == explorersPort) {
			writeExplorer(key);
		}
	}

	/**
	 * Channels registered with the channel selector and that are ready for read operations
	 * are handled here.
	 * 
	 * @param key key whose channel's ready for a read operation
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	private void read(SelectionKey key) throws IOException, InterruptedException {
		int port = ((SocketChannel) key.channel()).socket().getLocalPort();

		if (port == clientsPort) {
			readClient(key);
		} else if (port == explorersPort) {
			if (key.attachment() == null) {
				attachID(key);
			} else if (key.attachment() != null) {
				readExplorer(key);
			}
		}
	}

	/**
	 * Accepts a connection, finalizes it and sets its interest set for read
	 * operations.
	 * 
	 * @param key the key who's channel is to accept
	 * @throws IOException
	 */
	private void accept(SelectionKey key) throws IOException {
		ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
		SocketChannel socketChannel = serverSocketChannel.accept();
		int port = socketChannel.socket().getLocalPort();
		socketChannel.configureBlocking(false);

		if (port == clientsPort) {
			socketChannel.register(channelSelector, SelectionKey.OP_READ);
		} else if (port == explorersPort) {
			socketChannel.register(channelSelector, SelectionKey.OP_READ);
		}
	}

	/**
	 * Handles data sent through a client's channel.
	 * 
	 * @param key key whose channel is a client channel
	 * @throws IOException
	 */
	private void readClient(SelectionKey key) throws IOException {
		SocketChannel channel = (SocketChannel) key.channel();
		String bytesRead = "";
		ByteBuffer readBuffer = ByteBuffer.allocate(100 * 4096);

		channel.read(readBuffer);
		readBuffer.flip();
		bytesRead = CHARSET.decode(readBuffer).toString();

		if (bytesRead.contains(HTTPUtilities.HTTP_POST_REQUEST_PATTERN)
				&& bytesRead.contains(HTTPUtilities.HTTP_POST_REQUEST_ADDRESS_PARAMETER_PATTERN)) {

			String addressParameter = bytesRead.substring(bytesRead.lastIndexOf("\n"));
			String url = URLDecoder.decode(addressParameter.strip().substring(8, addressParameter.length() - 1),
					CHARSET);

			Client client = new Client(UUID.randomUUID(), url);

			List<Explorer> explorers = ExplorerFactory.makeExplorers(explorersPort, spawnRate, client.getId());
			connectedExplorers.addAll(explorers);

			ExecutorService executorService = Executors.newSingleThreadExecutor();

			executorService.execute(new Runnable() {
				public void run() {
					ExplorerFactory.startExplorers(explorers);
				}
			});

			executorService.shutdown();

			key.attach(client.getId());

			client.setRequestType(RequestType.ADDRESS_REQUEST);
			connectedClients.put(client.getId(), client);

		} else if (bytesRead.contains(HTTPUtilities.HTTP_POST_REQUEST_PATTERN)
				&& bytesRead.contains(HTTPUtilities.HTTP_POST_REQUEST_WORD_PARAMETER_PATTERN)) {

			String wordParameter = bytesRead.substring(bytesRead.lastIndexOf("\n"));
			String[] parameters = wordParameter.trim().split("&");

			String word = URLDecoder.decode(parameters[0].strip().substring(5, parameters[0].length()), CHARSET);
			String id = parameters[1].strip().substring(3, parameters[1].length());

			if (connectedClients.get(UUID.fromString(id)) != null) {
				connectedClients.get(UUID.fromString(id)).setRequestType(RequestType.WORD_REQUEST);
				connectedClients.get(UUID.fromString(id)).setRequestedWord(word);

			}

			key.attach(UUID.fromString(id));
		}

		key.interestOps(SelectionKey.OP_WRITE);
	}

	/**
	 * Gets the necessary data and writes it to a client channel.
	 * 
	 * @param key key for the client channel
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	private void writeClient(SelectionKey key) throws IOException, InterruptedException, ExecutionException {
		SocketChannel channel = (SocketChannel) key.channel();

		if (key.attachment() == null) {
			String header = HTTPUtilities.GENERIC_RESPONSE_HEADER;
			String body = HTTPUtilities.homepage(false);

			ByteBuffer writeBuffer = ByteBuffer.allocate(100 * 4096);
			writeBuffer.clear();
			writeBuffer.put(CHARSET.encode(CharBuffer.wrap(header + body)));
			writeBuffer.flip();

			channel.write(writeBuffer);
			channel.close();

		} else if (key.attachment() != null && connectedClients.containsKey(key.attachment())
				&& (connectedClients.get(key.attachment()).isFull()
						|| connectedClients.get(key.attachment()).isEmpty())) {

			Client client = connectedClients.get(key.attachment());

			if (!workerThreadsResults.containsKey(client.getId())) {
				ExecutorService executorService = Executors.newSingleThreadExecutor();

				WorkerThread workerThread = new WorkerThread(client.getResult());

				Future<Map<String, LinkedList<String>>> indexSearchResult = executorService.submit(workerThread);
				workerThreadsResults.put(client.getId(), indexSearchResult);

				executorService.shutdown();
			}

			if (client.getRequestType().equals(RequestType.ADDRESS_REQUEST)) {
				freeUpResources((UUID) key.attachment(), false);

				String header = HTTPUtilities.GENERIC_RESPONSE_HEADER;

				List<WebDocument> addresses = client.getResult();
				String initialAddress = client.getRequestedAddress();

				String body = HTTPUtilities.addressListToHTML(initialAddress, addresses, (UUID) key.attachment());

				ByteBuffer writeBuffer = ByteBuffer.allocate(100 * 4096);
				writeBuffer.clear();
				writeBuffer.put(CHARSET.encode(CharBuffer.wrap(header + body)));
				writeBuffer.flip();

				channel.write(writeBuffer);
				channel.close();

				writeToSaveFile(client.getResult());
				startTimer(client);

			} else if (client.getRequestType().equals(RequestType.WORD_REQUEST)
					&& workerThreadsResults.get(key.attachment()).isDone()) {

				freeUpResources((UUID) key.attachment(), false);

				Map<String, LinkedList<String>> index = workerThreadsResults.get(key.attachment()).get();

				String header = HTTPUtilities.GENERIC_RESPONSE_HEADER;
				String body = HTTPUtilities.wordSearchResult((UUID) key.attachment(),
						connectedClients.get(key.attachment()).getRequestedWord(), index);

				ByteBuffer writeBuffer = ByteBuffer.allocate(100 * 4096);
				writeBuffer.clear();
				writeBuffer.put(CHARSET.encode(CharBuffer.wrap(header + body)));
				writeBuffer.flip();

				channel.write(writeBuffer);
				channel.close();

			}

		} else if (key.attachment() != null && !connectedClients.containsKey(key.attachment())) {
			String header = HTTPUtilities.GENERIC_RESPONSE_HEADER;
			String body = HTTPUtilities.homepage(true);

			ByteBuffer writeBuffer = ByteBuffer.allocate(100 * 4096);
			writeBuffer.clear();
			writeBuffer.put(CHARSET.encode(CharBuffer.wrap(header + body)));
			writeBuffer.flip();

			channel.write(writeBuffer);
			channel.close();
		}

	}

	/**
	 * Attaches an ID to the explorer whose channel is represented by the key.
	 * 
	 * @param key key for the explorer channel
	 * @throws IOException
	 */
	private void attachID(SelectionKey key) throws IOException {
		SocketChannel channel = (SocketChannel) key.channel();
		String bytesRead = "";
		ByteBuffer readBuffer = ByteBuffer.allocate(100 * 4096);

		channel.read(readBuffer);
		readBuffer.flip();
		bytesRead = CHARSET.decode(readBuffer).toString();

		key.attach(UUID.fromString(bytesRead));
		key.interestOps(SelectionKey.OP_WRITE);
	}

	/**
	 * Reads data from an explorer channel.
	 * 
	 * @param key key for the explorer channel
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void readExplorer(SelectionKey key) throws IOException, InterruptedException {
		Client client = connectedClients.get(key.attachment());

		if (client.isEmpty() || client.isFull()) {
			freeUpResources((UUID) key.attachment(), true);
		}

		SocketChannel channel = (SocketChannel) key.channel();

		String data = getBytes(channel, "");

		WebDocument document = WebDocumentOperations.stringToWebDocument(data);

		if (!client.getDiscoveries().contains(document.getAddress())) {
			client.addToDiscoveries(document.getAddress());
			client.addToResult(document);

			retrieveAlreadyExploredChildren(document, client);
		}

		key.interestOps(SelectionKey.OP_WRITE);
	}

	/**
	 * Recursive method that waits on the explorer's channel until an entire
	 * WebDocument is read.
	 * 
	 * @param channel   the channel to read from
	 * @param bytesRead bytes that were read during the previous pass of the method
	 * @return the WebDocument in String format
	 * @throws IOException
	 */
	private String getBytes(SocketChannel channel, String bytesRead) throws IOException {
		ByteBuffer readBuffer = ByteBuffer.allocate(1000 * 4096);
		String bytesChunck = "";
		channel.read(readBuffer);
		readBuffer.flip();
		bytesChunck = CHARSET.decode(readBuffer).toString();

		bytesRead += bytesChunck.strip();

		if (!WebDocumentOperations.METADATA_PATTERN.matcher(bytesRead).matches()
				&& !WebDocumentOperations.URLS_PATTERN.matcher(bytesRead).matches()
				&& !WebDocumentOperations.WORDS_PATTERN.matcher(bytesRead).matches()) {
			bytesRead += getBytes(channel, bytesRead);
		}

		return bytesRead;
	}

	/**
	 * Retrieves data from the backup file for URLs that have already been explored.
	 * 
	 * @param document WebDocument whose URLs are to be checked if they exist in the
	 *                 backup file
	 * @param client   client whose request is being treated
	 * @throws InterruptedException
	 */
	void retrieveAlreadyExploredChildren(WebDocument document, Client client) throws InterruptedException {
		List<String> urls = document.getViableURLs().stream().distinct().collect(Collectors.toList());

		for (String url : urls) {
			if (client.isFull()) {
				break;
			}

			if (FileOperations.addressIsAlreadyExplored(url, backUp)) {

				if (!client.getDiscoveries().contains(url)) {
					WebDocument alreadyExploredPage = FileOperations.getAlreadyExploredAddress(url, backUp);
					client.addToDiscoveries(alreadyExploredPage.getAddress());
					client.addToResult(alreadyExploredPage);

					retrieveAlreadyExploredChildren(alreadyExploredPage, client);
				}

			} else {
				if (!client.getUrlsQueue().contains(url) && client.getUrlsQueue().size() < client.getQueueCapacity()
						&& !client.getDiscoveries().contains(url)) {
					client.addToQueue(url);
				}
			}
		}

	}

	/**
	 * Write data to the explorer's channel whose SelectionKey is key.
	 * 
	 * @param key key for the channel
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void writeExplorer(SelectionKey key) throws IOException, InterruptedException {
		String response = "";
		Client client = connectedClients.get(key.attachment());

		if (client != null) {

			if (client.queueIsEmpty()) {
				client.setEmpty(true);
				freeUpResources((UUID) key.attachment(), true);

			} else {

				response = client.takeFromQueue();
				SocketChannel channel = (SocketChannel) key.channel();

				ByteBuffer writeBuffer = ByteBuffer.allocate(1 * 4096);
				writeBuffer.clear();
				writeBuffer.put(CHARSET.encode(CharBuffer.wrap(response)));
				writeBuffer.flip();

				channel.write(writeBuffer);
				key.interestOps(SelectionKey.OP_READ);
			}
		}
	}

	/**
	 * Disconnect and stop the explorers for a given client.
	 * 
	 * @param id   explorer's ID
	 * @param flag true to stop the explorer's thread even if it is still alive,
	 *             false otherwise
	 */
	private final void freeUpResources(UUID id, boolean flag) {
		List<Explorer> connectedExplorersForThisClient = new LinkedList<Explorer>();

		connectedExplorers.stream().filter(explorer -> explorer.getUUID() == id && (flag ? explorer.isAlive() : true))
				.forEach(explorer -> {
					explorer.shutdown();
					connectedExplorersForThisClient.add(explorer);
				});

		connectedExplorers.removeAll(connectedExplorersForThisClient);
	}

	/**
	 * Starts the disconnection count-down for a given client.
	 * 
	 * @param client client for whom the count-down starts
	 */
	private final void startTimer(Client client) {
		if (timeout < -1)
			return;

		new Timer().schedule(new TimerTask() {

			@Override
			public void run() {
				System.out.println("info: disconnecting client " + client.getId());
				disconnectClient(client.getId());
			}
		}, timeout);
	}

	/**
	 * Disconnects a client from the server.
	 * 
	 * @param id ID of the client to be disconnected
	 */
	private final void disconnectClient(UUID id) {
		freeUpResources(id, true);
		connectedClients.remove(id);
		workerThreadsResults.remove(id);
	}

	/**
	 * Writes an exploration result to the save file.
	 * 
	 * @param data data to write
	 * @throws IOException
	 */
	private void writeToSaveFile(List<WebDocument> data) throws IOException {
		FileOperations.writeToBackupFile(data);
	}

	/**
	 * Reads data from the backup file.
	 * 
	 * @throws IOException
	 */
	private synchronized void loadSaveFile() throws IOException {
		try {
			this.backUp = FileOperations.readFromBackUpFile();
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("error: couldn't read the backup file");
		}
	}

	/**
	 * Force read data from the save file.
	 * 
	 * @throws IOException
	 */
	public synchronized void reloadSaveFile() throws IOException {
		loadSaveFile();
	}

	/**
	 * Disconnects everyone and stop the server.
	 * 
	 * @throws IOException
	 */
	public void shutdown() throws IOException {
		System.out.println("info: server shutting-down...");

		running = !running;

		for (Explorer explorer : connectedExplorers) {
			explorer.shutdown();
			explorer.interrupt();
		}

		explorersServerChannel.close();
		clientsServerChannel.close();

	}

	@SuppressWarnings("static-access")
	public void updateSpawnRate(int count) {
		this.spawnRate = count;
		System.out.println("Server: spawn rate updated to: " + spawnRate);
	}

	@SuppressWarnings("static-access")
	public void updateCapacity(int count) {
		this.capacity = count;
		System.out.println("Server: capacity updated to: " + capacity);
	}

	public void updateTimeout(int timeout) {
		this.timeout = timeout;
		System.out.format(
				timeout == -1 * 60000 ? "Server: timeout removed\n\r" : "Server: timeout updated to %s minute(s)\n\r",
				timeout / 60000);
	}
}
