package emu.grasscutter.game.props.ItemUseAction;

import emu.grasscutter.data.common.ItemUseData;
import emu.grasscutter.game.props.ItemUseOp;

public class ItemUseAction {
    public static ItemUseAction fromItemUseData(ItemUseData data) {
        var useParam = data.getUseParam();
        return switch (data.getUseOp()) {
            case ITEM_USE_NONE -> null;
            case ITEM_USE_ADD_EXP -> new ItemUseAddExp(useParam);
            case ITEM_USE_ADD_RELIQUARY_EXP -> new ItemUseAddReliquaryExp(useParam);
            case ITEM_USE_ADD_WEAPON_EXP -> new ItemUseAddWeaponExp(useParam);
            case ITEM_USE_ADD_ALL_ENERGY -> new ItemUseAddAllEnergy(useParam);
            case ITEM_USE_ADD_ELEM_ENERGY -> new ItemUseAddElemEnergy(useParam);
            case ITEM_USE_ADD_ITEM -> new ItemUseAddItem(useParam);
            case ITEM_USE_GAIN_AVATAR -> new ItemUseGainAvatar(useParam);
            case ITEM_USE_GAIN_COSTUME -> new ItemUseGainCostume(useParam);
            case ITEM_USE_GAIN_FLYCLOAK -> new ItemUseGainFlycloak(useParam);
            case ITEM_USE_GAIN_NAME_CARD -> new ItemUseGainNameCard(useParam);
            case ITEM_USE_UNLOCK_AVATAR_TRACE -> new ItemUseGainTraceEffect(useParam);
            case ITEM_USE_CHEST_SELECT_ITEM -> new ItemUseChestSelectItem(useParam);
            case ITEM_USE_ADD_SELECT_ITEM -> new ItemUseAddSelectItem(useParam);
            case ITEM_USE_GRANT_SELECT_REWARD -> new ItemUseGrantSelectReward(useParam);
            case ITEM_USE_COMBINE_ITEM -> new ItemUseCombineItem(useParam);
            case ITEM_USE_OPEN_RANDOM_CHEST -> new ItemUseOpenRandomChest(useParam);
            case ITEM_USE_RELIVE_AVATAR -> new ItemUseReliveAvatar(
                    useParam);
            case ITEM_USE_ADD_CUR_HP -> new ItemUseAddCurHp(useParam);
            case ITEM_USE_ADD_CUR_STAMINA -> new ItemUseAddCurStamina(useParam);
            case ITEM_USE_ADD_SERVER_BUFF -> new ItemUseAddServerBuff(useParam);
            case ITEM_USE_MAKE_GADGET -> new ItemUseMakeGadget(useParam);
            case ITEM_USE_UNLOCK_COMBINE -> new ItemUseUnlockCombine(useParam);
            case ITEM_USE_UNLOCK_CODEX -> new ItemUseUnlockCodex(
                    useParam);
            case ITEM_USE_UNLOCK_COOK_RECIPE -> new ItemUseUnlockCookRecipe(useParam);
            case ITEM_USE_UNLOCK_FORGE -> new ItemUseUnlockForge(useParam);
            case ITEM_USE_UNLOCK_FURNITURE_FORMULA -> new ItemUseUnlockFurnitureFormula(useParam);
            case ITEM_USE_UNLOCK_FURNITURE_SUITE -> new ItemUseUnlockFurnitureSuite(useParam);
            case ITEM_USE_UNLOCK_HOME_MODULE -> new ItemUseUnlockHomeModule(
                    useParam);
            case ITEM_USE_UNLOCK_HOME_BGM -> new ItemUseUnlockHomeBgm(useParam);
            case ITEM_USE_ACCEPT_QUEST -> new ItemUseAcceptQuest(useParam);
            case ITEM_USE_GAIN_CARD_PRODUCT -> new ItemUseGainCardProduct(useParam);
            case ITEM_USE_UNLOCK_PAID_BATTLE_PASS_NORMAL -> new ItemUseUnlockPaidBattlePassNormal(
                    useParam);

            case ITEM_USE_DEL_SERVER_BUFF -> null;
            case ITEM_USE_ADD_BIG_TALENT_POINT -> null;
            case ITEM_USE_GAIN_RESIN_CARD_PRODUCT -> null;
            case ITEM_USE_TRIGGER_ABILITY -> null;
            case ITEM_USE_ADD_TREASURE_MAP_BONUS_REGION_FRAGMENT -> null;
            case ITEM_USE_ADD_PERSIST_STAMINA -> null;
            case ITEM_USE_ADD_TEMPORARY_STAMINA -> null;
            case ITEM_USE_ADD_DUNGEON_COND_TIME -> null;
            case ITEM_USE_ADD_CHANNELLER_SLAB_BUFF -> null;
            case ITEM_USE_ADD_REGIONAL_PLAY_VAR -> null;
        };
    }

    public ItemUseOp getItemUseOp() {
        return ItemUseOp.ITEM_USE_NONE;
    }

    public boolean useItem(UseItemParams params) {
        return false;
    }

    public boolean postUseItem(UseItemParams params) {
        return false;
    }
}
