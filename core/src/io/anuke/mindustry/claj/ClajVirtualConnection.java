package io.anuke.mindustry.claj;

import io.anuke.mindustry.net.Net.SendMode;
import io.anuke.mindustry.net.NetConnection;

/** A connection that exists only inside the host proxy, backed by a relay client. */
public class ClajVirtualConnection extends NetConnection{
    private final ClajHost host;

    public ClajVirtualConnection(ClajHost host, int id, String address){
        super(id, address == null || address.isEmpty() ? "claj-" + Integer.toHexString(id) : address);
        this.host = host;
    }

    @Override
    public boolean isConnected(){
        return host.contains(id);
    }

    @Override
    public void send(Object object, SendMode mode){
        host.sendTo(id, object, mode == SendMode.tcp);
    }

    @Override
    public void close(){
        host.closeConnection(id);
    }

    public ClajHost host(){
        return host;
    }
}
