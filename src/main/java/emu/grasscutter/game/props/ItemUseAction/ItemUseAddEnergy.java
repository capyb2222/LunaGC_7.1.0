package emu.grasscutter.game.props.ItemUseAction;

import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;

public abstract class ItemUseAddEnergy extends ItemUseAction {
    public abstract float getAddEnergy(ElementType avatarElement);

    public float getAddEnergy(AvatarSkillDepotData depot) {
        if (depot == null) return 0f;
        var element = depot.getElementType();
        if (element == null) return 0f;
        return this.getAddEnergy(element);
    }

    @Override
    public boolean useItem(UseItemParams params) {
        var teamManager = params.player.getTeamManager();
        return switch (params.itemUseTarget) {
            case ITEM_USE_TARGET_CUR_AVATAR -> {
                this.addEnergy(teamManager.getCurrentAvatarEntity().getAvatar(), params.count);
                yield true;
            }
            case ITEM_USE_TARGET_CUR_TEAM -> {
                var activeTeam = teamManager.getActiveTeam();
                final float offFieldRatio =
                        switch (activeTeam.size()) {
                            case 2 -> 0.8f;
                            case 3 -> 0.7f;
                            default -> 0.6f;
                        };
                final int currentCharacterIndex = teamManager.getCurrentCharacterIndex();

                for (int i = 0; i < activeTeam.size(); i++) {
                    var avatar = activeTeam.get(i).getAvatar();
                    if (i == currentCharacterIndex) this.addEnergy(avatar, params.count);
                    else this.addEnergy(avatar, params.count * offFieldRatio);
                }

                yield true;
            }
            case ITEM_USE_TARGET_SPECIFY_AVATAR,
                    ITEM_USE_TARGET_SPECIFY_ALIVE_AVATAR,
                    ITEM_USE_TARGET_SPECIFY_DEAD_AVATAR -> this.addEnergy(
                    params.targetAvatar, params.count);
            case ITEM_USE_TARGET_NONE -> false;
        };
    }

    private boolean addEnergy(Avatar avatar, float multiplier) {
        float energy = this.getAddEnergy(avatar.getSkillDepot()) * multiplier;
        if (energy < 0.01f) return false;
        avatar.getAsEntity().addEnergy(energy, PropChangeReason.PropChangeReason_PROP_CHANGE_ENERGY_BALL);
        return true;
    }
}
