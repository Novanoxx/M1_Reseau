package fr.upem.net.tcp.nonblocking;

import fr.upem.net.tcp.nonblocking.readers.*;
import fr.upem.net.tcp.nonblocking.readers.type.LoginAnonym;
import fr.upem.net.tcp.nonblocking.readers.type.Message;
import fr.upem.net.tcp.nonblocking.readers.type.PrivateMessage;
import fr.upem.net.tcp.nonblocking.readers.type.PublicMessage;
import fr.upem.net.tcp.nonblocking.readers.visitor.PrivateVisitor;
import fr.upem.net.tcp.nonblocking.readers.visitor.PublicVisitor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Scanner;
import java.util.logging.Logger;

public class ClientChat {
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    static private class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final ArrayDeque<Message> queueMessage = new ArrayDeque<>();
        private boolean closed = false;
        private final String login;
        private String nameServer = null;
        private final PrivateMessageReader privateReader = new PrivateMessageReader();
        private final PublicMessageReader publicReader = new PublicMessageReader();
        private final StringReader stringReader = new StringReader();

        private Context(SelectionKey key, String login) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.login = login;
        }

        /**
         * Process the content of bufferIn
         *
         * The convention is that bufferIn is in write-mode before the call to process
         * and after the call
         *
         */
        private void processIn() {
            bufferIn.flip();
            int opCode = bufferIn.getInt();
            bufferIn.compact();
            while(true) {
                Reader.ProcessStatus status;
                switch (opCode) {
                    case 2 :
                        status = stringReader.process(bufferIn);
                        switch (status) {
                            case DONE:
                                nameServer = stringReader.get();
                                System.out.println("Welcome " + login + " in " + nameServer);
                                logged = true;
                                break;
                            case REFILL:
                                break;
                            case ERROR:
                                silentlyClose();
                                return;
                        }
                        stringReader.reset();
                        break;

                    case 3 :
                        System.out.println("Login failed");
                        return;

                    case 4 :
                        PublicVisitor publicVisitor = new PublicVisitor();
                        if(publicReader.accept(publicVisitor, bufferIn) == 0)
                            silentlyClose();

                    case 5 :
                        PrivateVisitor privateVisitor = new PrivateVisitor();
                        if(privateReader.accept(privateVisitor, bufferIn) == 0)
                            silentlyClose();
                    default:
                        return;
                }
            }
        }

        /**
         * Add a message to the message queue, tries to fill bufferOut and updateInterestOps
         *
         */
        private void queueMessage(Message msg) {
            queueMessage.add(msg);
            processOut();
            updateInterestOps();
        }

        /**
         * Try to fill bufferOut from the message queue
         *
         */
        private void processOut() {
            if (bufferOut.position() != 0) {
                return;
            }
            if(!bufferOut.hasRemaining()) {
                return;
            }
            var message = queueMessage.poll();
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
            int ops = 0;
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

        public void doConnect() throws IOException {
            if (!sc.finishConnect()) {
                return; // selector lied
            }
            key.interestOps(SelectionKey.OP_READ);
            queueMessage(new LoginAnonym(0, login));
        }
    }

    static private int BUFFER_SIZE = 10_000;
    static private Logger logger = Logger.getLogger(ClientChat.class.getName());

    private final SocketChannel sc;
    private final Selector selector;
    private final InetSocketAddress serverAddress;
    private String login;
    private final Thread console;
    private Context uniqueContext;
    private final ArrayDeque<Message> queueMessage = new ArrayDeque<>();

    private static boolean logged = false;
    private static final Object lock = new Object();

    public ClientChat(InetSocketAddress serverAddress, Path path, String login) throws IOException {
        this.serverAddress = serverAddress;
        this.login = login;
        this.sc = SocketChannel.open();
        this.selector = Selector.open();
        this.console = new Thread(this::consoleRun);
    }

    private void consoleRun() {
        try {
            try (var scanner = new Scanner(System.in)) {
                while (scanner.hasNextLine()) {
                    var msg = scanner.nextLine();
                    sendCommand(msg);
                }
            }
            logger.info("Console thread stopping");
        } catch (InterruptedException e) {
            logger.info("Console thread has been interrupted");
        }
    }

    /**
     * Send instructions to the selector via a BlockingQueue and wake it up
     *
     * @param msg
     * @throws InterruptedException
     */

    private void sendCommand(String msg) throws InterruptedException {
        synchronized (lock) {
            if (logged) {
                String[] messagePrive;
                if (msg.startsWith("@")) {
                    messagePrive = msg.split("@", 2);
                    String[] login0ServerMessage1 = messagePrive[1].split(":", 2);
                    String[] server0Message1 = login0ServerMessage1[1].split(" ", 2);
                    queueMessage.add(new PrivateMessage(5, uniqueContext.nameServer, login, server0Message1[0], login0ServerMessage1[0], server0Message1[1]));
                }else {
                    queueMessage.add(new PublicMessage(4, uniqueContext.nameServer, login, msg));
                }
                selector.wakeup();
            } else {
                queueMessage.add(new LoginAnonym(0, msg));
                selector.wakeup();
            }

        }
    }

    /**
     * Processes the command from the BlockingQueue
     */

    private void processCommands() {
        synchronized (lock) {
            var msg = queueMessage.poll();
            while (msg != null) {
                uniqueContext.queueMessage(msg);
                msg = queueMessage.poll();
            }
        }
    }

    public void launch() throws IOException {
        sc.configureBlocking(false);
        var key = sc.register(selector, SelectionKey.OP_CONNECT);
        uniqueContext = new Context(key, login);
        key.attach(uniqueContext);
        sc.connect(serverAddress);

        console.start();
        while (!Thread.interrupted()) {
            try {
                selector.select(this::treatKey);
                processCommands();
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
        }
        console.interrupt();
    }

    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isConnectable()) {
                uniqueContext.doConnect();
            }
            if (key.isValid() && key.isWritable()) {
                uniqueContext.doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                uniqueContext.doRead();
            }
        } catch (IOException ioe) {
            // lambda call in select requires to tunnel IOException
            throw new UncheckedIOException(ioe);
        }
    }


    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 4) {
            usage();
            return;
        }
        new ClientChat(new InetSocketAddress(args[0], Integer.parseInt(args[1])), Path.of(args[2]), args[3]).launch();
    }

    private static void usage() {
        System.out.println("Usage : ClientChat hostname port pathfile login");
    }
}