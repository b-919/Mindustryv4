package io.anuke.mindustry.maps.campaign;

/** A registered campaign generator definition. */
public class Definition{
    public final String name;
    public final PlanetGenerator generator;

    public Definition(String name, PlanetGenerator generator){
        this.name = name;
        this.generator = generator;
    }
}
