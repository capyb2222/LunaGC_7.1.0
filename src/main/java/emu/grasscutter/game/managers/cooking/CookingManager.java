package emu.grasscutter.game.managers.cooking;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.proto.CookRecipeDataOuterClass;
import emu.grasscutter.net.proto.PlayerCookArgsReqOuterClass.PlayerCookArgsReq;
import emu.grasscutter.net.proto.PlayerCookReqOuterClass.PlayerCookReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.*;
import io.netty.util.internal.ThreadLocalRandom;
import java.util.*;

public class CookingManager extends BasePlayerManager {
    private static final int MANUAL_PERFECT_COOK_QUALITY = 3;
    private static Set<Integer> defaultUnlockedRecipies;

    public CookingManager(Player player) {
        super(player);
    }

    public static void initialize() {
        defaultUnlockedRecipies = new HashSet<>();

        for (var recipe : GameData.getCookRecipeDataMap().values()) {
            if (recipe.isDefaultUnlocked()) {
                defaultUnlockedRecipies.add(recipe.getId());
            }
        }
    }

    public boolean unlockRecipe(int id) {
        if (this.player.getUnlockedRecipies().containsKey(id)) {
            return false;
        }
        this.player.getUnlockedRecipies().put(id, 0);
        this.player.sendPacket(new PacketCookRecipeDataNotify(id));

        return true;
    }

    private double getSpecialtyChance(ItemData cookedItem) {
        return switch (cookedItem.getRankLevel()) {
            case 1 -> 0.25;
            case 2 -> 0.2;
            case 3 -> 0.15;
            default -> 0;
        };
    }

    public void handlePlayerCookReq(PlayerCookReq req) {
        int recipeId = req.getRecipeId();
        int quality = 0;
        int count = 1;
        int avatar = req.getAssistAvatar();

        var recipeData = GameData.getCookRecipeDataMap().get(recipeId);
        if (recipeData == null) {
            this.player.sendPacket(new PacketPlayerCookRsp(Retcode.RET_FAIL));
            return;
        }

        int proficiency = this.player.getUnlockedRecipies().getOrDefault(recipeId, 0);

        boolean success =
                player.getInventory().payItems(recipeData.getInputVec(), count, ActionReason.Cook);
        if (!success) {
            this.player.sendPacket(new PacketPlayerCookRsp(Retcode.RET_FAIL));
        }

        int qualityIndex = quality == 0 ? 2 : quality - 1;

        ItemParamData resultParam = recipeData.getQualityOutputVec().get(qualityIndex);
        ItemData resultItemData = GameData.getItemDataMap().get(resultParam.getItemId());

        int specialtyCount = 0;
        double specialtyChance = this.getSpecialtyChance(resultItemData);

        var bonusData = GameData.getCookBonusDataMap().get(avatar);
        if (bonusData != null && recipeId == bonusData.getRecipeId()) {
            for (int i = 0; i < count; i++) {
                if (ThreadLocalRandom.current().nextDouble() <= specialtyChance) {
                    specialtyCount++;
                }
            }
        }

        List<GameItem> cookResults = new ArrayList<>();

        int normalCount = count - specialtyCount;
        GameItem cookResultNormal = new GameItem(resultItemData, resultParam.getCount() * normalCount);
        cookResults.add(cookResultNormal);
        this.player.getInventory().addItem(cookResultNormal);

        if (specialtyCount > 0) {
            ItemData specialtyItemData = GameData.getItemDataMap().get(bonusData.getReplacementItemId());
            GameItem cookResultSpecialty =
                    new GameItem(specialtyItemData, resultParam.getCount() * specialtyCount);
            cookResults.add(cookResultSpecialty);
            this.player.getInventory().addItem(cookResultSpecialty);
        }

        if (quality == MANUAL_PERFECT_COOK_QUALITY) {
            proficiency = Math.min(proficiency + 1, recipeData.getMaxProficiency());
            this.player.getUnlockedRecipies().put(recipeId, proficiency);
        }

        this.player.sendPacket(
                new PacketPlayerCookRsp(cookResults, quality, count, recipeId, proficiency));
    }

    public void handleCookArgsReq(PlayerCookArgsReq req) {
        this.player.sendPacket(new PacketPlayerCookArgsRsp());
    }

    private void addDefaultUnlocked() {
        var unlockedRecipies = this.player.getUnlockedRecipies();

        var additionalRecipies = new HashSet<>(defaultUnlockedRecipies);
        additionalRecipies.removeAll(unlockedRecipies.keySet());

        for (int id : additionalRecipies) {
            unlockedRecipies.put(id, 0);
        }
    }

    public void sendCookDataNotify() {
        this.addDefaultUnlocked();

        var unlockedRecipes = this.player.getUnlockedRecipies();

        List<CookRecipeDataOuterClass.CookRecipeData> data = new ArrayList<>();
        unlockedRecipes.forEach(
                (recipeId, proficiency) ->
                        data.add(
                                CookRecipeDataOuterClass.CookRecipeData.newBuilder()
                                        .setRecipeId(recipeId)
                                        .setProficiency(proficiency)
                                        .build()));

        this.player.sendPacket(new PacketCookDataNotify(data));
    }
}
