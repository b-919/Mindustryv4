package io.anuke.mindustry.graphics.planet;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.Planets;
import io.anuke.mindustry.graphics.mesh.HexMesher;
import io.anuke.mindustry.graphics.mesh.MeshBuilder;
import io.anuke.mindustry.graphics.mesh.PlanetGrid.Corner;
import io.anuke.mindustry.maps.Sector;
import io.anuke.mindustry.maps.campaign.CampaignRegistry;
import io.anuke.mindustry.maps.campaign.CloudDef;
import io.anuke.mindustry.maps.campaign.PlanetDefinition;
import io.anuke.mindustry.maps.campaign.PlanetGenerator;
import io.anuke.mindustry.maps.campaign.PlanetMap;
import io.anuke.mindustry.maps.campaign.PlanetMap.Region;
import io.anuke.mindustry.maps.campaign.PlanetVisual;
import io.anuke.ucore.noise.Simplex;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.world;

/**
 * Everything that is created for one body.
 * The model owns its meshes and knows how to sample the world generator to build them.
 * It contains no framebuffer/bloom/UI code.
 */
public class PlanetModel implements Disposable{
    private static final boolean mobileQuality = Vars.mobile;

    public final PlanetDefinition planet;
    public final PlanetMap map;
    public final SolarSystem system = new SolarSystem();

    public final TerrainMesh terrain;
    public final AtmosphereMesh atmosphere;
    public final CloudMesh[] clouds;
    public final OrbitMesh[] orbits;
    public final SunMesh sun;
    public final MoonMesh moon;
    public final PlanetOverlay overlay;

    public final float gridRadius;
    public final float atmosphereRadius;
    public final float gridLift;
    public final float[] regionHeights;

    private final Simplex cloudNoise = new Simplex(0x5e7f31);
    private final Simplex glowNoise = new Simplex(0x51c3a9);

    public static PlanetModel build(PlanetDefinition planet, PlanetShaders shaders){
        PlanetMap map = CampaignRegistry.planetMap(planet.campaign);
        if(map == null) return null;

        map.ensureSectors(world.sectors);
        return new PlanetModel(planet, map, shaders);
    }

    private PlanetModel(PlanetDefinition planet, PlanetMap map, PlanetShaders shaders){
        this.planet = planet;
        this.map = map;

        PlanetGenerator generator = planet.generator;
        if(generator != null){
            generator.attach(planet, map);
        }

        PlanetVisual visual = planet.visual;
        PlanetDefinition sunBody = Planets.sun == null ? planet : Planets.sun;
        PlanetDefinition moonBody = Planets.moon == null ? planet : Planets.moon;
        PlanetVisual sunVisual = sunBody.visual;
        PlanetVisual moonVisual = moonBody.visual;

        int terrainDivisions = Math.max(2, visual.terrainDivisions - (mobileQuality ? 1 : 0));
        int cloudDivisions = Math.max(2, visual.cloudDivisions - (mobileQuality ? 1 : 0));

        atmosphereRadius = visual.atmosphereRadius;
        gridLift = visual.gridLift;
        gridRadius = 1f + gridLift + 0.02f;

        system.configure(planet);

        terrain = new TerrainMesh(MeshBuilder.buildHex(new HexMesher(){
            @Override
            public float getHeight(Vector3 position){
                return heightAt(position);
            }

            @Override
            public void getColor(Vector3 position, Color out){
                if(generator == null){
                    out.set(Color.WHITE);
                }else{
                    generator.getColor(position, out);
                }
            }

            @Override
            public void getEmissiveColor(Vector3 position, Color out){
                sampleEmissive(position, out);
            }
        }, terrainDivisions, 1f, generator == null ? 1f : generator.meshHeightIntensity), shaders.planet);

        float[] gridHeights = new float[map.grid.corners.length];
        for(Corner corner : map.grid.corners){
            gridHeights[corner.id] = heightAt(corner.v);
        }

        regionHeights = new float[map.regions.length];
        for(Region region : map.regions){
            regionHeights[region.index] = heightAt(region.center);
        }

        atmosphere = new AtmosphereMesh(MeshBuilder.buildHex(new HexMesher(){
            @Override
            public void getColor(Vector3 position, Color out){
                out.set(Color.WHITE);
            }
        }, visual.atmosphereDivisions, atmosphereRadius, 0f), shaders.atmosphere,
            visual.atmosphereInnerRadius, atmosphereRadius,
            visual.atmosphereAlpha, visual.atmosphereIntensity, visual.atmosphereColor);

        clouds = new CloudMesh[visual.clouds.size];
        for(int i = 0; i < visual.clouds.size; i++){
            CloudDef def = visual.clouds.get(i);
            clouds[i] = new CloudMesh(buildCloudMesh(def, cloudDivisions), def.speed, shaders.cloud);
        }

        orbits = buildOrbits(shaders);
        sun = SunMesh.create(sunVisual, cloudNoise, shaders.sun);
        moon = new MoonMesh(buildMoonMesh(moonVisual), shaders.planet);

        overlay = new PlanetOverlay(map, shaders.overlay, gridHeights, regionHeights,
            gridLift, visual.fillLift, visual.lineLift, visual.arcLift);
    }

