package org.itxtech.synapseapi.messaging;

import org.itxtech.synapseapi.SynapseEntry;

public interface PluginMessageListener {
    void onPluginMessageReceived(SynapseEntry entry, String channel, byte[] message);
}

