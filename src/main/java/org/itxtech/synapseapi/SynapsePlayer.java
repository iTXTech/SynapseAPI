package org.itxtech.synapseapi;

import cn.nukkit.AdventureSettings;
import cn.nukkit.Player;
import cn.nukkit.PlayerFood;
import cn.nukkit.Server;
import cn.nukkit.block.BlockAir;
import cn.nukkit.command.Command;
import cn.nukkit.command.data.CommandDataVersions;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.item.EntityVehicle;
import cn.nukkit.entity.item.EntityXPOrb;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.PlayerInteractEntityEvent;
import cn.nukkit.event.player.PlayerKickEvent;
import cn.nukkit.event.player.PlayerLoginEvent;
import cn.nukkit.event.player.PlayerMouseOverEntityEvent;
import cn.nukkit.event.server.DataPacketReceiveEvent;
import cn.nukkit.event.server.DataPacketSendEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.*;
import cn.nukkit.utils.TextFormat;
import co.aikar.timings.Timing;
import co.aikar.timings.Timings;
import co.aikar.timings.TimingsManager;
import com.google.gson.Gson;
import org.itxtech.synapseapi.event.player.SynapsePlayerConnectEvent;
import org.itxtech.synapseapi.event.player.SynapsePlayerTransferEvent;
import org.itxtech.synapseapi.network.protocol.spp.FastPlayerListPacket;
import org.itxtech.synapseapi.network.protocol.spp.PlayerLoginPacket;
import org.itxtech.synapseapi.runnable.SendChangeDimensionRunnable;
import org.itxtech.synapseapi.runnable.SendPlayerSpawnRunnable;
import org.itxtech.synapseapi.runnable.TransferRunnable;
import org.itxtech.synapseapi.utils.ClientData;
import org.itxtech.synapseapi.utils.ClientData.Entry;
import org.itxtech.synapseapi.utils.DataPacketEidReplacer;

import java.util.*;

/**
 * Created by boybook on 16/6/24.
 */
public class SynapsePlayer extends Player {

    private static final Map<Byte, Timing> handlePlayerDataPacketTimings = new HashMap<>();
    public boolean isSynapseLogin = false;
    protected SynapseEntry synapseEntry;
    private boolean isFirstTimeLogin = false;
    private long synapseSlowLoginUntil = 0;

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

        CompoundTag nbt = this.server.getOfflinePlayerData(this.username);
        if (nbt == null) {
            this.close(this.getLeaveMessage(), "Invalid data");

            return;
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

        this.adventureSettings = (new AdventureSettings(this))
                .set(AdventureSettings.Type.WORLD_IMMUTABLE, !this.isAdventure())
                .set(AdventureSettings.Type.AUTO_JUMP, true)
                .set(AdventureSettings.Type.ALLOW_FLIGHT, this.isCreative())
                .set(AdventureSettings.Type.NO_CLIP, this.isSpectator());

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
            this.server.saveOfflinePlayerData(this.username, nbt, true);
        }

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

        if (this.isFirstTimeLogin) {
            System.out.println("Infoing"); //ToDo <-- here, it stops working, packet is not sent to MCPE
            ResourcePacksInfoPacket infoPacket = new ResourcePacksInfoPacket();
            infoPacket.resourcePackEntries = this.server.getResourcePackManager().getResourceStack();
            infoPacket.mustAccept = this.server.getForceResources();
            this.dataPacket(infoPacket);
        }
        else this.completeLoginSequence();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void completeLoginSequence(){
        System.out.println("Login sequence! Yay");
        PlayerLoginEvent ev;
        this.server.getPluginManager().callEvent(ev = new PlayerLoginEvent(this, "Plugin reason"));
        if (ev.isCancelled()) {
            this.close(this.getLeaveMessage(), ev.getKickMessage());

            return;
        }

        this.server.addOnlinePlayer(this);
        this.loggedIn = true;

        if (this.isCreative()) {
            this.inventory.setHeldItemSlot(0);
        } else {
            this.inventory.setHeldItemSlot(this.inventory.getHotbarSlotIndex(0));
        }

        if (this.isSpectator()) this.keepMovement = true;

        if (this.spawnPosition == null && this.namedTag.contains("SpawnLevel") && (level = this.server.getLevelByName(this.namedTag.getString("SpawnLevel"))) != null) {
            this.spawnPosition = new Position(this.namedTag.getInt("SpawnX"), this.namedTag.getInt("SpawnY"), this.namedTag.getInt("SpawnZ"), level);
        }

        Position spawnPosition = this.getSpawn();
        if (this.isFirstTimeLogin) {
            StartGamePacket startGamePacket = new StartGamePacket();
            startGamePacket.entityUniqueId = Long.MAX_VALUE;
            startGamePacket.entityRuntimeId = Long.MAX_VALUE;
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
            this.dataPacket(startGamePacket);
        } else {
            AdventureSettings newSettings = this.getAdventureSettings().clone(this);
            newSettings.set(AdventureSettings.Type.WORLD_IMMUTABLE, gamemode != 3);
            newSettings.set(AdventureSettings.Type.ALLOW_FLIGHT, (gamemode & 0x01) > 0);
            newSettings.set(AdventureSettings.Type.NO_CLIP, gamemode == 0x03);
            newSettings.set(AdventureSettings.Type.FLYING, gamemode == 0x03);
            if (this.isSpectator()) {
                this.keepMovement = true;
            } else {
                this.keepMovement = false;
            }
            SetPlayerGameTypePacket pk = new SetPlayerGameTypePacket();
            pk.gamemode = getClientFriendlyGamemode(gamemode);
            this.dataPacket(pk);
        }

        SetTimePacket setTimePacket = new SetTimePacket();
        setTimePacket.time = this.level.getTime();
        this.dataPacket(setTimePacket);

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

        this.server.sendFullPlayerListData(this);

        this.forceMovement = this.teleportPosition = this.getPosition();

        this.server.onPlayerLogin(this);

        ChunkRadiusUpdatedPacket chunkRadiusUpdatePacket = new ChunkRadiusUpdatedPacket();
        chunkRadiusUpdatePacket.radius = this.chunkRadius;
        this.dataPacket(chunkRadiusUpdatePacket);
    }

