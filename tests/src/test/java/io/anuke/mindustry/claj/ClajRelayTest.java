package io.anuke.mindustry.claj;

import org.junit.jupiter.api.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ClajRelayTest{
    @Test
    public void linkRoundTrip(){
        long id = 0x123456789ABCDEFL;
        ClajLink link = new ClajLink("relay.example", 7000, id);

        assertEquals("ASNFZ4mrze8", ClajLink.encodeRoomId(id));
        assertEquals(id, ClajLink.decodeRoomId("ASNFZ4mrze8="));
        assertEquals("claj://relay.example:7000/" + link.encodedRoomId, link.toString());

        ClajLink parsed = ClajLink.fromString(link.toString());
        assertEquals(link.host, parsed.host);
        assertEquals(link.port, parsed.port);
        assertEquals(link.roomId, parsed.roomId);

        ClajLink shortForm = ClajLink.fromString("relay.example:7000/" + link.encodedRoomId);
        assertEquals(link, shortForm);

        ClajLink negative = new ClajLink("relay.example", 7000, -0x123456789L);
        assertEquals(negative.roomId, ClajLink.fromString(negative.toString()).roomId);

        ClajLink ipv6 = new ClajLink("::1", 7000, id);
        assertEquals("claj://[::1]:7000/" + ipv6.encodedRoomId, ipv6.toString());
        assertEquals(ipv6, ClajLink.fromString(ipv6.toString()));

        assertThrows(IllegalArgumentException.class, () -> ClajLink.fromString("relay.example:7000"));
        assertThrows(IllegalArgumentException.class, () -> ClajLink.fromString("claj://relay.example:0/abc"));
    }

    @Test
    public void relayForwardsPayloadsAndClosesRooms() throws Exception{
        ClajRelayServer relay = new ClajRelayServer(0);
        relay.start();

        TestClient host = new TestClient("127.0.0.1", relay.getPort());
        TestClient client = new TestClient("127.0.0.1", relay.getPort());

        try{
            host.send(ClajProtocol.T_ROOM_CREATE,
                ClajProtocol.roomCreate(ClajProtocol.PROTOCOL_VERSION, 1, false, ClajProtocol.NO_PASSWORD));

            ClajProtocol.Frame created = host.next();
            assertEquals(ClajProtocol.T_ROOM_CREATED, created.type);
            long roomId = ClajProtocol.input(created.data).readLong();
            assertTrue(roomId != 0);

            client.send(ClajProtocol.T_ROOM_JOIN_REQUEST,
                ClajProtocol.roomJoinRequest(roomId, false, ClajProtocol.NO_PASSWORD,
                    ClajProtocol.PROTOCOL_VERSION, 1));

            ClajProtocol.Frame accepted = client.next();
            assertEquals(ClajProtocol.T_ROOM_JOIN_ACCEPTED, accepted.type);

            client.send(ClajProtocol.T_ROOM_JOIN,
                ClajProtocol.roomJoin(roomId, false, ClajProtocol.NO_PASSWORD));

            ClajProtocol.Frame joinedAck = client.next();
            assertEquals(ClajProtocol.T_ROOM_JOINED, joinedAck.type);

            ClajProtocol.Frame joined = host.next();
            assertEquals(ClajProtocol.T_CONNECTION_JOIN, joined.type);
            int conId = ClajProtocol.input(joined.data).readInt();
            assertTrue(conId > 0);

            byte[] upstream = new byte[]{1, 2, 3, 4};
            client.send(ClajProtocol.T_PAYLOAD,
                ClajProtocol.payload(ClajProtocol.CON_BROADCAST, upstream));

            ClajProtocol.Frame received = host.next();
            assertEquals(ClajProtocol.T_PAYLOAD, received.type);
            java.io.DataInputStream input = ClajProtocol.input(received.data);
            assertEquals(conId, input.readInt());
            assertArrayEquals(upstream, ClajProtocol.bytes(input));

            byte[] downstream = new byte[]{5, 6, 7};
            host.send(ClajProtocol.T_PAYLOAD, ClajProtocol.payload(conId, downstream));

            ClajProtocol.Frame delivered = client.next();
            assertEquals(ClajProtocol.T_PAYLOAD, delivered.type);
            java.io.DataInputStream deliveredInput = ClajProtocol.input(delivered.data);
            deliveredInput.readInt();
            assertArrayEquals(downstream, ClajProtocol.bytes(deliveredInput));

            host.send(ClajProtocol.T_ROOM_CLOSE, ClajProtocol.roomClose());

            ClajProtocol.Frame closed = client.next();
            assertEquals(ClajProtocol.T_ROOM_CLOSED, closed.type);
        }finally{
            host.close();
            client.close();
            relay.stop();
        }
    }

    @Test
    public void relayRejectsWrongPassword() throws Exception{
        ClajRelayServer relay = new ClajRelayServer(0);
        relay.start();

        TestClient host = new TestClient("127.0.0.1", relay.getPort());
        TestClient client = new TestClient("127.0.0.1", relay.getPort());

        try{
            host.send(ClajProtocol.T_ROOM_CREATE,
                ClajProtocol.roomCreate(ClajProtocol.PROTOCOL_VERSION, 1, true, (short)1234));
            long roomId = ClajProtocol.input(host.next().data).readLong();

            client.send(ClajProtocol.T_ROOM_JOIN_REQUEST,
                ClajProtocol.roomJoinRequest(roomId, true, (short)9999,
                    ClajProtocol.PROTOCOL_VERSION, 1));

            ClajProtocol.Frame denied = client.next();
            assertEquals(ClajProtocol.T_ROOM_JOIN_DENIED, denied.type);

            java.io.DataInputStream in = ClajProtocol.input(denied.data);
            assertEquals(roomId, in.readLong());
            assertEquals(ClajProtocol.RejectReason.invalidPassword.ordinal(), in.readUnsignedByte());
        }finally{
            host.close();
            client.close();
            relay.stop();
        }
    }

    private static class TestClient implements ClajSocket.Listener{
        private final LinkedBlockingQueue<ClajProtocol.Frame> frames = new LinkedBlockingQueue<>();
        private final ClajSocket socket;

        TestClient(String host, int port) throws Exception{
            socket = new ClajSocket(host, port, this);
            socket.start();
        }

        @Override
        public void onFrame(byte type, byte[] data, ClajSocket socket){
            frames.add(new ClajProtocol.Frame(type, data));
        }

        ClajProtocol.Frame next() throws InterruptedException{
            ClajProtocol.Frame frame = frames.poll(5000, TimeUnit.MILLISECONDS);
            assertNotNull(frame, "Timed out waiting for a relay frame");
            return frame;
        }

        void send(byte type, byte[] data){
            socket.send(type, data);
        }

        void close(){
            socket.close();
        }
    }
}
