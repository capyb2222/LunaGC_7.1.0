package emu.grasscutter.game.player;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import dev.morphia.annotations.*;
import emu.grasscutter.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.config.ConfigLevelEntity;
import emu.grasscutter.data.binout.config.fields.ConfigAbilityData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.tps.TpsAvatarSystem;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.EnterTypeOuterClass.EnterType;
import emu.grasscutter.net.proto.MotionStateOuterClass.MotionState;
import emu.grasscutter.net.proto.PlayerDieTypeOuterClass.PlayerDieType;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.GrantReasonOuterClass.GrantReason;
import emu.grasscutter.server.event.entity.EntityCreationEvent;
import emu.grasscutter.server.event.player.*;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.*;
import java.util.stream.Stream;
import lombok.*;

@Entity
public final class TeamManager extends BasePlayerDataManager {
    public static final int MOONSIGN_MAX_LEVEL = 2;
    public static final String VERDANT_DEW = "MoonOvergrowPoint_All";

    @Transient private final List<EntityAvatar> avatars;
    @Transient @Getter private final Set<EntityBaseGadget> gadgets;
    @Transient @Getter private final IntSet teamResonances;
    @Transient @Getter private final IntSet teamResonancesConfig;
    @Transient @Getter @Setter private Set<String> teamAbilityEmbryos;

    @Getter private LinkedHashMap<Integer, TeamInfo> teams;
    private int currentTeamIndex;
    @Getter @Setter private int currentCharacterIndex;
    @Transient @Getter @Setter private TeamInfo mpTeam;
    @Transient @Getter @Setter private EntityTeam entity;

    @Transient private int useTemporarilyTeamIndex = -1;
    @Transient private List<TeamInfo> temporaryTeam;
    @Transient @Getter @Setter private boolean usingTrialTeam;
    @Transient @Getter @Setter private TeamInfo trialAvatarTeam;

    @Transient @Getter @Setter private Map<Integer, Avatar> trialAvatars;

    @Transient @Getter @Setter
    private int previousIndex = -1;

    public TeamManager() {
        this.mpTeam = new TeamInfo();
        this.avatars = Collections.synchronizedList(new ArrayList<>());
        this.gadgets = new HashSet<>();
        this.teamResonances = new IntOpenHashSet();
        this.teamResonancesConfig = new IntOpenHashSet();
        this.teamAbilityEmbryos = new HashSet<>();
        this.trialAvatars = new HashMap<>();
        this.trialAvatarTeam = new TeamInfo();
    }

    public TeamManager(Player player) {
        this();
        this.setPlayer(player);

        this.teams = new LinkedHashMap<>();
        this.currentTeamIndex = 1;
        for (int i = 1; i <= GameConstants.DEFAULT_TEAMS; i++) {
            this.teams.put(i, new TeamInfo());
        }
    }

    public AbilityControlBlockOuterClass.AbilityControlBlock getAbilityControlBlock() {
        AbilityControlBlockOuterClass.AbilityControlBlock.Builder abilityControlBlock =
            AbilityControlBlockOuterClass.AbilityControlBlock.newBuilder();
        int embryoId = 0;

        if (Arrays.stream(GameConstants.DEFAULT_TEAM_ABILITY_STRINGS).count() > 0) {
            boolean inNatlan = player.getScene() != null && player.getScene().getId() == 101;
            List<String> teamAbilties =
                Arrays.stream(GameConstants.DEFAULT_TEAM_ABILITY_STRINGS).toList();
            for (String skill : teamAbilties) {
                if ("DynamicAbility_Phlogiston".equals(skill) && !inNatlan) continue;
                AbilityEmbryoOuterClass.AbilityEmbryo emb =
                    AbilityEmbryoOuterClass.AbilityEmbryo.newBuilder()
                        .setAbilityId(++embryoId)
                        .setAbilityNameHash(Utils.abilityHash(skill))
                        .setAbilityOverrideNameHash(GameConstants.DEFAULT_ABILITY_NAME)
                        .build();
                abilityControlBlock.addAbilityEmbryoList(emb);
            }
        }

        var scene = player.getScene();
        if (scene != null) {
            String configName = scene.getSceneData().getLevelEntityConfig();
            if (configName != null && !configName.isEmpty()) {
                ConfigLevelEntity levelConfig = GameData.getConfigLevelEntityDataMap().get(configName);
                if (levelConfig != null && levelConfig.getTeamAbilities() != null) {
                    for (ConfigAbilityData ability : levelConfig.getTeamAbilities()) {
                        String skill = ability.getAbilityName();
                        if (skill == null || skill.isEmpty()) {
                            Grasscutter.getLogger().warn("{} 中存在空能力名称", configName);
                            continue;
                        }
                        AbilityEmbryoOuterClass.AbilityEmbryo emb =
                            AbilityEmbryoOuterClass.AbilityEmbryo.newBuilder()
                                .setAbilityId(++embryoId)
                                .setAbilityNameHash(Utils.abilityHash(skill))
                                .setAbilityOverrideNameHash(GameConstants.DEFAULT_ABILITY_NAME)
                                .build();
                        abilityControlBlock.addAbilityEmbryoList(emb);
                    }
                }
            }
        }

        return abilityControlBlock.build();
    }

