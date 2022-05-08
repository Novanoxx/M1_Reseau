package fr.upem.net.tcp.nonblocking.readers.type;

import java.nio.ByteBuffer;

public record FusionInitKO(int opCode) implements Message {
    @Override
    public void fillBuffer(ByteBuffer buffer) {
        buffer.limit(10_000);
        buffer.putInt(opCode);
        buffer.limit(buffer.position());
    }

    @Override
    public int getOpCode() { return opCode; }

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
