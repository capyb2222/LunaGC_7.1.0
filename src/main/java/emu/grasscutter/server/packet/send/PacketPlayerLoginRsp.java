package emu.grasscutter.server.packet.send;

import static emu.grasscutter.config.Configuration.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerLoginRspOuterClass.PlayerLoginRsp;
import emu.grasscutter.net.proto.QueryCurrRegionHttpRspOuterClass;
import emu.grasscutter.net.proto.RegionInfoOuterClass.RegionInfo;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.http.dispatch.RegionHandler;
import emu.grasscutter.utils.Crypto;
import java.util.Objects;

public class PacketPlayerLoginRsp extends BasePacket {

    private static QueryCurrRegionHttpRspOuterClass.QueryCurrRegionHttpRsp regionCache;

    public PacketPlayerLoginRsp(GameSession session) {
        super(PacketOpcodes.PlayerLoginRsp, 1);

        this.setUseDispatchKey(true);

        RegionInfo info;

        if (Grasscutter.getRunMode() == ServerRunMode.GAME_ONLY) {
            if (regionCache == null) {
                try {
                    RegionInfo serverRegion =
                            RegionInfo.newBuilder()
                                    .setGateserverIp(lr(GAME_INFO.accessAddress, GAME_INFO.bindAddress))
                                    .setGateserverPort(lr(GAME_INFO.accessPort, GAME_INFO.bindPort))
                                    .build();

                    regionCache =
                            QueryCurrRegionHttpRspOuterClass.QueryCurrRegionHttpRsp.newBuilder()
                                    .setRegionInfo(serverRegion)
                                    .setClientSecretKey(ByteString.copyFrom(Crypto.DISPATCH_SEED))
                                    .build();
                } catch (Exception e) {
                    Grasscutter.getLogger().error("Error while initializing region cache!", e);
                }
            }

            info = regionCache.getRegionInfo();
        } else {
            info = Objects.requireNonNull(RegionHandler.getCurrentRegion()).getRegionInfo();
        }

        PlayerLoginRsp p =
                PlayerLoginRsp.newBuilder()
                        .setResVersionConfig(info.getResVersionConfig())
                        .build();

        this.setData(p.toByteArray());
    }
}
