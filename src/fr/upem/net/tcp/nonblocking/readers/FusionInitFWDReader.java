package fr.upem.net.tcp.nonblocking.readers;

import fr.upem.net.tcp.nonblocking.readers.type.FusionInitFWD;
import fr.upem.net.tcp.nonblocking.readers.visitor.ReaderVisitor;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class FusionInitFWDReader implements Reader<FusionInitFWD>{

    private enum State {
        DONE, WAITING, ERROR
    }

    private State state = State.WAITING;
    private  FusionInitFWD fusionInitFWD;
    private final IntReader intReader = new IntReader();

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        int nb;
        if(bb.get() == (byte) 4) {
            nb = 4;
        } else {
            nb = 16;
        }
        byte[] tmp = new byte[nb];
        for (var i = 0; i < nb; i++) {
            if (!bb.hasRemaining()) {
                return ProcessStatus.REFILL;
            }
            tmp[i] = bb.get();
        }
        bb.compact();

        var status = intReader.process(bb);
        if (status != ProcessStatus.DONE) {
            return status;
        }
        var port = intReader.get();

        try {
            if (nb == 4) {
                fusionInitFWD = new FusionInitFWD(11, new InetSocketAddress(Inet4Address.getByAddress(tmp), port));
            } else {
                fusionInitFWD = new FusionInitFWD(11, new InetSocketAddress(Inet6Address.getByAddress(tmp), port));
            }
        } catch (UnknownHostException e) {
            // do nothing?
        }
        state = State.DONE;
        return status;
    }

    @Override
    public FusionInitFWD get() {
        if (state != State.DONE)
            throw new IllegalStateException();
        return fusionInitFWD;
    }

    @Override
    public void reset() {
        intReader.reset();
        fusionInitFWD = null;
        state = State.WAITING;
    }

    @Override
    public int accept(ReaderVisitor v, ByteBuffer bufferIn) {
        // not implemented
        return 0;
    }
}
