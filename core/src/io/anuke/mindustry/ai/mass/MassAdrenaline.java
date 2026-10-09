package io.anuke.mindustry.ai.mass;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Timers;

/**
 * Per-hive "adrenaline mode". When more than {@link MassAIConfig#ADRENALINE_ENEMY_THRESHOLD} enemies sit inside a
 * hive's radius, that hive enters adrenaline: its queued turrets build faster, nearby biobulbs/tumors produce faster
 * and unit factories run faster
 */
public class MassAdrenaline{
    private static final Array<Tile> active = new Array<>();
    private static final Rectangle rect = new Rectangle();
    private static final int[] countScratch = {0};
    private static float timer;

    private MassAdrenaline(){}

    public static void reset(){
        active.clear();
        timer = 0f;
    }

    public static void update(ObjectSet<Tile> cores){
        timer -= Timers.delta();
        if(timer > 0f) return;
        timer = MassAIConfig.ADRENALINE_INTERVAL;

        active.clear();
        if(cores == null || cores.size == 0) return;

        float radiusWorld = MassAIConfig.ADRENALINE_RADIUS * Vars.tilesize;
        for(Tile core : cores){
            if(core == null) continue;
            if(countEnemies(core.worldx(), core.worldy(), radiusWorld) > MassAIConfig.ADRENALINE_ENEMY_THRESHOLD){
                active.add(core);
            }
        }
    }

    private static int countEnemies(float x, float y, float radius){
        rect.setSize(radius * 2f).setCenter(x, y);
        countScratch[0] = 0;
        Units.getNearbyEnemies(Team.themass, rect, unit -> {
            if(unit != null && unit.isAdded() && !unit.isDead()) countScratch[0]++;
        });
        return countScratch[0];
    }

    public static boolean anyActive(){
        return active.size > 0;
    }

    public static boolean isBonusActiveAt(float worldx, float worldy){
        if(active.size == 0) return false;
        float radiusWorld = MassAIConfig.ADRENALINE_RADIUS * Vars.tilesize;
        float r2 = radiusWorld * radiusWorld;
        for(Tile core : active){
            float dx = core.worldx() - worldx;
            float dy = core.worldy() - worldy;
            if(dx * dx + dy * dy <= r2) return true;
        }
        return false;
    }

    public static boolean isBonusActiveAt(Tile tile){
        return tile != null && isBonusActiveAt(tile.worldx(), tile.worldy());
    }
}
