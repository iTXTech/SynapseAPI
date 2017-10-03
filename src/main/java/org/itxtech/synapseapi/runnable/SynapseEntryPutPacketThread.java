package org.itxtech.synapseapi.runnable;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.ResourcePacksInfoPacket;
import cn.nukkit.utils.Binary;
import org.itxtech.synapseapi.SynapseAPI;
import org.itxtech.synapseapi.network.SynapseInterface;
import org.itxtech.synapseapi.network.protocol.spp.RedirectPacket;

import java.io.ByteArrayOutputStream;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.Deflater;

/**
 * org.itxtech.synapseapi.runnable
 * ===============
 * author: boybook
 * SynapseAPI Project
 * itxTech
 * ===============
 */
public class SynapseEntryPutPacketThread extends Thread {

    private final SynapseInterface synapseInterface;
    private final Queue<Entry> queue = new LinkedBlockingQueue<>();

    private final Deflater deflater = new Deflater(Server.getInstance().networkCompressionLevel);
    private final byte[] buf = new byte[1024];

    private final boolean isAutoCompress;
    private long tickUseTime = 0;
    private boolean isRunning = true;

    public SynapseEntryPutPacketThread(SynapseInterface synapseInterface) {
        super("SynapseEntryPutPacketThread");
        this.synapseInterface = synapseInterface;
        this.isAutoCompress = SynapseAPI.getInstance().isAutoCompress();
        this.start();
    }

    public void addMainToThread(Player player, DataPacket packet, boolean needACK, boolean immediate) {
        this.queue.offer(new Entry(player, packet, needACK, immediate));
        //Server.getInstance().getLogger().debug("SynapseEntryPutPacketThread Offer: " + packet.getClass().getSimpleName());
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
                    if (!entry.player.closed) {
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
                                buffer = deflate(
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
            tickUseTime = System.currentTimeMillis() - start;
            if (tickUseTime < 10){
                try {
                    Thread.sleep(10 - tickUseTime);
                } catch (InterruptedException e) {
                    //ignore
                }
            }
        }
    }

    private byte[] deflate(byte[] data, int level) throws Exception {
        if (deflater == null) throw new IllegalArgumentException("No deflate for level "+level+" !");
        deflater.reset();
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        while (!deflater.finished()) {
            int i = deflater.deflate(buf);
            bos.write(buf, 0, i);
        }
        //Deflater::end is called the time when the process exits.
        return bos.toByteArray();
    }

    private class Entry {
        private Player player;
        private DataPacket packet;
        private boolean needACK;
        private boolean immediate;
        public Entry(Player player, DataPacket packet, boolean needACK, boolean immediate) {
            this.player = player;
            this.packet = packet;
            this.needACK = needACK;
            this.immediate = immediate;
        }
    }

    public double getTicksPerSecond() {
        long more = this.tickUseTime - 10;
        if (more < 0) return 100;
        return NukkitMath.round(10f / (double)this.tickUseTime, 3) * 100;
    }

}
