package emu.grasscutter.game.managers.stamina;

import ch.qos.logback.classic.Logger;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.tps.TpsAvatarSystem;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.EntityMoveInfoOuterClass.EntityMoveInfo;
import emu.grasscutter.net.proto.MotionStateOuterClass.MotionState;
import emu.grasscutter.net.proto.PlayerDieTypeOuterClass.PlayerDieType;
import emu.grasscutter.net.proto.VectorOuterClass.Vector;
import emu.grasscutter.net.proto.VehicleInteractTypeOuterClass.VehicleInteractType;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

public class StaminaManager extends BasePlayerManager {

    public final static int GlobalCharacterMaximumStamina = PlayerProperty.PROP_MAX_STAMINA.getMax();
    public final static int GlobalVehicleMaxStamina = PlayerProperty.PROP_MAX_STAMINA.getMax();
    private static final Map<String, Set<MotionState>> MotionStatesCategorized = new HashMap<>() {{
        put("CLIMB", Set.of(
            MotionState.MotionState_MOTION_CLIMB,
            MotionState.MotionState_MOTION_STANDBY_TO_CLIMB
        ));
        put("DASH", Set.of(
            MotionState.MotionState_MOTION_DANGER_DASH,
            MotionState.MotionState_MOTION_DASH
        ));
        put("FLY", Set.of(
            MotionState.MotionState_MOTION_FLY,
            MotionState.MotionState_MOTION_FLY_FAST,
            MotionState.MotionState_MOTION_FLY_SLOW,
            MotionState.MotionState_MOTION_POWERED_FLY
        ));
        put("RUN", Set.of(
            MotionState.MotionState_MOTION_DANGER_RUN,
            MotionState.MotionState_MOTION_RUN
        ));
        put("SKIFF", Set.of(
            MotionState.MotionState_MOTION_SKIFF_BOARDING,
            MotionState.MotionState_MOTION_SKIFF_DASH,
            MotionState.MotionState_MOTION_SKIFF_NORMAL,
            MotionState.MotionState_MOTION_SKIFF_POWERED_DASH
        ));
        put("STANDBY", Set.of(
            MotionState.MotionState_MOTION_DANGER_STANDBY_MOVE,
            MotionState.MotionState_MOTION_DANGER_STANDBY,
            MotionState.MotionState_MOTION_LADDER_TO_STANDBY,
            MotionState.MotionState_MOTION_STANDBY_MOVE,
            MotionState.MotionState_MOTION_STANDBY
        ));
        put("SWIM", Set.of(
            MotionState.MotionState_MOTION_SWIM_IDLE,
            MotionState.MotionState_MOTION_SWIM_DASH,
            MotionState.MotionState_MOTION_SWIM_JUMP,
            MotionState.MotionState_MOTION_SWIM_MOVE
        ));
        put("WALK", Set.of(
            MotionState.MotionState_MOTION_DANGER_WALK,
            MotionState.MotionState_MOTION_WALK
        ));
        put("OTHER", Set.of(
            MotionState.MotionState_MOTION_CLIMB_JUMP,
            MotionState.MotionState_MOTION_DASH_BEFORE_SHAKE,
            MotionState.MotionState_MOTION_FIGHT,
            MotionState.MotionState_MOTION_JUMP_UP_WALL_FOR_STANDBY,
            MotionState.MotionState_MOTION_NOTIFY,
            MotionState.MotionState_MOTION_SIT_IDLE,
            MotionState.MotionState_MOTION_JUMP
        ));
        put("NOCOST_NORECOVER", Set.of(
            MotionState.MotionState_MOTION_LADDER_SLIP,
            MotionState.MotionState_MOTION_SLIP,
            MotionState.MotionState_MOTION_FLY_IDLE
        ));
        put("IGNORE", Set.of(
            MotionState.MotionState_MOTION_CROUCH_IDLE,
            MotionState.MotionState_MOTION_CROUCH_MOVE,
            MotionState.MotionState_MOTION_CROUCH_ROLL,
            MotionState.MotionState_MOTION_DESTROY_VEHICLE,
            MotionState.MotionState_MOTION_FALL_ON_GROUND,
            MotionState.MotionState_MOTION_FOLLOW_ROUTE,
            MotionState.MotionState_MOTION_FORCE_SET_POS,
            MotionState.MotionState_MOTION_GO_UPSTAIRS,
            MotionState.MotionState_MOTION_JUMP_OFF_WALL,
            MotionState.MotionState_MOTION_LADDER_IDLE,
            MotionState.MotionState_MOTION_LADDER_MOVE,
            MotionState.MotionState_MOTION_LAND_SPEED,
            MotionState.MotionState_MOTION_MOVE_FAIL_ACK,
            MotionState.MotionState_MOTION_NONE,
            MotionState.MOTION_NUM,
            MotionState.MotionState_MOTION_QUEST_FORCE_DRAG,
            MotionState.MotionState_MOTION_RESET,
            MotionState.MotionState_MOTION_STANDBY_TO_LADDER,
            MotionState.MotionState_MOTION_WATERFALL
        ));
    }};
    private static final Set<Integer> TalentMovements = Set.of(10013, 10413);
    private static final HashMap<Integer, Float> ClimbFoodReductionMap = new HashMap<>() {{
        put(0, 0.8f);
    }};
    private static final HashMap<Integer, Float> DashFoodReductionMap = new HashMap<>() {{
        put(0, 0.8f);
    }};
    private static final HashMap<Integer, Float> FlyFoodReductionMap = new HashMap<>() {{
        put(0, 0.8f);
    }};
    private static final HashMap<Integer, Float> SwimFoodReductionMap = new HashMap<>() {{
        put(0, 0.8f);
    }};
    private static final HashMap<Integer, Float> ClimbTalentReductionMap = new HashMap<>() {{
        put(262301, 0.8f);
    }};
    private static final HashMap<Integer, Float> FlyTalentReductionMap = new HashMap<>() {{
        put(212301, 0.8f);
        put(222301, 0.8f);
    }};
    private static final HashMap<Integer, Float> SwimTalentReductionMap = new HashMap<>() {{
        put(242301, 0.8f);
        put(542301, 0.8f);
    }};

