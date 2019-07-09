package org.itxtech.synapseapi;

import cn.nukkit.Nukkit;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.Zlib;
import co.aikar.timings.Timing;
import co.aikar.timings.TimingsManager;
import com.google.gson.Gson;
import org.itxtech.synapseapi.event.player.SynapsePlayerCreationEvent;
import org.itxtech.synapseapi.messaging.StandardMessenger;
import org.itxtech.synapseapi.network.SynLibInterface;
import org.itxtech.synapseapi.network.SynapseInterface;
import org.itxtech.synapseapi.network.protocol.spp.*;
import org.itxtech.synapseapi.utils.ClientData;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author boybook
 */
public class SynapseEntry {

    private final Timing handleDataPacketTiming = TimingsManager.getTiming("SynapseEntry - HandleDataPacket");
    private final Timing handleRedirectPacketTiming = TimingsManager.getTiming("SynapseEntry - HandleRedirectPacket");

    private SynapseAPI synapse;
    private boolean enable;
    private String serverIp;
    private int port;
    private boolean isMainServer;
    private boolean isLobbyServer;
    private boolean transferOnShutdown;
    private String password;
    private SynapseInterface synapseInterface;
    private boolean verified = false;
    private long lastUpdate;
    private long lastRecvInfo;
    private Map<UUID, SynapsePlayer> players = new HashMap<>();
    private SynLibInterface synLibInterface;
    private ClientData clientData;
    private String serverDescription;

    public SynapseEntry(SynapseAPI synapse, String serverIp, int port, boolean isMainServer, boolean isLobbyServer, boolean transferOnShutdown, String password, String serverDescription) {
        this.synapse = synapse;
        this.serverIp = serverIp;
        this.port = port;
        this.isMainServer = isMainServer;
        this.isLobbyServer = isLobbyServer;
        this.transferOnShutdown = transferOnShutdown;
        this.password = password;
        if (this.password.length() != 16) {
            synapse.getLogger().warning("You must use a 16 bit length key!");
            synapse.getLogger().warning("This SynapseAPI Entry will not be enabled!");
            enable = false;
            return;
        }
        this.serverDescription = serverDescription;

        this.synapseInterface = new SynapseInterface(this, this.serverIp, this.port);
        this.synLibInterface = new SynLibInterface(this.synapseInterface);
        this.lastUpdate = System.currentTimeMillis();
        this.lastRecvInfo = System.currentTimeMillis();
        this.getSynapse().getServer().getScheduler().scheduleRepeatingTask(SynapseAPI.getInstance(), new Ticker(this), 1);

        Thread ticker = new Thread(new AsyncTicker());
        ticker.setName("SynapseAPI Async Ticker");
        ticker.start();
    }

