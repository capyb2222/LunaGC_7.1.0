package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.*;

import emu.grasscutter.*;
import emu.grasscutter.command.commands.SendMailCommand.MailBuilder;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SetPlayerBornDataReqOuterClass.SetPlayerBornDataReq;
import emu.grasscutter.server.game.GameSession;
import java.util.Arrays;

@Opcodes(PacketOpcodes.SetPlayerBornDataReq)
public class HandlerSetPlayerBornDataReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        SetPlayerBornDataReq req = SetPlayerBornDataReq.parseFrom(payload);

        int avatarId = req.getAvatarId();
        int startingSkillDepot;
        if (avatarId == GameConstants.MAIN_CHARACTER_MALE) {
            startingSkillDepot = 504;
        } else if (avatarId == GameConstants.MAIN_CHARACTER_FEMALE) {
            startingSkillDepot = 704;
        } else {
            return;
        }

        if (!GameData.getAvatarDataMap().containsKey(avatarId)) {
            Grasscutter.getLogger()
                    .error("No avatar data found! Please check your ExcelBinOutput folder.");
            session.close();
            return;
        }

        Player player = session.getPlayer();
        player.setNickname(req.getNickName());

        if (player.getAvatars().getAvatarCount() == 0) {
            Avatar mainCharacter = new Avatar(avatarId);

            if (!GAME_OPTIONS.questing.enabled) {
                mainCharacter.setSkillDepotData(
                        GameData.getAvatarSkillDepotDataMap().get(startingSkillDepot));
            }

            player.addAvatar(mainCharacter, false);
            player.setMainCharacterId(avatarId);
            player.setHeadImage(avatarId);
            player
                    .getTeamManager()
                    .getCurrentSinglePlayerTeamInfo()
                    .getAvatars()
                    .add(mainCharacter.getAvatarId());
            player.save();
        } else {
            return;
        }

        session.getPlayer().onLogin();

        int rspCmdId = GAME_OPTIONS.newAccountIntro.setPlayerBornDataRsp;
        Grasscutter.getLogger()
                .info("[intro] character creation finished: {} picked avatar {} (rsp cmdId={}).",
                        req.getNickName(), avatarId, rspCmdId > 0 ? rspCmdId : "unsent");
        if (rspCmdId > 0) session.send(new BasePacket(rspCmdId));
        else session.send(new BasePacket(PacketOpcodes.SetPlayerBornDataRsp));

        var welcomeMail = GAME_INFO.joinOptions.welcomeMail;
        MailBuilder mailBuilder = new MailBuilder(player.getUid(), new Mail());
        mailBuilder.mail.mailContent.title = welcomeMail.title.replace("{version}", GameConstants.VERSION);
        mailBuilder.mail.mailContent.sender = welcomeMail.sender;
        mailBuilder.mail.mailContent.content =
                welcomeMail.content.replace("{version}", GameConstants.VERSION)
                        + "\n<type=\"browser\" text=\"GitHub\" href=\"https://github.com/Grasscutters/Grasscutter\"/>";
        mailBuilder.mail.itemList.addAll(Arrays.asList(welcomeMail.items));
        mailBuilder.mail.importance = 1;
        player.sendMail(mailBuilder.mail);
    }
}
