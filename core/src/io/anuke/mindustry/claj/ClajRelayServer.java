package io.anuke.mindustry.claj;

import io.anuke.mindustry.claj.ClajProtocol.CloseReason;
import io.anuke.mindustry.claj.ClajProtocol.Frame;
import io.anuke.mindustry.claj.ClajProtocol.RejectReason;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standalone CLaJ relay.
 *
 * <p>Hosts open a room on the relay and clients join it by room id. The relay
 * forwards opaque payloads between the host and the clients, so neither side
 * needs to expose a port.</p>
 */
public class ClajRelayServer{
    public static final int MAX_ROOMS = 4096;
    public static final int MAX_CLIENTS_PER_ROOM = 32;

    private final int port;
    private final Map<Integer, Client> clients = new ConcurrentHashMap<>();
    private final Map<Long, Room> rooms = new ConcurrentHashMap<>();
    private final AtomicInteger nextClientId = new AtomicInteger();
    private final AtomicInteger nextConId = new AtomicInteger(ClajProtocol.CON_ID_START);

    private volatile boolean running;
    private ServerSocket server;
    private Thread acceptThread;

    public ClajRelayServer(int port){
        this.port = port;
    }

    public void start() throws IOException{
        if(running) return;
        server = new ServerSocket(port);
        running = true;
        acceptThread = new Thread(this::acceptLoop, "CLaJ Relay Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        System.out.println("CLaJ relay listening on port " + port + ".");
    }

    public void stop(){
        running = false;
        try{
            if(server != null) server.close();
        }catch(IOException ignored){
        }
        for(Client client : new ArrayList<>(clients.values())) client.close();
        clients.clear();

        for(Room room : new ArrayList<>(rooms.values())) closeRoom(room, CloseReason.serverClosed);
        rooms.clear();
    }

    public boolean isRunning(){
        return running;
    }

    public int getPort(){
        return server == null ? port : server.getLocalPort();
    }

    public int getRoomCount(){
        return rooms.size();
    }

    public int getClientCount(){
        int count = 0;
        for(Room room : rooms.values()) count += room.clients.size();
        return count;
    }

    private void acceptLoop(){
        while(running){
            try{
                Socket socket = server.accept();
                try{
                    Client client = new Client(socket, nextClientId.getAndIncrement());
                    clients.put(client.id, client);
                    client.socket.start();
                }catch(Throwable t){
                    try{
                        socket.close();
                    }catch(IOException ignored){
                    }
                    if(running){
                        System.err.println("CLaJ relay failed to set up a connection.");
                        t.printStackTrace();
                    }
                }
            }catch(IOException e){
                if(running){
                    System.err.println("CLaJ relay failed to accept a connection.");
                    e.printStackTrace();
                }
            }
        }
    }

    private void onCreate(Client client, byte[] data) throws IOException{
        DataInputStream in = ClajProtocol.input(data);
        int version = in.readInt();
        int build = in.readInt();
        String implementation = in.readUTF();
        boolean isProtected = in.readBoolean();
        short password = in.readShort();

        if(version != ClajProtocol.PROTOCOL_VERSION || !ClajProtocol.IMPLEMENTATION.equals(implementation)){
            client.send(ClajProtocol.T_ROOM_CLOSED, ClajProtocol.roomClosed(CloseReason.outdated));
            client.closeAfterFlush();
            return;
        }

        if(client.host || client.room != null){
            client.send(ClajProtocol.T_ROOM_CLOSED, ClajProtocol.roomClosed(CloseReason.error));
            client.closeAfterFlush();
            return;
        }

        if(rooms.size() >= MAX_ROOMS){
            client.send(ClajProtocol.T_ROOM_CLOSED, ClajProtocol.roomClosed(CloseReason.serverFull));
            client.closeAfterFlush();
            return;
        }

        long roomId;
        Room room = null;
        do{
            roomId = ThreadLocalRandom.current().nextLong();
            if(roomId == 0) continue;
            room = new Room(roomId, client);
            room.isProtected = isProtected;
            room.password = isProtected ? password : ClajProtocol.NO_PASSWORD;
            room.gameBuild = build;
        }while(roomId == 0 || rooms.putIfAbsent(roomId, room) != null);

        client.host = true;
        client.room = room;

        if(client.socket.isClosed()){
            client.host = false;
            client.room = null;
            closeRoom(room, CloseReason.serverClosed);
            return;
        }

        client.send(ClajProtocol.T_ROOM_CREATED, ClajProtocol.roomCreated(roomId));
        System.out.println("CLaJ room " + client.sid() + " created by " + client.socket.address()
            + " (game build " + build + ").");
    }

    private void onConfig(Client client, byte[] data) throws IOException{
        if(!client.host || client.room == null) return;
        DataInputStream in = ClajProtocol.input(data);
        boolean isProtected = in.readBoolean();
        short password = in.readShort();
        client.room.isProtected = isProtected;
        client.room.password = password;
    }

    private void onRoomClose(Client client){
        if(!client.host || client.room == null) return;
        closeRoom(client.room, CloseReason.closed);
    }

    private void onJoinRequest(Client client, byte[] data) throws IOException{
        DataInputStream in = ClajProtocol.input(data);
        long roomId = in.readLong();
        boolean withPassword = in.readBoolean();
        short password = in.readShort();
        int version = in.readInt();
        int build = in.readInt();
        String implementation = in.readUTF();

        if(client.host){
            client.send(ClajProtocol.T_ROOM_JOIN_DENIED,
                ClajProtocol.roomJoinDenied(roomId, RejectReason.error));
            return;
        }

        RejectReason reason = validateJoin(roomId, withPassword, password, version, implementation, build);
        if(reason == null){
            client.requestedRoom = roomId;
            client.requestedBuild = build;
            client.send(ClajProtocol.T_ROOM_JOIN_ACCEPTED, ClajProtocol.roomJoinAccepted(roomId));
        }else{
            client.send(ClajProtocol.T_ROOM_JOIN_DENIED, ClajProtocol.roomJoinDenied(roomId, reason));
        }
    }

    private void onJoin(Client client, byte[] data) throws IOException{
        DataInputStream in = ClajProtocol.input(data);
        long roomId = in.readLong();
        boolean withPassword = in.readBoolean();
        short password = in.readShort();

        if(client.host){
            client.send(ClajProtocol.T_ROOM_JOIN_DENIED,
                ClajProtocol.roomJoinDenied(roomId, RejectReason.error));
            return;
        }

        Room room = rooms.get(roomId);
        if(client.requestedRoom != roomId){
            client.send(ClajProtocol.T_ROOM_JOIN_DENIED,
                ClajProtocol.roomJoinDenied(roomId, RejectReason.roomNotFound));
            client.closeAfterFlush();
            return;
        }

        RejectReason reason = validateJoin(roomId, withPassword, password, ClajProtocol.PROTOCOL_VERSION,
            ClajProtocol.IMPLEMENTATION, client.requestedBuild);
        if(reason != null){
            client.send(ClajProtocol.T_ROOM_JOIN_DENIED, ClajProtocol.roomJoinDenied(roomId, reason));
            client.closeAfterFlush();
            return;
        }

        if(room == null || room.closed || rooms.get(roomId) != room){
            client.send(ClajProtocol.T_ROOM_JOIN_DENIED,
                ClajProtocol.roomJoinDenied(roomId, RejectReason.roomNotFound));
            client.closeAfterFlush();
            return;
        }

        if(client.room != null) removeClient(client, true);

        int conId = nextConId.getAndIncrement();
        synchronized(room){
            if(room.closed || rooms.get(roomId) != room){
                client.send(ClajProtocol.T_ROOM_JOIN_DENIED,
                    ClajProtocol.roomJoinDenied(roomId, RejectReason.roomNotFound));
                client.closeAfterFlush();
                return;
            }

            client.room = room;
            client.conId = conId;
            room.clients.put(conId, client);

            room.host.send(ClajProtocol.T_CONNECTION_JOIN,
                ClajProtocol.connectionJoin(conId, client.socket.address()));
            client.send(ClajProtocol.T_ROOM_JOINED, ClajProtocol.roomJoined(roomId));
        }

        System.out.println("CLaJ client " + client.socket.address() + " joined room " + room.sid
            + " as connection " + Integer.toHexString(conId) + ".");
    }

    private RejectReason validateJoin(long roomId, boolean withPassword, short password, int version,
                                      String implementation, int build){
        if(version != ClajProtocol.PROTOCOL_VERSION || !ClajProtocol.IMPLEMENTATION.equals(implementation)){
            return RejectReason.incompatible;
        }

        Room room = rooms.get(roomId);
        if(room == null) return RejectReason.roomNotFound;
        if(room.gameBuild != build) return RejectReason.incompatible;
        if(room.isProtected && !withPassword) return RejectReason.passwordRequired;
        if(room.isProtected && room.password != password) return RejectReason.invalidPassword;
        if(room.clients.size() >= MAX_CLIENTS_PER_ROOM) return RejectReason.roomFull;
        return null;
    }

    private void onPayload(Client client, byte[] data) throws IOException{
        DataInputStream in = ClajProtocol.input(data);
        int conId = in.readInt();
        byte[] payload = ClajProtocol.bytes(in);

        if(client.host){
            Room room = client.room;
            if(room == null) return;

            if(conId == ClajProtocol.CON_BROADCAST){
                for(Client target : room.clients.values()){
                    target.send(ClajProtocol.T_PAYLOAD, ClajProtocol.payload(ClajProtocol.CON_BROADCAST, payload));
                }
            }else{
                Client target = room.clients.get(conId);
                if(target == null){
                    client.send(ClajProtocol.T_CONNECTION_CLOSE,
                        ClajProtocol.connectionClose(conId, CloseReason.error));
                }else{
                    target.send(ClajProtocol.T_PAYLOAD, ClajProtocol.payload(ClajProtocol.CON_BROADCAST, payload));
                }
            }
        }else if(client.room != null && client.conId != 0){
            client.room.host.send(ClajProtocol.T_PAYLOAD, ClajProtocol.payload(client.conId, payload));
        }
    }

    private void onConnectionClose(Client client, byte[] data) throws IOException{
        if(!client.host || client.room == null) return;
        DataInputStream in = ClajProtocol.input(data);
        int conId = in.readInt();
        CloseReason reason = CloseReason.get(in.readUnsignedByte());

        Client target = client.room.clients.remove(conId);
        if(target != null){
            target.room = null;
            target.conId = 0;
            target.clearQueue();
            target.send(ClajProtocol.T_ROOM_CLOSED, ClajProtocol.roomClosed(reason));
            target.closeAfterFlush();
        }
    }

    private void removeClient(Client client, boolean notifyHost){
        Room room = client.room;
        if(room == null) return;

        int conId = client.conId;
        room.clients.remove(conId);
        client.room = null;
        client.conId = 0;

        if(notifyHost && room.host != null && !room.host.socket.isClosed()){
            room.host.send(ClajProtocol.T_CONNECTION_CLOSE,
                ClajProtocol.connectionClose(conId, CloseReason.closed));
        }
    }

    private void closeRoom(Room room, CloseReason reason){
        if(rooms.get(room.id) != room) return;
        rooms.remove(room.id);

        ArrayList<Client> clients;
        Client host;
        synchronized(room){
            if(room.closed) return;
            room.closed = true;

            clients = new ArrayList<>(room.clients.values());
            room.clients.clear();
            host = room.host;
        }

        for(Client client : clients){
            client.room = null;
            client.conId = 0;
            client.clearQueue();
            client.send(ClajProtocol.T_ROOM_CLOSED, ClajProtocol.roomClosed(reason));
            client.closeAfterFlush();
        }

        if(host != null){
            host.room = null;
            host.host = false;
            host.clearQueue();
            host.send(ClajProtocol.T_ROOM_CLOSED, ClajProtocol.roomClosed(reason));
            host.closeAfterFlush();
        }

        System.out.println("CLaJ room " + room.sid + " closed (" + reason + ").");
    }

    private class Client implements ClajSocket.Listener{
        final ClajSocket socket;
        final int id;
        volatile Room room;
        volatile int conId;
        volatile boolean host;
        volatile long requestedRoom;
        volatile int requestedBuild;

        Client(Socket socket, int id){
            this.id = id;
            try{
                this.socket = new ClajSocket(socket, this);
            }catch(IOException e){
                throw new RuntimeException(e);
            }
        }

        String sid(){
            return Integer.toHexString(id);
        }

        @Override
        public void onFrame(byte type, byte[] data, ClajSocket socket){
            try{
                switch(type){
                    case ClajProtocol.T_INFO:
                        send(ClajProtocol.T_INFO, ClajProtocol.info(rooms.size(), getClientCount()));
                        break;
                    case ClajProtocol.T_ROOM_CREATE:
                        onCreate(this, data);
                        break;
                    case ClajProtocol.T_ROOM_CONFIG:
                        onConfig(this, data);
                        break;
                    case ClajProtocol.T_ROOM_CLOSE:
                        onRoomClose(this);
                        break;
                    case ClajProtocol.T_ROOM_JOIN_REQUEST:
                        onJoinRequest(this, data);
                        break;
                    case ClajProtocol.T_ROOM_JOIN:
                        onJoin(this, data);
                        break;
                    case ClajProtocol.T_PAYLOAD:
                        onPayload(this, data);
                        break;
                    case ClajProtocol.T_CONNECTION_CLOSE:
                        onConnectionClose(this, data);
                        break;
                    default:
                        break;
                }
            }catch(Throwable e){
                System.err.println("CLaJ relay failed to handle a packet.");
                e.printStackTrace();
                close();
            }
        }

        @Override
        public void onClosed(ClajSocket socket){
            clients.remove(id);

            if(host && room != null){
                closeRoom(room, CloseReason.serverClosed);
            }else{
                removeClient(this, true);
            }
        }

        void send(byte type, byte[] data){
            socket.send(type, data);
        }

        void close(){
            socket.close();
        }

        void closeAfterFlush(){
            socket.closeAfterFlush();
        }

        void clearQueue(){
            socket.clearQueue();
        }
    }

    private static class Room{
        final long id;
        final String sid;
        final Client host;
        final Map<Integer, Client> clients = new ConcurrentHashMap<>();

        volatile boolean isProtected;
        volatile short password = ClajProtocol.NO_PASSWORD;
        volatile int gameBuild;
        volatile boolean closed;

        Room(long id, Client host){
            this.id = id;
            this.sid = ClajLink.encodeRoomId(id);
            this.host = host;
        }
    }
}
