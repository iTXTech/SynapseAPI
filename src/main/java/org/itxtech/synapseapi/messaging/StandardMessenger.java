package org.itxtech.synapseapi.messaging;

import cn.nukkit.plugin.Plugin;
import cn.nukkit.utils.MainLogger;
import com.google.common.collect.ImmutableSet;
import org.itxtech.synapseapi.SynapseEntry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StandardMessenger implements Messenger {

    private final Map<String, Set<PluginMessageListenerRegistration>> incomingByChannel = new HashMap<>();
    private final Map<Plugin, Set<PluginMessageListenerRegistration>> incomingByPlugin = new HashMap<>();
    private final Map<String, Set<Plugin>> outgoingByChannel = new HashMap<>();
    private final Map<Plugin, Set<String>> outgoingByPlugin = new HashMap<>();
    private final Object incomingLock = new Object();
    private final Object outgoingLock = new Object();

    public static void validateChannel(String channel) {
        if (channel == null) {
            throw new IllegalArgumentException("Channel cannot be null");
        }
        if (channel.length() > 20) {
            throw new ChannelNameTooLongException(channel);
        }
    }

    public static void validatePluginMessage(Messenger messenger, Plugin source, String channel, byte[] message) {
        if (messenger == null) {
            throw new IllegalArgumentException("Messenger cannot be null");
        }
        if (source == null) {
            throw new IllegalArgumentException("Plugin source cannot be null");
        }
        if (!source.isEnabled()) {
            throw new IllegalArgumentException("Plugin must be enabled to send messages");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        if (!messenger.isOutgoingChannelRegistered(source, channel)) {
            throw new ChannelNotRegisteredException(channel);
        }
        if (message.length > 32766) {
            throw new MessageTooLargeException(message);
        }
        StandardMessenger.validateChannel(channel);
    }

    private void addToOutgoing(Plugin plugin, String channel) {
        synchronized (this.outgoingLock) {
            Set<Plugin> plugins = this.outgoingByChannel.get(channel);
            Set<String> channels = this.outgoingByPlugin.get(plugin);
            if (plugins == null) {
                plugins = new HashSet<>();
                this.outgoingByChannel.put(channel, plugins);
            }
            if (channels == null) {
                channels = new HashSet<>();
                this.outgoingByPlugin.put(plugin, channels);
            }
            plugins.add(plugin);
            channels.add(channel);
        }
    }

    private void removeFromOutgoing(Plugin plugin, String channel) {
        synchronized (this.outgoingLock) {
            Set<Plugin> plugins = this.outgoingByChannel.get(channel);
            Set<String> channels = this.outgoingByPlugin.get(plugin);
            if (plugins != null) {
                plugins.remove(plugin);
                if (plugins.isEmpty()) {
                    this.outgoingByChannel.remove(channel);
                }
            }
            if (channels != null) {
                channels.remove(channel);
                if (channels.isEmpty()) {
                    this.outgoingByChannel.remove(channel);
                }
            }
        }
    }

    private void removeFromOutgoing(Plugin plugin) {
        synchronized (this.outgoingLock) {
            Set<String> channels = this.outgoingByPlugin.get(plugin);
            if (channels != null) {
                String[] toRemove = channels.toArray(new String[0]);
                this.outgoingByPlugin.remove(plugin);

                int n = toRemove.length;
                int n2 = 0;
                while (n2 < n) {
                    String channel = toRemove[n2];
                    this.removeFromOutgoing(plugin, channel);
                    ++n2;
                }
            }
        }
    }

    private void addToIncoming(PluginMessageListenerRegistration registration) {
        synchronized (this.incomingLock) {
            Set<PluginMessageListenerRegistration> registrations = this.incomingByChannel.get(registration.getChannel());
            if (registrations == null) {
                registrations = new HashSet<>();
                this.incomingByChannel.put(registration.getChannel(), registrations);
            } else if (registrations.contains(registration)) {
                throw new IllegalArgumentException("This registration already exists");
            }
            registrations.add(registration);
            registrations = this.incomingByPlugin.get(registration.getPlugin());
            if (registrations == null) {
                registrations = new HashSet<>();
                this.incomingByPlugin.put(registration.getPlugin(), registrations);
            } else if (registrations.contains(registration)) {
                throw new IllegalArgumentException("This registration already exists");
            }
            registrations.add(registration);
        }
    }

    private void removeFromIncoming(PluginMessageListenerRegistration registration) {
        synchronized (this.incomingLock) {
            Set<PluginMessageListenerRegistration> registrations = this.incomingByChannel.get(registration.getChannel());
            if (registrations != null) {
                registrations.remove(registration);
                if (registrations.isEmpty()) {
                    this.incomingByChannel.remove(registration.getChannel());
                }
            }
            if ((registrations = this.incomingByPlugin.get(registration.getPlugin())) != null) {
                registrations.remove(registration);
                if (registrations.isEmpty()) {
                    this.incomingByPlugin.remove(registration.getPlugin());
                }
            }
        }
    }

    private void removeFromIncoming(Plugin plugin, String channel) {
        synchronized (this.incomingLock) {
            Set<PluginMessageListenerRegistration> registrations = this.incomingByPlugin.get(plugin);
            if (registrations != null) {
                PluginMessageListenerRegistration[] toRemove = registrations.toArray(new PluginMessageListenerRegistration[0]);
                int n = toRemove.length;
                int n2 = 0;
                while (n2 < n) {
                    PluginMessageListenerRegistration registration = toRemove[n2];
                    if (registration.getChannel().equals(channel)) {
                        this.removeFromIncoming(registration);
                    }
                    ++n2;
                }
            }
        }
    }

    private void removeFromIncoming(Plugin plugin) {
        synchronized (this.incomingLock) {
            Set<PluginMessageListenerRegistration> registrations = this.incomingByPlugin.get(plugin);
            if (registrations != null) {
                PluginMessageListenerRegistration[] toRemove = registrations.toArray(new PluginMessageListenerRegistration[0]);
                this.incomingByPlugin.remove(plugin);

                int n = toRemove.length;
                int n2 = 0;
                while (n2 < n) {
                    PluginMessageListenerRegistration registration = toRemove[n2];
                    this.removeFromIncoming(registration);
                    ++n2;
                }
            }
        }
    }

    @Override
    public boolean isReservedChannel(String channel) {
        StandardMessenger.validateChannel(channel);
        return false;
    }

    @Override
    public void registerOutgoingPluginChannel(Plugin plugin, String channel) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        StandardMessenger.validateChannel(channel);
        if (this.isReservedChannel(channel)) {
            throw new ReservedChannelException(channel);
        }
        this.addToOutgoing(plugin, channel);
    }

    @Override
    public void unregisterOutgoingPluginChannel(Plugin plugin, String channel) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        StandardMessenger.validateChannel(channel);
        this.removeFromOutgoing(plugin, channel);
    }

    @Override
    public void unregisterOutgoingPluginChannel(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        this.removeFromOutgoing(plugin);
    }

    @Override
    public PluginMessageListenerRegistration registerIncomingPluginChannel(Plugin plugin, String channel, PluginMessageListener listener) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        StandardMessenger.validateChannel(channel);
        if (this.isReservedChannel(channel)) {
            throw new ReservedChannelException(channel);
        }
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        PluginMessageListenerRegistration result = new PluginMessageListenerRegistration(this, plugin, channel, listener);
        this.addToIncoming(result);
        return result;
    }

    @Override
    public void unregisterIncomingPluginChannel(Plugin plugin, String channel, PluginMessageListener listener) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        StandardMessenger.validateChannel(channel);
        this.removeFromIncoming(new PluginMessageListenerRegistration(this, plugin, channel, listener));
    }

    @Override
    public void unregisterIncomingPluginChannel(Plugin plugin, String channel) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        StandardMessenger.validateChannel(channel);
        this.removeFromIncoming(plugin, channel);
    }

    @Override
    public void unregisterIncomingPluginChannel(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        this.removeFromIncoming(plugin);
    }

    @Override
    public Set<String> getOutgoingChannels() {
        synchronized (this.outgoingLock) {
            Set<String> keys = this.outgoingByChannel.keySet();
            return ImmutableSet.copyOf(keys);
        }
    }

    @Override
    public Set<String> getOutgoingChannels(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }

        synchronized (this.outgoingLock) {
            Set<String> channels = this.outgoingByPlugin.get(plugin);
            if (channels != null) {
                return ImmutableSet.copyOf(channels);
            }
            return ImmutableSet.of();
        }
    }

    @Override
    public Set<String> getIncomingChannels() {
        synchronized (this.incomingLock) {
            Set<String> keys = this.incomingByChannel.keySet();
            return ImmutableSet.copyOf(keys);
        }
    }

    @Override
    public Set<String> getIncomingChannels(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }

        synchronized (this.incomingLock) {
            Set<PluginMessageListenerRegistration> registrations = this.incomingByPlugin.get(plugin);
            if (registrations != null) {
                ImmutableSet.Builder builder = ImmutableSet.builder();
                for (PluginMessageListenerRegistration registration : registrations) {
                    builder.add(registration.getChannel());
                }
                return builder.build();
            }
            return ImmutableSet.of();
        }
    }

    @Override
    public Set<PluginMessageListenerRegistration> getIncomingChannelRegistrations(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }

        synchronized (this.incomingLock) {
            Set<PluginMessageListenerRegistration> registrations = this.incomingByPlugin.get(plugin);
            if (registrations != null) {
                return ImmutableSet.copyOf(registrations);
            }
            return ImmutableSet.of();
        }
    }

    @Override
    public Set<PluginMessageListenerRegistration> getIncomingChannelRegistrations(String channel) {
        StandardMessenger.validateChannel(channel);

        synchronized (this.incomingLock) {
            Set<PluginMessageListenerRegistration> registrations = this.incomingByChannel.get(channel);
            if (registrations != null) {
                return ImmutableSet.copyOf(registrations);
            }
            return ImmutableSet.of();
        }
    }

    @Override
    public Set<PluginMessageListenerRegistration> getIncomingChannelRegistrations(Plugin plugin, String channel) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        StandardMessenger.validateChannel(channel);

        synchronized (this.incomingLock) {
            Set<PluginMessageListenerRegistration> registrations = this.incomingByPlugin.get(plugin);
            if (registrations != null) {
                ImmutableSet.Builder builder = ImmutableSet.builder();
                for (PluginMessageListenerRegistration registration : registrations) {
                    if (!registration.getChannel().equals(channel)) continue;
                    builder.add(registration);
                }
                return builder.build();
            }
            return ImmutableSet.of();
        }
    }

    @Override
    public boolean isRegistrationValid(PluginMessageListenerRegistration registration) {
        if (registration == null) {
            throw new IllegalArgumentException("Registration cannot be null");
        }

        synchronized (this.incomingLock) {
            Set<PluginMessageListenerRegistration> registrations = this.incomingByPlugin.get(registration.getPlugin());

            return registrations != null && registrations.contains(registration);
        }
    }

    @Override
    public boolean isIncomingChannelRegistered(Plugin plugin, String channel) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        StandardMessenger.validateChannel(channel);

        synchronized (this.incomingLock) {
            Set<PluginMessageListenerRegistration> registrations = this.incomingByPlugin.get(plugin);
            if (registrations != null) {
                for (PluginMessageListenerRegistration registration : registrations) {
                    if (!registration.getChannel().equals(channel)) continue;
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public boolean isOutgoingChannelRegistered(Plugin plugin, String channel) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        StandardMessenger.validateChannel(channel);

        synchronized (this.outgoingLock) {
            Set<String> channels = this.outgoingByPlugin.get(plugin);
            return channels != null && channels.contains(channel);
        }
    }

    @Override
    public void dispatchIncomingMessage(SynapseEntry entry, String channel, byte[] message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        StandardMessenger.validateChannel(channel);
        Set<PluginMessageListenerRegistration> registrations = this.getIncomingChannelRegistrations(channel);
        for (PluginMessageListenerRegistration registration : registrations) {
            try {
                registration.getListener().onPluginMessageReceived(entry, channel, message);
            } catch (Throwable t) {
                MainLogger.getLogger().warning("Could not pass incoming plugin message to " + registration.getPlugin(), t);
            }
        }
    }
}

