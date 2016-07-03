package org.itxtech.synapseapi.network.synlib;

import cn.nukkit.Server;
import cn.nukkit.utils.Binary;
import org.itxtech.synapseapi.utils.Util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

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
        this.ip = socket.getSocket().socket().getInetAddress().getHostAddress();
        this.port = socket.getSocket().socket().getPort();

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
            try {
                this.tick();
            } catch (Exception e) {
                Server.getInstance().getLogger().alert(e.getLocalizedMessage());
            }

            long time = System.currentTimeMillis();
            if (time - start < 1) {  //todo TPS ???
                try {
                    Thread.sleep(1 - time + start);
                } catch (InterruptedException e) {
                    //ignore
                }
            }
        }
        try {
            this.tick();
        } catch (Exception e) {
            Server.getInstance().getLogger().alert(e.getLocalizedMessage());
        }
        this.socket.close();
    }

    private void tick() throws Exception {
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

    public void update() throws Exception {
        if (this.server.needReconnect && this.connected) {
            this.connected = false;
            this.server.needReconnect = false;
        }
        if (this.connected) {
            try {
                byte[] buffer = readPacket();
                this.receiveBuffer = Binary.appendBytes(buffer, this.receiveBuffer);
                if (this.sendBuffer.length > 0) {
                    this.socket.getSocket().write(ByteBuffer.wrap(this.sendBuffer));
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

    public byte[] readPacket() throws Exception {
        byte[] buffer = this.socket.readPacket();
        String str = Arrays.toString(buffer);
        String[] arr = str.split(Arrays.toString(MAGIC_BYTES),2);
        if(arr.length <= 2){
            if(arr.length == 1){
                if (arr[0].length() >= 0) {
                    this.receiveBuffer = new byte[0];
                }else{
                    return new byte[0];
                }
            }else{
                this.receiveBuffer = arr[1].getBytes();
            }
            buffer = arr[0].getBytes();
            if(buffer.length < 4){
                return new byte[0];
            }
            int len = Binary.readLInt(Arrays.copyOfRange(buffer, 0, 4));
            if(len != Arrays.copyOfRange(buffer, 4, buffer.length).length){
                throw new Exception("Wrong packet buffer");
            }
        }

        return buffer;
    }

    public void writePacket(byte[] data) {
        byte[] buffer = Util.concatByte(Binary.writeLInt(data.length), data, ServerConnection.MAGIC_BYTES);
        this.sendBuffer = Binary.appendBytes(buffer);
    }

}
