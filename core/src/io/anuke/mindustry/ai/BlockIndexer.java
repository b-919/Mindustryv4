package io.anuke.mindustry.ai;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.*;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.game.EventType.TileChangeEvent;
import io.anuke.mindustry.game.EventType.WorldLoadEvent;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.game.Teams.TeamData;
import io.anuke.mindustry.maps.generation.ChunkManager.WorldChunk;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.meta.BlockFlag;
import io.anuke.ucore.core.Events;
import io.anuke.ucore.function.Predicate;
import io.anuke.ucore.util.EnumSet;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.ThreadArray;

import static io.anuke.mindustry.Vars.*;

//TODO consider using quadtrees for finding specific types of blocks within an area

/**Class used for indexing special target blocks for AI.*/
@SuppressWarnings("unchecked")
public class BlockIndexer{
    /**Size of one ore quadrant.*/
    private final static int oreQuadrantSize = 20;
    /**Size of one structure quadrant.*/
    private final static int structQuadrantSize = 12;
    /**How many ore anchor candidates findClosestOre refines at most, nearest-first.*/
    private final static int maxOreCandidates = 12;

    /**Set of all ores that are being scanned.*/
    private final ObjectSet<Item> scanOres = new ObjectSet<Item>(){{addAll(Item.getAllOres());}};
    private final ObjectSet<Item> itemSet = new ObjectSet<>();
    /**Stores representative anchor tiles per ore quadrant; keys are absolute quadrant coords so open-world streaming works anywhere.*/
    private ObjectMap<Item, ObjectSet<Tile>> ores;
    /**Tags occupied structure quadrants by team; keys are absolute quadrant coords (packQuad).*/
    private LongSet[] structQuadrants;
    /**Stores all damaged tile entities by team.*/
    private ObjectSet<Tile>[] damagedTiles = new ObjectSet[Team.all.length];

    /**Maps teams to a map of flagged tiles by type.*/
    private ObjectSet<Tile>[][] flagMap = new ObjectSet[Team.all.length][BlockFlag.all.length];
    /**Maps tile positions to their last known tile index data.*/
    private LongMap<TileIndex> typeMap = new LongMap<>();
    /**Empty set used for returning.*/
    private ObjectSet<Tile> emptySet = new ObjectSet<>();
    /**Array used for returning and reusing.*/
    private Array<Tile> returnArray = new ThreadArray<>();
    /**Memoized combined enemy flag arrays [team][flag]; invalidated on tile change and world load.*/
    private Array<Tile>[][] enemyFlagCache = new Array[Team.all.length][BlockFlag.all.length];
    /**Scratch array for sorting ore anchor candidates by distance.*/
    private Array<Tile> candidateArray = new ThreadArray<>();

    public BlockIndexer(){
        Events.on(TileChangeEvent.class, event -> {
            if(typeMap.get(event.tile.packedPosition()) != null){
                TileIndex index = typeMap.get(event.tile.packedPosition());
                for(BlockFlag flag : index.flags){
                    ObjectSet<Tile> set = getFlagged(index.team)[flag.ordinal()];
                    if(set != null) set.remove(event.tile);
                }
            }
            process(event.tile);
            updateQuadrant(event.tile);
            clearEnemyCache();
        });

        Events.on(WorldLoadEvent.class, event -> {
            damagedTiles = new ObjectSet[Team.all.length];
            flagMap = new ObjectSet[Team.all.length][BlockFlag.all.length];

            for(int i = 0; i < flagMap.length; i++){
                for(int j = 0; j < BlockFlag.all.length; j++){
                    flagMap[i][j] = new ObjectSet<>();
                }
            }

            typeMap.clear();
            clearEnemyCache();
            ores = null;

            //create occupied-quadrant sets for each team
            structQuadrants = new LongSet[Team.all.length];
            for(int i = 0; i < Team.all.length; i++){
                structQuadrants[i] = new LongSet();
            }

            //must run first: allocates the ore anchor map used by everything below
            scanOres();

            if(!world.isOpenWorld()){
                //classic maps are fully loaded here; open-world content is indexed per chunk
                for(int x = 0; x < world.width(); x++){
                    for(int y = 0; y < world.height(); y++){
                        Tile tile = world.tile(x, y);

                        process(tile);

                        //map-placed blocks never fire TileChangeEvents during load, so
                        //structure quadrant occupancy must be registered here as well
                        if(tile.entity != null && tile.block().targetable){
                            structQuadrants[tile.getTeam().ordinal()].add(packQuad(
                                Math.floorDiv(tile.x, structQuadrantSize),
                                Math.floorDiv(tile.y, structQuadrantSize)));
                        }

                        if(tile.entity != null && tile.entity.healthf() < 0.9999f){
                            notifyTileDamaged(tile.entity);
                        }
                    }
                }
            }else if(world.chunks() != null){
                //chunks pre-loaded before this event fired missed ore/structure indexing
                for(WorldChunk chunk : world.chunks().getLoadedChunks()){
                    indexChunk(chunk);
                }
            }
        });
    }

