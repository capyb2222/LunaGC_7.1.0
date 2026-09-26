package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.QuickUseWidgetRspOuterClass.QuickUseWidgetRsp;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.QuickUseWidgetReq)
public class HandlerQuickUseWidgetReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        QuickUseWidgetRsp.Builder proto = QuickUseWidgetRsp.newBuilder();
        Player pl = session.getPlayer();
        synchronized (pl) {
            int materialId = pl.getWidgetId();
            Inventory inventory = session.getPlayer().getInventory();
            InventoryTab inventoryTab = inventory.getInventoryTab(ItemType.ITEM_MATERIAL);
            GameItem item = inventoryTab.getItemById(materialId);
            int remain = item.getCount();
            if (remain > 0) {
                remain--;
                if (remain <= 0) {
                    proto.setRetcode(1);
                } else {
                    proto.setRetcode(0);
                }
                proto.setMaterialId(materialId);
                inventory.removeItem(item, 1);
                BasePacket rsp = new BasePacket(PacketOpcodes.QuickUseWidgetRsp);
                rsp.setData(proto);
                session.send(rsp);
                Grasscutter.getLogger().warn("class has no effects in the game, feel free to implement it");
            }
        }
    }
}
