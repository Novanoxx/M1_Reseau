package fr.upem.net.tcp.nonblocking.readers.type;

import java.nio.ByteBuffer;

public interface Message {
    void fillBuffer(ByteBuffer buffer);
    int getOpCode();
    String getLoginSrc();
    String getLoginDst();
    String getMsg();
    String getServerSrc();
    String getServerDst();
}
