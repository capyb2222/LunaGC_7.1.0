package emu.grasscutter.game.tps;

import com.google.gson.JsonElement;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.FightPropData;
import emu.grasscutter.data.excels.ConstValueData;
import emu.grasscutter.data.excels.EquipAffixData;
import emu.grasscutter.data.excels.tps.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityWeapon;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaUpdateTpsWeaponAmmunitionOuterClass.AbilityMetaUpdateTpsWeaponAmmunition;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SceneWeaponInfoOuterClass.SceneWeaponInfo;
import emu.grasscutter.net.proto.TpsAmmunitionChangeNotifyOuterClass.TpsAmmunitionChangeNotify;
import emu.grasscutter.net.proto.TpsAmmunitionCountOuterClass.TpsAmmunitionCount;
import emu.grasscutter.net.proto.TpsWeaponAmmunitionInfoOuterClass.TpsWeaponAmmunitionInfo;
import emu.grasscutter.net.proto._TpsWeaponOuterClass._TpsWeapon;
import emu.grasscutter.server.packet.send.PacketStoreItemChangeNotify;
import emu.grasscutter.server.packet.send.PacketTpsEquipChangeNotify;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.*;
import java.nio.file.Files;
import java.util.*;
import javax.annotation.Nullable;

public final class TpsWeaponSystem {
    private static final String CONST_ITEM_LIMIT = "CONST_VALUE_TPS_WEAPON_ITEM_LIMIT";
    private static final String CONST_WEAR_LIMIT = "CONST_VALUE_TPS_SLOT_WEAR_NUM_LIMIT";
    private static final int DEFAULT_ITEM_LIMIT = 20;

    private static volatile Int2IntMap wearLimits;
    private static volatile Int2ObjectMap<TpsAmmunitionData> ammunitionBySlot;
    private static volatile Int2ObjectMap<TpsWeaponAccessoryData> accessoryByMaterial;

    private static final String COMBAT_BASE_PATH = "BinOutput/Ability/Temp/EquipAbilities/TPSWeaponAbilities/";
    private static final String COMBAT_BASE_MIXIN = "TPSCombatBaseMixin";
    private static final String COMBAT_BASE_SLOT = "FDEMCPFAFOC";
    private static final String COMBAT_BASE_CAPACITY = "ECJJJOKGNNI";

    private static volatile Int2IntMap magazineCapacities;

    private TpsWeaponSystem() {}

    public static int getItemLimit() {
        return ConstValueData.getInt(CONST_ITEM_LIMIT, 0, DEFAULT_ITEM_LIMIT);
    }

