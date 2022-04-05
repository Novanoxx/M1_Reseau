package fr.upem.net.tcp.nonblocking;

import fr.upem.net.tcp.nonblocking.readers.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Scanner;
import java.util.logging.Logger;

public class ClientChat {
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    /*
    static enum OpCodes {
        ANONYMOUS_LOGIN, PUBLIC_MESSAGE, PRIVATE_MESSAGE
    }
     */

    static private class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final ArrayDeque<Login> queueLogin = new ArrayDeque<>();
        private final ArrayDeque<Message> queueMessage = new ArrayDeque<>();
        private boolean closed = false;
        private String login = null;
        private final PrivateMessageReader privateReader = new PrivateMessageReader();
        private final PublicMessageReader publicReader = new PublicMessageReader();

        private Context(SelectionKey key) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
        }

        /**
         * Process the content of bufferIn
         *
         * The convention is that bufferIn is in write-mode before the call to process
         * and after the call
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
                                System.out.println(publicReader.get().login() + " : " + publicReader.get().msg());
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
                                System.out.println(privateReader.get().loginSrc() + "from " + privateReader.get().serverSrc() + " : " + privateReader.get().msg());
                                privateReader.reset();
                                break;
                            case REFILL:
                                return;
                            case ERROR:
                                silentlyClose();
                                return;
                        }
                    }

                    default:
                        System.out.println("Unknown pack");
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

        private void queueLogin(Login login) {
            queueLogin.add(login);
            processOut();
            updateInterestOps();
        }

        /**
         * Try to fill bufferOut from the message queue
         *
         */
        private void processOut() {
            if (login == null) {
                var log = queueLogin.poll();
                Objects.requireNonNull(log);
                var login = UTF8.encode(log.login());
                bufferOut.putInt(log.opCode()).putInt(login.remaining()).put(login);
                return;
            }
            if (bufferOut.remaining() < Integer.BYTES) {
                return;
            }
            var message = queueMessage.poll();
            if (message == null) {
                return;
            }

            var server = UTF8.encode(message.getServerSrc());
            if (server.remaining() > 100) {
                return;
            }
            var login = UTF8.encode(message.getLoginSrc());
            if (login.remaining() > 30) {
                return;
            }
            var text = UTF8.encode(message.getMsg());
            if (text.remaining() > 1024) {
                return;
            }

            if (message.getOpCode() == 4) {
                bufferOut.putInt(4).putInt(server.remaining()).put(server);
                bufferOut.putInt(login.remaining()).put(login);
                bufferOut.putInt(text.remaining()).put(text);
            }
            if (message.getOpCode() == 5) {
                var serverDst = UTF8.encode(message.getServerDst());
                if (serverDst.remaining() > 100) {
                    return;
                }
                var loginDst = UTF8.encode(message.getLoginDst());
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

        public void doConnect() throws IOException {
            if (!sc.finishConnect()) {
                return; // selector lied
            }
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    static private int BUFFER_SIZE = 10_000;
    static private Logger logger = Logger.getLogger(ClientChat.class.getName());

    private final SocketChannel sc;
    private final Selector selector;
    private final InetSocketAddress serverAddress;
    private String login;
    private Path path;
    private final Thread console;
    private Context uniqueContext;
    private final ArrayDeque<Login> queueLogin = new ArrayDeque<>();
    private final ArrayDeque<Message> queueMessage = new ArrayDeque<>();
    private boolean connected = false;

    private final Object lock = new Object();

    public ClientChat(InetSocketAddress serverAddress, Path path, String login) throws IOException {
        this.serverAddress = serverAddress;
        this.login = login;
        this.path = path;
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
            if (!connected) {
                if (StandardCharsets.UTF_8.encode(msg).remaining() > 30) {
                    System.out.println("Login too long");
                    return;
                }
                queueLogin.add(new Login(0, msg));
            } else {
                if (msg.startsWith("/+@")) {
                    StringBuilder tmp = new StringBuilder();
                    int dot = 0;
                    int at = 0;
                    int space = 0;
                    for (int i = 0; i < msg.length(); i++) {
                        if (msg.charAt(i) == '@') {
                            at = 1;
                        } else if (msg.charAt(i) == ':') {
                            dot = 1;
                        } else if (msg.charAt(i) == ' ') {
                            space = 1;
                        }
                        if (dot == 1 && at == 1 && space == 1) {
                            var data = tmp.toString().split(" ");
                            var newMsg = msg.split(" ", 2);
                            queueMessage.add(new PrivateMessage(5, serverAddress.toString(), login, data[0], data[1], newMsg[1]));
                        } else {
                            tmp.append(msg.charAt(i));
                        }
                    }
                } else {
                    queueMessage.add(new PublicMessage(4, serverAddress.toString(), login, msg));
                }
            }
            selector.wakeup();
        }
    }

    /**
     * Processes the command from the BlockingQueue 
     */

    private void processCommands() {
        synchronized (lock) {
            if (!connected) {
                var pack = queueLogin.poll();
                while (pack != null) {
                    uniqueContext.queueLogin(pack);
                    pack = queueLogin.poll();
                }
            } else {
                var msg = queueMessage.poll();
                while (msg != null) {
                    uniqueContext.queueMessage(msg);
                    msg = queueMessage.poll();
                }
            }
        }
    }

    public void launch() throws IOException {
        sc.configureBlocking(false);
        var key = sc.register(selector, SelectionKey.OP_CONNECT);
        uniqueContext = new Context(key);
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

    private void silentlyClose(SelectionKey key) {
        Channel sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 3) {
            usage();
            return;
        }
        new ClientChat(new InetSocketAddress(args[0], Integer.parseInt(args[1])), Path.of(args[2]), String.valueOf(args[3])).launch();
    }

    private static void usage() {
        System.out.println("Usage : ClientChat login hostname port");
    }
}