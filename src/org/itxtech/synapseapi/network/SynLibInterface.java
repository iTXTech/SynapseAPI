package org.itxtech.synapseapi.network;

import cn.nukkit.Player;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import org.itxtech.synapseapi.SynapseAPI;
import org.itxtech.synapseapi.network.protocol.spp.RedirectPacket;

/**
 * Created by boybook on 16/6/24.
 */
public class SynLibInterface implements SourceInterface {

    private SynapseInterface synapseInterface;

    public SynLibInterface(SynapseInterface synapseInterface) {
        this.synapseInterface = synapseInterface;
    }

    @Override
    public int getNetworkLatency(Player player) {
        return 0;
    }

    @Override
    public void emergencyShutdown() {
    }

    @Override
    public void setName(String name) {

    }

    @Override
    public Integer putPacket(Player player, DataPacket packet) {
        return this.putPacket(player, packet, false);
    }

    @Override
    public Integer putPacket(Player player, DataPacket packet, boolean needACK) {
        return this.putPacket(player, packet, needACK, false);
    }

    @Override
    public Integer putPacket(Player player, DataPacket packet, boolean needACK, boolean immediate) {
        if(!player.closed) {
            packet.encode();
            RedirectPacket pk = new RedirectPacket();
            pk.uuid = player.getUniqueId();
            pk.direct = immediate;
            pk.mcpeBuffer = packet.getBuffer();
            this.synapseInterface.putPacket(pk);
        }
        return 0;  //这个返回值在nk中并没有被用到
    }

    @Override
    public boolean process() {
        return false;
    }

    @Override
    public void close(Player player, String reason) {

    }

    @Override
    public void close(Player player) {

    }

    @Override
    public void shutdown() {

    }
}
