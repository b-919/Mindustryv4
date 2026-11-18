package io.anuke.mindustry.claj;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ClajProtocolTest{
    @Test
    public void parsesHostAndPort(){
        ClajProtocol.Address address = ClajProtocol.parseAddress("relay.example:50000");
        assertEquals("relay.example", address.host);
        assertEquals(50000, address.port);
    }

    @Test
    public void parsesSchemeAndPath(){
        ClajProtocol.Address address = ClajProtocol.parseAddress("claj://relay.example:1234/room");
        assertEquals("relay.example", address.host);
        assertEquals(1234, address.port);
    }

    @Test
    public void parsesIpv6AndDefaultPort(){
        ClajProtocol.Address address = ClajProtocol.parseAddress("[::1]:50000");
        assertEquals("::1", address.host);
        assertEquals(50000, address.port);

        address = ClajProtocol.parseAddress("relay.example");
        assertEquals("relay.example", address.host);
        assertEquals(ClajProtocol.DEFAULT_PORT, address.port);
    }

    @Test
    public void rejectsInvalidAddresses(){
        assertThrows(IllegalArgumentException.class, () -> ClajProtocol.parseAddress(null));
        assertThrows(IllegalArgumentException.class, () -> ClajProtocol.parseAddress(""));
        assertThrows(IllegalArgumentException.class, () -> ClajProtocol.parseAddress("relay.example:0"));
        assertThrows(IllegalArgumentException.class, () -> ClajProtocol.parseAddress("relay.example:notaport"));
        assertThrows(IllegalArgumentException.class, () -> ClajProtocol.parseAddress("[::1"));
    }
}
