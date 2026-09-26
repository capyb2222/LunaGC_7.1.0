package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetShopRspNewOuterClass.GetShopRspNew;
import java.util.*;

public class PacketGetShopRspNew extends BasePacket {
    
    private static final List<Integer> AVAILABLE_SHOPS = Arrays.asList(
        900,
        100000,
        1052,
        101000,
        902,
        102000,
        1001,
        103000, 
        903
    );
    
    public PacketGetShopRspNew(int param) {
        super(PacketOpcodes.GetShopRspNew);

        GetShopRspNew.Builder rsp = GetShopRspNew.newBuilder()
                .setParam(param)
                .setRetcode(0);

        rsp.addAllDGINCLDAKFI(AVAILABLE_SHOPS);

        this.setData(rsp.build());
    }
    
    public PacketGetShopRspNew(int param, List<Integer> availableShops) {
        super(PacketOpcodes.GetShopRspNew);

        GetShopRspNew.Builder rsp = GetShopRspNew.newBuilder()
                .setParam(param)
                .setRetcode(0);

        rsp.addAllDGINCLDAKFI(availableShops);

        this.setData(rsp.build());
    }
}