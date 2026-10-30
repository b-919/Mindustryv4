package io.anuke.mindustry.claj;

import io.anuke.mindustry.claj.ClajProtocol.CloseReason;
import io.anuke.mindustry.claj.ClajProtocol.RejectReason;
import io.anuke.mindustry.net.Net;
import io.anuke.ucore.function.Consumer;

/** Global CLaJ manager. */
public final class Claj{
    private static volatile ClajHost host;
    private static volatile PendingJoin pending;

    private Claj(){}

    public static ClajHost host(){
        return host;
    }

    public static boolean hasOpenRoom(){
        ClajHost current = host;
        return current != null && current.isRoomCreated();
    }

    public static boolean isCreatingRoom(){
        ClajHost current = host;
        return current != null && current.isCreating();
    }

    public static ClajLink link(){
        ClajHost current = host;
        return current == null ? null : current.link();
    }

    public static void createRoom(String address, boolean isProtected, short password,
                                  Consumer<ClajLink> created, Consumer<CloseReason> closed,
                                  Consumer<Throwable> failed){
        if(!Net.server()){
            failed.accept(new IllegalStateException("Host a game before creating a CLaJ room"));
            return;
        }

        ClajLink.Address relay;
        try{
            relay = ClajLink.parseAddress(address);
        }catch(Exception e){
            failed.accept(e);
            return;
        }

        ClajHost old = host;
        if(old != null && (old.isRoomCreated() || old.isCreating())){
            failed.accept(new IllegalStateException("A CLaJ room is already open"));
            return;
        }

        ClajHost current = new OfficialRelayHost();
        host = current;
        current.createRoom(relay.host, relay.port, isProtected, password, link -> {
            if(created != null) created.accept(link);
        }, reason -> {
            if(host == current) host = null;
            if(closed != null) closed.accept(reason);
        }, error -> {
            if(host == current) host = null;
            if(failed != null) failed.accept(error);
        });
    }

    public static void closeRoom(){
        ClajHost current = host;
        if(current != null){
            current.closeRoom();
            if(!current.isCreating() && !current.isRoomCreated() && host == current) host = null;
        }
    }

    public static void setConfiguration(boolean isProtected, short password){
        ClajHost current = host;
        if(current != null) current.setConfiguration(isProtected, password);
    }

    public static void join(ClajLink link, short password, Runnable success,
                            Consumer<RejectReason> reject, Consumer<Throwable> failed){
        if(link == null){
            if(failed != null) failed.accept(new IllegalArgumentException("Missing CLaJ link"));
            return;
        }
        if(Net.active()){
            if(failed != null) failed.accept(new IllegalStateException("Already connected"));
            return;
        }

        pending = new PendingJoin(link, password, success, reject, failed);
        Net.connect(link.host, link.port, success);
    }

    public static PendingJoin takePendingJoin(){
        PendingJoin current = pending;
        pending = null;
        return current;
    }

    public static void dispose(){
        pending = null;
        ClajHost current = host;
        host = null;
        if(current != null) current.dispose();
    }

    public static class PendingJoin{
        public final ClajLink link;
        public final short password;
        public final Runnable success;
        public final Consumer<RejectReason> reject;
        public final Consumer<Throwable> failed;

        public PendingJoin(ClajLink link, short password, Runnable success,
                           Consumer<RejectReason> reject, Consumer<Throwable> failed){
            this.link = link;
            this.password = password;
            this.success = success;
            this.reject = reject;
            this.failed = failed;
        }
    }
}
