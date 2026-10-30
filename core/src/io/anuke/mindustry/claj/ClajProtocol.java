package io.anuke.mindustry.claj;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

/**
 * CLaJ wire protocol used by the v4 implementation.
 *
 * <p>Every TCP message is a frame:</p>
 * <pre>
 * int length (payload length, including the type byte)
 * byte type
 * byte[] payload
 * </pre>
 *
 * <p>There is no UDP transport. v4's unreliable packets are sent over the same
 * reliable connection; this keeps the relay simple and avoids NAT issues.</p>
 */
public final class ClajProtocol{
    public static final int DEFAULT_PORT = 7000;
    public static final int PROTOCOL_VERSION = 1;
    public static final String IMPLEMENTATION = "MindustryV4";

    public static final int MAX_FRAME = 4 * 1024 * 1024;
    public static final int CON_BROADCAST = 0;
    public static final int CON_ID_START = 0x10000;

    public static final short NO_PASSWORD = -1;

    // Internal control messages.
    public static final byte T_PING = 1;
    public static final byte T_PONG = 2;
    public static final byte T_INFO = 3;

    public static final byte T_ROOM_CREATE = 10;
    public static final byte T_ROOM_CREATED = 11;
    public static final byte T_ROOM_CLOSE = 12;
    public static final byte T_ROOM_CLOSED = 13;
    public static final byte T_ROOM_CONFIG = 14;

    public static final byte T_ROOM_JOIN_REQUEST = 20;
    public static final byte T_ROOM_JOIN_ACCEPTED = 21;
    public static final byte T_ROOM_JOIN_DENIED = 22;
    public static final byte T_ROOM_JOIN = 23;
    public static final byte T_ROOM_JOINED = 24;

    public static final byte T_CONNECTION_JOIN = 30;
    public static final byte T_CONNECTION_CLOSE = 31;
    public static final byte T_PAYLOAD = 32;

    public static final byte T_MESSAGE = 40;
    public static final byte T_POPUP = 41;

    private ClajProtocol(){}

    public static void writeFrame(DataOutputStream out, byte type, byte[] data) throws IOException{
        if(data == null) data = new byte[0];
        out.writeInt(data.length + 1);
        out.writeByte(type);
        out.write(data);
        out.flush();
    }

    public static Frame readFrame(DataInputStream in) throws IOException{
        int length;
        try{
            length = in.readInt();
        }catch(EOFException e){
            throw e;
        }

        if(length < 1 || length > MAX_FRAME){
            throw new IOException("Invalid CLaJ frame length: " + length);
        }

        byte type = in.readByte();
        byte[] data = new byte[length - 1];
        in.readFully(data);
        return new Frame(type, data);
    }

    // Packet encoding lives in ClajPayloadCodec so the relay never loads packet classes.

    // Control packet builders

    public interface Writer{
        void write(DataOutputStream out) throws IOException;
    }

    public static byte[] build(Writer writer){
        try{
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(32);
            DataOutputStream out = new DataOutputStream(bytes);
            writer.write(out);
            out.flush();
            return bytes.toByteArray();
        }catch(IOException e){
            throw new RuntimeException(e);
        }
    }

    public static byte[] roomCreate(int version, int build, boolean isProtected, short password){
        return build(out -> {
            out.writeInt(version);
            out.writeInt(build);
            out.writeUTF(IMPLEMENTATION);
            out.writeBoolean(isProtected);
            out.writeShort(password);
        });
    }

    public static byte[] roomCreated(long roomId){
        return build(out -> out.writeLong(roomId));
    }

    public static byte[] roomClose(){
        return new byte[0];
    }

    public static byte[] roomClosed(CloseReason reason){
        return build(out -> out.writeByte(reason.ordinal()));
    }

    public static byte[] roomConfig(boolean isProtected, short password){
        return build(out -> {
            out.writeBoolean(isProtected);
            out.writeShort(password);
        });
    }

    public static byte[] roomJoinRequest(long roomId, boolean withPassword, short password, int version, int build){
        return build(out -> {
            out.writeLong(roomId);
            out.writeBoolean(withPassword);
            out.writeShort(password);
            out.writeInt(version);
            out.writeInt(build);
            out.writeUTF(IMPLEMENTATION);
        });
    }

    public static byte[] roomJoinAccepted(long roomId){
        return build(out -> out.writeLong(roomId));
    }

    public static byte[] roomJoinDenied(long roomId, RejectReason reason){
        return build(out -> {
            out.writeLong(roomId);
            out.writeByte(reason.ordinal());
        });
    }

    public static byte[] roomJoin(long roomId, boolean withPassword, short password){
        return build(out -> {
            out.writeLong(roomId);
            out.writeBoolean(withPassword);
            out.writeShort(password);
        });
    }

    public static byte[] roomJoined(long roomId){
        return build(out -> out.writeLong(roomId));
    }

    public static byte[] connectionJoin(int conId, String address){
        return build(out -> {
            out.writeInt(conId);
            out.writeUTF(address == null ? "" : address);
        });
    }

    public static byte[] connectionClose(int conId, CloseReason reason){
        return build(out -> {
            out.writeInt(conId);
            out.writeByte(reason.ordinal());
        });
    }

    public static byte[] payload(int conId, byte[] data){
        return build(out -> {
            out.writeInt(conId);
            out.write(data);
        });
    }

    public static byte[] info(int rooms, int clients){
        return build(out -> {
            out.writeInt(PROTOCOL_VERSION);
            out.writeInt(rooms);
            out.writeInt(clients);
        });
    }

    public static byte[] ping(long time){
        return build(out -> out.writeLong(time));
    }

    public static byte[] message(String text){
        return build(out -> out.writeUTF(text == null ? "" : text));
    }

    public static DataInputStream input(byte[] data){
        return new DataInputStream(new ByteArrayInputStream(data));
    }

    public static byte[] bytes(DataInputStream in) throws IOException{
        byte[] data = new byte[in.available()];
        in.readFully(data);
        return data;
    }

    public static final class Frame{
        public final byte type;
        public final byte[] data;

        public Frame(byte type, byte[] data){
            this.type = type;
            this.data = data;
        }
    }

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
