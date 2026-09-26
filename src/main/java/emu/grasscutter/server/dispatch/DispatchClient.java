package emu.grasscutter.server.dispatch;

import static emu.grasscutter.config.Configuration.DISPATCH_INFO;

import com.google.gson.*;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.server.event.dispatch.ServerMessageEvent;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.http.handlers.GachaHandler;
import emu.grasscutter.utils.*;
import emu.grasscutter.utils.objects.HandbookBody;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;
import lombok.Getter;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;

public final class DispatchClient extends WebSocketClient implements IDispatcher {
    @Getter private final Logger logger = Grasscutter.getLogger();
    @Getter private final Map<Integer, BiConsumer<WebSocket, JsonElement>> handlers = new HashMap<>();

    @Getter private final Map<Integer, List<Consumer<JsonElement>>> callbacks = new HashMap<>();

    public DispatchClient(URI serverUri) {
        super(serverUri);

        this.setAttachment(true);

        this.registerHandler(PacketIds.GachaHistoryReq, this::fetchGachaHistory);
        this.registerHandler(PacketIds.GmTalkReq, this::handleHandbookAction);
        this.registerHandler(PacketIds.GetPlayerFieldsReq, this::fetchPlayerFields);
        this.registerHandler(PacketIds.GetPlayerByAccountReq, this::fetchPlayerByAccount);
        this.registerHandler(PacketIds.ServerMessageNotify, ServerMessageEvent::invoke);
    }

    private void fetchGachaHistory(WebSocket socket, JsonElement object) {
        var message = IDispatcher.decode(object);
        var accountId = message.get("accountId").getAsString();
        var page = message.get("page").getAsInt();
        var type = message.get("gachaType").getAsInt();

        var response = new JsonObject();

        var player = DatabaseHelper.getPlayerByAccount(accountId);
        if (player == null) {
            response.addProperty("retcode", 1);
            this.sendMessage(PacketIds.GachaHistoryRsp, response);
            return;
        }

        GachaHandler.fetchGachaRecords(player, response, page, type);

        this.sendMessage(PacketIds.GachaHistoryRsp, response);
    }

    private void handleHandbookAction(WebSocket socket, JsonElement object) {
        var message = IDispatcher.decode(object);
        var actionStr = message.get("action").getAsString();
        var data = message.getAsJsonObject("data");

        var action = HandbookBody.Action.valueOf(actionStr);

        var response =
                DispatchUtils.performHandbookAction(
                        action,
                        switch (action) {
                            case GRANT_AVATAR -> JsonUtils.decode(data, HandbookBody.GrantAvatar.class);
                            case GIVE_ITEM -> JsonUtils.decode(data, HandbookBody.GiveItem.class);
                            case TELEPORT_TO -> JsonUtils.decode(data, HandbookBody.TeleportTo.class);
                            case SPAWN_ENTITY -> JsonUtils.decode(data, HandbookBody.SpawnEntity.class);
                        });

        if (response.getStatus() == 1) return;

        this.sendMessage(PacketIds.GmTalkRsp, response);
    }

    private void fetchPlayerFields(WebSocket socket, JsonElement object) {
        var message = IDispatcher.decode(object);
        var playerId = message.get("playerId").getAsInt();
        var fieldsRaw = message.get("fields").getAsJsonArray();

        var player = Grasscutter.getGameServer().getPlayerByUid(playerId, true);
        if (player == null) return;

        var fieldsList = new ArrayList<String>();
        for (var field : fieldsRaw) fieldsList.add(field.getAsString());
        var fields = fieldsList.toArray(new String[0]);

        this.sendMessage(PacketIds.GetPlayerFieldsRsp, DispatchUtils.getPlayerFields(playerId, fields));
    }

    private void fetchPlayerByAccount(WebSocket socket, JsonElement object) {
        var message = IDispatcher.decode(object);
        var accountId = message.get("accountId").getAsString();
        var fieldsRaw = message.get("fields").getAsJsonArray();

        var player = Grasscutter.getGameServer().getPlayerByAccountId(accountId);
        if (player == null) return;

        var fieldsList = new ArrayList<String>();
        for (var field : fieldsRaw) fieldsList.add(field.getAsString());
        var fields = fieldsList.toArray(new String[0]);

        this.sendMessage(
                PacketIds.GetPlayerByAccountRsp, DispatchUtils.getPlayerByAccount(accountId, fields));
    }

    public void sendMessage(int packetId, Object message) {
        var serverMessage = this.encodeMessage(packetId, message);
        var serialized = JSON.toJson(serverMessage).getBytes(StandardCharsets.UTF_8);
        Crypto.xor(serialized, DISPATCH_INFO.encryptionKey);
        this.send(serialized);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        this.sendMessage(PacketIds.LoginNotify, DISPATCH_INFO.dispatchKey);

        this.getLogger().info("Dispatch connection opened.");
    }

    @Override
    public void onMessage(String message) {
        this.getLogger().debug("Received dispatch message from server:\n{}", message);
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        this.handleMessage(this, bytes.array());
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        this.getLogger().info("Dispatch connection closed.");

        new Thread(
                        () -> {
                            try {
                                Thread.sleep(5000L);
                            } catch (Exception ignored) {
                            }

                            Grasscutter.getGameServer()
                                    .setDispatchClient(new DispatchClient(GameServer.getDispatchUrl()));
                            Grasscutter.getGameServer().getDispatchClient().connect();
                        })
                .start();
    }

    @Override
    public void onError(Exception ex) {
        if (ex instanceof ConnectException) {
            this.getLogger().info("Failed to reconnect, trying again in 5s...");
        } else {
            this.getLogger().error("Dispatch connection error.", ex);
        }
    }
}
