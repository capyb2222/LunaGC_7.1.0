package emu.grasscutter.server.dispatch;

import static emu.grasscutter.config.Configuration.DISPATCH_INFO;

import com.google.gson.*;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.server.event.dispatch.ServerMessageEvent;
import emu.grasscutter.utils.Crypto;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;
import lombok.Getter;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;

public final class DispatchServer extends WebSocketServer implements IDispatcher {
    @Getter private final Logger logger = Grasscutter.getLogger();
    @Getter private final Map<Integer, BiConsumer<WebSocket, JsonElement>> handlers = new HashMap<>();

    @Getter private final Map<Integer, List<Consumer<JsonElement>>> callbacks = new HashMap<>();

    public DispatchServer(String address, int port) {
        super(new InetSocketAddress(address, port));

        this.registerHandler(PacketIds.LoginNotify, this::handleLogin);
        this.registerHandler(PacketIds.TokenValidateReq, this::validateToken);
        this.registerHandler(PacketIds.GetAccountReq, this::fetchAccount);
        this.registerHandler(PacketIds.ServerMessageNotify, ServerMessageEvent::invoke);
    }

    private void handleLogin(WebSocket socket, JsonElement object) {
        var dispatchKey = object.getAsString().replaceAll("\"", "");

        if (!dispatchKey.equals(DISPATCH_INFO.dispatchKey)) {
            this.getLogger()
                    .warn("Invalid dispatch key received from {}.", socket.getRemoteSocketAddress());
            this.getLogger().debug("Expected: {}, Received: {}", DISPATCH_INFO.dispatchKey, dispatchKey);
            socket.close();
        } else {
            socket.setAttachment(true);
        }
    }

    private void validateToken(WebSocket socket, JsonElement object) {
        var message = IDispatcher.decode(object);
        var accountId = message.get("uid").getAsString();
        var token = message.get("token").getAsString();

        var account = DatabaseHelper.getAccountById(accountId);
        var valid = account != null && account.getToken().equals(token);
        var response = new JsonObject();
        response.addProperty("valid", valid);
        if (valid) response.add("account", JSON.toJsonTree(account));

        this.sendMessage(socket, PacketIds.TokenValidateRsp, response);
    }

    private void fetchAccount(WebSocket socket, JsonElement object) {
        var message = IDispatcher.decode(object);
        var accountId = message.get("accountId").getAsString();

        var account = DatabaseHelper.getAccountById(accountId);
        this.sendMessage(socket, PacketIds.GetAccountRsp, JSON.toJsonTree(account));
    }

    public void sendMessage(int packetId, Object message) {
        var serverMessage = this.encodeMessage(packetId, message);
        this.getConnections().forEach(socket -> this.sendMessage(socket, serverMessage));
    }

    public void sendMessage(WebSocket socket, Object message) {
        var serialized = JSON.toJson(message).getBytes(StandardCharsets.UTF_8);
        Crypto.xor(serialized, DISPATCH_INFO.encryptionKey);
        socket.send(serialized);
    }

    public void sendMessage(WebSocket socket, int packetId, Object message) {
        this.sendMessage(socket, this.encodeMessage(packetId, message));
    }

    @Override
    public void onStart() {
        this.getLogger().info("Dispatch server started on port {}.", this.getPort());
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        this.getLogger().debug("Dispatch client connected from {}.", conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        this.getLogger()
                .debug("Received dispatch message from {}:\n{}", conn.getRemoteSocketAddress(), message);
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        this.handleMessage(conn, message.array());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        this.getLogger().debug("Dispatch client disconnected from {}.", conn.getRemoteSocketAddress());
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        this.getLogger().warn("Dispatch server error.", ex);
    }
}
