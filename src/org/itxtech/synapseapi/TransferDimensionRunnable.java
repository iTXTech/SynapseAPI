package org.itxtech.synapseapi;

import cn.nukkit.network.protocol.ChangeDimensionPacket;
import cn.nukkit.network.protocol.PlayStatusPacket;
import cn.nukkit.network.protocol.RespawnPacket;

/**
 * Created by boybook on 16/7/26.
 */
public class TransferDimensionRunnable implements Runnable {
    
    private SynapsePlayer player;
    
    public TransferDimensionRunnable(SynapsePlayer player) {
        this.player = player;
    }

    public void run() {
        RespawnPacket respawnPacket = new RespawnPacket();
        respawnPacket.x = 0;
        respawnPacket.y = 0;
        respawnPacket.z = 0;
        player.dataPacket(respawnPacket);
        PlayStatusPacket statusPacket1 = new PlayStatusPacket();
        statusPacket1.status = PlayStatusPacket.PLAYER_SPAWN;
        player.dataPacket(statusPacket1);
        ChangeDimensionPacket changeDimensionPacket1 = new ChangeDimensionPacket();
        changeDimensionPacket1.dimension = 0;
        changeDimensionPacket1.x = (float)player.getX();
        changeDimensionPacket1.y = (float)player.getY();
        changeDimensionPacket1.z = (float)player.getZ();
        player.dataPacket(changeDimensionPacket1);
        RespawnPacket respawnPacket1 = new RespawnPacket();
        respawnPacket1.x = (float)player.getX();
        respawnPacket1.y = (float)player.getY();
        respawnPacket1.z = (float)player.getZ();
        player.dataPacket(respawnPacket1);
    }
    
}
