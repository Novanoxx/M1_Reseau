package fr.upem.net.tcp.nonblocking.readers.type;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record PublicMessage(int opCode, String server, String login, String msg) implements Message {

    public void fillBuffer(ByteBuffer buffer) {
        buffer.limit(10_000);
        var encodedServ = StandardCharsets.UTF_8.encode(server);
        if (encodedServ.remaining() > 100) {
            System.out.println("PublicMessage: server name too big");
            return;
        }
        buffer.putInt(4).putInt(encodedServ.remaining()).put(encodedServ);

        var encodedLogin = StandardCharsets.UTF_8.encode(login);
        if (encodedLogin.remaining() > 30) {
            System.out.println("PublicMessage: login too big");
            return;
        }
        buffer.putInt(encodedLogin.remaining()).put(encodedLogin);

        var encodedText = StandardCharsets.UTF_8.encode(msg);
        if (encodedText.remaining() > 1024) {
            System.out.println("PublicMessage: text too big");
            return;
        }
        buffer.putInt(encodedText.remaining()).put(encodedText);
        buffer.limit(buffer.position());
    }

    @Override
    public int getOpCode() {
        return opCode;
    }

    @Override
    public String getLoginSrc() {
        return login;
    }

    @Override
    public String getLoginDst() {
        return null;
    }

    @Override
    public String getMsg() {
        return msg;
    }

    @Override
    public String getServerSrc() {
        return server;
    }

    @Override
    public String getServerDst() {
        return null;
    }
}
