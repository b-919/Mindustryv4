package io.anuke.mindustry.ai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.entities.units.Squad;
import io.anuke.mindustry.entities.units.UnitType;
import io.anuke.mindustry.maps.missions.WaveExtraMission;
import io.anuke.mindustry.game.EventType.WorldLoadEvent;
import io.anuke.mindustry.game.GameMode;
import io.anuke.mindustry.game.SpawnGroup;
import io.anuke.mindustry.game.Waves;
import io.anuke.mindustry.type.ContentType;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Events;
import io.anuke.ucore.util.GridBits;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Structs;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.*;

public class WaveSpawner{
    private static final int quadsize = 4;

    private GridBits quadrants;

    private Array<SpawnGroup> groups;
    private boolean dynamicSpawn;

    private Array<FlyerSpawn> flySpawns = new Array<>();
    private Array<GroundSpawn> groundSpawns = new Array<>();

    public WaveSpawner(){
        Events.on(WorldLoadEvent.class, this::reset);
    }

    public void write(DataOutput output) throws IOException{
        output.writeShort(flySpawns.size);
        for(FlyerSpawn spawn : flySpawns){
            output.writeFloat(spawn.angle);
        }

        output.writeShort(groundSpawns.size);
        for(GroundSpawn spawn : groundSpawns){
            output.writeShort((short) spawn.x);
            output.writeShort((short) spawn.y);
        }
    }

    public void read(DataInput input) throws IOException{
        short flya = input.readShort();

        for(int i = 0; i < flya; i++){
            FlyerSpawn spawn = new FlyerSpawn();
            spawn.angle = input.readFloat();
            flySpawns.add(spawn);
        }

        short grounda = input.readShort();
        for(int i = 0; i < grounda; i++){
            GroundSpawn spawn = new GroundSpawn();
            spawn.x = input.readShort();
            spawn.y = input.readShort();
            groundSpawns.add(spawn);
        }
    }

    public void spawnEnemies(){
        if(state.mode == GameMode.SiegeMode){
            spawnSiegeModeEnemies();
            return;
        }

        int flyGroups = 0;
        int groundGroups = 0;

        //count total subgroups spawned by flying/group types
        for(SpawnGroup group : groups){
            int amount = group.getGroupsSpawned(state.wave);
            if(group.type.isFlying){
                flyGroups += amount;
            }else if(dynamicSpawn){
                groundGroups += amount;
            }
        }

        int addGround = groundGroups - groundSpawns.size, addFly = flyGroups - flySpawns.size;

        //add extra groups if the total exceeds it
        if(dynamicSpawn){
            for(int i = 0; i < addGround; i++){
                GroundSpawn spawn = new GroundSpawn();
                findLocation(spawn);
                groundSpawns.add(spawn);
            }
        }

        for(int i = 0; i < addFly; i++){
            FlyerSpawn spawn = new FlyerSpawn();
            findLocation(spawn);
            flySpawns.add(spawn);
        }

        //store index of last used fly/ground spawn locations
        int flyCount = 0, groundCount = 0;

        for(SpawnGroup group : groups){
            int groups = group.getGroupsSpawned(state.wave);
            int spawned = group.getUnitsSpawned(state.wave);

            for(int i = 0; i < groups; i++){
                Squad squad = new Squad();
                float spawnX, spawnY;
                float spread;

                if(!group.type.isFlying && groundCount >= groundSpawns.size) continue;

                if(group.type.isFlying){
                    FlyerSpawn spawn = flySpawns.get(flyCount);

                    if(world.isOpenWorld()){
                        float playerX = players[0].x;
                        float playerY = players[0].y;
                        float flyRadius = world.width() * tilesize / 2f + 40f;
                        spawnX = playerX + Mathf.sqrwavex(spawn.angle) * flyRadius;
                        spawnY = playerY + Mathf.sqrwavey(spawn.angle) * flyRadius;
                    }else{
                        float margin = 40f;
                        spawnX = world.width() * tilesize / 2f + Mathf.sqrwavex(spawn.angle) * (world.width() / 2f * tilesize + margin);
                        spawnY = world.height() * tilesize / 2f + Mathf.sqrwavey(spawn.angle) * (world.height() / 2f * tilesize + margin);
                    }
                    spread = 40f / 1.5f;

                    flyCount++;
                }else{ //make sure it works for non-dynamic spawns
                    GroundSpawn spawn = groundSpawns.get(groundCount);

                    if(dynamicSpawn){
                        if(world.isOpenWorld()){
                            int qcx = MathUtils.floor(players[0].x / tilesize / quadsize);
                            int qcy = MathUtils.floor(players[0].y / tilesize / quadsize);
                            int localX = spawn.x - qcx + openWorldQuadRadius();
                            int localY = spawn.y - qcy + openWorldQuadRadius();
                            if(localX >= 0 && localY < openWorldQuadDiameter()){
                                checkQuadrantOpenWorld(spawn.x, spawn.y, qcx, qcy);
                                if(!getQuadSafe(localX, localY)){
                                    findLocation(spawn);
                                }
                            }else{
                                findLocation(spawn);
                            }
                        }else{
                            checkQuadrant(spawn.x, spawn.y);
                            if(!getQuad(spawn.x, spawn.y)){
                                findLocation(spawn);
                            }
                        }
                    }

                    if(world.isOpenWorld()){
                        spawnX = spawn.x * quadsize * tilesize + quadsize * tilesize / 2f;
                        spawnY = spawn.y * quadsize * tilesize + quadsize * tilesize / 2f;
                    }else{
                        spawnX = spawn.x * quadsize * tilesize + quadsize * tilesize / 2f;
                        spawnY = spawn.y * quadsize * tilesize + quadsize * tilesize / 2f;
                    }
                    spread = quadsize * tilesize / 3f;

                    groundCount++;
                }

                for(int j = 0; j < spawned; j++){
                    BaseUnit unit = group.createUnit(state.enemyTeam);
                    unit.setWave();
                    unit.setSquad(squad);
                    unit.set(spawnX + Mathf.range(spread), spawnY + Mathf.range(spread));
                    unit.add();
                }
            }
        }
    }

