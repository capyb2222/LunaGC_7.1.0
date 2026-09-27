package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.gacha.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GachaWishReqOuterClass.GachaWishReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGachaWishRsp;
import java.util.Arrays;

@Opcodes(PacketOpcodes.GachaWishReq)
public class HandlerGachaWishReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        GachaWishReq req = GachaWishReq.parseFrom(payload);

        GachaBanner banner =
                session.getServer().getGachaSystem().getGachaBanners().get(req.getGachaScheduleId());
        if (banner == null || !banner.hasEpitomized()) {
            session.send(new PacketGachaWishRsp(Retcode.RET_GACHA_SCHEDULE_NOT_MATCH));
            return;
        }

        if (req.getItemId() != 0
                && Arrays.stream(banner.getRateUpItems5()).noneMatch(id -> id == req.getItemId())) {
            session.send(new PacketGachaWishRsp(Retcode.RET_GACHA_WISH_INVALID_ITEM));
            return;
        }

        PlayerGachaBannerInfo gachaInfo = session.getPlayer().getGachaInfo().getBannerInfo(banner);

        if (gachaInfo.getWishItemId() != req.getItemId()) {
            gachaInfo.setFailedChosenItemPulls(0);
            gachaInfo.setWishItemId(req.getItemId());
        }

        session.send(
                new PacketGachaWishRsp(
                        req.getGachaType(),
                        req.getGachaScheduleId(),
                        gachaInfo.getWishItemId(),
                        gachaInfo.getFailedChosenItemPulls(),
                        banner.getWishMaxProgress()));
    }
}