    public World getWorld() {
        return this.getPlayer().getWorld();
    }

    public int getTeamId(TeamInfo team) {
        for (int i = 1; i <= this.teams.size(); i++) {
            if (this.teams.get(i).equals(team)) {
                return i;
            }
        }
        return -1;
    }

    public int getCurrentTeamId() {

        return currentTeamIndex;
    }

    private void setCurrentTeamId(int currentTeamIndex) {
        this.currentTeamIndex = currentTeamIndex;
    }

    public long getCurrentCharacterGuid() {
        var currentAvatarEntity = this.getCurrentAvatarEntity();
        if (currentAvatarEntity == null){

            Avatar mainCharacter = new Avatar(this.getPlayer().getMainCharacterId());
            return mainCharacter.getGuid();
        }

        return currentAvatarEntity.getAvatar().getGuid();
    }

    public TeamInfo getCurrentTeamInfo() {
        if (temporaryTeam != null
                && useTemporarilyTeamIndex >= 0
                && useTemporarilyTeamIndex < temporaryTeam.size()) {
            return temporaryTeam.get(useTemporarilyTeamIndex);
        }
        if (this.getPlayer().isInMultiplayer()) {
            return this.getMpTeam();
        }
        return this.getTeams().get(this.currentTeamIndex);
    }

    public TeamInfo getCurrentSinglePlayerTeamInfo() {
        return this.getTeams().get(this.currentTeamIndex);
    }

    public boolean removeUnownedAvatarsFromTeams() {
        var owned = this.getPlayer().getAvatars();
        boolean changed = false;
        for (TeamInfo team : this.getTeams().values()) {
            changed |= team.getAvatars().removeIf(id -> owned.getAvatarById(id) == null);
            if (team.getAvatars().isEmpty() && team == this.getCurrentSinglePlayerTeamInfo()) {
                var main = owned.getAvatarById(this.getPlayer().getMainCharacterId());
                if (main != null) team.getAvatars().add(main.getAvatarId());
            }
        }
        return changed;
    }

    public List<EntityAvatar> getActiveTeam() {
        return avatars;
    }

    public List<EntityAvatar> getActiveTeam(boolean fix) {
        if (!fix) return this.getActiveTeam();

        var avatars = this.getActiveTeam();
        var avatarIds = new HashSet<Long>();
        for (var entityAvatar : new ArrayList<>(avatars)) {
            if (avatarIds.contains(entityAvatar.getAvatar().getGuid())) {
                avatars.remove(entityAvatar);
            } else {
                avatarIds.add(entityAvatar.getAvatar().getGuid());
            }
        }

        return avatars;
    }

    public EntityAvatar getCurrentAvatarEntity() {

        if (this.getActiveTeam().isEmpty()) {
            this.currentCharacterIndex = 0;
            return null;
        }

        if (this.currentCharacterIndex >= this.getActiveTeam().size()) {
            this.currentCharacterIndex = 0;
        }

        EntityAvatar currentCharacter;

        try {
            currentCharacter = this.getActiveTeam().get(this.currentCharacterIndex);
        } catch (Exception e) {
            currentCharacter = this.getActiveTeam().get(0);
        }

        return currentCharacter;
    }

    public boolean isSpawned() {
        return this.getPlayer().getScene() != null
            && this.getPlayer()
            .getScene()
            .getEntities()
            .containsKey(this.getCurrentAvatarEntity().getId());
    }

    public int getMaxTeamSize() {
        if (this.getPlayer().isInMultiplayer()) {
            int max = GAME_OPTIONS.avatarLimits.multiplayerTeam;
            if (this.getPlayer().getWorld().getHost() == this.getPlayer()) {
                return Math.max(1, (int) Math.ceil(max / (double) this.getWorld().getPlayerCount()));
            }
            return Math.max(1, (int) Math.floor(max / (double) this.getWorld().getPlayerCount()));
        }

        return GAME_OPTIONS.avatarLimits.singlePlayerTeam;
    }

    public boolean canAddAvatarsToTeam(TeamInfo team, int avatars) {
        return team.size() + avatars <= this.getMaxTeamSize();
    }

    public boolean canAddAvatarToTeam(TeamInfo team) {
        return this.canAddAvatarsToTeam(team, 1);
    }

    public boolean canAddAvatarsToCurrentTeam(int avatars) {
        if (this.useTemporarilyTeamIndex != -1) {
            return false;
        }
        return this.canAddAvatarsToTeam(this.getCurrentTeamInfo(), avatars);
    }

    public boolean canAddAvatarToCurrentTeam() {
        return this.canAddAvatarsToCurrentTeam(1);
    }

