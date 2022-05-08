package fr.upem.net.tcp.nonblocking.readers.type;

public record FileMessage(String server_src, String login_src, String server_dst, String login_dst, String filename, int nbBlocks, int blockSize) {
}
