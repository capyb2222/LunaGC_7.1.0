package emu.grasscutter.game.managers;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.proto.RetcodeOuterClass;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;

public class ResinManager extends BasePlayerManager {
    public static final int MAX_RESIN_BUYING_COUNT = 6;
    public static final int AMOUNT_TO_ADD = 60;
    public static final int[] HCOIN_NUM_TO_BUY_RESIN = new int[] {50, 100, 100, 150, 200, 200};

    public ResinManager(Player player) {
        super(player);
    }

    public synchronized boolean useResin(int amount) {
        if (!GAME_OPTIONS.resinOptions.resinUsage) {
            return true;
        }

        int currentResin = this.player.getProperty(PlayerProperty.PROP_PLAYER_RESIN);

        if (currentResin < amount) {
            return false;
        }

        int newResin = currentResin - amount;
        this.player.setProperty(PlayerProperty.PROP_PLAYER_RESIN, newResin);

        if (this.player.getNextResinRefresh() == 0 && newResin < GAME_OPTIONS.resinOptions.cap) {
            int currentTime = Utils.getCurrentSeconds();
            this.player.setNextResinRefresh(currentTime + GAME_OPTIONS.resinOptions.rechargeTime);
        }

        this.player.sendPacket(new PacketResinChangeNotify(this.player));

        this.player
                .getBattlePassManager()
                .triggerMission(
                        WatcherTriggerType.TRIGGER_COST_MATERIAL, 106, amount);

        return true;
    }

    public synchronized boolean useCondensedResin(int amount) {
        if (!GAME_OPTIONS.resinOptions.resinUsage) return true;
        return this.player.getInventory().payItem(220007, amount);
    }

    public synchronized void addResin(int amount) {
        if (!GAME_OPTIONS.resinOptions.resinUsage) {
            return;
        }

        int currentResin = this.player.getProperty(PlayerProperty.PROP_PLAYER_RESIN);
        int newResin = currentResin + amount;
        this.player.setProperty(PlayerProperty.PROP_PLAYER_RESIN, newResin);

        if (newResin >= GAME_OPTIONS.resinOptions.cap) {
            this.player.setNextResinRefresh(0);
        }

        this.player.sendPacket(new PacketResinChangeNotify(this.player));
    }

    public synchronized void rechargeResin() {
        if (!GAME_OPTIONS.resinOptions.resinUsage) {
            return;
        }

        int currentResin = this.player.getProperty(PlayerProperty.PROP_PLAYER_RESIN);
        int currentTime = Utils.getCurrentSeconds();

        if (this.player.getNextResinRefresh() <= 0) {
            return;
        }

        if (currentTime < this.player.getNextResinRefresh()) {
            return;
        }

        int recharge =
                1
                        + (int)
                                ((currentTime - this.player.getNextResinRefresh())
                                        / GAME_OPTIONS.resinOptions.rechargeTime);
        int newResin = Math.min(GAME_OPTIONS.resinOptions.cap, currentResin + recharge);
        int resinChange = newResin - currentResin;

        this.player.setProperty(PlayerProperty.PROP_PLAYER_RESIN, newResin);

        if (newResin >= GAME_OPTIONS.resinOptions.cap) {
            this.player.setNextResinRefresh(0);
        } else {
            int nextRecharge =
                    this.player.getNextResinRefresh() + resinChange * GAME_OPTIONS.resinOptions.rechargeTime;
            this.player.setNextResinRefresh(nextRecharge);
        }

        this.player.sendPacket(new PacketResinChangeNotify(this.player));
    }

    public synchronized void onPlayerLogin() {
        if (!GAME_OPTIONS.resinOptions.resinUsage) {
            this.player.setProperty(PlayerProperty.PROP_PLAYER_RESIN, GAME_OPTIONS.resinOptions.cap);
            this.player.setNextResinRefresh(0);
        }

        int currentResin = this.player.getProperty(PlayerProperty.PROP_PLAYER_RESIN);
        int currentTime = Utils.getCurrentSeconds();

        if (currentResin < GAME_OPTIONS.resinOptions.cap && this.player.getNextResinRefresh() == 0) {
            this.player.setNextResinRefresh(currentTime + GAME_OPTIONS.resinOptions.rechargeTime);
        }

        this.player.sendPacket(new PacketResinChangeNotify(this.player));
    }

    public int buy() {
        if (this.player.getResinBuyCount() >= MAX_RESIN_BUYING_COUNT) {
            return RetcodeOuterClass.Retcode.RET_RESIN_BOUGHT_COUNT_EXCEEDED_VALUE;
        }

        var res =
                this.player
                        .getInventory()
                        .payItem(201, HCOIN_NUM_TO_BUY_RESIN[this.player.getResinBuyCount()]);
        if (!res) {
            return RetcodeOuterClass.Retcode.RET_HCOIN_NOT_ENOUGH_VALUE;
        }

        this.player.setResinBuyCount(this.player.getResinBuyCount() + 1);
        this.player.setProperty(PlayerProperty.PROP_PLAYER_WAIT_SUB_HCOIN, 0);
        this.addResin(AMOUNT_TO_ADD);
        this.player.sendPacket(
                new PacketItemAddHintNotify(new GameItem(106, AMOUNT_TO_ADD), ActionReason.BuyResin));

        return 0;
    }
}
