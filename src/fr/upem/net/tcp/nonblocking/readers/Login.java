package fr.upem.net.tcp.nonblocking.readers;

public record Login(int opCode, String login) implements Message {

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
