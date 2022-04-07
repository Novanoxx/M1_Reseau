package fr.upem.net.tcp.nonblocking.readers;

public record PrivateMessage(int opCode, String serverSrc, String loginSrc, String serverDst, String loginDst, String msg) implements Message {

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
