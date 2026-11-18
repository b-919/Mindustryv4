package io.anuke.mindustry.claj;

import arc.net.Connection;
import arc.net.DcReason;
import arc.net.NetListener;
import com.xpdustry.claj.api.ClajPinger;
import com.xpdustry.claj.api.ClajProvider;
import com.xpdustry.claj.api.ClajProxy;
import com.xpdustry.claj.common.packets.ConnectionPayloadPacket;
import com.xpdustry.claj.common.status.ClajType;
import com.xpdustry.claj.common.status.ClajVersion;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.net.Packet;
import io.anuke.mindustry.net.Packets;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.Pooling;

import java.nio.ByteBuffer;

class V4ClajProvider implements ClajProvider{
    private static final ClajVersion version = ClajVersion.of("2.4.5");
    private final NetListener listener = new HostListener();

    @Override
    public void postCallback(Runnable task){
        ClajSupport.post(task);
    }

    @Override
    public void postConnect(Runnable task){
        Thread thread = new Thread(task, "CLaJ Task");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public ClajProxy newProxy(){
        ClajProxy proxy = new ClajProxy(this);
        proxy.forceTcp = true;
        return proxy;
    }

    @Override
    public ClajType getType(){
        return OfficialClaj.TYPE;
    }

    @Override
    public ClajVersion getVersion(){
        return version;
    }

    @Override
    public NetListener getConnectionListener(ClajProxy proxy){
        return listener;
    }

    @Override
    public ConnectionPayloadPacket.Serializer getPacketWrapperSerializer(){
        return OfficialClaj.serializer();
    }

    @Override
    public void connectClient(String host, int port, Runnable success, ByteBuffer joinPacket){
        ClajClientProvider.prepareJoin(joinPacket, success);
        Net.connect(host, port, success);
    }

    @Override
    public void handleProxyError(ClajProxy proxy, Throwable error){
        Log.err("CLaJ proxy error.");
        Log.err(error);
    }

    @Override
    public void handlePingerError(ClajPinger pinger, Throwable error){
        Log.err("CLaJ pinger error.");
        Log.err(error);
    }

    private static class HostListener implements NetListener{
        @Override
        public void connected(Connection connection){
            ClajSupport.addConnection(connection);

            Packets.Connect packet = new Packets.Connect();
            packet.id = connection.getID();
            packet.addressTCP = ClajSupport.address(connection);
            ClajSupport.post(() -> Net.handleServerReceived(packet.id, packet));
        }

        @Override
        public void received(Connection connection, Object object){
            if(object instanceof Packet){
                Packet packet = (Packet)object;
                int id = connection.getID();

                ClajSupport.post(() -> {
                    if(ClajSupport.hasConnection(id)) Net.handleServerReceived(id, packet);
                    else Pooling.free(packet);
                });
            }else if(object != null){
                Log.err("Unhandled CLaJ host packet: {0}", object);
            }
        }

        @Override
        public void disconnected(Connection connection, DcReason reason){
            ClajSupport.removeConnection(connection.getID());

            Packets.Disconnect packet = new Packets.Disconnect();
            packet.id = connection.getID();
            ClajSupport.post(() -> Net.handleServerReceived(packet.id, packet));
        }

        @Override
        public void idle(Connection connection){
        }
    }
}
