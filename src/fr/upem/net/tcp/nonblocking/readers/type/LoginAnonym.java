package fr.upem.net.tcp.nonblocking.readers.type;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record LoginAnonym(int opCode, String login) implements Message {

    @Override
    public void fillBuffer(ByteBuffer buffer) {
        buffer.limit(10_000);
        buffer.putInt(opCode);
        var encodedLogin = StandardCharsets.UTF_8.encode(login);
        if (encodedLogin.remaining() > 30) {
            System.out.println("LoginAnonym: Login too big");
            return;
        }
        buffer.putInt(encodedLogin.remaining()).put(encodedLogin);
        buffer.limit(buffer.position());
    }

    @Override
    public int getOpCode() { return opCode; }

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
