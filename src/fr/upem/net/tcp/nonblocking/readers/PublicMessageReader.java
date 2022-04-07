package fr.upem.net.tcp.nonblocking.readers;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class PublicMessageReader implements Reader<PublicMessage>{
    private enum State {
        DONE, WAITING, ERROR
    }

    private ArrayList<String> lst = new ArrayList<>();
    private PublicMessage msg;
    private State state = State.WAITING;
    private final StringReader reader = new StringReader();

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        for (int i = 0; i < 3; i++) {
            var readerState = reader.process(bb);
            if (readerState == ProcessStatus.DONE) {
                lst.add(reader.get());
                reader.reset();
            } else {
                return readerState;
            }
        }
        msg = new PublicMessage(4, lst.get(0), lst.get(1), lst.get(2));
        state = State.DONE;
        return ProcessStatus.DONE;
    }

    @Override
    public PublicMessage get() {
        if (state == State.DONE) {
            return msg;
        }
        throw new IllegalStateException();
    }

    @Override
    public void reset() {
        state = State.WAITING;
        lst.clear();
        reader.reset();
    }
}