    public boolean addAvatarsToTeam(TeamInfo team, Collection<Avatar> avatars) {
        if (!this.canAddAvatarsToTeam(team, avatars.size())) {
            return false;
        }

        team.getAvatars().addAll(avatars.stream().map(a -> a.getAvatarId()).toList());

        if (this.getPlayer().isInMultiplayer()) {
            if (team.equals(this.getMpTeam())) {

                this.updateTeamEntities(new PacketChangeMpTeamAvatarRsp(this.getPlayer(), team));
            }
        } else {

            this.getPlayer().sendPacket(new PacketAvatarTeamUpdateNotify(this.getPlayer()));

            int teamId = this.getTeamId(team);
            if (teamId != -1) {

                if (teamId == this.getCurrentTeamId()) {
                    this.updateTeamEntities(new PacketSetUpAvatarTeamRsp(this.getPlayer(), teamId, team));
                } else {
                    this.getPlayer().sendPacket(new PacketSetUpAvatarTeamRsp(this.getPlayer(), teamId, team));
                }
            }
        }

        return true;
    }

    public boolean addAvatarToTeam(TeamInfo team, Avatar avatar) {
        return this.addAvatarsToTeam(team, Collections.singleton(avatar));
    }

    public boolean addAvatarsToCurrentTeam(Collection<Avatar> avatars) {
        if (this.useTemporarilyTeamIndex != -1) {
            return false;
        }
        return this.addAvatarsToTeam(this.getCurrentTeamInfo(), avatars);
    }

    public boolean addAvatarToCurrentTeam(Avatar avatar) {
        return this.addAvatarsToCurrentTeam(Collections.singleton(avatar));
    }

    private void updateTeamResonances() {
        this.getTeamResonances().clear();
        this.getTeamResonancesConfig().clear();

        if (this.avatars.size() < 4) return;

        var elementCounts = new Object2IntOpenHashMap<ElementType>();
        this.getActiveTeam().stream()
            .map(EntityAvatar::getAvatar)
            .filter(Objects::nonNull)
            .map(Avatar::getSkillDepot)
            .filter(Objects::nonNull)
            .map(AvatarSkillDepotData::getElementType)
            .filter(Objects::nonNull)
            .forEach(elementType -> elementCounts.addTo(elementType, 1));

        elementCounts.object2IntEntrySet().stream()
            .filter(e -> e.getIntValue() >= 2)
            .map(e -> e.getKey())
            .filter(elementType -> elementType.getTeamResonanceId() != 0)
            .forEach(
                elementType -> {
                    this.teamResonances.add(elementType.getTeamResonanceId());
                    this.teamResonancesConfig.add(elementType.getConfigHash());
                });

        if (elementCounts.size() >= 4) {
            this.teamResonances.add(ElementType.Default.getTeamResonanceId());
            this.teamResonancesConfig.add(ElementType.Default.getConfigHash());
        }
    }

    public int getMoonsignLevel() {
        long count = this.getActiveTeam().stream()
            .filter(e -> PacketPlayerEnterSceneInfoNotify.getMoonphaseIds().contains(e.getAvatar().getAvatarId()))
            .count();

        return (int) Math.min(count, MOONSIGN_MAX_LEVEL);
    }

    public void sendMoonsignState() {
        int level = this.getMoonsignLevel();
        int teamEntityId = this.getEntity().getId();

        this.getEntity().getGlobalAbilityValues().put("SGV_MoonPhaseLevel", (float) level);
        this.getPlayer().sendPacket(new PacketServerGlobalValueChangeNotify(
            teamEntityId, "SGV_MoonPhaseLevel", (float) level));

        this.getPlayer().sendPacket(new PacketTeamMoonPhaseChangeNotify(level));
    }

    public void updateTeamProperties() {
        this.updateTeamResonances();
        this.getWorld()
            .broadcastPacket(
                new PacketSceneTeamUpdateNotify(
                    this.getPlayer()));

        this.getActiveTeam().stream()
            .map(EntityAvatar::getAvatar)
            .forEach(Avatar::sendSkillExtraChargeMap);

        this.sendMoonsignState();

        long hexenzirkelCount = this.getActiveTeam().stream()
            .filter(e -> PacketPlayerEnterSceneInfoNotify.getHexenzirkelIds().contains(e.getAvatar().getAvatarId()))
            .count();
        this.getEntity().getGlobalAbilityValues().put("SGV_HexenzirkelLevel", (float) hexenzirkelCount);
        this.getPlayer().sendPacket(new PacketServerGlobalValueChangeNotify(
            this.getEntity().getId(), "SGV_HexenzirkelLevel", (float) hexenzirkelCount));
        this.getPlayer().sendPacket(new PacketTeamHexenzirkelChangeNotify((int) hexenzirkelCount));

        var abilityManager = this.getPlayer().getAbilityManager();
        if (abilityManager != null) {
            for (var avatarEntity : this.getActiveTeam()) {
                abilityManager.refreshGlobalValueWatchers(avatarEntity);
            }
        }
    }

