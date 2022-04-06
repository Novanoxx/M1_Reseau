package fr.upem.net.tcp.nonblocking.readers;

import java.nio.ByteBuffer;

public class LoginReader implements Reader<Login>{
    private enum State {
        DONE, WAITING, ERROR
    }

    private Login login;
    private State state = State.WAITING;
    private final StringReader reader = new StringReader();

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        var readerState = reader.process(bb);
        if (readerState == ProcessStatus.DONE) {
            login = new Login(0, reader.get());
        } else {
            return readerState;
        }
        state = State.DONE;
        return ProcessStatus.DONE;
    }

    @Override
    public Login get() {
        if (state == State.DONE) {
            return login;
        }
        throw new IllegalStateException();
    }

    @Override
    public void reset() {
        state = State.WAITING;
        reader.reset();
    }
}
