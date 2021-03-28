package webcrawler.mactans.explorer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

import webcrawler.mactans.server.Server;
import webcrawler.mactans.utils.FileOperations;
import webcrawler.mactans.utils.HTMLScrambler;
import webcrawler.mactans.utils.HTTPUtilities;
import webcrawler.mactans.utils.HTTPUtilities.HTTPStatusCodes;
import webcrawler.mactans.webdocument.WebDocument;
import webcrawler.mactans.webdocument.WebDocumentOperations;
import webcrawler.mactans.webdocument.URL.URLType;

/**
 * Explorer thread. When first created and started, it connects to the server
 * and requests to be attached to a job. Once that's, done it enters a loop of
 * slave / master with the server via network where it waits for URLs sent by
 * the server, explores them, and then sends the result back to the server.
 * 
 * @see Server
 *
 */
public class Explorer extends Thread {

	/**
	 * Character encoders for our IO operations.
	 */
	public static final Charset UTF_8 = StandardCharsets.UTF_8;
	public static final Charset ISO_8859_1 = StandardCharsets.ISO_8859_1;

	/**
	 * Channel for our connection with the server.
	 */
	private SocketChannel socketChannel;

	/**
	 * Monitors the channel's state for IO updates.
	 */
	private Selector channelSelector;

	/**
	 * A placeholder for when we receive a URL from the server. While a capacity of
	 * 1 would still work as fine, it was set to 100 just to be sure.
	 */
	private ArrayBlockingQueue<String> url = new ArrayBlockingQueue<String>(100);

	/**
	 * Client ID to whom this explorer's result gets forwarded to by the server.
	 */
	private UUID id;

	/**
	 * Stores whether this explorer is running or not.
	 */
	private boolean running = false;

	/**
	 * Whether this explorer is registered to a client or not.
	 */
	private Boolean registered = false;

	/**
	 * Buffer for write operations.
	 */
	private ByteBuffer writeBuffer = ByteBuffer.allocate(1000 * 4096);

	/**
	 * Buffer for read operations.
	 */
	private ByteBuffer readBuffer = ByteBuffer.allocate(1 * 4096);