    @Override
    protected void doFirstSpawn() {
        super.doFirstSpawn();
        this.setMovementSpeed(DEFAULT_SPEED);
        this.sendAttributes();
    }

    protected void forceSendEmptyChunks() {
        int chunkPositionX = this.getFloorX() >> 4;
        int chunkPositionZ = this.getFloorZ() >> 4;
        List<FullChunkDataPacket> pkList = new ArrayList<>();
        for (int x = -3; x < 3; x++) {
            for (int z = -3; z < 3; z++) {
                FullChunkDataPacket chunk = new FullChunkDataPacket();
                chunk.chunkX = chunkPositionX + x;
                chunk.chunkZ = chunkPositionZ + z;
                chunk.data = new byte[0];
                pkList.add(chunk);
            }
        }
        Server.getInstance().batchPackets(new Player[]{this}, pkList.stream().toArray(DataPacket[]::new));
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

            for (Entity entity : this.getLevel().getEntities()) {
                if (entity.getViewers().containsKey(this.getLoaderId())) {
                    entity.despawnFrom(this);
                }
            }
            if (loadScreen && SynapseAPI.getInstance().isUseLoadingScreen()) {
                //Load Screen
                this.getServer().getScheduler().scheduleDelayedTask(new SendChangeDimensionRunnable(this, 1), 1);
                this.forceSendEmptyChunks();
                this.getServer().getScheduler().scheduleDelayedTask(new SendPlayerSpawnRunnable(this), 10);
                this.getServer().getScheduler().scheduleDelayedTask(new SendChangeDimensionRunnable(this, 0), 12);
                this.getServer().getScheduler().scheduleDelayedTask(new TransferRunnable(this, hash), 14);
            } else {
                new TransferRunnable(this, hash).run();
            }
            return true;
        }
        return false;
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
        if (this.loggedIn && this.synapseSlowLoginUntil != -1 && System.currentTimeMillis() >= this.synapseSlowLoginUntil) {
            this.processLogin();
        }
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
        pk.commands = new Gson().toJson(data);
        this.dataPacket(pk, true);
    }

    @Override
    public int dataPacket(DataPacket packet, boolean needACK) {
        if (!this.isSynapseLogin) return super.dataPacket(packet, needACK);
        if (!this.isFirstTimeLogin && packet instanceof ResourcePacksInfoPacket) {
            this.processLogin();
            return -1;
        }
        packet = new DataPacketEidReplacer(packet).replace(this.getId(), Long.MAX_VALUE);
        DataPacketSendEvent ev = new DataPacketSendEvent(this, packet);
        this.server.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            return -1;
        }
        packet.encode();
        //this.server.getLogger().warning("Send to player: " + Binary.bytesToHexString(new byte[]{packet.getBuffer()[0]}) + "  len: " + packet.getBuffer().length);
        return this.interfaz.putPacket(this, packet, needACK);
    }

    @Override
    public int directDataPacket(DataPacket packet, boolean needACK) {
        if (!this.isSynapseLogin) return super.directDataPacket(packet, needACK);
        packet = new DataPacketEidReplacer(packet).replace(this.getId(), Long.MAX_VALUE);
        DataPacketSendEvent ev = new DataPacketSendEvent(this, packet);
        this.server.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            return -1;
        }
        return this.interfaz.putPacket(this, packet, needACK, true);
    }

    public void sendFullPlayerListData(boolean self) {
        FastPlayerListPacket pk = new FastPlayerListPacket();
        pk.sendTo = this.getUniqueId();
        pk.type = PlayerListPacket.TYPE_ADD;
        List<FastPlayerListPacket.Entry> entries = new ArrayList<>();
        for (Player p : this.getServer().getOnlinePlayers().values()) {
            if (!self && p == this) {
                continue;
            }
            entries.add(new FastPlayerListPacket.Entry(p.getUniqueId(), p.getId(), p.getDisplayName()));
        }

        pk.entries = entries.stream().toArray(FastPlayerListPacket.Entry[]::new);

        this.getSynapseEntry().sendDataPacket(pk);
    }

}