    private final Logger logger = Grasscutter.getLogger();
    private final HashMap<String, BeforeUpdateStaminaListener> beforeUpdateStaminaListeners = new HashMap<>();
    private final HashMap<String, AfterUpdateStaminaListener> afterUpdateStaminaListeners = new HashMap<>();
    private Position currentCoordinates = new Position(0, 0, 0);
    private Position previousCoordinates = new Position(0, 0, 0);
    private MotionState currentState = MotionState.MotionState_MOTION_STANDBY;
    private MotionState previousState = MotionState.MotionState_MOTION_STANDBY;
    private Timer sustainedStaminaHandlerTimer;
    private GameSession cachedSession = null;
    private GameEntity cachedEntity = null;
    public int staminaRecoverDelay = 0;
    private long lastCostStaminaTime = 0;
    private int lastSkillId = 0;
    private int lastSkillCasterId = 0;
    private boolean lastSkillFirstTick = true;
    private int vehicleId = -1;
    private int vehicleStamina = GlobalVehicleMaxStamina;

    public StaminaManager(Player player) {
        super(player);
    }

    public static void initialize() {
    }


    public void setSkillCast(int skillId, int skillCasterId) {
        lastSkillFirstTick = true;
        lastSkillId = skillId;
        lastSkillCasterId = skillCasterId;
    }

    private boolean usesTpsStamina() {
        var entity = player.getTeamManager().getCurrentAvatarEntity();
        return entity != null && TpsAvatarSystem.isTpsAvatar(entity.getAvatar());
    }

    private PlayerProperty maxStaminaProperty() {
        return usesTpsStamina() ? PlayerProperty.PROP_MAX_TPS_STAMINA : PlayerProperty.PROP_MAX_STAMINA;
    }

    private PlayerProperty currentStaminaProperty() {
        return usesTpsStamina()
                ? PlayerProperty.PROP_CUR_PERSIST_TPS_STAMINA
                : PlayerProperty.PROP_CUR_PERSIST_STAMINA;
    }

