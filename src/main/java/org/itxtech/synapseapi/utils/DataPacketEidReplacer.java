package org.itxtech.synapseapi.utils;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.EntityData;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.network.protocol.*;
import cn.nukkit.utils.MainLogger;
import com.google.common.collect.Sets;

import java.util.Arrays;
import java.util.Set;

/**
 * DataPacketEidReplacer
 * ===============
 * author: boybook
 * EaseCation Network Project
 * codefuncore
 * ===============
 */
public class DataPacketEidReplacer {

    private static final Set<Integer> replaceMetadata = Sets.newHashSet(Entity.DATA_OWNER_EID, Entity.DATA_LEAD_HOLDER_EID, Entity.DATA_TRADING_PLAYER_EID, Entity.DATA_TARGET_EID);

    public static DataPacket replace(DataPacket pk, long from, long to) {
        DataPacket packet = pk.clone();
        boolean change = true;

        switch (packet.pid()) {
            case AddPlayerPacket.NETWORK_ID:
                AddPlayerPacket app = (AddPlayerPacket) packet;

                app.metadata = replaceMetadata(app.metadata, from, to);
                break;
            case AddEntityPacket.NETWORK_ID:
                AddEntityPacket aep = (AddEntityPacket) packet;

                aep.metadata = replaceMetadata(aep.metadata, from, to);
                break;
            case AddItemEntityPacket.NETWORK_ID:
                AddItemEntityPacket aiep = (AddItemEntityPacket) packet;

                aiep.metadata = replaceMetadata(aiep.metadata, from, to);
                break;
            case AnimatePacket.NETWORK_ID:
                if (((AnimatePacket) packet).eid == from) ((AnimatePacket) packet).eid = to;
                break;
            case TakeItemEntityPacket.NETWORK_ID:
                if (((TakeItemEntityPacket) packet).entityId == from) ((TakeItemEntityPacket) packet).entityId = to;
                break;
            case SetEntityMotionPacket.NETWORK_ID:
                if (((SetEntityMotionPacket) packet).eid == from) ((SetEntityMotionPacket) packet).eid = to;
                break;
            case SetEntityLinkPacket.NETWORK_ID:
                if (((SetEntityLinkPacket) packet).rider == from) ((SetEntityLinkPacket) packet).rider = to;
                if (((SetEntityLinkPacket) packet).riding == from) ((SetEntityLinkPacket) packet).riding = to;
                break;
            case SetEntityDataPacket.NETWORK_ID:
                SetEntityDataPacket sedp = (SetEntityDataPacket) packet;

                if (sedp.eid == from) sedp.eid = to;
                sedp.metadata = replaceMetadata(sedp.metadata, from, to);
                break;
            case UpdateAttributesPacket.NETWORK_ID:
                if (((UpdateAttributesPacket) packet).entityId == from) ((UpdateAttributesPacket) packet).entityId = to;
                break;
            case EntityEventPacket.NETWORK_ID:
                if (((EntityEventPacket) packet).eid == from) ((EntityEventPacket) packet).eid = to;
                break;
            case MovePlayerPacket.NETWORK_ID:
                if (((MovePlayerPacket) packet).eid == from) ((MovePlayerPacket) packet).eid = to;
                break;
            case MobEquipmentPacket.NETWORK_ID:
                if (((MobEquipmentPacket) packet).eid == from) ((MobEquipmentPacket) packet).eid = to;
                break;
            case MobEffectPacket.NETWORK_ID:
                if (((MobEffectPacket) packet).eid == from) ((MobEffectPacket) packet).eid = to;
                break;
            case MoveEntityPacket.NETWORK_ID:
                if (((MoveEntityPacket) packet).eid == from) ((MoveEntityPacket) packet).eid = to;
                break;
            case MobArmorEquipmentPacket.NETWORK_ID:
                if (((MobArmorEquipmentPacket) packet).eid == from) ((MobArmorEquipmentPacket) packet).eid = to;
                break;
            case PlayerListPacket.NETWORK_ID:
                Arrays.stream(((PlayerListPacket) packet).entries).filter(entry -> entry.entityId == from).forEach(entry -> entry.entityId = to);
                break;
            case BossEventPacket.NETWORK_ID:
                if (((BossEventPacket) packet).bossEid == from) ((BossEventPacket) packet).bossEid = to;
                break;
            case AdventureSettingsPacket.NETWORK_ID:
                if (((AdventureSettingsPacket) packet).entityUniqueId == from)
                    ((AdventureSettingsPacket) packet).entityUniqueId = to;
                break;
            case ProtocolInfo.UPDATE_EQUIPMENT_PACKET:
                if (((UpdateEquipmentPacket) packet).eid == from) ((UpdateEquipmentPacket) packet).eid = to;
                break;
            default:
                change = false;
        }

        if (change) {
            packet.isEncoded = false;
        }

        return packet;
    }

    private static EntityMetadata replaceMetadata(EntityMetadata data, long from, long to) {
        boolean changed = false;

        for (Integer key : replaceMetadata) {
            try {
                if (data.getLong(key) == from) {
                    if (!changed) {
                        data = cloneMetadata(data);
                        changed = true;
                    }

                    data.putLong(key, to);
                }
            } catch (Exception e) {
                MainLogger.getLogger().error("Exception while replacing metadata '" + key + "'", e);
            }
        }

        return data;
    }

    private static EntityMetadata cloneMetadata(EntityMetadata data) {
        EntityMetadata newData = new EntityMetadata();

        for (EntityData value : data.getMap().values()) {
            newData.put(value);
        }

        return newData;
    }
}