	/**
	 * Constructs a new explorer.
	 * 
	 * @param port the port to which the explorer connects to
	 * @param id   client's ID who's concerned by this explorer's results
	 */
	public Explorer(int port, UUID id) {
		this.id = id;
		this.running = true;

		try {
			channelSelector = SelectorProvider.provider().openSelector();
			socketChannel = SocketChannel.open(new InetSocketAddress("127.0.0.1", port));
			socketChannel.configureBlocking(false);
			socketChannel.register(channelSelector, SelectionKey.OP_WRITE);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Explorer thread's run method.
	 */
	@Override
	public void run() {
		while (running) {
			try {
				channelSelector.select();
				Iterator<SelectionKey> keyIterator = channelSelector.selectedKeys().iterator();

				while (keyIterator.hasNext()) {

					SelectionKey key = (SelectionKey) keyIterator.next();
					keyIterator.remove();

					if (key.isValid()) {

						if (key.isConnectable()) {
							connect(key);
						} else if (key.isReadable()) {
							read(key);
						} else if (key.isWritable()) {
							write(key);
						}

					} else
						continue;
				}

			} catch (IOException | InterruptedException | URISyntaxException e) {
				e.printStackTrace();
			}
		}

	}

	/**
	 * Finalize the connection for the channel of the SelectionKey key.
	 * 
	 * @param key key whose channel wants to connect
	 * @throws ClosedChannelException if the channel was closed before the
	 *                                connection was finalized
	 */
	private void connect(SelectionKey key) throws ClosedChannelException {
		SocketChannel channel = (SocketChannel) key.channel();

		try {
			channel.finishConnect();
		} catch (IOException e) {
			e.printStackTrace();
		}

		channel.register(channelSelector, SelectionKey.OP_WRITE);
	}

	/**
	 * Write to the channel of the SelectionKey key.
	 * 
	 * @param key key to whose channel write to
	 * @throws CharacterCodingException
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws InterruptedException
	 */
	private void write(SelectionKey key)
			throws CharacterCodingException, IOException, URISyntaxException, InterruptedException {
		SocketChannel channel = (SocketChannel) key.channel();

		if (!registered) {
			String response = id.toString();

			writeBuffer.clear();
			writeBuffer.put(UTF_8.encode(CharBuffer.wrap(response)));
			writeBuffer.flip();
			channel.write(writeBuffer);

			registered = true;

		} else {
			String address = url.take();

			WebDocument webDocument = harnessURLs(address);

			String response = WebDocumentOperations.webDocumentToString(webDocument);

			writeBuffer.clear();
			writeBuffer.put(UTF_8.encode(CharBuffer.wrap(response)));
			writeBuffer.flip();

			channel.write(writeBuffer);
		}

		key.interestOps(SelectionKey.OP_READ);
	}

	/**
	 * Read from the key's channel.
	 * 
	 * @param key from whose channel read from
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void read(SelectionKey key) throws IOException, InterruptedException {
		SocketChannel channel = (SocketChannel) key.channel();
		String bytesRead = "";

		readBuffer.clear();
		channel.read(readBuffer);
		readBuffer.flip();
		bytesRead = UTF_8.decode(readBuffer).toString();
		url.put(bytesRead);

		key.interestOps(SelectionKey.OP_WRITE);
	}

	/**
	 * Stop this thread.
	 */
	public void shutdown() {
		running = false;
	}

	/**
	 * Download the webpage for the URL passed as parameter.
	 * 
	 * @param url     address for the webpage to be downloaded
	 * @param charset character encoding for the content of the webpage
	 * @return the downloaded webpage in string format
	 */
	public static String downloadWebpage(String url, Charset charset) {
		String data = "";

		URL urlObject = null;
		ReadableByteChannel readableByteChannel = null;
		Scanner scanner = null;

		try {
			urlObject = new URL(url);
			readableByteChannel = Channels.newChannel(urlObject.openStream());

			scanner = new Scanner(readableByteChannel, charset);

			while (scanner.hasNextLine()) {
				data += " " + scanner.nextLine();
			}

		} catch (IOException e) {
			System.out.println(e.getMessage());
		} finally {
			try {
				if (readableByteChannel != null) {
					scanner.close();
					readableByteChannel.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return data;
	}

	/**
	 * Get HTTP header's contents for a URL.
	 * 
	 * @param url the URL to get HTTP header's info for
	 * @return an array containing the contents
	 */
	public static Object[] getContentType(String url) {
		Object[] httpResponseContent = new Object[5];
		URI uri = null;

		try {
			uri = new URI(url);
		} catch (URISyntaxException e) {
			System.out.println(e.getMessage());
			HTTPUtilities.pageDown(httpResponseContent);
			return httpResponseContent;
		}

		String hostname = uri.getHost();
		int port = uri.getPort();

		if (port == -1)
			port = 80;

		String path = uri.getRawPath();

		if (path == null || path.length() == 0)
			path = "/";

		System.out.println("Connecting to: " + url + " - Host: " + hostname + ":" + port + " w/ Path: " + path);
		SocketAddress socketAddress = new InetSocketAddress(hostname, port);
		SocketChannel socketChannel = null;

		try {
			socketChannel = SocketChannel.open(socketAddress);
		} catch (UnresolvedAddressException | IOException e) {
			System.out.println("Couldn't resolve address: " + url);
		}

		if (socketChannel != null) {
			try {
				socketChannel.configureBlocking(false);

				String request = HTTPUtilities.getHeaderForAddress(path, hostname);
				String bytesRead = "";
				String bytesOld = "-";

				HTTPStatusCodes status = HTTPStatusCodes.BAD_REQUEST;
				String contentType = "";
				String contentSize = "";
				String contentLocation = "";
				String contentEncoding = "";

				socketChannel.write(UTF_8.encode(CharBuffer.wrap(request)));

				ByteBuffer buffer = ByteBuffer.allocate(2 * 4096);

				while (socketChannel.read(buffer) != -1) {
					buffer.flip();
					bytesRead += UTF_8.decode(buffer).toString();
					buffer.clear();

					if (bytesRead.getBytes().length >= HTTPUtilities.MAXIMUM_HEADER_SIZE
							|| bytesRead.equals(bytesOld)) {
						break;
					}

					if (bytesRead.length() != 0) {
						bytesOld = bytesRead;
					}

				}

				bytesRead = bytesRead + "\n";

				if (bytesRead.toLowerCase().contains("content-type:")) {
					contentType = FileOperations.snipContentType(bytesRead.toLowerCase());
					contentEncoding = FileOperations.snipContentEncoding(bytesRead.toLowerCase());
				} else {
					contentType = "Unspecified";
					contentEncoding = "Unspecified";
				}

				if (bytesRead.toLowerCase().contains("content-length:")) {
					contentSize = FileOperations.snipContentSize(bytesRead.toLowerCase()) + " octets";
				} else {
					contentSize = "Unspecified";
				}

				if (bytesRead.toLowerCase().contains("200 ok")) {
					status = HTTPStatusCodes.OK;
				} else if (bytesRead.toLowerCase().contains("404 not found")) {
					status = HTTPStatusCodes.NOT_FOUND;
				} else if (bytesRead.toLowerCase().contains("400 bad request")) {
					status = HTTPStatusCodes.BAD_REQUEST;
				} else if (bytesRead.toLowerCase().contains("301 moved permanently")) {
					status = HTTPStatusCodes.MOVED_PERMANENTLY;
					if (bytesRead.toLowerCase().contains("location:")) {
						contentLocation = FileOperations.snipContentLocation(bytesRead.toLowerCase());
					}
				} else if (bytesRead.toLowerCase().contains("302 found")) {
					status = HTTPStatusCodes.FOUND;
					if (bytesRead.toLowerCase().contains("location:")) {
						contentLocation = FileOperations.snipContentLocation(bytesRead.toLowerCase());
					}
				}

				socketChannel.close();

				httpResponseContent[0] = status;
				httpResponseContent[1] = contentType;
				httpResponseContent[2] = contentSize;

				if (status == HTTPStatusCodes.FOUND || status == HTTPStatusCodes.MOVED_PERMANENTLY) {
					httpResponseContent[3] = contentLocation;
				} else {
					httpResponseContent[3] = "Unspecified";
				}

				httpResponseContent[4] = contentEncoding;
			}

			catch (IOException e) {
				System.out.println(e.getMessage());
				HTTPUtilities.pageDown(httpResponseContent);
				return httpResponseContent;
			}

		} else {
			HTTPUtilities.pageDown(httpResponseContent);
		}

		return httpResponseContent;
	}

	/**
	 * Creates a WebDocument and fills it with data from downloadURL and
	 * getContentType functions.
	 * 
	 * @param url address to be downloaded and/or converted to WebDocument object
	 * @return a new WebDocument populated with data
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public static WebDocument harnessURLs(String url) throws IOException, URISyntaxException {
		Object[] response = getContentType(url);
		List<String> urls = new LinkedList<String>();
		List<String> words = new LinkedList<String>();

		WebDocument webDocument = null;

		if (response != null) {
			HTTPStatusCodes status = (HTTPStatusCodes) response[0];
			String actualType = (String) response[1];
			String size = (String) response[2];
			String location = (String) response[3];
			String encoding = (String) response[4];

			URLType type = actualType.contains("html") ? URLType.HTML : URLType.OTHER;
			Charset charset = encoding.contains("iso-8859-1") ? ISO_8859_1 : UTF_8;

			webDocument = new WebDocument(url, actualType, size, type);

			if (status.equals(HTTPStatusCodes.OK) && actualType.contains("html")) {
				String page = downloadWebpage(url, charset);
				urls = FileOperations.scanForURLs(url, page);
				words = HTMLScrambler.filterHTMLIntoWords(page);

				webDocument.setViableURLs(urls);
				webDocument.setWords(words);
			}

			if ((status.equals(HTTPStatusCodes.FOUND)
					|| status.equals(HTTPStatusCodes.MOVED_PERMANENTLY)) && location != null) {
				System.out.println(url + " moved to: " + location);
				
				location = location.strip();
				
				if (location.startsWith("http://")) {
					webDocument.addToURLs(location);
				}
			}
		}

		return webDocument;
	}

	public UUID getUUID() {
		return id;
	}

}
