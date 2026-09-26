package emu.grasscutter.game.dungeons.fallback;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.dungeons.challenge.trigger.InTimeTrigger;
import emu.grasscutter.game.dungeons.challenge.trigger.KillMonsterCountTrigger;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.server.packet.send.PacketWorktopOptionNotify;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MissingDomainFallbackManager {
    private static final int LOCAL_CHALLENGE_ID = 1;

    private static final int CHALLENGE_DATA_ID = 2;

    private static final int START_OPTION_ID = 7;

    private static final int STARTER_KEY_CONFIG_ID = 9001;

    private static final Map<Integer, FallbackDomainConfig> CONFIGS_BY_SCENE = Map.ofEntries(
			Map.entry(
					40774,
					FallbackDomainConfig.acceptAnyDungeonId(
							40774,
							240774001,
							240774004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003, 1004},
									{1005, 1006, 1007}
							})),

			Map.entry(
					40775,
					FallbackDomainConfig.acceptAnyDungeonId(
							40775,
							240775001,
							240775004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003, 1004},
									{1005, 1006, 1007}
							})),

			Map.entry(
					40776,
					FallbackDomainConfig.acceptAnyDungeonId(
							40776,
							240776001,
							240776004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003},
									{1004, 1005, 1006}
							})),

			Map.entry(
					40777,
					FallbackDomainConfig.acceptAnyDungeonId(
							40777,
							240777001,
							240777004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001}
							})),
							
			Map.entry(
					40764,
					FallbackDomainConfig.acceptAnyDungeonId(
							40764,
							240764001,
							240764004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003, 1004},
									{1005, 1006, 1007}
							})),

			Map.entry(
					40765,
					FallbackDomainConfig.acceptAnyDungeonId(
							40765,
							240765001,
							240765004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003, 1004},
									{1005, 1006, 1007}
							})),

			Map.entry(
					40766,
					FallbackDomainConfig.acceptAnyDungeonId(
							40766,
							240766001,
							240766004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003},
									{1004, 1005, 1006}
							})),
			
			Map.entry(
					40767,
					FallbackDomainConfig.acceptAnyDungeonId(
							40767,
							240767001,
							240767004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002}
							})),
			
			Map.entry(
					40792,
					FallbackDomainConfig.exactDungeonIds(
							40792,
							Set.of(5018),
							240792001,
							240792004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003, 1004},
									{1005, 1006, 1007}
							})),
			
			Map.entry(
					40793,
					FallbackDomainConfig.exactDungeonIds(
							40793,
							Set.of(5019),
							240793001,
							240793004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003, 1004},
									{1005, 1006, 1007}
							})),

			Map.entry(
					40794,
					FallbackDomainConfig.exactDungeonIds(
							40794,
							Set.of(5020),
							240794001,
							240794004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003},
									{1004, 1005, 1006}
							})),

			Map.entry(
					40795,
					FallbackDomainConfig.exactDungeonIds(
							40795,
							Set.of(5021),
							240795001,
							240795004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003}
							})),
			
            Map.entry(
                    40796,
                    FallbackDomainConfig.exactDungeonIds(
                            40796,
                            Set.of(5022),
                            240796001,
                            240796004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001, 1002, 1003},
                                    {1004, 1005, 1006}
                            })),

            Map.entry(
                    40797,
                    FallbackDomainConfig.exactDungeonIds(
                            40797,
                            Set.of(5023),
                            240797001,
                            240797004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001, 1002, 1003},
                                    {1004, 1005, 1006}
                            })),

            Map.entry(
                    40798,
                    FallbackDomainConfig.exactDungeonIds(
                            40798,
                            Set.of(5024),
                            240798001,
                            240798004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001, 1002, 1003},
                                    {1004, 1005, 1006}
                            })),

            Map.entry(
                    40799,
                    FallbackDomainConfig.exactDungeonIds(
                            40799,
                            Set.of(5025),
                            240799001,
                            240799004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001}
                            })),
			
			Map.entry(
					40780,
					FallbackDomainConfig.acceptAnyDungeonId(
							40780,
							240780001,
							240780004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002},
									{1003, 1004, 1005}
							})),

			Map.entry(
					40781,
					FallbackDomainConfig.acceptAnyDungeonId(
							40781,
							240781001,
							240781004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003},
									{1004, 1005, 1006}
							})),

			Map.entry(
					40782,
					FallbackDomainConfig.acceptAnyDungeonId(
							40782,
							240782001,
							240782004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003},
									{1004, 1005, 1006}
							})),

			Map.entry(
					40783,
					FallbackDomainConfig.acceptAnyDungeonId(
							40783,
							240783001,
							240783004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003},
									{1004, 1005}
							})),
			
			Map.entry(
					40788,
					FallbackDomainConfig.acceptAnyDungeonId(
							40788,
							240788001,
							240788004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010, 1011, 1012},
									{1013, 1014, 1015}
							})),

			Map.entry(
					40789,
					FallbackDomainConfig.acceptAnyDungeonId(
							40789,
							240789001,
							240789004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003, 1004, 1005, 1006},
									{1007, 1008, 1009, 1010}
							})),

			Map.entry(
					40790,
					FallbackDomainConfig.acceptAnyDungeonId(
							40790,
							240790001,
							240790004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001},
									{1002, 1003, 1004, 1005}
							})),

			Map.entry(
					40791,
					FallbackDomainConfig.acceptAnyDungeonId(
							40791,
							240791001,
							240791004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002},
									{1003, 1004, 1005}
							})),
			
			Map.entry(
					40784,
					FallbackDomainConfig.exactDungeonIds(
							40784,
							Set.of(4484),
							240784001,
							240784004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010, 1011, 1012},
									{1013, 1014, 1015}
							})),

			Map.entry(
					40785,
					FallbackDomainConfig.exactDungeonIds(
							40785,
							Set.of(4485),
							240785001,
							240785004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010, 1011, 1012},
									{1013, 1014, 1015}
							})),

			Map.entry(
					40786,
					FallbackDomainConfig.exactDungeonIds(
							40786,
							Set.of(4486),
							240786001,
							240786004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003},
									{1004, 1005, 1006}
							})),

			Map.entry(
					40787,
					FallbackDomainConfig.exactDungeonIds(
							40787,
							Set.of(4487),
							240787001,
							240787004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003, 1004},
									{1005, 1006}
							})),
							
			Map.entry(
					40760,
					FallbackDomainConfig.acceptAnyDungeonId(
							40760,
							240760001,
							240760004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002},
									{1003}
							})),

			Map.entry(
					40761,
					FallbackDomainConfig.acceptAnyDungeonId(
							40761,
							240761001,
							240761004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003},
									{1004}
							})),

			Map.entry(
					40762,
					FallbackDomainConfig.acceptAnyDungeonId(
							40762,
							240762001,
							240762004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002}
							})),

			Map.entry(
					40763,
					FallbackDomainConfig.acceptAnyDungeonId(
							40763,
							240763001,
							240763004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002}
							})),
			
			Map.entry(
					40770,
					FallbackDomainConfig.acceptAnyDungeonId(
							40770,
							240770001,
							240770004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
											{1001, 1002, 1003},
											{1004, 1005, 1006}
							})),

			Map.entry(
					40771,
					FallbackDomainConfig.acceptAnyDungeonId(
							40771,
							240771001,
							240771004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
											{1001, 1002, 1003, 1004},
											{1005, 1006, 1007}
							})),

			Map.entry(
					40772,
					FallbackDomainConfig.acceptAnyDungeonId(
							40772,
							240772001,
							240772004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
											{1001, 1002, 1003},
											{1004, 1005}
							})),

			Map.entry(
					40773,
					FallbackDomainConfig.acceptAnyDungeonId(
							40773,
							240773001,
							240773004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
											{1001, 1002, 1003},
											{1004, 1005}
							})),
			
			Map.entry(
					40664,
					FallbackDomainConfig.exactDungeonIds(
							40664,
							Set.of(5064),
							240664001,
							240664004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003, 1004, 1005, 1006},
									{1007, 1008, 1009, 1010},
									{1011, 1012}
							})),

			Map.entry(
					40665,
					FallbackDomainConfig.exactDungeonIds(
							40665,
							Set.of(5065),
							240665001,
							240665004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003, 1004, 1005},
									{1006, 1007, 1008, 1009}
							})),

			Map.entry(
					40666,
					FallbackDomainConfig.exactDungeonIds(
							40666,
							Set.of(5066),
							240666001,
							240666004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002, 1003, 1004},
									{1005, 1006, 1007}
							})),

			Map.entry(
					40667,
					FallbackDomainConfig.exactDungeonIds(
							40667,
							Set.of(5067),
							240667001,
							240667004,
							STARTER_KEY_CONFIG_ID,
							300,
							new int[][] {
									{1001, 1002},
									{1003, 1004}
							})),
            Map.entry(
                    40810,
                    FallbackDomainConfig.exactDungeonIds(
                            40810,
                            Set.of(4665),
                            240810001,
                            240810004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001, 1002, 1003, 1004},
                                    {1005, 1006, 1007}
                            })),

            Map.entry(
                    40811,
                    FallbackDomainConfig.exactDungeonIds(
                            40811,
                            Set.of(4666),
                            240811001,
                            240811004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001, 1002, 1003, 1004},
                                    {1005, 1006, 1007}
                            })),

            Map.entry(
                    40812,
                    FallbackDomainConfig.exactDungeonIds(
                            40812,
                            Set.of(4667),
                            240812001,
                            240812004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001, 1002}
                            })),

            Map.entry(
                    40813,
                    FallbackDomainConfig.exactDungeonIds(
                            40813,
                            Set.of(4668),
                            240813001,
                            240813004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001}
                            })),
            Map.entry(
                    40820,
                    FallbackDomainConfig.exactDungeonIds(
                            40820,
                            Set.of(4683),
                            240820001,
                            240820004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001, 1002, 1003, 1004},
                                    {1005, 1006, 1007}
                            })),

            Map.entry(
                    40821,
                    FallbackDomainConfig.exactDungeonIds(
                            40821,
                            Set.of(4684),
                            240821001,
                            240821004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001, 1002, 1003, 1004},
                                    {1005, 1006, 1007}
                            })),

            Map.entry(
                    40822,
                    FallbackDomainConfig.exactDungeonIds(
                            40822,
                            Set.of(4685),
                            240822001,
                            240822004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001, 1002, 1003, 1004},
                                    {1005, 1006, 1007}
                            })),

            Map.entry(
                    40823,
                    FallbackDomainConfig.exactDungeonIds(
                            40823,
                            Set.of(4686),
                            240823001,
                            240823004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001, 1002, 1003},
                                    {1004, 1005, 1006}
                            })),
							
            Map.entry(
                    40824,
                    FallbackDomainConfig.exactDungeonIds(
                            40824,
                            Set.of(4687),
                            240824001,
                            240824004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001, 1002},
                                    {1003}
                            })),

            Map.entry(
                    40825,
                    FallbackDomainConfig.exactDungeonIds(
                            40825,
                            Set.of(4688),
                            240825001,
                            240825004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001}
                            })),

            Map.entry(
                    40826,
                    FallbackDomainConfig.exactDungeonIds(
                            40826,
                            Set.of(4689),
                            240826001,
                            240826004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001}
                            })),

            Map.entry(
                    40827,
                    FallbackDomainConfig.exactDungeonIds(
                            40827,
                            Set.of(4690),
                            240827001,
                            240827004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001, 1002}
                            })),
							
            Map.entry(
                    40704,
                    FallbackDomainConfig.exactDungeonIds(
                            40704,
                            Set.of(4651, 4655, 4659),
                            240704001,
                            240704004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001, 1002, 1003, 1004},
                                    {1005, 1006, 1007}
                            })),

            Map.entry(
                    40705,
                    FallbackDomainConfig.exactDungeonIds(
                            40705,
                            Set.of(4652, 4656, 4660),
                            240705001,
                            240705004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001, 1002, 1003, 1004},
                                    {1005, 1006, 1007}
                            })),

            Map.entry(
                    40706,
                    FallbackDomainConfig.exactDungeonIds(
                            40706,
                            Set.of(4653, 4657, 4661),
                            240706001,
                            240706004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001, 1002, 1003, 1004},
                                    {1005, 1006, 1007}
                            })),

            Map.entry(
                    40707,
                    FallbackDomainConfig.exactDungeonIds(
                            40707,
                            Set.of(4654, 4658, 4662),
                            240707001,
                            240707004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001, 1002, 1003}
                            })),

            Map.entry(
                    40816,
                    FallbackDomainConfig.exactDungeonIds(
                            40816,
                            Set.of(4671, 4675, 4679),
                            240816001,
                            240816004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001, 1002, 1003, 1004},
                                    {1005, 1006, 1007}
                            })),

            Map.entry(
                    40817,
                    FallbackDomainConfig.exactDungeonIds(
                            40817,
                            Set.of(4672, 4676, 4680),
                            240817001,
                            240817004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001, 1002, 1003, 1004},
                                    {1005, 1006, 1007}
                            })),

            Map.entry(
                    40818,
                    FallbackDomainConfig.exactDungeonIds(
                            40818,
                            Set.of(4673, 4677, 4681),
                            240818001,
                            240818004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001, 1002, 1003, 1004},
                                    {1005, 1006, 1007}
                            })),

            Map.entry(
                    40819,
                    FallbackDomainConfig.exactDungeonIds(
                            40819,
                            Set.of(4674, 4678, 4682),
                            240819001,
                            240819004,
                            STARTER_KEY_CONFIG_ID,
                            300,
                            new int[][] {
                                    {1001, 1002}
                            }))
	);

    private MissingDomainFallbackManager() {}

    public static int getStatueDropOverride(int dungeonId) {
		return switch (dungeonId) {
			case 5060 -> 85333500;
			case 5061 -> 85334000;
			case 5062 -> 85334500;
			case 5063 -> 85335500;

			case 5064 -> 85343500;
			case 5065 -> 85344000;
			case 5066 -> 85344500;
			case 5067 -> 85345500;

			default -> 0;
		};
	}



    public static void install(Scene scene, DungeonData dungeonData) {
        if (scene == null || dungeonData == null) {
            return;
        }

        FallbackDomainConfig config = CONFIGS_BY_SCENE.get(scene.getId());
        if (config == null) {
            return;
        }

        if (!config.acceptsDungeonId(dungeonData.getId())) {
			Grasscutter.getLogger()
					.warn(
							"[MissingDomainFallback] Scene {} matched fallback config, but dungeon id {} is not in the allowed list {}.",
							scene.getId(),
							dungeonData.getId(),
							config.allowedDungeonIds);
			return;
		}

        scene.runWhenHostInitialized(
                () -> Grasscutter.getGameServer()
                        .getScheduler()
                        .scheduleDelayedTask(() -> prepareFallbackDomain(scene, config), 1));
    }

    public static boolean handleSelectWorktopOption(Scene scene, GameEntity entity, int optionId) {
        if (scene == null || entity == null || optionId != START_OPTION_ID) {
            return false;
        }

        FallbackDomainConfig config = CONFIGS_BY_SCENE.get(scene.getId());
        if (config == null) {
            return false;
        }

        if (!(entity instanceof EntityGadget gadget)) {
            return false;
        }

        if (gadget.getGroupId() != config.combatGroupId
                || gadget.getConfigId() != config.starterKeyConfigId) {
            return false;
        }

        if (scene.getDungeonManager() == null) {
            return false;
        }

        if (scene.getChallenge() != null && scene.getChallenge().inProgress()) {
            return true;
        }

        Grasscutter.getLogger()
                .info(
                        "[MissingDomainFallback] Starter key selected for scene {}.",
                        config.sceneId);

        scene.removeEntity(gadget, VisionType.VisionType_VISION_REMOVE);
        startFallback(scene, config);

        return true;
    }

    private static void prepareFallbackDomain(Scene scene, FallbackDomainConfig config) {
        if (scene == null || scene.getDungeonManager() == null) {
            return;
        }

        loadGroup(scene, config.combatGroupId);
        loadGroup(scene, config.rewardGroupId);

        Grasscutter.getGameServer()
                .getScheduler()
                .scheduleDelayedTask(() -> setupStarterKey(scene, config), 1);
    }

    private static void setupStarterKey(Scene scene, FallbackDomainConfig config) {
        if (scene == null) {
            return;
        }

        GameEntity entity = scene.getEntityByConfigId(config.starterKeyConfigId, config.combatGroupId);
        EntityGadget gadget;

        if (entity instanceof EntityGadget initialGadget) {
            gadget = initialGadget;
        } else {
            Grasscutter.getLogger()
                    .warn(
                            "[MissingDomainFallback] Starter key config {} was not present in scene {} group {}. Forcing suite refresh once.",
                            config.starterKeyConfigId,
                            config.sceneId,
                            config.combatGroupId);

            SceneGroup group = scene.getScriptManager().getGroupById(config.combatGroupId);
            if (group != null) {
                group.dynamic_load = true;
                group.dontUnload = true;
                scene.getScriptManager().refreshGroupSuite(config.combatGroupId, 1);
            }

            entity = scene.getEntityByConfigId(config.starterKeyConfigId, config.combatGroupId);
            if (!(entity instanceof EntityGadget refreshedGadget)) {
                Grasscutter.getLogger()
                        .warn(
                                "[MissingDomainFallback] Starter key config {} still not found in scene {} group {} after suite refresh.",
                                config.starterKeyConfigId,
                                config.sceneId,
                                config.combatGroupId);
                return;
            }

            gadget = refreshedGadget;
        }

        if (!(gadget.getContent() instanceof GadgetWorktop worktop)) {
            Grasscutter.getLogger()
                    .warn(
                            "[MissingDomainFallback] Starter key config {} in scene {} is not a worktop gadget.",
                            config.starterKeyConfigId,
                            config.sceneId);
            return;
        }

        worktop.addWorktopOptions(new int[] {START_OPTION_ID});
        scene.broadcastPacket(new PacketWorktopOptionNotify(gadget));

        Grasscutter.getLogger()
                .info(
                        "[MissingDomainFallback] Starter key armed for scene {}.",
                        config.sceneId);
    }

    private static void startFallback(Scene scene, FallbackDomainConfig config) {
        if (scene == null || scene.getDungeonManager() == null) {
            return;
        }

        if (scene.getChallenge() != null && scene.getChallenge().inProgress()) {
            return;
        }

        loadGroup(scene, config.combatGroupId);
        loadGroup(scene, config.rewardGroupId);

        SceneGroup combatGroup = scene.getScriptManager().getGroupById(config.combatGroupId);
        if (combatGroup == null) {
            Grasscutter.getLogger()
                    .warn(
                            "[MissingDomainFallback] Scene {} combat group {} is missing.",
                            config.sceneId,
                            config.combatGroupId);
            return;
        }

        int totalMonsterCount = config.totalMonsterCount();

        WorldChallenge challenge =
                new FallbackWaveChallenge(
                        scene,
                        combatGroup,
                        LOCAL_CHALLENGE_ID,
                        CHALLENGE_DATA_ID,
                        config.timeLimitSeconds,
                        totalMonsterCount,
                        config);

        Grasscutter.getLogger()
                .info(
                        "[MissingDomainFallback] Starting fallback wave challenge for scene {} with {} total monsters.",
                        config.sceneId,
                        totalMonsterCount);

        scene.setChallenge(challenge);
        challenge.start();

        spawnWave(scene, combatGroup, config, 0);
    }

    private static void loadGroup(Scene scene, int groupId) {
        if (scene == null || scene.getScriptManager() == null) {
            return;
        }

        SceneGroup group = scene.getScriptManager().getGroupById(groupId);
        if (group == null) {
            Grasscutter.getLogger()
                    .warn(
                            "[MissingDomainFallback] Could not find fallback group {} in scene {}.",
                            groupId,
                            scene.getId());
            return;
        }

        group.dynamic_load = true;
        group.dontUnload = true;

        int suite = scene.loadDynamicGroup(groupId);
        if (suite < 0) {
            Grasscutter.getLogger()
                    .warn(
                            "[MissingDomainFallback] Could not load fallback group {} in scene {}.",
                            groupId,
                            scene.getId());
        }
    }

    private static void spawnWave(Scene scene, SceneGroup group, FallbackDomainConfig config, int waveIndex) {
        if (scene == null || group == null || config == null) {
            return;
        }

        if (waveIndex < 0 || waveIndex >= config.waves.length) {
            return;
        }

        int[] wave = config.waves[waveIndex];

        Grasscutter.getLogger()
                .info(
                        "[MissingDomainFallback] Spawning wave {}/{} for scene {} with {} monsters.",
                        waveIndex + 1,
                        config.waves.length,
                        config.sceneId,
                        wave.length);

        for (int configId : wave) {
            spawnMonster(scene, group, configId);
        }
    }

    private static void spawnMonster(Scene scene, SceneGroup group, int configId) {
        if (group == null || group.monsters == null || !group.monsters.containsKey(configId)) {
            Grasscutter.getLogger()
                    .warn(
                            "[MissingDomainFallback] Monster config {} not found in group {}.",
                            configId,
                            group != null ? group.id : 0);
            return;
        }

        EntityMonster monster =
                scene.getScriptManager()
                        .createMonster(group.id, group.block_id, group.monsters.get(configId));

        if (monster == null) {
            Grasscutter.getLogger()
                    .warn(
                            "[MissingDomainFallback] Failed to create monster config {} in group {}.",
                            configId,
                            group.id);
            return;
        }

        scene.addEntity(monster);
    }

    private static final class FallbackWaveChallenge extends WorldChallenge {
        private final FallbackDomainConfig config;
        private int currentWaveIndex;
        private int currentWaveKillCount;

        private FallbackWaveChallenge(
                Scene scene,
                SceneGroup group,
                int challengeIndex,
                int challengeId,
                int timeLimitSeconds,
                int totalMonsterCount,
                FallbackDomainConfig config) {
            super(
                    scene,
                    group,
                    challengeId,
                    challengeIndex,
                    List.of(totalMonsterCount, timeLimitSeconds),
                    timeLimitSeconds,
                    totalMonsterCount,
                    List.of(new KillMonsterCountTrigger(), new InTimeTrigger()));

            this.config = config;
            this.currentWaveIndex = 0;
            this.currentWaveKillCount = 0;
        }

        @Override
        public void onMonsterDeath(EntityMonster monster) {
            if (!inProgress()) {
                return;
            }

            if (monster == null || monster.getGroupId() != getGroup().id) {
                return;
            }

            super.onMonsterDeath(monster);

            if (!inProgress()) {
                return;
            }

            this.currentWaveKillCount++;

            if (this.currentWaveIndex >= this.config.waves.length) {
                return;
            }

            int currentWaveSize = this.config.waves[this.currentWaveIndex].length;
            if (this.currentWaveKillCount < currentWaveSize) {
                return;
            }

            this.currentWaveIndex++;
            this.currentWaveKillCount = 0;

            if (this.currentWaveIndex >= this.config.waves.length) {
                return;
            }

            int nextWaveIndex = this.currentWaveIndex;

            Grasscutter.getGameServer()
                    .getScheduler()
                    .scheduleDelayedTask(
                            () -> {
                                if (getScene() == null || getScene().getChallenge() != this || !inProgress()) {
                                    return;
                                }

                                spawnWave(getScene(), getGroup(), this.config, nextWaveIndex);
                            },
                            1);
        }
    }

    private static final class FallbackDomainConfig {
		private final int sceneId;
		private final Set<Integer> allowedDungeonIds;
		private final boolean acceptAnyDungeonId;
		private final int combatGroupId;
		private final int rewardGroupId;
		private final int starterKeyConfigId;
		private final int timeLimitSeconds;
		private final int[][] waves;

		private static FallbackDomainConfig exactDungeonIds(
				int sceneId,
				Set<Integer> allowedDungeonIds,
				int combatGroupId,
				int rewardGroupId,
				int starterKeyConfigId,
				int timeLimitSeconds,
				int[][] waves) {
			return new FallbackDomainConfig(
					sceneId,
					allowedDungeonIds,
					false,
					combatGroupId,
					rewardGroupId,
					starterKeyConfigId,
					timeLimitSeconds,
					waves);
		}

		private static FallbackDomainConfig acceptAnyDungeonId(
				int sceneId,
				int combatGroupId,
				int rewardGroupId,
				int starterKeyConfigId,
				int timeLimitSeconds,
				int[][] waves) {
			return new FallbackDomainConfig(
					sceneId,
					Set.of(),
					true,
					combatGroupId,
					rewardGroupId,
					starterKeyConfigId,
					timeLimitSeconds,
					waves);
		}

		private FallbackDomainConfig(
				int sceneId,
				Set<Integer> allowedDungeonIds,
				boolean acceptAnyDungeonId,
				int combatGroupId,
				int rewardGroupId,
				int starterKeyConfigId,
				int timeLimitSeconds,
				int[][] waves) {
			this.sceneId = sceneId;
			this.allowedDungeonIds = allowedDungeonIds;
			this.acceptAnyDungeonId = acceptAnyDungeonId;
			this.combatGroupId = combatGroupId;
			this.rewardGroupId = rewardGroupId;
			this.starterKeyConfigId = starterKeyConfigId;
			this.timeLimitSeconds = timeLimitSeconds;
			this.waves = waves;
		}

		private boolean acceptsDungeonId(int dungeonId) {
			return this.acceptAnyDungeonId || this.allowedDungeonIds.contains(dungeonId);
		}

		private int totalMonsterCount() {
			return Arrays.stream(this.waves).mapToInt(wave -> wave.length).sum();
		}
	}
}