package org.itxtech.synapseapi.network.synlib;

import cn.nukkit.utils.ThreadedLogger;

import java.net.*;
import java.nio.channels.*;
import java.nio.*;
import java.io.*;

/**
 * Created by boybook on 16/6/24.
 */
public class SynapseSocket {

    private SocketChannel socket;
    private Selector selector = null;
    private ThreadedLogger logger;
    private String interfaz;
    private int port;

    public SynapseSocket(ThreadedLogger logger, int port) {
        this(logger, port, "127.0.0.1");
    }

    public SynapseSocket(ThreadedLogger logger, String interfaz) {
        this(logger, 10305, interfaz);
    }

    public SynapseSocket(ThreadedLogger logger) {
        this(logger, 10305, "127.0.0.1");
    }

    public SynapseSocket(ThreadedLogger logger, int port, String interfaz) {
        this.logger = logger;
        this.interfaz = interfaz;
        this.port = port;
        this.connect();
    }

    public boolean connect() {
        try {
            this.selector = Selector.open();
            InetSocketAddress isa = new InetSocketAddress(this.interfaz, this.port);
            this.socket = SocketChannel.open(isa);
            this.socket.configureBlocking(false);
            this.socket.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT);
            this.logger.notice("SynapseAPI has connected to " + this.interfaz + ":" + this.port);
        } catch (IOException e) {
            this.logger.critical("Synapse Client can't connect " + this.interfaz + ":" + this.port);
            this.logger.error("Socket error: " + e.getMessage());
            return false;
        }
        return true;
    }

    public SocketChannel getSocket() {
        return this.socket;
    }

    public int getPort() {
        return this.port;
    }

    public byte[] readPacket() {
        byte[] buffer = new byte[2048];
        try {
            if (selector.select() > 0) {
                for (SelectionKey sk : selector.selectedKeys()) {
                    selector.selectedKeys().remove(sk);
                    if (sk.isReadable()) {
                        SocketChannel sc = (SocketChannel) sk.channel();
                        ByteBuffer buff = ByteBuffer.allocate(2048);
                        while (sc.read(buff) > 0) {
                            sc.read(buff);
                            buff.flip();
                        }
                        sk.interestOps(SelectionKey.OP_READ);
                        buffer = buff.array();
                    }
                }
            }
        } catch (IOException e) {
            this.logger.error("ReadPacket error: " + e.getMessage());
        }
        return buffer;
    }

    public void close() {
        try {
            this.socket.close();
        } catch (IOException e) {
            this.logger.critical("Synapse Client can't close!");
        }

    }
}
