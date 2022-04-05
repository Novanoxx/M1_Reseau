package fr.upem.net.tcp.nonblocking;

import fr.upem.net.tcp.nonblocking.readers.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerChaton {
	static private class Context {
		private final SelectionKey key;
		private final SocketChannel sc;
		private final ByteBuffer bufferIn = ByteBuffer.allocate(2 * (BUFFER_SIZE + Integer.BYTES));
		private final ByteBuffer bufferOut = ByteBuffer.allocate(2 * (BUFFER_SIZE + Integer.BYTES));
		private final ArrayDeque<Message> queue = new ArrayDeque<>();
		private final ServerChaton server; // we could also have Context as an instance class, which would naturally
		// give access to ServerChatInt.this
		private boolean closed = false;
		private String name;
		private final PrivateMessageReader privateReader = new PrivateMessageReader();
		private final PublicMessageReader publicReader = new PublicMessageReader();
		//private final FileMessageReader fileMessageReader = new FileMessageReader();

		private Context(ServerChaton server, SelectionKey key) {
			this.key = key;
			this.sc = (SocketChannel) key.channel();
			this.server = server;
		}

		/**
		 * Process the content of bufferIn
		 *
		 * The convention is that bufferIn is in write-mode before the call to process and
		 * after the call
		 *
		 */
		private void processIn() {
			Reader.ProcessStatus status;
			while(true) {
				switch (bufferIn.getInt()) {
					case 4 : {
						status = publicReader.process(bufferIn);
						switch (status) {
							case DONE:
								var value = publicReader.get();
								server.broadcast(value, 4);
								publicReader.reset();
								break;
							case REFILL:
								return;
							case ERROR:
								silentlyClose();
								return;
						}
					}

					case 5 : {
						status = privateReader.process(bufferIn);
						switch (status) {
							case DONE:
								var value = privateReader.get();
								server.broadcast(value, 5);
								privateReader.reset();
								break;
							case REFILL:
								return;
							case ERROR:
								silentlyClose();
								return;
						}
					}
/*
					case 6 : {
						status = fileMessageReader.process(bufferIn);
						switch (status) {
							case DONE:
								var value = fileMessageReader.get();
								server.broadcast(value);
								fileMessageReader.reset();
								break;
							case REFILL:
								return;
							case ERROR:
								silentlyClose();
								return;
						}
					}

 */
				}
			}
		}

		/**
		 * Add a message to the message queue, tries to fill bufferOut and updateInterestOps
		 *
		 * @param msg
		 */
		public void queueMessage(Message msg) {
			queue.add(msg);
			processOut();
			updateInterestOps();
		}

		/**
		 * Try to fill bufferOut from the message queue
		 *
		 */
		private void processOut() {
			if (bufferOut.remaining() < Integer.BYTES) {
				return;
			}
			var message = queue.poll();
			if (message == null) {
				return;
			}
			var server = StandardCharsets.UTF_8.encode(message.getServerSrc());
			if (server.remaining() > 100) {
				return;
			}
			var login = StandardCharsets.UTF_8.encode(message.getLoginSrc());
			if (login.remaining() > 30) {
				return;
			}
			var text = StandardCharsets.UTF_8.encode(message.getMsg());
			if (text.remaining() > 1024) {
				return;
			}

			if (message.getOpCode() == 4) {
				bufferOut.putInt(4).putInt(server.remaining()).put(server);
				bufferOut.putInt(login.remaining()).put(login);
				bufferOut.putInt(text.remaining()).put(text);
			}
			if (message.getOpCode() == 5) {
				var serverDst = StandardCharsets.UTF_8.encode(message.getServerDst());
				if (serverDst.remaining() > 100) {
					return;
				}
				var loginDst = StandardCharsets.UTF_8.encode(message.getLoginDst());
				if (loginDst.remaining() > 30) {
					return;
				}
				bufferOut.putInt(5).putInt(server.remaining()).put(server);
				bufferOut.putInt(login.remaining()).put(login);
				bufferOut.putInt(serverDst.remaining()).put(serverDst);
				bufferOut.putInt(loginDst.remaining()).put(loginDst);
				bufferOut.putInt(text.remaining()).put(text);
			}
		}

		/**
		 * Update the interestOps of the key looking only at values of the boolean
		 * closed and of both ByteBuffers.
		 *
		 * The convention is that both buffers are in write-mode before the call to
		 * updateInterestOps and after the call. Also it is assumed that process has
		 * been be called just before updateInterestOps.
		 */

		private void updateInterestOps() {
			var ops = 0;
			if (!closed && bufferOut.hasRemaining()) {
				ops |= SelectionKey.OP_READ;
			}
			if (bufferOut.position() != 0){
				ops |= SelectionKey.OP_WRITE;
			}
			if (ops == 0) {
				silentlyClose();
				return;
			}
			key.interestOps(ops);
		}

		private void silentlyClose() {
			try {
				sc.close();
			} catch (IOException e) {
				// ignore exception
			}
		}

		/**
		 * Performs the read action on sc
		 *
		 * The convention is that both buffers are in write-mode before the call to
		 * doRead and after the call
		 *
		 * @throws IOException
		 */
		private void doRead() throws IOException {
			if (sc.read((bufferIn)) == -1) {
				closed = true;
			}
			processIn();
		}

		/**
		 * Performs the write action on sc
		 *
		 * The convention is that both buffers are in write-mode before the call to
		 * doWrite and after the call
		 *
		 * @throws IOException
		 */

		private void doWrite() throws IOException {
			bufferOut.flip();
			sc.write(bufferOut);
			bufferOut.compact();
			updateInterestOps();
		}

	}

	private static final int BUFFER_SIZE = 1_024;
	private static final Logger logger = Logger.getLogger(ServerChaton.class.getName());

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final Thread console;
	private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(10);

	public ServerChaton(int port) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		selector = Selector.open();
		this.console = new Thread(this::consoleRun);
	}

	private void consoleRun() {
		try {
			try (var scanner = new Scanner(System.in)) {
				while (scanner.hasNextLine() && !Thread.interrupted()) {
					var msg = scanner.nextLine();
					sendCommand(msg);
				}
			}
			logger.info("Console thread stopping");
		} catch (InterruptedException e) {
			logger.info("Console thread has been interrupted");
		}
	}

	private void sendCommand(String msg) throws InterruptedException {
		if (msg == null) {
			return;
		}
		queue.add(msg);
		selector.wakeup();
	}

	private void processCommands() {
		if (queue.isEmpty()) {
			return;
		}
		switch (queue.poll().toUpperCase()) {
			case "INFO" -> {
				int nbConnectedClient = 0;
				for (var key : selector.keys()) {
					var context = (Context) key.attachment();
					if (context != null && !context.closed) {
						nbConnectedClient++;
					}
				}
				logger.info("Connected client: " + nbConnectedClient + "\n");
			}

			case "SHUTDOWN" -> {
				logger.info("shut down\n");
				try {
					serverSocketChannel.close();
				} catch (IOException ioe) {
					// Ignore exception
				}
			}

			case "SHUTDOWNNOW" -> {
				logger.info("shut down now\n");
				selector.keys().forEach(this::silentlyClose);
				Thread.currentThread().interrupt();
			}

			default -> {
				break;
			}
		}
	}

	public void launch() throws IOException, InterruptedException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

		console.start();
		while (!Thread.interrupted()) {
			Helpers.printKeys(selector); // for debug
			System.out.println("Starting select");
			try {
				selector.select(this::treatKey);
				processCommands();
			} catch(ClosedByInterruptException e) {
				logger.info("ClosedByInterruptedException " + e);
			} catch (AsynchronousCloseException e) {
				logger.info("Closed connection due to timeout");
			} catch (UncheckedIOException tunneled) {
				throw tunneled.getCause();
			}
			System.out.println("Select finished");
		}
		console.interrupt();
	}

	private void treatKey(SelectionKey key) {
		Helpers.printSelectedKey(key); // for debug
		try {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
		} catch (IOException ioe) {
			// lambda call in select requires to tunnel IOException
			throw new UncheckedIOException(ioe);
		}
		try {
			if (key.isValid() && key.isWritable()) {
				((Context) key.attachment()).doWrite();
			}
			if (key.isValid() && key.isReadable()) {
				((Context) key.attachment()).doRead();
			}
		} catch (IOException e) {
			logger.log(Level.INFO, "Connection closed with client due to IOException", e);
			silentlyClose(key);
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
		SocketChannel sc = ssc.accept();
		if (sc == null) {
			return;
		}
		sc.configureBlocking(false);
		var client = sc.register(selector, SelectionKey.OP_READ);
		client.attach(new Context(this, client));
	}

	private void silentlyClose(SelectionKey key) {
		Channel sc = (Channel) key.channel();
		try {
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	/**
	 * Add a message to all connected clients queue
	 *
	 * @param msg
	 */
	private void broadcast(Message msg, int opCode) {
		switch (opCode) {
			case 4: {
				for (var key : selector.keys()) {
					var context = (Context) key.attachment();
					if (context != null) {
						context.queueMessage(msg);
					}
				}
				break;
			}

			case 5: {

			}
		}
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		if (args.length != 1) {
			usage();
			return;
		}

		var serverList = new ArrayList<Thread>();
		var server = new ServerChaton(Integer.parseInt(args[0]));

		for (int i = 0; i < 2; i++) {
			Thread thread = new Thread(() -> {
				try {
					server.launch();
				} catch (InterruptedException e) {
					logger.info("Interrupted server\n" + e);
				} catch (IOException ioe) {
					logger.log(Level.SEVERE, "Server " + Thread.currentThread().getName() + "problem\n" + ioe);
				}
			});
			serverList.add(thread);
			thread.start();
		}
	}

	private static void usage() {
		System.out.println("Usage : ServerChaton port");
	}
}