    public int getMaxCharacterStamina() {
        return player.getProperty(this.maxStaminaProperty());
    }

    public int getCurrentCharacterStamina() {
        return player.getProperty(this.currentStaminaProperty());
    }

    public int getMaxVehicleStamina() {
        return GlobalVehicleMaxStamina;
    }

    public int getCurrentVehicleStamina() {
        return vehicleStamina;
    }

    public long getLastCostStaminaTime() {
    return lastCostStaminaTime;
    }

    public void setLastCostStaminaTime(long time) {
    this.lastCostStaminaTime = time;
    }

    public boolean addCurrentStamina(int amount) {
        var cur = this.getCurrentCharacterStamina();
        var max = this.getMaxCharacterStamina();
        if (cur >= max) return false;
        var value = cur + amount;
        if (value > max)
            value = max;
        this.player.setProperty(this.currentStaminaProperty(), value);
        return true;
    }

    public boolean registerBeforeUpdateStaminaListener(String listenerName, BeforeUpdateStaminaListener listener) {
        if (beforeUpdateStaminaListeners.containsKey(listenerName)) {
            return false;
        }
        beforeUpdateStaminaListeners.put(listenerName, listener);
        return true;
    }

    public boolean unregisterBeforeUpdateStaminaListener(String listenerName) {
        if (!beforeUpdateStaminaListeners.containsKey(listenerName)) {
            return false;
        }
        beforeUpdateStaminaListeners.remove(listenerName);
        return true;
    }

    public boolean registerAfterUpdateStaminaListener(String listenerName, AfterUpdateStaminaListener listener) {
        if (afterUpdateStaminaListeners.containsKey(listenerName)) {
            return false;
        }
        afterUpdateStaminaListeners.put(listenerName, listener);
        return true;
    }

    public boolean unregisterAfterUpdateStaminaListener(String listenerName) {
        if (!afterUpdateStaminaListeners.containsKey(listenerName)) {
            return false;
        }
        afterUpdateStaminaListeners.remove(listenerName);
        return true;
    }

    private boolean isPlayerMoving() {
        float diffX = currentCoordinates.getX() - previousCoordinates.getX();
        float diffY = currentCoordinates.getY() - previousCoordinates.getY();
        float diffZ = currentCoordinates.getZ() - previousCoordinates.getZ();
        logger.trace("isPlayerMoving: " + previousCoordinates + ", " + currentCoordinates +
            ", " + diffX + ", " + diffY + ", " + diffZ);
        return Math.abs(diffX) > 0.3 || Math.abs(diffY) > 0.2 || Math.abs(diffZ) > 0.3;
    }

    public int updateStaminaRelative(GameSession session, Consumption consumption, boolean isCharacterStamina) {
        int currentStamina = isCharacterStamina ? getCurrentCharacterStamina() : getCurrentVehicleStamina();
        if (consumption.amount == 0) {
            return currentStamina;
        }

        for (Map.Entry<String, BeforeUpdateStaminaListener> listener : beforeUpdateStaminaListeners.entrySet()) {
            Consumption overriddenConsumption = listener.getValue().onBeforeUpdateStamina(consumption.type.toString(), consumption, isCharacterStamina);
            if ((overriddenConsumption.type != consumption.type) && (overriddenConsumption.amount != consumption.amount)) {
                logger.debug("Stamina update relative(" +
                    consumption.type.toString() + ", " + consumption.amount + ") overridden to relative(" +
                    consumption.type.toString() + ", " + consumption.amount + ") by: " + listener.getKey());
                return currentStamina;
            }
        }

        int maxStamina = isCharacterStamina ? getMaxCharacterStamina() : getMaxVehicleStamina();
        logger.trace((isCharacterStamina ? "C " : "V ") + currentStamina + "/" + maxStamina + "\t" + currentState + "\t" +
            (isPlayerMoving() ? "moving" : "      ") + "\t(" + consumption.type + "," +
            consumption.amount + ")");

        int newStamina = currentStamina + consumption.amount;
        if (newStamina < 0) {
            newStamina = 0;
        } else if (newStamina > maxStamina) {
            newStamina = maxStamina;
        }

        return setStamina(session, consumption.type.toString(), newStamina, isCharacterStamina);
    }

