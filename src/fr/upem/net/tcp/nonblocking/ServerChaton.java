package fr.upem.net.tcp.nonblocking;

import fr.upem.net.tcp.nonblocking.readers.*;
import fr.upem.net.tcp.nonblocking.readers.type.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayDeque;
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
		private final ServerChaton server;

		private boolean closed = false;
		private final PrivateMessageReader privateReader = new PrivateMessageReader();
		private final PublicMessageReader publicReader = new PublicMessageReader();
		private final FusionInitReader fusionInitReader = new FusionInitReader();
		private final FusionInitOKReader fusionInitOKReader = new FusionInitOKReader();
		private final FusionInitFWDReader fusionInitFWDReader = new FusionInitFWDReader();
		private final StringReader stringReader = new StringReader();

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
		private void processIn() throws IOException {
			Reader.ProcessStatus status;
			bufferIn.flip();
			var tmp = bufferIn.getInt();
			bufferIn.compact();
			while(true) {
				switch (tmp) {
					case 0 :
						if (processInConnection() == 0) {
							return;
						}

					case 4 :
						if (processInPublicMessage() == 0) {
							silentlyClose();
						}

					case 5 :
						if (processInPrivateMessage() == 0) {
							silentlyClose();
						}

					case 8 :
						if (processInFusionInit() == 0) {
							silentlyClose();
						}

					case 9 :
						if (processInFusionInitOK() == 0) {
							silentlyClose();
						}

					/*
					case 11 :
						status = fusionInitFWDReader.process(bufferIn);
						switch(status) {
							case DONE :
								var fusionPack = fusionInitFWDReader.get();

							case REFILL :
								return;
							case ERROR :
								silentlyClose();
								return;
						}
					break;
					*/
					default :
						return;
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
			processOut();
			updateInterestOps();
		}

		private int processInConnection() {
			Reader.ProcessStatus status;
			status = stringReader.process(bufferIn);
			switch (status) {
				case DONE:
					var checkLogin = stringReader.get();
					stringReader.reset();
					if (listClient.containsKey(checkLogin)) {
						System.out.println("Login already used");
						queueMessage(new LoginRefused(3));
					} else {
						listClient.put(checkLogin, key);
						var context = (Context) key.attachment();
						context.queueMessage(new LoginAccepted(2, nameServer));
					}
					break;
				case REFILL:
					break;
				case ERROR:
					return 0;
			}
			return 1;
		}

		private int processInPublicMessage() throws IOException {
			Reader.ProcessStatus status;
			status = publicReader.process(bufferIn);
			switch (status) {
				case DONE:
					var value = publicReader.get();
					server.broadcast(value, 4);
					publicReader.reset();
					break;
				case REFILL:
					break;
				case ERROR:
					return 0;
			}
			return 1;
		}

		private int processInPrivateMessage() throws IOException {
			Reader.ProcessStatus status;
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
					break;
				case ERROR:
					return 0;
			}
			return 1;
		}

		private int processInFusionInit() throws IOException {
			Reader.ProcessStatus status;
			status = fusionInitReader.process(bufferIn);
			switch (status) {
				case DONE :
					var fusionPack = fusionInitReader.get();
					if (leader) {
						boolean flag = false;
						for (var nameServer : fusionPack.nameServers()) {
							if (listServer.containsKey(nameServer)) {
								flag = true;
								break;
							}
						}
						if (flag) { // si il y a un server en commun dans le mega server
							server.broadcast(new FusionInitKO(10), 10);
						} else {
							server.broadcast(new FusionInitOK(9, nameServer, server.address, listServer.size(), listServer.keySet()), 9);
						}

						if (fusionPack.nameServer().compareTo(nameServer) < 0) {
							leader = false;
						}

						listServer.put(fusionPack.nameServer(), key);
						for (var tmpServer : fusionPack.nameServers()) {
							listServer.put(tmpServer, null);
						}
					} else {
						/*
						// Si ce n'est pas le leader de son mega server
						listServer.values().forEach(e -> {
							var context = (Context) e.attachment();
							if (context.server.isLeader()) {
								try {
									server.broadcast(new FusionInitFWD(11, context.server.address), 11);
								} catch (IOException ioe) {
									// Do nothing?
								}
							}
						});
						*/
					}
					fusionInitReader.reset();
					break;
				case REFILL:
					break;
				case ERROR:
					return 0;
			}
			return 1;
		}

		private int processInFusionInitOK() throws IOException {
			Reader.ProcessStatus status;
			status = fusionInitOKReader.process(bufferIn);
			switch (status) {
				case DONE :
					var fusionPack = fusionInitOKReader.get();
					boolean flag = false;
					for (var tmpServer : fusionPack.nameServers()) {
						if (listServer.containsKey(tmpServer)) {
							flag = true;
							break;
						}
					}
					if (flag) {
						server.broadcast(new FusionInitKO(10), 10);
					} else {
						if (fusionPack.nameAddress().compareTo(nameServer) < 0) {
							leader = false;
						}
						listServer.put(fusionPack.nameAddress(), key);
						for (var tmpServer: fusionPack.nameServers()) {
							listServer.computeIfAbsent(tmpServer, k->null);	// rentre les noms des autre seveurs sans leur adresse
						}
						logger.info("FUSION DONE");
					}
					fusionInitOKReader.reset();
					break;
				case REFILL :
					break;
				case ERROR :
					return 0;
			}
			return 1;
		}

		/**
		 * Try to fill bufferOut from the message queue
		 *
		 */
		private void processOut() {
			if (bufferOut.remaining() < Integer.BYTES) {
				return;
			}
			var message = queueMsg.poll();
			if (message == null) {
				return;
			}
			message.fillBuffer(bufferOut);
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
			try {
				if (!sc.finishConnect()) {
					return;
				}
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
			} catch (IOException ioe) {
				// do nothing
			}

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

	public boolean isLeader() {
		return leader;
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
			} else {
				//TODO
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
	private void broadcast(Message msg, int opCode) throws IOException {
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
				break;
			}

			case 10 : {
				// go to case 9
			}

			case 9: {
				var sc = SocketChannel.open();
				sc.configureBlocking(false);
				var server = sc.register(selector, SelectionKey.OP_CONNECT);
				server.attach(new Context(this, server));
				var port = (FusionInitOK) msg;
				sc.connect(new InetSocketAddress(port.address().getAddress(), port.address().getPort()));	// socketAddress + port
				var context = (Context) server.attachment();
				context.queueMessage(msg);
				break;
			}

			case 11: {
				var sc = SocketChannel.open();
				sc.configureBlocking(false);
				var server = sc.register(selector, SelectionKey.OP_CONNECT);
				var port = (FusionInitFWD) msg;
				sc.connect(new InetSocketAddress(port.addressLeader().getAddress(), port.addressLeader().getPort()));	// socketAddress + port
				var context = (Context) server.attachment();
				context.queueMessage(msg);
				break;
			}

			default: break;
		}
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