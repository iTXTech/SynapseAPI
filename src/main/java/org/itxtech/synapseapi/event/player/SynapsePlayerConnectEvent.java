package org.itxtech.synapseapi.event.player;

import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import org.itxtech.synapseapi.SynapsePlayer;

/**
 * Created by boybook on 16/6/25.
 */
public class SynapsePlayerConnectEvent extends SynapsePlayerEvent implements Cancellable{

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    private boolean firstTime;

    public SynapsePlayerConnectEvent(SynapsePlayer player) {
        this(player, true);
    }

    public SynapsePlayerConnectEvent(SynapsePlayer player, boolean firstTime) {
        super(player);
        this.firstTime = firstTime;
    }

    public boolean isFirstTime() {
        return firstTime;
    }
}
