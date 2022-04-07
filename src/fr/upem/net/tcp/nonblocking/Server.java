package fr.upem.net.tcp.nonblocking;

import java.net.InetSocketAddress;

public record Server(InetSocketAddress sc, String name) {
}
