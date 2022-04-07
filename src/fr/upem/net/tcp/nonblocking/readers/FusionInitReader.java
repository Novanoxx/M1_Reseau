package fr.upem.net.tcp.nonblocking.readers;

import java.net.*;
import java.nio.ByteBuffer;
import java.util.HashSet;

public class FusionInitReader implements Reader<FusionInit> {
    private enum State {
        DONE, WAITING, ERROR
    }

    private State state = State.WAITING;
    private  FusionInit privateMsg;
    private final StringReader reader = new StringReader();
    private final IntReader intReader = new IntReader();

    @Override
    public Reader.ProcessStatus process(ByteBuffer bb) {
        var status = reader.process(bb);
        if (status != Reader.ProcessStatus.DONE) {
            return status;
        }
        var serverName = reader.get();
        reader.reset();


        bb.flip();
        if (!bb.hasRemaining()) {
            return ProcessStatus.REFILL;
        }


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

        status = intReader.process(bb);
        if (status != ProcessStatus.DONE) {
            return status;
        }
        var port = intReader.get();
        intReader.reset();

        status = intReader.process(bb);
        if (status != ProcessStatus.DONE) {
            return status;
        }
        var nbServer = intReader.get();
        intReader.reset();

        var set = new HashSet<String>();
        for(var i = 0 ; i < nbServer - 1 ; i++){
            status = reader.process(bb);
            if (status == ProcessStatus.DONE) {
                set.add(reader.get());
                reader.reset();
            }
            else {
                return status;
            }
        }
        if (status == ProcessStatus.DONE) {
            try {
                if (nb == 4) {
                    privateMsg = new FusionInit(8, serverName, new InetSocketAddress(Inet4Address.getByAddress(tmp), port), set);
                } else {
                    privateMsg = new FusionInit(8, serverName, new InetSocketAddress(Inet6Address.getByAddress(tmp), port), set);
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
            state = State.DONE;
        }
        return status;
    }

    @Override
    public FusionInit get() {
        if (state != State.DONE)
            throw new IllegalStateException();
        return privateMsg;
    }

    @Override
    public void reset() {
        reader.reset();
        intReader.reset();
        state = State.WAITING;
        privateMsg = null;
    }
}