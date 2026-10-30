package io.anuke.mindustry.claj;

import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.claj.ClajProtocol.CloseReason;
import io.anuke.mindustry.net.NetConnection;
import io.anuke.mindustry.net.Streamable;
import io.anuke.ucore.function.Consumer;

/** Host-side room interface for the CLaJ relay transport. */
public interface ClajHost{
    void createRoom(String host, int port, boolean isProtected, short password,
                    Consumer<ClajLink> created, Consumer<CloseReason> closed, Consumer<Throwable> failed);

    void closeRoom();

    void setConfiguration(boolean isProtected, short password);

    boolean isRoomCreated();

    boolean isCreating();

    ClajLink link();

    long roomId();

    int getPing();

    int getQueuedFrames();

    boolean contains(int conId);

    boolean hasConnections();

    Array<NetConnection> getConnections();

    void sendTo(int conId, Object object, boolean reliable);

    void broadcast(Object object);

    void sendExcept(int exceptId, Object object);

    void sendStream(int conId, Streamable stream);

    void closeConnection(int conId);

    void dispose();
}
