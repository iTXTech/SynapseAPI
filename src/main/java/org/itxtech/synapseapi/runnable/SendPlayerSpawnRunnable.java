package org.itxtech.synapseapi.runnable;

import cn.nukkit.network.protocol.PlayStatusPacket;
import org.itxtech.synapseapi.SynapsePlayer;

/**
 * Created by boybook on 16/9/26.
 */
public class SendPlayerSpawnRunnable implements Runnable {

    private SynapsePlayer player;

    public SendPlayerSpawnRunnable(SynapsePlayer player) {
        this.player = player;
    }

    @Override
    public void run() {
        PlayStatusPacket statusPacket0 = new PlayStatusPacket();
        statusPacket0.status = PlayStatusPacket.PLAYER_SPAWN;
        this.player.dataPacket(statusPacket0);
    }
}