    private static Int2IntMap getWearLimits() {
        var limits = wearLimits;
        if (limits != null) return limits;

        limits = new Int2IntOpenHashMap();
        for (var entry : ConstValueData.get(CONST_WEAR_LIMIT, 0).split(";")) {
            var pair = entry.split(":");
            if (pair.length != 2) continue;
            try {
                limits.put(Integer.parseInt(pair[0].trim()), Integer.parseInt(pair[1].trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        wearLimits = limits;
        return limits;
    }

    private static Int2ObjectMap<TpsAmmunitionData> getAmmunitionBySlot() {
        var map = ammunitionBySlot;
        if (map != null) return map;

        map = new Int2ObjectOpenHashMap<>();
        for (var ammunition : GameData.getTpsAmmunitionDataMap().values()) {
            for (int slotId : ammunition.getAmmoSlotIds()) {
                map.put(slotId, ammunition);
            }
        }
        ammunitionBySlot = map;
        return map;
    }

    private static Int2ObjectMap<TpsWeaponAccessoryData> getAccessoryByMaterial() {
        var map = accessoryByMaterial;
        if (map != null) return map;

        map = new Int2ObjectOpenHashMap<>();
        for (var accessory : GameData.getTpsWeaponAccessoryDataMap().values()) {
            if (accessory.getUnlockMaterialId() > 0) {
                map.put(accessory.getUnlockMaterialId(), accessory);
            }
        }
        accessoryByMaterial = map;
        return map;
    }

    private static Int2IntMap getMagazineCapacities() {
        var capacities = magazineCapacities;
        if (capacities != null) return capacities;

        capacities = new Int2IntOpenHashMap();
        try (var files =
                Files.newDirectoryStream(FileUtils.getResourcePath(COMBAT_BASE_PATH), "*.json")) {
            for (var file : files) {
                collectCapacities(JsonUtils.loadToClass(file, JsonElement.class), capacities);
            }
        } catch (Exception e) {
            Grasscutter.getLogger().warn("[tps] Could not read the TPS weapon magazine sizes", e);
        }
        magazineCapacities = capacities;
        return capacities;
    }

    private static void collectCapacities(JsonElement element, Int2IntMap capacities) {
        if (element == null) return;
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectCapacities(child, capacities));
            return;
        }
        if (!element.isJsonObject()) return;

        var object = element.getAsJsonObject();
        var type = object.get("$type");
        if (type != null
                && type.isJsonPrimitive()
                && COMBAT_BASE_MIXIN.equals(type.getAsString())
                && object.has(COMBAT_BASE_SLOT)
                && object.has(COMBAT_BASE_CAPACITY)) {
            int slotId = object.get(COMBAT_BASE_SLOT).getAsInt();
            int capacity = object.get(COMBAT_BASE_CAPACITY).getAsInt();
            if (!capacities.containsKey(slotId) || capacity < capacities.get(slotId)) {
                capacities.put(slotId, capacity);
            }
        }
        object.entrySet().forEach(entry -> collectCapacities(entry.getValue(), capacities));
    }

    public static int getMagazine(Player player, int slotId, int ammunitionId) {
        int total = getTotal(player, ammunitionId);
        int loaded =
                player.getTpsMagazines().getOrDefault(slotId, getMagazineCapacities().getOrDefault(slotId, 0));
        return Math.max(0, Math.min(loaded, total));
    }

    public static boolean isTpsWeapon(@Nullable GameItem item) {
        return item != null
                && item.getItemData() != null
                && item.getItemType() == ItemType.ITEM_TPS_WEAPON;
    }

    @Nullable public static GameItem findOwnedWeapon(Player player, int itemId) {
        for (GameItem item : player.getInventory().getItems().values()) {
            if (item.getItemId() == itemId && isTpsWeapon(item)) return item;
        }
        return null;
    }

    public static List<GameItem> getOwnedWeapons(Player player) {
        return player.getInventory().getItems().values().stream()
                .filter(TpsWeaponSystem::isTpsWeapon)
                .sorted(Comparator.comparingInt(GameItem::getItemId))
                .toList();
    }

    public static List<GameItem> getWornWeapons(Avatar avatar) {
        var player = avatar.getPlayer();
        if (player == null
                || avatar.getTpsWeaponIds().isEmpty()
                || !TpsAvatarSystem.isTpsAvatar(avatar)) {
            return List.of();
        }

        var worn = new ArrayList<GameItem>(avatar.getTpsWeaponIds().size());
        for (int itemId : avatar.getTpsWeaponIds()) {
            var item = findOwnedWeapon(player, itemId);
            if (item != null) worn.add(item);
        }
        return worn;
    }

    public static List<Avatar> getWearers(Player player) {
        var wearers = new ArrayList<Avatar>();
        player.getAvatars().forEach(wearers::add);
        wearers.addAll(player.getTeamManager().getTrialAvatars().values());
        wearers.removeIf(
                avatar -> avatar.getTpsWeaponIds().isEmpty() || !TpsAvatarSystem.isTpsAvatar(avatar));
        return wearers;
    }

    public static List<Integer> getUnlockedAccessories(Player player, int weaponId) {
        return GameData.getTpsWeaponAccessoryDataMap().values().stream()
                .filter(accessory -> accessory.getTpsWeaponId() == weaponId)
                .filter(accessory -> accessory.getUnlockMaterialId() > 0)
                .filter(
                        accessory ->
                                player.getInventory().getItemById(accessory.getUnlockMaterialId()) != null)
                .map(TpsWeaponAccessoryData::getId)
                .sorted()
                .toList();
    }

    public static boolean refreshAccessories(Player player, GameItem weapon, boolean notify) {
        var unlocked = getUnlockedAccessories(player, weapon.getItemId());
        if (unlocked.equals(weapon.getTpsAccessoryIds())) return false;

        weapon.setTpsAccessoryIds(new ArrayList<>(unlocked));
        weapon.save();
        if (!notify) return true;

        player.sendPacket(new PacketStoreItemChangeNotify(weapon));
        for (Avatar avatar : getWearers(player)) {
            if (!avatar.getTpsWeaponIds().contains(weapon.getItemId())) continue;
            avatar.recalcStats();
            sendEquipChange(avatar);
        }
        return true;
    }

    public static void onMaterialChanged(Player player, int materialId) {
        var accessory = getAccessoryByMaterial().get(materialId);
        if (accessory == null) return;

        var weapon = findOwnedWeapon(player, accessory.getTpsWeaponId());
        if (weapon != null) refreshAccessories(player, weapon, player.hasSentLoginPackets());
    }

    public static List<Integer> getAccessoryMaterials(int weaponId) {
        return GameData.getTpsWeaponAccessoryDataMap().values().stream()
                .filter(accessory -> accessory.getTpsWeaponId() == weaponId)
                .map(TpsWeaponAccessoryData::getUnlockMaterialId)
                .filter(materialId -> materialId > 0)
                .sorted()
                .toList();
    }

    public static Int2IntMap getAffixLevels(GameItem item) {
        var affixes = new Int2IntLinkedOpenHashMap();
        var weaponData = GameData.getTpsWeaponDataMap().get(item.getItemId());
        if (weaponData != null) {
            putAffix(affixes, weaponData.getEquipAffixId(), weaponData.getTpsWeaponBaseAffix());
        }
        for (int accessoryId : item.getTpsAccessoryIds()) {
            var accessory = GameData.getTpsWeaponAccessoryDataMap().get(accessoryId);
            if (accessory == null || accessory.getTpsWeaponId() != item.getItemId()) continue;
            putAffix(affixes, accessory.getEquipAffixId(), accessory.getTpsWeaponBaseAffix());
        }
        return affixes;
    }

    private static void putAffix(Int2IntMap affixes, int equipAffixId, List<Integer> baseAffixes) {
        if (equipAffixId > 0) {
            affixes.put(equipAffixId / 10, equipAffixId % 10);
            return;
        }
        for (int affixId : baseAffixes) {
            if (affixId > 0) affixes.put(affixId, 0);
        }
    }

    public static _TpsWeapon toTpsWeaponProto(GameItem item) {
        return _TpsWeapon.newBuilder()
                .addAllAccessoryIdList(item.getTpsAccessoryIds())
                .putAllAffixMap(getAffixLevels(item))
                .build();
    }

    public static void ensureWeaponEntity(GameItem item, @Nullable Scene scene) {
        if (scene == null || scene.getWorld() == null) return;
        var entity = item.getWeaponEntity();
        if (entity != null && entity.getScene() == scene) return;

        entity = new EntityWeapon(scene, item.getItemData().getGadgetId());
        item.setWeaponEntity(entity);
        scene.getWeaponEntities().put(entity.getId(), entity);
    }

    public static void ensureWeaponEntities(Avatar avatar, @Nullable Scene scene) {
        for (GameItem item : getWornWeapons(avatar)) {
            dropWeaponEntity(item);
            ensureWeaponEntity(item, scene);
        }
    }

    public static void dropWeaponEntities(Avatar avatar) {
        getWornWeapons(avatar).forEach(TpsWeaponSystem::dropWeaponEntity);
    }

    public static void clearNonTpsWearers(Player player) {
        for (Avatar avatar : player.getAvatars()) {
            if (avatar.getTpsWeaponIds().isEmpty() || TpsAvatarSystem.isTpsAvatar(avatar)) continue;
            avatar.getTpsWeaponIds().clear();
            avatar.save();
        }
    }

    private static void dropWeaponEntity(GameItem item) {
        var entity = item.getWeaponEntity();
        if (entity != null && entity.getScene() != null) {
            entity.getScene().getWeaponEntities().remove(entity.getId());
        }
        item.setWeaponEntity(null);
    }

    private static void respawnWeaponEntities(Avatar avatar) {
        var avatarEntity = avatar.getAsEntity();
        var scene = avatarEntity != null ? avatarEntity.getScene() : null;
        for (GameItem item : getWornWeapons(avatar)) {
            dropWeaponEntity(item);
            ensureWeaponEntity(item, scene);
        }
    }

    public static SceneWeaponInfo toSceneWeaponInfo(Player player, GameItem item) {
        var info =
                SceneWeaponInfo.newBuilder()
                        .setEntityId(item.getWeaponEntity() != null ? item.getWeaponEntity().getId() : 0)
                        .setGadgetId(item.getItemData().getGadgetId())
                        .setItemId(item.getItemId())
                        .setGuid(item.getGuid())
                        .setLevel(item.getLevel())
                        .setPromoteLevel(item.getPromoteLevel())
                        .putAllAffixMap(getAffixLevels(item))
                        .setAbilityInfo(AbilitySyncStateInfo.newBuilder());

        var weaponData = GameData.getTpsWeaponDataMap().get(item.getItemId());
        if (weaponData != null) {
            for (int slotId : weaponData.getAmmoSlotIds()) {
                var ammunition = getAmmunitionBySlot().get(slotId);
                if (ammunition == null) continue;
                info.addAmmunitionList(
                        TpsWeaponAmmunitionInfo.newBuilder()
                                .setAmmunitionType(slotId)
                                .setAmmunitionConfigId(ammunition.getId())
                                .setCurrentAmmunition(getMagazine(player, slotId, ammunition.getId())));
            }
        }
        return info.build();
    }

    public static List<SceneWeaponInfo> getSceneWeaponInfos(Avatar avatar) {
        var player = avatar.getPlayer();
        if (player == null) return List.of();
        return getWornWeapons(avatar).stream().map(item -> toSceneWeaponInfo(player, item)).toList();
    }

    public static void applyAffixes(Avatar avatar) {
        for (GameItem item : getWornWeapons(avatar)) {
            for (var affix : getAffixLevels(item).int2IntEntrySet()) {
                EquipAffixData affixData =
                        GameData.getEquipAffixDataMap().get(affix.getIntKey() * 10 + affix.getIntValue());
                if (affixData == null) continue;
                if (affixData.getAddProps() != null) {
                    for (FightPropData prop : affixData.getAddProps()) {
                        if (prop.getProp() != null) avatar.addFightProperty(prop.getProp(), prop.getValue());
                    }
                }
                avatar.addToExtraAbilityEmbryos(affixData.getOpenConfig(), true);
            }
        }
    }

    public static int wear(Player player, long avatarGuid, List<Long> equipGuids) {
        var avatar = findAvatar(player, avatarGuid);
        if (avatar == null) return Retcode.RET_CAN_NOT_FIND_AVATAR_VALUE;
        if (!TpsAvatarSystem.isTpsAvatar(avatar)) {
            if (!avatar.getTpsWeaponIds().isEmpty()) {
                avatar.getTpsWeaponIds().clear();
                avatar.save();
            }
            return Retcode.RET_FAIL_VALUE;
        }

        var itemIds = new ArrayList<Integer>(equipGuids.size());
        var slotCounts = new Int2IntOpenHashMap();
        for (long guid : equipGuids) {
            var item = player.getInventory().getItemByGuid(guid);
            if (!isTpsWeapon(item)) return Retcode.RET_ITEM_NOT_EXIST_VALUE;
            if (itemIds.contains(item.getItemId())) continue;

            var weaponData = GameData.getTpsWeaponDataMap().get(item.getItemId());
            int slotType = weaponData != null ? weaponData.getWearSlotType() : 0;
            if (slotCounts.addTo(slotType, 1) + 1 > getWearLimits().getOrDefault(slotType, 0)) {
                return Retcode.RET_EQUIP_EXCEED_LIMIT_VALUE;
            }
            itemIds.add(item.getItemId());
        }

        if (itemIds.equals(avatar.getTpsWeaponIds())) return Retcode.RET_SUCC_VALUE;

        for (Avatar other : getWearers(player)) {
            if (other == avatar || !other.getTpsWeaponIds().removeIf(itemIds::contains)) continue;
            if (other.getTrialAvatarId() == 0) other.save();
            other.recalcStats();
            sendEquipChange(other);
        }

        var loadout = player.getTpsLoadout();
        boolean loadoutChanged;
        if (TpsAvatarSystem.isTpsAvatar(avatar)) {
            loadoutChanged = !loadout.equals(itemIds);
            loadout.clear();
            loadout.addAll(itemIds);
        } else {
            loadoutChanged = loadout.removeIf(itemIds::contains);
        }
        if (loadoutChanged) player.save();

        var removed = new ArrayList<>(avatar.getTpsWeaponIds());
        removed.removeAll(itemIds);

        avatar.getTpsWeaponIds().clear();
        avatar.getTpsWeaponIds().addAll(itemIds);
        if (avatar.getTrialAvatarId() == 0) avatar.save();

        for (int itemId : removed) {
            var item = findOwnedWeapon(player, itemId);
            if (item != null) dropWeaponEntity(item);
        }

        avatar.recalcStats();
        sendEquipChange(avatar);
        sendAmmunition(player);
        return Retcode.RET_SUCC_VALUE;
    }

    public static void onItemRemoved(Player player, GameItem item) {
        if (!isTpsWeapon(item)) {
            onMaterialChanged(player, item.getItemId());
            return;
        }

        dropWeaponEntity(item);
        if (player.getTpsLoadout().removeIf(id -> id == item.getItemId())) player.save();
        for (Avatar avatar : getWearers(player)) {
            if (!avatar.getTpsWeaponIds().removeIf(id -> id == item.getItemId())) continue;
            if (avatar.getTrialAvatarId() == 0) avatar.save();
            avatar.recalcStats();
            sendEquipChange(avatar);
        }
    }

    @Nullable private static Avatar findAvatar(Player player, long avatarGuid) {
        var avatar = player.getAvatars().getAvatarByGuid(avatarGuid);
        if (avatar != null) return avatar;
        return player.getTeamManager().getTrialAvatars().values().stream()
                .filter(trial -> trial.getGuid() == avatarGuid)
                .findFirst()
                .orElse(null);
    }

    public static void sendEquipChange(Avatar avatar) {
        var player = avatar.getPlayer();
        if (player == null || !player.hasSentLoginPackets()) return;

        respawnWeaponEntities(avatar);
        var packet = new PacketTpsEquipChangeNotify(avatar, getSceneWeaponInfos(avatar));
        var entity = avatar.getAsEntity();
        if (entity != null && entity.getScene() != null) {
            entity.getScene().broadcastPacket(packet);
        } else {
            player.sendPacket(packet);
        }
    }

    public static int getTotal(Player player, int ammunitionId) {
        var data = GameData.getTpsAmmunitionDataMap().get(ammunitionId);
        int limit = data != null ? data.getTpsAmmoLimit() : 0;
        return Math.max(0, Math.min(player.getTpsAmmunition().getOrDefault(ammunitionId, limit), limit));
    }

    public static int changeTotal(Player player, int ammunitionId, int delta) {
        var data = GameData.getTpsAmmunitionDataMap().get(ammunitionId);
        if (data == null) return 0;

        long value = (long) getTotal(player, ammunitionId) + delta;
        int total = (int) Math.max(0, Math.min(value, data.getTpsAmmoLimit()));
        player.getTpsAmmunition().put(ammunitionId, total);
        return total;
    }

    public static void refillAmmunition(Player player) {
        for (var data : GameData.getTpsAmmunitionDataMap().values()) {
            player.getTpsAmmunition().put(data.getId(), data.getTpsAmmoLimit());
        }
        player.getTpsMagazines().clear();
        player.save();
        getWearers(player).forEach(TpsWeaponSystem::sendEquipChange);
        sendAmmunition(player);
    }

    private static Int2IntMap getWornAmmunition(Player player) {
        var reserves = new Int2IntLinkedOpenHashMap();
        for (Avatar avatar : getWearers(player)) {
            for (GameItem item : getWornWeapons(avatar)) {
                var data = GameData.getTpsWeaponDataMap().get(item.getItemId());
                if (data == null) continue;
                for (int slotId : data.getAmmoSlotIds()) {
                    var ammunition = getAmmunitionBySlot().get(slotId);
                    if (ammunition != null) {
                        reserves.put(ammunition.getId(), getTotal(player, ammunition.getId()));
                    }
                }
            }
        }
        return reserves;
    }

    public static void sendSceneAmmunition(Player player) {
        if (getWornAmmunition(player).isEmpty()) return;
        player.sendPacket(new BasePacket(PacketOpcodes.TpsRegionalPlaySupplyInfoNotify));
        sendAmmunition(player);
    }

    public static void sendAmmunition(Player player) {
        var reserves = getWornAmmunition(player);
        if (reserves.isEmpty() || !player.hasSentLoginPackets()) return;

        var sent = player.getTpsAmmunitionSent();
        var proto = TpsAmmunitionChangeNotify.newBuilder();
        for (var entry : reserves.int2IntEntrySet()) {
            int delta = entry.getIntValue() - sent.getOrDefault(entry.getIntKey(), 0);
            if (delta == 0) continue;
            sent.put(entry.getIntKey(), entry.getIntValue());
            proto.addAmmunitionList(
                    TpsAmmunitionCount.newBuilder()
                            .setAmmunitionConfigId(entry.getIntKey())
                            .setCount(delta));
        }
        if (proto.getAmmunitionListCount() == 0) return;
        var packet = new BasePacket(PacketOpcodes.TpsAmmunitionChangeNotify);
        packet.setData(proto);
        player.sendPacket(packet);
    }

    public static void onAmmunitionInvoke(Player player, AbilityInvokeEntry invoke) throws Exception {
        var update = AbilityMetaUpdateTpsWeaponAmmunition.parseFrom(invoke.getAbilityData());
        for (var change : update.getAmmunitionListList()) {
            changeTotal(player, change.getAmmunitionConfigId(), change.getChangeCount());
            player.getTpsAmmunitionSent()
                    .merge(change.getAmmunitionConfigId(), change.getChangeCount(), Integer::sum);
        }
        for (var slot : update.getAccessoryListList()) {
            player.getTpsMagazines().put(slot.getAmmunitionType(), slot.getCurrentAmmunition());
        }
    }
}
