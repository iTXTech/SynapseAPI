package org.itxtech.synapseapi;

import cn.nukkit.AdventureSettings;
import cn.nukkit.AdventureSettings.Type;
import cn.nukkit.Player;
import cn.nukkit.PlayerFood;
import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.command.data.CommandDataVersions;
import cn.nukkit.event.player.PlayerKickEvent;
import cn.nukkit.event.player.PlayerLoginEvent;
import cn.nukkit.event.server.DataPacketSendEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.*;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.TextFormat;
import co.aikar.timings.Timing;
import co.aikar.timings.TimingsManager;
import org.itxtech.synapseapi.event.player.SynapsePlayerConnectEvent;
import org.itxtech.synapseapi.event.player.SynapsePlayerTransferEvent;
import org.itxtech.synapseapi.network.protocol.spp.PlayerLoginPacket;
import org.itxtech.synapseapi.runnable.TransferRunnable;
import org.itxtech.synapseapi.utils.ClientData;
import org.itxtech.synapseapi.utils.ClientData.Entry;
import org.itxtech.synapseapi.utils.DataPacketEidReplacer;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Created by boybook on 16/6/24.
 */
public class SynapsePlayer extends Player {
    private static final Method updateName;

    public static final long REPLACE_ID = Long.MAX_VALUE;

    private static final Map<Byte, Timing> handlePlayerDataPacketTimings = new HashMap<>();

    public boolean isSynapseLogin = false;
    protected SynapseEntry synapseEntry;
    private boolean isFirstTimeLogin = false;
    private long synapseSlowLoginUntil = 0;

    static {
        try {
            updateName = Server.class.getDeclaredMethod("updateName", UUID.class, String.class);
            updateName.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public SynapsePlayer(SourceInterface interfaz, SynapseEntry synapseEntry, Long clientID, String ip, int port) {
        super(interfaz, clientID, ip, port);
        this.synapseEntry = synapseEntry;
        this.isSynapseLogin = this.synapseEntry != null;
    }

    /**
     * Returns a client-friendly gamemode of the specified real gamemode
     * This function takes care of handling gamemodes known to MCPE (as of 1.1.0.3, that includes Survival, Creative and Adventure)
     * <p>
     * TODO: remove this when Spectator Mode gets added properly to MCPE
     */
    private static int getClientFriendlyGamemode(int gamemode) {
        gamemode &= 0x03;
        if (gamemode == Player.SPECTATOR) {
            return Player.CREATIVE;
        }
        return gamemode;
    }

    public void handleLoginPacket(PlayerLoginPacket packet) {
        if (!this.isSynapseLogin) {
            super.handleDataPacket(SynapseAPI.getInstance().getPacket(packet.cachedLoginPacket));
            return;
        }
        this.isFirstTimeLogin = packet.isFirstTime;
        SynapsePlayerConnectEvent ev;
        this.server.getPluginManager().callEvent(ev = new SynapsePlayerConnectEvent(this, this.isFirstTimeLogin));
        if (!ev.isCancelled()) {
            DataPacket pk = SynapseAPI.getInstance().getPacket(packet.cachedLoginPacket);
            pk.setOffset(1);
            pk.decode();
            this.handleDataPacket(pk);
        }
    }

    public SynapseEntry getSynapseEntry() {
        return synapseEntry;
    }

    @Override
    protected void processLogin() {
        if (!this.isSynapseLogin) {
            super.processLogin();
            return;
        }
        if (!this.server.isWhitelisted((this.getName()).toLowerCase())) {
            this.kick(PlayerKickEvent.Reason.NOT_WHITELISTED, "Server is white-listed");

            return;
        } else if (this.isBanned()) {
            this.kick(PlayerKickEvent.Reason.NAME_BANNED, "You are banned");
            return;
        } else if (this.server.getIPBans().isBanned(this.getAddress())) {
            this.kick(PlayerKickEvent.Reason.IP_BANNED, "You are banned");
            return;
        }

        if (this.hasPermission(Server.BROADCAST_CHANNEL_USERS)) {
            this.server.getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_USERS, this);
        }
        if (this.hasPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE)) {
            this.server.getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE, this);
        }

        for (Player p : new ArrayList<>(this.server.getOnlinePlayers().values())) {
            if (p != this && p.getName() != null && p.getName().equalsIgnoreCase(this.getName())) {
                if (!p.kick(PlayerKickEvent.Reason.NEW_CONNECTION, "logged in from another location")) {
                    this.close(this.getLeaveMessage(), "Already connected");
                    return;
                }
            } else if (p.loggedIn && this.getUniqueId().equals(p.getUniqueId())) {
                if (!p.kick(PlayerKickEvent.Reason.NEW_CONNECTION, "logged in from another location")) {
                    this.close(this.getLeaveMessage(), "Already connected");
                    return;
                }
            }
        }

