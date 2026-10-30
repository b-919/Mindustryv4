package io.anuke.mindustry.claj;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.anuke.mindustry.claj.ClajProtocol.*;

/** A framed TCP connection used by the relay, the host proxy and the join client. */
public class ClajSocket{
    public interface Listener{
        void onFrame(byte type, byte[] data, ClajSocket socket);

        default void onClosed(ClajSocket socket){}
    }

    private static final int CONNECT_TIMEOUT = 5000;
    private static final int KEEPALIVE_INTERVAL = 5000;
    private static final int IDLE_TIMEOUT = 30000;
    private static final int QUEUE_SIZE = 8192;
    /** Internal sentinel used to drain the writer before closing. */
    private static final byte CLOSE_TYPE = (byte)0x7F;

    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final Listener listener;
    private final BlockingQueue<Frame> outgoing = new ArrayBlockingQueue<>(QUEUE_SIZE);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean closing = new AtomicBoolean();
    private final AtomicBoolean notified = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();

    private volatile long lastRead = System.currentTimeMillis();
    private volatile int ping = -1;

    public ClajSocket(String host, int port, Listener listener) throws IOException{
        this.socket = new Socket();
        this.listener = listener;
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT);
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    public ClajSocket(Socket socket, Listener listener) throws IOException{
        this.socket = socket;
        this.listener = listener;
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    public void start(){
        if(!started.compareAndSet(false, true)) return;

        Thread reader = new Thread(this::readLoop, "CLaJ Connection Reader");
        reader.setDaemon(true);
        reader.start();

        Thread writer = new Thread(this::writeLoop, "CLaJ Connection Writer");
        writer.setDaemon(true);
        writer.start();

        Thread keepalive = new Thread(this::keepaliveLoop, "CLaJ Connection Keepalive");
        keepalive.setDaemon(true);
        keepalive.start();
    }

    private void readLoop(){
        try{
            while(!closed.get()){
                Frame frame = readFrame(input);
                lastRead = System.currentTimeMillis();

                if(frame.type == T_PING){
                    send(T_PONG, frame.data);
                }else if(frame.type == T_PONG){
                    if(frame.data.length >= 8){
                        long sent = input(frame.data).readLong();
                        ping = (int)Math.max(0, System.currentTimeMillis() - sent);
                    }
                }else{
                    listener.onFrame(frame.type, frame.data, this);
                }
            }
        }catch(Throwable ignored){
        }finally{
            close();
        }
    }

    private void writeLoop(){
        try{
            while(!closed.get()){
                Frame frame = outgoing.poll(1, TimeUnit.SECONDS);
                if(frame == null) continue;

                if(frame.type == CLOSE_TYPE){
                    close();
                    return;
                }

                synchronized(output){
                    writeFrame(output, frame.type, frame.data);
                }
            }
        }catch(InterruptedException ignored){
        }catch(Throwable ignored){
            close();
        }
    }

    private void keepaliveLoop(){
        try{
            while(!closed.get()){
                Thread.sleep(KEEPALIVE_INTERVAL);
                if(closed.get()) return;

                if(System.currentTimeMillis() - lastRead > IDLE_TIMEOUT){
                    close();
                    return;
                }

                send(T_PING, ping(System.currentTimeMillis()));
            }
        }catch(InterruptedException ignored){
        }
    }

    public void send(byte type, byte[] data){
        if(closed.get() || closing.get()) return;
        if(!started.get()) start();

        if(!outgoing.offer(new Frame(type, data))){
            // A dead or extremely slow peer must not block the game thread.
            close();
        }
    }

    /** Writes all queued frames, then closes the socket. */
    public void closeAfterFlush(){
        if(closed.get() || !closing.compareAndSet(false, true)) return;
        if(!started.get()) start();

        if(!outgoing.offer(new Frame(CLOSE_TYPE, new byte[0]))){
            close();
        }
    }

    public boolean isClosed(){
        return closed.get();
    }

    public int getPing(){
        return ping;
    }

    public int getQueuedFrames(){
        return outgoing.size();
    }

    /** Drops pending outgoing frames. Used when a room is closing. */
    public void clearQueue(){
        outgoing.clear();
    }

    public String address(){
        return socket.getInetAddress() == null ? "unknown" : socket.getInetAddress().getHostAddress();
    }

    public void close(){
        if(!closed.compareAndSet(false, true)) return;
        closing.set(true);
        try{
            socket.close();
        }catch(IOException ignored){
        }
        notifyClosed();
    }

    private void notifyClosed(){
        if(!notified.compareAndSet(false, true)) return;
        try{
            listener.onClosed(this);
        }catch(Throwable ignored){
        }
    }
}
