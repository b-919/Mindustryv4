package io.anuke.mindustry.claj;

import com.xpdustry.claj.common.ClajPackets;
import com.xpdustry.claj.common.packets.ConnectionPayloadPacket;
import com.xpdustry.claj.common.status.ClajType;
import com.xpdustry.claj.common.status.CloseReason;
import com.xpdustry.claj.common.status.RejectReason;
import io.anuke.mindustry.net.Packet;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

/**
 * Shared state/helpers for the official CLaJ relay transport.
 *
 * <p>Official CLaJ relays use Arc Net and the CLaJ v2 control protocol. The room payload
 * itself remains our v4 packet encoding, so the relay only sees opaque bytes.</p>
 */
public final class OfficialClaj{
    /** Public CLaJ relays currently run CLaJ 2.4.x; the relay only checks the major version. */
    public static final int DEFAULT_MAJOR_VERSION = 4;

    public static final ClajType TYPE = ClajType.of("MindustryV4");

    private static boolean initialized;

    private OfficialClaj(){}

    public static synchronized void init(){
        if(initialized) return;
        ClajPackets.init();

        ConnectionPayloadPacket.serializer = new ConnectionPayloadPacket.Serializer(){
            @Override
            public void read(ConnectionPayloadPacket packet, arc.util.io.ByteBufferInput read){
                byte[] raw = new byte[read.buffer.remaining()];
                read.buffer.get(raw);
                packet.object = ClajPayloadCodec.decode(raw);
            }

            @Override
            public void write(ConnectionPayloadPacket packet, arc.util.io.ByteBufferOutput write){
                if(packet.object instanceof Packet){
                    write.buffer.put(ClajPayloadCodec.encode((Packet)packet.object));
                }else if(packet.object instanceof byte[]){
                    write.buffer.put((byte[])packet.object);
                }else{
                    throw new IllegalArgumentException("Unsupported CLaJ payload object: " + packet.object);
                }
            }
        };

        initialized = true;
    }

    /** Queries a relay over UDP discovery. Returns the relay's CLaJ major version, or {@link #DEFAULT_MAJOR_VERSION}. */
    public static int relayMajorVersion(String host, int port){
        try(DatagramSocket socket = new DatagramSocket()){
            socket.setSoTimeout(2000);
            byte[] request = {(byte)-2, 1}; // Arc framework DiscoverHost
            socket.send(new DatagramPacket(request, request.length, InetAddress.getByName(host), port));

            byte[] response = new byte[16];
            DatagramPacket packet = new DatagramPacket(response, response.length);
            socket.receive(packet);

            if(packet.getLength() >= 5 && response[0] == (byte)-4){
                return ByteBuffer.wrap(response, 1, 4).getInt();
            }
        }catch(Throwable ignored){
        }

        return DEFAULT_MAJOR_VERSION;
    }

    public static ClajProtocol.CloseReason toCloseReason(CloseReason reason){
        if(reason == null) return ClajProtocol.CloseReason.error;
        switch(reason){
            case closed: return ClajProtocol.CloseReason.closed;
            case serverClosed: return ClajProtocol.CloseReason.serverClosed;
            case serverFull: return ClajProtocol.CloseReason.serverFull;
            case outdatedClient:
            case outdatedServer:
            case obsoleteClient: return ClajProtocol.CloseReason.outdated;
            case blacklisted: return ClajProtocol.CloseReason.incompatible;
            case afk: return ClajProtocol.CloseReason.afk;
            case spam: return ClajProtocol.CloseReason.spam;
            default: return ClajProtocol.CloseReason.error;
        }
    }

    public static ClajProtocol.RejectReason toRejectReason(RejectReason reason){
        if(reason == null) return ClajProtocol.RejectReason.error;
        switch(reason){
            case serverFull: return ClajProtocol.RejectReason.serverFull;
            case serverClosing: return ClajProtocol.RejectReason.serverClosing;
            case roomNotFound: return ClajProtocol.RejectReason.roomNotFound;
            case roomFull: return ClajProtocol.RejectReason.roomFull;
            case passwordRequired: return ClajProtocol.RejectReason.passwordRequired;
            case invalidPassword: return ClajProtocol.RejectReason.invalidPassword;
            case incompatible: return ClajProtocol.RejectReason.incompatible;
            default: return ClajProtocol.RejectReason.error;
        }
    }
}