    public void updateTeamEntities(BasePacket responsePacket) {

        if (this.getCurrentTeamInfo().getAvatars().size() <= 0) {
            return;
        }

        var currentEntity = this.getCurrentAvatarEntity();
        var existingAvatars = new Int2ObjectOpenHashMap<EntityAvatar>();
        var prevSelectedAvatarIndex = -1;

        for (EntityAvatar entity : this.getActiveTeam()) {
            existingAvatars.put(entity.getAvatar().getAvatarId(), entity);
        }

        this.getActiveTeam().clear();

        for (int i = 0; i < this.getCurrentTeamInfo().getAvatars().size(); i++) {
            var avatarId = (int) this.getCurrentTeamInfo().getAvatars().get(i);
            EntityAvatar entity;
            if (existingAvatars.containsKey(avatarId)) {
                entity = existingAvatars.get(avatarId);
                existingAvatars.remove(avatarId);
                if (entity == currentEntity) {
                    prevSelectedAvatarIndex = i;
                }
            } else {
                var player = this.getPlayer();
                entity =
                    EntityCreationEvent.call(
                        EntityAvatar.class,
                        new Class<?>[] {Scene.class, Avatar.class},
                        new Object[] {player.getScene(), player.getAvatars().getAvatarById(avatarId)});
            }

            this.getActiveTeam().add(entity);
        }

        for (var entity : existingAvatars.values()) {
            if (entity == currentEntity) {
                entity.getAvatar().save();
                continue;
            }
            this.getPlayer().getScene().removeEntity(entity);
            entity.getAvatar().save();
        }

        if (prevSelectedAvatarIndex == -1) {

            prevSelectedAvatarIndex =
                Math.min(this.currentCharacterIndex, this.getActiveTeam().size() - 1);
        }
        this.currentCharacterIndex = prevSelectedAvatarIndex;

        this.updateTeamProperties();

        if (responsePacket != null) {
            this.getPlayer().sendPacket(responsePacket);
        }

        checkCurrentAvatarIsAlive(currentEntity);
    }

    public void checkCurrentAvatarIsAlive(EntityAvatar currentEntity) {
        if (currentEntity == null) {
            currentEntity = this.getCurrentAvatarEntity();
        }

        if (!this.getActiveTeam().get(this.currentCharacterIndex).isAlive()) {

            int replaceIndex = getDeadAvatarReplacement();
            if (0 <= replaceIndex && replaceIndex < this.getActiveTeam().size()) {
                this.currentCharacterIndex = replaceIndex;
            } else {

                this.currentCharacterIndex = 0;
                this.reviveAvatar(this.getCurrentAvatarEntity().getAvatar());
            }
        }

        var newAvatarEntity = this.getCurrentAvatarEntity();
        if (currentEntity != null && newAvatarEntity != null && currentEntity != newAvatarEntity) {

            var event =
                new PlayerSwitchAvatarEvent(
                    this.getPlayer(), currentEntity.getAvatar(), newAvatarEntity.getAvatar());
            if (!event.call()) return;

            this.getPlayer().getScene().replaceEntity(currentEntity, newAvatarEntity);
        }
    }

    public synchronized void setupAvatarTeam(int teamId, List<Long> list) {

        if (list.isEmpty()
            || list.size() > this.getMaxTeamSize()
            || this.getPlayer().isInMultiplayer()) {
            return;
        }

        List<Integer> team = new ArrayList<>();
        team.add(teamId);

        TeamInfo teamInfo = this.getTeams().get(team.size());
        if (teamInfo == null) {
            return;
        }

        LinkedHashSet<Avatar> newTeam = new LinkedHashSet<>();
        for (Long aLong : list) {
            Avatar avatar = this.getPlayer().getAvatars().getAvatarByGuid(aLong);
            if (avatar == null || newTeam.contains(avatar)) {

                return;
            }
            newTeam.add(avatar);
        }

        teamInfo.getAvatars().clear();
        this.addAvatarsToTeam(teamInfo, newTeam);
    }

    public void setupMpTeam(List<Long> list) {

        if (list.size() == 0
            || list.size() > this.getMaxTeamSize()
            || !this.getPlayer().isInMultiplayer()) {
            return;
        }

        TeamInfo teamInfo = this.getMpTeam();

        LinkedHashSet<Avatar> newTeam = new LinkedHashSet<>();
        for (Long aLong : list) {
            Avatar avatar = this.getPlayer().getAvatars().getAvatarByGuid(aLong);
            if (avatar == null || newTeam.contains(avatar)) {

                return;
            }
            newTeam.add(avatar);
        }

        teamInfo.getAvatars().clear();
        this.addAvatarsToTeam(teamInfo, newTeam);
    }

    public void setupTrialAvatars(boolean save) {
        this.setPreviousIndex(this.getCurrentCharacterIndex());

        if (save) {
            var originalTeam = this.getCurrentTeamInfo();
            this.getTrialAvatarTeam().copyFrom(originalTeam);
        } else this.getActiveTeam().clear();

        this.usingTrialTeam = true;
    }

    public void trialAvatarTeamPostUpdate(int newCharacterIndex) {
        this.setCurrentCharacterIndex(Math.min(newCharacterIndex, this.getActiveTeam().size() - 1));

        this.updateTeamProperties();
        if (this.getPlayer().getScene() != null)
            this.getPlayer().getScene().addEntity(this.getCurrentAvatarEntity());
    }

