package org.itxtech.synapseapi;

import cn.nukkit.AdventureSettings;
import cn.nukkit.Player;
import cn.nukkit.PlayerFood;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerKickEvent;
import cn.nukkit.event.player.PlayerLoginEvent;
import cn.nukkit.event.player.PlayerRespawnEvent;
import cn.nukkit.event.server.DataPacketSendEvent;
import cn.nukkit.item.Item;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.*;
import cn.nukkit.utils.TextFormat;
import org.itxtech.synapseapi.event.player.SynapsePlayerConnectEvent;
import org.itxtech.synapseapi.network.protocol.spp.FastPlayerListPacket;
import org.itxtech.synapseapi.network.protocol.spp.PlayerLoginPacket;
import org.itxtech.synapseapi.runnable.TransferRunnable;
import org.itxtech.synapseapi.utils.ClientData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by boybook on 16/6/24.
 */
public class SynapsePlayer extends Player {

    private boolean isFirstTimeLogin = false;
    public boolean isSynapseLogin = false;

    protected SynapseEntry synapseEntry;

    private long slowLoginUntil = 0;

    public SynapsePlayer(SourceInterface interfaz, SynapseEntry synapseEntry, Long clientID, String ip, int port) {
        super(interfaz, clientID, ip, port);
        this.synapseEntry = synapseEntry;
        this.isSynapseLogin = this.synapseEntry != null;
    }

