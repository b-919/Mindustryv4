package io.anuke.mindustry.claj;

import arc.util.io.ByteBufferOutput;
import com.xpdustry.claj.common.packets.ConnectionPayloadPacket;
import io.anuke.mindustry.net.Packet;
import io.anuke.mindustry.net.Packets;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClajPayloadCodecTest{
    @Test
    public void roundTripsConnectPacket(){
        Packets.ConnectPacket packet = new Packets.ConnectPacket();
        packet.versionType = "official";
        packet.name = "claj-test";
        packet.usid = "usid";
        packet.uuid = "AAAAAAAAAAA=";
        packet.mobile = true;
        packet.color = 0xff00ff00;

        Packets.ConnectPacket decoded = (Packets.ConnectPacket)roundTrip(packet);
        assertEquals(packet.versionType, decoded.versionType);
        assertEquals(packet.name, decoded.name);
        assertEquals(packet.usid, decoded.usid);
        assertEquals(packet.uuid, decoded.uuid);
        assertEquals(packet.mobile, decoded.mobile);
        assertEquals(packet.color, decoded.color);
    }

    @Test
    public void roundTripsInvokePacket(){
        Packets.InvokePacket packet = new Packets.InvokePacket();
        packet.type = 3;
        packet.priority = 1;
        packet.writeLength = 3;
        packet.writeBuffer = ByteBuffer.wrap(new byte[]{1, 2, 3});

        Packets.InvokePacket decoded = (Packets.InvokePacket)roundTrip(packet);
        assertEquals(packet.type, decoded.type);
        assertEquals(packet.priority, decoded.priority);
        assertEquals(packet.writeLength, decoded.writeLength);

        byte[] data = new byte[decoded.writeLength];
        decoded.writeBuffer.get(data);
        assertArrayEquals(new byte[]{1, 2, 3}, data);
    }

    @Test
    public void roundTripsChunkRequest(){
        Packets.ChunkRequest packet = new Packets.ChunkRequest();
        packet.keys = new long[]{0x123456789abcdef0L, 0x0fedcba987654321L};

        Packets.ChunkRequest decoded = (Packets.ChunkRequest)roundTrip(packet);
        assertArrayEquals(packet.keys, decoded.keys);
    }

    @Test
    public void roundTripsStreamPackets(){
        Packets.StreamBegin begin = new Packets.StreamBegin();
        begin.total = 128;
        begin.type = 2;

        Packets.StreamBegin decodedBegin = (Packets.StreamBegin)roundTrip(begin);
        assertEquals(begin.id, decodedBegin.id);
        assertEquals(begin.total, decodedBegin.total);
        assertEquals(begin.type, decodedBegin.type);

        Packets.StreamChunk chunk = new Packets.StreamChunk();
        chunk.id = begin.id;
        chunk.data = new byte[]{4, 5, 6};

        Packets.StreamChunk decodedChunk = (Packets.StreamChunk)roundTrip(chunk);
        assertEquals(chunk.id, decodedChunk.id);
        assertArrayEquals(chunk.data, decodedChunk.data);
    }

    @Test
    public void serializerAcceptsBufferPayloads(){
        ConnectionPayloadPacket.Serializer previous = ConnectionPayloadPacket.serializer;
        ConnectionPayloadPacket.serializer = V4ClajProvider.serializer();

        try{
            ConnectionPayloadPacket packet = new ConnectionPayloadPacket();
            packet.isTCP = true;
            packet.object = ByteBuffer.wrap(new byte[]{1, 2, 3});

            ByteBuffer buffer = ByteBuffer.allocate(64);
            packet.write(new ByteBufferOutput(buffer));

            assertEquals(8, buffer.position());
        }finally{
            ConnectionPayloadPacket.serializer = previous;
        }
    }

    private static Packet roundTrip(Packet packet){
        byte[] encoded = ClajPayloadCodec.encode(packet);
        assertTrue(encoded.length > 1);

        Packet decoded = ClajPayloadCodec.decode(encoded);
        assertEquals(packet.getClass(), decoded.getClass());
        return decoded;
    }
}