        CompoundTag nbt;
        File legacyDataFile = new File(server.getDataPath() + "players/" + this.username.toLowerCase() + ".dat");
        File dataFile = new File(server.getDataPath() + "players/" + this.uuid.toString() + ".dat");
        if (legacyDataFile.exists() && !dataFile.exists()) {
            nbt = this.server.getOfflinePlayerData(this.username);

            if (!legacyDataFile.delete()) {
                MainLogger.getLogger().warning("Could not delete legacy player data for " + this.username);
            }
        } else {
            nbt = this.server.getOfflinePlayerData(this.uuid);
        }

        if (nbt == null) {
            this.close(this.getLeaveMessage(), "Invalid data");
            return;
        }

        if (getLoginChainData().isXboxAuthed() && server.getPropertyBoolean("xbox-auth") || !server.getPropertyBoolean("xbox-auth")) {
            try {
                updateName.invoke(server, this.uuid, this.username);
            } catch (IllegalAccessException | InvocationTargetException e) {
                // TODO: 13/03/2019 Something here?
            }
        }

        this.playedBefore = (nbt.getLong("lastPlayed") - nbt.getLong("firstPlayed")) > 1;

        boolean alive = true;

        nbt.putString("NameTag", this.username);

        if (0 >= nbt.getShort("Health")) {
            alive = false;
        }

        int exp = nbt.getInt("EXP");
        int expLevel = nbt.getInt("expLevel");
        this.setExperience(exp, expLevel);

        this.gamemode = nbt.getInt("playerGameType") & 0x03;
        if (this.server.getForceGamemode()) {
            this.gamemode = this.server.getGamemode();
            nbt.putInt("playerGameType", this.gamemode);
        }

        this.adventureSettings = new AdventureSettings(this)
                .set(Type.WORLD_IMMUTABLE, isAdventure())
                .set(Type.WORLD_BUILDER, !isAdventure())
                .set(Type.AUTO_JUMP, true)
                .set(Type.ALLOW_FLIGHT, isCreative())
                .set(Type.NO_CLIP, isSpectator());

        Level level;
        if ((level = this.server.getLevelByName(nbt.getString("Level"))) == null || !alive) {
            this.setLevel(this.server.getDefaultLevel());
            nbt.putString("Level", this.level.getName());
            nbt.getList("Pos", DoubleTag.class)
                    .add(new DoubleTag("0", this.level.getSpawnLocation().x))
                    .add(new DoubleTag("1", this.level.getSpawnLocation().y))
                    .add(new DoubleTag("2", this.level.getSpawnLocation().z));
        } else {
            this.setLevel(level);
        }

        for (Tag achievement : nbt.getCompound("Achievements").getAllTags()) {
            if (!(achievement instanceof ByteTag)) {
                continue;
            }

            if (((ByteTag) achievement).getData() > 0) {
                this.achievements.add(achievement.getName());
            }
        }

        nbt.putLong("lastPlayed", System.currentTimeMillis() / 1000);

        if (this.server.getAutoSave()) {
            this.server.saveOfflinePlayerData(this.uuid, nbt, true);
        }

        this.sendPlayStatus(PlayStatusPacket.LOGIN_SUCCESS);
        this.server.onPlayerLogin(this);

        ListTag<DoubleTag> posList = nbt.getList("Pos", DoubleTag.class);

        super.init(this.level.getChunk((int) posList.get(0).data >> 4, (int) posList.get(2).data >> 4, true), nbt);

        if (!this.namedTag.contains("foodLevel")) {
            this.namedTag.putInt("foodLevel", 20);
        }
        int foodLevel = this.namedTag.getInt("foodLevel");
        if (!this.namedTag.contains("FoodSaturationLevel")) {
            this.namedTag.putFloat("FoodSaturationLevel", 20);
        }
        float foodSaturationLevel = this.namedTag.getFloat("foodSaturationLevel");
        this.foodData = new PlayerFood(this, foodLevel, foodSaturationLevel);

        if (this.isSpectator()) this.keepMovement = true;

        this.forceMovement = this.teleportPosition = this.getPosition();

