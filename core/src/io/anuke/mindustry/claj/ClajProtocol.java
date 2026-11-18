package io.anuke.mindustry.claj;

/**
 * Shared CLaJ constants and relay address parsing.
 *
 * <p>The wire protocol itself comes from the official CLaJ {@code common} dependency;
 * the room payload uses {@link ClajPayloadCodec}.</p>
 */
public final class ClajProtocol{
    /** Fallback port used when a relay address omits the port. */
    public static final int DEFAULT_PORT = 50000;
    /** Password value used when a room has no password. */
    public static final short NO_PASSWORD = -1;

    private ClajProtocol(){}

    /** Parses a {@code host:port} relay address, with {@link #DEFAULT_PORT} as fallback. */
    static Address parseAddress(String text){
        if(text == null) throw new IllegalArgumentException("Missing host");
        String value = text.trim();
        if(value.isEmpty()) throw new IllegalArgumentException("Missing host");

        int scheme = value.indexOf("://");
        if(scheme >= 0) value = value.substring(scheme + 3);

        int slash = value.indexOf('/');
        if(slash >= 0) value = value.substring(0, slash);

        String host;
        int port;
        if(value.startsWith("[")){
            int end = value.indexOf(']');
            if(end < 0) throw new IllegalArgumentException("Invalid host");
            host = value.substring(1, end);
            String rest = value.substring(end + 1);
            port = rest.startsWith(":") ? parsePort(rest.substring(1)) : DEFAULT_PORT;
        }else{
            int colon = value.lastIndexOf(':');
            if(colon < 0){
                host = value;
                port = DEFAULT_PORT;
            }else{
                host = value.substring(0, colon);
                port = parsePort(value.substring(colon + 1));
            }
        }

        if(host.isEmpty()) throw new IllegalArgumentException("Missing host");
        return new Address(host, port);
    }

    private static int parsePort(String value){
        try{
            int port = Integer.parseInt(value);
            if(port < 1 || port > 0xFFFF) throw new IllegalArgumentException("Invalid port");
            return port;
        }catch(NumberFormatException e){
            throw new IllegalArgumentException("Invalid port");
        }
    }

    static final class Address{
        final String host;
        final int port;

        Address(String host, int port){
            this.host = host;
            this.port = port;
        }
    }
}
