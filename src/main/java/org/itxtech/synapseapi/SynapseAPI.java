package org.itxtech.synapseapi;

import cn.nukkit.Server;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.RakNetInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Binary;
import org.itxtech.synapseapi.network.protocol.mcpe.SetHealthPacket;

import java.util.*;

/**
 * Created by boybook on 16/6/24.
 */
public class SynapseAPI extends PluginBase {

    private static SynapseAPI instance;

    public static SynapseAPI getInstance() {
        return instance;
    }

    public static boolean enable = true;
    private boolean autoConnect = true;
    private boolean loadingScreen = true;

    public boolean isAutoConnect() {
        return autoConnect;
    }

    private Map<String, SynapseEntry> synapseEntries = new HashMap<>();

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

        String serverIp = this.getConfig().getString("server-ip", "127.0.0.1");
        int port = this.getConfig().getInt("server-port", 10305);
        boolean isMainServer = this.getConfig().getBoolean("isMainServer");
        String password = this.getConfig().getString("password");
        String serverDescription = this.getConfig().getString("description");
        this.loadingScreen = this.getConfig().getBoolean("loadingScreen", true);

        for(SourceInterface interfaz : this.getServer().getNetwork().getInterfaces()){
            if(interfaz instanceof RakNetInterface){
                if(this.getConfig().getBoolean("disable-rak")){
                    interfaz.shutdown();
                    break;
                }
            }
        }
        this.autoConnect = this.getConfig().getBoolean("autoConnect", true);

        if (this.autoConnect) {
            SynapseEntry entry = new SynapseEntry(this, serverIp, port, isMainServer, password, serverDescription);
            this.addSynapseAPI(entry);
        }
        /*
        this.synapseInterface = new SynapseInterface(this, this.serverIp, this.port);
        this.synLibInterface = new SynLibInterface(this.synapseInterface);
        this.lastUpdate = System.currentTimeMillis();
        this.lastRecvInfo = System.currentTimeMillis();
        if (this.autoConnect) this.connect();
        */
    }

    public boolean isUseLoadingScreen() {
        return loadingScreen;
    }

    public Map<String, SynapseEntry> getSynapseEntries() {
        return synapseEntries;
    }

    public void addSynapseAPI(SynapseEntry entry) {
        this.synapseEntries.put(entry.getHash(), entry);
    }

    public SynapseEntry getSynapseEntry(String hash) {
        return this.synapseEntries.get(hash);
    }

    public void shutdownAll() {
        for (SynapseEntry entry: new ArrayList<>(this.synapseEntries.values())) {
            entry.shutdown();
        }
    }

    @Override
    public void onDisable() {
        this.shutdownAll();
    }

    public DataPacket getPacket(byte[] buffer){
        byte pid = buffer[0] == (byte) 0xfe ? (byte) 0xff : buffer[0];

        byte start = 1;
        DataPacket data;
        data = this.getServer().getNetwork().getPacket(pid);

        if(data == null){
            Server.getInstance().getLogger().notice("C => S 未找到匹配数据包");
            return null;
        }
        data.setBuffer(buffer, start);
        return data;
    }


}
