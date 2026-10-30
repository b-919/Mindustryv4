package io.anuke.mindustry.claj;

import arc.net.Client;
import arc.net.DcReason;
import arc.net.NetListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.xpdustry.claj.api.ClajClientSerializer;
import com.xpdustry.claj.common.packets.ClajMessagePacket;
import com.xpdustry.claj.common.packets.ClajPopupPacket;
import com.xpdustry.claj.common.packets.ClajTextMessagePacket;
import com.xpdustry.claj.common.packets.ConnectionClosedPacket;
import com.xpdustry.claj.common.packets.ConnectionJoinPacket;
import com.xpdustry.claj.common.packets.ConnectionPayloadPacket;
import com.xpdustry.claj.common.packets.RoomClosedPacket;
import com.xpdustry.claj.common.packets.RoomClosureRequestPacket;
import com.xpdustry.claj.common.packets.RoomConfigPacket;
import com.xpdustry.claj.common.packets.RoomCreationRequestPacket;
import com.xpdustry.claj.common.packets.RoomLinkPacket;
import com.xpdustry.claj.common.packets.RoomStateRequestPacket;
import com.xpdustry.claj.common.util.AddressUtil;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.claj.ClajProtocol.CloseReason;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.net.NetConnection;
import io.anuke.mindustry.net.Packet;
import io.anuke.mindustry.net.Packets;
import io.anuke.mindustry.net.Registrator;
import io.anuke.mindustry.net.Streamable;
import io.anuke.ucore.function.Consumer;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.Pooling;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Host side of a room hosted on an official CLaJ relay (Arc Net transport). */
public class OfficialRelayHost implements ClajHost{
    private final Object lock = new Object();
    private final IntMap<OfficialVirtualConnection> connections = new IntMap<>();
    private final ExecutorService streamExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "CLaJ Official Stream Sender");
        thread.setDaemon(true);
        return thread;
    });

    private volatile Client client;
    private volatile long roomId;
    private volatile ClajLink link;
    private volatile String relayHost;
    private volatile int relayPort;
    private volatile int majorVersion = OfficialClaj.DEFAULT_MAJOR_VERSION;
    private volatile boolean isProtected;
    private volatile short password = ClajProtocol.NO_PASSWORD;

    private volatile boolean creating, closing, finished;

    private Consumer<ClajLink> created;
    private Consumer<CloseReason> closed;
    private Consumer<Throwable> failed;

    @Override
    public void createRoom(String host, int port, boolean isProtected, short password,
                           Consumer<ClajLink> created, Consumer<CloseReason> closed,
                           Consumer<Throwable> failed){
        synchronized(lock){
            if(creating || client != null || roomId != 0){
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
            this.majorVersion = OfficialClaj.DEFAULT_MAJOR_VERSION;
        }

        try{
            OfficialClaj.init();
        }catch(Throwable t){
            failure(t);
            return;
        }

        Thread thread = new Thread(this::connect, "CLaJ Official Host Connect");
        thread.setDaemon(true);
        thread.start();
    }

    private void connect(){
        try{
            majorVersion = OfficialClaj.relayMajorVersion(relayHost, relayPort);

            Client connection = new Client(131072, 32768, new ClajClientSerializer());
            client = connection;
            connection.addListener(new Listener());
            connection.start();

            if(finished){
                connection.close();
                return;
            }

            connection.connect(5000, relayHost, relayPort, relayPort);
        }catch(Throwable t){
            failure(t);
        }
    }

    private class Listener implements NetListener{
        @Override
        public void connected(arc.net.Connection connection){
            if(finished) return;

            RoomCreationRequestPacket packet = new RoomCreationRequestPacket();
            packet.version = majorVersion;
            packet.type = OfficialClaj.TYPE;
            connection.sendTCP(packet);
        }

        @Override
        public void disconnected(arc.net.Connection connection, DcReason reason){
            if(finished || closing) return;

            if(roomId == 0 && creating){
                failure(new IOException("Official CLaJ relay connection closed"));
            }else{
                closed(CloseReason.error);
            }
        }

        @Override
        public void received(arc.net.Connection connection, Object object){
            if(finished) return;

            try{
                if(object instanceof RoomLinkPacket){
                    onRoomCreated((RoomLinkPacket)object, connection);
                }else if(object instanceof RoomClosedPacket){
                    RoomClosedPacket packet = (RoomClosedPacket)object;
                    closed(OfficialClaj.toCloseReason(packet.reason));
                }else if(object instanceof ConnectionJoinPacket){
                    ConnectionJoinPacket packet = (ConnectionJoinPacket)object;
                    addConnection(packet.conID, packet.addressHash);
                }else if(object instanceof ConnectionClosedPacket){
                    ConnectionClosedPacket packet = (ConnectionClosedPacket)object;
                    removeConnection(packet.conID, CloseReason.closed, false);
                }else if(object instanceof ConnectionPayloadPacket){
                    ConnectionPayloadPacket packet = (ConnectionPayloadPacket)object;
                    // CLaJ reuses packet instances per network thread; copy the fields now before posting.
                    final int conId = packet.conID;
                    final Packet payload = packet.object instanceof Packet ? (Packet)packet.object : null;

                    if(payload == null || !contains(conId)){
                        if(payload != null) Pooling.free(payload);
                        return;
                    }

                    post(() -> {
                        if(!contains(conId)){
                            Pooling.free(payload);
                            return;
                        }
                        Net.handleServerReceived(conId, payload);
                    });
                }else if(object instanceof ClajTextMessagePacket){
                    String text = ((ClajTextMessagePacket)object).message;
                    post(() -> Call.sendMessage("[scarlet][[CLaJ]:[] " + text));
                }else if(object instanceof ClajPopupPacket){
                    String text = ((ClajPopupPacket)object).message;
                    post(() -> {
                        if(Vars.ui != null) Vars.ui.showInfo(text);
                    });
                }else if(object instanceof ClajMessagePacket){
                    String message = ((ClajMessagePacket)object).message.name();
                    post(() -> Call.sendMessage("[scarlet][[CLaJ]:[] " + message));
                }else if(object instanceof RoomStateRequestPacket){
                    // State browsing is not advertised (requestState=false).
                }
            }catch(Throwable t){
                Log.err("Failed to handle an official CLaJ packet.");
                Log.err(t);
                if(isRoomCreated()) closed(CloseReason.error);
                else failure(t);
            }
        }
    }

    private void onRoomCreated(RoomLinkPacket packet, arc.net.Connection connection){
        if(packet.roomId == 0) throw new IllegalArgumentException("Relay returned an invalid room id");

        roomId = packet.roomId;
        link = new ClajLink(relayHost, relayPort, roomId);
        creating = false;

        RoomConfigPacket config = new RoomConfigPacket();
        config.isPublic = false;
        config.isProtected = isProtected;
        config.password = password;
        config.requestState = false;
        config.maxClients = 0;
        connection.sendTCP(config);

        final ClajLink createdLink = link;
        Consumer<ClajLink> callback = created;
        created = null;
        if(callback != null) post(() -> callback.accept(createdLink));
    }

    private void addConnection(int conId, long addressHash){
        OfficialVirtualConnection connection;
        synchronized(lock){
            if(connections.containsKey(conId)) return;
            connection = new OfficialVirtualConnection(conId, addressHash);
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

        if(notifyRelay && client != null){
            try{
                ConnectionClosedPacket packet = new ConnectionClosedPacket();
                packet.conID = conId;
                packet.reason = DcReason.closed;
                client.sendTCP(packet);
            }catch(Throwable ignored){
            }
        }

        if(removed){
            post(() -> {
                Packets.Disconnect disconnect = new Packets.Disconnect();
                disconnect.id = conId;
                Net.handleServerReceived(conId, disconnect);
            });
        }
    }

    @Override
    public void closeRoom(){
        Client connection = client;
        if(connection == null) return;

        closing = true;
        if(roomId != 0 && connection.isConnected()){
            try{
                connection.sendTCP(RoomClosureRequestPacket.instance);
            }catch(Throwable ignored){
            }
        }
        closed(CloseReason.closed);
        closing = false;
    }

    @Override
    public void setConfiguration(boolean isProtected, short password){
        this.isProtected = isProtected;
        this.password = isProtected ? password : ClajProtocol.NO_PASSWORD;

        if(isRoomCreated() && client != null){
            RoomConfigPacket config = new RoomConfigPacket();
            config.isPublic = false;
            config.isProtected = this.isProtected;
            config.password = this.password;
            config.requestState = false;
            config.maxClients = 0;
            client.sendTCP(config);
        }
    }

    private void closed(CloseReason reason){
        OfficialVirtualConnection[] stale;
        synchronized(lock){
            if(finished) return;
            finished = true;
            creating = false;
            roomId = 0;
            link = null;

            stale = new OfficialVirtualConnection[connections.size];
            int index = 0;
            for(IntMap.Entry<OfficialVirtualConnection> entry : connections.entries()){
                stale[index++] = entry.value;
            }
            connections.clear();
        }

        Client connection = client;
        client = null;
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
        streamExecutor.shutdownNow();

        for(OfficialVirtualConnection virtual : stale){
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

        Client connection = client;
        client = null;
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
        streamExecutor.shutdownNow();

        Consumer<Throwable> callback = failed;
        failed = null;
        if(callback != null) post(() -> callback.accept(error));
    }

    @Override
    public boolean isRoomCreated(){
        Client connection = client;
        return roomId != 0 && link != null && connection != null && connection.isConnected() && !finished;
    }

    @Override
    public boolean isCreating(){
        return creating;
    }

    @Override
    public ClajLink link(){
        return link;
    }

    @Override
    public long roomId(){
        return roomId;
    }

    @Override
    public int getPing(){
        Client connection = client;
        return connection == null ? -1 : connection.getReturnTripTime();
    }

    @Override
    public int getQueuedFrames(){
        Client connection = client;
        return connection == null ? 0 : connection.getTcpWriteBufferSize() / 512;
    }

    @Override
    public boolean contains(int conId){
        synchronized(lock){
            return connections.containsKey(conId);
        }
    }

    @Override
    public boolean hasConnections(){
        synchronized(lock){
            return !connections.isEmpty();
        }
    }

    @Override
    public Array<NetConnection> getConnections(){
        Array<NetConnection> result = new Array<>();
        synchronized(lock){
            for(IntMap.Entry<OfficialVirtualConnection> entry : connections.entries()){
                result.add(entry.value);
            }
        }
        return result;
    }

    @Override
    public void sendTo(int conId, Object object, boolean reliable){
        if(object == null || object instanceof Streamable) return;
        Client connection = client;
        if(connection == null || !isRoomCreated() || !(object instanceof Packet)) return;

        try{
            ConnectionPayloadPacket packet = new ConnectionPayloadPacket();
            packet.conID = conId;
            packet.isTCP = true;
            packet.object = object;
            connection.sendTCP(packet);
        }catch(Throwable t){
            Log.err("Failed to send an official CLaJ packet.");
            Log.err(t);
            if(isRoomCreated()) closed(CloseReason.error);
            else failure(t);
        }
    }

    @Override
    public void broadcast(Object object){
        if(object == null || object instanceof Streamable || !hasConnections()) return;
        Client connection = client;
        if(connection == null || !isRoomCreated() || !(object instanceof Packet)) return;

        try{
            ConnectionPayloadPacket packet = new ConnectionPayloadPacket();
            packet.conID = ClajProtocol.CON_BROADCAST;
            packet.isTCP = true;
            packet.object = object;
            connection.sendTCP(packet);
        }catch(Throwable t){
            Log.err("Failed to broadcast an official CLaJ packet.");
            Log.err(t);
            if(isRoomCreated()) closed(CloseReason.error);
            else failure(t);
        }
    }

    @Override
    public void sendExcept(int exceptId, Object object){
        if(object == null || object instanceof Streamable || !hasConnections()) return;

        OfficialVirtualConnection[] current;
        synchronized(lock){
            current = new OfficialVirtualConnection[connections.size];
            int index = 0;
            for(IntMap.Entry<OfficialVirtualConnection> entry : connections.entries()){
                current[index++] = entry.value;
            }
        }

        for(OfficialVirtualConnection virtual : current){
            if(virtual.id != exceptId) sendTo(virtual.id, object, true);
        }
    }

    @Override
    public void sendStream(int conId, Streamable stream){
        if(stream == null || stream.stream == null) return;

        try{
            streamExecutor.execute(() -> {
                try{
                    int total = stream.stream.available();
                    byte type = Registrator.getID(stream.getClass());
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
                    Log.err("Failed to send an official CLaJ stream.");
                    Log.err(t);
                    removeConnection(conId, CloseReason.error, true);
                }
            });
        }catch(RejectedExecutionException ignored){
        }
    }

    @Override
    public void closeConnection(int conId){
        removeConnection(conId, CloseReason.closed, true);
    }

    @Override
    public void dispose(){
        closing = true;
        closed(CloseReason.closed);
        closing = false;
    }

    private void post(Runnable runnable){
        Runnable safe = () -> {
            try{
                runnable.run();
            }catch(Throwable t){
                Log.err("CLaJ official host task failed.");
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

    private class OfficialVirtualConnection extends NetConnection{
        OfficialVirtualConnection(int id, long addressHash){
            super(id, address(addressHash, id));
        }

        @Override
        public boolean isConnected(){
            return contains(id);
        }

        @Override
        public void send(Object object, Net.SendMode mode){
            OfficialRelayHost.this.sendTo(id, object, mode == Net.SendMode.tcp);
        }

        @Override
        public void close(){
            OfficialRelayHost.this.closeConnection(id);
        }
    }

    private static String address(long hash, int id){
        try{
            InetAddress address = AddressUtil.generate(hash);
            if(address != null) return address.getHostAddress();
        }catch(Throwable ignored){
        }
        return "claj-" + Integer.toHexString(id);
    }
}
