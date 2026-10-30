package io.anuke.mindustry.claj;

/**
 * A CLaJ room link.
 *
 * <p>Links use the form {@code claj://host:port/room-id}, where {@code room-id} is an
 * URL-safe base64 encoded 64-bit room identifier. For convenience, {@code host:port/room-id}
 * (without the scheme) is accepted as well.</p>
 */
public final class ClajLink{
    public static final String PROTOCOL = "claj";
    public static final String SCHEME = PROTOCOL + "://";

    public final String host;
    public final int port;
    public final long roomId;
    public final String encodedRoomId;

    public ClajLink(String host, int port, long roomId){
        if(host == null || (host = host.trim()).isEmpty()){
            throw new IllegalArgumentException("Missing host");
        }
        if(port < 1 || port > 0xFFFF){
            throw new IllegalArgumentException("Invalid port");
        }
        if(roomId == 0){
            throw new IllegalArgumentException("Invalid room id");
        }

        host = trimBrackets(host);
        this.host = host;
        this.port = port;
        this.roomId = roomId;
        this.encodedRoomId = encodeRoomId(roomId);
    }

    public ClajLink(String host, int port, String encodedRoomId){
        this(host, port, decodeRoomId(encodedRoomId));
    }

    @Override
    public String toString(){
        return format(host, port, encodedRoomId);
    }

    @Override
    public boolean equals(Object object){
        if(this == object) return true;
        if(!(object instanceof ClajLink)) return false;
        ClajLink other = (ClajLink)object;
        return port == other.port && roomId == other.roomId && host.equals(other.host);
    }

    @Override
    public int hashCode(){
        return (host.hashCode() * 31 + port) * 31 + Long.hashCode(roomId);
    }

    /** @throws IllegalArgumentException if the link is invalid. */
    public static ClajLink fromString(String text){
        if(text == null) throw new IllegalArgumentException("Missing link");
        String value = text.trim();
        if(value.isEmpty()) throw new IllegalArgumentException("Missing link");

        int scheme = value.indexOf("://");
        if(scheme >= 0){
            if(!value.regionMatches(true, 0, SCHEME, 0, SCHEME.length())){
                throw new IllegalArgumentException("Not a CLaJ link");
            }
            value = value.substring(SCHEME.length());
        }

        int semicolon = value.indexOf(';');
        if(semicolon >= 0) value = value.substring(0, semicolon);

        int slash = value.lastIndexOf('/');
        if(slash <= 0 || slash == value.length() - 1){
            throw new IllegalArgumentException("Missing room id");
        }

        String authority = value.substring(0, slash);
        String room = value.substring(slash + 1);

        String host;
        int port;
        if(authority.startsWith("[")){
            int end = authority.indexOf(']');
            if(end < 0) throw new IllegalArgumentException("Invalid host");
            host = authority.substring(1, end);
            String rest = authority.substring(end + 1);
            if(rest.isEmpty()){
                port = ClajProtocol.DEFAULT_PORT;
            }else if(rest.startsWith(":")){
                port = parsePort(rest.substring(1));
            }else{
                throw new IllegalArgumentException("Invalid host");
            }
        }else{
            int colon = authority.lastIndexOf(':');
            if(colon < 0){
                host = authority;
                port = ClajProtocol.DEFAULT_PORT;
            }else{
                host = authority.substring(0, colon);
                port = parsePort(authority.substring(colon + 1));
            }
        }

        if(host.isEmpty()) throw new IllegalArgumentException("Missing host");

        return new ClajLink(host, port, room);
    }

