package io.anuke.mindustry.claj;

import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.net.Packets;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class OfficialRelayPublicTest{
    @Test
    @Disabled("Manual test against a live public CLaJ relay")
    public void publicRelayHostAndJoin() throws Exception{
        Net.setClientLoaded(true);
        CountDownLatch payload = new CountDownLatch(1);
        CountDownLatch hostPayload = new CountDownLatch(1);
        Net.handleClient(Packets.ConnectPacket.class, packet -> payload.countDown());
        Net.handleServer(Packets.ConnectPacket.class, (id, packet) -> hostPayload.countDown());

        OfficialRelayHost host = new OfficialRelayHost();
        CountDownLatch created = new CountDownLatch(1);
        AtomicReference<ClajLink> linkRef = new AtomicReference<>();

        try{
            host.createRoom("n4.xpdustry.com", 50000, false, ClajProtocol.NO_PASSWORD,
                link -> {
                    linkRef.set(link);
                    created.countDown();
                },
                reason -> {},
                error -> {
                    error.printStackTrace();
                    fail(error);
                });

            assertTrue(created.await(20, TimeUnit.SECONDS), "no room created on public relay");
            ClajLink link = linkRef.get();
            System.out.println("Official relay room: " + link);

            CountDownLatch joined = new CountDownLatch(1);
            OfficialRelayJoin join = new OfficialRelayJoin(link, ClajProtocol.NO_PASSWORD,
                joined::countDown,
                reason -> fail("rejected: " + reason),
                error -> {
                    error.printStackTrace();
                    fail(error);
                });
            join.start();

            assertTrue(joined.await(20, TimeUnit.SECONDS), "public relay join failed");

            for(int i = 0; i < 100 && !host.hasConnections(); i++) Thread.sleep(50);
            assertTrue(host.hasConnections(), "host never saw the virtual connection");
            int conId = host.getConnections().first().id;

            Packets.ConnectPacket packet = new Packets.ConnectPacket();
            packet.name = "official-test";
            packet.usid = "usid";
            packet.versionType = "official";
            packet.uuid = "AAAAAAAAAAA=";
            packet.color = 0xffffffff;
            host.sendTo(conId, packet, true);

            assertTrue(payload.await(10, TimeUnit.SECONDS), "payload did not arrive");
            join.send(packet);
            assertTrue(hostPayload.await(10, TimeUnit.SECONDS), "host payload did not arrive");
            join.close();
        }finally{
            host.closeRoom();
        }
    }
}
