package org.itxtech.synapseapi.event.player;

import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import org.itxtech.synapseapi.SynapsePlayer;
import org.itxtech.synapseapi.utils.ClientData.Entry;

/**
 * @author CreeperFace
 */
public class SynapsePlayerTransferEvent extends SynapsePlayerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final Entry clientData;

    public SynapsePlayerTransferEvent(SynapsePlayer player, Entry data) {
        super(player);
        this.clientData = data;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }

    public Entry getClientData() {
        return clientData;
    }
}
