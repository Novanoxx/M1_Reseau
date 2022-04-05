package fr.upem.net.tcp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerEchoWithConsole {
	static private class Context {
		private final SelectionKey key;
		private final SocketChannel sc;
		private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
		private boolean closed = false;

		private Context(SelectionKey key) {
			this.key = key;
			this.sc = (SocketChannel) key.channel();
		}

		/**
		 * Update the interestOps of the key looking only at values of the boolean
		 * closed and the ByteBuffer buffer.
		 *
		 * The convention is that buff is in write-mode.
		 */
		private void updateInterestOps() {
			var ops = 0;
			if (!closed && buffer.hasRemaining()) {
				ops |= SelectionKey.OP_READ;
			}
			if (buffer.position() != 0){
				ops |= SelectionKey.OP_WRITE;
			}
			if (ops == 0) {
				silentlyClose();
				return;
			}
			key.interestOps(ops);
		}

		/**
		 * Performs the read action on sc
		 *
		 * The convention is that buffer is in write-mode before calling doRead and is in
		 * write-mode after calling doRead
		 *
		 * @throws IOException
		 */
		private void doRead() throws IOException {
			if (sc.read(buffer) == -1) {
				logger.info("Connection closed");
				closed = true;
			}
			updateInterestOps();
		}

		/**
		 * Performs the write action on sc
		 *
		 * The convention is that buffer is in write-mode before calling doWrite and is in
		 * write-mode after calling doWrite
		 *
		 * @throws IOException
		 */
		private void doWrite() throws IOException {
			buffer.flip();
			if (closed && !buffer.hasRemaining()) {
				silentlyClose();
				return;
			}
			sc.write(buffer);
			buffer.compact();
			updateInterestOps();
		}

		private void silentlyClose() {
			try {
				sc.close();
			} catch (IOException e) {
				// ignore exception
			}
		}
	}

	private static final int BUFFER_SIZE = 1_024;
	private static final Logger logger = Logger.getLogger(ServerEchoWithConsole.class.getName());

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final Thread console;
	private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(10);

	public ServerEchoWithConsole(int port) throws IOException {
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
		}
	}

	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

		console.start();
		while (!Thread.interrupted()) {
			Helpers.printKeys(selector); // for debug
			System.out.println("Starting select");
			try {
				selector.select(this::treatKey);
				processCommands();
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
			logger.warning("accept() in doAccept() lied");
			return;
		}
		sc.configureBlocking(false);
		var keyContext = sc.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
		keyContext.attach(new Context(keyContext));
	}

	private void silentlyClose(SelectionKey key) {
		var sc = (Channel) key.channel();
		try {
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		if (args.length != 1) {
			usage();
			return;
		}
		new ServerEchoWithConsole(Integer.parseInt(args[0])).launch();
	}

	private static void usage() {
		System.out.println("Usage : ServerEcho port");
	}
}