        if (this.isFirstTimeLogin) {
            ResourcePacksInfoPacket infoPacket = new ResourcePacksInfoPacket();
            infoPacket.resourcePackEntries = this.server.getResourcePackManager().getResourceStack();
            infoPacket.mustAccept = this.server.getForceResources();
            this.dataPacket(infoPacket);
        } else {
            this.shouldLogin = true;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void completeLoginSequence() {
        if (!this.isSynapseLogin) {
            super.completeLoginSequence();
            return;
        }
        PlayerLoginEvent ev;
        this.server.getPluginManager().callEvent(ev = new PlayerLoginEvent(this, "Plugin reason"));
        if (ev.isCancelled()) {
            this.close(this.getLeaveMessage(), ev.getKickMessage());

            return;
        }

        if (this.isCreative()) {
            this.inventory.setHeldItemSlot(0);
        } else {
            this.inventory.setHeldItemSlot(this.inventory.getHotbarSlotIndex(0));
        }

        if (this.isSpectator()) this.keepMovement = true;

        Level level;
        if (this.spawnPosition == null && this.namedTag.contains("SpawnLevel") && (level = this.server.getLevelByName(this.namedTag.getString("SpawnLevel"))) != null) {
            this.spawnPosition = new Position(this.namedTag.getInt("SpawnX"), this.namedTag.getInt("SpawnY"), this.namedTag.getInt("SpawnZ"), level);
        }

        Position spawnPosition = this.getSpawn();
        if (this.isFirstTimeLogin) {
            StartGamePacket startGamePacket = new StartGamePacket();
            startGamePacket.entityUniqueId = REPLACE_ID;
            startGamePacket.entityRuntimeId = REPLACE_ID;
            startGamePacket.playerGamemode = getClientFriendlyGamemode(this.gamemode);
            startGamePacket.x = (float) this.x;
            startGamePacket.y = (float) this.y;
            startGamePacket.z = (float) this.z;
            startGamePacket.yaw = (float) this.yaw;
            startGamePacket.pitch = (float) this.pitch;
            startGamePacket.seed = -1;
            startGamePacket.dimension = (byte) (this.level.getDimension() & 0xff);
            startGamePacket.worldGamemode = getClientFriendlyGamemode(this.gamemode);
            startGamePacket.difficulty = this.server.getDifficulty();
            startGamePacket.spawnX = (int) spawnPosition.x;
            startGamePacket.spawnY = (int) spawnPosition.y;
            startGamePacket.spawnZ = (int) spawnPosition.z;
            startGamePacket.hasAchievementsDisabled = true;
            startGamePacket.dayCycleStopTime = -1;
            startGamePacket.eduMode = false;
            startGamePacket.rainLevel = 0;
            startGamePacket.lightningLevel = 0;
            startGamePacket.commandsEnabled = this.isEnableClientCommand();
            startGamePacket.levelId = "";
            startGamePacket.worldName = this.getServer().getNetwork().getName();
            startGamePacket.generator = 1; //0 old, 1 infinite, 2 flat
            startGamePacket.gameRules = this.getLevel().getGameRules();
            this.dataPacket(startGamePacket);

            this.dataPacket(new BiomeDefinitionListPacket());
            this.dataPacket(new AvailableEntityIdentifiersPacket());
        } else {
            AdventureSettings newSettings = this.getAdventureSettings().clone(this);
            newSettings.set(Type.WORLD_IMMUTABLE, (gamemode & 0x02) > 0);
            newSettings.set(Type.BUILD_AND_MINE, (gamemode & 0x02) <= 0);
            newSettings.set(Type.WORLD_BUILDER, (gamemode & 0x02) <= 0);
            newSettings.set(Type.ALLOW_FLIGHT, (gamemode & 0x01) > 0);
            newSettings.set(Type.NO_CLIP, gamemode == 0x03);
            newSettings.set(Type.FLYING, gamemode == 0x03);
            this.keepMovement = this.isSpectator();
            SetPlayerGameTypePacket pk = new SetPlayerGameTypePacket();
            pk.gamemode = getClientFriendlyGamemode(gamemode);
            this.dataPacket(pk);
        }

        this.loggedIn = true;

        spawnPosition.level.sendTime(this);

        this.setMovementSpeed(DEFAULT_SPEED);
        this.sendAttributes();
        this.setNameTagVisible(true);
        this.setNameTagAlwaysVisible(true);
        this.setCanClimb(true);

        this.server.getLogger().info(this.getServer().getLanguage().translateString("nukkit.player.logIn",
                TextFormat.AQUA + this.username + TextFormat.WHITE,
                this.ip,
                String.valueOf(this.port),
                String.valueOf(this.id),
                this.level.getName(),
                String.valueOf(NukkitMath.round(this.x, 4)),
                String.valueOf(NukkitMath.round(this.y, 4)),
                String.valueOf(NukkitMath.round(this.z, 4))));

        if (this.isOp()) {
            this.setRemoveFormat(false);
        }

        if (this.gamemode == Player.SPECTATOR) {
            InventoryContentPacket inventoryContentPacket = new InventoryContentPacket();
            inventoryContentPacket.inventoryId = InventoryContentPacket.SPECIAL_CREATIVE;
            this.dataPacket(inventoryContentPacket);
        } else {
            this.inventory.sendCreativeContents();
        }

        this.setEnableClientCommand(true);

        this.forceMovement = this.teleportPosition = this.getPosition();

        this.server.addOnlinePlayer(this);
        this.server.onPlayerCompleteLoginSequence(this);

        ChunkRadiusUpdatedPacket chunkRadiusUpdatePacket = new ChunkRadiusUpdatedPacket();
        chunkRadiusUpdatePacket.radius = this.chunkRadius;
        this.dataPacket(chunkRadiusUpdatePacket);
    }

    @Override
    protected void doFirstSpawn() {
        super.doFirstSpawn();
    }

    protected void forceSendEmptyChunks() {
        int chunkPositionX = this.getFloorX() >> 4;
        int chunkPositionZ = this.getFloorZ() >> 4;
        List<LevelChunkPacket> pkList = new ArrayList<>();
        for (int x = -3; x < 3; x++) {
            for (int z = -3; z < 3; z++) {
                LevelChunkPacket chunk = new LevelChunkPacket();
                chunk.chunkX = chunkPositionX + x;
                chunk.chunkZ = chunkPositionZ + z;
                chunk.data = new byte[0];
                pkList.add(chunk);
            }
        }
        Server.getInstance().batchPackets(new Player[]{this}, pkList.toArray(new DataPacket[0]));
    }

    public boolean transferByDescription(String serverDescription) {
        return this.transfer(this.getSynapseEntry().getClientData().getHashByDescription(serverDescription));
    }

    public boolean transfer(String hash) {
        return this.transfer(hash, true);
    }

    public boolean transfer(String hash, boolean loadScreen) {
        ClientData clients = this.getSynapseEntry().getClientData();
        Entry clientData = clients.clientList.get(hash);

        if (clientData != null) {
            SynapsePlayerTransferEvent event = new SynapsePlayerTransferEvent(this, clientData);
            this.server.getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return false;
            }

            new TransferRunnable(this, hash).run();
            return true;
        }

        return false;
    }

