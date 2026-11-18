package io.anuke.mindustry.claj;

import arc.net.Connection;
import arc.net.DcReason;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.xpdustry.claj.api.Claj;
import com.xpdustry.claj.api.ClajLink;
import com.xpdustry.claj.api.ClajProxy;
import com.xpdustry.claj.api.net.VirtualConnection;
import com.xpdustry.claj.common.status.CloseReason;
import com.xpdustry.claj.common.status.RejectReason;
import com.xpdustry.claj.common.util.AddressUtil;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.net.NetConnection;
import io.anuke.mindustry.net.Packets;
import io.anuke.mindustry.net.Registrator;
import io.anuke.mindustry.net.Streamable;
import io.anuke.ucore.function.Consumer;
import io.anuke.ucore.util.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Bridges the official CLaJ API to the v4 Net implementation. */
public final class ClajSupport{
    private static final Object lock = new Object();
    private static final IntMap<V4NetConnection> connections = new IntMap<>();
    private static final Object streamLock = new Object();

    private static boolean initialized;
    private static boolean disposed;
    private static ExecutorService streamExecutor;

    private ClajSupport(){}

    public static synchronized void init(){
        if(initialized) return;
        if(!Claj.initialized()) Claj.init(new V4ClajProvider());
        initialized = true;
    }

    public static void createRoom(String address, boolean isProtected, short password,
                                  Consumer<ClajLink> created, Consumer<CloseReason> closed,
                                  Consumer<Throwable> failed){
        init();

        if(!Net.server()){
            fail(failed, new IllegalStateException("Host a game before creating a CLaJ room"));
            return;
        }

        ClajProtocol.Address relay;
        try{
            relay = ClajProtocol.parseAddress(address);
        }catch(Exception e){
            fail(failed, e);
            return;
        }

        if(hasOpenRoom() || isCreating()){
            fail(failed, new IllegalStateException("A CLaJ room is already open"));
            return;
        }

        ClajProxy proxy = Claj.get().proxies.get(0);
        proxy.setDefaultConfiguration(false, isProtected,
            isProtected ? password : ClajProtocol.NO_PASSWORD, false);

        Claj.get().createRoom(relay.host, relay.port,
            link -> {
                if(created != null) created.accept(link);
            },
            reason -> {
                if(closed != null) closed.accept(reason == null ? CloseReason.closed : reason);
            },
            error -> {
                if(failed != null) failed.accept(error);
            });
    }

    public static void closeRoom(){
        if(Claj.initialized()) Claj.get().closeRooms();
    }

    public static boolean hasOpenRoom(){
        return Claj.initialized() && Claj.get().hasOpenRoom();
    }

    public static boolean isCreating(){
        ClajProxy proxy = proxy();
        return proxy != null && proxy.isConnecting();
    }

    public static ClajLink link(){
        ClajProxy proxy = proxy();
        return proxy != null && proxy.roomCreated() ? proxy.link() : null;
    }

    public static void join(ClajLink link, short password, Runnable success,
                            Consumer<RejectReason> reject, Consumer<Throwable> failed){
        init();

        if(link == null){
            fail(failed, new IllegalArgumentException("Missing CLaJ link"));
            return;
        }

        if(Net.active()){
            fail(failed, new IllegalStateException("Already connected"));
            return;
        }

        Claj.get().joinRoom(link, password, success,
            reason -> {
                if(reject != null) reject.accept(reason);
            },
            error -> {
                if(failed != null) failed.accept(error);
            });
    }

    public static void dispose(){
        if(disposed) return;
        disposed = true;

        if(Claj.initialized()) Claj.get().dispose();

        synchronized(lock){
            connections.clear();
        }

        synchronized(streamLock){
            if(streamExecutor != null){
                streamExecutor.shutdownNow();
                streamExecutor = null;
            }
        }
    }

    static Array<NetConnection> getConnections(){
        Array<NetConnection> result = new Array<>();
        synchronized(lock){
            for(IntMap.Entry<V4NetConnection> entry : connections.entries()){
                result.add(entry.value);
            }
        }
        return result;
    }

    static V4NetConnection getConnection(int id){
        synchronized(lock){
            return connections.get(id);
        }
    }

