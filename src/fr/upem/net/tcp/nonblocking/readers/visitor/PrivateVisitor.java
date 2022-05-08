package fr.upem.net.tcp.nonblocking.readers.visitor;

import fr.upem.net.tcp.nonblocking.readers.Reader;
import fr.upem.net.tcp.nonblocking.readers.type.Message;

import java.nio.ByteBuffer;

public class PrivateVisitor implements ReaderVisitor {
    @Override
    public int visit(Reader<Message> msgReader, ByteBuffer bufferIn) {
        Reader.ProcessStatus status = msgReader.process(bufferIn);
        switch (status) {
            case DONE:
                System.out.println("Private: " + msgReader.get().getLoginSrc() + " from " + msgReader.get().getServerSrc() + " : " + msgReader.get().getMsg());
                msgReader.reset();
                break;
            case REFILL:
                break;
            case ERROR:
                return 0;

        }
        return 1;
    }
}