    public boolean transferToLobby() {
        new TransferRunnable(this, "lobby").run();
        return true;
    }

    @Override
    public void handleDataPacket(DataPacket packet) {
        if (!this.isSynapseLogin) {
            super.handleDataPacket(packet);
            return;
        }
        Timing dataPacketTiming = handlePlayerDataPacketTimings.getOrDefault(packet.pid(), TimingsManager.getTiming("SynapseEntry - HandlePlayerDataPacket - " + packet.getClass().getSimpleName()));
        dataPacketTiming.startTiming();

        super.handleDataPacket(packet);

        dataPacketTiming.stopTiming();
        if (!handlePlayerDataPacketTimings.containsKey(packet.pid()))
            handlePlayerDataPacketTimings.put(packet.pid(), dataPacketTiming);

    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (!this.isSynapseLogin) {
            return super.onUpdate(currentTick);
        }
        /*if (this.loggedIn && this.synapseSlowLoginUntil != -1 && System.currentTimeMillis() >= this.synapseSlowLoginUntil) {
            this.processLogin();
        }*/
        return super.onUpdate(currentTick);
    }

    public void setUniqueId(UUID uuid) {
        this.uuid = uuid;
    }

    public void sendCommandData() {
        AvailableCommandsPacket pk = new AvailableCommandsPacket();
        Map<String, CommandDataVersions> data = new HashMap<>();
        for (Command command : this.server.getCommandMap().getCommands().values()) {
            if (!command.testPermissionSilent(this)) {
                continue;
            }
            CommandDataVersions data0 = command.generateCustomCommandData(this);
            data.put(command.getName(), data0);
        }
        pk.commands = data;
        this.dataPacket(pk, true);
    }

    @Override
    public int dataPacket(DataPacket packet, boolean needACK) {
        if (!this.isSynapseLogin) return super.dataPacket(packet, needACK);

        return sendDataPacket(packet, needACK, false);
    }

    @Override
    public int directDataPacket(DataPacket packet, boolean needACK) {
        if (!this.isSynapseLogin) return super.directDataPacket(packet, needACK);

        return sendDataPacket(packet, needACK, true);
    }

    public int sendDataPacket(DataPacket packet, boolean needACK, boolean direct) {
        packet = DataPacketEidReplacer.replace(packet, this.getId(), REPLACE_ID);
        DataPacketSendEvent ev = new DataPacketSendEvent(this, packet);
        this.server.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            return -1;
        }

        if (!packet.isEncoded) {
            packet.encode();
            packet.isEncoded = true;
        }

        return this.interfaz.putPacket(this, packet, needACK, direct);
    }
}