    public int updateStaminaAbsolute(GameSession session, String reason, int newStamina, boolean isCharacterStamina) {
        int currentStamina = isCharacterStamina ? getCurrentCharacterStamina() : getCurrentVehicleStamina();
        for (Map.Entry<String, BeforeUpdateStaminaListener> listener : beforeUpdateStaminaListeners.entrySet()) {
            int overriddenNewStamina = listener.getValue().onBeforeUpdateStamina(reason, newStamina, isCharacterStamina);
            if (overriddenNewStamina != newStamina) {
                logger.debug("Stamina update absolute(" +
                    reason + ", " + newStamina + ") overridden to absolute(" +
                    reason + ", " + newStamina + ") by: " + listener.getKey());
                return currentStamina;
            }
        }
        int maxStamina = isCharacterStamina ? getMaxCharacterStamina() : getMaxVehicleStamina();
        if (newStamina < 0) {
            newStamina = 0;
        } else if (newStamina > maxStamina) {
            newStamina = maxStamina;
        }
        return setStamina(session, reason, newStamina, isCharacterStamina);
    }

    public int setStamina(GameSession session, String reason, int newStamina, boolean isCharacterStamina) {
        if (!GAME_OPTIONS.staminaUsage || session.getPlayer().isUnlimitedStamina()) {
            newStamina = getMaxCharacterStamina();
        }

        if (isCharacterStamina) {
            player.setProperty(this.currentStaminaProperty(), newStamina);
        } else {
            vehicleStamina = newStamina;
            session.send(new PacketVehicleStaminaNotify(vehicleId, ((float) newStamina) / 100));
        }
        int s = newStamina;
        afterUpdateStaminaListeners.forEach((k, v) -> v.onAfterUpdateStamina(reason, s, isCharacterStamina));
        return newStamina;
    }

    public void killAvatar(GameSession session, GameEntity entity, PlayerDieType dieType) {
        session.send(new PacketAvatarLifeStateChangeNotify(player.getTeamManager().getCurrentAvatarEntity().getAvatar(),
            LifeState.LIFE_DEAD, dieType));
        session.send(new PacketLifeStateChangeNotify(entity, LifeState.LIFE_DEAD, dieType));
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        entity.getWorld().broadcastPacket(new PacketEntityFightPropUpdateNotify(entity, FightProperty.FIGHT_PROP_CUR_HP));
        entity.getWorld().broadcastPacket(new PacketLifeStateChangeNotify(0, entity, LifeState.LIFE_DEAD));
        player.getScene().removeEntity(entity);

        if (entity instanceof EntityAvatar avatar)
            avatar.onDeath(dieType, 0);
    }

    public void startSustainedStaminaHandler() {
        if (!player.isPaused() && sustainedStaminaHandlerTimer == null) {
            sustainedStaminaHandlerTimer = new Timer();
            sustainedStaminaHandlerTimer.scheduleAtFixedRate(new SustainedStaminaHandler(), 0, 200);
            logger.trace("[MovementManager] SustainedStaminaHandlerTimer started");
        }
    }

    public void stopSustainedStaminaHandler() {
        if (sustainedStaminaHandlerTimer != null) {
            sustainedStaminaHandlerTimer.cancel();
            sustainedStaminaHandlerTimer = null;
            logger.trace("[MovementManager] SustainedStaminaHandlerTimer stopped");
        }
    }



    public void handleEvtDoSkillSuccNotify(GameSession session, int skillId, int casterId) {
        if (casterId != player.getTeamManager().getCurrentAvatarEntity().getId()) {
            return;
        }
        setSkillCast(skillId, casterId);
        Avatar currentAvatar = player.getTeamManager().getCurrentAvatarEntity().getAvatar();
        if (currentAvatar.getAvatarData().getWeaponType() == WeaponType.WEAPON_CLAYMORE) {
        }
    }

    public void handleMixinCostStamina(boolean isSwim) {
        if (lastSkillCasterId == player.getTeamManager().getCurrentAvatarEntity().getId()) {
            handleImmediateStamina(cachedSession, lastSkillId);
        }
    }

