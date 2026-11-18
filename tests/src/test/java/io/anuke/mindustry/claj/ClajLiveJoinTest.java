package io.anuke.mindustry.claj;

import com.xpdustry.claj.api.Claj;
import com.xpdustry.claj.api.ClajLink;
import com.xpdustry.claj.api.ClajPinger;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.net.Packets;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClajLiveJoinTest{
    @Test
    @Disabled("Manual test against a live public CLaJ relay")
    public void hostAndJoin() throws Exception{
        Field server = Net.class.getDeclaredField("server");
        server.setAccessible(true);
        server.setBoolean(null, true);

        Field active = Net.class.getDeclaredField("active");
        active.setAccessible(true);
        active.setBoolean(null, true);

        Net.setClientLoaded(true);

        AtomicInteger hostId = new AtomicInteger(-1);
        CountDownLatch hostConnect = new CountDownLatch(1);
        CountDownLatch clientConnect = new CountDownLatch(1);
        CountDownLatch clientPayload = new CountDownLatch(1);

        Net.handleServer(Packets.Connect.class, (id, packet) -> {
            hostId.set(id);
            hostConnect.countDown();
        });
        Net.handleClient(Packets.Connect.class, packet -> clientConnect.countDown());
        Net.handleClient(Packets.ConnectPacket.class, packet -> clientPayload.countDown());

        CountDownLatch created = new CountDownLatch(1);
        AtomicReference<ClajLink> linkRef = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        ClajSupport.createRoom("n4.xpdustry.com:50000", false, ClajProtocol.NO_PASSWORD,
            link -> {
                linkRef.set(link);
                created.countDown();
            },
            reason -> {
                error.set(new RuntimeException("closed: " + reason));
                created.countDown();
            },
            throwable -> {
                error.set(throwable);
                created.countDown();
            });

        assertTrue(created.await(20, TimeUnit.SECONDS), "room creation timed out");
        assertNull(error.get(), "room creation failed");
        ClajLink link = linkRef.get();
        assertNotNull(link, "no link returned");

        CountDownLatch magicLatch = new CountDownLatch(1);
        AtomicReference<ByteBuffer> magicRef = new AtomicReference<>();
        AtomicReference<Throwable> joinError = new AtomicReference<>();
        ClajPinger pinger = new ClajPinger(Claj.get().provider);

        pinger.joinRoom(link.host, link.port, link.roomId,
            magic -> {
                magicRef.set(magic);
                magicLatch.countDown();
            },
            reason -> {
                joinError.set(new RuntimeException("rejected: " + reason));
                magicLatch.countDown();
            },
            throwable -> {
                joinError.set(throwable);
                magicLatch.countDown();
            });

        assertTrue(magicLatch.await(20, TimeUnit.SECONDS), "join request timed out");
        assertNull(joinError.get(), "join request failed");
        assertNotNull(magicRef.get(), "no join packet returned");

        CountDownLatch joined = new CountDownLatch(1);
        OfficialRelayJoin join = new OfficialRelayJoin(link.host, link.port, magicRef.get(),
            joined::countDown,
            throwable -> {
                joinError.set(throwable);
                joined.countDown();
            });
        join.start();

        assertTrue(joined.await(20, TimeUnit.SECONDS), "join transport timed out");
        assertNull(joinError.get(), "join transport failed");
        assertTrue(clientConnect.await(10, TimeUnit.SECONDS), "client never connected");
        assertTrue(hostConnect.await(10, TimeUnit.SECONDS), "host never saw the connection");

        Packets.ConnectPacket packet = new Packets.ConnectPacket();
        packet.name = "claj-test";
        packet.usid = "usid";
        packet.versionType = "official";
        packet.uuid = "AAAAAAAAAAA=";
        packet.color = 0xffffffff;
        ClajSupport.sendTo(hostId.get(), packet, true);

        assertTrue(clientPayload.await(10, TimeUnit.SECONDS), "payload did not arrive");

        join.close();
        pinger.close();
        ClajSupport.closeRoom();
    }
}
