package fr.upem.net.tcp.nonblocking.readers;

public interface Message {
    int getOpCode();
    String getLoginSrc();
    String getLoginDst();
    String getMsg();
    String getServerSrc();
    String getServerDst();
}
