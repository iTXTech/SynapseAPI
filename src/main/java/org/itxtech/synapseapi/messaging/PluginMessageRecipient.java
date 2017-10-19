package org.itxtech.synapseapi.messaging;

import cn.nukkit.plugin.Plugin;

import java.util.Set;

public interface PluginMessageRecipient {
    void sendPluginMessage(Plugin var1, String var2, byte[] var3);

    Set<String> getListeningPluginChannels();
}

