package org.itxtech.synapseapi.event.player;

import org.itxtech.synapseapi.SynapsePlayer;
import org.itxtech.synapseapi.event.SynapseEvent;

/**
 * Created by boybook on 16/6/25.
 */
public class SynapsePlayerEvent extends SynapseEvent {

    protected SynapsePlayer player;

    public SynapsePlayerEvent(SynapsePlayer player) {
        this.player = player;
    }

    public SynapsePlayer getPlayer() {
        return player;
    }
}
