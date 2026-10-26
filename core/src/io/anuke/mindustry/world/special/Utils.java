package io.anuke.mindustry.world.special;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.bullet.Bullet;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.entities.EntityGroup;
import io.anuke.ucore.function.Consumer;
import io.anuke.ucore.function.Predicate;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.SeedRandom;

import static io.anuke.mindustry.Vars.tilesize;

/**
 * Ported helpers from FlameOutV8, adapted to the v4/uCore API.
 */
public class Utils{
    public static final Rectangle r = new Rectangle(), r2 = new Rectangle();
    public static final Vector2 v = new Vector2(), vv = new Vector2(), v2 = new Vector2(), v3 = new Vector2();
    public static final SeedRandom rand = new SeedRandom(), rand2 = new SeedRandom();

    public static float biasSlope(float fin, float bias){
        return (fin < bias ? (fin / bias) : 1f - (fin - bias) / (1f - bias));
    }

    /**Distance between two points.*/
    public static float dst(float x1, float y1, float x2, float y2){
        return Vector2.dst(x1, y1, x2, y2);
    }

    /**Squared distance between two points.*/
    public static float dst2(float x1, float y1, float x2, float y2){
        return Vector2.dst2(x1, y1, x2, y2);
    }

    /**Whether the second point is within the given radius of the first.*/
    public static boolean within(float x1, float y1, float x2, float y2, float radius){
        return dst2(x1, y1, x2, y2) <= radius * radius + 0.0001f;
    }

    /**Whether the angle is within the given degrees of the other angle.*/
    public static boolean angleWithin(float angle1, float angle2, float range){
        return Mathf.angNear(angle1, angle2, range);
    }

    /**The angle subtended by an object of the given size at the given distance, in degrees.*/
    public static float angleSize(float dst, float size){
        if(dst <= size) return 180f;
        return (float)(2.0 * Math.asin(Math.min(1f, size / dst)) * (180.0 / Math.PI));
    }

    /**Hit radius of a unit.*/
    public static float hitSize(BaseUnit unit){
        return unit == null ? 0f : unit.getSize();
    }

    /**Hit radius of a building.*/
    public static float hitSize(TileEntity build){
        if(build == null) return 0f;
        Tile tile = build.tile;
        if(tile == null || tile.block() == null) return tilesize / 2f;
        return tile.block().size * tilesize;
    }

    /**Iterates over all units belonging to the given team, or all teams if null.*/
    public static void forEachUnit(Team team, Consumer<BaseUnit> cons){
        if(team == null){
            for(EntityGroup<BaseUnit> group : Vars.unitGroups){
                if(group == null) continue;
                group.forEach(cons);
            }
        }else{
            EntityGroup<BaseUnit> group = Vars.unitGroups[team.ordinal()];
            if(group == null) return;
            group.forEach(cons);
        }
    }

    /**Iterates over all buildings belonging to the given team.*/
    public static void forEachBuilding(Team team, Consumer<TileEntity> cons){
        if(Vars.tileGroup == null) return;
        Vars.tileGroup.forEach(build -> {
            if(team != null && build.getTeam() != team) return;
            cons.accept(build);
        });
    }

    /**Iterates over all bullets belonging to the given team.*/
    public static void forEachBullet(Team team, Consumer<Bullet> cons){
        if(Vars.bulletGroup == null) return;
        Vars.bulletGroup.forEach(bullet -> {
            if(team != null && bullet.getTeam() != team) return;
            cons.accept(bullet);
        });
    }

    /**Scans a cone of units belonging to the given team.*/
    public static void scanConeUnits(Team team, float x, float y, float rotation, float length, float spread, Consumer<BaseUnit> cons){
        forEachUnit(team, unit -> {
            float mx = unit.getX() - x, my = unit.getY() - y;
            float size = hitSize(unit) / 2f;
            float bounds = size + length;
            float d2 = mx * mx + my * my;
            if(d2 < bounds * bounds && angleWithin(rotation, Mathf.atan2(mx, my), spread + angleSize(Mathf.sqrt(d2), size))){
                cons.accept(unit);
            }
        });
    }

    /**Scans a cone of buildings belonging to the given team.*/
    public static void scanConeBuildings(Team team, float x, float y, float rotation, float length, float spread, Consumer<TileEntity> cons){
        forEachBuilding(team, build -> {
            float mx = build.getX() - x, my = build.getY() - y;
            float size = hitSize(build) / 2f;
            float bounds = size + length;
            float d2 = mx * mx + my * my;
            if(d2 < bounds * bounds && angleWithin(rotation, Mathf.atan2(mx, my), spread + angleSize(Mathf.sqrt(d2), size))){
                cons.accept(build);
            }
        });
    }

    /**Scans a cone of bullets belonging to the given team.*/
    public static void scanConeBullets(Team team, float x, float y, float rotation, float length, float spread, Consumer<Bullet> cons){
        forEachBullet(team, bullet -> {
            float mx = bullet.getX() - x, my = bullet.getY() - y;
            float d2 = mx * mx + my * my;
            if(d2 < length * length && angleWithin(rotation, Mathf.atan2(mx, my), spread)){
                cons.accept(bullet);
            }
        });
    }