    private void spawnSiegeModeEnemies(){
        Array<UnitType> picked = pickExtraSurvivalUnits();
        if(picked.size == 0){
            return;
        }

        int flyUnits = 0;
        int groundUnits = 0;
        for(UnitType type : picked){
            if(type.isFlying){
                flyUnits++;
            }else if(dynamicSpawn){
                groundUnits++;
            }
        }

        int addGround = groundUnits - groundSpawns.size;
        int addFly = flyUnits - flySpawns.size;

        if(dynamicSpawn){
            for(int i = 0; i < addGround; i++){
                GroundSpawn spawn = new GroundSpawn();
                findLocation(spawn);
                groundSpawns.add(spawn);
            }
        }

        for(int i = 0; i < addFly; i++){
            FlyerSpawn spawn = new FlyerSpawn();
            findLocation(spawn);
            flySpawns.add(spawn);
        }

        int flyCount = 0, groundCount = 0;

        for(UnitType type : picked){
            Squad squad = new Squad();
            float spawnX, spawnY;
            float spread;

            if(type.isFlying){
                FlyerSpawn spawn = flySpawns.get(Mathf.mod(flyCount, flySpawns.size));

                if(world.isOpenWorld()){
                    float playerX = players[0].x;
                    float playerY = players[0].y;
                    float flyRadius = world.width() * tilesize / 2f + 40f;
                    spawnX = playerX + Mathf.sqrwavex(spawn.angle) * flyRadius;
                    spawnY = playerY + Mathf.sqrwavey(spawn.angle) * flyRadius;
                }else{
                    float margin = 40f;
                    spawnX = world.width() * tilesize / 2f + Mathf.sqrwavex(spawn.angle) * (world.width() / 2f * tilesize + margin);
                    spawnY = world.height() * tilesize / 2f + Mathf.sqrwavey(spawn.angle) * (world.height() / 2f * tilesize + margin);
                }
                spread = 40f / 1.5f;
                flyCount++;
            }else{
                if(groundSpawns.size == 0) continue;

                GroundSpawn spawn = groundSpawns.get(Mathf.mod(groundCount, groundSpawns.size));

                if(dynamicSpawn){
                    if(world.isOpenWorld()){
                        int qcx = MathUtils.floor(players[0].x / tilesize / quadsize);
                        int qcy = MathUtils.floor(players[0].y / tilesize / quadsize);
                        checkQuadrantOpenWorld(spawn.x, spawn.y, qcx, qcy);
                        int localX = spawn.x - qcx + openWorldQuadRadius();
                        int localY = spawn.y - qcy + openWorldQuadRadius();
                        if(!getQuadSafe(localX, localY)){
                            findLocation(spawn);
                        }
                    }else{
                        checkQuadrant(spawn.x, spawn.y);
                        if(!getQuad(spawn.x, spawn.y)){
                            findLocation(spawn);
                        }
                    }
                }

                spawnX = spawn.x * quadsize * tilesize + quadsize * tilesize / 2f;
                spawnY = spawn.y * quadsize * tilesize + quadsize * tilesize / 2f;
                spread = quadsize * tilesize / 3f;
                groundCount++;
            }

            BaseUnit unit = type.create(state.enemyTeam);
            unit.setWave();
            unit.setSquad(squad);
            unit.set(spawnX + Mathf.range(spread), spawnY + Mathf.range(spread));
            unit.add();
        }
    }