    public void addAvatarToTrialTeam(Avatar trialAvatar) {

        this.getActiveTeam()
            .forEach(
                x ->
                    this.getPlayer()
                        .getScene()
                        .removeEntity(x, VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE));

        this.getActiveTeam().removeIf(x -> x.getAvatar().getAvatarId() == trialAvatar.getAvatarId());
        this.getCurrentTeamInfo().getAvatars().removeIf(x -> x == trialAvatar.getAvatarId());

        var trialEntity =
            EntityCreationEvent.call(
                EntityAvatar.class,
                new Class<?>[] {Scene.class, Avatar.class},
                new Object[] {player.getScene(), trialAvatar});
        if (trialEntity == null) {
            throw new IllegalStateException(
                "Unable to create an entity for trial avatar " + trialAvatar.getAvatarId());
        }
        this.getActiveTeam().add(trialEntity);
        this.getCurrentTeamInfo().addAvatar(trialAvatar);
        this.getTrialAvatars().put(trialAvatar.getAvatarId(), trialAvatar);
    }

    public long getTrialAvatarGuid(int trialAvatarId) {
        return this.getTrialAvatars().values().stream()
            .filter(avatar -> avatar.getTrialAvatarId() == trialAvatarId)
            .map(Avatar::getGuid)
            .findFirst()
            .orElse(0L);
    }

    public void unsetTrialAvatarTeam() {

        var index = this.getPreviousIndex();
        if (index < 0) index = 0;

        this.trialAvatarTeamPostUpdate(index);

        this.setPreviousIndex(-1);
    }

    public void removeTrialAvatarTeam() {
        this.removeTrialAvatarTeam(
            this.getActiveTeam().stream().map(avatar -> avatar.getAvatar().getAvatarId()).toList());
    }

    public void removeTrialAvatarTeam(int avatarId) {
        this.removeTrialAvatarTeam(List.of(avatarId));
    }

    public void removeTrialAvatarTeam(List<Integer> trialAvatarIds) {
        var isTeam = trialAvatarIds.size() == this.getActiveTeam().size();

        var player = this.getPlayer();
        var scene = player.getScene();

        this.usingTrialTeam = false;
        this.trialAvatarTeam = new TeamInfo();
        this.removeUnownedAvatarsFromTeams();

        this.getActiveTeam()
            .forEach(
                avatarEntity ->
                    scene.removeEntity(
                        avatarEntity, VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE));

        if (isTeam) {
            this.getActiveTeam().clear();
            this.getTrialAvatars().clear();
        } else {
            trialAvatarIds.forEach(
                trialAvatarId -> {
                    this.getActiveTeam().removeIf(x -> x.getAvatar().getTrialAvatarId() == trialAvatarId);
                    this.getTrialAvatars().values().removeIf(x -> x.getTrialAvatarId() == trialAvatarId);
                });
        }

        if (isTeam) {

            this.getCurrentTeamInfo()
                .getAvatars()
                .forEach(
                    avatarId ->
                        this.getActiveTeam()
                            .add(
                                EntityCreationEvent.call(
                                    EntityAvatar.class,
                                    new Class<?>[] {Scene.class, Avatar.class},
                                    new Object[] {scene, player.getAvatars().getAvatarById(avatarId)})));
        } else {

            var avatars = this.getCurrentTeamInfo().getAvatars();
            for (var index = 0; index < avatars.size() - 1; index++) {
                var avatar = avatars.get(index);
                if (this.getActiveTeam().stream()
                    .map(entity -> entity.getAvatar().getAvatarId())
                    .toList()
                    .contains(avatar)) continue;

                var avatarData = player.getAvatars().getAvatarById(avatar);
                if (avatarData == null) continue;

                this.getActiveTeam()
                    .add(
                        index,
                        EntityCreationEvent.call(
                            EntityAvatar.class,
                            new Class<?>[] {Scene.class, Avatar.class},
                            new Object[] {scene, avatarData}));
            }
        }

        this.unsetTrialAvatarTeam();
    }

    public void setupTemporaryTeam(List<List<Long>> guidList) {
        var teams = new ArrayList<TeamInfo>(guidList.size());

        for (int i = 0; i < guidList.size(); i++) {
            var guids = guidList.get(i);
            String rejected = null;
            List<Integer> avatarIds = null;

            if (guids.isEmpty()) {
                rejected = "it is empty";
            } else if (guids.size() > this.getMaxTeamSize()) {
                rejected = guids.size() + " avatars, over the limit of " + this.getMaxTeamSize();
            } else {
                var newTeam = new LinkedHashSet<Avatar>();
                for (Long guid : guids) {
                    Avatar avatar = this.getPlayer().getAvatars().getAvatarByGuid(guid);
                    if (avatar == null) {
                        rejected = "no avatar with guid " + guid;
                        break;
                    }
                    if (!newTeam.add(avatar)) {
                        rejected = "avatar " + avatar.getAvatarId() + " is listed twice";
                        break;
                    }
                }
                if (rejected == null) {
                    avatarIds = newTeam.stream().map(Avatar::getAvatarId).toList();
                }
            }

            if (rejected != null) {
                Grasscutter.getLogger().warn("Temporary team {} rejected: {}", i + 1, rejected);
                teams.add(new TeamInfo());
            } else {
                teams.add(new TeamInfo(avatarIds));
            }
        }

        this.temporaryTeam = teams;
    }