    public void handleLoginPacket(PlayerLoginPacket packet) {
        if (!this.isSynapseLogin) {
            super.handleDataPacket(packet);
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

        if (!this.isFirstTimeLogin || this.slowLoginUntil == 0) {
            if (!this.server.isWhitelisted((this.getName()).toLowerCase())) {
                this.kick(PlayerKickEvent.Reason.NOT_WHITELISTED, "Server is white-listed");

                return;
            } else if (this.server.getNameBans().isBanned(this.getName().toLowerCase())) {
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

            this.adventureSettings = new AdventureSettings.Builder(this)
                    .canDestroyBlock(!isAdventure())
                    .autoJump(true)
                    .canFly(isCreative())
                    .noclip(isSpectator())
                    .build();

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

            //todo achievement
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

            PlayStatusPacket statusPacket = new PlayStatusPacket();
            statusPacket.status = PlayStatusPacket.LOGIN_SUCCESS;
            this.dataPacket(statusPacket);

            this.dataPacket(new ResourcePacksInfoPacket());

            if (this.isFirstTimeLogin && this.slowLoginUntil == 0) {
                this.slowLoginUntil = System.currentTimeMillis() + 50;
                return;
            }
        }

        if (this.spawnPosition == null && this.namedTag.contains("SpawnLevel") && (level = this.server.getLevelByName(this.namedTag.getString("SpawnLevel"))) != null) {
            this.spawnPosition = new Position(this.namedTag.getInt("SpawnX"), this.namedTag.getInt("SpawnY"), this.namedTag.getInt("SpawnZ"), level);
        }

        Position spawnPosition = this.getSpawn();

        if (this.isFirstTimeLogin) {
            StartGamePacket startGamePacket = new StartGamePacket();
            startGamePacket.entityUniqueId = 0;
            startGamePacket.entityRuntimeId = 0;
            startGamePacket.x = (float) this.x;
            startGamePacket.y = (float) this.y;
            startGamePacket.z = (float) this.z;
            startGamePacket.seed = -1;
            startGamePacket.dimension = (byte) (this.level.getDimension() & 0xff);
            startGamePacket.gamemode = this.gamemode & 0x01;
            startGamePacket.difficulty = this.server.getDifficulty();
            startGamePacket.spawnX = (int) spawnPosition.x;
            startGamePacket.spawnY = (int) spawnPosition.y;
            startGamePacket.spawnZ = (int) spawnPosition.z;
            startGamePacket.hasAchievementsDisabled = true;
            startGamePacket.dayCycleStopTime = -1;
            startGamePacket.eduMode = false;
            startGamePacket.rainLevel = 0;
            startGamePacket.lightningLevel = 0;
            startGamePacket.commandsEnabled = true;
            startGamePacket.levelId = "";
            startGamePacket.worldName = this.getServer().getNetwork().getName();
            startGamePacket.generator = 1; //0 old, 1 infinite, 2 flat
            this.dataPacket(startGamePacket);
        } else {
            if (SynapseAPI.getInstance().isUseLoadingScreen()) {
                //Load Screen
                ChangeDimensionPacket changeDimensionPacket1 = new ChangeDimensionPacket();
                changeDimensionPacket1.dimension = (byte) this.getLevel().getDimension();
                changeDimensionPacket1.x = (float) this.getX();
                changeDimensionPacket1.y = (float) this.getY();
                changeDimensionPacket1.z = (float) this.getZ();
                this.dataPacket(changeDimensionPacket1);
            }
        }

        SetTimePacket setTimePacket = new SetTimePacket();
        setTimePacket.time = this.level.getTime();
        setTimePacket.started = !this.level.stopTime;
        this.dataPacket(setTimePacket);

        this.setMovementSpeed(DEFAULT_SPEED);

        this.sendAttributes(true);

        this.setNameTagVisible(true);
        this.setNameTagAlwaysVisible(true);

        this.server.getLogger().info(this.getServer().getLanguage().translateString("nukkit.player.logIn", new String[]{
                TextFormat.AQUA + this.username + TextFormat.WHITE,
                this.ip,
                String.valueOf(this.port),
                String.valueOf(this.id),
                this.level.getName(),
                String.valueOf(NukkitMath.round(this.x, 4)),
                String.valueOf(NukkitMath.round(this.y, 4)),
                String.valueOf(NukkitMath.round(this.z, 4))
        }));

        if (this.isOp()) {
            this.setRemoveFormat(false);
        }

        if (this.gamemode == Player.SPECTATOR) {
            ContainerSetContentPacket containerSetContentPacket = new ContainerSetContentPacket();
            containerSetContentPacket.windowid = ContainerSetContentPacket.SPECIAL_CREATIVE;
            this.dataPacket(containerSetContentPacket);
        } else {
            ContainerSetContentPacket containerSetContentPacket = new ContainerSetContentPacket();
            containerSetContentPacket.windowid = ContainerSetContentPacket.SPECIAL_CREATIVE;
            containerSetContentPacket.slots = Item.getCreativeItems().stream().toArray(Item[]::new);
            this.dataPacket(containerSetContentPacket);
        }

        this.setEnableClientCommand(true);

        this.server.sendFullPlayerListData(this);

        if (!this.isFirstTimeLogin) this.chunkRadius = this.viewDistance;
        this.forceMovement = this.teleportPosition = this.getPosition();

        this.server.onPlayerLogin(this);
        this.isFirstTimeLogin = false;
    }

    protected void forceSendEmptyChunks() {
        int chunkPositionX = this.getFloorX() >> 4;
        int chunkPositionZ = this.getFloorZ() >> 4;
        for (int x = -3; x < 3; x++) {
            for (int z = -3; z < 3; z++) {
                FullChunkDataPacket chunk = new FullChunkDataPacket();
                chunk.chunkX = chunkPositionX + x;
                chunk.chunkZ = chunkPositionZ + z;
                chunk.data = new byte[0];
                this.dataPacket(chunk);
            }
        }
    }

    @Override
    protected void doFirstSpawn() {
        this.spawned = true;

        this.server.sendRecipeList(this);
        this.getAdventureSettings().update();

        this.sendPotionEffects(this);
        this.sendData(this);
        this.inventory.sendContents(this);
        this.inventory.sendArmorContents(this);

        SetTimePacket setTimePacket = new SetTimePacket();
        setTimePacket.time = this.level.getTime();
        setTimePacket.started = !this.level.stopTime;
        this.dataPacket(setTimePacket);

        Position pos = this.level.getSafeSpawn(this);

        PlayerRespawnEvent respawnEvent = new PlayerRespawnEvent(this, pos);

        this.server.getPluginManager().callEvent(respawnEvent);

        pos = respawnEvent.getRespawnPosition();

        RespawnPacket respawnPacket = new RespawnPacket();
        respawnPacket.x = (float) pos.x;
        respawnPacket.y = (float) pos.y;
        respawnPacket.z = (float) pos.z;
        this.dataPacket(respawnPacket);

        PlayStatusPacket playStatusPacket = new PlayStatusPacket();
        playStatusPacket.status = PlayStatusPacket.PLAYER_SPAWN;
        this.dataPacket(playStatusPacket);

        PlayerJoinEvent playerJoinEvent = new PlayerJoinEvent(this,
                new TranslationContainer(TextFormat.YELLOW + "%multiplayer.player.joined", new String[]{
                        this.getDisplayName()
                })
        );

        this.server.getPluginManager().callEvent(playerJoinEvent);

        if (playerJoinEvent.getJoinMessage().toString().trim().length() > 0) {
            this.server.broadcastMessage(playerJoinEvent.getJoinMessage());
        }

        this.noDamageTicks = 60;

        for (long index : this.usedChunks.keySet()) {
            int chunkX = Level.getHashX(index);
            int chunkZ = Level.getHashZ(index);
            for (Entity entity : this.level.getChunkEntities(chunkX, chunkZ).values()) {
                if (this != entity && !entity.closed && entity.isAlive()) {
                    entity.spawnTo(this);
                }
            }
        }

        this.sendExperience(this.getExperience());
        this.sendExperienceLevel(this.getExperienceLevel());

        this.teleport(pos, null); // Prevent PlayerTeleportEvent during player spawn

        if (!this.isSpectator()) {
            this.spawnToAll();
        }

        //todo Updater

        if (this.getHealth() <= 0) {
            respawnPacket = new RespawnPacket();
            pos = this.getSpawn();
            respawnPacket.x = (float) pos.x;
            respawnPacket.y = (float) pos.y;
            respawnPacket.z = (float) pos.z;
            this.dataPacket(respawnPacket);
        }

        //Weather
        this.getLevel().sendWeather(this);

        //FoodLevel
        this.getFoodData().sendFoodLevel();
    }

    public void transfer(String hash) {
        ClientData clients = this.getSynapseEntry().getClientData();
        if (clients.clientList.containsKey(hash)) {
            for (Entity entity : this.getLevel().getEntities()) {
                if (entity.getViewers().containsKey(this.getLoaderId())) {
                    entity.despawnFrom(this);
                }
            }
            if (SynapseAPI.getInstance().isUseLoadingScreen()) {
                //Load Screen
                ChangeDimensionPacket changeDimensionPacket = new ChangeDimensionPacket();
                changeDimensionPacket.dimension = (byte) (this.getLevel().getDimension() == 0 ? 1 : 0);
                changeDimensionPacket.x = (float) this.getX();
                changeDimensionPacket.y = (float) this.getY();
                changeDimensionPacket.z = (float) this.getZ();
                this.dataPacket(changeDimensionPacket);
                PlayStatusPacket statusPacket0 = new PlayStatusPacket();
                statusPacket0.status = PlayStatusPacket.PLAYER_SPAWN;
                this.dataPacket(statusPacket0);
                this.forceSendEmptyChunks();
                this.getServer().getScheduler().scheduleDelayedTask(new TransferRunnable(this, hash), 2);
            } else {
                new TransferRunnable(this, hash).run();
            }
        }
    }

    @Override
    public void handleDataPacket(DataPacket packet) {
        if (!this.isSynapseLogin) {
            super.handleDataPacket(packet);
            return;
        }
        super.handleDataPacket(packet);
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (!this.isSynapseLogin) {
            return super.onUpdate(currentTick);
        }
        if (this.loggedIn && this.isFirstTimeLogin && System.currentTimeMillis() >= this.slowLoginUntil) {
            this.processLogin();
        }
        return super.onUpdate(currentTick);
    }

    public void setUniqueId(UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public int dataPacket(DataPacket packet, boolean needACK) {
        if (!this.isSynapseLogin) return super.dataPacket(packet, needACK);
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
