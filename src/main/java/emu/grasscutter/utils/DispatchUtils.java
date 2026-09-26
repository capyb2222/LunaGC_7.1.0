package emu.grasscutter.utils;

import static emu.grasscutter.config.Configuration.DISPATCH_INFO;

import com.google.gson.JsonObject;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.auth.AuthenticationSystem.AuthenticationRequest;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.*;
import emu.grasscutter.server.dispatch.*;
import emu.grasscutter.server.http.handlers.GachaHandler;
import emu.grasscutter.server.http.objects.LoginTokenRequestJson;
import emu.grasscutter.utils.objects.*;
import emu.grasscutter.utils.objects.HandbookBody.*;
import java.util.concurrent.*;
import javax.annotation.Nullable;

public interface DispatchUtils {
    static String getDispatchUrl() {
        return DISPATCH_INFO.dispatchUrl;
    }

    @Nullable static Account authenticate(String accountId, String token) {
        return switch (Grasscutter.getRunMode()) {
            case GAME_ONLY ->
            Grasscutter.getAuthenticationSystem()
                    .getSessionTokenValidator()
                    .authenticate(
                            AuthenticationRequest.builder()
                                    .tokenRequest(LoginTokenRequestJson.builder().uid(accountId).token(token).build())
                                    .build());
            case HYBRID, DISPATCH_ONLY -> {
                var account = DatabaseHelper.getAccountById(accountId);
                if (account == null) yield null;

                yield account.getToken().equals(token) ? account : null;
            }
        };
    }

    @Nullable static String fetchSessionKey(int playerId) {
        return switch (Grasscutter.getRunMode()) {
            case GAME_ONLY -> {
                var player = DatabaseHelper.getPlayerByUid(playerId);
                if (player == null) yield null;

                var accountId = player.getAccountId();
                var account = DispatchUtils.getAccountById(accountId);

                yield account == null ? null : account.getSessionKey();
            }
            case DISPATCH_ONLY -> {
                var playerFields = DispatchUtils.getPlayerFields(playerId, "accountId");
                if (playerFields == null) yield null;

                var accountId = playerFields.get("accountId").getAsString();
                if (accountId == null) yield null;

                var account = DatabaseHelper.getAccountById(accountId);
                yield account == null ? null : account.getSessionKey();
            }
            case HYBRID -> {
                var player = DatabaseHelper.getPlayerByUid(playerId);
                if (player == null) yield null;

                var account = player.getAccount();
                yield account == null ? null : account.getSessionKey();
            }
        };
    }

    @Nullable static Account getAccountById(String accountId) {
        return switch (Grasscutter.getRunMode()) {
            case GAME_ONLY -> {
                var request = new JsonObject();
                request.addProperty("accountId", accountId);

                yield Grasscutter.getGameServer()
                        .getDispatchClient()
                        .await(
                                request,
                                PacketIds.GetAccountReq,
                                PacketIds.GetAccountRsp,
                                packet -> IDispatcher.decode(packet, Account.class));
            }
            case HYBRID, DISPATCH_ONLY -> DatabaseHelper.getAccountById(accountId);
        };
    }

    @Nullable static JsonObject getPlayerFields(int playerId, String... fields) {
        return switch (Grasscutter.getRunMode()) {
            case DISPATCH_ONLY -> {
                var request = new JsonObject();
                request.addProperty("playerId", playerId);
                request.add("fields", IDispatcher.JSON.toJsonTree(fields));

                yield Grasscutter.getDispatchServer()
                        .await(
                                request,
                                PacketIds.GetPlayerFieldsReq,
                                PacketIds.GetPlayerFieldsRsp,
                                IDispatcher.DEFAULT_PARSER);
            }
            case HYBRID, GAME_ONLY -> {
                var player = Grasscutter.getGameServer().getPlayerByUid(playerId, true);
                if (player == null) yield null;

                yield player.fetchFields(fields);
            }
        };
    }

    @Nullable static JsonObject getPlayerByAccount(String accountId, String... fields) {
        return switch (Grasscutter.getRunMode()) {
            case DISPATCH_ONLY -> {
                var request = JObject.c().add("accountId", accountId).add("fields", fields);

                yield Grasscutter.getDispatchServer()
                        .await(
                                request.gson(),
                                PacketIds.GetPlayerByAccountReq,
                                PacketIds.GetPlayerByAccountRsp,
                                IDispatcher.DEFAULT_PARSER);
            }
            case HYBRID, GAME_ONLY -> {
                var player = Grasscutter.getGameServer().getPlayerByAccountId(accountId);
                if (player == null) yield null;

                yield player.fetchFields(fields);
            }
        };
    }

    static JsonObject fetchGachaRecords(String accountId, int page, int gachaType) {
        return switch (Grasscutter.getRunMode()) {
            case DISPATCH_ONLY -> {
                var request = new JsonObject();
                request.addProperty("accountId", accountId);
                request.addProperty("page", page);
                request.addProperty("gachaType", gachaType);

                var future = new CompletableFuture<JsonObject>();
                var server = Grasscutter.getDispatchServer();
                server.registerCallback(
                        PacketIds.GachaHistoryRsp,
                        packet -> future.complete(IDispatcher.decode(packet, JsonObject.class)));

                server.sendMessage(PacketIds.GachaHistoryReq, request);

                try {
                    yield future.get(5L, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    yield null;
                }
            }
            case HYBRID, GAME_ONLY -> {
                var response = new JsonObject();

                var player = Grasscutter.getGameServer().getPlayerByAccountId(accountId);
                if (player == null) {
                    response.addProperty("retcode", 1);
                    yield response;
                }

                GachaHandler.fetchGachaRecords(player, response, page, gachaType);

                yield response;
            }
        };
    }

    static Response performHandbookAction(HandbookBody.Action action, Object data) {
        return switch (Grasscutter.getRunMode()) {
            case DISPATCH_ONLY -> {
                var request = new JsonObject();
                request.addProperty("action", action.name());
                request.add("data", JsonUtils.toJson(data));

                var future = new CompletableFuture<Response>();
                var server = Grasscutter.getDispatchServer();
                server.registerCallback(
                        PacketIds.GmTalkRsp,
                        packet -> future.complete(IDispatcher.decode(packet, Response.class)));

                server.sendMessage(PacketIds.GmTalkReq, request);

                try {
                    yield future.get(5L, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    yield Response.builder()
                            .status(400)
                            .message("No response received from any server.")
                            .build();
                }
            }
            case HYBRID, GAME_ONLY -> switch (action) {
                case GRANT_AVATAR -> HandbookActions.grantAvatar((GrantAvatar) data);
                case GIVE_ITEM -> HandbookActions.giveItem((GiveItem) data);
                case TELEPORT_TO -> HandbookActions.teleportTo((TeleportTo) data);
                case SPAWN_ENTITY -> HandbookActions.spawnEntity((SpawnEntity) data);
            };
        };
    }
}