    public void useTemporaryTeam(int index) {
        int available = this.temporaryTeam == null ? 0 : this.temporaryTeam.size();
        if (index < 0 || index >= available) {
            Grasscutter.getLogger()
                    .warn(
                            "Asked to use temporary team {} but only {} are set up; the current team stays",
                            index + 1,
                            available);
        }
        var scene = this.getPlayer().getScene();
        var previous =
                scene == null || this.getActiveTeam().isEmpty()
                        ? null
                        : this.getActiveTeam().get(Math.min(this.currentCharacterIndex, this.getActiveTeam().size() - 1));

        this.useTemporarilyTeamIndex = index;
        this.updateTeamEntities(null);

        if (scene == null) return;

        if (previous != null && !this.getActiveTeam().contains(previous)) {
            scene.removeEntity(previous);
        }

        scene.spawnPlayer(this.getPlayer());
    }

    public boolean cleanTemporaryTeam() {

        if (useTemporarilyTeamIndex < 0) {
            return false;
        }

        this.useTemporarilyTeamIndex = -1;
        this.temporaryTeam = null;
        this.updateTeamEntities(null);
        return true;
    }

    public synchronized void setCurrentTeam(int teamId) {

        if (this.getPlayer().isInMultiplayer()) {
            return;
        }

        TeamInfo teamInfo = this.getTeams().get(teamId);
        if (teamInfo == null || teamInfo.getAvatars().size() == 0) {
            return;
        }

        this.setCurrentTeamId(teamId);
        this.updateTeamEntities(new PacketChooseCurAvatarTeamRsp(teamId));
    }

    public synchronized void setTeamName(int teamId, String teamName) {

        TeamInfo teamInfo = this.getTeams().get(teamId);
        if (teamInfo == null) {
            return;
        }

        teamInfo.setName(teamName);

        this.getPlayer().sendPacket(new PacketChangeTeamNameRsp(teamId, teamName));
    }

    public synchronized void changeAvatar(long guid) {
        EntityAvatar oldEntity = this.getCurrentAvatarEntity();
        if (oldEntity == null || guid == oldEntity.getAvatar().getGuid()) {
            return;
        }

        EntityAvatar newEntity = null;
        int index = -1;
        for (int i = 0; i < this.getActiveTeam().size(); i++) {
            if (guid == this.getActiveTeam().get(i).getAvatar().getGuid()) {
                index = i;
                newEntity = this.getActiveTeam().get(i);
            }
        }

        if (index < 0 || newEntity == oldEntity) {
            return;
        }

        var event =
            new PlayerSwitchAvatarEvent(this.getPlayer(), oldEntity.getAvatar(), newEntity.getAvatar());
        if (!event.call()) return;

        newEntity = event.getNewAvatarEntity();

        this.setCurrentCharacterIndex(index);

        oldEntity.setMotionState(MotionState.MotionState_MOTION_STANDBY);

        this.getPlayer().getScene().replaceEntity(oldEntity, newEntity);
        this.getPlayer().sendPacket(new PacketChangeAvatarRsp(guid));
    }

