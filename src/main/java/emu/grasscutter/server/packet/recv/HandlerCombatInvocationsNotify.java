package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.net.proto.CombatInvocationsNotifyOuterClass.CombatInvocationsNotify;
import emu.grasscutter.net.proto.CombatInvokeEntryOuterClass.CombatInvokeEntry;
import emu.grasscutter.net.proto.EntityMoveInfoOuterClass.EntityMoveInfo;
import emu.grasscutter.net.proto.EvtAnimatorParameterInfoOuterClass.EvtAnimatorParameterInfo;
import emu.grasscutter.net.proto.EvtBeingHitInfoOuterClass.EvtBeingHitInfo;
import emu.grasscutter.net.proto.MotionInfoOuterClass.MotionInfo;
import emu.grasscutter.net.proto.MotionStateOuterClass.MotionState;
import emu.grasscutter.net.proto.PlayerDieTypeOuterClass;
import emu.grasscutter.server.event.entity.EntityMoveEvent;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;

@Opcodes(PacketOpcodes.CombatInvocationsNotify)
public class HandlerCombatInvocationsNotify extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        CombatInvocationsNotify notif = CombatInvocationsNotify.parseFrom(payload);
        for (CombatInvokeEntry entry : notif.getInvokeListList()) {

            switch (entry.getArgumentType()) {
                case CombatTypeArgument_COMBAT_EVT_BEING_HIT -> {
                    EvtBeingHitInfo hitInfo = EvtBeingHitInfo.parseFrom(entry.getCombatData());
                    AttackResult attackResult = hitInfo.getAttackResult();
                    Player player = session.getPlayer();

                    if (attackResult.getAttackerId()
                                    != player.getTeamManager().getCurrentAvatarEntity().getId()
                            && player.getAbilityManager().isAbilityInvulnerable()
                            && player.getScene().getEntityById(attackResult.getDefenseId())
                                    instanceof EntityAvatar) break;


                    player.getAttackResults().add(attackResult);
                    player.getEnergyManager().handleAttackHit(hitInfo);
                }
                case CombatTypeArgument_ENTITY_MOVE -> {

                    EntityMoveInfo moveInfo = EntityMoveInfo.parseFrom(entry.getCombatData());
                    GameEntity entity = session.getPlayer().getScene().getEntityById(moveInfo.getEntityId());
                    if (entity != null
                            && session.getPlayer().getSceneLoadState() != Player.SceneLoadState.LOADING) {

                        MotionInfo motionInfo = moveInfo.getMotionInfo();
                        MotionState motionState = motionInfo.getState();

                        EntityMoveEvent event =
                                new EntityMoveEvent(
                                        entity,
                                        new Position(motionInfo.getPos()),
                                        new Position(motionInfo.getRot()),
                                        motionState);
                        event.call();

                        entity.move(event.getPosition(), event.getRotation());
                        entity.setLastMoveSceneTimeMs(moveInfo.getSceneTime());
                        entity.setLastMoveReliableSeq(moveInfo.getReliableSeq());
                        entity.setMotionState(motionState);

                        session
                                .getPlayer()
                                .getStaminaManager()
                                .handleCombatInvocationsNotify(session, moveInfo, entity);

                        var player = session.getPlayer();
                        if (motionState == MotionState.MotionState_MOTION_LAND_SPEED) {
                            player.setCachedLandingSpeed(motionInfo.getSpeed().getY());
                            player.setCachedLandingTimeMillisecond(System.currentTimeMillis());
                            player.setMonitorLandingEvent(true);
                        }
                        if (player.isMonitorLandingEvent()
                                && motionState == MotionState.MotionState_MOTION_FALL_ON_GROUND) {
                            player.setMonitorLandingEvent(false);
                            handleFallOnGround(session, entity, motionState);
                        }

                        if (motionState == MotionState.MotionState_MOTION_NOTIFY
                                || motionState == MotionState.MotionState_MOTION_FIGHT) {
                            continue;
                        }
                    }
                }
                case CombatTypeArgument_COMBAT_ANIMATOR_PARAMETER_CHANGED -> {
                    EvtAnimatorParameterInfo paramInfo =
                            EvtAnimatorParameterInfo.parseFrom(entry.getCombatData());
                    if (paramInfo.getIsServerCache()) {
                        paramInfo = paramInfo.toBuilder().setIsServerCache(false).build();
                        entry = entry.toBuilder().setCombatData(paramInfo.toByteString()).build();
                    }
                }
                default -> {
                    Grasscutter.getLogger().debug("UnhandledCombatType: type={} typeVal={} dataSize={}",
                        entry.getArgumentType(), entry.getArgumentTypeValue(), entry.getCombatData().size());
                }
            }

            session.getPlayer().getCombatInvokeHandler().addEntry(entry.getForwardType(), entry);
        }
    }

    private void handleFallOnGround(GameSession session, GameEntity entity, MotionState motionState) {
        if (session.getPlayer().isInGodMode()) {
            return;
        }

        int maxDelay = 200;
        long actualDelay =
                System.currentTimeMillis() - session.getPlayer().getCachedLandingTimeMillisecond();
        Grasscutter.getLogger()
                .trace(
                        "MOTION_FALL_ON_GROUND received after "
                                + actualDelay
                                + "/"
                                + maxDelay
                                + "ms."
                                + (actualDelay > maxDelay ? " Discard" : ""));
        if (actualDelay > maxDelay) {
            return;
        }
        float currentHP = entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        float maxHP = entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float landingSpeed = session.getPlayer().getCachedLandingSpeed();
        float damageFactor = 0;
        if (landingSpeed < -23.5) {
            damageFactor = 0.33f;
        }
        if (landingSpeed < -25) {
            damageFactor = 0.5f;
        }
        if (landingSpeed < -26.5) {
            damageFactor = 0.66f;
        }
        if (landingSpeed < -28) {
            damageFactor = 1f;
        }
        float damage = maxHP * damageFactor;
        float newHP = currentHP - damage;
        if (newHP < 0) {
            newHP = 0;
        }
        if (damageFactor > 0) {
            Grasscutter.getLogger()
                    .debug(
                            currentHP
                                    + "/"
                                    + maxHP
                                    + "\tLandingSpeed: "
                                    + landingSpeed
                                    + "\tDamageFactor: "
                                    + damageFactor
                                    + "\tDamage: "
                                    + damage
                                    + "\tNewHP: "
                                    + newHP);
        } else {
            Grasscutter.getLogger().trace(currentHP + "/" + maxHP + "\tLandingSpeed: 0\tNo damage");
        }
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, newHP);
        entity
                .getWorld()
                .broadcastPacket(
                        new PacketEntityFightPropUpdateNotify(entity, FightProperty.FIGHT_PROP_CUR_HP));
        if (newHP == 0) {
            session
                    .getPlayer()
                    .getStaminaManager()
                    .killAvatar(session, entity, PlayerDieTypeOuterClass.PlayerDieType.PlayerDieType_PLAYER_DIE_FALL);
        }
        session.getPlayer().setCachedLandingSpeed(0);
    }
}
