package io.anuke.mindustry.claj;

import com.badlogic.gdx.utils.Array;
import com.xpdustry.claj.api.Claj;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.net.NetConnection;
import io.anuke.mindustry.net.Packet;
import io.anuke.mindustry.net.Streamable;

import java.io.IOException;

/** Wraps the real server provider and routes CLaJ clients through the official proxy. */
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
        if(Claj.initialized()) Claj.get().closeRooms();
        delegate.host(port);
    }

    @Override
    public void sendStream(int id, Streamable stream){
        if(ClajSupport.hasConnection(id)){
            ClajSupport.sendStream(id, stream);
        }else{
            delegate.sendStream(id, stream);
        }
    }

    @Override
    public void send(Object object, Net.SendMode mode){
        delegate.send(object, mode);

        if(object instanceof Packet) ClajSupport.broadcast(object);
    }

    @Override
    public void sendTo(int id, Object object, Net.SendMode mode){
        if(ClajSupport.hasConnection(id)){
            ClajSupport.sendTo(id, object);
        }else{
            delegate.sendTo(id, object, mode);
        }
    }

    @Override
    public void sendExcept(int id, Object object, Net.SendMode mode){
        delegate.sendExcept(id, object, mode);

        if(object instanceof Packet) ClajSupport.sendExcept(id, object);
    }

    @Override
    public void close(){
        if(Claj.initialized()) Claj.get().closeRooms();
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

        result.addAll(ClajSupport.getConnections());
        return result;
    }

    @Override
    public NetConnection getByID(int id){
        NetConnection connection = ClajSupport.getConnection(id);
        return connection != null ? connection : delegate.getByID(id);
    }

    @Override
    public void dispose(){
        ClajSupport.dispose();
        delegate.dispose();
    }
}
