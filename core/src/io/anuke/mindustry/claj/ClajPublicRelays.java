package io.anuke.mindustry.claj;

import java.util.LinkedHashMap;
import java.util.Map;

/** Public official CLaJ relay nodes. Kept in sync with xpdustry/claj's public-servers.hjson. */
public final class ClajPublicRelays{
    public static final Map<String, String> SERVERS = new LinkedHashMap<>();

    static{
        SERVERS.put("Chaotic Neutral FR", "n4.xpdustry.com:50000");
        SERVERS.put("Eradication IN", "in2.eradication.industries:8025");
        SERVERS.put("Eradication US", "us1.eradication.industries:8025");
        SERVERS.put("Eradication UK", "uk1.eradication.industries:8025");
        SERVERS.put("MDF Alps", "2.7.107.21:50000");
        SERVERS.put("Mindustry France Official", "claj.jojofr.dev:34650");
        SERVERS.put("German bunny DE", "claj.l4p1n.ch:50000");
        SERVERS.put("MindCup RUS / SPB", "MindCup.ru:1111");
    }

    private ClajPublicRelays(){}

    public static String defaultAddress(){
        for(String address : SERVERS.values()) return address;
        return "";
    }
}
