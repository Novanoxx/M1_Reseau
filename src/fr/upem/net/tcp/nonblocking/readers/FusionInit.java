package fr.upem.net.tcp.nonblocking.readers;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public record FusionInit(int opCode, String nameServer, InetSocketAddress address, Set<String> nameServers) implements Message {

    public void fillBuffer(ByteBuffer buffer) {
        var tmpBuffer = ByteBuffer.allocate(10_000);


        buffer.putInt(opCode); //op code

        //nom du Leader
        tmpBuffer.put(StandardCharsets.UTF_8.encode(nameServer));
        tmpBuffer.flip();
        buffer.putInt(tmpBuffer.remaining());
        buffer.put(tmpBuffer);
        tmpBuffer.clear();

        //InetSocketAdress
        buffer.put((byte) 4);   //IPv4
        buffer.put(address.getAddress().getAddress());
        buffer.putInt(address.getPort());

        //nombre de server que contient le leader
        buffer.putInt(nameServers.size());

        //tous les noms des serveurs
        nameServers.forEach(e -> {
            tmpBuffer.put(StandardCharsets.UTF_8.encode(e));
            tmpBuffer.flip();
            buffer.putInt(tmpBuffer.remaining());
            buffer.put(tmpBuffer);
            tmpBuffer.clear();
        });
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
        return nameServer;
    }

    @Override
    public String getServerDst() {
        return null;
    }
}