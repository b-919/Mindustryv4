package io.anuke.mindustry.claj;

import io.anuke.mindustry.net.Host;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.net.Packet;
import io.anuke.ucore.function.Consumer;

import java.io.IOException;

/** Wraps the real client provider and switches to the CLaJ transport for room links. */
public class ClajClientProvider implements Net.ClientProvider{
    private final Net.ClientProvider delegate;
    private volatile ClajJoin current;

    public ClajClientProvider(Net.ClientProvider delegate){
        this.delegate = delegate;
    }

    public Net.ClientProvider delegate(){
        return delegate;
    }

    @Override
    public void connect(String ip, int port, Runnable success) throws IOException{
        Claj.PendingJoin pending = Claj.takePendingJoin();
        if(pending != null){
            ClajJoin join = new ClajJoin(pending.link, pending.password,
                pending.success == null ? success : pending.success, pending.reject, pending.failed);
            current = join;
            join.start();
        }else{
            delegate.connect(ip, port, success);
        }
    }

    @Override
    public void send(Object object, Net.SendMode mode){
        ClajJoin join = current;
        if(join != null){
            if(join.isJoined() && object instanceof Packet){
                join.send((Packet)object);
            }
            return;
        }

        delegate.send(object, mode);
    }

    @Override
    public void updatePing(){
        ClajJoin join = current;
        if(join != null) return;
        delegate.updatePing();
    }

    @Override
    public int getPing(){
        ClajJoin join = current;
        if(join != null) return Math.max(0, join.getPing());
        return delegate.getPing();
    }

    @Override
    public void disconnect(){
        ClajJoin join = current;
        current = null;
        if(join != null) join.close();

        delegate.disconnect();
    }

    @Override
    public byte[] decompressSnapshot(byte[] input, int size){
        return delegate.decompressSnapshot(input, size);
    }

    @Override
    public void discover(Consumer<Host> callback, Runnable done){
        delegate.discover(callback, done);
    }

    @Override
    public void pingHost(String address, int port, Consumer<Host> valid, Consumer<Exception> failed){
        delegate.pingHost(address, port, valid, failed);
    }

    @Override
    public void dispose(){
        ClajJoin join = current;
        current = null;
        if(join != null) join.close();

        delegate.dispose();
    }
}
