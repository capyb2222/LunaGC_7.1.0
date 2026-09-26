package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Opcodes(PacketOpcodes.PlayerForceExitReq)
public class HandlerPlayerForceExitReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        session.send(new BasePacket(PacketOpcodes.PlayerForceExitRsp));
        CompletableFuture.runAsync(
                session::close, CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS));
    }
}
