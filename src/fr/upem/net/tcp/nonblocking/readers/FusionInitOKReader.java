package fr.upem.net.tcp.nonblocking.readers;

import fr.upem.net.tcp.nonblocking.readers.type.FusionInitOK;
import fr.upem.net.tcp.nonblocking.readers.visitor.ReaderVisitor;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;

public class FusionInitOKReader implements Reader<FusionInitOK>{

    private enum State {
        DONE, WAITING, ERROR
    }

    private State state = State.WAITING;
    private FusionInitOK fusionInitOK;
    private final StringReader stringReader = new StringReader();
    private final IntReader intReader = new IntReader();

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        var status = stringReader.process(bb);
        if (status != Reader.ProcessStatus.DONE) {
            return status;
        }
        var serverName = stringReader.get();

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
            fusionInitOK = new FusionInitOK(9, serverName, new InetSocketAddress(Inet4Address.getByAddress(tmp), lstElem.get(0)), lstElem.get(1), set);
        } catch (UnknownHostException e) {
            // do nothing?
        }
        state = State.DONE;
        return status;
    }

    @Override
    public FusionInitOK get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return fusionInitOK;
    }

    @Override
    public void reset() {
        state = State.WAITING;
        fusionInitOK = null;
        stringReader.reset();
        intReader.reset();
    }

    @Override
    public int accept(ReaderVisitor v, ByteBuffer bufferIn) {
        // not implemented
        return 0;
    }
}