    private ObjectSet<Tile>[] getFlagged(Team team){
        return flagMap[team.ordinal()];
    }

    private static long packQuad(int qx, int qy){
        return ((long)qx << 32) | (qy & 0xFFFFFFFFL);
    }

    /** Returns tiles through non-generating access in open world; regular access otherwise. */
    private Tile safeTile(int x, int y){
        if(world.isOpenWorld() && world.chunks() != null){
            return world.chunks().peekTile(x, y);
        }
        return world.tile(x, y);
    }

    /**Returns all damaged tiles by team.*/
    public ObjectSet<Tile> getDamaged(Team team){
        returnArray.clear();

        if(damagedTiles[team.ordinal()] == null){
            damagedTiles[team.ordinal()] = new ObjectSet<>();
        }

        ObjectSet<Tile> set = damagedTiles[team.ordinal()];
        for(Tile tile : set){
            if(tile.entity == null || tile.entity.getTeam() != team || tile.entity.healthf() >= 0.9999f){
                returnArray.add(tile);
            }
        }

        for(Tile tile : returnArray){
            set.remove(tile);
        }

        return set;
    }

    /**Get all allied blocks with a flag.*/
    public ObjectSet<Tile> getAllied(Team team, BlockFlag type){
        return flagMap[team.ordinal()][type.ordinal()];
    }

    /**Get all enemy blocks with a flag. The result is cached and invalidated on tile changes.*/
    public Array<Tile> getEnemy(Team team, BlockFlag type){
        Array<Tile> cached = enemyFlagCache[team.ordinal()][type.ordinal()];
        if(cached != null) return cached;

        Array<Tile> result = new Array<>();
        for(Team enemy : state.teams.enemiesOf(team)){
            if(state.teams.isActive(enemy)){
                for(Tile tile : getFlagged(enemy)[type.ordinal()]){
                    result.add(tile);
                }
            }
        }
        enemyFlagCache[team.ordinal()][type.ordinal()] = result;
        return result;
    }

    /**Drops every memoized enemy flag array; called when a tile change may have altered flag membership.*/
    private void clearEnemyCache(){
        for(int i = 0; i < enemyFlagCache.length; i++){
            for(int j = 0; j < enemyFlagCache[i].length; j++){
                enemyFlagCache[i][j] = null;
            }
        }
    }

    public void notifyTileDamaged(TileEntity entity){
        if(damagedTiles[entity.getTeam().ordinal()] == null){
            damagedTiles[entity.getTeam().ordinal()] = new ObjectSet<>();
        }

        ObjectSet<Tile> set = damagedTiles[entity.getTeam().ordinal()];
        set.add(entity.tile);
    }

    public TileEntity findTile(Team team, float x, float y, float range, Predicate<Tile> pred){
        TileEntity closest = null;
        float dst = 0;

        int halfQuads = Mathf.ceil(range / tilesize / structQuadrantSize);
        int centerQx = Math.floorDiv((int)Math.floor(x / tilesize), structQuadrantSize);
        int centerQy = Math.floorDiv((int)Math.floor(y / tilesize), structQuadrantSize);

        for(int qx = centerQx - halfQuads; qx <= centerQx + halfQuads; qx++){
            for(int qy = centerQy - halfQuads; qy <= centerQy + halfQuads; qy++){

                if(!getQuad(team, qx, qy)) continue;

                for(int tx = qx * structQuadrantSize; tx < (qx + 1) * structQuadrantSize; tx++){
                    for(int ty = qy * structQuadrantSize; ty < (qy + 1) * structQuadrantSize; ty++){
                        Tile other = safeTile(tx, ty);

                        if(other == null) continue;

                        other = other.target();

                        if(other.entity == null || other.getTeam() != team || !pred.test(other) || !other.block().targetable) continue;

                        TileEntity e = other.entity;

                        float ndst = Vector2.dst(x, y, e.x, e.y);
                        if(ndst < range && (closest == null || ndst < dst)){
                            dst = ndst;
                            closest = e;
                        }
                    }
                }
            }
        }

        return closest;
    }

