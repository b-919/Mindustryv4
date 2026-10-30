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
import com.xpdustry.claj.common.packets.RoomJoinAcceptedPacket;
import com.xpdustry.claj.common.packets.RoomJoinDeniedPacket;
import com.xpdustry.claj.common.packets.RoomJoinPacket;
import com.xpdustry.claj.common.packets.RoomJoinRequestPacket;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.claj.ClajProtocol.RejectReason;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.net.Packet;
import io.anuke.mindustry.net.Packets;
import io.anuke.ucore.function.Consumer;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.Pooling;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Join side of a room hosted on an official CLaJ relay (Arc Net transport). */
public class OfficialRelayJoin implements ClajJoinConnection, NetListener{
    private static final int HANDSHAKE_TIMEOUT = 5000;
    private static final int JOIN_GRACE = 1500;

    private final ClajLink link;
    private final short password;
    private final Runnable success;
    private final Consumer<RejectReason> reject;
    private final Consumer<Throwable> failed;
    private final CountDownLatch handshake = new CountDownLatch(1);
    private final CountDownLatch joinedLatch = new CountDownLatch(1);

    private volatile Client client;
    private volatile boolean accepted;
    private volatile RejectReason denied;
    private volatile Throwable error;
    private volatile boolean joined;
    private volatile boolean closed;

    public OfficialRelayJoin(ClajLink link, short password, Runnable success,
                             Consumer<RejectReason> reject, Consumer<Throwable> failed){
        this.link = link;
        this.password = password;
        this.success = success;
        this.reject = reject;
        this.failed = failed;
    }

    public void start(){
        try{
            OfficialClaj.init();
        }catch(Throwable t){
            fail(t);
            return;
        }

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

            connection.connect(HANDSHAKE_TIMEOUT, link.host, link.port, link.port);

            if(!handshake.await(HANDSHAKE_TIMEOUT, TimeUnit.MILLISECONDS)){
                fail(new RuntimeException("CLaJ join timed out"));
                return;
            }

            if(closed) return;

            if(denied != null){
                reject(denied);
                return;
            }

            if(error != null){
                fail(error);
                return;
            }

            if(!accepted){
                fail(new RuntimeException("CLaJ join was not accepted"));
                return;
            }

            RoomJoinPacket join = new RoomJoinPacket();
            join.roomId = link.roomId;
            join.withPassword = password != ClajProtocol.NO_PASSWORD;
            join.password = password;
            join.type = OfficialClaj.TYPE;
            connection.sendTCP(join);

            if(!joinedLatch.await(JOIN_GRACE, TimeUnit.MILLISECONDS)){
                // The official protocol has no acknowledgement for the actual join packet.
                accepted = true;
            }

            if(closed) return;

            if(denied != null){
                reject(denied);
                return;
            }

            if(error != null){
                fail(error);
                return;
            }

            joined = true;

            post(() -> {
                if(closed) return;

                Packets.Connect connect = new Packets.Connect();
                connect.id = 0;
                connect.addressTCP = link.host;

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
    public void connected(arc.net.Connection connection){
        if(closed) return;

        RoomJoinRequestPacket request = new RoomJoinRequestPacket();
        request.roomId = link.roomId;
        request.withPassword = password != ClajProtocol.NO_PASSWORD;
        request.password = password;
        request.type = OfficialClaj.TYPE;
        connection.sendTCP(request);
    }

    @Override
    public void disconnected(arc.net.Connection connection, DcReason reason){
        if(closed) return;

        if(!joined){
            error = new RuntimeException("CLaJ relay connection closed");
            handshake.countDown();
            joinedLatch.countDown();
            return;
        }

        disconnected();
    }

    @Override
    public void received(arc.net.Connection connection, Object object){
        if(closed) return;

        try{
            if(object instanceof RoomJoinAcceptedPacket){
                long roomId = ((RoomJoinAcceptedPacket)object).roomId;
                if(roomId != link.roomId){
                    error = new RuntimeException("Relay accepted the wrong room");
                }else{
                    accepted = true;
                }
                handshake.countDown();
            }else if(object instanceof RoomJoinDeniedPacket){
                RoomJoinDeniedPacket packet = (RoomJoinDeniedPacket)object;
                if(packet.roomId != link.roomId){
                    error = new RuntimeException("Relay denied the wrong room");
                }else if(joined){
                    fail(new RuntimeException("Relay denied the room after accepting it"));
                }else{
                    denied = OfficialClaj.toRejectReason(packet.reason);
                }
                handshake.countDown();
                joinedLatch.countDown();
            }else if(object instanceof RoomClosedPacket){
                if(joined){
                    disconnected();
                }else{
                    error = new RuntimeException("Room closed by the relay");
                    handshake.countDown();
                    joinedLatch.countDown();
                }
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
                // CLaJ reuses packet instances per network thread; read the value before posting.
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
        handshake.countDown();
        joinedLatch.countDown();

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

    @Override
    public int getPing(){
        Client connection = client;
        return connection == null ? -1 : connection.getReturnTripTime();
    }

    private void disconnected(){
        if(closed) return;
        closed = true;
        joined = false;

        post(() -> {
            if(Net.client()){
                Net.handleClientReceived(new Packets.Disconnect());
            }
        });
    }

    private void reject(RejectReason reason){
        if(closed) return;
        closed = true;
        joined = false;
        handshake.countDown();
        joinedLatch.countDown();
        closeClient();

        post(() -> {
            try{
                if(reject != null) reject.accept(reason);
            }finally{
                resetConnection();
            }
        });
    }

    private void fail(Throwable throwable){
        if(closed) return;
        closed = true;
        joined = false;
        handshake.countDown();
        joinedLatch.countDown();
        closeClient();

        post(() -> {
            try{
                if(failed != null) failed.accept(throwable);
            }finally{
                resetConnection();
            }
        });
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

    private void resetConnection(){
        try{
            if(Vars.netClient != null) Vars.netClient.setQuiet();
            if(Net.active()) Net.disconnect();
        }catch(Throwable t){
            Log.err("Failed to reset the official CLaJ connection.");
            Log.err(t);
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
