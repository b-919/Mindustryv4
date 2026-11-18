package io.anuke.mindustry.claj;

import arc.net.Client;
import arc.net.DcReason;
import arc.net.NetListener;
import com.badlogic.gdx.Gdx;
import com.xpdustry.claj.api.ClajClientSerializer;
import com.xpdustry.claj.common.ClajNet;
import com.xpdustry.claj.common.packets.ClajMessagePacket;
import com.xpdustry.claj.common.packets.ClajPopupPacket;
import com.xpdustry.claj.common.packets.ClajTextMessagePacket;
import com.xpdustry.claj.common.packets.RoomClosedPacket;
import com.xpdustry.claj.common.packets.RoomJoinDeniedPacket;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.net.Packet;
import io.anuke.mindustry.net.Packets;
import io.anuke.ucore.function.Consumer;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.Pooling;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Join side of a room hosted on an official CLaJ relay (Arc Net transport). */
public class OfficialRelayJoin implements ClajJoinConnection, NetListener{
    private static final int HANDSHAKE_TIMEOUT = 5000;

    private final String host;
    private final int port;
    private final ByteBuffer joinPacket;
    private final Runnable success;
    private final Consumer<Throwable> failed;

    private volatile Client client;
    private volatile boolean joined;
    private volatile boolean closed;

    public OfficialRelayJoin(String host, int port, ByteBuffer joinPacket,
                             Runnable success, Consumer<Throwable> failed){
        this.host = host;
        this.port = port;
        this.joinPacket = joinPacket;
        this.success = success;
        this.failed = failed;
    }

    public void start(){
        Thread thread = new Thread(this::connect, "CLaJ Official Join Connect");
        thread.setDaemon(true);
        thread.start();
    }

    private void connect(){
        try{
            Client connection = new Client(32768, 32768, new JoinSerializer());
            client = connection;
            connection.addListener(this);
            connection.start();

            if(closed){
                connection.close();
                return;
            }

            connection.connect(HANDSHAKE_TIMEOUT, host, port, port);
        }catch(Throwable t){
            fail(t);
        }
    }

    @Override
    public void connected(arc.net.Connection connection){
        if(closed) return;

        try{
            connection.sendTCP(joinPacket.duplicate());
            joined = true;

            post(() -> {
                if(closed) return;

                Packets.Connect connect = new Packets.Connect();
                connect.id = 0;
                connect.addressTCP = host;

                try{
                    Net.handleClientReceived(connect);
                }catch(Throwable t){
                    fail(t);
                    return;
                }

                if(success != null) success.run();
            });
        }catch(Throwable t){
            fail(t);
        }
    }

    @Override
    public void disconnected(arc.net.Connection connection, DcReason reason){
        if(closed) return;

        if(joined){
            disconnected();
        }else{
            fail(new IOException("CLaJ relay connection closed"));
        }
    }

    @Override
    public void received(arc.net.Connection connection, Object object){
        if(closed) return;

        try{
            if(object instanceof RoomClosedPacket){
                if(joined) disconnected();
                else fail(new IOException("Room closed by the relay"));
            }else if(object instanceof RoomJoinDeniedPacket){
                fail(new IOException("Relay denied the room"));
            }else if(object instanceof Packet){
                if(!joined){
                    Pooling.free(object);
                    return;
                }

                Packet packet = (Packet)object;
                post(() -> {
                    if(joined && !closed){
                        Net.handleClientReceived(packet);
                    }else{
                        Pooling.free(packet);
                    }
                });
            }else if(object instanceof ClajTextMessagePacket){
                String text = ((ClajTextMessagePacket)object).message;
                post(() -> {
                    if(Vars.ui != null && Vars.ui.chatfrag != null){
                        Vars.ui.chatfrag.addMessage("[scarlet][[CLaJ]:[] " + text, null);
                    }
                });
            }else if(object instanceof ClajPopupPacket){
                String text = ((ClajPopupPacket)object).message;
                post(() -> {
                    if(Vars.ui != null) Vars.ui.showInfo(text);
                });
            }else if(object instanceof ClajMessagePacket){
                String message = ((ClajMessagePacket)object).message.name();
                post(() -> {
                    if(Vars.ui != null && Vars.ui.chatfrag != null){
                        Vars.ui.chatfrag.addMessage("[scarlet][[CLaJ]:[] " + message, null);
                    }
                });
            }
        }catch(Throwable t){
            fail(t);
        }
    }

    @Override
    public void idle(arc.net.Connection connection){
    }

    @Override
    public boolean isJoined(){
        return joined && !closed;
    }

    @Override
    public void send(Packet packet){
        Client connection = client;
        if(joined && connection != null && connection.isConnected()){
            try{
                connection.sendTCP(packet);
            }catch(Throwable t){
                Log.err("Failed to send an official CLaJ packet.");
                Log.err(t);
            }
        }
    }

    @Override
    public void close(){
        closed = true;
        joined = false;
        closeClient();
    }

    @Override
    public int getPing(){
        Client connection = client;
        return connection == null ? -1 : connection.getReturnTripTime();
    }

    private void disconnected(){
        if(closed) return;
        closed = true;
        joined = false;
        closeClient();

        post(() -> {
            if(Net.client()) Net.handleClientReceived(new Packets.Disconnect());
        });
    }

    private void fail(Throwable throwable){
        if(closed) return;
        closed = true;
        joined = false;
        closeClient();

        post(() -> Net.showError(throwable));
    }

    private void closeClient(){
        Client connection = client;
        if(connection != null){
            try{
                connection.close();
            }catch(Throwable ignored){
            }
            try{
                connection.stop();
            }catch(Throwable ignored){
            }
        }
    }

    private void post(Runnable runnable){
        Runnable safe = () -> {
            try{
                runnable.run();
            }catch(Throwable t){
                Log.err("CLaJ official join task failed.");
                Log.err(t);
            }
        };

        try{
            if(Gdx.app != null){
                Gdx.app.postRunnable(safe);
            }else{
                safe.run();
            }
        }catch(Throwable ignored){
        }
    }

    /** Serializer that reads/writes raw v4 packets after the CLaJ control handshake. */
    private static class JoinSerializer extends ClajClientSerializer{
        @Override
        public Object read(ByteBuffer buffer){
            if(!buffer.hasRemaining()) return null;

            int pos = buffer.position();
            byte id = buffer.get();
            if(id == ClajNet.id || id == ClajNet.oldId || id == ClajNet.frameworkId){
                buffer.position(pos);
                return super.read(buffer);
            }

            byte[] raw = new byte[buffer.remaining() + 1];
            raw[0] = id;
            buffer.get(raw, 1, raw.length - 1);
            return ClajPayloadCodec.decode(raw);
        }

        @Override
        public void write(ByteBuffer buffer, Object object){
            if(object instanceof Packet){
                buffer.put(ClajPayloadCodec.encode((Packet)object));
            }else{
                super.write(buffer, object);
            }
        }
    }
}
