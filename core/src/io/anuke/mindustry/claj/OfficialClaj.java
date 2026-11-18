package io.anuke.mindustry.claj;

import com.xpdustry.claj.common.packets.ConnectionPayloadPacket;
import com.xpdustry.claj.common.status.ClajType;
import io.anuke.mindustry.net.Packet;

import java.nio.ByteBuffer;

/**
 * Shared state/helpers for the official CLaJ relay transport.
 *
 * <p>Official CLaJ relays use Arc Net and the CLaJ v2 control protocol. The room payload
 * itself remains our v4 packet encoding, so the relay only sees opaque bytes.</p>
 */
public final class OfficialClaj{
    /** Public CLaJ relays validate the implementation type. */
    public static final ClajType TYPE = ClajType.of("MindustryV4");

    private static final ConnectionPayloadPacket.Serializer serializer =
        new ConnectionPayloadPacket.Serializer(){
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
                }else if(packet.object instanceof ByteBuffer){
                    write.buffer.put((ByteBuffer)packet.object);
                }else{
                    throw new IllegalArgumentException("Unsupported CLaJ payload object: " + packet.object);
                }
            }
        };

    private OfficialClaj(){}

    static ConnectionPayloadPacket.Serializer serializer(){
        return serializer;
    }
}
