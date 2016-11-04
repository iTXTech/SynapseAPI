package org.itxtech.synapseapi.network.protocol.spp;

import java.util.UUID;

/**
 * Created by boybook on 16/6/24.
 */
public class PlayerLogoutPacket extends SynapseDataPacket {

    public static final byte NETWORK_ID = SynapseInfo.PLAYER_LOGOUT_PACKET;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public UUID uuid;
    public String reason;

    @Override
    public void encode(){
        this.reset();
        this.putUUID(this.uuid);
        this.putString(this.reason);
    }
    
    @Override
    public void decode(){
        this.uuid = this.getUUID();
        this.reason = this.getString();
    }
}
