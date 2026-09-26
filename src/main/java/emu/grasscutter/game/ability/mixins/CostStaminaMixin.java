package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.managers.stamina.Consumption;
import emu.grasscutter.game.managers.stamina.ConsumptionType;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.game.managers.stamina.StaminaManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import emu.grasscutter.Grasscutter;

@AbilityMixin(value = AbilityMixinData.Type.CostStaminaMixin)
public class CostStaminaMixin extends AbilityMixinHandler {
    private static final long MIN_COST_INTERVAL = 500;

    @Override
    public boolean execute(Ability ability, AbilityMixinData mixinData, ByteString abilityData, GameEntity target) {
        float staminaRatio = mixinData.costStaminaDelta.get(ability);
        if (staminaRatio == 0.0f) {
            staminaRatio = 1.0f;
        }

        Player player = ability.getPlayerOwner();
        if (player == null) {
            return false;
        }

        StaminaManager staminaManager = player.getStaminaManager();
        GameSession session = player.getSession();
        long now = System.currentTimeMillis();
        long lastCostTime = staminaManager.getLastCostStaminaTime();
        long pastTime = now - lastCostTime;

        if (pastTime > MIN_COST_INTERVAL) {
            staminaManager.setLastCostStaminaTime(now);
            pastTime = 0;
        }

        int baseStaminaCost = (int) (staminaRatio * 100);
        int costStamina = -(int) ((float) pastTime / 1000 * (float) baseStaminaCost);

        Consumption consumption = new Consumption(ConsumptionType.FIGHT, costStamina);
        staminaManager.updateStaminaRelative(session, consumption, true);
        staminaManager.setLastCostStaminaTime(now);
        staminaManager.staminaRecoverDelay = 0;

        Grasscutter.getLogger().debug("MIXIN STAMINA CONSUMPTION: Skill={}, Cost={}, PastTime={}",
            ability.getData() != null ? ability.getData().abilityName : null, costStamina, pastTime);

        return true;
    }
}
