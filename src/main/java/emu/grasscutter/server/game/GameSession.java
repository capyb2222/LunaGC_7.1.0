package emu.grasscutter.server.game;

import static emu.grasscutter.config.Configuration.*;
import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerDebugMode;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.event.game.SendPacketEvent;
import emu.grasscutter.utils.*;
import io.netty.buffer.*;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import lombok.*;

public class GameSession implements GameSessionManager.KcpChannel {
    private final GameServer server;
    private GameSessionManager.KcpTunnel tunnel;

    @Getter @Setter private Account account;
    @Getter private Player player;

    @Getter private long encryptSeed = Crypto.ENCRYPT_SEED;
    private byte[] encryptKey = Crypto.ENCRYPT_KEY;

    @Setter private boolean useSecretKey;

    private boolean reportedBadMagic;

    private static final java.util.Set<String> missingCmdIdReported =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    @Getter @Setter private SessionState state;

    @Getter private int clientTime;
    @Getter private long lastPingTime;
    private int lastClientSeq = 10;

    public GameSession(GameServer server) {
        this.server = server;
        this.state = SessionState.WAITING_FOR_TOKEN;
        this.lastPingTime = System.currentTimeMillis();

        if (GAME_INFO.useUniquePacketKey) {
            this.encryptKey = new byte[4096];
            this.encryptSeed = Crypto.generateEncryptKeyAndSeed(this.encryptKey);
        }
    }

    public GameServer getServer() {
        return server;
    }

    public InetSocketAddress getAddress() {
        try {
            return tunnel.getAddress();
        } catch (Throwable ignore) {
            return null;
        }
    }

    public boolean useSecretKey() {
        return useSecretKey;
    }

    public String getAccountId() {
        return this.getAccount().getId();
    }

    public synchronized void setPlayer(Player player) {
        this.player = player;
        this.player.setSession(this);
        this.player.setAccount(this.getAccount());
    }

    public boolean isLoggedIn() {
        return this.getPlayer() != null;
    }

    public void updateLastPingTime(int clientTime) {
        this.clientTime = clientTime;
        this.lastPingTime = System.currentTimeMillis();
    }

    public int getNextClientSequence() {
        return ++lastClientSeq;
    }

    public void replayPacket(int opcode, String name) {
        Path filePath = FileUtils.getPluginPath(name);
        File p = filePath.toFile();

        if (!p.exists()) return;

        byte[] packet = FileUtils.read(p);

        BasePacket basePacket = new BasePacket(opcode);
        basePacket.setData(packet);

        send(basePacket);
    }

    private static final java.util.Set<Integer> MUTED_LOG_OPCODES = java.util.Set.of(2497);

    public void logPacket(String sendOrRecv, int opcode, byte[] payload) {
        if (MUTED_LOG_OPCODES.contains(opcode)) return;
        Grasscutter.getLogger()
                .info(sendOrRecv + ": " + PacketOpcodesUtils.getOpcodeName(opcode) + " (" + opcode + ")");
        if (GAME_INFO.isShowPacketPayload) System.out.println(Utils.bytesToHex(payload));
    }

    public void send(BasePacket packet) {

        if (GAME_OPTIONS.disableCutscenes && packet.getOpcode() == PacketOpcodes.CutSceneBeginNotify) {
            Grasscutter.getLogger()
                    .info("Suppressed a CutSceneBeginNotify: game.disableCutscenes is on.");
            return;
        }

        if (packet.getOpcode() <= 0 && FanOut.send(this, packet)) {
            return;
        }

        if (packet.getOpcode() <= 0) {
            if (missingCmdIdReported.add(packet.getClass().getSimpleName())) {
                Grasscutter.getLogger()
                        .warn(
                                "{} has no CmdId in this version, so it is not being sent.",
                                packet.getClass().getSimpleName());
            }
            return;
        }

        if (packet.shouldBuildHeader()) {
            packet.buildHeader(this.getNextClientSequence());
        }

        switch (GAME_INFO.logPackets) {
            case ALL -> {
                if ((!PacketOpcodesUtils.LOOP_PACKETS.contains(packet.getOpcode())
                        || GAME_INFO.isShowLoopPackets)
                        && !PacketOpcodes.BANNED_PACKETS.contains(packet.getOpcode())) {
                    logPacket("SEND", packet.getOpcode(), packet.getData());
                }
            }
            case WHITELIST -> {
                if (SERVER.debugWhitelist.contains(packet.getOpcode())
                        && !PacketOpcodes.BANNED_PACKETS.contains(packet.getOpcode())) {
                    logPacket("SEND", packet.getOpcode(), packet.getData());
                }
            }
            case BLACKLIST -> {
                if (!SERVER.debugBlacklist.contains(packet.getOpcode())
                        && !PacketOpcodes.BANNED_PACKETS.contains(packet.getOpcode())) {
                    logPacket("SEND", packet.getOpcode(), packet.getData());
                }
            }
            default -> {}
        }

        SendPacketEvent event = new SendPacketEvent(this, packet);
        event.call();
        if (!event.isCanceled()) {
            try {
                packet = event.getPacket();
                var bytes = packet.build();
                if (packet.shouldEncrypt) {
                    if (Grasscutter.getConfig().server.game.useXorEncryption) {
                        Crypto.xor(bytes, packet.useDispatchKey() || !useSecretKey() ? Crypto.DISPATCH_KEY : this.encryptKey);
                    }
                }
                tunnel.writeData(bytes);
            } catch (Exception ignored) {
                Grasscutter.getLogger().debug("Unable to send packet to client.");
            }
        }
    }

    @Override
    public void onConnected(GameSessionManager.KcpTunnel tunnel) {
        this.tunnel = tunnel;
        Grasscutter.getLogger().info(translate("messages.game.connect", this.getAddress().toString()));
    }

