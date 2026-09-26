package emu.grasscutter.game.managers.deforestation;

public class HitTreeRecord {
    private final int unique;
    private short count;
    private long time;

    HitTreeRecord(int unique) {
        this.count = 0;
        this.time = 0;
        this.unique = unique;
    }

    private void resetTime() {
        this.time = System.currentTimeMillis();
    }

    public boolean record() {
        if (this.count < DeforestationManager.RECORD_MAX_TIMES) {
            this.count++;
            resetTime();
            return true;
        }
        boolean isWaiting =
                System.currentTimeMillis() - this.time
                        < DeforestationManager.RECORD_EXPIRED_SECONDS * 1000L;
        if (isWaiting) {
            return false;
        } else {
            this.count = 1;
            resetTime();
            return true;
        }
    }

    public int getUnique() {
        return unique;
    }

    @Override
    public String toString() {
        return "HitTreeRecord{" + "unique=" + unique + ", count=" + count + ", time=" + time + '}';
    }
}
