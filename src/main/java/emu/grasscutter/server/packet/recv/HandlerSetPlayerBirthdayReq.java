package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.mail.BirthdayMailSystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SetPlayerBirthdayReqOuterClass.SetPlayerBirthdayReq;
import emu.grasscutter.net.proto.SocialDetailOuterClass.SocialDetail;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.*;

@Opcodes(PacketOpcodes.SetPlayerBirthdayReq)
public class HandlerSetPlayerBirthdayReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        SetPlayerBirthdayReq req = SetPlayerBirthdayReq.parseFrom(payload);

        if (session.getPlayer().hasBirthday()) {
            session.send(new PacketSetPlayerBirthdayRsp(7009));
            return;
        }

        int month = req.getBirthday().getMonth();
        int day = req.getBirthday().getDay();

        if (!isValidBirthday(month, day)) {
            session.send(new PacketSetPlayerBirthdayRsp(7022));
            return;
        }

		var player = session.getPlayer();

		player.setBirthday(day, month);

		player.save();

		SocialDetail.Builder detail = player.getSocialDetail();

		session.send(new PacketSetPlayerBirthdayRsp(player));

		session.send(new PacketGetPlayerSocialDetailRsp(detail));
		
		BirthdayMailSystem.checkAndSend(player);
    }

    private boolean isValidBirthday(int month, int day) {

        switch (month) {
            case 1:
            case 3:
            case 5:
            case 7:
            case 8:
            case 10:
            case 12:
                return day > 0 & day <= 31;
            case 4:
            case 6:
            case 9:
            case 11:
                return day > 0 && day <= 30;
            case 2:
                return day > 0 & day <= 29;
        }

        return false;
    }
}