    static boolean hasConnection(int id){
        synchronized(lock){
            return connections.containsKey(id);
        }
    }

    static void addConnection(Connection connection){
        if(!(connection instanceof VirtualConnection)) return;

        synchronized(lock){
            if(connections.containsKey(connection.getID())) return;
            connections.put(connection.getID(), new V4NetConnection((VirtualConnection)connection));
        }
    }

    static void removeConnection(int id){
        synchronized(lock){
            connections.remove(id);
        }
    }

    static String address(Connection connection){
        String address = AddressUtil.getString(connection);
        return address == null ? "claj-" + connection.getID() : address;
    }

    static void sendTo(int id, Object object, boolean tcp){
        if(object == null || object instanceof Streamable) return;

        V4NetConnection connection = getConnection(id);
        ClajProxy proxy = proxy();
        if(connection == null || proxy == null) return;

        proxy.send(connection.connection, object, tcp);
    }

    static void broadcast(Object object, boolean tcp){
        if(object == null || object instanceof Streamable) return;

        ClajProxy proxy = proxy();
        if(proxy != null && proxy.roomCreated()) proxy.broadcast(object, tcp);
    }

    static void sendExcept(int exceptId, Object object, boolean tcp){
        if(object == null || object instanceof Streamable) return;

        ClajProxy proxy = proxy();
        if(proxy == null || !proxy.roomCreated()) return;

        for(VirtualConnection connection : proxy.getConnections()){
            if(connection.getID() != exceptId) proxy.send(connection, object, tcp);
        }
    }

    static void sendStream(int id, Streamable stream){
        if(stream == null || stream.stream == null) return;

        V4NetConnection connection = getConnection(id);
        ClajProxy proxy = proxy();
        if(connection == null || proxy == null) return;

        try{
            streamExecutor().execute(() -> {
                try{
                    int total = stream.stream.available();
                    byte type = Registrator.getID(stream.getClass());
                    if(type < 0) throw new IOException("Stream type is not registered");

                    Packets.StreamBegin begin = new Packets.StreamBegin();
                    begin.total = total;
                    begin.type = type;
                    sendTo(id, begin, true);

                    byte[] buffer = new byte[512];
                    int read;
                    while((read = stream.stream.read(buffer)) > 0){
                        if(!hasConnection(id)) break;

                        while(proxy.getTcpWriteBufferSize() > 512 * 1024
                            && !Thread.currentThread().isInterrupted()){
                            Thread.sleep(5);
                        }

                        Packets.StreamChunk chunk = new Packets.StreamChunk();
                        chunk.id = begin.id;
                        chunk.data = Arrays.copyOf(buffer, read);
                        sendTo(id, chunk, true);
                    }
                }catch(Throwable t){
                    Log.err("Failed to send a CLaJ stream.");
                    Log.err(t);
                    closeConnection(id);
                }
            });
        }catch(RejectedExecutionException ignored){
        }
    }

    static void closeConnection(int id){
        V4NetConnection connection = getConnection(id);
        ClajProxy proxy = proxy();
        if(connection != null && proxy != null) proxy.close(connection.connection, DcReason.closed);
    }

    static void post(Runnable task){
        try{
            if(Gdx.app != null) Gdx.app.postRunnable(task);
            else task.run();
        }catch(Throwable ignored){
        }
    }

    private static ClajProxy proxy(){
        return Claj.initialized() ? Claj.get().proxies.getOrNull(0) : null;
    }

    private static ExecutorService streamExecutor(){
        synchronized(streamLock){
            if(streamExecutor == null){
                streamExecutor = Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "CLaJ Stream Sender");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            return streamExecutor;
        }
    }

    private static void fail(Consumer<Throwable> failed, Throwable error){
        if(failed != null) failed.accept(error);
    }

    static class V4NetConnection extends NetConnection{
        final VirtualConnection connection;

        V4NetConnection(VirtualConnection connection){
            super(connection.getID(), address(connection));
            this.connection = connection;
        }

        @Override
        public boolean isConnected(){
            return connection.isConnected();
        }

        @Override
        public void send(Object object, Net.SendMode mode){
            ClajSupport.sendTo(id, object, mode == Net.SendMode.tcp);
        }

        @Override
        public void close(){
            ClajSupport.closeConnection(id);
        }
    }
}

