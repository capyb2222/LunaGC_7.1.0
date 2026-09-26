package emu.grasscutter.utils.objects;

import lombok.*;

@SuppressWarnings("FieldMayBeFinal")
public interface HandbookBody {
    @Builder
    @Getter
    class Response {
        private int status;
        private String message;
    }

    enum Action {
        GRANT_AVATAR,
        GIVE_ITEM,
        TELEPORT_TO,
        SPAWN_ENTITY
    }

    @Getter
    class GrantAvatar {
        private String player;
        private String playerToken;
        private String avatar;

        private int level = 90;
        private int constellations = 6;
        private int talentLevels = 10;
    }

    @Getter
    class GiveItem {
        private String player;
        private String playerToken;
        private String item;

        private long amount = 1;
    }

    @Getter
    class TeleportTo {
        private String player;
        private String playerToken;
        private String scene;
    }

    @Getter
    class SpawnEntity {
        private String player;
        private String playerToken;
        private String entity;

        private long amount = 1;
        private int level = 1;
    }
}
