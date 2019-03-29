package org.itxtech.synapseapi.runnable;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.Zlib;
import org.itxtech.synapseapi.network.SynapseInterface;
import org.itxtech.synapseapi.network.protocol.spp.RedirectPacket;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * org.itxtech.synapseapi.runnable
 * ===============
 * author: boybook
 * SynapseAPI Project
 * itxTech
 * ===============
 */
//TODO: add option for compression in synapse
public class SynapseEntryPutPacketThread extends Thread {

    private final SynapseInterface synapseInterface;
    private final Queue<Entry> queue = new LinkedBlockingQueue<>();
    private final Queue<Map<Player, DataPacket[]>> broadcastQueue = new LinkedBlockingQueue<>();

    private final boolean isAutoCompress = true;
    private long tickUseTime = 0;
    private boolean isRunning = true;

    public SynapseEntryPutPacketThread(SynapseInterface synapseInterface) {
        super("SynapseEntryPutPacketThread");
        this.synapseInterface = synapseInterface;
        this.start();
    }

    public void addMainToThread(Player player, DataPacket packet, boolean needACK, boolean immediate) {
        this.queue.offer(new Entry(player, packet, needACK, immediate));
        //Server.getInstance().getLogger().debug("SynapseEntryPutPacketThread Offer: " + packet.getClass().getSimpleName());
    }

    public void addMainToThreadBroadcast(Map<Player, DataPacket[]> packets) {
        if (packets == null || packets.isEmpty()) {
            return;
        }

        this.broadcastQueue.offer(packets);
    }

    public void setRunning(boolean running) {
        isRunning = running;
    }

    @Override
    public void run() {
        while (this.isRunning) {
            long start = System.currentTimeMillis();
            Entry entry;
            while ((entry = queue.poll()) != null) {
                try {
                    if (!entry.player.closed || entry.immediate) { //temporary fix for disconnect packet
                        RedirectPacket pk = new RedirectPacket();
                        pk.uuid = entry.player.getUniqueId();
                        pk.direct = entry.immediate;
                        if (!entry.packet.isEncoded) {
                            entry.packet.encode();
                            entry.packet.isEncoded = true;
                        }
                        if (!(entry.packet instanceof BatchPacket) && this.isAutoCompress) {
                            byte[] buffer = entry.packet.getBuffer();
                            try {
                                buffer = Zlib.deflate(
                                        Binary.appendBytes(Binary.writeUnsignedVarInt(buffer.length), buffer),
                                        Server.getInstance().networkCompressionLevel);
                                pk.mcpeBuffer = Binary.appendBytes((byte) 0xfe, buffer);
                                if (entry.packet.pid() == ProtocolInfo.RESOURCE_PACKS_INFO_PACKET)
                                    Server.getInstance().getLogger().notice("ResourcePacksInfoPacket length=" + buffer.length + " " + Binary.bytesToHexString(buffer));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                            //Server.getInstance().getLogger().notice("S => C  " + entry.packet.getClass().getSimpleName());
                        } else {
                            pk.mcpeBuffer = entry.packet instanceof BatchPacket ? Binary.appendBytes((byte) 0xfe, ((BatchPacket) entry.packet).payload) : entry.packet.getBuffer();
                        }
                        this.synapseInterface.putPacket(pk);
                        //Server.getInstance().getLogger().warning("SynapseEntryPutPacketThread PutPacket");
                    }
                } catch (Exception e) {
                    Server.getInstance().getLogger().alert("Catch exception when Synapse Entry Put Packet: " + e.getMessage());
                    Server.getInstance().getLogger().logException(e);
                }
            }

            Map<Player, DataPacket[]> entry1;
            while ((entry1 = broadcastQueue.poll()) != null) {
                try {
                    for (Map.Entry<Player, DataPacket[]> playerEntry : entry1.entrySet()) {
                        Player player = playerEntry.getKey();
                        DataPacket[] playerPackets = playerEntry.getValue();

                        for (DataPacket packet : playerPackets) {
                            byte[] data = packet.getBuffer();

                            if (data == null) {
                                continue;
                            }

                            RedirectPacket pk = new RedirectPacket();
                            pk.uuid = player.getUniqueId();
                            pk.mcpeBuffer = data;
                            this.synapseInterface.putPacket(pk);
                        }
                    }
                } catch (Exception e) {
                    Server.getInstance().getLogger().alert("Catch exception when Synapse Entry Put Packet: " + e.getMessage());
                    Server.getInstance().getLogger().logException(e);
                }
            }

            tickUseTime = System.currentTimeMillis() - start;
            if (tickUseTime < 10) {
                try {
                    Thread.sleep(10 - tickUseTime);
                } catch (InterruptedException e) {
                    //ignore
                }
            }
        }
    }

    private class Entry {

        private final Player player;
        private final DataPacket packet;
        private final boolean needACK;
        private final boolean immediate;

        public Entry(Player player, DataPacket packet, boolean needACK, boolean immediate) {
            this.player = player;
            this.packet = packet;
            this.needACK = needACK;
            this.immediate = immediate;
        }
    }
}
