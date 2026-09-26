package emu.grasscutter.game.entity.gadget;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.drop.DropSystem;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.gadget.chest.BossChestInteractHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.BossChestInfoOuterClass.BossChestInfo;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.InterOpTypeOuterClass.InterOpType;
import emu.grasscutter.net.proto.InteractTypeOuterClass.InteractType;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;
import emu.grasscutter.scripts.constants.ScriptGadgetState;
import emu.grasscutter.scripts.data.SceneGadget;
import emu.grasscutter.server.packet.send.*;

public class GadgetChest extends GadgetContent {

    public GadgetChest(EntityGadget gadget) {
        super(gadget);
    }

    public boolean onInteract(Player player, GadgetInteractReq req) {
        if (Grasscutter.getConfig().server.game.enableScriptInBigWorld) {
            SceneGadget chest = getGadget().getMetaGadget();
            if (chest != null) {
                DropSystem dropSystem = player.getServer().getDropSystem();
                if (chest.boss_chest != null && chest.drop_tag != null) {
                    if (req.getOpType() == InterOpType.InterOpType_INTER_OP_START) {
                        player.sendPacket(
                                new PacketGadgetInteractRsp(
                                        getGadget(),
                                        InteractType.InteractType_INTERACT_OPEN_CHEST,
                                        InterOpType.InterOpType_INTER_OP_START));
                        return false;
                    }
                    if (player.getResinManager().useResin(chest.boss_chest.resin)
                            && dropSystem.handleBossChestDrop(chest.drop_tag, player)) {
                        player
                                .getBattlePassManager()
                                .triggerMission(
                                        WatcherTriggerType.TRIGGER_WORLD_BOSS_REWARD,
                                        chest.boss_chest.monster_config_id,
                                        1);
                        getGadget().updateState(ScriptGadgetState.ChestOpened);
                        player.sendPacket(
                                new PacketGadgetInteractRsp(
                                        this.getGadget(),
                                        InteractTypeOuterClass.InteractType.InteractType_INTERACT_OPEN_CHEST,
                                        InterOpType.InterOpType_INTER_OP_FINISH));
                        return true;
                    }
                } else {
                    if (player != player.getWorld().getHost()) return false;
                    boolean status = false;
                    if (chest.drop_tag != null) {
                        status = dropSystem.handleChestDrop(chest.drop_tag, chest.level, getGadget());
                    } else if (chest.chest_drop_id != 0) {
                        status = dropSystem.handleChestDrop(chest.chest_drop_id, chest.drop_count, getGadget());
                    }

                    if (status) {
                        getGadget().updateState(ScriptGadgetState.ChestOpened);
                        player.sendPacket(
                                new PacketGadgetInteractRsp(
                                        getGadget(),
                                        InteractType.InteractType_INTERACT_OPEN_CHEST,
                                        InterOpType.InterOpType_INTER_OP_FINISH));
                        player.sendPacket(
                                new PacketWorldChestOpenNotify(
                                        getGadget().getGroupId(), player.getSceneId(), chest.config_id));
                        return true;
                    }
                }
                Grasscutter.getLogger()
                        .warn(
                                "Can not solve chest drop: chest_drop_id = {} , drop_tag = {}.Fallback to legacy drop system.",
                                chest.chest_drop_id,
                                chest.drop_tag);
            }
        }

        var chestInteractHandlerMap =
                getGadget()
                        .getScene()
                        .getWorld()
                        .getServer()
                        .getWorldDataSystem()
                        .getChestInteractHandlerMap();
        var handler = chestInteractHandlerMap.get(getGadget().getGadgetData().getJsonName());
        if (handler == null) {
            Grasscutter.getLogger()
                    .warn(
                            "Could not found the handler of this type of Chests {}",
                            getGadget().getGadgetData().getJsonName());
            return false;
        }

        if (req.getOpType() == InterOpType.InterOpType_INTER_OP_START && handler.isTwoStep()) {
            player.sendPacket(
                    new PacketGadgetInteractRsp(
                            getGadget(), InteractType.InteractType_INTERACT_OPEN_CHEST, InterOpType.InterOpType_INTER_OP_START));
            return false;
        } else {
            boolean success;
            if (handler instanceof BossChestInteractHandler bossChestInteractHandler) {
                success =
                        bossChestInteractHandler.onInteract(
                                this,
                                player,
                                req.getResinCostType()
                                        == ResinCostTypeOuterClass.ResinCostType.ResinCostType_CONDENSE);
            } else {
                success = handler.onInteract(this, player);
            }
            if (!success) {
                return false;
            }

            getGadget().updateState(ScriptGadgetState.ChestOpened);
            player.sendPacket(
                    new PacketGadgetInteractRsp(
                            this.getGadget(),
                            InteractTypeOuterClass.InteractType.InteractType_INTERACT_OPEN_CHEST,
                            InterOpType.InterOpType_INTER_OP_FINISH));

            return true;
        }
    }

    public void onBuildProto(SceneGadgetInfo.Builder gadgetInfo) {
        if (getGadget().getMetaGadget() == null) {
            return;
        }

        var bossChest = getGadget().getMetaGadget().boss_chest;
        if (bossChest != null) {
            var players = getGadget().getScene().getPlayers().stream().map(Player::getUid).toList();

            gadgetInfo.setBossChest(
                    BossChestInfo.newBuilder()
                            .setMonsterConfigId(bossChest.monster_config_id)
                            .setResin(bossChest.resin)
                            .addAllQualifyUidList(players)
                            .addAllRemainUidList(players)
                            .build());
        }
    }
}
