package emu.grasscutter.game.talk;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.TalkConfigData;
import emu.grasscutter.data.excels.TalkConfigData.TalkExecParam;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.game.*;
import it.unimi.dsi.fastutil.ints.*;

public final class TalkSystem extends BaseGameSystem {
    private final Int2ObjectMap<TalkExecHandler> execHandlers = new Int2ObjectOpenHashMap<>();

    public TalkSystem(GameServer server) {
        super(server);

        this.registerHandlers(this.execHandlers, TalkExecHandler.class);
    }

    public <T> void registerHandlers(Int2ObjectMap<T> map, Class<T> clazz) {
        var handlerClasses = Grasscutter.reflector.getSubTypesOf(clazz);
        for (var obj : handlerClasses) {
            this.registerTalkHandler(map, obj);
        }
    }

    public <T> void registerTalkHandler(Int2ObjectMap<T> map, Class<? extends T> handlerClass) {
        try {
            var value = 0;
            if (handlerClass.isAnnotationPresent(TalkValueExec.class)) {
                TalkValueExec opcode = handlerClass.getAnnotation(TalkValueExec.class);
                value = opcode.value().getValue();
            } else {
                return;
            }

            if (value <= 0) return;
            map.put(value, handlerClass.getDeclaredConstructor().newInstance());
        } catch (Exception exception) {
            Grasscutter.getLogger().debug("Unable to register talk handler.", exception);
        }
    }

    public void triggerExec(Player player, TalkConfigData talkData, TalkExecParam execParam) {
        var handler = this.execHandlers.get(execParam.getType().getValue());
        if (handler == null) {
            Grasscutter.getLogger()
                    .debug(
                            "Could not execute talk handlers for {} ({}).",
                            talkData.getId(),
                            execParam.getType().getValue());
            return;
        }

        handler.execute(player, talkData, execParam);
    }
}