    private Array<UnitType> pickExtraSurvivalUnits(){
        Array<UnitType> result = new Array<>();
        int funds = WaveExtraMission.displayedFunds;

        while(funds > 0){
            Array<UnitType> affordable = new Array<>();
            int cheapest = Integer.MAX_VALUE;

            for(UnitType type : content.<UnitType>getBy(ContentType.unit)){
                if(type == null || !type.spawnsInSiegeMode || type.unitCost <= 0) continue;
                if(type.unitCost <= funds){
                    affordable.add(type);
                    cheapest = Math.min(cheapest, type.unitCost);
                }
            }

            if(affordable.size == 0 || cheapest == Integer.MAX_VALUE){
                break;
            }

            affordable.sort((a, b) -> Integer.compare(a.unitCost, b.unitCost));

            int maxIndex = Math.min(affordable.size - 1, Math.max(0, state.wave / 4));
            UnitType choice = affordable.get(Mathf.random(maxIndex));
            result.add(choice);
            funds -= choice.unitCost;
        }

        int spent = WaveExtraMission.displayedFunds - funds;
        if(spent > 0){
            WaveExtraMission.spendFunds(spent);
        }

        return result;
    }

    public void checkAllQuadrants(){
        if(world.isOpenWorld()){
            return;
        }
        for(int x = 0; x < quadWidth(); x++){
            for(int y = 0; y < quadHeight(); y++){
                checkQuadrant(x, y);
            }
        }
    }

    private void checkQuadrant(int quadx, int quady){
        setQuad(quadx, quady, true);

        outer:
        for(int x = quadx * quadsize; x < world.width() && x < (quadx + 1) * quadsize; x++){
            for(int y = quady * quadsize; y < world.height() && y < (quady + 1) * quadsize; y++){
                Tile tile = world.tile(x, y);

                if(tile == null || tile.solid() || world.pathfinder.getValueforTeam(state.enemyTeam, x, y) == Float.MAX_VALUE){
                    setQuad(quadx, quady, false);
                    break outer;
                }
            }
        }
    }

    private void checkQuadrantOpenWorld(int worldQX, int worldQY, int centerQX, int centerQY){
        int localX = worldQX - centerQX + openWorldQuadRadius();
        int localY = worldQY - centerQY + openWorldQuadRadius();

        setQuadSafe(localX, localY, true);

        outer:
        for(int dx = 0; dx < quadsize; dx++){
            for(int dy = 0; dy < quadsize; dy++){
                int tx = worldQX * quadsize + dx;
                int ty = worldQY * quadsize + dy;
                Tile tile = world.tile(tx, ty);

                if(tile == null || tile.solid() || world.pathfinder.getValueforTeam(state.enemyTeam, tx, ty) == Float.MAX_VALUE){
                    setQuadSafe(localX, localY, false);
                    break outer;
                }
            }
        }
    }

