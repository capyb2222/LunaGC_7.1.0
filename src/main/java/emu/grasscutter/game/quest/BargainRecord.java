package emu.grasscutter.game.quest;

import dev.morphia.annotations.Entity;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.BargainData;
import emu.grasscutter.net.proto.BargainResultTypeOuterClass.BargainResultType;
import emu.grasscutter.net.proto.BargainSnapshotOuterClass.BargainSnapshot;
import emu.grasscutter.utils.Utils;
import lombok.*;

@Data
@Entity
@Builder
public final class BargainRecord {
    public static BargainRecord resolve(int bargainId) {
        var bargainData = GameData.getBargainDataMap().get(bargainId);
        if (bargainData == null)
            throw new RuntimeException("No bargain data found for " + bargainId + ".");

        return BargainRecord.builder().bargainId(bargainId).build().determineBase(bargainData);
    }

    private int bargainId;
    private int lowestPrice;
    private int expectedPrice;

    private int currentMood;

    private boolean finished;
    private BargainResultType result;

    public BargainRecord determineBase(BargainData data) {
        var price = data.getExpectedValue();
        this.setExpectedPrice(Utils.randomRange(price.get(0), price.get(1)));
        this.setLowestPrice(price.get(0));

        var mood = data.getRandomMood();
        this.setCurrentMood(Utils.randomRange(mood.get(0), mood.get(1)));

        return this;
    }

    public BargainResultType applyOffer(int offer) {
        if (offer < this.getLowestPrice()) {
            this.currentMood -= Utils.randomRange(1, 3);
            return this.result = BargainResultType.BARGAIN_SINGLE_FAIL;
        }

        if (offer > this.getExpectedPrice()) {
            this.setFinished(true);
            return this.result = BargainResultType.BARGAIN_COMPLETE_SUCC;
        }

        var moodAdjustment = (int) Math.floor(this.getCurrentMood() / 100.0);
        var expectedPrice = this.getExpectedPrice() - moodAdjustment;
        if (offer < expectedPrice) {
            this.currentMood -= Utils.randomRange(1, 3);
            return this.result = BargainResultType.BARGAIN_SINGLE_FAIL;
        } else {
            this.setFinished(true);
            return this.result = BargainResultType.BARGAIN_COMPLETE_SUCC;
        }
    }

    public BargainSnapshot toSnapshot() {
        return BargainSnapshot.newBuilder()
                .setBargainId(this.getBargainId())
                .setCurMood(this.getCurrentMood())
                .setBalopachcdb(this.getExpectedPrice())
                .setIocnpjjnhld(this.getLowestPrice())
                .build();
    }
}