    public void handleCombatInvocationsNotify(@NotNull GameSession session, @NotNull EntityMoveInfo moveInfo, @NotNull GameEntity entity) {
        this.cachedSession = session;
        this.cachedEntity = entity;

        var motionInfo = moveInfo.getMotionInfo();
        var motionState = motionInfo.getState();
        var notifyEntityId = entity.getId();
        var currentAvatarEntityId = session.getPlayer().getTeamManager().getCurrentAvatarEntity().getId();
        if (notifyEntityId != currentAvatarEntityId && notifyEntityId != vehicleId) {
            return;
        }

        this.previousState = currentState;

        this.currentState = motionState;
        Vector posVector = motionInfo.getPos();
        Position newPos = new Position(posVector.getX(), posVector.getY(), posVector.getZ());
        if (newPos.getX() != 0 && newPos.getY() != 0 && newPos.getZ() != 0) {
            currentCoordinates = newPos;
        }

        startSustainedStaminaHandler();
        handleImmediateStamina(session, motionState);
    }

    public void handleVehicleInteractReq(GameSession session, int vehicleId, VehicleInteractType vehicleInteractType) {
        if (vehicleInteractType == VehicleInteractType.VehicleInteractType_VEHICLE_INTERACT_IN) {
            this.vehicleId = vehicleId;
            updateStaminaAbsolute(session, "board vehicle", getMaxCharacterStamina(), true);
            updateStaminaAbsolute(session, "board vehicle", getMaxVehicleStamina(), false);
        } else {
            this.vehicleId = -1;
        }
    }


    private void handleImmediateStamina(GameSession session, @NotNull MotionState motionState) {
        if (previousState == currentState) {
            return;
        }

        switch (motionState) {
            case MotionState_MOTION_CLIMB ->
                updateStaminaRelative(session, new Consumption(ConsumptionType.CLIMB_START), true);
            case MotionState_MOTION_DASH_BEFORE_SHAKE ->
                updateStaminaRelative(session, new Consumption(ConsumptionType.SPRINT), true);
            case MotionState_MOTION_CLIMB_JUMP ->
                updateStaminaRelative(session, new Consumption(ConsumptionType.CLIMB_JUMP), true);
            case MotionState_MOTION_SWIM_DASH ->
                updateStaminaRelative(session, new Consumption(ConsumptionType.SWIM_DASH_START), true);
        }
    }

    private void handleImmediateStamina(GameSession session, int skillId) {
        Consumption consumption = getFightConsumption(skillId);
        updateStaminaRelative(session, consumption, true);
    }

    private class SustainedStaminaHandler extends TimerTask {
        public void run() {
            boolean moving = isPlayerMoving();
            int currentCharacterStamina = getCurrentCharacterStamina();
            int maxCharacterStamina = getMaxCharacterStamina();
            int currentVehicleStamina = getCurrentVehicleStamina();
            int maxVehicleStamina = getMaxVehicleStamina();
            if (moving || (currentCharacterStamina < maxCharacterStamina) || (currentVehicleStamina < maxVehicleStamina)) {
                logger.trace("Player moving: " + moving + ", stamina full: " +
                        (currentCharacterStamina >= maxCharacterStamina) + ", recalculate stamina");
                boolean isCharacterStamina = true;
                Consumption consumption;

                if (MotionStatesCategorized.get("CLIMB").contains(currentState)) {
                    consumption = getClimbConsumption();
                } else if (MotionStatesCategorized.get("DASH").contains(currentState)) {
                    consumption = getDashConsumption();
                } else if (MotionStatesCategorized.get("FLY").contains(currentState)) {
                    consumption = getFlyConsumption();
                } else if (MotionStatesCategorized.get("RUN").contains(currentState)) {
                    consumption = new Consumption(ConsumptionType.RUN);
                } else if (MotionStatesCategorized.get("SKIFF").contains(currentState)) {
                    consumption = getSkiffConsumption();
                    isCharacterStamina = false;
                } else if (MotionStatesCategorized.get("STANDBY").contains(currentState)) {
                    consumption = new Consumption(ConsumptionType.STANDBY);
                } else if (MotionStatesCategorized.get("SWIM").contains(currentState)) {
                    consumption = getSwimConsumptions();
                } else if (MotionStatesCategorized.get("WALK").contains(currentState)) {
                    consumption = new Consumption(ConsumptionType.WALK);
                } else if (MotionStatesCategorized.get("NOCOST_NORECOVER").contains(currentState)) {
                    consumption = new Consumption();
                } else if (MotionStatesCategorized.get("OTHER").contains(currentState)) {
                    consumption = getOtherConsumptions();
                } else {
                    return;
                }

                if (consumption.amount < 0 && isCharacterStamina) {
                    if (player.getTeamManager().getTeamResonances().contains(10301)) {
                        consumption.amount *= 0.85f;
                    }
                }
                if (consumption.amount != 0 && cachedSession != null) {
                    if (consumption.amount < 0) {
                        staminaRecoverDelay = 0;
                    }
                    if (consumption.amount > 0
                            && consumption.type != ConsumptionType.POWERED_FLY
                            && consumption.type != ConsumptionType.POWERED_SKIFF) {
                        if (staminaRecoverDelay < 5) {
                            staminaRecoverDelay++;
                            consumption.amount = 0;
                            logger.trace("Delaying recovery: " + staminaRecoverDelay);
                        }
                    }
                    updateStaminaRelative(cachedSession, consumption, isCharacterStamina);
                }
            }
            previousState = currentState;
            previousCoordinates = currentCoordinates.clone();
        }
    }

