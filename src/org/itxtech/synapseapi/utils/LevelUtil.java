package org.itxtech.synapseapi.utils;

import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.mcregion.Chunk;
import cn.nukkit.level.format.mcregion.McRegion;
import cn.nukkit.network.protocol.FullChunkDataPacket;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.BinaryStream;

/**
 * Created by boybook on 16/7/18.
 */
public class LevelUtil {

    private static byte[] cachedEmptyChunk = null;

    public static FullChunkDataPacket getEmptyChunkFullPacket(int x, int z) {
        if (cachedEmptyChunk == null) {
            cachedEmptyChunk = getEmptyChunkPayload();
        }
        FullChunkDataPacket pk = new FullChunkDataPacket();
        pk.chunkX = x;
        pk.chunkZ = z;
        pk.order = FullChunkDataPacket.ORDER_LAYERED;
        pk.data = cachedEmptyChunk;
        return pk;
    }

    public static byte[] getEmptyChunkPayload() {
        BaseFullChunk chunk = new Chunk(McRegion.class);

        byte[] tiles = new byte[0];

        BinaryStream extraData = new BinaryStream();

        BinaryStream stream = new BinaryStream();
        stream.put(chunk.getBlockIdArray());
        stream.put(chunk.getBlockDataArray());
        stream.put(chunk.getBlockSkyLightArray());
        stream.put(chunk.getBlockLightArray());
        for (int height : chunk.getHeightMapArray()) {
            stream.putByte((byte) (height & 0xff));
        }
        for (int color : chunk.getBiomeColorArray()) {
            stream.put(Binary.writeInt(color));
        }
        stream.put(extraData.getBuffer());
        stream.put(tiles);

        return stream.getBuffer();
    }

}