    private OrbitMesh[] buildOrbits(PlanetShaders shaders){
        Array<OrbitMesh> rings = new Array<>();

        if(planet.displayOrbit && planet.parent != null){
            addOrbit(rings, planet, shaders);
        }

        Array<PlanetDefinition> bodies = new Array<>();
        system.collectBodies(bodies);
        for(PlanetDefinition body : bodies){
            if(body != planet && body.parent == planet && body.displayOrbit){
                addOrbit(rings, body, shaders);
            }
        }

        return rings.toArray(OrbitMesh.class);
    }

    private void addOrbit(Array<OrbitMesh> rings, PlanetDefinition body, PlanetShaders shaders){
        Vector3 bodyPos = system.positionOf(body);
        Vector3 parentPos = system.positionOf(body.parent);
        if(bodyPos == null || parentPos == null) return;

        rings.add(OrbitMesh.create(parentPos, bodyPos, body.visual.orbitColor, shaders.overlay));
    }

    private Mesh buildCloudMesh(CloudDef def, int divisions){
        Simplex noise = new Simplex(7 + def.seed);

        return MeshBuilder.buildHex(new HexMesher(){
            @Override
            public void getColor(Vector3 position, Color out){
                out.set(def.color);
            }

            @Override
            public boolean skip(Vector3 position){
                return noise.octaveNoise3D(2, 0.45, 0.95, position.x, position.y * 3f, position.z) >= def.threshold;
            }
        }, divisions, def.radius, 0f);
    }

    private Mesh buildMoonMesh(PlanetVisual moonVisual){
        return MeshBuilder.buildHex(new HexMesher(){
            @Override
            public void getColor(Vector3 position, Color out){
                double noise = cloudNoise.octaveNoise3D(3, 0.5, 6f, position.x + 4f, position.y, position.z);
                out.set(moonVisual.moonColor.r + (float)noise * 0.2f,
                    moonVisual.moonColor.g + (float)noise * 0.2f,
                    moonVisual.moonColor.b + (float)noise * 0.18f, 1f);
            }
        }, 2, moonVisual.moonRadius, 0.02f);
    }

    private float heightAt(Vector3 direction){
        return planet.generator == null ? 0f : planet.generator.getHeight(direction);
    }

    private void sampleEmissive(Vector3 direction, Color out){
        if(planet.generator == null){
            out.set(0f, 0f, 0f, 0f);
        }else{
            planet.generator.getEmissiveColor(direction, out);
        }

        //team-colored glow on base/attack sector land tiles liquids are the exception
        if(planet.visual.sectorGlow && planet.generator != null && !planet.generator.isLiquid(direction)){
            Region region = map.regionAt(direction);
            Sector sector = region == null ? null : region.sector;

            if(sector != null && !sector.isInfected()){
                Color glow = null;

                if(sector.hasEnemyBase()){
                    glow = planet.visual.sectorEnemyGlow;
                }else if(sector.hasBase()){
                    glow = planet.visual.sectorPlayerGlow;
                }

                if(glow != null){
                    float noise = (float)glowNoise.octaveNoise3D(3, 0.5, planet.visual.sectorGlowScale,
                        direction.x, direction.y, direction.z);
                    float mask = Mathf.clamp((noise - planet.visual.sectorGlowThreshold) /
                        Math.max(0.001f, planet.visual.sectorGlowSoftness), 0f, 1f);

                    if(mask > 0.01f){
                        out.set(glow);
                        out.a = planet.visual.sectorGlowAlpha * mask;
                    }
                }
            }
        }
    }
    @Override
    public void dispose(){
        terrain.dispose();
        atmosphere.dispose();
        for(CloudMesh cloud : clouds){
            cloud.dispose();
        }
        for(OrbitMesh orbit : orbits){
            orbit.dispose();
        }
        sun.dispose();
        moon.dispose();
        overlay.dispose();
    }
}
