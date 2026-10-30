/**
 * This file is part of CLaJ. The system that allows you to play with your friends,
 * just by creating a room, copying the link and sending it to your friends.
 * Copyright (c) 2025-2026  Xpdustry
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.xpdustry.claj.api;

import java.nio.ByteBuffer;

import arc.net.ArcNetException;
import arc.net.FrameworkMessage;
import arc.net.NetSerializer;
import arc.util.Threads;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import com.xpdustry.claj.common.ClajNet;
import com.xpdustry.claj.common.net.FrameworkSerializer;
import com.xpdustry.claj.common.packets.Packet;
import com.xpdustry.claj.common.packets.RawPacket;


public class ClajClientSerializer implements NetSerializer, FrameworkSerializer {
  protected final ThreadLocal<ByteBufferInput> read = Threads.local(ByteBufferInput::new);
  protected final ThreadLocal<ByteBufferOutput> write = Threads.local(ByteBufferOutput::new);

  @Override
  public Object read(ByteBuffer buffer) {
    if (!buffer.hasRemaining()) return null;
    byte id = buffer.get();
    if (id == ClajNet.frameworkId) return readFramework(buffer);
    if (id == ClajNet.oldId) throw new ArcNetException("Received a packet from the old CLaJ protocol");
    if (id == ClajNet.id) return readClaj(buffer);
    throw new ArcNetException("Unknown protocol type: " + id);
  }

  public Packet readClaj(ByteBuffer buffer) {
    Packet packet = ClajNet.newLocalPacket(buffer.get());
    if (!packet.allow(false))
      throw new ArcNetException("Invalid packet type for endpoint: " + packet.getClass().getName());
    ByteBufferInput in = read.get();
    in.buffer = buffer;
    packet.read(in);
    return packet;
  }

  @Override
  public void write(ByteBuffer buffer, Object object) {
    if (object instanceof ByteBuffer) {
      buffer.put((ByteBuffer)object);
    } else if (object instanceof FrameworkMessage) {
      writeFramework(buffer.put(ClajNet.frameworkId), (FrameworkMessage)object);
    } else if (object instanceof RawPacket) {
      ((RawPacket)object).write(buffer);
    } else if (object instanceof Packet) {
      writeClaj(buffer.put(ClajNet.id), (Packet)object);
    } else {
      throw new ArcNetException("Unknown packet type: " + object.getClass().getName());
    }
  }

  public void writeClaj(ByteBuffer buffer, Packet packet) {
    buffer.put(ClajNet.getId(packet));
    ByteBufferOutput out = write.get();
    out.buffer = buffer;
    packet.write(out);
  }
}
