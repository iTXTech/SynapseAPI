package org.itxtech.synapseapi.runnable;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.network.protocol.ChangeDimensionPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.PlayStatusPacket;
import org.itxtech.synapseapi.SynapsePlayer;

/**
 * Created by boybook on 16/9/26.
 */
public class SendChangeDimensionRunnable implements Runnable {

    private SynapsePlayer player;
    private int dimension;

    public SendChangeDimensionRunnable(SynapsePlayer player, int dimension) {
        this.player = player;
        this.dimension = dimension;
    }

    @Override
    public void run() {
        ChangeDimensionPacket changeDimensionPacket1 = new ChangeDimensionPacket();
        changeDimensionPacket1.dimension = this.dimension;
        changeDimensionPacket1.x = (float) player.getX();
        changeDimensionPacket1.y = (float) player.getY();
        changeDimensionPacket1.z = (float) player.getZ();
        player.dataPacket(changeDimensionPacket1);
    }
}