    private void handleDrowning() {
        int stamina = getCurrentCharacterStamina();
        if (stamina < 10) {
            logger.trace(getCurrentCharacterStamina() + "/" +
                getMaxCharacterStamina() + "\t" + currentState);
            if (currentState != MotionState.MotionState_MOTION_SWIM_IDLE) {
                killAvatar(cachedSession, cachedEntity, PlayerDieType.PlayerDieType_PLAYER_DIE_DRAWN);
            }
        }
    }



    public Consumption getFightConsumption(int skillCasting) {
        if (TalentMovements.contains(skillCasting)) {
            return getTalentMovingSustainedCost(skillCasting);
        }
        Avatar currentAvatar = player.getTeamManager().getCurrentAvatarEntity().getAvatar();

        return switch (currentAvatar.getAvatarData().getWeaponType()) {
            case WEAPON_BOW -> getBowSustainedCost(skillCasting);
            case WEAPON_CLAYMORE -> getClaymoreSustainedCost(skillCasting);
            case WEAPON_CATALYST -> getCatalystCost(skillCasting);
            case WEAPON_POLE -> getPolearmCost(skillCasting);
            case WEAPON_SWORD_ONE_HAND -> getSwordCost(skillCasting);
            default -> new Consumption();
        };
    }

    private Consumption getClimbConsumption() {
        Consumption consumption = new Consumption();
        if (currentState == MotionState.MotionState_MOTION_CLIMB && isPlayerMoving()) {
            consumption.type = ConsumptionType.CLIMBING;
            consumption.amount = ConsumptionType.CLIMBING.amount;
        }
        consumption.amount *= getFoodCostReductionFactor(ClimbFoodReductionMap);
        consumption.amount *= getTalentCostReductionFactor(ClimbTalentReductionMap);
        return consumption;
    }

    private Consumption getSwimConsumptions() {
        handleDrowning();
        Consumption consumption = new Consumption();
        if (currentState == MotionState.MotionState_MOTION_SWIM_MOVE) {
            consumption.type = ConsumptionType.SWIMMING;
            consumption.amount = ConsumptionType.SWIMMING.amount;
        }
        if (currentState == MotionState.MotionState_MOTION_SWIM_DASH) {
            consumption.type = ConsumptionType.SWIM_DASH;
            consumption.amount = ConsumptionType.SWIM_DASH.amount;
        }
        consumption.amount *= getFoodCostReductionFactor(SwimFoodReductionMap);
        consumption.amount *= getTalentCostReductionFactor(SwimTalentReductionMap);
        return consumption;
    }

