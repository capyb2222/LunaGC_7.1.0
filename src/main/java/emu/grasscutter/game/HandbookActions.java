package emu.grasscutter.game;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.server.packet.send.PacketAddNoGachaAvatarCardNotify;
import emu.grasscutter.utils.objects.HandbookBody.*;
import java.util.Objects;

public interface HandbookActions {
    static boolean isAuthenticated(Player player, String token) {
        if (player == null || token == null) return false;
        return player.getSessionKey().equals(token);
    }

    static Response grantAvatar(GrantAvatar request) {
        if (request.getPlayer() == null || request.getAvatar() == null) {
            return Response.builder().status(400).message("Invalid request.").build();
        }

        try {
            var playerId = Integer.parseInt(request.getPlayer());
            var player = Grasscutter.getGameServer().getPlayerByUid(playerId);

            var avatarId = Integer.parseInt(request.getAvatar());
            var avatarData = GameData.getAvatarDataMap().get(avatarId);

            if (player == null) {
                return Response.builder().status(1).message("Player not found.").build();
            }
            if (!HandbookActions.isAuthenticated(player, request.getPlayerToken())) {
                return Response.builder().status(1).message("Player not authorized.").build();
            }
            if (avatarData == null) {
                return Response.builder().status(400).message("Invalid avatar ID.").build();
            }

            var avatar = new Avatar(avatarData);
            avatar.setLevel(request.getLevel());
            avatar.setPromoteLevel(Avatar.getMinPromoteLevel(avatar.getLevel()));
            Objects.requireNonNull(avatar.getSkillDepot())
                    .getSkillsAndEnergySkill()
                    .forEach(id -> avatar.setSkillLevel(id, request.getTalentLevels()));
            avatar.forceConstellationLevel(request.getConstellations());
            avatar.recalcStats(true);
            avatar.save();

            player.addAvatar(avatar);
            player.sendPacket(new PacketAddNoGachaAvatarCardNotify(avatar, ActionReason.Gm));
            return Response.builder().status(200).message("Avatar granted.").build();
        } catch (NumberFormatException ignored) {
            return Response.builder().status(500).message("Invalid player UID or avatar ID.").build();
        } catch (Exception exception) {
            Grasscutter.getLogger().debug("A handbook command error occurred.", exception);
            return Response.builder()
                    .status(500)
                    .message("An error occurred while granting the avatar.")
                    .build();
        }
    }

    static Response giveItem(GiveItem request) {
        if (request.getPlayer() == null || request.getItem() == null) {
            return Response.builder().status(400).message("Invalid request.").build();
        }

        try {
            var playerId = Integer.parseInt(request.getPlayer());
            var player = Grasscutter.getGameServer().getPlayerByUid(playerId);

            var itemId = Integer.parseInt(request.getItem());
            var itemData = GameData.getItemDataMap().get(itemId);

            if (player == null) {
                return Response.builder().status(1).message("Player not found.").build();
            }
            if (!HandbookActions.isAuthenticated(player, request.getPlayerToken())) {
                return Response.builder().status(1).message("Player not authorized.").build();
            }
            if (itemData == null) {
                return Response.builder().status(400).message("Invalid player UID or item ID.").build();
            }

            var amount = request.getAmount();
            if (amount > Integer.MAX_VALUE) {
                var times = Math.floor((double) amount / Integer.MAX_VALUE);
                amount = amount % Integer.MAX_VALUE;

                for (var i = 0; i < times; i++) {
                    var itemStack = new GameItem(itemData, Integer.MAX_VALUE);
                    player.getInventory().addItem(itemStack, ActionReason.Gm);
                }
            }

            var itemStack = new GameItem(itemData, (int) amount);
            player.getInventory().addItem(itemStack, ActionReason.Gm);

            return Response.builder().status(200).message("Item granted.").build();
        } catch (NumberFormatException ignored) {
            return Response.builder().status(500).message("Invalid player UID or item ID.").build();
        } catch (Exception exception) {
            Grasscutter.getLogger().debug("A handbook command error occurred.", exception);
            return Response.builder()
                    .status(500)
                    .message("An error occurred while granting the item.")
                    .build();
        }
    }

    static Response teleportTo(TeleportTo request) {
        if (request.getPlayer() == null || request.getScene() == null) {
            return Response.builder().status(400).message("Invalid request.").build();
        }

        try {
            var playerId = Integer.parseInt(request.getPlayer());
            var player = Grasscutter.getGameServer().getPlayerByUid(playerId);

            var sceneId = Integer.parseInt(request.getScene());

            if (player == null) {
                return Response.builder().status(1).message("Player not found.").build();
            }
            if (!HandbookActions.isAuthenticated(player, request.getPlayerToken())) {
                return Response.builder().status(1).message("Player not authorized.").build();
            }

            var scene = player.getWorld().getSceneById(sceneId);
            if (scene == null) {
                return Response.builder().status(400).message("Invalid scene ID.").build();
            }

            var position = scene.getDefaultLocation(player);
            var rotation = scene.getDefaultRotation(player);
            scene.getWorld().transferPlayerToScene(player, scene.getId(), position);
            player.getRotation().set(rotation);

            return Response.builder().status(200).message("Player teleported.").build();
        } catch (NumberFormatException ignored) {
            return Response.builder().status(400).message("Invalid player UID or scene ID.").build();
        } catch (Exception exception) {
            Grasscutter.getLogger().debug("A handbook command error occurred.", exception);
            return Response.builder()
                    .status(500)
                    .message("An error occurred while teleporting to the scene.")
                    .build();
        }
    }

    static Response spawnEntity(SpawnEntity request) {
        if (request.getPlayer() == null || request.getEntity() == null) {
            return Response.builder().status(400).message("Invalid request.").build();
        }

        try {
            var playerId = Integer.parseInt(request.getPlayer());
            var player = Grasscutter.getGameServer().getPlayerByUid(playerId);

            var entityId = Integer.parseInt(request.getEntity());
            var entityData = GameData.getMonsterDataMap().get(entityId);

            if (player == null) {
                return Response.builder().status(1).message("Player not found.").build();
            }
            if (!HandbookActions.isAuthenticated(player, request.getPlayerToken())) {
                return Response.builder().status(1).message("Player not authorized.").build();
            }
            if (entityData == null) {
                return Response.builder().status(400).message("Invalid entity ID.").build();
            }

            var scene = player.getScene();
            var level = request.getLevel();
            if (scene == null || level > 200 || level < 1) {
                return Response.builder().status(400).message("Invalid scene or level.").build();
            }

            for (var i = 1; i <= request.getAmount(); i++) {
                var entity =
                        new EntityMonster(scene, entityData, player.getPosition(), player.getRotation(), level);
                scene.addEntity(entity);
            }

            return Response.builder().status(200).message("Entity(s) spawned.").build();
        } catch (NumberFormatException ignored) {
            return Response.builder().status(400).message("Invalid player UID or entity ID.").build();
        } catch (Exception exception) {
            Grasscutter.getLogger().debug("A handbook command error occurred.", exception);
            return Response.builder()
                    .status(500)
                    .message("An error occurred while teleporting to the scene.")
                    .build();
        }
    }
}
