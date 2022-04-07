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
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerChaton {
	static private class Context {
		private final SelectionKey key;
		private final SocketChannel sc;
		private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
		private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
		private final ArrayDeque<Message> queueMsg = new ArrayDeque<>();
		private final ArrayDeque<String> queue = new ArrayDeque<>();
		private final ServerChaton server;

		private boolean closed = false;
		private final String name;
		private final PrivateMessageReader privateReader = new PrivateMessageReader();
		private final PublicMessageReader publicReader = new PublicMessageReader();
		private final StringReader stringReader = new StringReader();

		private Context(ServerChaton server, SelectionKey key) {
			this.key = key;
			this.sc = (SocketChannel) key.channel();
			this.server = server;
			this.name = nameServer;
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
			bufferIn.flip();
			var tmp = bufferIn.getInt();
			bufferIn.compact();
			while(true) {
				switch (tmp) {
					case 0 :
						status = stringReader.process(bufferIn);
						switch (status) {
							case DONE:
								var checkLogin = stringReader.get();
								if (listClient.containsKey(checkLogin)) {
									System.out.println("Login already used");
									processOut(3);
									silentlyClose();
									return;
								}
								stringReader.reset();
								listClient.put(checkLogin, key);
								server.sendLogin(key);
								break;
							case REFILL:
								return;
							case ERROR:
								silentlyClose();
								return;
						}
					break;

					case 4 :
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
					break;

					case 5 :
						status = privateReader.process(bufferIn);
						switch (status) {
							case DONE:
								var value = privateReader.get();
								if (listServer.containsKey(value.getServerDst())) {
									server.broadcast(value, 5);
								}
								privateReader.reset();
								break;
							case REFILL:
								return;
							case ERROR:
								silentlyClose();
								return;
						}
					break;
				}
			}
		}

		/**
		 * Add a message to the message queue, tries to fill bufferOut and updateInterestOps
		 *
		 * @param msg
		 */
		public void queueMessage(Message msg) {
			queueMsg.add(msg);
			processOut(msg.getOpCode());
			updateInterestOps();
		}

		public void queueString(String str) {
			queue.add(str);
			processOut(2);
			updateInterestOps();
		}

		/**
		 * Try to fill bufferOut from the message queue
		 *
		 */
		private void processOut(int opCode) {
			if (bufferOut.remaining() < Integer.BYTES) {
				return;
			}
			if (opCode == 2) {
				var serv = queue.poll();
				if (serv == null) {
					return;
				}
				var encodedServ = StandardCharsets.UTF_8.encode(serv);
				bufferOut.putInt(2).putInt(encodedServ.remaining()).put(encodedServ);
				bufferOut.limit(bufferOut.position());
				return;
			}
			if (opCode == 3) {
				bufferOut.putInt(3);
				bufferOut.limit(bufferOut.position());
				return;
			}

			var message = queueMsg.poll();
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
			if (opCode == 4) {
				bufferOut.limit(BUFFER_SIZE);
				bufferOut.putInt(4).putInt(server.remaining()).put(server);
				bufferOut.putInt(login.remaining()).put(login);
				bufferOut.putInt(text.remaining()).put(text);
				bufferOut.limit(bufferOut.position());
			}
			if (opCode == 5) {
				var serverDst = StandardCharsets.UTF_8.encode(message.getServerDst());
				if (serverDst.remaining() > 100) {
					return;
				}
				var loginDst = StandardCharsets.UTF_8.encode(message.getLoginDst());
				if (loginDst.remaining() > 30) {
					return;
				}
				bufferOut.limit(bufferOut.limit());
				bufferOut.putInt(5).putInt(server.remaining()).put(server);
				bufferOut.putInt(login.remaining()).put(login);
				bufferOut.putInt(serverDst.remaining()).put(serverDst);
				bufferOut.putInt(loginDst.remaining()).put(loginDst);
				bufferOut.putInt(text.remaining()).put(text);
				bufferOut.limit(bufferOut.position());
			}
			if (opCode == 8) {

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
			if (!closed && bufferIn.hasRemaining()) {
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

	private static final int BUFFER_SIZE = 10_000;
	private static final Logger logger = Logger.getLogger(ServerChaton.class.getName());

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final Thread console;
	private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(10);
	private static String nameServer;
	private static final HashMap<String, SelectionKey> listClient = new HashMap<>();
	private static final HashMap<String, SelectionKey> listServer = new HashMap<>();
	private static boolean leader = true;
	private final InetSocketAddress address;

	public ServerChaton(int port) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		this.address = new InetSocketAddress(port);
		serverSocketChannel.bind(address);
		selector = Selector.open();
		listServer.put(nameServer, null);
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

	private void processCommands() throws IOException {
		if (queue.isEmpty()) {
			return;
		}
		var command = queue.poll().toUpperCase();
		switch (command) {
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
		if (command.startsWith("FUSION")) {
			var commandSplit = command.split(" ");
			// check for the leader in listServer
			if (leader) {
				var sc = SocketChannel.open();
				sc.configureBlocking(false);
				var server = sc.register(selector, SelectionKey.OP_CONNECT);
				server.attach(new Context(this, server));
				sc.connect(new InetSocketAddress(commandSplit[1], Integer.parseInt(commandSplit[2])));
				var context = (Context) server.attachment();
				context.queueMessage(new FusionInit(8, nameServer, address, listServer.keySet()));
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
				var dest = msg.getLoginDst();
				var client = listClient.get(dest);
				if (client == null) {
					return;
				}
				var context = (Context) client.attachment();
				context.queueMessage(msg);
			}
		}
	}

	private void sendLogin(SelectionKey key) {
		var context = (Context) key.attachment();
		context.queueString(nameServer);
	}

	public static void main(String[] args) throws NumberFormatException, IOException, InterruptedException {
		if (args.length != 2) {
			usage();
			return;
		}
		nameServer = args[1];
		new ServerChaton(Integer.parseInt(args[0])).launch();
	}

	private static void usage() {
		System.out.println("Usage : ServerChaton port name");
	}
}