package io.anuke.mindustry.core;

import com.badlogic.gdx.utils.LongSet;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.Rock;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.modules.Module;
import io.anuke.ucore.util.Geometry;
import io.anuke.ucore.util.Mathf;

public class InfectionManager extends Module {
    private LongSet infectedQueue = new LongSet();
    private LongSet nextQueue = new LongSet();
    private LongSet infectorPositions = new LongSet();
    private LongSet staleInfectors = new LongSet();
    private float timer;
    private static final float INTERVAL = 60f * 1.5f;
    private static final float BASE_CHANCE = 0.05f;
    private static final float NEIGHBOR_MULTIPLIER = 0.15f;

    public void infect(Tile tile) {
        if (tile == null) return;

        if (tile.block().spreadsInfection) {
            register(tile);
            infectedQueue.add(tile.packedPosition());
        }

        if (tile.isInfected) return;

        infectInternal(tile);
        infectedQueue.add(tile.packedPosition());
    }

    /** Registers a tile whose block spreads infection, so it seeds spread from itself.*/
    public void register(Tile tile) {
        if (tile == null || !tile.block().spreadsInfection) return;
        infectorPositions.add(tile.packedPosition());
    }

    public void infectAll(Tile[][] tiles){
        for (Tile[] value : tiles) {
            for (int y = 0; y < tiles[0].length; y++) {
                Tile tile = value[y];
                if (tile == null) continue;

                if (tile.floor().infectedVariant != null) {
                    tile.setFloor(tile.floor().infectedVariant);
                    tile.isInfected = true;
                }

                if (tile.block() instanceof Rock rock) {
                    if (rock.infectedVariant != null) {
                        tile.setBlock(rock.infectedVariant);
                        tile.isInfected = true;
                    }
                }
            }
        }
    }

    private void infectInternal(Tile tile) {
        tile.isInfected = true;

        if (tile.floor().infectedVariant != null) {
            tile.setFloor(tile.floor().infectedVariant);
        }
        
        if (tile.block() instanceof Rock rock) {
            if (rock.infectedVariant != null) {
                tile.setBlock(rock.infectedVariant);
            }
        }
    }

    @Override
    public void update() {
        if(Net.client()) return;
        if (Vars.state.isPaused()) return;

        seedSpreaders();

        if (infectedQueue.size == 0) return;

        timer += Timers.delta();
        if (timer >= INTERVAL) {
            timer = 0;
            spread();
        }
    }

    private void seedSpreaders() {
        if (infectorPositions.size == 0) return;

        LongSet.LongSetIterator it = infectorPositions.iterator();
        while (it.hasNext) {
            Tile source = Vars.world.tile(it.next());
            if (source == null || !source.block().spreadsInfection) continue;

            if (!source.isInfected) {
                infectInternal(source);
            }
            infectedQueue.add(source.packedPosition());
        }
    }

    private void spread() {
        if (infectedQueue.size == 0) return;

        pruneInfectors();
        nextQueue.clear();
        
        LongSet.LongSetIterator it = infectedQueue.iterator();
        while (it.hasNext) {
            long packed = it.next();
            Tile tile = Vars.world.tile(packed);
            if (tile == null) {
                continue;
            }

            boolean hasUninfectedNeighbor = false;

            int start = Mathf.random(7);
            for (int i = 0; i < 8; i++) {
                Tile other = tile.getNearby(Geometry.d8[(i + start) % 8]);
                if (other != null && !other.isInfected) {
                    if (canInfect(other)) {
                        hasUninfectedNeighbor = true;

                        int infectedNeighbors = 0;
                        for (int j = 0; j < 8; j++) {
                            Tile n = other.getNearby(Geometry.d8[j]);
                            if (n != null && n.isInfected) {
                                infectedNeighbors++;
                            }
                        }

                        float chance = BASE_CHANCE + (infectedNeighbors * NEIGHBOR_MULTIPLIER);
                        if (Mathf.chance(chance)) {

                            if (!other.isInfected) {
                                infectInternal(other);
                                nextQueue.add(other.packedPosition());
                                
                                break;
                            }
                        }
                    }
                }
            }

            if (hasUninfectedNeighbor) {
                nextQueue.add(packed);
            }
        }
        
        infectedQueue.clear();
        infectedQueue.addAll(nextQueue);
    }

    private boolean canInfect(Tile tile) {
        if (tile == null || tile.isInfected) return false;
        if (!isWithinRadius(tile)) return false;
        if (tile.floor().infectedVariant != null) return true;
        if (tile.block() instanceof Rock rock) {
            return rock.infectedVariant != null;
        }
        return false;
    }

    private void pruneInfectors() {
        if (infectorPositions.size == 0) return;

        staleInfectors.clear();
        LongSet.LongSetIterator it = infectorPositions.iterator();
        while (it.hasNext) {
            long packed = it.next();
            Tile source = Vars.world.tile(packed);
            if (source == null || !source.block().spreadsInfection) {
                staleInfectors.add(packed);
            }
        }
        if (staleInfectors.size > 0) {
            LongSet.LongSetIterator staleIt = staleInfectors.iterator();
            while (staleIt.hasNext) {
                infectorPositions.remove(staleIt.next());
            }
            staleInfectors.clear();
        }
    }

    private boolean isWithinRadius(Tile tile) {
        LongSet.LongSetIterator it = infectorPositions.iterator();
        while (it.hasNext) {
            Tile source = Vars.world.tile(it.next());
            if (source == null) continue;

            float dx = source.x - tile.x;
            float dy = source.y - tile.y;
            float range = source.block().infectionRadius;

            if (dx * dx + dy * dy <= range * range) {
                return true;
            }
        }
        return false;
    }

    public LongSet getInfectedQueue() {
        return infectedQueue;
    }

    public void reset() {
        infectedQueue.clear();
        nextQueue.clear();
        infectorPositions.clear();
        timer = 0;
    }
}
