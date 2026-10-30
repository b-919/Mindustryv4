package io.anuke.mindustry.claj;

import com.badlogic.gdx.Gdx;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.claj.ClajProtocol.RejectReason;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.net.Packet;
import io.anuke.mindustry.net.Packets;
import io.anuke.mindustry.game.Version;
import io.anuke.ucore.function.Consumer;
import io.anuke.ucore.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Client side of a CLaJ connection. */
public class ClajJoin implements ClajSocket.Listener{
    private static final int HANDSHAKE_TIMEOUT = 5000;

    private final ClajLink link;
    private final short password;
    private final Runnable success;
    private final Consumer<RejectReason> reject;
    private final Consumer<Throwable> failed;
    private final CountDownLatch handshake = new CountDownLatch(1);
    private final CountDownLatch joinedLatch = new CountDownLatch(1);

    private volatile ClajSocket socket;
    private volatile boolean accepted;
    private volatile boolean joinAccepted;
    private volatile RejectReason denied;
    private volatile Throwable error;
    private volatile boolean joined;
    private volatile boolean closed;

    public ClajJoin(ClajLink link, short password, Runnable success,
                    Consumer<RejectReason> reject, Consumer<Throwable> failed){
        this.link = link;
        this.password = password;
        this.success = success;
        this.reject = reject;
        this.failed = failed;
    }

    public void start(){
        Thread thread = new Thread(this::connect, "CLaJ Join Connect");
        thread.setDaemon(true);
        thread.start();
    }

    private void connect(){
        try{
            ClajSocket connection = new ClajSocket(link.host, link.port, this);
            socket = connection;
            connection.start();

            if(closed){
                connection.close();
                return;
            }

            boolean withPassword = password != ClajProtocol.NO_PASSWORD;
            connection.send(ClajProtocol.T_ROOM_JOIN_REQUEST,
                ClajProtocol.roomJoinRequest(link.roomId, withPassword, password,
                    ClajProtocol.PROTOCOL_VERSION, Version.build));

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

            connection.send(ClajProtocol.T_ROOM_JOIN,
                ClajProtocol.roomJoin(link.roomId, withPassword, password));

            if(!joinedLatch.await(1500, TimeUnit.MILLISECONDS)){
                // Older relays do not acknowledge the actual join, so assume it succeeded.
                joinAccepted = true;
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

            if(!joinAccepted){
                fail(new RuntimeException("CLaJ room join was not accepted"));
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
    public void onFrame(byte type, byte[] data, ClajSocket connection){
        if(closed) return;

        try{
            switch(type){
                case ClajProtocol.T_ROOM_JOIN_ACCEPTED:{
                    long roomId = ClajProtocol.input(data).readLong();
                    if(roomId != link.roomId){
                        error = new RuntimeException("Relay accepted the wrong room");
                        handshake.countDown();
                    }else{
                        accepted = true;
                        handshake.countDown();
                    }
                    break;
                }
                case ClajProtocol.T_ROOM_JOIN_DENIED:{
                    java.io.DataInputStream in = ClajProtocol.input(data);
                    long roomId = in.readLong();
                    RejectReason reason = RejectReason.get(in.readUnsignedByte());
                    if(roomId != link.roomId){
                        error = new RuntimeException("Relay denied the wrong room");
                    }else if(joined || joinAccepted){
                        fail(new RuntimeException("Relay denied the room after accepting it"));
                    }else{
                        denied = reason;
                    }
                    handshake.countDown();
                    joinedLatch.countDown();
                    break;
                }
                case ClajProtocol.T_ROOM_JOINED:{
                    long roomId = ClajProtocol.input(data).readLong();
                    if(roomId != link.roomId){
                        error = new RuntimeException("Relay joined the wrong room");
                    }else{
                        joinAccepted = true;
                    }
                    joinedLatch.countDown();
                    break;
                }
                case ClajProtocol.T_ROOM_CLOSED:
                    if(joined){
                        disconnected();
                    }else{
                        error = new RuntimeException("Room closed by the relay");
                        handshake.countDown();
                        joinedLatch.countDown();
                    }
                    break;
                case ClajProtocol.T_PAYLOAD:{
                    if(!joined || closed) return;
                    java.io.DataInputStream in = ClajProtocol.input(data);
                    in.readInt(); // connection id is only meaningful for the host
                    byte[] payload = ClajProtocol.bytes(in);
                    Packet packet = ClajPayloadCodec.decode(payload);
                    post(() -> {
                        if(joined && !closed){
                            Net.handleClientReceived(packet);
                        }else{
                            io.anuke.ucore.util.Pooling.free(packet);
                        }
                    });
                    break;
                }
                case ClajProtocol.T_MESSAGE:{
                    String text = ClajProtocol.input(data).readUTF();
                    post(() -> {
                        if(Vars.ui != null && Vars.ui.chatfrag != null){
                            Vars.ui.chatfrag.addMessage("[scarlet][[CLaJ]:[] " + text, null);
                        }
                    });
                    break;
                }
                case ClajProtocol.T_POPUP:{
                    String text = ClajProtocol.input(data).readUTF();
                    post(() -> {
                        if(Vars.ui != null) Vars.ui.showInfo(text);
                    });
                    break;
                }
                default:
                    break;
            }
        }catch(Throwable t){
            fail(t);
        }
    }

    @Override
    public void onClosed(ClajSocket connection){
        if(closed) return;

        if(!joined){
            if(handshake.getCount() > 0){
                error = new RuntimeException("CLaJ relay connection closed");
                handshake.countDown();
            }
            joinedLatch.countDown();
            return;
        }

        disconnected();
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

    public void send(Packet packet){
        ClajSocket connection = socket;
        if(joined && connection != null && !connection.isClosed()){
            try{
                connection.send(ClajProtocol.T_PAYLOAD,
                    ClajProtocol.payload(ClajProtocol.CON_BROADCAST, ClajPayloadCodec.encode(packet)));
            }catch(Throwable t){
                Log.err("Failed to send a CLaJ packet."); Log.err(t);
            }
        }
    }

    /** Called when the user disconnects, or when the provider is disposed. */
    public void close(){
        closed = true;
        joined = false;
        handshake.countDown();
        joinedLatch.countDown();
        ClajSocket connection = socket;
        if(connection != null) connection.close();
    }

    public boolean isJoined(){
        return joined && !closed;
    }

    public int getPing(){
        ClajSocket connection = socket;
        return connection == null ? -1 : connection.getPing();
    }

    private void reject(RejectReason reason){
        if(closed) return;
        closed = true;
        joined = false;
        handshake.countDown();
        joinedLatch.countDown();
        closeSocket();

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
        closeSocket();

        post(() -> {
            try{
                if(failed != null) failed.accept(throwable);
            }finally{
                resetConnection();
            }
        });
    }

    private void closeSocket(){
        ClajSocket connection = socket;
        if(connection != null) connection.close();
    }

    private void resetConnection(){
        try{
            if(Vars.netClient != null) Vars.netClient.setQuiet();
            if(Net.active()) Net.disconnect();
        }catch(Throwable t){
            Log.err("Failed to reset the CLaJ connection."); Log.err(t);
        }
    }

    private void post(Runnable runnable){
        Runnable safe = () -> {
            try{
                runnable.run();
            }catch(Throwable t){
                Log.err("CLaJ join task failed."); Log.err(t);
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
}
