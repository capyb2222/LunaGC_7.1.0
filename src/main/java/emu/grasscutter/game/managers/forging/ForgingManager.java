package emu.grasscutter.game.managers.forging;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.proto.ForgeQueueDataOuterClass.ForgeQueueData;
import emu.grasscutter.net.proto.ForgeQueueManipulateReqOuterClass.ForgeQueueManipulateReq;
import emu.grasscutter.net.proto.ForgeQueueManipulateTypeOuterClass.ForgeQueueManipulateType;
import emu.grasscutter.net.proto.ForgeStartReqOuterClass.ForgeStartReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.event.player.PlayerForgeItemEvent;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;
import java.util.*;

public final class ForgingManager extends BasePlayerManager {

    public ForgingManager(Player player) {
        super(player);
    }

    public boolean unlockForgingBlueprint(int id) {
        if (!this.player.getUnlockedForgingBlueprints().add(id)) {
            return false;
        }
        this.player.sendPacket(new PacketForgeFormulaDataNotify(id));
        return true;
    }

    private synchronized int determineNumberOfQueues() {
        int adventureRank = player.getLevel();
        return (adventureRank >= 15) ? 4 : (adventureRank >= 10) ? 3 : (adventureRank >= 5) ? 2 : 1;
    }

    private synchronized Map<Integer, ForgeQueueData> determineCurrentForgeQueueData() {
        Map<Integer, ForgeQueueData> res = new HashMap<>();
        int currentTime = Utils.getCurrentSeconds();

        for (int i = 0; i < this.player.getActiveForges().size(); i++) {
            ActiveForgeData activeForge = this.player.getActiveForges().get(i);

            ForgeQueueData data =
                    ForgeQueueData.newBuilder()
                            .setQueueId(i + 1)
                            .setForgeId(activeForge.getForgeId())
                            .setFinishCount(activeForge.getFinishedCount(currentTime))
                            .setUnfinishCount(activeForge.getUnfinishedCount(currentTime))
                            .setTotalFinishTimestamp(activeForge.getTotalFinishTimestamp())
                            .setNextFinishTimestamp(activeForge.getNextFinishTimestamp(currentTime))
                            .setAvatarId(activeForge.getAvatarId())
                            .build();

            res.put(i + 1, data);
        }

        return res;
    }

    public synchronized void sendForgeDataNotify() {
        int numQueues = this.determineNumberOfQueues();
        var unlockedItems = this.player.getUnlockedForgingBlueprints();
        var queueData = this.determineCurrentForgeQueueData();

        this.player.sendPacket(new PacketForgeDataNotify(unlockedItems, numQueues, queueData));
    }

    public synchronized void handleForgeGetQueueDataReq() {
        int numQueues = this.determineNumberOfQueues();
        var queueData = this.determineCurrentForgeQueueData();

        this.player.sendPacket(new PacketForgeGetQueueDataRsp(Retcode.RET_SUCC, numQueues, queueData));
    }

    private synchronized void sendForgeQueueDataNotify() {
        var queueData = this.determineCurrentForgeQueueData();
        this.player.sendPacket(new PacketForgeQueueDataNotify(queueData, List.of()));
    }

    private synchronized void sendForgeQueueDataNotify(boolean hasRemoved) {
        var queueData = this.determineCurrentForgeQueueData();

        if (hasRemoved) {
            this.player.sendPacket(new PacketForgeQueueDataNotify(Map.of(), List.of(1, 2, 3, 4)));
        }

        this.player.sendPacket(new PacketForgeQueueDataNotify(queueData, List.of()));
    }

    public synchronized void handleForgeStartReq(ForgeStartReq req) {
        if (this.player.getActiveForges().size() >= this.determineNumberOfQueues()) {
            this.player.sendPacket(new PacketForgeStartRsp(Retcode.RET_FORGE_QUEUE_FULL));
            return;
        }

        if (!GameData.getForgeDataMap().containsKey(req.getForgeId())) {
            this.player.sendPacket(
                    new PacketForgeStartRsp(Retcode.RET_FAIL));
            return;
        }

        ForgeData forgeData = GameData.getForgeDataMap().get(req.getForgeId());

        int requiredPoints = forgeData.getForgePoint() * req.getForgeCount();
        if (requiredPoints > this.player.getForgePoints()) {
            this.player.sendPacket(new PacketForgeStartRsp(Retcode.RET_FORGE_POINT_NOT_ENOUGH));
            return;
        }

        List<ItemParamData> material = new ArrayList<>(forgeData.getMaterialItems());
        material.add(new ItemParamData(202, forgeData.getScoinCost()));

        boolean success =
                player.getInventory().payItems(material, req.getForgeCount(), ActionReason.ForgeCost);

        if (!success) {
            this.player.sendPacket(
                    new PacketForgeStartRsp(
                            Retcode.RET_ITEM_COUNT_NOT_ENOUGH));
        }

        this.player.setForgePoints(this.player.getForgePoints() - requiredPoints);

        ActiveForgeData activeForge = new ActiveForgeData();
        activeForge.setForgeId(req.getForgeId());
        activeForge.setAvatarId(req.getAvatarId());
        activeForge.setCount(req.getForgeCount());
        activeForge.setStartTime(Utils.getCurrentSeconds());
        activeForge.setForgeTime(forgeData.getForgeTime());

        this.player.getActiveForges().add(activeForge);

        this.sendForgeQueueDataNotify();
        this.player.sendPacket(new PacketForgeStartRsp(Retcode.RET_SUCC));
    }

