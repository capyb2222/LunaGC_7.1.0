package emu.grasscutter.game.managers;

import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.server.packet.send.*;
import java.util.*;

public class SatiationManager extends BasePlayerManager {

    public SatiationManager(Player player) {
        super(player);
    }

    public synchronized boolean addSatiation(Avatar avatar, float satiationIncrease, int itemId) {

        Map<Integer, Long> propMap = new HashMap<>();
        int satiation = Math.round(satiationIncrease * 100);
        float totalSatiation = ((satiationIncrease * 100) + avatar.getSatiation());

        updateTime();

        var playerTime = (player.getClientTime() / 1000);
        float finishTime = playerTime + (totalSatiation / 30);

        long penaltyTime = playerTime;
        long penaltyValue = avatar.getSatiationPenalty();
        if (totalSatiation + avatar.getSatiation() > 10000 && penaltyValue == 0) {
            penaltyTime += 30;
            penaltyValue = 3000;
        }

        if (!addSatiationDirectly(avatar, satiation)) return false;
        propMap.put(PlayerProperty.PROP_SATIATION_VAL.getId(), Long.valueOf(satiation));
        propMap.put(PlayerProperty.PROP_SATIATION_PENALTY_TIME.getId(), penaltyValue);

        player.getSession().send(new PacketAvatarPropNotify(avatar, propMap));
        player.getSession().send(new PacketAvatarSatiationDataNotify(avatar, finishTime, penaltyTime));
        return true;
    }

    public synchronized boolean addSatiationDirectly(Avatar avatar, int value) {
        if (!avatar.addSatiation(value)) return false;
        avatar.save();
        return true;
    }

    public synchronized void removeSatiationDirectly(Avatar avatar, int value) {
        avatar.reduceSatiation(value);
        avatar.reduceSatiationPenalty(3000);
        avatar.save();
        updateSingleAvatar(avatar, 0);
    }

    public synchronized void reduceSatiation() {
        player
                .getAvatars()
                .forEach(
                        avatar -> {
                            if (avatar.getSatiationPenalty() > 0 && avatar.getSatiation() == 0) {
                                avatar.reduceSatiationPenalty(3000);
                            }

                            if (avatar.getSatiation() > 0) {
                                if (avatar.getSatiationPenalty() > 0) {
                                    avatar.reduceSatiationPenalty(100);
                                } else {
                                    avatar.reduceSatiation(30);

                                    addSatiation(avatar, 0, 0);
                                }
                            }
                        });
    }

    public synchronized void updateSingleAvatar(Avatar avatar, float givenTime) {
        float time = (player.getClientTime() / 1000) + givenTime;
        player.getSession().send(new PacketAvatarPropNotify(avatar));
        player.getSession().send(new PacketAvatarSatiationDataNotify(time, avatar));
    }

    private void updateTime() {
        player.getSession().send(new PacketPlayerGameTimeNotify(player));
        player.getSession().send(new PacketPlayerTimeNotify(player));
    }
}
