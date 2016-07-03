package org.itxtech.synapseapi;

import cn.nukkit.Nukkit;
import cn.nukkit.event.player.PlayerLoginEvent;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.LoginPacket;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import com.google.gson.Gson;
import org.itxtech.synapseapi.network.SynLibInterface;
import org.itxtech.synapseapi.network.SynapseInterface;
import org.itxtech.synapseapi.network.protocol.spp.*;
import org.itxtech.synapseapi.utils.AES;
import org.itxtech.synapseapi.utils.ClientData;
import org.itxtech.synapseapi.utils.Util;
import sun.misc.BASE64Encoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Created by boybook on 16/6/24.
 */
public class SynapseAPI extends PluginBase {

    private static SynapseAPI instance;

    public static SynapseAPI getInstance() {
        return instance;
    }

    private String serverIp;
    private int port;
    private boolean isMainServer;
    private String password;
    private SynapseInterface synapseInterface;
    private boolean verified = false;
    private long lastUpdate;
    private long lastRecvInfo;
    private Map<UUID, SynapsePlayer> players = new HashMap<>();
    private SynLibInterface synLibInterface;
    private ClientData clientData;
    private String serverDescription;

    public static boolean enable = true;

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        enable = this.getConfig().getBoolean("enable", true);
        this.serverIp = this.getConfig().getString("server-ip", "127.0.0.1");
        this.port = this.getConfig().getInt("server-port");
        this.isMainServer = this.getConfig().getBoolean("isMainServer");
        this.password = this.getConfig().getString("password");
        this.serverDescription = this.getConfig().getString("description");
        this.synapseInterface = new SynapseInterface(this, this.serverIp, this.port);
        this.synLibInterface = new SynLibInterface(this, this.synapseInterface);
        this.lastUpdate = System.currentTimeMillis();
        this.lastRecvInfo = System.currentTimeMillis();
        this.connect();
    }

    public ClientData getClientData() {
        return clientData;
    }

    public SynapseInterface getSynapseInterface() {
        return synapseInterface;
    }

    public void shutdown(){
        if(this.verified){
            DisconnectPacket pk = new DisconnectPacket();
            pk.type = DisconnectPacket.TYPE_GENERIC;
            pk.message = "Server closed";
            this.sendDataPacket(pk);
            this.getLogger().debug("Synapse client has disconnected from Synapse server");
        }
    }

    public String getServerDescription() {
        return serverDescription;
    }

    public void setServerDescription(String serverDescription) {
        this.serverDescription = serverDescription;
    }

    public void sendDataPacket(SynapseDataPacket pk){
        this.synapseInterface.putPacket(pk);
    }

    public String getServerIp() {
        return serverIp;
    }

    public int getPort() {
        return port;
    }

    public boolean isMainServer() {
        return isMainServer;
    }

    public String getHash() {
        return this.serverIp + ":" + this.port;
    }

    public void connect(){
        this.verified = false;
        ConnectPacket pk = new ConnectPacket();
        pk.encodedPassword = Util.base64Encode(AES.encrypt(this.password, this.password));
        pk.isMainServer = this.isMainServer();
        pk.description = this.serverDescription;
        pk.maxPlayers = this.getServer().getMaxPlayers();
        pk.protocol = SynapseInfo.CURRENT_PROTOCOL;
        this.sendDataPacket(pk);
    }

    public void tick(){
        this.synapseInterface.process();
        long time = System.currentTimeMillis();
        if((time - this.lastUpdate) >= 5){//Heartbeat!
            this.lastUpdate = time;
            HeartbeatPacket pk = new HeartbeatPacket();
            pk.tps = this.getServer().getTicksPerSecondAverage();
            pk.load = this.getServer().getTickUsageAverage();
            pk.upTime = System.currentTimeMillis() - Nukkit.START_TIME;
            this.sendDataPacket(pk);
        }

        time = System.currentTimeMillis();
        if(((time - this.lastUpdate) >= 30) && this.synapseInterface.isConnected()){//30 seconds timeout
            this.synapseInterface.reconnect();
        }
    }

    public SynapseDataPacket getPacket(byte[] buffer){
        byte pid = buffer[0];
        byte start = 1;
        if(pid == 0xfe){
            pid = buffer[1];
            start++;
        }
        DataPacket data = this.getServer().getNetwork().getPacket(pid);
        if(!(data instanceof SynapseDataPacket)){
            return null;
        }
        SynapseDataPacket dataPacket = (SynapseDataPacket) data;
        data.setBuffer(buffer, start);
        return dataPacket;
    }
    
    public void removePlayer(SynapsePlayer player){
        UUID uuid = player.getUniqueId();
        if(this.players.containsKey(uuid)){
            this.players.remove(uuid);
        }
    }

    public void removePlayer(UUID uuid){
        if(this.players.containsKey(uuid)){
            this.players.remove(uuid);
        }
    }
    
    public void handleDataPacket(DataPacket pk){
        this.getLogger().debug("Received packet " + pk.pid() + " from {this.serverIp}:{this.port}");
        switch(pk.pid()){
            case SynapseInfo.INFORMATION_PACKET:
                InformationPacket pk0 = (InformationPacket)pk;
                switch(pk0.type){
                    case InformationPacket.TYPE_LOGIN:
                        if (pk0.message.equals(InformationPacket.INFO_LOGIN_SUCCESS)){
                            this.getLogger().info("Login success to " + this.serverIp + ":" + this.port);
                            this.verified = true;
                        } else if(pk0.message.equals(InformationPacket.INFO_LOGIN_FAILED)){
                        this.getLogger().info("Login failed to " + this.serverIp + ":" + this.port);
                    }
                    break;
                    case InformationPacket.TYPE_CLIENT_DATA:
                        this.clientData = new Gson().fromJson(pk0.message, ClientData.class);
                        this.lastRecvInfo = System.currentTimeMillis();
                        break;
                }
                break;
            case SynapseInfo.PLAYER_LOGIN_PACKET:
                PlayerLoginPacket pk1 = (PlayerLoginPacket)pk;
                SynapsePlayer player = new SynapsePlayer(this.synLibInterface, new Random().nextLong(), pk1.address, pk1.port);
                player.setUniqueId(pk1.uuid);
                this.getServer().addOnlinePlayer(player);
                this.players.put(pk1.uuid, player);
                player.handleLoginPacket(pk1);
                break;
            case SynapseInfo.REDIRECT_PACKET:
                RedirectPacket pk2 = (RedirectPacket)pk;
                UUID uuid = pk2.uuid;
                if(this.players.containsKey(uuid)){
                    pk = this.getPacket(pk2.mcpeBuffer);
                    pk2.decode();
                    this.players.get(uuid).handleDataPacket(pk);
                }
                break;
            case SynapseInfo.PLAYER_LOGOUT_PACKET:
                PlayerLogoutPacket pk3 = (PlayerLogoutPacket) pk;
                UUID uuid1;
                if(this.players.containsKey(uuid1 = pk3.uuid)){
                    this.players.get(uuid1).close("", pk3.reason);
                    this.removePlayer(uuid1);
                }
                break;
        }
    }
}