    /** Parses a {@code host:port} address. */
    public static Address parseAddress(String text){
        if(text == null) throw new IllegalArgumentException("Missing host");
        String value = text.trim();
        if(value.isEmpty()) throw new IllegalArgumentException("Missing host");

        if(value.startsWith(SCHEME)){
            value = value.substring(SCHEME.length());
        }

        int slash = value.indexOf('/');
        if(slash >= 0) value = value.substring(0, slash);

        String host;
        int port;
        if(value.startsWith("[")){
            int end = value.indexOf(']');
            if(end < 0) throw new IllegalArgumentException("Invalid host");
            host = value.substring(1, end);
            String rest = value.substring(end + 1);
            port = rest.startsWith(":") ? parsePort(rest.substring(1)) : ClajProtocol.DEFAULT_PORT;
        }else{
            int colon = value.lastIndexOf(':');
            if(colon < 0){
                host = value;
                port = ClajProtocol.DEFAULT_PORT;
            }else{
                host = value.substring(0, colon);
                port = parsePort(value.substring(colon + 1));
            }
        }
        if(host.isEmpty()) throw new IllegalArgumentException("Missing host");
        return new Address(host, port);
    }

    public static final class Address{
        public final String host;
        public final int port;

        public Address(String host, int port){
            this.host = host;
            this.port = port;
        }
    }

    public static String format(String host, int port, String encodedRoomId){
        String display = host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]" : host;
        return SCHEME + display + ":" + port + "/" + encodedRoomId;
    }

    public static String encodeRoomId(long roomId){
        byte[] bytes = new byte[8];
        for(int i = 7; i >= 0; i--){
            bytes[i] = (byte)(roomId & 0xFF);
            roomId >>= 8;
        }
        return encodeBase64(bytes);
    }

    public static long decodeRoomId(String encoded){
        if(encoded == null) throw new IllegalArgumentException("Missing room id");
        String value = encoded.trim();
        if(value.startsWith("/")) value = value.substring(1);
        if(value.isEmpty()) throw new IllegalArgumentException("Missing room id");

        byte[] bytes;
        try{
            bytes = decodeBase64(value);
        }catch(IllegalArgumentException e){
            throw new IllegalArgumentException("Invalid room id");
        }

        if(bytes.length != 8) throw new IllegalArgumentException("Invalid room id");

        long result = 0;
        for(byte b : bytes){
            result = (result << 8) | (b & 0xFF);
        }
        if(result == 0) throw new IllegalArgumentException("Invalid room id");
        return result;
    }

    private static final char[] BASE64_URL =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

    /** URL-safe base64 without padding. */
    private static String encodeBase64(byte[] data){
        char[] out = new char[(data.length * 8 + 5) / 6];
        int buffer = 0, bits = 0, index = 0;

        for(byte b : data){
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;

            while(bits >= 6){
                bits -= 6;
                out[index++] = BASE64_URL[(buffer >> bits) & 0x3F];
            }
        }

        if(bits > 0){
            out[index++] = BASE64_URL[(buffer << (6 - bits)) & 0x3F];
        }

        return new String(out, 0, index);
    }

    private static byte[] decodeBase64(String value){
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(value.length() * 6 / 8 + 1);
        int buffer = 0, bits = 0;

        for(int i = 0; i < value.length(); i++){
            char c = value.charAt(i);
            if(c == '=' || Character.isWhitespace(c)) continue;

            int digit = base64Digit(c);
            if(digit < 0) throw new IllegalArgumentException("Invalid base64");

            buffer = (buffer << 6) | digit;
            bits += 6;

            if(bits >= 8){
                bits -= 8;
                out.write((buffer >> bits) & 0xFF);
            }
        }

        return out.toByteArray();
    }

    private static int base64Digit(char c){
        if(c >= 'A' && c <= 'Z') return c - 'A';
        if(c >= 'a' && c <= 'z') return c - 'a' + 26;
        if(c >= '0' && c <= '9') return c - '0' + 52;
        if(c == '-' || c == '+') return 62;
        if(c == '_' || c == '/') return 63;
        return -1;
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

    private static String trimBrackets(String host){
        if(host.startsWith("[") && host.endsWith("]")) return host.substring(1, host.length() - 1);
        return host;
    }
}
