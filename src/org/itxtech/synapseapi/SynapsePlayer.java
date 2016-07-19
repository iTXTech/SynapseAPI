package org.itxtech.synapseapi;

import cn.nukkit.Player;
import cn.nukkit.PlayerFood;
import cn.nukkit.Server;
import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.player.PlayerLoginEvent;
import cn.nukkit.item.Item;
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
import org.itxtech.synapseapi.network.protocol.mcpe.SetHealthPacket;
import org.itxtech.synapseapi.network.protocol.spp.PlayerLoginPacket;
import org.itxtech.synapseapi.network.protocol.spp.TransferPacket;
import org.itxtech.synapseapi.utils.ClientData;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

/**
 * Created by boybook on 16/6/24.
 */
public class SynapsePlayer extends Player {

    private boolean isFirstTimeLogin = false;
    private long lastPacketTime = System.currentTimeMillis();
    public boolean isSynapseLogin = false;

    private long needSlowLogin = 0;

    public SynapsePlayer(SourceInterface interfaz, Long clientID, String ip, int port) {
        super(interfaz, clientID, ip, port);
    }

    public void handleLoginPacket(PlayerLoginPacket packet){
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

    @Override
    protected void processLogin() {
        if (!this.isSynapseLogin) {      
            super.processLogin();
            return;
        }
        if (needSlowLogin == 0) {
            if (!this.server.isWhitelisted((this.getName()).toLowerCase())) {
                this.close(this.getLeaveMessage(), "Server is white-listed");

                return;
            } else if (this.server.getNameBans().isBanned(this.getName().toLowerCase()) || this.server.getIPBans().isBanned(this.getAddress())) {
                this.close(this.getLeaveMessage(), "You are banned");

                return;
            }

            if (this.hasPermission(Server.BROADCAST_CHANNEL_USERS)) {
                this.server.getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_USERS, this);
            }
            if (this.hasPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE)) {
                this.server.getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE, this);
            }

            for (Player p : new ArrayList<>(this.server.getOnlinePlayers().values())) {
                if (p != this && p.getName() != null && this.getName() != null && Objects.equals(p.getName().toLowerCase(), this.getName().toLowerCase())) {
                    if (!p.kick("logged in from another location")) {
                        this.close(this.getLeaveMessage(), "Logged in from another location");

                        return;
                    }
                } else if (p.loggedIn && this.getUniqueId().equals(p.getUniqueId())) {
                    if (!p.kick("logged in from another location")) {
                        this.close(this.getLeaveMessage(), "Logged in from another location");

                        return;
                    }
                }
            }

            this.setNameTag(this.username);

            CompoundTag nbt = this.server.getOfflinePlayerData(this.username);
            if (nbt == null) {
                this.close(this.getLeaveMessage(), "Invalid data");

                return;
            }

            this.playedBefore = (nbt.getLong("lastPlayed") - nbt.getLong("firstPlayed")) > 1;

            nbt.putString("NameTag", this.username);

            int exp = nbt.getInt("EXP");
            int expLevel = nbt.getInt("expLevel");
            this.setExperience(exp, expLevel);

            this.gamemode = nbt.getInt("playerGameType") & 0x03;
            if (this.server.getForceGamemode()) {
                this.gamemode = this.server.getGamemode();
                nbt.putInt("playerGameType", this.gamemode);
            }

            this.allowFlight = this.isCreative();

