package emu.grasscutter.data.binout;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import emu.grasscutter.data.ResourceLoader.OpenConfigData;
import java.util.*;

public class OpenConfigEntry {
    private final String name;
    private String[] addAbilities;
    private int extraTalentIndex;
    private SkillPointModifier[] skillPointModifiers;
    private AbilityVarSetter[] abilityVarSetters;
    private TalentParamEntry[] talentParamEntries;

    public OpenConfigEntry(String name, OpenConfigData[] data) {
        this.name = name;

        List<String> abilityList = new ArrayList<>();
        List<SkillPointModifier> modList = new ArrayList<>();
        List<AbilityVarSetter> varList = new ArrayList<>();
        List<TalentParamEntry> paramList = new ArrayList<>();

        for (OpenConfigData entry : data) {
            if (entry.$type == null) continue;
            if (entry.$type.contains("AddAbility")
                    || entry.$type.equals("CLMGJOKFOOB")
                    || entry.$type.equals("PHNIGFHBFMD")) {
                if (entry.abilityName != null) abilityList.add(entry.abilityName);
            } else if (entry.$type.contains("AddTalentExtraLevel") || entry.$type.equals("DPACHKADLMK")) {
                if (entry.talentIndex > 0) this.extraTalentIndex = entry.talentIndex;
            } else if (entry.talentIndex > 0) {
                this.extraTalentIndex = entry.talentIndex;
            } else if (entry.$type.contains("ModifySkillPoint")) {
                modList.add(new SkillPointModifier(entry.skillID, entry.pointDelta));
            } else if (entry.$type.equals("NPLMAPHOLOF") || entry.$type.contains("SetAbilityVar")
                    || entry.$type.equals("ModifyAbility")
                    || entry.$type.equals("GMOELNAHCOH")) {

                if (entry.abilityName != null && entry.varName != null && entry.varValue != null) {
                    int paramIdx = parseParamIndex(entry.varValue);
                    if (paramIdx >= 0) {
                        varList.add(new AbilityVarSetter(entry.abilityName, entry.varName, paramIdx));
                    }
                }
            } else if (entry.$type.contains("UnlockTalentParam")
                    || entry.$type.equals("JNFAEBAAPDM")
                    || entry.$type.equals("CMGFNDNMFFO")) {
                if (entry.abilityName != null && entry.talentParam != null) {
                    paramList.add(new TalentParamEntry(entry.abilityName, entry.talentParam));
                }
            }
        }

        if (!abilityList.isEmpty()) this.addAbilities = abilityList.toArray(new String[0]);
        if (!modList.isEmpty()) this.skillPointModifiers = modList.toArray(new SkillPointModifier[0]);
        if (!varList.isEmpty()) this.abilityVarSetters = varList.toArray(new AbilityVarSetter[0]);
        if (!paramList.isEmpty()) this.talentParamEntries = paramList.toArray(new TalentParamEntry[0]);
    }

    private static int parseParamIndex(JsonElement el) {
        try {

            String val = null;
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                for (var e : obj.entrySet()) {
                    val = e.getValue().getAsString();
                    break;
                }
            } else if (el.isJsonPrimitive()) {
                val = el.getAsString();
            }
            if (val != null && val.startsWith("%")) {
                return Integer.parseInt(val.substring(1)) - 1;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    public String getName() { return name; }
    public String[] getAddAbilities() { return addAbilities; }
    public int getExtraTalentIndex() { return extraTalentIndex; }
    public SkillPointModifier[] getSkillPointModifiers() { return skillPointModifiers; }
    public AbilityVarSetter[] getAbilityVarSetters() { return abilityVarSetters; }
    public TalentParamEntry[] getTalentParamEntries() { return talentParamEntries; }

    public static class SkillPointModifier {
        private final int skillId;
        private final int delta;
        public SkillPointModifier(int skillId, int delta) { this.skillId = skillId; this.delta = delta; }
        public int getSkillId() { return skillId; }
        public int getDelta() { return delta; }
    }

    public static class AbilityVarSetter {
        private final String abilityName;
        private final String varName;
        private final int paramIndex;
        public AbilityVarSetter(String abilityName, String varName, int paramIndex) {
            this.abilityName = abilityName;
            this.varName = varName;
            this.paramIndex = paramIndex;
        }
        public String getAbilityName() { return abilityName; }
        public String getVarName() { return varName; }
        public int getParamIndex() { return paramIndex; }
    }

    public static class TalentParamEntry {
        private final String abilityName;
        private final String paramName;
        public TalentParamEntry(String abilityName, String paramName) {
            this.abilityName = abilityName;
            this.paramName = paramName;
        }
        public String getAbilityName() { return abilityName; }
        public String getParamName() { return paramName; }
    }
}