    public static String getRandomString(int length) { //length表示生成字符串的长度
        String base = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(base.length());
            sb.append(base.charAt(number));
        }
        return sb.toString();
    }

    public SynapseAPI getSynapse() {
        return this.synapse;
    }

    public boolean isEnable() {
        return enable;
    }

    public ClientData getClientData() {
        return clientData;
    }

    public SynapseInterface getSynapseInterface() {
        return synapseInterface;
    }

    public void shutdown() {
        if (this.verified) {
            DisconnectPacket pk = new DisconnectPacket();
            pk.type = DisconnectPacket.TYPE_GENERIC;
            pk.message = "Server closed";
            this.sendDataPacket(pk);
            this.getSynapse().getLogger().debug("Synapse client has disconnected from Synapse synapse");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                //ignore
            }
        }
        if (this.synapseInterface != null) this.synapseInterface.shutdown();
    }

    public String getServerDescription() {
        return serverDescription;
    }

    public void setServerDescription(String serverDescription) {
        this.serverDescription = serverDescription;
    }

    public void sendDataPacket(SynapseDataPacket pk) {
        this.synapseInterface.putPacket(pk);
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void broadcastPacket(SynapsePlayer[] players, DataPacket packet) {
        this.broadcastPacket(players, packet, false);
    }

    public void broadcastPacket(SynapsePlayer[] players, DataPacket packet, boolean direct) {
        packet.encode();
        BroadcastPacket broadcastPacket = new BroadcastPacket();
        broadcastPacket.direct = direct;
        broadcastPacket.payload = packet.getBuffer();
        broadcastPacket.entries = new ArrayList<>();
        for (SynapsePlayer player : players) {
            broadcastPacket.entries.add(player.getUniqueId());
        }
        this.sendDataPacket(broadcastPacket);
    }

    public boolean isMainServer() {
        return isMainServer;
    }

    public void setMainServer(boolean mainServer) {
        isMainServer = mainServer;
    }

    public String getHash() {
        return this.serverIp + ":" + this.port;
    }

    public void connect() {
        this.getSynapse().getLogger().notice("Connecting " + this.getHash());
        this.verified = false;
        ConnectPacket pk = new ConnectPacket();
        pk.password = this.password;
        pk.isMainServer = this.isMainServer();
        pk.isLobbyServer = isLobbyServer;
        pk.transferShutdown = transferOnShutdown;
        pk.description = this.serverDescription;
        pk.maxPlayers = this.getSynapse().getServer().getMaxPlayers();
        pk.protocol = SynapseInfo.CURRENT_PROTOCOL;
        this.sendDataPacket(pk);
    }

    public class AsyncTicker implements Runnable {
        private long tickUseTime;
        private long lastWarning = 0;

        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            while (Server.getInstance().isRunning()) {
                try {
                    threadTick();
                } catch (Throwable t) {
                    getSynapse().getLogger().error("Exception in Synapse Async Ticker", t);
                }

                tickUseTime = System.currentTimeMillis() - startTime;
                if (tickUseTime < 10) {
                    try {
                        Thread.sleep(10 - tickUseTime);
                    } catch (InterruptedException ignore) {
                    }
                } else if (System.currentTimeMillis() - lastWarning >= 5000) {
                    Server.getInstance().getLogger().warning("SynapseEntry<" + getHash() + "> Async Thread is overloading! TPS: " + getTicksPerSecond() + " tickUseTime: " + tickUseTime);
                    lastWarning = System.currentTimeMillis();
                }
                startTime = System.currentTimeMillis();
            }
        }

        public double getTicksPerSecond() {
            long more = this.tickUseTime - 10;
            if (more < 0) return 100;
            return NukkitMath.round(10f / (double) this.tickUseTime, 3) * 100;
        }
    }

    public class Ticker implements Runnable {
        private SynapseEntry entry;

        private Ticker(SynapseEntry entry) {
            this.entry = entry;
        }

        @Override
        public void run() {
            PlayerLoginPacket playerLoginPacket;
            while ((playerLoginPacket = playerLoginQueue.poll()) != null) {
                SynapsePlayerCreationEvent ev = new SynapsePlayerCreationEvent(synLibInterface, SynapsePlayer.class, SynapsePlayer.class, new Random().nextLong(), playerLoginPacket.address, playerLoginPacket.port);
                getSynapse().getServer().getPluginManager().callEvent(ev);
                Class<? extends SynapsePlayer> clazz = ev.getPlayerClass();
                try {
                    Constructor constructor = clazz.getConstructor(SourceInterface.class, SynapseEntry.class, Long.class, String.class, int.class);
                    SynapsePlayer player = (SynapsePlayer) constructor.newInstance(synLibInterface, this.entry, ev.getClientId(), ev.getAddress(), ev.getPort());
                    player.setUniqueId(playerLoginPacket.uuid);
                    players.put(playerLoginPacket.uuid, player);
                    getSynapse().getServer().addPlayer(playerLoginPacket.uuid.toString(), player);
                    player.handleLoginPacket(playerLoginPacket);
                } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                    Server.getInstance().getLogger().logException(e);
                }
            }

            RedirectPacketEntry redirectPacketEntry;
            while ((redirectPacketEntry = redirectPacketQueue.poll()) != null) {
                //Server.getInstance().getLogger().warning("C => S  " + redirectPacketEntry.dataPacket.getClass().getSimpleName());
                redirectPacketEntry.player.handleDataPacket(redirectPacketEntry.dataPacket);
            }

            PlayerLogoutPacket playerLogoutPacket;
            while ((playerLogoutPacket = playerLogoutQueue.poll()) != null) {
                UUID uuid1;
                if (players.containsKey(uuid1 = playerLogoutPacket.uuid)) {
                    Player player = players.get(uuid1);
                    player.close(player.getLeaveMessage(), playerLogoutPacket.reason, true);
                    removePlayer(uuid1);
                }
            }
        }
    }

    public void threadTick() {
        this.synapseInterface.process();
        if (!this.getSynapseInterface().isConnected() || !this.verified) return;
        long time = System.currentTimeMillis();
        if ((time - this.lastUpdate) >= 5000) {  //Heartbeat!
            this.lastUpdate = time;
            HeartbeatPacket pk = new HeartbeatPacket();
            pk.tps = this.getSynapse().getServer().getTicksPerSecondAverage();
            pk.load = this.getSynapse().getServer().getTickUsageAverage();
            pk.upTime = (System.currentTimeMillis() - Nukkit.START_TIME) / 1000;
            this.sendDataPacket(pk);
            //this.getSynapse().getServer().getLogger().debug(time + " -> Sending Heartbeat Packet to " + this.getHash());
        }
        /*
        for (int i = 0; i < new Random().nextInt(10) + 1; i++) {
            InformationPacket test = new InformationPacket();
            test.type = InformationPacket.TYPE_PLUGIN_MESSAGE;
            test.message = getRandomString(1024 * (new Random().nextInt(20) + 110));
            this.sendDataPacket(test);
        }*/

        long finalTime = System.currentTimeMillis();
        long usedTime = finalTime - time;
        //this.getSynapse().getServer().getLogger().warning(time + " -> threadTick 用时 " + usedTime + " 毫秒");
        if (((finalTime - this.lastUpdate) >= 30000) && this.synapseInterface.isConnected()) {  //30 seconds timeout
            this.synapseInterface.reconnect();
        }
    }

    public void removePlayer(SynapsePlayer player) {
        UUID uuid = player.getUniqueId();
        if (this.players.containsKey(uuid)) {
            this.players.remove(uuid);
        }
    }

    public void removePlayer(UUID uuid) {
        if (this.players.containsKey(uuid)) {
            this.players.remove(uuid);
        }
    }

    private final Queue<PlayerLoginPacket> playerLoginQueue = new LinkedBlockingQueue<>();
    private final Queue<PlayerLogoutPacket> playerLogoutQueue = new LinkedBlockingQueue<>();
    private final Queue<RedirectPacketEntry> redirectPacketQueue = new LinkedBlockingQueue<>();

    public void handleDataPacket(SynapseDataPacket pk) {
        this.handleDataPacketTiming.startTiming();
        //this.getSynapse().getLogger().warning("Received packet " + pk.pid() + "(" + pk.getClass().getSimpleName() + ") from " + this.serverIp + ":" + this.port);
        switch (pk.pid()) {
            case SynapseInfo.DISCONNECT_PACKET:
                DisconnectPacket disconnectPacket = (DisconnectPacket) pk;
                this.verified = false;
                switch (disconnectPacket.type) {
                    case DisconnectPacket.TYPE_GENERIC:
                        this.getSynapse().getLogger().notice("Synapse Client has disconnected due to " + disconnectPacket.message);
                        this.synapseInterface.reconnect();
                        break;
                    case DisconnectPacket.TYPE_WRONG_PROTOCOL:
                        this.getSynapse().getLogger().error(disconnectPacket.message);
                        break;
                }
                break;
            case SynapseInfo.INFORMATION_PACKET:
                InformationPacket informationPacket = (InformationPacket) pk;
                switch (informationPacket.type) {
                    case InformationPacket.TYPE_LOGIN:
                        if (informationPacket.message.equals(InformationPacket.INFO_LOGIN_SUCCESS)) {
                            this.getSynapse().getLogger().notice("Login success to " + this.serverIp + ":" + this.port);
                            this.verified = true;
                        } else if (informationPacket.message.equals(InformationPacket.INFO_LOGIN_FAILED)) {
                            this.getSynapse().getLogger().notice("Login failed to " + this.serverIp + ":" + this.port);
                        }
                        break;
                    case InformationPacket.TYPE_CLIENT_DATA:
                        this.clientData = new Gson().fromJson(informationPacket.message, ClientData.class);
                        this.lastRecvInfo = System.currentTimeMillis();
                        //this.getSynapse().getLogger().debug("Received ClientData from " + this.serverIp + ":" + this.port);
                        break;
                }
                break;
            case SynapseInfo.PLAYER_LOGIN_PACKET:
                this.playerLoginQueue.offer((PlayerLoginPacket) pk);
                break;
            case SynapseInfo.REDIRECT_PACKET:
                RedirectPacket redirectPacket = (RedirectPacket) pk;
                UUID uuid = redirectPacket.uuid;
                if (this.players.containsKey(uuid)) {
                    DataPacket pk0 = this.getSynapse().getPacket(redirectPacket.mcpeBuffer);
                    if (pk0 != null) {
                        this.handleRedirectPacketTiming.startTiming();
                        if (pk0.pid() == ProtocolInfo.BATCH_PACKET) pk0.setOffset(1);
                        pk0.decode();
                        SynapsePlayer player = this.players.get(uuid);
                        if (pk0.pid() == ProtocolInfo.BATCH_PACKET) {
                            this.processBatch((BatchPacket) pk0).forEach(subPacket -> {
                                this.redirectPacketQueue.offer(new RedirectPacketEntry(player, subPacket));
                                //Server.getInstance().getLogger().info("C => S  " + subPacket.getClass().getSimpleName());
                            });
                        } else {
                            this.redirectPacketQueue.offer(new RedirectPacketEntry(player, pk0));
                        }
                        this.handleRedirectPacketTiming.stopTiming();
                    }
                }
                break;
            case SynapseInfo.PLAYER_LOGOUT_PACKET:
                this.playerLogoutQueue.offer((PlayerLogoutPacket) pk);
                break;
            case SynapseInfo.PLUGIN_MESSAGE_PACKET:
                PluginMessagePacket messagePacket = (PluginMessagePacket) pk;

                this.synapse.getMessenger().dispatchIncomingMessage(this, messagePacket.channel, messagePacket.data);
                break;
        }
        //this.handleDataPacketTiming.stopTiming();
    }

    private class RedirectPacketEntry {
        private SynapsePlayer player;
        private DataPacket dataPacket;

        private RedirectPacketEntry(SynapsePlayer player, DataPacket dataPacket) {
            this.player = player;
            this.dataPacket = dataPacket;
        }
    }

    private List<DataPacket> processBatch(BatchPacket packet) {
        byte[] data;
        try {
            data = Zlib.inflate(packet.payload, 64 * 1024 * 1024);
        } catch (Exception e) {
            return new ArrayList<>();
        }

        int len = data.length;
        BinaryStream stream = new BinaryStream(data);
        try {
            List<DataPacket> packets = new ArrayList<>();
            while (stream.offset < len) {
                byte[] buf = stream.getByteArray();

                DataPacket pk;

                if ((pk = Server.getInstance().getNetwork().getPacket(buf[0])) != null) {
                    pk.setBuffer(buf, 3);

                    pk.decode();

                    packets.add(pk);
                }
            }
            return packets;
        } catch (Exception e) {
            if (Nukkit.DEBUG > 0) {
                Server.getInstance().getLogger().debug("BatchPacket 0x" + Binary.bytesToHexString(packet.payload));
                Server.getInstance().getLogger().logException(e);
            }
        }
        return new ArrayList<>();
    }

    public void sendPluginMessage(Plugin plugin, String channel, byte[] message) {
        StandardMessenger.validatePluginMessage(this.synapse.getMessenger(), plugin, channel, message);

        PluginMessagePacket pk = new PluginMessagePacket();
        pk.channel = channel;
        pk.data = message;

        this.sendDataPacket(pk);
    }
}
