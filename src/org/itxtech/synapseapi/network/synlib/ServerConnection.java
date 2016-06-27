package org.itxtech.synapseapi.network.synlib;

import cn.nukkit.utils.Binary;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by boybook on 16/6/24.
 */
public class ServerConnection {
    public static final byte[] MAGIC_BYTES = new byte[]{
            (byte) 0x35, (byte) 0xac, (byte) 0x66, (byte) 0xbf
    };

    private byte[] receiveBuffer = new byte[0];
    private byte[] sendBuffer = new byte[0];
    /**
     * @var resource
     */
    private SynapseSocket socket;
    private String ip;
    private int port;
    /**
     * @var SynapseClient
     */
    private SynapseClient server;
    private long lastCheck;
    private boolean connected;

    public ServerConnection(SynapseClient server, SynapseSocket socket) {
        this.server = server;
        this.socket = socket;
        this.ip = socket.getSocket().getInetAddress().getHostAddress();
        this.port = socket.getSocket().getPort();

        this.lastCheck = System.currentTimeMillis();
        this.connected = true;

        this.run();
    }

    public void run() {
        this.tickProcessor();
    }

    private void tickProcessor() {
        while (!this.server.isShutdown()) {
            long start = System.currentTimeMillis();
            this.tick();
            long time = System.currentTimeMillis();
            if (time - start < 1) {  //todo TPS ???
                try {
                    Thread.sleep(1 - time + start);
                } catch (InterruptedException e) {
                    //ignore
                }
            }
        }
        this.tick();
        this.socket.close();
    }

    private void tick() {
        this.update();
        byte[] data = this.readPacket();
        while (data != null) {
            this.server.pushThreadToMainPacket(data);
        }
        byte[] data1 = this.server.readMainToThreadPacket();
        while (data1.length > 0) {
            this.writePacket(data1);
        }
    }

    public String getHash() {
        return this.getIp() + ":" + this.getPort();
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public SynapseSocket getSocket() {
        return socket;
    }

    public void update() {
        if (this.server.needReconnect && this.connected) {
            this.connected = false;
            this.server.needReconnect = false;
        }
        if (this.connected) {
            try {
                byte[] buffer = readPacket();
                //已无能为力...............................................................
                byte[] bytes = new byte[buffer.length + this.receiveBuffer.length];
                System.arraycopy(buffer, 0, bytes, 0, buffer.length);
                System.arraycopy(this.receiveBuffer, 0, bytes, 0, receiveBuffer.length);
                this.receiveBuffer = bytes;
                if (this.sendBuffer.length > 0) {
                    writePacket(this.sendBuffer);
                    this.sendBuffer = new byte[0];
                }
            } catch (IOException e) {
                int err = e.hashCode();  //todo ??????
                if (err == 10057 || err == 10054) {
                    this.server.getLogger().error("Synapse connection has disconnected unexpectedly");
                    this.connected = false;
                    this.server.setConnected(false);
                }
            }
        } else {
            long time;
            if (((time = System.currentTimeMillis()) - this.lastCheck) >= 3) {//re-connect
                this.server.getLogger().notice("Trying to re-connect to Synapse Server");
                if (this.socket.connect()) {
                    this.connected = true;
                    this.ip = "127.0.0.1";
                    this.port = this.socket.getPort();
                    this.server.setConnected(true);
                    this.server.setNeedAuth(true);
                }
                this.lastCheck = time;
            }
        }
    }

    public byte[] readPacket() {
        byte[] buffer = new byte[2048];
        byte[] bytes = this.socket.readPacket();
        /*
        end = explode(self::MAGIC_BYTES, this.receiveBuffer, 2);
        if(count(end) <= 2){
            if(count(end) == 1){
                if(strstr(end[0], self::MAGIC_BYTES)){
                    this.receiveBuffer = "";
                }else{
                    return null;
                }
            }else{
                this.receiveBuffer = end[1];
            }
            buffer = end[0];
            if(strlen(buffer) < 4){
                return null;
            }
            len = Binary::readLInt(substr(buffer, 0, 4));
            buffer = substr(buffer, 4);
            if(len != strlen(buffer)){
                throw new \Exception("Wrong packet buffer");
            }
            return buffer;
        }
        */
        return buffer;
    }

    public void writePacket(byte[] data) {
        this.socket.getSocket().write(Binary.writeLInt(data.length) + data + ServerConnection.MAGIC_BYTES);
    }
}
