package org.itxtech.synapseapi.network.protocol.spp;

import java.util.UUID;

/**
 * Created by boybook on 16/6/24.
 */
public class RedirectPacket extends SynapseDataPacket {

    public static final byte NETWORK_ID = SynapseInfo.REDIRECT_PACKET;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public UUID uuid;
    public boolean direct;
    public byte[] mcpeBuffer;

    @Override
    public void encode(){
        this.reset();
        this.putUUID(this.uuid);
        this.putByte(this.direct ? (byte)1 : (byte)0);
        this.putShort(this.mcpeBuffer.length);
        this.put(this.mcpeBuffer);
    }
    
    @Override
    public void decode(){
        this.uuid = this.getUUID();
        this.direct = this.getByte() == 1;
        this.mcpeBuffer = this.get(this.getShort());
    }
}
