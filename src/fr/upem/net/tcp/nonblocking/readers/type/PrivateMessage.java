package fr.upem.net.tcp.nonblocking.readers.type;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record PrivateMessage(int opCode, String serverSrc, String loginSrc, String serverDst, String loginDst, String msg) implements Message {

    public void fillBuffer(ByteBuffer buffer) {
        buffer.limit(buffer.limit());

        var encodedServ = StandardCharsets.UTF_8.encode(serverSrc);
        if (encodedServ.remaining() > 100) {
            System.out.println("PrivateMessage: server src name too big");
            return;
        }
        buffer.putInt(opCode).putInt(encodedServ.remaining()).put(encodedServ);

        var encodedLogin = StandardCharsets.UTF_8.encode(loginSrc);
        if (encodedLogin.remaining() > 30) {
            System.out.println("PrivateMessage: login src too big");
            return;
        }
        buffer.putInt(encodedLogin.remaining()).put(encodedLogin);

        encodedServ = StandardCharsets.UTF_8.encode(serverDst);
        if (encodedServ.remaining() > 100) {
            System.out.println("PrivateMessage: server dst name too big");
            return;
        }
        buffer.putInt(encodedServ.remaining()).put(encodedServ);

        encodedLogin = StandardCharsets.UTF_8.encode(loginDst);
        if (encodedLogin.remaining() > 30) {
            System.out.println("PrivateMessage: login dst too big");
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
        return loginSrc;
    }

    @Override
    public String getLoginDst() {
        return loginDst;
    }

    @Override
    public String getMsg() {
        return msg;
    }

    @Override
    public String getServerSrc() {
        return serverSrc;
    }

    @Override
    public String getServerDst() {
        return serverDst;
    }
}
