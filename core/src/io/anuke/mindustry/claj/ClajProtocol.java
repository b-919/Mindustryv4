package io.anuke.mindustry.claj;

/**
 * Shared CLaJ constants and relay reason enums.
 *
 * <p>The wire protocol itself is implemented by the official CLaJ common sources in
 * {@code com.xpdustry.claj.common}; the room payload uses {@link ClajPayloadCodec}.</p>
 */
public final class ClajProtocol{
    /** Fallback port used when a link omits the relay port. */
    public static final int DEFAULT_PORT = 50000;
    /** Prefix used by the official protocol for broadcast payloads. */
    public static final int CON_BROADCAST = 0;
    /** Password value used when a room has no password. */
    public static final short NO_PASSWORD = -1;

    private ClajProtocol(){}

    public enum CloseReason{
        /** A connection error occurred, or an unknown error happened on the relay. */
        error,
        /** The room was closed normally. */
        closed,
        /** The relay is shutting down. */
        serverClosed,
        /** The relay has no capacity left. */
        serverFull,
        /** One of the peers is using an incompatible CLaJ/v4 version. */
        outdated,
        /** The host/client implementation does not match. */
        incompatible,
        /** The relay closed the room because of packet spam. */
        spam,
        /** The room was closed because nobody joined for a long time. */
        afk;

        public static final CloseReason[] all = values();

        public static CloseReason get(int index){
            return index < 0 || index >= all.length ? error : all[index];
        }
    }

    public enum RejectReason{
        error,
        serverFull,
        serverClosing,
        roomNotFound,
        roomFull,
        passwordRequired,
        invalidPassword,
        incompatible;

        public static final RejectReason[] all = values();

        public static RejectReason get(int index){
            return index < 0 || index >= all.length ? error : all[index];
        }
    }
}
