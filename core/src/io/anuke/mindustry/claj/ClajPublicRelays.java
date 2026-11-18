package io.anuke.mindustry.claj;

import com.badlogic.gdx.utils.Array;

/** Public official CLaJ relay nodes. Kept in sync with xpdustry/claj's public-servers.hjson. */
public final class ClajPublicRelays{
    public static final Array<Relay> SERVERS = new Array<>();

    static{
        SERVERS.add(new Relay("Chaotic Neutral FR", "n4.xpdustry.com:50000"));
        SERVERS.add(new Relay("Eradication IN", "in2.eradication.industries:8025"));
        SERVERS.add(new Relay("Eradication US", "us1.eradication.industries:8025"));
        SERVERS.add(new Relay("Eradication UK", "uk1.eradication.industries:8025"));
        SERVERS.add(new Relay("MDF Alps", "2.7.107.21:50000"));
        SERVERS.add(new Relay("Mindustry France Official", "claj.jojofr.dev:34650"));
        SERVERS.add(new Relay("German bunny DE", "claj.l4p1n.ch:50000"));
        SERVERS.add(new Relay("MindCup RUS / SPB", "MindCup.ru:1111"));
    }

    private ClajPublicRelays(){}

    public static String defaultAddress(){
        return SERVERS.size == 0 ? "" : SERVERS.first().address;
    }

    public static final class Relay{
        public final String name;
        public final String address;

        Relay(String name, String address){
            this.name = name;
            this.address = address;
        }
    }
}