    public void applyVoidDamage() {
        this.getActiveTeam()
            .forEach(
                entity -> {
                    entity.damage(entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP) * .1f);
                    player.sendPacket(new PacketAvatarLifeStateChangeNotify(entity.getAvatar()));
                });
    }

    public void onAvatarDie(long dieGuid) {
        EntityAvatar deadAvatar = this.getCurrentAvatarEntity();
        if (deadAvatar == null || deadAvatar.getId() != dieGuid) return;

        PlayerDieType dieType = deadAvatar.getKilledType();
        int killedBy = deadAvatar.getKilledBy();

        if (dieType == PlayerDieType.PlayerDieType_PLAYER_DIE_DRAWN) {

            this.getPlayer().sendPacket(new PacketWorldPlayerDieNotify(dieType, killedBy));
        } else {

            int replaceIndex = getDeadAvatarReplacement();
            if (0 <= replaceIndex && replaceIndex < this.getActiveTeam().size()) {

                this.setCurrentCharacterIndex(replaceIndex);
                this.getPlayer().getScene().addEntity(this.getActiveTeam().get(replaceIndex));
            } else {

                this.getPlayer().sendPacket(new PacketWorldPlayerDieNotify(dieType, killedBy));

                PlayerTeamDeathEvent event =
                    new PlayerTeamDeathEvent(
                        this.getPlayer(), this.getActiveTeam().get(this.getCurrentCharacterIndex()));
                event.call();
            }
        }

        this.getPlayer().sendPacket(new PacketAvatarDieAnimationEndRsp(deadAvatar.getId(), 0));
    }

    public int getDeadAvatarReplacement() {
        int replaceIndex = -1;

        for (int i = 0; i < this.getActiveTeam().size(); i++) {
            EntityAvatar entity = this.getActiveTeam().get(i);
            if (entity.isAlive()) {
                replaceIndex = i;
                break;
            }
        }

        return replaceIndex;
    }

    public boolean reviveAvatar(Avatar avatar) {
        for (EntityAvatar entity : this.getActiveTeam()) {
            if (entity.getAvatar() == avatar) {
                if (entity.isAlive()) {
                    return false;
                }

                entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 1f);

                player.getSatiationManager().removeSatiationDirectly(entity.getAvatar(), 15000);
                this.getPlayer()
                    .sendPacket(
                        new PacketAvatarFightPropUpdateNotify(
                            entity.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP));
                this.getPlayer().sendPacket(new PacketAvatarLifeStateChangeNotify(entity.getAvatar()));
                return true;
            }
        }

        return false;
    }

    public boolean healAvatar(Avatar avatar, int healRate, int healAmount) {
        for (EntityAvatar entity : this.getActiveTeam()) {
            if (entity.getAvatar() == avatar) {
                if (!entity.isAlive()) {
                    return false;
                }

                entity.setFightProperty(
                    FightProperty.FIGHT_PROP_CUR_HP,
                    (float)
                        Math.min(
                            (entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP)
                                + entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP)
                                * (float) healRate
                                / 100.0
                                + (float) healAmount / 100.0),
                            entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP)));
                this.getPlayer()
                    .sendPacket(
                        new PacketAvatarFightPropUpdateNotify(
                            entity.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP));
                this.getPlayer().sendPacket(new PacketAvatarLifeStateChangeNotify(entity.getAvatar()));
                return true;
            }
        }
        return false;
    }

    public void respawnTeam() {

        this.getPlayer()
            .getStaminaManager()
            .stopSustainedStaminaHandler();

        for (EntityAvatar entity : this.getActiveTeam()) {
            entity.setFightProperty(
                FightProperty.FIGHT_PROP_CUR_HP,
                entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP) * .4f);
            this.getPlayer().getSatiationManager().removeSatiationDirectly(entity.getAvatar(), 15000);
            this.getPlayer()
                .sendPacket(
                    new PacketAvatarFightPropUpdateNotify(
                        entity.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP));
            this.getPlayer().sendPacket(new PacketAvatarLifeStateChangeNotify(entity.getAvatar()));
        }

        try {
            this.getPlayer()
                .sendPacket(
                    new PacketPlayerEnterSceneNotify(
                        this.getPlayer(),
                        EnterType.EnterType_ENTER_SELF,
                        EnterReason.Revival,
                        this.getPlayer().getSceneId(),
                        this.getRespawnPosition()));
            this.getPlayer().sendPacket(new PacketEnterScenePeerNotify(this.getPlayer()));
            this.getPlayer().getPosition().set(this.getRespawnPosition());
        } catch (Exception ignored) {
            this.getPlayer()
                .sendPacket(
                    new PacketPlayerEnterSceneNotify(
                        this.getPlayer(),
                        EnterType.EnterType_ENTER_SELF,
                        EnterReason.Revival,
                        3,
                        GameConstants.START_POSITION));
            this.getPlayer().sendPacket(new PacketEnterScenePeerNotify(this.getPlayer()));
            this.getPlayer()
                .getPosition()
                .set(GameConstants.START_POSITION);
        }

        this.getPlayer().sendPacket(new BasePacket(PacketOpcodes.WorldPlayerReviveRsp));
    }

    public Position getRespawnPosition() {
        var deathPos = this.getPlayer().getPosition();
        int sceneId = this.getPlayer().getSceneId();

        var respawnPoint =
            this.getPlayer().getUnlockedScenePoints(sceneId).stream()
                .map(pointId -> GameData.getScenePointEntryById(sceneId, pointId))
                .filter(point -> point.getPointData().getType().equals("SceneTransPoint"))
                .min(
                    (Comparator.comparingDouble(
                        pos -> Utils.getDist(pos.getPointData().getTranPos(), deathPos))));

        return respawnPoint.get().getPointData().getTranPos();
    }

    public void saveAvatars() {

        for (EntityAvatar entity : this.getActiveTeam()) {
            entity.getAvatar().save();
        }
    }

    public void onPlayerLogin() {
        this.updateTeamResonances();
    }

    public synchronized void addNewCustomTeam() {

        if (this.teams.size() == GameConstants.MAX_TEAMS) {
            player.sendPacket(new PacketAddBackupAvatarTeamRsp(Retcode.RET_FAIL));
            return;
        }

        int id = -1;
        for (int i = 5; i <= GameConstants.MAX_TEAMS; i++) {
            if (!this.teams.containsKey(i)) {
                id = i;
                break;
            }
        }

        this.teams.put(id, new TeamInfo());

        player.sendPacket(new PacketAvatarTeamAllDataNotify(player));
        player.sendPacket(new PacketAddBackupAvatarTeamRsp());
    }

    public synchronized void removeCustomTeam(int id) {

        if (!this.teams.containsKey(id)) {
            player.sendPacket(new PacketDelBackupAvatarTeamRsp(Retcode.RET_FAIL, id));
        }

        this.teams.remove(id);

        player.sendPacket(new PacketAvatarTeamAllDataNotify(player));
        player.sendPacket(new PacketDelBackupAvatarTeamRsp(id));
    }

    public void applyAbilities(Scene scene) {
        try {
            var levelEntityConfig = scene.getSceneData().getLevelEntityConfig();
            var config = GameData.getConfigLevelEntityDataMap().get(levelEntityConfig);
            if (config == null) return;

            var avatars = this.getPlayer().getAvatars();
            var avatarIds = scene.getSceneData().getSpecifiedAvatarList();
            var specifiedAvatarList = this.getActiveTeam();

            if (avatarIds != null && avatarIds.size() > 0) {

                specifiedAvatarList.clear();
                for (int id : avatarIds) {
                    var avatar = avatars.getAvatarById(id);
                    if (avatar == null) continue;

                    specifiedAvatarList.add(
                        EntityCreationEvent.call(
                            EntityAvatar.class,
                            new Class<?>[] {Scene.class, Avatar.class},
                            new Object[] {scene, avatar}));
                }
            }

            for (var entityAvatar : specifiedAvatarList) {
                var avatarData = entityAvatar.getAvatar().getAvatarData();
                if (avatarData == null) {
                    continue;
                }

                avatarData.buildEmbryo();
                if (config.getAvatarAbilities() == null) {
                    continue;

                }

                for (var abilities : config.getAvatarAbilities()) {
                    avatarData.getAbilities().add(Utils.abilityHash(abilities.getAbilityName()));
                }
            }
        } catch (Exception e) {
            Grasscutter.getLogger()
                .error(
                    "Error applying level entity config for scene {}", scene.getSceneData().getId(), e);
        }
    }

    public List<Integer> getTrialAvatarParam(int trialAvatarId) {
        if (GameData.getTrialAvatarCustomData()
            .isEmpty()) {
            if (GameData.getTrialAvatarDataMap().get(trialAvatarId) == null) return List.of();

            return GameData.getTrialAvatarDataMap().get(trialAvatarId).getTrialAvatarParamList();
        }

        if (GameData.getTrialAvatarCustomData().get(trialAvatarId) == null) return List.of();

        val trialCustomParams =
            GameData.getTrialAvatarCustomData().get(trialAvatarId).getTrialAvatarParamList();
        return trialCustomParams.isEmpty()
            ? List.of()
            : Stream.of(trialCustomParams.get(0).split(";")).map(Integer::parseInt).toList();
    }

    public boolean addTrialAvatar(int avatarId, int questMainId, GrantReason reason) {
        List<Integer> trialAvatarBasicParam = getTrialAvatarParam(avatarId);
        if (trialAvatarBasicParam.isEmpty()) return false;

        var avatar = new Avatar(trialAvatarBasicParam.get(0));
        if (avatar.getAvatarData() == null || !this.getPlayer().hasSentLoginPackets()) return false;

        avatar.setOwner(this.getPlayer());

        avatar.setTrialAvatarInfo(trialAvatarBasicParam.get(1), avatarId, reason, questMainId);
        avatar.equipTrialItems();
        TpsAvatarSystem.onTrialAvatarCreated(avatar);

        avatar.recalcStats();

        this.getPlayer().sendPacket(new PacketAvatarAddNotify(avatar, false));

        this.addAvatarToTrialTeam(avatar);
        return true;
    }

    public void addTrialAvatar(int avatarId, int questMainId) {
        this.addTrialAvatars(List.of(avatarId), questMainId, true);

        this.getPlayer().sendPacket(new PacketAvatarTeamUpdateNotify(this.getPlayer()));
    }

    public void addTrialAvatars(List<Integer> avatarIds) {
        this.addTrialAvatars(avatarIds, 0, false);
    }

    public void addTrialAvatars(List<Integer> avatarIds, boolean save) {
        this.addTrialAvatars(avatarIds, 0, save);
    }

    public void addTrialAvatars(List<Integer> trialAvatarIds, int questId, boolean save) {
        this.setupTrialAvatars(save);

        trialAvatarIds.forEach(
            trialAvatarId -> {
                var result =
                    this.addTrialAvatar(
                        trialAvatarId,
                        questId,
                        questId != 0
                            ? GrantReason.GRANT_REASON_BY_QUEST
                            : GrantReason.GRANT_REASON_BY_TRIAL_AVATAR_ACTIVITY);

                if (!result) throw new RuntimeException("Unable to add trial avatar to team.");
            });

        this.trialAvatarTeamPostUpdate(questId != 0 ? this.getActiveTeam().size() - 1 : 0);
        TpsAvatarSystem.onTrialTeamReady(this.getPlayer());
    }

    public void removeTrialAvatar() {
        this.removeTrialAvatar(
            this.getActiveTeam().stream()
                .map(EntityAvatar::getAvatar)
                .map(Avatar::getTrialAvatarId)
                .toList());
    }

    public void removeTrialAvatar(int trialAvatarId) {
        this.removeTrialAvatar(List.of(trialAvatarId));
    }

    public void removeTrialAvatar(List<Integer> trialAvatarIds) {

        if (!this.isUsingTrialTeam()) return;

        this.getPlayer()
            .sendPacket(
                new PacketAvatarDelNotify(
                    trialAvatarIds.stream().map(this::getTrialAvatarGuid).toList()));
        this.removeTrialAvatarTeam(trialAvatarIds);

        if (trialAvatarIds.size() == 1) this.getPlayer().sendPacket(new PacketAvatarTeamUpdateNotify());
    }
}
