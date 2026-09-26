package emu.grasscutter.server.packet.send;

import com.google.protobuf.ByteString;
import emu.grasscutter.GameConstants;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetPlayerTokenRspOuterClass.GetPlayerTokenRsp;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.utils.Crypto;

public class PacketGetPlayerTokenRsp extends BasePacket {

    public PacketGetPlayerTokenRsp(GameSession session, int keyId) {
        super(PacketOpcodes.GetPlayerTokenRsp, true);
        this.setUseDispatchKey(true);
        this.setData(build(session, 0, "", 0, null, null, keyId));
    }

    public PacketGetPlayerTokenRsp(GameSession session, int retcode, String msg, int blackEndTime) {
        super(PacketOpcodes.GetPlayerTokenRsp, true);
        this.setUseDispatchKey(true);
        this.setData(build(session, retcode, msg, blackEndTime, null, null, 0));
    }

    public PacketGetPlayerTokenRsp(
            GameSession session, String encryptedSeed, String encryptedSeedSign, int keyId) {
        super(PacketOpcodes.GetPlayerTokenRsp, true);
        this.setUseDispatchKey(true);
        this.setData(build(session, 0, "", 0, encryptedSeed, encryptedSeedSign, keyId));
    }

    private static GetPlayerTokenRsp build(
            GameSession session,
            int retcode,
            String msg,
            int blackEndTime,
            String serverRandKey,
            String sign,
            int keyId) {
        var p = GetPlayerTokenRsp.newBuilder();

        if (retcode != 0) p.setRetcode(retcode);
        if (msg != null && !msg.isEmpty()) p.setMsg(msg);
        if (blackEndTime != 0) p.setBlackUidEndTime(blackEndTime);

        var account = session.getAccount();
        if (account != null) {
            if (account.getId() != null) p.setAccountUid(account.getId());
            if (account.getToken() != null) p.setToken(account.getToken());
        }

        var player = session.getPlayer();
        if (player != null) {
            p.setUid(player.getUid());
            p.setIsProficientPlayer(player.getNickname() != null && !player.getNickname().isEmpty());
        }

        p.setAccountType(1)
                .setPlatformType(3)
                .setRegPlatform(3)
                .setChannelId(1)
                .setCountryCode("US")
                .setClientVersionRandomKey("c25-314dd05b0b5f")
                .setClientIpStr(session.getAddress().getAddress().getHostAddress())
                .setAuthAppid("csc")
                .setClientVersion("OSRELWin" + GameConstants.VERSION);

        if (serverRandKey != null && !serverRandKey.isEmpty()) {
            p.setKeyId(keyId).setServerRandKey(serverRandKey);
            if (sign != null && !sign.isEmpty()) p.setSign(sign);
        } else if (retcode == 0) {
            p.setSecretKeySeed(Crypto.ENCRYPT_SEED)
                    .setSecurityCmdBuffer(ByteString.copyFrom(Crypto.ENCRYPT_SEED_BUFFER));
        }

        return p.build();
    }
}
