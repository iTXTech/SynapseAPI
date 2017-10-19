package org.itxtech.synapseapi.event.client;

import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import org.itxtech.synapseapi.SynapseEntry;
import org.itxtech.synapseapi.event.SynapseEvent;
import org.itxtech.synapseapi.network.protocol.spp.SynapseDataPacket;

/**
 * @author CreeperFace
 */
public class SynapseDataPacketReceiveEvent extends SynapseEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final SynapseDataPacket packet;
    private final SynapseEntry entry;

    public SynapseDataPacketReceiveEvent(SynapseEntry entry, SynapseDataPacket packet) {
        this.packet = packet;
        this.entry = entry;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }

    public SynapseDataPacket getPacket() {
        return packet;
    }
}
