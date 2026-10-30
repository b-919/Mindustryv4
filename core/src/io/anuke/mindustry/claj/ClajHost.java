package io.anuke.mindustry.claj;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.claj.ClajProtocol.CloseReason;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.net.NetConnection;
import io.anuke.mindustry.net.Packet;
import io.anuke.mindustry.net.Packets;
import io.anuke.mindustry.net.Streamable;
import io.anuke.mindustry.game.Version;
import io.anuke.ucore.function.Consumer;
import io.anuke.ucore.util.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Host side of a CLaJ room. */
public class ClajHost implements ClajSocket.Listener{
    private final Object lock = new Object();
    private final IntMap<ClajVirtualConnection> connections = new IntMap<>();
    private final ExecutorService streamExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "CLaJ Stream Sender");
        thread.setDaemon(true);
        return thread;
    });

    private volatile ClajSocket socket;
    private volatile long roomId;
    private volatile ClajLink link;
    private volatile boolean isProtected;
    private volatile short password = ClajProtocol.NO_PASSWORD;

    private String relayHost;
    private int relayPort;

    private volatile boolean creating, closing, finished, disposed;

    private Consumer<ClajLink> created;
    private Consumer<CloseReason> closed;
    private Consumer<Throwable> failed;

    public void createRoom(String host, int port, boolean isProtected, short password,
                           Consumer<ClajLink> created, Consumer<CloseReason> closed,
                           Consumer<Throwable> failed){
        synchronized(lock){
            if(creating || socket != null || roomId != 0){
                if(failed != null) failed.accept(new IllegalStateException("A room is already being created"));
                return;
            }

            this.relayHost = host;
            this.relayPort = port;
            this.isProtected = isProtected;
            this.password = isProtected ? password : ClajProtocol.NO_PASSWORD;
            this.created = created;
            this.closed = closed;
            this.failed = failed;
            this.creating = true;
            this.finished = false;
            this.closing = false;
            this.disposed = false;
        }

        Thread thread = new Thread(this::connect, "CLaJ Host Connect");
        thread.setDaemon(true);
        thread.start();
    }

    private void connect(){
        try{
            ClajSocket connection = new ClajSocket(relayHost, relayPort, this);
            socket = connection;
            connection.start();

            if(disposed || finished){
                connection.close();
                return;
            }

            connection.send(ClajProtocol.T_ROOM_CREATE,
                ClajProtocol.roomCreate(ClajProtocol.PROTOCOL_VERSION, Version.build, isProtected, password));
        }catch(Throwable t){
            failure(t);
        }
    }

    @Override
    public void onFrame(byte type, byte[] data, ClajSocket connection){
        if(finished) return;

        try{
            switch(type){
                case ClajProtocol.T_ROOM_CREATED:{
                    long id = ClajProtocol.input(data).readLong();
                    if(id == 0) throw new IOException("Relay returned an invalid room id");

                    roomId = id;
                    link = new ClajLink(relayHost, relayPort, id);
                    creating = false;

                    final ClajLink createdLink = link;
                    Consumer<ClajLink> callback = created;
                    created = null;
                    if(callback != null) post(() -> callback.accept(createdLink));
                    Log.info("CLaJ room created: {0}", link);
                    break;
                }

                case ClajProtocol.T_ROOM_CLOSED:{
                    CloseReason reason = CloseReason.get(ClajProtocol.input(data).readUnsignedByte());
                    closed(reason);
                    break;
                }

                case ClajProtocol.T_CONNECTION_JOIN:{
                    java.io.DataInputStream in = ClajProtocol.input(data);
                    int conId = in.readInt();
                    String address = in.available() > 0 ? in.readUTF() : "claj";
                    addConnection(conId, address);
                    break;
                }

                case ClajProtocol.T_CONNECTION_CLOSE:{
                    java.io.DataInputStream in = ClajProtocol.input(data);
                    int conId = in.readInt();
                    CloseReason reason = CloseReason.get(in.readUnsignedByte());
                    removeConnection(conId, reason, false);
                    break;
                }

                case ClajProtocol.T_PAYLOAD:{
                    java.io.DataInputStream in = ClajProtocol.input(data);
                    int conId = in.readInt();
                    if(!contains(conId)) return;

                    byte[] payload = ClajProtocol.bytes(in);
                    Packet packet = ClajPayloadCodec.decode(payload);
                    post(() -> {
                        if(!contains(conId)){
                            io.anuke.ucore.util.Pooling.free(packet);
                            return;
                        }
                        Net.handleServerReceived(conId, packet);
                    });
                    break;
                }

                case ClajProtocol.T_MESSAGE:{
                    String text = ClajProtocol.input(data).readUTF();
                    post(() -> Call.sendMessage("[scarlet][[CLaJ]:[] " + text));
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
            if(isRoomCreated()) closed(CloseReason.error);
            else failure(t);
        }
    }

    @Override
    public void onClosed(ClajSocket connection){
        if(closing) return;

        if(roomId == 0 && creating){
            failure(new IOException("CLaJ relay connection closed"));
        }else{
            closed(CloseReason.error);
        }
    }

    private void addConnection(int conId, String address){
        ClajVirtualConnection connection;
        synchronized(lock){
            if(connections.containsKey(conId)) return;
            connection = new ClajVirtualConnection(this, conId, address);
            connections.put(conId, connection);
        }

        post(() -> {
            Packets.Connect connect = new Packets.Connect();
            connect.id = conId;
            connect.addressTCP = connection.address;
            Net.handleServerReceived(conId, connect);
        });
    }

    private void removeConnection(int conId, CloseReason reason, boolean notifyRelay){
        boolean removed;
        synchronized(lock){
            removed = connections.remove(conId) != null;
        }

        if(notifyRelay && socket != null){
            socket.send(ClajProtocol.T_CONNECTION_CLOSE, ClajProtocol.connectionClose(conId, reason));
        }

        if(removed){
            post(() -> {
                Packets.Disconnect disconnect = new Packets.Disconnect();
                disconnect.id = conId;
                Net.handleServerReceived(conId, disconnect);
            });
        }
    }

    private void closed(CloseReason reason){
        closed(reason, false);
    }

    private void closed(CloseReason reason, boolean graceful){
        ClajVirtualConnection[] stale;
        synchronized(lock){
            if(finished) return;
            finished = true;
            creating = false;
            roomId = 0;
            link = null;

            stale = new ClajVirtualConnection[connections.size];
            int index = 0;
            for(IntMap.Entry<ClajVirtualConnection> entry : connections.entries()){
                stale[index++] = entry.value;
            }
            connections.clear();
        }

        ClajSocket connection = socket;
        socket = null;
        if(connection != null){
            if(graceful) connection.closeAfterFlush();
            else connection.close();
        }
        streamExecutor.shutdownNow();

        for(ClajVirtualConnection virtual : stale){
            post(() -> {
                Packets.Disconnect disconnect = new Packets.Disconnect();
                disconnect.id = virtual.id;
                Net.handleServerReceived(virtual.id, disconnect);
            });
        }

        Consumer<CloseReason> callback = closed;
        closed = null;
        if(callback != null) post(() -> callback.accept(reason));
    }

    private void failure(Throwable error){
        synchronized(lock){
            if(finished) return;
            finished = true;
            creating = false;
            roomId = 0;
            link = null;
        }

        ClajSocket connection = socket;
        socket = null;
        if(connection != null) connection.close();
        streamExecutor.shutdownNow();

        Consumer<Throwable> callback = failed;
        failed = null;
        if(callback != null) post(() -> callback.accept(error));
    }

    public void closeRoom(){
        ClajSocket connection = socket;
        if(connection == null) return;

        closing = true;
        connection.clearQueue();
        if(roomId != 0){
            connection.send(ClajProtocol.T_ROOM_CLOSE, ClajProtocol.roomClose());
        }
        closed(CloseReason.closed, true);
        closing = false;
    }

    public void setConfiguration(boolean isProtected, short password){
        this.isProtected = isProtected;
        this.password = isProtected ? password : ClajProtocol.NO_PASSWORD;
        if(isRoomCreated()){
            ClajSocket connection = socket;
            if(connection != null){
                connection.send(ClajProtocol.T_ROOM_CONFIG,
                    ClajProtocol.roomConfig(this.isProtected, this.password));
            }
        }
    }

    public boolean isRoomCreated(){
        return roomId != 0 && link != null && socket != null && !socket.isClosed();
    }

    public boolean isCreating(){
        return creating;
    }

    public ClajLink link(){
        return link;
    }

    public long roomId(){
        return roomId;
    }

    public int getPing(){
        ClajSocket connection = socket;
        return connection == null ? -1 : connection.getPing();
    }

    public int getQueuedFrames(){
        ClajSocket connection = socket;
        return connection == null ? 0 : connection.getQueuedFrames();
    }

    public boolean contains(int conId){
        synchronized(lock){
            return connections.containsKey(conId);
        }
    }

    public boolean hasConnections(){
        synchronized(lock){
            return !connections.isEmpty();
        }
    }

    public Array<NetConnection> getConnections(){
        Array<NetConnection> result = new Array<>();
        synchronized(lock){
            for(IntMap.Entry<ClajVirtualConnection> entry : connections.entries()){
                result.add(entry.value);
            }
        }
        return result;
    }

    public void sendTo(int conId, Object object, boolean reliable){
        if(object == null || object instanceof Streamable) return;
        ClajSocket connection = socket;
        if(connection == null || !isRoomCreated()) return;

        byte[] data;
        try{
            data = ClajPayloadCodec.encode((Packet)object);
        }catch(Throwable t){
            Log.err("Failed to encode a CLaJ packet."); Log.err(t);
            return;
        }

        try{
            connection.send(ClajProtocol.T_PAYLOAD, ClajProtocol.payload(conId, data));
        }catch(Throwable t){
            Log.err("Failed to send a CLaJ packet."); Log.err(t);
            if(isRoomCreated()) closed(CloseReason.error);
            else failure(t);
        }
    }

    public void broadcast(Object object){
        if(object == null || object instanceof Streamable || !hasConnections()) return;
        ClajSocket connection = socket;
        if(connection == null || !isRoomCreated()) return;

        byte[] data;
        try{
            data = ClajPayloadCodec.encode((Packet)object);
        }catch(Throwable t){
            Log.err("Failed to encode a CLaJ broadcast packet."); Log.err(t);
            return;
        }

        try{
            connection.send(ClajProtocol.T_PAYLOAD,
                ClajProtocol.payload(ClajProtocol.CON_BROADCAST, data));
        }catch(Throwable t){
            Log.err("Failed to broadcast a CLaJ packet."); Log.err(t);
            if(isRoomCreated()) closed(CloseReason.error);
            else failure(t);
        }
    }

    public void sendExcept(int exceptId, Object object){
        if(object == null || object instanceof Streamable || !hasConnections()) return;

        ClajSocket connection = socket;
        if(connection == null || !isRoomCreated()) return;

        byte[] data;
        try{
            data = ClajPayloadCodec.encode((Packet)object);
        }catch(Throwable t){
            Log.err("Failed to encode a CLaJ packet."); Log.err(t);
            return;
        }

        ClajVirtualConnection[] current;
        synchronized(lock){
            current = new ClajVirtualConnection[connections.size];
            int index = 0;
            for(IntMap.Entry<ClajVirtualConnection> entry : connections.entries()){
                current[index++] = entry.value;
            }
        }

        for(ClajVirtualConnection virtual : current){
            if(virtual.id != exceptId){
                connection.send(ClajProtocol.T_PAYLOAD,
                    ClajProtocol.payload(virtual.id, data));
            }
        }
    }

    public void sendStream(int conId, Streamable stream){
        if(stream == null || stream.stream == null) return;

        try{
            streamExecutor.execute(() -> {
                try{
                    int total = stream.stream.available();
                    byte type = io.anuke.mindustry.net.Registrator.getID(stream.getClass());
                    if(type < 0) throw new IOException("Stream type is not registered");

                    Packets.StreamBegin begin = new Packets.StreamBegin();
                    begin.total = total;
                    begin.type = type;
                    sendTo(conId, begin, true);

                    byte[] buffer = new byte[512];
                    int read;
                    while((read = stream.stream.read(buffer)) > 0){
                        if(!isRoomCreated()) break;

                        while(getQueuedFrames() > 1024 && !Thread.currentThread().isInterrupted()){
                            Thread.sleep(5);
                        }

                        Packets.StreamChunk chunk = new Packets.StreamChunk();
                        chunk.id = begin.id;
                        chunk.data = Arrays.copyOf(buffer, read);
                        sendTo(conId, chunk, true);
                    }
                }catch(Throwable t){
                    Log.err("Failed to send a CLaJ stream."); Log.err(t);
                    removeConnection(conId, CloseReason.error, true);
                }
            });
        }catch(RejectedExecutionException ignored){
        }
    }

    public void closeConnection(int conId){
        removeConnection(conId, CloseReason.closed, true);
    }

    public void dispose(){
        closing = true;
        disposed = true;
        closed(CloseReason.closed, true);
        closing = false;
    }

    private void post(Runnable runnable){
        Runnable safe = () -> {
            try{
                runnable.run();
            }catch(Throwable t){
                Log.err("CLaJ host task failed."); Log.err(t);
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
