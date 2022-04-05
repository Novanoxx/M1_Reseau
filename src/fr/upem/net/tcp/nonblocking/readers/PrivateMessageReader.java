package fr.upem.net.tcp.nonblocking.readers;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class PrivateMessageReader implements Reader<PrivateMessage>{
    private enum State {
        DONE, WAITING, ERROR
    }

    private ArrayList<String> lst = new ArrayList<>();
    private PrivateMessage msg;
    private State state = State.WAITING;
    private final StringReader reader = new StringReader();

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        for (int i = 0; i < 6; i++) {
            var readerState = reader.process(bb);
            if (readerState == ProcessStatus.DONE) {
                lst.add(reader.get());
                reader.reset();
            } else {
                return readerState;
            }
        }
        msg = new PrivateMessage(5, lst.get(0), lst.get(1), lst.get(2), lst.get(3), lst.get(4));
        state = State.DONE;
        return ProcessStatus.DONE;
    }

    @Override
    public PrivateMessage get() {
        if (state == State.DONE) {
            return msg;
        }
        throw new IllegalStateException();
    }

    @Override
    public void reset() {
        state = State.WAITING;
        reader.reset();
    }
}
