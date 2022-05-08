package fr.upem.net.tcp.nonblocking.readers.type;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public record FusionInitOK(int opCode, String nameAddress, InetSocketAddress address, int nbMember, Set<String> nameServers) implements Message {

    public void fillBuffer(ByteBuffer buffer) {
        buffer.limit(10_000);
        buffer.putInt(opCode);

        //nom du Leader
        var encoded = StandardCharsets.UTF_8.encode(nameAddress);
        if (encoded.remaining() > 100) {
            System.out.println("nameAddress too big");
            return;
        }
        buffer.putInt(encoded.remaining());
        buffer.put(encoded);

        //InetSocketAdress
        buffer.put((byte) 4);   //IPv4
        buffer.put(address.getAddress().getAddress());
        buffer.putInt(address.getPort());

        //nombre de client membre que contient le leader
        buffer.putInt(nbMember);

        //tous les noms des clients
        nameServers.forEach(e -> {
            var encodedClient = StandardCharsets.UTF_8.encode(e);
            buffer.putInt(encodedClient.remaining());
            buffer.put(encodedClient);
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
        return nameAddress;
    }

    @Override
    public String getServerDst() {
        return null;
    }
}
