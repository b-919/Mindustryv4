package io.anuke.mindustry.content;

import com.badlogic.gdx.graphics.Color;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.maps.campaign.CampaignRegistry;
import io.anuke.mindustry.maps.campaign.CloudDef;
import io.anuke.mindustry.maps.campaign.PlanetDefinition;
import io.anuke.mindustry.maps.campaign.PlanetVisual;
import io.anuke.mindustry.graphics.planet.SunGlowDef;

/** Content definitions for planets and campaigns.*/
public class Planets{
    public static PlanetDefinition sun, serpulo, moon;

    public static void load(){
        if(serpulo != null) return;

        sun = new PlanetDefinition("Sun", null,
            1f, 0.62f, 0.18f,
            1, 1, 0){{
            isSun = true;
            radius = 2f;
            displayOrbit = false;
        }};


        PlanetVisual sunV = sun.visual;
        sunV.sunRadius = 2f;
        sunV.sunDivisions = 3;
        sunV.sunLowColor.set(1f, 0.42f, 0.06f, 1f);
        sunV.sunHighColor.set(1f, 0.95f, 0.55f, 1f);
        sunV.sunColor.set(1f, 0.62f, 0.18f, 1f);
        sunV.bloomThreshold = 0.95f;
        sunV.bloomIntensity = 5.4f;
        sunV.sunGlows.clear();
        sunV.sunGlows.add(new SunGlowDef(2.55f, 0.18f));
        sunV.sunGlows.add(new SunGlowDef(3.60f, 0.07f));

        serpulo = new PlanetDefinition("Serpulo", CampaignRegistry.serpulo,
            0.22f, 0.44f, 0.85f,
            48, 24, 3) {{
            parent = sun;
            radius = 1f;
            displayOrbit = true;
            generator = CampaignRegistry.generator(CampaignRegistry.serpulo);
            generator.meshHeightIntensity = 0.95f;
        }};


        moon = new PlanetDefinition("Moon", CampaignRegistry.serpulo,
            0.55f, 0.62f, 0.78f,
            1, 1, 0){{
            parent = serpulo;
            radius = 0.55f;
            displayOrbit = true;
            visual.moonPosition.set(-4.5f, 3.2f, -6f);
            visual.moonRadius = 0.55f;
            visual.moonColor.set(0.55f, 0.62f, 0.78f, 1f);
            visual.orbitColor.set(0.72f, 0.80f, 1f, 0.16f);
        }};

        PlanetVisual v = serpulo.visual;
        v.terrainDivisions = 6;
        v.cloudDivisions = 5;
        v.gridColor.set(0.82f, 0.86f, 0.95f, 0.30f);
        v.atmosphereRadius = 1.35f;
        v.atmosphereInnerRadius = 1f;
        v.atmosphereAlpha = 1f;
        v.atmosphereIntensity = 0.35f;
        v.atmosphereDivisions = 3;
        v.atmosphereColor.set(0.36f, 0.55f, 0.95f, 1f);
        v.sectorGlow = true;
        v.sectorGlowAlpha = 10f;
        v.sectorGlowScale = 9f;
        v.sectorGlowThreshold = 0.58f;
        v.sectorGlowSoftness = 0.0f;
        v.sectorPlayerGlow.set(Vars.defaultTeam.color);
        v.sectorEnemyGlow.set(Team.red.color);
        //v.sectorInfectedGlow.set(0.72f, 0.25f, 1f, 1f);
        v.sunDirection.set(0.82f, 0.34f, 0.48f).nor();
        v.sunDistance = 20f;
        v.orbitColor.set(0.62f, 0.74f, 1f, 0.12f);
        v.clouds.clear();
        v.clouds.add(new CloudDef(11, 1.11f, 0.08f, 0.38f,
            new Color(0.780f, 0.957f, 1f, 0.78f)));
        v.clouds.add(new CloudDef(1, 1.14f, 1.24f, 0.41f,
            new Color(0.671f, 0.918f, 0.984f, 0.72f)));
        v.moonPosition.set(-4.5f, 3.2f, -6f);
        v.moonRadius = 0.55f;
        v.moonColor.set(0.55f, 0.62f, 0.78f, 1f);

        CampaignRegistry.addPlanet(serpulo);
    }

    private Planets(){}
}
