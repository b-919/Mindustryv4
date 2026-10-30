package io.anuke.mindustry.claj;

import io.anuke.mindustry.net.Packet;

/** Join-side connection interface for the CLaJ relay transport. */
public interface ClajJoinConnection{
    void start();

    boolean isJoined();

    void send(Packet packet);

    void close();

    int getPing();
}
