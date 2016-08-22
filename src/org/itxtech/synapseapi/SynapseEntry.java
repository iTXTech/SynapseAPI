package org.itxtech.synapseapi;

import cn.nukkit.network.RakNetInterface;
import cn.nukkit.network.SourceInterface;
import org.itxtech.synapseapi.network.SynLibInterface;
import org.itxtech.synapseapi.network.SynapseInterface;
import org.itxtech.synapseapi.utils.ClientData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Created by boybook on 16/8/21.
 */
public class SynapseEntry {

    private SynapseAPI plugin;

    private boolean enable;
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

    public SynapseAPI getPlugin() {
        return this.plugin;
    }

    public SynapseEntry(SynapseAPI plugin, String serverIp, int port, boolean isMainServer, String password, String serverDescription) {
        this.plugin = plugin;
        this.serverIp = serverIp;
        this.port = port;
        this.isMainServer = isMainServer;
        this.password = password;
        if (this.password.length() != 16) {
            plugin.getLogger().warning("You must use a 16 bit length key!");
            plugin.getLogger().warning("This SynapseAPI Entry will not be enabled!");
            enable = false;
            return;
        }
        this.serverDescription = serverDescription;

        //this.synapseInterface = new SynapseInterface(this, this.serverIp, this.port);
        this.synLibInterface = new SynLibInterface(this.synapseInterface);
        this.lastUpdate = System.currentTimeMillis();
        this.lastRecvInfo = System.currentTimeMillis();
        //if (plugin.isAutoConnect()) this.connect();
    }

    public boolean isEnable() {
        return enable;
    }


}