    /**
     * Registers everything AI-relevant in a freshly live chunk: flagged structures, ore
     * deposits, and occupied structure quadrant. Called by the ChunkManager whenever a
     * chunk finishes generating/loading/cold-restoring, since terrain creation and
     * rebuildEntity() do not fire tile events.
     * All operations are idempotent (set-based), so repeated calls are harmless.
     */
    public void indexChunk(WorldChunk chunk){
        if(chunk == null || chunk.tiles == null) return;
        ensureInitialized();

        for(int i = 0; i < chunk.tiles.length; i++){
            Tile tile = chunk.tiles[i];
            processFlags(tile);

            if(tile.block() == Blocks.air && tile.floor().drops != null && scanOres.contains(tile.floor().drops.item)){
                addOreAnchor(tile.floor().drops.item, tile.x, tile.y);
            }

            if(tile.entity != null && tile.block().targetable){
                LongSet quads = structQuadrants[tile.getTeam().ordinal()];
                if(quads != null){
                    quads.add(packQuad(Math.floorDiv(tile.x, structQuadrantSize), Math.floorDiv(tile.y, structQuadrantSize)));
                }
            }

            if(tile.entity != null && tile.entity.healthf() < 0.9999f){
                notifyTileDamaged(tile.entity);
            }
        }
    }

    /**
     * Lazily allocates all indexer state so per-chunk indexing can also run before the
     * first WorldLoadEvent (e.g. open-world chunk creation from a fresh instance).
     * No-op when the WorldLoadEvent handler has already set everything up.
     */
    private void ensureInitialized(){
        if(damagedTiles == null || damagedTiles.length != Team.all.length){
            damagedTiles = new ObjectSet[Team.all.length];
        }

        if(flagMap == null || flagMap.length != Team.all.length){
            flagMap = new ObjectSet[Team.all.length][BlockFlag.all.length];
        }
        for(int i = 0; i < flagMap.length; i++){
            for(int j = 0; j < BlockFlag.all.length; j++){
                if(flagMap[i][j] == null) flagMap[i][j] = new ObjectSet<>();
            }
        }

        if(structQuadrants == null || structQuadrants.length != Team.all.length){
            structQuadrants = new LongSet[Team.all.length];
        }
        for(Team team : Team.all){
            if(structQuadrants[team.ordinal()] == null){
                structQuadrants[team.ordinal()] = new LongSet();
            }
        }

        if(typeMap == null){
            typeMap = new LongMap<>();
        }

        if(ores == null){
            scanOres();
        }
    }

    /**
     * Returns a set of tiles that have ores of the specified type nearby.
     * While each tile in the set is not guaranteed to have an ore directly on it,
     * each tile will at least have an ore within {@link #oreQuadrantSize} / 2 blocks of it.
     * Only specific ore types are scanned. See {@link #scanOres}.
     */
    public ObjectSet<Tile> getOrePositions(Item item){
        if(ores == null) ensureInitialized();
        return ores.get(item, emptySet);
    }

    /**Find the closest ore block relative to a position.*/
    public Tile findClosestOre(float xp, float yp, Item item){
        if(ores == null) return null;

        ObjectSet<Tile> positions = getOrePositions(item);
        if(positions.size == 0) return null;

        if(positions.size == 1){
            return refineOre(positions.first(), item);
        }

        candidateArray.clear();
        for(Tile t : positions){
            candidateArray.add(t);
        }
        candidateArray.sort((a, b) -> {
            float dax = a.worldx() - xp, day = a.worldy() - yp;
            float dbx = b.worldx() - xp, dby = b.worldy() - yp;
            return Float.compare(dax * dax + day * day, dbx * dbx + dby * dby);
        });

        int tries = Math.min(candidateArray.size, maxOreCandidates);
        for(int i = 0; i < tries; i++){
            Tile refined = refineOre(candidateArray.get(i), item);
            if(refined != null) return refined;
        }

        return null;
    }

    private Tile refineOre(Tile anchor, Item item){
        if(anchor == null) return null;

        for(int x = anchor.x - oreQuadrantSize / 2; x < anchor.x + oreQuadrantSize / 2; x++){
            for(int y = anchor.y - oreQuadrantSize / 2; y < anchor.y + oreQuadrantSize / 2; y++){
                Tile res = safeTile(x, y);
                if(res != null && res.block() == Blocks.air && res.floor().drops != null && res.floor().drops.item == item){
                    return res;
                }
            }
        }

        return null;
    }

