package emu.grasscutter.data.binout;

import com.google.gson.*;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import emu.grasscutter.utils.JsonAdapters;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

public class AbilityMixinData implements Serializable {
    private static final long serialVersionUID = -2001232313615923575L;

    public enum Type {
        AttachToGadgetStateMixin,
        AttachToStateIDMixin,
        SetGadgetStateV2,
        ShieldBarMixin,
        AvatarCombatMixin,
        DoActionByEventMixin,
        DoActionByKillingMixin,
        SkillButtonHoldChargeMixin,
        GlobalSubShieldMixin,
        TileAttackMixin,
        SwitchHealToHPDebtsMixin,
        AttachModifierToGlobalValueMixin,
        DJLJLPAFPGN,
        KENHGCLICPB,
        DoActionOnGlobalValueChangeMixin,
        CurLocalAvatarMixin,
        NyxCostMixin,
        ModifyDamageMixin,
        AvatarChangeSkillMixin,
        @SerializedName(value = "KHOENFHDFJE", alternate = {"AttachModifierToPhlogistonMixin"})
        KHOENFHDFJE,
        @SerializedName(value = "HJKDMEOOBDK", alternate = {"GOBNKFIFGFJ"})
        HJKDMEOOBDK,
        @SerializedName(value = "FIGCOCJJHCH", alternate = {"AKFJKJBCFKI"})
        FIGCOCJJHCH,
        DMKDPHHJENO,
        LAAJCBLNLDO,
        CameraBlurMixin,
        AttachToNormalizedTimeMixin,
        AttachToMultiNormalizedTimeMixin,
        DLJBCMKDMEK,
        PhlogistonCostMixin,
        @SerializedName(value = "FIHACJPNNED", alternate = {"SkillCanUseByLTMixin"})
        FIHACJPNNED,
        JMEOJHGPNMB,
        AttachModifierToSelfGlobalValueMixin,
        AttachActionToModifierMixin,
        AttachModifierToSelfGlobalValueNoInitMixin,
        HPDebtsMixin,
        LimitHpDebtsByTagMixin,
        TileAttackManagerMixin,
        CostStaminaMixin,
        DoActionByEnergyChangeMixin,
        RejectAttackMixin,
        DoActionByTargetsCountMixin,
        AttachToAbilityStateMixin,
        IPIBBIDFDOL,
        EBBCNBHOAIP,
        ReviveElemEnergyMixin,
        HKGPGGJAKGL,
        ENPGGGNLLJG,
        PBOJOFIGPIC,
        IEABBMGDJHC,
        ModifyBeHitDamageMixin,
        DoActionByCreateGadgetMixin,
        MuteHitEffectMixin,
        EntityInVisibleMixin,
        DDCOPGJBHLB,
        @SerializedName(value = "IBAMBHPLNNA", alternate = {"ShaderLerpMixin"})
        IBAMBHPLNNA,
        @SerializedName(value = "PCKKGOMJIKL", alternate = {"DisableNyxBarMixin"})
        PCKKGOMJIKL,
        TriggerPostProcessEffectMixin,
        JGOOOFOCJBI,
        OOAMMMJMKPD,
        EFDAMNIDHDC,
        JMJFEPHFFFN,
        AttachToAnimatorStateIDMixin,
        AvatarSteerByCameraMixin,
        ModifyDamageCountMixin,
        AttackCostElementMixin,
        OnAvatarUseSkillMixin,
        DoActionByElementReactionMixin,
        DoActionBySelfElementReactionMixin,
        CurLocalAvatarMixinV2
    }
    public AbilityModifierAction[] idontknowwhattonamethis;
    public AbilityModifierAction[] idontknowwhattonamethis2;

    @SerializedName("onEnterCombat")
    public AbilityModifierAction[] onEnterCombat;

    @SerializedName("onExitCombat")
    public AbilityModifierAction[] onExitCombat;

    @SerializedName("onTriggerSkill")
    public AbilityModifierAction[] onTriggerSkill;

    @SerializedName(value = "onTriggerUltimateSkill", alternate = {"HMBEKPDBCEK"})
    public AbilityModifierAction[] onTriggerUltimateSkill;

    public AbilityModifierAction[] IOKPLLOKGGJ;
    
    @SerializedName("onKill")
    public AbilityModifierAction[] onKill;
    
    @SerializedName("successActions")
    public AbilityModifierAction[] successActions;

    @SerializedName(value = "succActions", alternate = {"CMEPEHIJMPL"})
    public AbilityModifierAction[] succActions;

    @SerializedName("actions")
    public AbilityModifierAction[] actions;

    @SerializedName("actionQueue")
    public AbilityModifierAction[] actionQueue;

    @SerializedName(value = "reactionTypes", alternate = {"IGAMNNAADJB"})
    public List<String> reactionTypes = new ArrayList<>();
    public List<String> entityTypes = new ArrayList<>();
    public List<String> attackTags = new ArrayList<>();

    @SerializedName("$type")
    public Type type;

    public JsonElement modifierName;


    public DynamicFloat speed = DynamicFloat.ZERO;
    public DynamicFloat costStaminaDelta = DynamicFloat.ZERO;
    public DynamicFloat ratio = DynamicFloat.ONE;
    public DynamicFloat detectWindow = DynamicFloat.ONE;
    public String globalValueKey;
    public List<String> stateIDs = new ArrayList<>();
    public String stateID;
    public DynamicFloat defaultGlobalValueOnCreate = DynamicFloat.ZERO;
    public List<DynamicFloat> ratioSteps = new ArrayList<>();
    public List<DynamicFloat> valueSteps = new ArrayList<>();
    public String globalValueTarget;
    @SerializedName(value = "removeAppliedModifier", alternate = {"DEFOFMOIAAI"})
    public boolean removeAppliedModifier = true;
    @JsonAdapter(JsonAdapters.ModifierNameStepsAdapter.class)
    public List<String> modifierNameSteps = new ArrayList<>();
    public boolean EJEMBMFPBKF = true;
    public boolean isCheckOnAttach = true;
    public boolean AMFABNCKJNG = true;
    public boolean forceStopWhenRemoved = true;
    public boolean FKAJIEOFOAB = true;
    public List<String> getModifierNames() {
        if (modifierName.isJsonArray()) {
            java.lang.reflect.Type listType = (new TypeToken<List<String>>() {}).getType();
            List<String> list = (new Gson()).fromJson(modifierName, listType);
            return list;
        } else {
            return Arrays.asList(modifierName.getAsString());
        }
    }
}