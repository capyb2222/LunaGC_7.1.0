package emu.grasscutter.server.event.dispatch;

import com.google.gson.*;
import emu.grasscutter.server.dispatch.IDispatcher;
import emu.grasscutter.server.event.Event;
import java.util.Base64;
import lombok.*;
import org.java_websocket.WebSocket;

@Getter
@RequiredArgsConstructor
public final class ServerMessageEvent extends Event {
    public static void invoke(WebSocket client, JsonElement object) {
        var message = IDispatcher.decode(object);
        var isBinary = message.get("binary").getAsBoolean();
        var data = Base64.getDecoder().decode(message.get("data").getAsString());

        new ServerMessageEvent(client, isBinary, data).call();
    }

    private final WebSocket client;
    private final boolean isBinary;
    private final byte[] message;

    public String asString() {
        if (this.isBinary)
            throw new UnsupportedOperationException("Cannot convert binary message to string.");
        return new String(this.message);
    }

    public JsonObject asJson() {
        return IDispatcher.JSON.fromJson(this.asString(), JsonObject.class);
    }

    public <T> T asJson(Class<T> type) {
        return IDispatcher.JSON.fromJson(this.asString(), type);
    }
}