    /**Registers an ore deposit under its quadrant's anchor tile (absolute coordinates, offset-free).*/
    private void addOreAnchor(Item item, int wx, int wy){
        int qx = Math.floorDiv(wx, oreQuadrantSize);
        int qy = Math.floorDiv(wy, oreQuadrantSize);

        Tile anchor = safeTile(qx * oreQuadrantSize + oreQuadrantSize / 2, qy * oreQuadrantSize + oreQuadrantSize / 2);
        if(anchor == null) anchor = safeTile(wx, wy);
        if(anchor == null) return;

        ores.get(item).add(anchor);
    }

    private void process(Tile tile){
        processFlags(tile);
        processOres(tile);
    }

    private void processFlags(Tile tile){
        if(tile.block().flags != null &&
                tile.getTeam() != Team.none){
            ObjectSet<Tile>[] map = getFlagged(tile.getTeam());

            for(BlockFlag flag : tile.block().flags){

                ObjectSet<Tile> arr = map[flag.ordinal()];

                if(arr == null){
                    arr = new ObjectSet<>();
                    map[flag.ordinal()] = arr;
                }

                arr.add(tile);

                map[flag.ordinal()] = arr;
            }
            typeMap.put(tile.packedPosition(), new TileIndex(tile.block().flags, tile.getTeam()));
        }
    }

    private void processOres(Tile tile){
        if(ores == null) return;

        int qx = Math.floorDiv(tile.x, oreQuadrantSize);
        int qy = Math.floorDiv(tile.y, oreQuadrantSize);
        itemSet.clear();

        //find all items that this quadrant contains
        for(int x = qx * oreQuadrantSize; x < (qx + 1) * oreQuadrantSize; x++){
            for(int y = qy * oreQuadrantSize; y < (qy + 1) * oreQuadrantSize; y++){
                Tile result = safeTile(x, y);
                if(result == null || result.floor().drops == null || !scanOres.contains(result.floor().drops.item)) continue;

                itemSet.add(result.floor().drops.item);
            }
        }

        //update quadrant status depending on whether the item is in it
        for(Item item : scanOres){
            ObjectSet<Tile> set = ores.get(item);

            Tile rounded = safeTile(qx * oreQuadrantSize + oreQuadrantSize / 2, qy * oreQuadrantSize + oreQuadrantSize / 2);
            if(rounded == null) continue;

            if(!itemSet.contains(item)){
                set.remove(rounded);
            }else{
                set.add(rounded);
            }
        }
    }

    private void updateQuadrant(Tile tile){
        if(structQuadrants == null) return;

        int quadrantX = Math.floorDiv(tile.x, structQuadrantSize);
        int quadrantY = Math.floorDiv(tile.y, structQuadrantSize);
        long quadKey = packQuad(quadrantX, quadrantY);

        for(Team team : Team.all){
            TeamData data = state.teams.get(team);
            LongSet quads = structQuadrants[data.team.ordinal()];
            if(quads == null) continue;

            //fast-set this quadrant to 'occupied' if the tile just placed is already of this team
            if(tile.getTeam() == data.team && tile.entity != null && tile.block().targetable){
                quads.add(quadKey);
                continue; //no need to process futher
            }

            quads.remove(quadKey);

            outer:
            for(int x = quadrantX * structQuadrantSize; x < (quadrantX + 1) * structQuadrantSize; x++){
                for(int y = quadrantY * structQuadrantSize; y < (quadrantY + 1) * structQuadrantSize; y++){
                    Tile result = safeTile(x, y);
                    //when a targetable block is found, mark this quadrant as occupied and stop searching
                    if(result != null && result.entity != null && result.getTeam() == data.team){
                        quads.add(quadKey);
                        break outer;
                    }
                }
            }
        }
    }

    private boolean getQuad(Team team, int quadrantX, int quadrantY){
        if(structQuadrants == null || structQuadrants[team.ordinal()] == null) return false;
        return structQuadrants[team.ordinal()].contains(packQuad(quadrantX, quadrantY));
    }

    private void scanOres(){
        ores = new ObjectMap<>();

        //initialize ore map with empty sets
        for(Item item : scanOres){
            ores.put(item, new ObjectSet<>());
        }

        for(int x = 0; x < world.width(); x++){
            for(int y = 0; y < world.height(); y++){
                Tile tile = world.tile(x, y);

                if(tile != null && tile.floor().drops != null && scanOres.contains(tile.floor().drops.item) && tile.block() == Blocks.air){
                    addOreAnchor(tile.floor().drops.item, tile.x, tile.y);
                }
            }
        }
    }

    private class TileIndex{
        public final EnumSet<BlockFlag> flags;
        public final Team team;

        public TileIndex(EnumSet<BlockFlag> flags, Team team){
            this.flags = flags;
            this.team = team;
        }
    }
}
