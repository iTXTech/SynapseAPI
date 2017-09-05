package org.itxtech.synapseapi.messaging;

public interface PluginMessageListener {
    void onPluginMessageReceived(String channel, byte[] message);
}

