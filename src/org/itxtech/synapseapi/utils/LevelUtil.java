package org.itxtech.synapseapi.utils;

import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.mcregion.Chunk;
import cn.nukkit.level.format.mcregion.McRegion;
import cn.nukkit.network.protocol.FullChunkDataPacket;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.BinaryStream;

import java.util.Arrays;

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
        byte[] tiles = new byte[0];

        BinaryStream extraData = new BinaryStream();

        BinaryStream stream = new BinaryStream();
        stream.put(new byte['耀']);
        stream.put(new byte[16384]);
        stream.put(new byte[16384]);
        stream.put(new byte[16384]);
        int[] heightMap = new int[256];
        Arrays.fill(heightMap, 127);
        for (int height : heightMap) {
            stream.putByte((byte) (height & 0xff));
        }
        int[] biomeColors = new int[256];
        Arrays.fill(biomeColors, Binary.readInt(new byte[]{(byte)-1, (byte)0, (byte)0, (byte)0}));
        for (int color : biomeColors) {
            stream.put(Binary.writeInt(color));
        }
        stream.put(extraData.getBuffer());
        stream.put(tiles);

        return stream.getBuffer();
    }

}
