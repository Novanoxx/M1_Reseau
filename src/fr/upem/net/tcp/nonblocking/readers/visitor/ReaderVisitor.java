package fr.upem.net.tcp.nonblocking.readers.visitor;

import fr.upem.net.tcp.nonblocking.readers.Reader;
import fr.upem.net.tcp.nonblocking.readers.type.Message;

import java.nio.ByteBuffer;

public interface ReaderVisitor {
    int visit(Reader<Message> msgReader, ByteBuffer bufferIn);
}
