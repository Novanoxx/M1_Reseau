package fr.upem.net.tcp.nonblocking.readers.type;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public record FusionInitFWD(int opCode, InetSocketAddress addressLeader) implements Message {

    public void fillBuffer(ByteBuffer buffer) {
        buffer.putInt(opCode);

        //InetSocketAdress
        buffer.put((byte) 4);   //IPv4
        buffer.put(addressLeader.getAddress().getAddress());
        buffer.putInt(addressLeader.getPort());
    }

    @Override
    public int getOpCode() {
        return opCode;
    }

    @Override
    public String getLoginSrc() {
        return null;
    }

    @Override
    public String getLoginDst() {
        return null;
    }

    @Override
    public String getMsg() {
        return null;
    }

    @Override
    public String getServerSrc() {
        return null;
    }

    @Override
    public String getServerDst() {
        return null;
    }
}