    private void reset(WorldLoadEvent event){
        dynamicSpawn = false;
        flySpawns.clear();
        groundSpawns.clear();

        if(world.isOpenWorld()){
            quadrants = new GridBits(openWorldQuadDiameter(), openWorldQuadDiameter());
        }else{
            quadrants = new GridBits(quadWidth(), quadHeight());
        }

        if(world.getSector() == null){
            groups = Waves.getSpawns();
        }else{
            groups = world.getSector().spawns;
        }

        if(world.isOpenWorld()){
            dynamicSpawn = true;
            return;
        }

        dynamicSpawn = true;

        for(int x = 0; x < world.width(); x++){
            for(int y = 0; y < world.height(); y++){
                if(world.tile(x, y).block() == Blocks.spawn){
                    dynamicSpawn = false;
                    GroundSpawn spawn = new GroundSpawn();
                    spawn.x = x/quadsize;
                    spawn.y = y/quadsize;
                    groundSpawns.add(spawn);
                }
            }
        }
    }

    private boolean getQuad(int quadx, int quady){
        return quadrants.get(quadx, quady);
    }

    private boolean getQuadSafe(int quadx, int quady){
        if(quadx < 0 || quady < 0 || quadx >= openWorldQuadDiameter() || quady >= openWorldQuadDiameter()){
            return false;
        }
        return quadrants.get(quadx, quady);
    }

    private void setQuad(int quadx, int quady, boolean valid){
        if(quadrants == null){
            quadrants = new GridBits(quadWidth(), quadHeight());
        }

        if(!Structs.inBounds(quadx, quady, quadWidth(), quadHeight())){
            return;
        }

        quadrants.set(quadx, quady, valid);
    }

    private void setQuadSafe(int quadx, int quady, boolean valid){
        if(quadrants == null) return;
        int diam = openWorldQuadDiameter();
        if(quadx < 0 || quady < 0 || quadx >= diam || quady >= diam) return;
        quadrants.set(quadx, quady, valid);
    }

    private void findLocation(GroundSpawn spawn){
        spawn.x = -1;
        spawn.y = -1;

        if(world.isOpenWorld()){
            int qcx = MathUtils.floor(players[0].x / tilesize / quadsize);
            int qcy = MathUtils.floor(players[0].y / tilesize / quadsize);
            int radius = openWorldQuadRadius();

            for(int r = 0; r <= radius; r++){
                for(int dx = -r; dx <= r; dx++){
                    for(int dy = -r; dy <= r; dy++){
                        if(Math.abs(dx) != r && Math.abs(dy) != r) continue;
                        int localX = dx + radius;
                        int localY = dy + radius;
                        if(getQuadSafe(localX, localY)){
                            spawn.x = qcx + dx;
                            spawn.y = qcy + dy;
                            return;
                        }
                    }
                }
            }

            spawn.x = qcx + Mathf.random(-radius, radius);
            spawn.y = qcy + Mathf.random(-radius, radius);
            return;
        }

        int shellWidth = quadWidth() * 2 + quadHeight() * 2 * 6;
        shellWidth = Math.min(quadWidth() * quadHeight() / 4, shellWidth);

        Mathf.traverseSpiral(quadWidth(), quadHeight(), Mathf.random(shellWidth), (x, y) -> {
            if(getQuad(x, y)){
                spawn.x = x;
                spawn.y = y;
                return true;
            }

            return false;
        });
    }

    private void findLocation(FlyerSpawn spawn){
        spawn.angle = Mathf.random(360f);
    }

    private int quadWidth(){
        return Mathf.ceil(world.width() / (float) quadsize);
    }

    private int quadHeight(){
        return Mathf.ceil(world.height() / (float) quadsize);
    }

    private int openWorldQuadRadius(){
        return 4;
    }

    private int openWorldQuadDiameter(){
        return openWorldQuadRadius() * 2 + 1;
    }

    private class FlyerSpawn{
        //square angle
        float angle;
    }

    private class GroundSpawn{
        //quadrant spawn coordinates (world-space quadrant coords)
        int x, y;
    }
}
