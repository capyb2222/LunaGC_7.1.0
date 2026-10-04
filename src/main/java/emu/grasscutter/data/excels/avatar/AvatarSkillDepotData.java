package emu.grasscutter.data.excels.avatar;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import emu.grasscutter.data.ResourceLoader.AvatarConfig;
import emu.grasscutter.data.ResourceType.LoadPriority;
import emu.grasscutter.data.binout.AbilityEmbryoEntry;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import java.util.stream.IntStream;
import lombok.Getter;

@ResourceType(name = "AvatarSkillDepotExcelConfigData.json", loadPriority = LoadPriority.HIGH)
@Getter
public class AvatarSkillDepotData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int energySkill;
    private int attackModeSkill;

    private List<Integer> skills;
    private List<Integer> subSkills;
    private List<String> extraAbilities;
    private List<Integer> talents;

    @SerializedName(
            value = "inherentProudSkillOpens",
            alternate = {"BOIOJNENKHP"})
    private List<InherentProudSkillOpens> inherentProudSkillOpens;

    @SerializedName(
            value = "specialProudSkillOpens",
            alternate = {"DAEIJGCFNLL", "NMKACHALCPO", "EIBOFEEGGID"})
    private List<SpecialProudSkillOpens> specialProudSkillOpens;

    private String talentStarName;
    private String skillDepotAbilityGroup;

    private AvatarSkillData energySkillData;
    private ElementType elementType;
    private IntList abilities;
    private int talentCostItemId;
    @Getter private IntList questProudSkillGroupIds;

    public void setAbilities(AbilityEmbryoEntry info) {
        this.abilities = new IntArrayList(info.getAbilities().length);
        for (String ability : info.getAbilities()) {
            this.abilities.add(Utils.abilityHash(ability));
        }
    }

    @Override
    public void onLoad() {
        this.energySkillData = GameData.getAvatarSkillDataMap().get(this.energySkill);
        if (this.energySkillData != null) {
            this.elementType = this.energySkillData.getCostElemType();
        } else {
            this.elementType = ElementType.None;
        }
        if (getSkillDepotAbilityGroup() != null && getSkillDepotAbilityGroup().length() > 0) {
            AvatarConfig config = GameDepot.getPlayerAbilities().get(getSkillDepotAbilityGroup());

            if (config != null) {
                this.setAbilities(
                        new AbilityEmbryoEntry(
                                getSkillDepotAbilityGroup(),
                                config.abilities.stream().map(Object::toString).toArray(String[]::new)));
            }
        }

        if (this.inherentProudSkillOpens == null) {
            this.inherentProudSkillOpens = List.of();
        }

        this.questProudSkillGroupIds = (this.specialProudSkillOpens == null) ? IntLists.EMPTY_LIST :
            new IntArrayList(
                this.specialProudSkillOpens.stream()
                    .mapToInt(SpecialProudSkillOpens::getProudSkillGroupId)
                    .filter(id -> id > 0)
                    .toArray());

        Optional.ofNullable(this.talents)
                .map(talents -> talents.get(0))
                .map(i -> GameData.getAvatarTalentDataMap().get((int) i))
                .map(talentData -> talentData.getMainCostItemId())
                .ifPresent(itemId -> this.talentCostItemId = itemId);
    }

    public IntStream getSkillsAndEnergySkill() {
        var skillIds =
                IntStream.concat(this.skills.stream().mapToInt(i -> i), IntStream.of(this.energySkill))
                        .filter(skillId -> skillId > 0)
                        .toArray();
        if (skillIds.length == 0 && this.attackModeSkill > 0) {
            return IntStream.of(this.attackModeSkill);
        }
        return IntStream.of(skillIds);
    }

    @Getter
    public static class InherentProudSkillOpens {
        private int proudSkillGroupId;

        @SerializedName(
                value = "needAvatarPromoteLevel",
                alternate = {"CCHNLJKDDKI"})
        private int needAvatarPromoteLevel;
    }

    @Getter
    public static class SpecialProudSkillOpens {
        private int proudSkillGroupId;
    }
}