            Level level;
            if ((level = this.server.getLevelByName(nbt.getString("Level"))) == null) {
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

            this.server.addOnlinePlayer(this, false);

            PlayerLoginEvent ev;
            this.server.getPluginManager().callEvent(ev = new PlayerLoginEvent(this, "Plugin reason"));
            if (ev.isCancelled()) {
                this.close(this.getLeaveMessage(), ev.getKickMessage());

                return;
            }

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

            if (this.spawnPosition == null && this.namedTag.contains("SpawnLevel") && (level = this.server.getLevelByName(this.namedTag.getString("SpawnLevel"))) != null) {
                this.spawnPosition = new Position(this.namedTag.getInt("SpawnX"), this.namedTag.getInt("SpawnY"), this.namedTag.getInt("SpawnZ"), level);
            }

            Position spawnPosition = this.getSpawn();

            if (this.isFirstTimeLogin) {
                StartGamePacket startGamePacket = new StartGamePacket();
                startGamePacket.seed = -1;
                startGamePacket.dimension = (byte) (getLevel().getDimension() & 0xFF);
                startGamePacket.x = (float) this.x;
                startGamePacket.y = (float) this.y;
                startGamePacket.z = (float) this.z;
                startGamePacket.spawnX = (int) spawnPosition.x;
                startGamePacket.spawnY = (int) spawnPosition.y;
                startGamePacket.spawnZ = (int) spawnPosition.z;
                startGamePacket.generator = 1; //0 old, 1 infinite, 2 flat
                startGamePacket.gamemode = this.gamemode & 0x01;
                startGamePacket.eid = 0; //Always use EntityID as zero for the actual player
                startGamePacket.b1 = true;
                startGamePacket.b2 = true;
                startGamePacket.b3 = false;
                startGamePacket.unknownstr = "";
                this.dataPacket(startGamePacket);
            } else {
                /*
                ChangeDimensionPacket changeDimensionPacket = new ChangeDimensionPacket();
                changeDimensionPacket.dimension = 1;
                changeDimensionPacket.x = 0;
                changeDimensionPacket.y = 0;
                changeDimensionPacket.z = 0;
                this.dataPacket(changeDimensionPacket);
                FullChunkDataPacket fullChunkDataPacket = LevelUtil.getEmptyChunkFullPacket(0, 0);
                this.dataPacket(fullChunkDataPacket);
                FullChunkDataPacket fullChunkDataPacket1 = LevelUtil.getEmptyChunkFullPacket(0, -1);
                this.dataPacket(fullChunkDataPacket1);
                FullChunkDataPacket fullChunkDataPacket2 = LevelUtil.getEmptyChunkFullPacket(-1, 0);
                this.dataPacket(fullChunkDataPacket2);
                FullChunkDataPacket fullChunkDataPacket3 = LevelUtil.getEmptyChunkFullPacket(-1, -1);
                this.dataPacket(fullChunkDataPacket3);
                RespawnPacket respawnPacket = new RespawnPacket();
                respawnPacket.x = 0;
                respawnPacket.y = 0;
                respawnPacket.z = 0;
                //this.dataPacket(respawnPacket);
                PlayStatusPacket statusPacket1 = new PlayStatusPacket();
                statusPacket1.status = PlayStatusPacket.PLAYER_SPAWN;
                this.dataPacket(statusPacket1);
                ChangeDimensionPacket changeDimensionPacket1 = new ChangeDimensionPacket();
                changeDimensionPacket1.dimension = 0;
                changeDimensionPacket1.x = (float)this.getX();
                changeDimensionPacket1.y = (float)this.getY();
                changeDimensionPacket1.z = (float)this.getZ();
                this.dataPacket(changeDimensionPacket1);
                RespawnPacket respawnPacket1 = new RespawnPacket();
                respawnPacket1.x = (float)this.getX();
                respawnPacket1.y = (float)this.getY();
                respawnPacket1.z = (float)this.getZ();
                this.dataPacket(respawnPacket1);
                */
            }

            SetTimePacket setTimePacket = new SetTimePacket();
            setTimePacket.time = this.level.getTime();
            setTimePacket.started = !this.level.stopTime;
            this.dataPacket(setTimePacket);

            SetSpawnPositionPacket setSpawnPositionPacket = new SetSpawnPositionPacket();
            setSpawnPositionPacket.x = (int) spawnPosition.x;
            setSpawnPositionPacket.y = (int) spawnPosition.y;
            setSpawnPositionPacket.z = (int) spawnPosition.z;
            this.dataPacket(setSpawnPositionPacket);
        }

        if (this.isFirstTimeLogin) {
            if (this.needSlowLogin == 0) {
                return;
            } else {
                if (System.currentTimeMillis() - this.needSlowLogin < 100) {
                    return;
                }
            }
        }

        if (this.isFirstTimeLogin) {
            SetHealthPacket pk = new SetHealthPacket();
            pk.health = this.getHealth();
            this.dataPacket(pk);
        } else {
            UpdateAttributesPacket updateAttributesPacket = new UpdateAttributesPacket();
            updateAttributesPacket.entityId = 0;
            updateAttributesPacket.entries = new Attribute[]{
                    Attribute.getAttribute(Attribute.MAX_HEALTH).setMaxValue(this.getMaxHealth()).setValue(this.getHealth()),
                    Attribute.getAttribute(Attribute.MOVEMENT_SPEED).setValue(this.getMovementSpeed())
            };
            this.dataPacket(updateAttributesPacket);
        }

        SetDifficultyPacket setDifficultyPacket = new SetDifficultyPacket();
        setDifficultyPacket.difficulty = this.server.getDifficulty();
        this.dataPacket(setDifficultyPacket);

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

        SetPlayerGameTypePacket pk = new SetPlayerGameTypePacket();
        pk.gamemode = this.gamemode & 0x01;
        this.dataPacket(pk);
        this.sendSettings();

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

        this.forceMovement = this.teleportPosition = this.getPosition();
        this.needSlowLogin = 0;

        this.server.onPlayerLogin(this);

    }

    public void transfer(String hash){
        ClientData clients = SynapseAPI.getInstance().getClientData();
        if(clients.clientList.containsKey(hash)){
            for (Entity entity: this.getLevel().getEntities()){
                if(entity.getViewers().containsKey(this.getLoaderId())){
                    entity.despawnFrom(this);
                }
            }
            TransferPacket pk = new TransferPacket();
            pk.uuid = this.uuid;
            pk.clientHash = hash;
            SynapseAPI.getInstance().sendDataPacket(pk);
        }
    }

    @Override
    public void handleDataPacket(DataPacket packet){
        if (!this.isSynapseLogin) {      
            super.handleDataPacket(packet);
            return;
        }
        this.lastPacketTime = System.currentTimeMillis();
        super.handleDataPacket(packet);
    }

    @Override
    public boolean onUpdate(int currentTick){
        if (!this.isSynapseLogin) {
            return super.onUpdate(currentTick);
        }
        if (this.isFirstTimeLogin && this.needSlowLogin != 0) this.processLogin();
        if((System.currentTimeMillis() - this.lastPacketTime) >= 5 * 60 * 1000){//5 minutes time out
            this.close("", "timeout");
            return false;
        }
        return super.onUpdate(currentTick);
    }

    public void setUniqueId(UUID uuid){
        this.uuid = uuid;
    }

    @Override
    public int dataPacket(DataPacket packet, boolean needACK){
        if (!this.isSynapseLogin) return super.dataPacket(packet, needACK);
        return this.interfaz.putPacket(this, packet, needACK);
    }

    @Override
    public int directDataPacket(DataPacket packet, boolean needACK){
        if (!this.isSynapseLogin) return super.directDataPacket(packet, needACK);
        return this.interfaz.putPacket(this, packet, needACK, true);
    }

}
