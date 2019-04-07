package org.itxtech.synapseapi.utils;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.EntityData;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.entity.data.LongEntityData;
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
        boolean change = false;

        //TODO: return original packet if there is no change
        switch (packet.pid()) {
            case AddPlayerPacket.NETWORK_ID:
                AddPlayerPacket app = (AddPlayerPacket) packet;

                EntityMetadata replaced = replaceMetadata(app.metadata, from, to);

                if (replaced != null) {
                    change = true;
                    app.metadata = replaced;
                }
                break;
            case AddEntityPacket.NETWORK_ID:
                AddEntityPacket aep = (AddEntityPacket) packet;

                replaced = replaceMetadata(aep.metadata, from, to);

                if (replaced != null) {
                    change = true;
                    aep.metadata = replaced;
                }
                break;
            case AddItemEntityPacket.NETWORK_ID:
                AddItemEntityPacket aiep = (AddItemEntityPacket) packet;

                replaced = replaceMetadata(aiep.metadata, from, to);

                if (replaced != null) {
                    change = true;
                    aiep.metadata = replaced;
                }
                break;
            case AnimatePacket.NETWORK_ID:
                if (((AnimatePacket) packet).eid == from) {
                    ((AnimatePacket) packet).eid = to;
                    change = true;
                }
                break;
            case TakeItemEntityPacket.NETWORK_ID:
                if (((TakeItemEntityPacket) packet).entityId == from) {
                    ((TakeItemEntityPacket) packet).entityId = to;
                    change = true;
                }
                break;
            case SetEntityMotionPacket.NETWORK_ID:
                if (((SetEntityMotionPacket) packet).eid == from) {
                    ((SetEntityMotionPacket) packet).eid = to;
                    change = true;
                }
                break;
            case SetEntityLinkPacket.NETWORK_ID:
                SetEntityLinkPacket selp = (SetEntityLinkPacket) packet;

                if (selp.riderUniqueId == from) {
                    selp.riderUniqueId = to;
                    change = true;
                }
                if (selp.vehicleUniqueId == from) {
                    selp.vehicleUniqueId = to;
                    change = true;
                }

                break;
            case SetEntityDataPacket.NETWORK_ID:
                SetEntityDataPacket sedp = (SetEntityDataPacket) packet;

                if (sedp.eid == from) {
                    sedp.eid = to;
                    change = true;
                }

                replaced = replaceMetadata(sedp.metadata, from, to);

                if (replaced != null) {
                    change = true;
                    sedp.metadata = replaced;
                }
                break;
            case UpdateAttributesPacket.NETWORK_ID:
                if (((UpdateAttributesPacket) packet).entityId == from) {
                    ((UpdateAttributesPacket) packet).entityId = to;
                    change = true;
                }
                break;
            case EntityEventPacket.NETWORK_ID:
                if (((EntityEventPacket) packet).eid == from) {
                    ((EntityEventPacket) packet).eid = to;
                    change = true;
                }
                break;
            case MovePlayerPacket.NETWORK_ID:
                if (((MovePlayerPacket) packet).eid == from) {
                    ((MovePlayerPacket) packet).eid = to;
                    change = true;
                }
                break;
            case MobEquipmentPacket.NETWORK_ID:
                if (((MobEquipmentPacket) packet).eid == from) {
                    ((MobEquipmentPacket) packet).eid = to;
                    change = true;
                }
                break;
            case MobEffectPacket.NETWORK_ID:
                if (((MobEffectPacket) packet).eid == from) {
                    ((MobEffectPacket) packet).eid = to;
                    change = true;
                }
                break;
            case MoveEntityAbsolutePacket.NETWORK_ID:
                if (((MoveEntityAbsolutePacket) packet).eid == from) {
                    ((MoveEntityAbsolutePacket) packet).eid = to;
                    change = true;
                }
                break;
            case MobArmorEquipmentPacket.NETWORK_ID:
                if (((MobArmorEquipmentPacket) packet).eid == from) {
                    ((MobArmorEquipmentPacket) packet).eid = to;
                    change = true;
                }
                break;
            case PlayerListPacket.NETWORK_ID:
                Arrays.stream(((PlayerListPacket) packet).entries).filter(entry -> entry.entityId == from).forEach(entry -> entry.entityId = to);
                change = true;
                break;
            case BossEventPacket.NETWORK_ID:
                if (((BossEventPacket) packet).bossEid == from) {
                    ((BossEventPacket) packet).bossEid = to;
                    change = true;
                }
                break;
            case AdventureSettingsPacket.NETWORK_ID:
                if (((AdventureSettingsPacket) packet).entityUniqueId == from) {
                    ((AdventureSettingsPacket) packet).entityUniqueId = to;
                    change = true;
                }
                break;
            case ProtocolInfo.UPDATE_EQUIPMENT_PACKET:
                if (((UpdateEquipmentPacket) packet).eid == from) {
                    ((UpdateEquipmentPacket) packet).eid = to;
                    change = true;
                }
                break;
        }

        if (change) {
            packet.isEncoded = false;
            return packet;
        }

        return pk;
    }

    private static EntityMetadata replaceMetadata(EntityMetadata data, long from, long to) {
        boolean changed = false;

        for (Integer key : replaceMetadata) {
            try {
                EntityData ed = data.get(key);

                if (ed == null) {
                    continue;
                }

                if (ed.getType() != Entity.DATA_TYPE_LONG) {
                    MainLogger.getLogger().info("Wrong entity data type (" + key + ") expected 'Long' got '" + dataTypeToString(ed.getType()) + "'");
                    continue;
                }

                long value = ((LongEntityData) ed).getData();

                if (value == from) {
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

        if (!changed) return null;

        return data;
    }

    private static EntityMetadata cloneMetadata(EntityMetadata data) {
        EntityMetadata newData = new EntityMetadata();

        for (EntityData value : data.getMap().values()) {
            newData.put(value);
        }

        return newData;
    }

    private static String dataTypeToString(int type) {
        switch (type) {
            case Entity.DATA_TYPE_BYTE:
                return "Byte";
            case Entity.DATA_TYPE_SHORT:
                return "Short";
            case Entity.DATA_TYPE_INT:
                return "Int";
            case Entity.DATA_TYPE_FLOAT:
                return "Float";
            case Entity.DATA_TYPE_STRING:
                return "String";
            case Entity.DATA_TYPE_SLOT:
                return "Slot";
            case Entity.DATA_TYPE_POS:
                return "Pos";
            case Entity.DATA_TYPE_LONG:
                return "Long";
            case Entity.DATA_TYPE_VECTOR3F:
                return "Vector3f";
        }

        return "Unknown";
    }
}