    private synchronized void obtainItems(int queueId) {
        int currentTime = Utils.getCurrentSeconds();
        ActiveForgeData forge = this.player.getActiveForges().get(queueId - 1);

        int finished = forge.getFinishedCount(currentTime);
        int unfinished = forge.getUnfinishedCount(currentTime);

        if (finished <= 0) {
            return;
        }

        ForgeData data = GameData.getForgeDataMap().get(forge.getForgeId());

        int resultId = data.getResultItemId() > 0 ? data.getResultItemId() : data.getShowItemId();
        ItemData resultItemData = GameData.getItemDataMap().get(resultId);
        GameItem addItem = new GameItem(resultItemData, data.getResultItemCount() * finished);

        var event = new PlayerForgeItemEvent(this.player, addItem);
        if (!event.call()) return;

        addItem = event.getItemForged();
        this.player.getInventory().addItem(addItem);

        this.player
                .getBattlePassManager()
                .triggerMission(WatcherTriggerType.TRIGGER_DO_FORGE, 0, finished);

        if (unfinished > 0) {
            ActiveForgeData remainingForge = new ActiveForgeData();

            remainingForge.setForgeId(forge.getForgeId());
            remainingForge.setAvatarId(forge.getAvatarId());
            remainingForge.setCount(unfinished);
            remainingForge.setForgeTime(forge.getForgeTime());
            remainingForge.setStartTime(forge.getStartTime() + finished * forge.getForgeTime());

            this.player.getActiveForges().set(queueId - 1, remainingForge);
            this.sendForgeQueueDataNotify();
        }
        else {
            this.player.getActiveForges().remove(queueId - 1);
            this.sendForgeQueueDataNotify(true);
        }

        this.player.sendPacket(
                new PacketForgeQueueManipulateRsp(
                        Retcode.RET_SUCC,
                        ForgeQueueManipulateType.ForgeQueueManipulateType_RECEIVE_OUTPUT,
                        List.of(addItem),
                        List.of(),
                        List.of()));
    }

    private synchronized void cancelForge(int queueId) {
        int currentTime = Utils.getCurrentSeconds();
        ActiveForgeData forge = this.player.getActiveForges().get(queueId - 1);

        if (forge.getFinishedCount(currentTime) > 0) {
            return;
        }

        ForgeData data = GameData.getForgeDataMap().get(forge.getForgeId());

        var returnItems = new ArrayList<GameItem>();
        for (var material : data.getMaterialItems()) {
            if (material.getItemId() == 0) {
                continue;
            }

            ItemData resultItemData = GameData.getItemDataMap().get(material.getItemId());
            GameItem returnItem =
                    new GameItem(resultItemData, material.getItemCount() * forge.getCount());

            this.player.getInventory().addItem(returnItem);
            returnItems.add(returnItem);
        }

        this.player.setMora(this.player.getMora() + data.getScoinCost() * forge.getCount());

        ItemData moraItem = GameData.getItemDataMap().get(202);
        GameItem returnMora = new GameItem(moraItem, data.getScoinCost() * forge.getCount());
        returnItems.add(returnMora);

        int requiredPoints = data.getForgePoint() * forge.getCount();
        int newPoints = Math.min(this.player.getForgePoints() + requiredPoints, 300_000);

        this.player.setForgePoints(newPoints);

        this.player.getActiveForges().remove(queueId - 1);
        this.sendForgeQueueDataNotify(true);

        this.player.sendPacket(
                new PacketForgeQueueManipulateRsp(
                        Retcode.RET_SUCC,
                        ForgeQueueManipulateType.ForgeQueueManipulateType_STOP_FORGE,
                        List.of(),
                        returnItems,
                        List.of()));
    }

    public synchronized void handleForgeQueueManipulateReq(ForgeQueueManipulateReq req) {
        int queueId = req.getForgeQueueId();
        var manipulateType = req.getManipulateType();

        switch (manipulateType) {
            case ForgeQueueManipulateType_RECEIVE_OUTPUT -> this.obtainItems(queueId);
            case ForgeQueueManipulateType_STOP_FORGE -> this.cancelForge(queueId);
            default -> {}
        }
    }

    public synchronized void sendPlayerForgingUpdate() {
        int currentTime = Utils.getCurrentSeconds();

        if (this.player.getActiveForges().size() <= 0) {
            return;
        }

        boolean hasChanges =
                this.player.getActiveForges().stream().anyMatch(forge -> forge.updateChanged(currentTime));

        if (!hasChanges) {
            return;
        }

        this.sendForgeQueueDataNotify();

        this.player.getActiveForges().stream().forEach(forge -> forge.setChanged(false));
    }
}
