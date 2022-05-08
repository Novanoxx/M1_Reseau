package fr.upem.net.tcp.nonblocking.readers;

import fr.upem.net.tcp.nonblocking.readers.type.FileMessage;
import fr.upem.net.tcp.nonblocking.readers.visitor.ReaderVisitor;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class FileMessageReader implements Reader<FileMessage>{
    private enum State {
        DONE, WAITING_DATA, WAITING, ERROR
    }
    // que faire avec block??
    private ArrayList<String> lst = new ArrayList<>();
    private ByteBuffer block;
    private FileMessage file;
    private State state = State.WAITING_DATA;
    private final StringReader stringReader = new StringReader();

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.WAITING_DATA) {
            for (int i = 0; i < 7; i++) {
                var readerState = stringReader.process(bb);
                if (readerState == ProcessStatus.DONE) {
                    lst.add(stringReader.get());
                    stringReader.reset();
                } else {
                    return readerState;
                }
            }
            file = new FileMessage(lst.get(0), lst.get(1), lst.get(2), lst.get(3), lst.get(4), Integer.parseInt(lst.get(5)), Integer.parseInt(lst.get(6)));
        } else {
            block = ByteBuffer.allocate(file.blockSize());
            for (int i = 0; i < file.blockSize(); i++) {
                block.put(bb.get());
            }
        }

        state = State.DONE;
        return ProcessStatus.DONE;
    }

    @Override
    public FileMessage get() {
        if (state == State.DONE) {
            return file;
        }
        throw new IllegalStateException();
    }

    @Override
    public void reset() {
        state = State.WAITING;
        block.clear();
        stringReader.reset();
    }

    @Override
    public int accept(ReaderVisitor v, ByteBuffer bufferIn) {
        // not implemented
        return 0;
    }
}
