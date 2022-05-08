package fr.upem.net.tcp.nonblocking.readers;

import fr.upem.net.tcp.nonblocking.readers.type.FusionInit;
import fr.upem.net.tcp.nonblocking.readers.visitor.ReaderVisitor;

import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;

public class FusionInitReader implements Reader<FusionInit> {
    private enum State {
        DONE, WAITING, ERROR
    }

    private State state = State.WAITING;
    private  FusionInit fusionInitPack;
    private final StringReader stringReader = new StringReader();
    private final IntReader intReader = new IntReader();

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        var status = stringReader.process(bb);
        if (status != ProcessStatus.DONE) {
            return status;
        }
        var serverName = stringReader.get();
        stringReader.reset();

        bb.flip();
        if (!bb.hasRemaining()) {
            return ProcessStatus.REFILL;
        }
        // IPv4 or IPv6
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

        // Retrieve port and nbServer
        ArrayList<Integer> lstElem = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            status = intReader.process(bb);
            if (status != ProcessStatus.DONE) {
                return status;
            }
            lstElem.add(intReader.get());
            intReader.reset();
        }

        var set = new HashSet<String>();
        for(var i = 0 ; i < lstElem.get(1) - 1 ; i++){
            status = stringReader.process(bb);
            if (status == ProcessStatus.DONE) {
                set.add(stringReader.get());
                stringReader.reset();
            }
            else {
                return status;
            }
        }
        try {
            fusionInitPack = new FusionInit(8, serverName, new InetSocketAddress(Inet4Address.getByAddress(tmp), lstElem.get(0)), set);
        } catch (UnknownHostException e) {
            // do nothing?
        }
        state = State.DONE;
        return status;
    }

    @Override
    public FusionInit get() {
        if (state != State.DONE)
            throw new IllegalStateException();
        return fusionInitPack;
    }

    @Override
    public void reset() {
        stringReader.reset();
        intReader.reset();
        state = State.WAITING;
        fusionInitPack = null;
    }

    @Override
    public int accept(ReaderVisitor v, ByteBuffer bufferIn) {
        // not implemented
        return 0;
    }
}