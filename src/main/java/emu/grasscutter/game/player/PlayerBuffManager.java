package emu.grasscutter.game.player;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.BuffData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.ServerBuffChangeNotifyOuterClass.ServerBuffChangeNotify.ServerBuffChangeType;
import emu.grasscutter.net.proto.ServerBuffOuterClass.ServerBuff;
import emu.grasscutter.server.packet.send.PacketServerBuffChangeNotify;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import lombok.Getter;

public final class PlayerBuffManager extends BasePlayerManager {
    private final List<PlayerBuff> pendingBuffs;
    private final Int2ObjectMap<PlayerBuff> buffs;
    private int nextBuffUid;

    public PlayerBuffManager(Player player) {
        super(player);

        this.buffs = new Int2ObjectOpenHashMap<>();
        this.pendingBuffs = new ArrayList<>();
    }

    private int getNextBuffUid() {
        return ++nextBuffUid;
    }

    public synchronized boolean hasBuff(int groupId) {
        return this.buffs.containsKey(groupId);
    }

    public synchronized void clearBuffs() {
        getPlayer()
                .sendPacket(
                        new PacketServerBuffChangeNotify(
                                getPlayer(),
                                ServerBuffChangeType.SERVER_BUFF_CHANGE_TYPE_DEL_SERVER_BUFF,
                                this.buffs.values()));

        this.buffs.clear();
    }

    public boolean addBuff(int buffId) {
        return addBuff(buffId, -1f);
    }

    public synchronized boolean addBuff(int buffId, float duration) {
        return addBuff(buffId, duration, null);
    }

    public synchronized boolean addBuff(int buffId, float duration, Avatar target) {
        var buffData = GameData.getBuffDataMap().get(buffId);
        if (buffData == null) return false;

        var success =
                Optional.ofNullable(GameData.getAbilityData(buffData.getAbilityName()))
                        .map(data -> data.modifiers.get(buffData.getModifierName()))
                        .map(modifier -> modifier.onAdded)
                        .map(
                                onAdded -> {
                                    var shouldHeal = false;
                                    for (var ability : onAdded) {
                                        if (ability.type == null) {
                                            continue;
                                        }

                                        if (ability.type == AbilityModifierAction.Type.HealHP) {
                                            if (target == null) continue;

                                            var maxHp = target.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
                                            var amount =
                                                    ability.amount.get() + ability.amountByCasterMaxHPRatio.get() * maxHp;

                                            target.getAsEntity().heal(amount);
                                            shouldHeal = true;
                                        }
                                    }

                                    return shouldHeal;
                                })
                        .orElse(false);

        if (duration < 0f) {
            duration = buffData.getTime();
        }

        if (duration <= 0) {
            return success;
        }

        this.removeBuff(buffData.getGroupId());

        PlayerBuff buff = new PlayerBuff(getNextBuffUid(), buffData, duration);
        this.buffs.put(buff.getGroupId(), buff);

        getPlayer()
                .sendPacket(
                        new PacketServerBuffChangeNotify(
                                getPlayer(), ServerBuffChangeType.SERVER_BUFF_CHANGE_TYPE_ADD_SERVER_BUFF, buff));

        return true;
    }

    public synchronized boolean removeBuff(int buffGroupId) {
        PlayerBuff buff = this.buffs.remove(buffGroupId);

        if (buff != null) {
            getPlayer()
                    .sendPacket(
                            new PacketServerBuffChangeNotify(
                                    getPlayer(), ServerBuffChangeType.SERVER_BUFF_CHANGE_TYPE_DEL_SERVER_BUFF, buff));
            return true;
        }

        return false;
    }

    public synchronized void onTick() {
        if (this.buffs.isEmpty()) return;

        long currentTime = System.currentTimeMillis();

        this.buffs
                .values()
                .removeIf(
                        buff -> {
                            if (currentTime <= buff.getEndTime()) return false;
                            this.pendingBuffs.add(buff);
                            return true;
                        });

        if (this.pendingBuffs.size() > 0) {
            getPlayer()
                    .sendPacket(
                            new PacketServerBuffChangeNotify(
                                    getPlayer(),
                                    ServerBuffChangeType.SERVER_BUFF_CHANGE_TYPE_DEL_SERVER_BUFF,
                                    this.pendingBuffs));
            this.pendingBuffs.clear();
        }
    }

    @Getter
    public static class PlayerBuff {
        private final int uid;
        private final BuffData buffData;
        private final long endTime;

        public PlayerBuff(int uid, BuffData buffData, float duration) {
            this.uid = uid;
            this.buffData = buffData;
            this.endTime = System.currentTimeMillis() + ((long) duration * 1000);
        }

        public int getGroupId() {
            return getBuffData().getGroupId();
        }

        public ServerBuff toProto() {
            return ServerBuff.newBuilder()
                    .setServerBuffUid(this.getUid())
                    .setServerBuffId(this.getBuffData().getId())
                    .setServerBuffType(this.getBuffData().getServerBuffType().getValue())
                    .setInstancedModifierId(1)
                    .build();
        }
    }
}