    private Consumption getDashConsumption() {
        Consumption consumption = new Consumption();
        if (currentState == MotionState.MotionState_MOTION_DASH) {
            consumption.type = ConsumptionType.DASH;
            consumption.amount = ConsumptionType.DASH.amount;
            consumption.amount *= getFoodCostReductionFactor(DashFoodReductionMap);
        }
        return consumption;
    }

    private Consumption getFlyConsumption() {
        if (currentState == MotionState.MotionState_MOTION_POWERED_FLY) {
            return new Consumption(ConsumptionType.POWERED_FLY);
        }
        Consumption consumption = new Consumption(ConsumptionType.FLY);
        consumption.amount *= getFoodCostReductionFactor(FlyFoodReductionMap);
        consumption.amount *= getTalentCostReductionFactor(FlyTalentReductionMap);
        return consumption;
    }

    private Consumption getSkiffConsumption() {
        return switch (currentState) {
            case MotionState_MOTION_SKIFF_DASH -> new Consumption(ConsumptionType.SKIFF_DASH);
            case MotionState_MOTION_SKIFF_POWERED_DASH -> new Consumption(ConsumptionType.POWERED_SKIFF);
            case MotionState_MOTION_SKIFF_NORMAL -> new Consumption(ConsumptionType.SKIFF);
            default -> new Consumption();
        };
    }

    private Consumption getOtherConsumptions() {
        return switch (this.currentState) {
            case MotionState_MOTION_FIGHT -> new Consumption(ConsumptionType.FIGHT, 500);
            case MotionState_MOTION_NOTIFY -> new Consumption(ConsumptionType.NOTIFY);
            case MotionState_MOTION_JUMP -> new Consumption(ConsumptionType.STANDBY);
            case MotionState_MOTION_SIT_IDLE -> new Consumption(ConsumptionType.STANDBY);
            default -> new Consumption();
        };
    }


    private float getTalentCostReductionFactor(HashMap<Integer, Float> talentReductionMap) {
        float reduction = 1;
        for (EntityAvatar entity : cachedSession.getPlayer().getTeamManager().getActiveTeam()) {
            for (int skillId : entity.getAvatar().getProudSkillList()) {
                if (talentReductionMap.containsKey(skillId)) {
                    float potentialLowerReduction = talentReductionMap.get(skillId);
                    if (potentialLowerReduction < reduction) {
                        reduction = potentialLowerReduction;
                    }
                }
            }
        }
        return reduction;
    }

    private float getFoodCostReductionFactor(HashMap<Integer, Float> foodReductionMap) {
        float reduction = 1;
        return reduction;
    }

    private Consumption getTalentMovingSustainedCost(int skillId) {
        if (lastSkillFirstTick) {
            lastSkillFirstTick = false;
            return new Consumption(ConsumptionType.TALENT_DASH, -1000);
        } else {
            return new Consumption(ConsumptionType.TALENT_DASH, -500);
        }
    }

    private Consumption getBowSustainedCost(int skillId) {
        return new Consumption(ConsumptionType.FIGHT, +500);
    }

    private Consumption getCatalystCost(int skillId) {
        Consumption consumption = new Consumption(ConsumptionType.FIGHT, -5000);
        switch (skillId) {
        }
        return consumption;
    }

    private Consumption getClaymoreSustainedCost(int skillId) {
        Consumption consumption = new Consumption(ConsumptionType.FIGHT, -1333);
        switch (skillId) {
            case 10571:
            case 10532:
                consumption.amount = 0;
                break;
            case 10160:
                if (player.getTeamManager().getCurrentAvatarEntity().getAvatar().getProudSkillList().contains(162101)) {
                    consumption.amount /= 2;
                }
                break;
        }
        return consumption;
    }

    private Consumption getPolearmCost(int skillId) {
        Consumption consumption = new Consumption(ConsumptionType.FIGHT, -2500);
        switch (skillId) {
        }
        return consumption;
    }

    private Consumption getSwordCost(int skillId) {
        Consumption consumption = new Consumption(ConsumptionType.FIGHT, -2000);
        switch (skillId) {
            case 10421:
                consumption.amount = -2500;
                break;
        }
        return consumption;
    }
}
