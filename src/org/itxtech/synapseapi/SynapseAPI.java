package org.itxtech.synapseapi;

import cn.nukkit.Nukkit;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.player.PlayerCreationEvent;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Utils;
import com.google.gson.Gson;
import org.itxtech.synapseapi.event.player.SynapsePlayerCreationEvent;
import org.itxtech.synapseapi.network.SynLibInterface;
import org.itxtech.synapseapi.network.SynapseInterface;
import org.itxtech.synapseapi.network.protocol.mcpe.SetHealthPacket;
import org.itxtech.synapseapi.network.protocol.spp.*;
import org.itxtech.synapseapi.utils.AES;
import org.itxtech.synapseapi.utils.ClientData;
import org.itxtech.synapseapi.utils.Util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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
        this.getServer().getNetwork().registerPacket(ProtocolInfo.SET_HEALTH_PACKET, SetHealthPacket.class);
        this.saveDefaultConfig();
        enable = this.getConfig().getBoolean("enable", true);
        if (!enable) {
            this.getLogger().warning("The SynapseAPI is not be enabled!");
            this.setEnabled(false);
            return;
        }
        this.serverIp = this.getConfig().getString("server-ip", "127.0.0.1");
        this.port = this.getConfig().getInt("server-port", 10305);
        this.isMainServer = this.getConfig().getBoolean("isMainServer");
        this.password = this.getConfig().getString("password");
        if (this.password.length() != 16) {
            this.getLogger().warning("You must use a 16 bit length key!");
            this.getLogger().warning("The SynapseAPI will not be enabled!");
            enable = false;
            this.setEnabled(false);
            return;
        }
        this.serverDescription = this.getConfig().getString("description");
        this.synapseInterface = new SynapseInterface(this, this.serverIp, this.port);
        this.synLibInterface = new SynLibInterface(this, this.synapseInterface);
        this.lastUpdate = System.currentTimeMillis();
        this.lastRecvInfo = System.currentTimeMillis();
        this.connect();
    }

    @Override
    public void onDisable() {
        this.shutdown();
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
        this.getLogger().notice("Start to connect Synapse Server!   Address: " + this.getHash());
        this.verified = false;
        ConnectPacket pk = new ConnectPacket();
        pk.encodedPassword = Util.base64Encode(AES.Encrypt(this.password, this.password));
        pk.isMainServer = this.isMainServer();
        pk.description = this.serverDescription;
        pk.maxPlayers = this.getServer().getMaxPlayers();
        pk.protocol = SynapseInfo.CURRENT_PROTOCOL;
        this.sendDataPacket(pk);
        new Thread(new Ticker()).start();
    }

    public class Ticker implements Runnable {
        public void run() {
            long startTime = System.currentTimeMillis();
            while (isEnabled()) {
                tick();
                long duration = System.currentTimeMillis() - startTime;
                if (duration < 50) {
                    try{
                        Thread.sleep(50 - duration);
                    } catch (InterruptedException e) {
                        //ignore
                    }
                }
                startTime = System.currentTimeMillis();
            }
        }
    }

    public void tick(){
        this.synapseInterface.process();
        long time = System.currentTimeMillis();
        if((time - this.lastUpdate) >= 5000){//Heartbeat!
            this.lastUpdate = time;
            HeartbeatPacket pk = new HeartbeatPacket();
            pk.tps = this.getServer().getTicksPerSecondAverage();
            pk.load = this.getServer().getTickUsageAverage();
            pk.upTime = (System.currentTimeMillis() - Nukkit.START_TIME) / 1000;
            this.sendDataPacket(pk);
        }

        time = System.currentTimeMillis();
        if(((time - this.lastUpdate) >= 30000) && this.synapseInterface.isConnected()){//30 seconds timeout
            this.synapseInterface.reconnect();
        }
    }

    public DataPacket getPacket(byte[] buffer){
        byte pid = buffer[0];
        byte start = 1;
        if(pid == (byte) 0xfe){
            pid = buffer[1];
            start++;
        }
        DataPacket data = this.getServer().getNetwork().getPacket(pid);
        if(data == null){
            return null;
        }
        data.setBuffer(buffer, start);
        return data;
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
        this.getLogger().debug("Received packet " + pk.pid() + " from " + this.serverIp + ":" + this.port);
        switch(pk.pid()){
            case SynapseInfo.DISCONNECT_PACKET:
                DisconnectPacket disconnectPacket = (DisconnectPacket) pk;
                this.verified = false;
                switch(disconnectPacket.type){
                    case DisconnectPacket.TYPE_GENERIC:
                        this.getLogger().notice("Synapse Client has disconnected due to " + disconnectPacket.message);
                        this.synapseInterface.reconnect();
                        break;
                    case DisconnectPacket.TYPE_WRONG_PROTOCOL:
                        this.getLogger().error(disconnectPacket.message);
                        break;
                }
                break;
            case SynapseInfo.INFORMATION_PACKET:
                InformationPacket informationPacket = (InformationPacket)pk;
                switch(informationPacket.type){
                    case InformationPacket.TYPE_LOGIN:
                        if (informationPacket.message.equals(InformationPacket.INFO_LOGIN_SUCCESS)){
                            this.getLogger().notice("Login success to " + this.serverIp + ":" + this.port);
                            this.verified = true;
                        } else if(informationPacket.message.equals(InformationPacket.INFO_LOGIN_FAILED)){
                        this.getLogger().notice("Login failed to " + this.serverIp + ":" + this.port);
                    }
                    break;
                    case InformationPacket.TYPE_CLIENT_DATA:
                        this.clientData = new Gson().fromJson(informationPacket.message, ClientData.class);
                        this.lastRecvInfo = System.currentTimeMillis();
                        this.getLogger().notice("Received ClientData from " + this.serverIp + ":" + this.port);
                        break;
                }
                break;
            case SynapseInfo.PLAYER_LOGIN_PACKET:
                PlayerLoginPacket playerLoginPacket = (PlayerLoginPacket)pk;
                SynapsePlayerCreationEvent ev = new SynapsePlayerCreationEvent(this.synLibInterface, SynapsePlayer.class, SynapsePlayer.class, new Random().nextLong(), playerLoginPacket.address, playerLoginPacket.port);
                this.getServer().getPluginManager().callEvent(ev);
                Class<? extends SynapsePlayer> clazz = ev.getPlayerClass();
                try {
                    Constructor constructor = clazz.getConstructor(SourceInterface.class, Long.class, String.class, int.class);
                    SynapsePlayer player = (SynapsePlayer) constructor.newInstance(this.synLibInterface, ev.getClientId(), ev.getAddress(), ev.getPort());
                    player.setUniqueId(playerLoginPacket.uuid);
                    this.players.put(playerLoginPacket.uuid, player);
                    this.getServer().addPlayer(playerLoginPacket.uuid.toString(), player);
                    player.handleLoginPacket(playerLoginPacket);
                } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                    Server.getInstance().getLogger().logException(e);
                }
                break;
            case SynapseInfo.REDIRECT_PACKET:
                RedirectPacket redirectPacket = (RedirectPacket)pk;
                UUID uuid = redirectPacket.uuid;
                if(this.players.containsKey(uuid)){
                    pk = this.getPacket(redirectPacket.mcpeBuffer);
                    if(pk != null) {
                        pk.decode();
                        this.players.get(uuid).handleDataPacket(pk);
                    }
                }
                break;
            case SynapseInfo.PLAYER_LOGOUT_PACKET:
                PlayerLogoutPacket playerLogoutPacket = (PlayerLogoutPacket) pk;
                UUID uuid1;
                if(this.players.containsKey(uuid1 = playerLogoutPacket.uuid)){
                    this.players.get(uuid1).close("", playerLogoutPacket.reason);
                    this.removePlayer(uuid1);
                }
                break;
        }
    }
}
