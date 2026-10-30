package io.anuke.mindustry.claj;

import io.anuke.mindustry.net.Packet;
import io.anuke.mindustry.net.Registrator;
import io.anuke.ucore.function.Supplier;
import io.anuke.ucore.util.Pooling;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

/**
 * Converts regular v4 packets to/from opaque payload bytes.
 *
 * <p>This is intentionally separate from {@link ClajProtocol}: the relay only needs the
 * wire protocol and must not load any Mindustry/libGDX packet classes.</p>
 */
public final class ClajPayloadCodec{
    private static final ThreadLocal<ByteBuffer> writeBuffer = new ThreadLocal<ByteBuffer>(){
        @Override
        protected ByteBuffer initialValue(){
            return ByteBuffer.allocate(1 << 15);
        }
    };

    private ClajPayloadCodec(){}

    public static byte[] encode(Packet packet){
        byte id = Registrator.getID(packet.getClass());
        if(id < 0) throw new IllegalArgumentException("Unregistered packet class: " + packet.getClass());

        ByteBuffer buffer = writeBuffer.get();
        for(int attempt = 0; ; attempt++){
            buffer.clear();
            try{
                packet.write(buffer);
                break;
            }catch(BufferOverflowException e){
                if(attempt >= 4) throw e;
                buffer = ByteBuffer.allocate(buffer.capacity() * 2);
                writeBuffer.set(buffer);
            }
        }

        int length = buffer.position();
        byte[] result = new byte[length + 1];
        result[0] = id;
        System.arraycopy(buffer.array(), 0, result, 1, length);
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Packet decode(byte[] data){
        if(data == null || data.length == 0) throw new IllegalArgumentException("Empty packet data");

        ByteBuffer buffer = ByteBuffer.wrap(data);
        byte id = buffer.get();
        Registrator.ClassEntry entry = Registrator.getByID(id);
        if(entry == null) throw new IllegalArgumentException("Unknown packet id: " + id);

        Packet packet = (Packet)Pooling.obtain((Class)entry.type, (Supplier)entry.constructor);
        try{
            packet.read(buffer);
        }catch(Throwable t){
            Pooling.free(packet);
            throw t;
        }
        return packet;
    }
}
