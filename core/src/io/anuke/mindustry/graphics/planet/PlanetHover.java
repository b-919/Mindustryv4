package io.anuke.mindustry.graphics.planet;

import io.anuke.mindustry.maps.Sector;

/** Result of a mouse pick against the planet. */
public class PlanetHover{
    public Sector sector;
    public float x, y;
    public boolean selected;

    public void reset(){
        sector = null;
        x = y = 0f;
        selected = false;
    }
}
