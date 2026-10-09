package emu.grasscutter.game.combat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.utils.lang.Language;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DamageLog {
    private static final int MAX_RECENT = 40;
    private static final long IDLE_MS = 5000;
    private static final int MAX_GADGETS = 4096;
    private static final long OTHER = 0L;

    private final Map<Long, Row> rows = new LinkedHashMap<>();
    private final Deque<Hit> recent = new ArrayDeque<>();
    private final Map<Integer, EntityAvatar> gadgetOwners =
            new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, EntityAvatar> eldest) {
                    return this.size() > MAX_GADGETS;
                }
            };
    private long firstHitMs;
    private long lastHitMs;
    private long activeMs;
    private int revision;

    private static final class Row {
        int uid;
        int avatarId;
        String name;
        String owner;
        int element;
        double dealt;
        double taken;
        float maxHit;
        int hits;
    }

    private record Hit(long time, String name, String target, float damage, int element) {}

    public synchronized void trackGadget(EntityClientGadget gadget) {
        GameEntity owner = gadget.getTrueOwner();
        EntityAvatar avatar =
                owner instanceof EntityAvatar a ? a : this.gadgetOwners.get(gadget.getOwnerEntityId());
        if (avatar != null) this.gadgetOwners.put(gadget.getId(), avatar);
    }

    public synchronized void record(
            int attackerId, GameEntity attacker, GameEntity target, float damage, int element) {
        if (damage <= 0 || Float.isNaN(damage) || Float.isInfinite(damage)) return;

        GameEntity source = attacker == null ? null : attacker.getTrueOwner();
        if (!(source instanceof EntityAvatar)) {
            EntityAvatar cached = this.gadgetOwners.get(attackerId);
            if (cached != null) source = cached;
        }

        if (target instanceof EntityAvatar hurt) {
            this.row(hurt).taken += damage;
            this.revision++;
        } else if (!(source instanceof EntityMonster)) {
            Row row = source instanceof EntityAvatar avatar ? this.row(avatar) : this.other();
            row.dealt += damage;
            row.hits++;
            row.maxHit = Math.max(row.maxHit, damage);
            this.touch();

            this.recent.addLast(
                    new Hit(System.currentTimeMillis(), row.name, nameOf(target), damage, element));
            while (this.recent.size() > MAX_RECENT) this.recent.removeFirst();
        }
    }

    public synchronized void reset() {
        this.rows.clear();
        this.gadgetOwners.clear();
        this.recent.clear();
        this.firstHitMs = 0;
        this.lastHitMs = 0;
        this.activeMs = 0;
        this.revision++;
    }

    private void touch() {
        long now = System.currentTimeMillis();
        if (this.firstHitMs == 0) {
            this.firstHitMs = now;
        } else if (now - this.lastHitMs < IDLE_MS) {
            this.activeMs += now - this.lastHitMs;
        }
        this.lastHitMs = now;
        this.revision++;
    }

    private Row other() {
        return this.rows.computeIfAbsent(
                OTHER,
                guid -> {
                    Row row = new Row();
                    row.name = "Other";
                    row.owner = "";
                    return row;
                });
    }

    private Row row(EntityAvatar entity) {
        var avatar = entity.getAvatar();
        return this.rows.computeIfAbsent(
                avatar.getGuid(),
                guid -> {
                    Row row = new Row();
                    row.uid = entity.getPlayer().getUid();
                    row.owner = entity.getPlayer().getNickname();
                    row.avatarId = avatar.getAvatarId();
                    row.name = nameOf(entity);
                    var depot = avatar.getSkillDepot();
                    ElementType type = depot == null ? null : depot.getElementType();
                    row.element = type == null ? 0 : type.getValue();
                    return row;
                });
    }

    private static String nameOf(GameEntity entity) {
        long hash = 0;
        String fallback = "Unknown";
        if (entity instanceof EntityAvatar avatar) {
            var data = avatar.getAvatar().getAvatarData();
            hash = data.getNameTextMapHash();
            fallback = "Avatar " + data.getId();
        } else if (entity instanceof EntityMonster monster) {
            var describe = monster.getMonsterData().getDescribeData();
            hash =
                    describe != null
                            ? describe.getNameTextMapHash()
                            : monster.getMonsterData().getNameTextMapHash();
            fallback = "Monster " + monster.getMonsterData().getId();
        } else if (entity != null) {
            fallback = entity.getClass().getSimpleName().replace("Entity", "");
        }

        if (hash != 0) {
            try {
                var strings = Language.getTextMapKey(hash);
                String text = strings == null ? null : strings.get("EN");
                if (text != null && !text.isBlank()) return text;
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    public synchronized JsonObject toJson() {
        long now = System.currentTimeMillis();
        double seconds = Math.max(this.activeMs / 1000.0, 1.0);

        JsonObject root = new JsonObject();
        root.addProperty("revision", this.revision);
        root.addProperty("duration", this.activeMs / 1000.0);
        root.addProperty("active", this.lastHitMs != 0 && now - this.lastHitMs < IDLE_MS);

        double total = 0;
        double totalTaken = 0;
        JsonArray rows = new JsonArray();
        for (Row row : this.rows.values()) {
            total += row.dealt;
            totalTaken += row.taken;

            JsonObject o = new JsonObject();
            o.addProperty("uid", row.uid);
            o.addProperty("owner", row.owner);
            o.addProperty("avatarId", row.avatarId);
            o.addProperty("name", row.name);
            o.addProperty("element", row.element);
            o.addProperty("dealt", row.dealt);
            o.addProperty("taken", row.taken);
            o.addProperty("maxHit", row.maxHit);
            o.addProperty("hits", row.hits);
            o.addProperty("dps", row.dealt / seconds);
            rows.add(o);
        }
        root.addProperty("total", total);
        root.addProperty("totalTaken", totalTaken);
        root.addProperty("dps", total / seconds);
        root.add("rows", rows);

        JsonArray hits = new JsonArray();
        for (Hit hit : this.recent) {
            JsonObject o = new JsonObject();
            o.addProperty("ago", (now - hit.time) / 1000.0);
            o.addProperty("name", hit.name);
            o.addProperty("target", hit.target);
            o.addProperty("damage", hit.damage);
            o.addProperty("element", hit.element);
            hits.add(o);
        }
        root.add("hits", hits);
        return root;
    }
}
