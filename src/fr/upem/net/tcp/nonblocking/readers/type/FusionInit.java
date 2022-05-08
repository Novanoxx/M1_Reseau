package fr.upem.net.tcp.nonblocking.readers.type;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public record FusionInit(int opCode, String nameServer, InetSocketAddress address, Set<String> nameServers) implements Message {

    @Override
    public void fillBuffer(ByteBuffer buffer) {
        buffer.limit(10_000);
        buffer.putInt(opCode);

        //nom du Leader
        var encoded = StandardCharsets.UTF_8.encode(nameServer);
        if (encoded.remaining() > 100) {
            System.out.println("nameServer too big");
            return;
        }
        buffer.putInt(encoded.remaining());
        buffer.put(encoded);

        //InetSocketAdress
        buffer.put((byte) 4);   //IPv4
        buffer.put(address.getAddress().getAddress());
        buffer.putInt(address.getPort());

        //nombre de server que contient le leader
        buffer.putInt(nameServers.size());

        //tous les noms des serveurs
        nameServers.forEach(e -> {
            var encodedServ = StandardCharsets.UTF_8.encode(e);
            buffer.putInt(encodedServ.remaining());
            buffer.put(encodedServ);
        });

        buffer.limit(buffer.position());
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