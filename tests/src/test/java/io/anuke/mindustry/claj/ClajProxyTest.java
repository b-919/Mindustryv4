package io.anuke.mindustry.claj;

import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.net.Packets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ClajProxyTest{
    @AfterEach
    void reset(){
        Claj.dispose();
        Net.setClientLoaded(false);
    }

    @Test
    public void hostAndJoinExchangePackets() throws Exception{
        ClajRelayServer relay = new ClajRelayServer(0);
        relay.start();

        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch hostPayload = new CountDownLatch(1);
        CountDownLatch clientConnected = new CountDownLatch(1);
        CountDownLatch clientPayload = new CountDownLatch(1);
        AtomicInteger hostConId = new AtomicInteger();

        Net.handleServer(Packets.Connect.class, (id, packet) -> {
            hostConId.set(id);
            connected.countDown();
        });
        Net.handleServer(Packets.ConnectPacket.class, (id, packet) -> hostPayload.countDown());
        Net.handleClient(Packets.Connect.class, packet -> clientConnected.countDown());
        Net.handleClient(Packets.ConnectPacket.class, packet -> clientPayload.countDown());
        Net.setClientLoaded(true);

        ClajHost host = new ClajHost();
        CountDownLatch created = new CountDownLatch(1);

        try{
            host.createRoom("127.0.0.1", relay.getPort(), false, ClajProtocol.NO_PASSWORD,
                link -> created.countDown(),
                reason -> {},
                error -> fail(error));

            assertTrue(created.await(5, TimeUnit.SECONDS), "Host did not create a room");
            ClajLink link = host.link();
            assertTrue(link != null);

            ClajJoin join = new ClajJoin(link, ClajProtocol.NO_PASSWORD,
                () -> {},
                reason -> fail("Join rejected: " + reason),
                error -> fail(error));
            join.start();

            assertTrue(clientConnected.await(5, TimeUnit.SECONDS), "Join client did not connect");
            assertTrue(connected.await(5, TimeUnit.SECONDS), "Host did not receive the virtual connection");

            host.sendTo(hostConId.get(), connectPacket("from-host"), true);
            assertTrue(clientPayload.await(5, TimeUnit.SECONDS), "Client did not receive the payload");

            join.send(connectPacket("from-client"));
            assertTrue(hostPayload.await(5, TimeUnit.SECONDS), "Host did not receive the payload");

            join.close();
        }finally{
            host.closeRoom();
            relay.stop();
        }
    }

    private static Packets.ConnectPacket connectPacket(String name){
        Packets.ConnectPacket packet = new Packets.ConnectPacket();
        packet.name = name;
        packet.usid = "usid";
        packet.versionType = "official";
        packet.uuid = "AAAAAAAAAAA=";
        packet.color = 0xffffffff;
        return packet;
    }
}
