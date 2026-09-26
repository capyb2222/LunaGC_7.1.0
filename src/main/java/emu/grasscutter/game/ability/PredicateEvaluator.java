package emu.grasscutter.game.ability;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.ProudSkillData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import java.util.List;
import java.util.Map;

public final class PredicateEvaluator {

    private PredicateEvaluator() {}

    public static boolean all(List<Map<String, Object>> predicates, Ability ability,
                               GameEntity owner, GameEntity target, AbilityModifierAction action) {
        if (predicates == null || predicates.isEmpty()) return true;
        for (var pred : predicates) {
            if (pred == null) continue;
            if (!evaluate(pred, ability, owner, target, action)) return false;
        }
        return true;
    }

    public static boolean evaluate(Map<String, Object> pred, Ability ability,
                                    GameEntity owner, GameEntity target, AbilityModifierAction action) {
        Object typeObj = pred.get("$type");
        if (!(typeObj instanceof String type)) return true;
        GameEntity resolved = resolveTarget(pred, ability, owner, target);
        if ("BJJDEAIEIGP".equals(type)) {
            GameEntity caster = ability != null ? ability.getCasterEntity() : null;
            return hasHexenzirkelTag(caster != null ? caster : (owner != null ? owner : resolved));
        }
        if ("ByUnlockTalentParam".equals(type)) {
            GameEntity caster = ability != null ? ability.getCasterEntity() : null;
            return byUnlockTalentParam(pred, caster != null ? caster : (owner != null ? owner : resolved));
        }
        return switch (type) {
            case "ByHasModifier"      -> byHasModifier(pred, ability, resolved);
            case "ByTargetGlobalValue" -> byTargetGlobalValue(pred, ability, resolved);
            case "ByTargetHPRatio"    -> byTargetHPRatio(pred, ability, resolved);
            default -> true;
        };
    }

    private static boolean hasHexenzirkelTag(GameEntity target) {
        if (!(target instanceof EntityAvatar ea)) return false;
        Avatar avatar = ea.getAvatar();
        if (avatar == null || avatar.getAvatarData() == null) return false;
        var tags = avatar.getAvatarData().getTags();
        return tags != null && tags.contains("AVATAR_TAG_HEXENZIRKEL");
    }

    private static GameEntity resolveTarget(Map<String, Object> pred, Ability ability,
                                              GameEntity owner, GameEntity defaultTarget) {
        Object t = pred.get("target");
        if (!(t instanceof String s)) return defaultTarget;
        return switch (s) {
            case "Self", "Target", "Applier" -> defaultTarget;
            case "Owner" -> owner != null ? owner : defaultTarget;
            case "OriginOwner", "CurLocalAvatar" ->
                ability != null && ability.getPlayerOwner() != null
                    ? ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity()
                    : defaultTarget;
            case "Team" ->
                ability != null && ability.getPlayerOwner() != null
                    ? ability.getPlayerOwner().getTeamManager().getEntity()
                    : defaultTarget;
            default -> defaultTarget;
        };
    }

    private static boolean byUnlockTalentParam(Map<String, Object> pred, GameEntity target) {
        Object tp = pred.get("talentParam");
        if (!(tp instanceof String talentParam) || talentParam.isEmpty()) return false;
        if (!(target instanceof EntityAvatar ea)) return false;
        Avatar avatar = ea.getAvatar();
        if (avatar == null) return false;
        if (avatar.getProudSkillList() != null) {
            for (int proudSkillId : avatar.getProudSkillList()) {
                ProudSkillData ps = GameData.getProudSkillDataMap().get(proudSkillId);
                if (ps != null && talentParam.equals(ps.getOpenConfig())) return true;
            }
        }
        if (avatar.getTalentIdList() != null) {
            for (int talentId : avatar.getTalentIdList()) {
                var td = GameData.getAvatarTalentDataMap().get(talentId);
                if (td != null && talentParam.equals(td.getOpenConfig())) return true;
            }
        }
        return false;
    }

    private static boolean byHasModifier(Map<String, Object> pred, Ability ability, GameEntity target) {
        Object mn = pred.get("modifierName");
        if (!(mn instanceof String modifierName) || ability == null || target == null) return false;
        for (Ability ab : target.getInstancedAbilities()) {
            if (ab == null) continue;
            if (ab.getModifiers().containsKey(modifierName)) return true;
        }
        return false;
    }

    private static boolean byTargetGlobalValue(Map<String, Object> pred, Ability ability, GameEntity target) {
        if (target == null) return false;
        Object key = pred.get("key");
        if (!(key instanceof String k)) return false;
        Float stored = target.getGlobalAbilityValues().get(k);
        float current = stored != null ? stored : 0f;
        float bound = readFloat(pred.get("value"), ability, target);
        Object cmpObj = pred.get("compareType");
        String cmp = cmpObj instanceof String s ? s : "Equal";
        return switch (cmp) {
            case "MoreThan", "Greater"    -> current > bound;
            case "MoreThanAndEqual", "MoreOrEqual", "GreaterOrEqual" -> current >= bound;
            case "LessThan", "Lesser"     -> current < bound;
            case "LessAndEqual", "LessThanAndEqual", "LessOrEqual", "LesserOrEqual" -> current <= bound;
            case "Between"                -> current >= bound
                    && current <= readFloat(pred.get("maxValue"), ability, target);
            case "NoneOrEqual"            -> stored == null || current == bound;
            case "NotEqual"               -> current != bound;
            default                       -> current == bound;
        };
    }

    private static boolean byTargetHPRatio(Map<String, Object> pred, Ability ability, GameEntity target) {
        if (target == null || ability == null) return true;
        Object hpRatio = pred.get("HPRatio");
        if (!(hpRatio instanceof String key)) return true;
        float threshold = ability.getAbilitySpecials().getOrDefault(key, 0f);
        if (threshold <= 0f) return true;
        float maxHp = target.getFightProperty(emu.grasscutter.game.props.FightProperty.FIGHT_PROP_MAX_HP);
        float curHp = target.getFightProperty(emu.grasscutter.game.props.FightProperty.FIGHT_PROP_CUR_HP);
        if (maxHp <= 0f) return true;
        return curHp / maxHp > threshold;
    }

    private static float readFloat(Object v, Ability ability, GameEntity target) {
        if (v instanceof Number n) return n.floatValue();
        if (v instanceof String name) {
            if (ability != null && ability.getAbilitySpecials().containsKey(name)) {
                return ability.getAbilitySpecials().getFloat(name);
            }
            Float global = target != null ? target.getGlobalAbilityValues().get(name) : null;
            return global != null ? global : 0f;
        }
        if (v instanceof Map<?, ?> m) {
            Object inner = m.get("value");
            if (inner instanceof Number n) return n.floatValue();
            Object exp = m.get("__exp_FixedValue");
            if (exp instanceof Number n) return n.floatValue();
        }
        return 0f;
    }
}
