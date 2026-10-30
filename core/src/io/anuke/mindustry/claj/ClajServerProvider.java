package io.anuke.mindustry.claj;

import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.net.NetConnection;
import io.anuke.mindustry.net.Packet;
import io.anuke.mindustry.net.Streamable;

import java.io.IOException;

/** Wraps the real server provider and routes CLaJ clients through the host proxy. */
public class ClajServerProvider implements Net.ServerProvider{
    private final Net.ServerProvider delegate;

    public ClajServerProvider(Net.ServerProvider delegate){
        this.delegate = delegate;
    }

    public Net.ServerProvider delegate(){
        return delegate;
    }

    @Override
    public void host(int port) throws IOException{
        ClajHost host = Claj.host();
        if(host != null && (host.isRoomCreated() || host.isCreating())) host.dispose();
        delegate.host(port);
    }

    @Override
    public void sendStream(int id, Streamable stream){
        ClajHost host = Claj.host();
        if(host != null && host.contains(id)){
            host.sendStream(id, stream);
        }else{
            delegate.sendStream(id, stream);
        }
    }

    @Override
    public void send(Object object, Net.SendMode mode){
        delegate.send(object, mode);

        ClajHost host = Claj.host();
        if(host != null && host.isRoomCreated() && object instanceof Packet) host.broadcast(object);
    }

    @Override
    public void sendTo(int id, Object object, Net.SendMode mode){
        ClajHost host = Claj.host();
        if(host != null && host.contains(id) && object instanceof Packet){
            host.sendTo(id, object, mode == Net.SendMode.tcp);
        }else{
            delegate.sendTo(id, object, mode);
        }
    }

    @Override
    public void sendExcept(int id, Object object, Net.SendMode mode){
        delegate.sendExcept(id, object, mode);

        ClajHost host = Claj.host();
        if(host != null && host.isRoomCreated() && object instanceof Packet) host.sendExcept(id, object);
    }

    @Override
    public void close(){
        ClajHost host = Claj.host();
        if(host != null) host.dispose();
        delegate.close();
    }

    @Override
    public byte[] compressSnapshot(byte[] input){
        return delegate.compressSnapshot(input);
    }

    @Override
    public Array<NetConnection> getConnections(){
        Array<NetConnection> result = new Array<>();
        for(NetConnection connection : delegate.getConnections()){
            result.add(connection);
        }

        ClajHost host = Claj.host();
        if(host != null) result.addAll(host.getConnections());
        return result;
    }

    @Override
    public NetConnection getByID(int id){
        ClajHost host = Claj.host();
        if(host != null && host.contains(id)){
            for(NetConnection connection : host.getConnections()){
                if(connection.id == id) return connection;
            }
        }
        return delegate.getByID(id);
    }

    @Override
    public void dispose(){
        Claj.dispose();
        delegate.dispose();
    }
}