    /**Finds the first intersection of a line with a circle, or null.*/
    public static Vector2 intersectCircle(float x1, float y1, float x2, float y2, float cx, float cy, float cr){
        Intersector.nearestSegmentPoint(x1, y1, x2, y2, cx, cy, v2);
        if(v2.dst(cx, cy) > cr + 0.0001f) return null;

        float rx = x1 - cx, ry = y1 - cy;
        float vx = x2 - x1, vy = y2 - y1;
        float a = vx * vx + vy * vy;
        float b = 2f * (vx * rx + vy * ry);
        float c = rx * rx + ry * ry - cr * cr;
        float det = b * b - 4f * a * c;

        if(a <= 0.0000001f || det < 0f) return null;
        if(Math.abs(det) < 0.0000001f){
            float t = -b / (2f * a);
            return v.set(x1 + t * vx, y1 + t * vy);
        }
        det = Mathf.sqrt(det);
        float t1 = (-b - det) / (2f * a);
        return v.set(x1 + t1 * vx, y1 + t1 * vy);
    }

    /**Finds the closest intersection of a line with a rectangle, or null.*/
    public static Vector2 intersectRect(float x1, float y1, float x2, float y2, Rectangle rect){
        boolean intersected = false;
        float nearX = 0f, nearY = 0f, lastDst2 = 0f;

        for(int i = 0; i < 4; i++){
            int mod = i % 2;
            float rx1 = i < 2 ? (rect.x + rect.width * mod) : rect.x;
            float rx2 = i < 2 ? (rect.x + rect.width * mod) : rect.x + rect.width;
            float ry1 = i < 2 ? rect.y : (rect.y + rect.height * mod);
            float ry2 = i < 2 ? rect.y + rect.height : (rect.y + rect.height * mod);

            if(Intersector.intersectSegments(x1, y1, x2, y2, rx1, ry1, rx2, ry2, vv)){
                float d2 = Vector2.dst2(x1, y1, vv.x, vv.y);
                if(!intersected || d2 < lastDst2){
                    nearX = vv.x;
                    nearY = vv.y;
                    lastDst2 = d2;
                }
                intersected = true;
            }
        }

        if(rect.contains(x1, y1)){
            nearX = x1;
            nearY = y1;
            intersected = true;
        }

        return intersected ? v2.set(nearX, nearY) : null;
    }

    /**Damageable target wrapper used by laser-style attacks.*/
    public static class Hit{
        public Object entity;
        public float x, y;
        public float health, maxHealth, hitSize;
        public Team team;

        public Hit(Object entity, float x, float y, float health, float maxHealth, float hitSize, Team team){
            this.entity = entity;
            this.x = x;
            this.y = y;
            this.health = health;
            this.maxHealth = maxHealth;
            this.hitSize = hitSize;
            this.team = team;
        }
    }

    /**Handler for line intersections.*/
    public interface LineHitHandler<T>{
        void get(T t, float x, float y);
    }

    /**
     * Collects all units and buildings of enemy teams hit by a laser-like line.
     * @return the distance traveled along the line before the first blocking target.
     */
    public static float hitLaser(Team team, float width, float x1, float y1, float x2, float y2, Predicate<Hit> within, Predicate<Hit> stop, LineHitHandler<Hit> cons){
        Array<Hit> hits = new Array<>();
        float length = dst(x1, y1, x2, y2);

        forEachUnit(null, unit -> {
            if(unit.getTeam() == team) return;
            Hit hit = new Hit(unit, unit.getX(), unit.getY(), unit.health, unit.maxHealth(), hitSize(unit), unit.getTeam());
            if(within != null && !within.test(hit)) return;
            Vector2 point = intersectCircle(x1, y1, x2, y2, unit.getX(), unit.getY(), hitSize(unit) / 2f + width / 2f);
            if(point != null){
                hit.x = point.x;
                hit.y = point.y;
                hits.add(hit);
            }
        });

        forEachBuilding(null, build -> {
            if(build.getTeam() == team) return;
            Hit hit = new Hit(build, build.getX(), build.getY(), build.health, build.maxHealth(), hitSize(build), build.getTeam());
            if(within != null && !within.test(hit)) return;
            Vector2 point = intersectCircle(x1, y1, x2, y2, build.getX(), build.getY(), hitSize(build) / 2f + width / 2f);
            if(point != null){
                hit.x = point.x;
                hit.y = point.y;
                hits.add(hit);
            }
        });

        hits.sort((a, b) -> Float.compare(dst2(x1, y1, a.x, a.y), dst2(x1, y1, b.x, b.y)));

        float result = length;
        for(Hit hit : hits){
            cons.get(hit, hit.x, hit.y);
            if(stop != null && stop.test(hit)){
                result = dst(x1, y1, hit.x, hit.y) - hit.hitSize / 4f;
                break;
            }
        }
        return result;
    }

    /**Signed difference between two angles, in degrees, in the range (-180, 180].*/
    public static float angleDistSigned(float a, float b){
        a = ((a % 360f) + 360f) % 360f;
        b = ((b % 360f) + 360f) % 360f;
        float d = ((a - b + 540f) % 360f) - 180f;
        return d;
    }
}
