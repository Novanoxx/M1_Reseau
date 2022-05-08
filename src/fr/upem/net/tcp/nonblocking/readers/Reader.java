package fr.upem.net.tcp.nonblocking.readers;

import fr.upem.net.tcp.nonblocking.readers.visitor.ReaderVisitor;

import java.nio.ByteBuffer;

public interface Reader<T> {

    public static enum ProcessStatus { DONE, REFILL, ERROR };

    public ProcessStatus process(ByteBuffer bb);

    public T get();

    public void reset();

    public int accept(ReaderVisitor v, ByteBuffer bufferIn);

}
