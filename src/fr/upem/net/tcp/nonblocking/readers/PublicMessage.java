package fr.upem.net.tcp.nonblocking.readers;

public record PublicMessage(int opCode, String server, String login, String msg) implements Message {
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