    private void decryptWithEitherKey(byte[] bytes) {
        var primary = useSecretKey() ? this.encryptKey : Crypto.DISPATCH_KEY;
        Crypto.xor(bytes, primary);
        if (bytes.length >= 2 && readMagic(bytes) == 17767) return;

        var fallback = useSecretKey() ? Crypto.DISPATCH_KEY : this.encryptKey;
        if (fallback == null || fallback == primary) return;

        Crypto.xor(bytes, primary);
        Crypto.xor(bytes, fallback);
        if (bytes.length >= 2 && readMagic(bytes) == 17767) {
            this.setUseSecretKey(!useSecretKey());
            Grasscutter.getLogger()
                    .info(
                            "Client {} is now talking on the {} key - following it.",
                            this.getAddress(),
                            useSecretKey() ? "session" : "dispatch");
            return;
        }
        Crypto.xor(bytes, fallback);
        Crypto.xor(bytes, primary);
    }

    private static int readMagic(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
    }

    @Override
    public void handleReceive(byte[] bytes) {
        if (Grasscutter.getConfig().server.game.useXorEncryption) {
            decryptWithEitherKey(bytes);
        }
        ByteBuf packet = Unpooled.wrappedBuffer(bytes);

        try {
            boolean allDebug = GAME_INFO.logPackets == ServerDebugMode.ALL;
            int prevOpcode = -1, prevHeaderLen = -1, prevPayloadLen = -1, prevStart = -1;
            while (packet.readableBytes() > 0) {
                if (packet.readableBytes() < 12) {
                    return;
                }
                int pktStart = packet.readerIndex();
                int const1 = packet.readShort();
                if (const1 != 17767) {
                    if (!this.reportedBadMagic) {
                        this.reportedBadMagic = true;
                        Grasscutter.getLogger()
                                .warn(
                                        "A {}-byte frame from {} did not decrypt (magic {}, expected 17767) - the {} key does not match this client. First bytes: {}",
                                        bytes.length,
                                        this.getAddress(),
                                        const1,
                                        useSecretKey() ? "session" : "dispatch",
                                        Utils.bytesToHex(
                                                java.util.Arrays.copyOf(bytes, Math.min(bytes.length, 32))));
                    }
                    if (allDebug) {
                        int badOffset = packet.readerIndex() - 2;
                        Grasscutter.getLogger()
                                .error("Bad Data Package Received: got {} ,expect 17767 (bad magic at offset {} of {}-byte frame; prev packet opcode={} headerLen={} payloadLen={} startedAt={})", const1, badOffset, bytes.length, prevOpcode, prevHeaderLen, prevPayloadLen, prevStart);
                        Grasscutter.getLogger()
                                .error("RAW FRAME HEX: {}", Utils.bytesToHex(bytes));
                    }
                    return;
                }
                int opcode = packet.readShort();
                int headerLength = packet.readShort();
                int payloadLength = packet.readInt();
                byte[] header = new byte[headerLength];
                byte[] payload = new byte[payloadLength];

                packet.readBytes(header);
                packet.readBytes(payload);
                int const2 = packet.readShort();
                if (const2 != -30293) {
                    if (allDebug) {
                        Grasscutter.getLogger()
                                .error("Bad Data Package Received: got {} ,expect -30293", const2);
                    }
                    return;
                }

                prevOpcode = opcode;
                prevHeaderLen = headerLength;
                prevPayloadLen = payloadLength;
                prevStart = pktStart;

                switch (GAME_INFO.logPackets) {
                    case ALL -> {
                        if ((!PacketOpcodesUtils.LOOP_PACKETS.contains(opcode) || GAME_INFO.isShowLoopPackets)
                                && !PacketOpcodes.BANNED_PACKETS.contains(opcode)) {
                            logPacket("RECV", opcode, payload);
                        }
                    }
                    case WHITELIST -> {
                        if (SERVER.debugWhitelist.contains(opcode)
                                && !PacketOpcodes.BANNED_PACKETS.contains(opcode)) {
                            logPacket("RECV", opcode, payload);
                        }
                    }
                    case BLACKLIST -> {
                        if (!(SERVER.debugBlacklist.contains(opcode))
                                && !PacketOpcodes.BANNED_PACKETS.contains(opcode)) {
                            logPacket("RECV", opcode, payload);
                        }
                    }
                    default -> {}
                }

                getServer().getPacketHandler().handle(this, opcode, header, payload);
            }
        } catch (Throwable e) {
            Grasscutter.getLogger()
                    .error(
                            "Dropped an inbound packet from {}.",
                            this.getPlayer() != null ? this.getPlayer().getUid() : this.getAddress(),
                            e);
        } finally {

            packet.release();
        }
    }

        @Override
        public void handleClose() {
            setState(SessionState.INACTIVE);

            Grasscutter.getLogger()
                    .info(translate("messages.game.disconnect", this.getAddress().toString()));

            if (this.isLoggedIn()) {
                Player player = getPlayer();

                player.onLogout();
            }
            try {
                send(new BasePacket(PacketOpcodes.ServerDisconnectClientNotify));
            } catch (Throwable ignore) {
                Grasscutter.getLogger().warn("closing {} error", getAddress().getAddress().getHostAddress());
            }
            tunnel = null;
        }

    public void close() {
        tunnel.close();
    }

    public boolean isActive() {
        return getState() == SessionState.ACTIVE;
    }

    public boolean isConnected() {
        return this.tunnel != null;
    }

    public enum SessionState {
        INACTIVE,
        WAITING_FOR_TOKEN,
        WAITING_FOR_LOGIN,
        PICKING_CHARACTER,
        ACTIVE,
        ACCOUNT_BANNED
    }
}
