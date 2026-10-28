package io.anuke.mindustry.game;

import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.Queue;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.util.EnumSet;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.ThreadSet;

/**
 * Class for various team-based utilities.
 */
public class Teams {
    private TeamData[] map = new TeamData[Team.all.length];

    /**
     * Register a team.
     *
     * @param team    The team type enum.
     * @param enemies The array of enemies of this team. Any team not in this array is considered neutral.
     */
    public void add(Team team, Team... enemies) {
        map[team.ordinal()] = new TeamData(team, EnumSet.of(enemies));
    }

    /**
     * Returns team data by type.
     */
    public TeamData get(Team team) {
        if (map[team.ordinal()] == null) {
            //By default, a non-defined team will be enemies of everything.
            Team[] others = new Team[Team.all.length - 1];
            for (int i = 0, j = 0; i < Team.all.length; i++) {
                if (Team.all[i] != team) others[j++] = Team.all[i];
            }
            add(team, others);
        }
        return map[team.ordinal()];
    }

    /**
     * Returns team data by type, or null if the team was never registered.
     */
    public TeamData getOrNull(Team team) {
        return map[team.ordinal()];
    }

    /**
     * Returns whether a team is active, e.g. whether it has any cores remaining.
     */
    public boolean isActive(Team team) {
        //the selected enemy team is always active
        return (!Vars.state.mode.disableWaves && team == Vars.state.enemyTeam) || get(team).cores.size > 0;
    }

    /**
     * Returns a set of all teams that are enemies of this team.
     */
    public EnumSet<Team> enemiesOf(Team team) {
        return get(team).enemies;
    }

    /**
     * Returns whether {@param other} is an enemy of {@param #team}.
     */
    public boolean areEnemies(Team team, Team other) {
        return enemiesOf(team).contains(other);
    }

    public class TeamData {
        public final ObjectSet<Tile> cores = new ThreadSet<>();
        public final EnumSet<Team> enemies;
        public final Team team;
        public final Queue<BlockPlan> plans = new Queue<>();
        public io.anuke.mindustry.ai.RtsAI rtsAI;
        private static final int maxPlans = 1000;

        public TeamData(Team team, EnumSet<Team> enemies) {
            this.team = team;
            this.enemies = enemies;
        }

        /**
         * Adds a plan for a destroyed block, replacing any existing plan at the same position.
         */
        public void addPlan(BlockPlan plan) {
            removePlan(plan.x, plan.y);
            plans.addFirst(plan);

            while (plans.size > maxPlans) {
                plans.removeLast();
            }
        }

        /**
         * Removes the plan at this position, if present.
         */
        public boolean removePlan(int x, int y) {
            for (int i = 0; i < plans.size; i++) {
                BlockPlan plan = plans.get(i);
                if (plan.x == x && plan.y == y) {
                    plans.removeIndex(i);
                    return true;
                }
            }
            return false;
        }

        /**
         * Removes the plan whose block footprint covers this tile, if present.
         */
        public boolean removePlanAt(int x, int y) {
            for (int i = 0; i < plans.size; i++) {
                BlockPlan plan = plans.get(i);
                int offsetx = -(plan.block.size - 1) / 2;
                int offsety = -(plan.block.size - 1) / 2;

                if (x >= plan.x + offsetx && x < plan.x + offsetx + plan.block.size
                        && y >= plan.y + offsety && y < plan.y + offsety + plan.block.size) {
                    plans.removeIndex(i);
                    return true;
                }
            }
            return false;
        }

        /**
         * Removes all plans whose block footprints overlap the given tile rectangle.
         * Returns how many plans were removed.
         */
        public int removePlansInRect(int x1, int y1, int x2, int y2) {
            int minX = Math.min(x1, x2), minY = Math.min(y1, y2);
            int maxX = Math.max(x1, x2), maxY = Math.max(y1, y2);
            int removed = 0;

            for (int i = plans.size - 1; i >= 0; i--) {
                BlockPlan plan = plans.get(i);
                int offsetx = -(plan.block.size - 1) / 2;
                int offsety = -(plan.block.size - 1) / 2;
                int planX1 = plan.x + offsetx;
                int planY1 = plan.y + offsety;
                int planX2 = planX1 + plan.block.size - 1;
                int planY2 = planY1 + plan.block.size - 1;

                if (planX1 <= maxX && planX2 >= minX && planY1 <= maxY && planY2 >= minY) {
                    plans.removeIndex(i);
                    removed++;
                }
            }

            return removed;
        }

        /**
         * Removes all plans covered by the footprint of a block at this position.
         */
        public void removePlansOverlapping(Block block, int x, int y) {
            int offsetx = -(block.size - 1) / 2;
            int offsety = -(block.size - 1) / 2;

            for (int dx = 0; dx < block.size; dx++) {
                for (int dy = 0; dy < block.size; dy++) {
                    removePlan(x + dx + offsetx, y + dy + offsety);
                }
            }
        }

        /* Returns the closest valid plan to a position, or null.
        public BlockPlan findClosestPlan(float x, float y){
            BlockPlan best = null;
            float bestDst = Float.MAX_VALUE;

            for(BlockPlan plan : plans){
                float dst = Mathf.dst(plan.x * Vars.tilesize - x, plan.y * Vars.tilesize - y);
                if(dst < bestDst){
                    bestDst = dst;
                    best = plan;
                }
            }

            return best;
        }*/ // unused code for now
    }

        /**
         * Represents a block made by this team that was destroyed somewhere on the map.
         * Deconstructed blocks are not included.
         */
        public static class BlockPlan {
            public final int x, y, rotation;
            public final Block block;
            public final Object config;

            public BlockPlan(int x, int y, int rotation, Block block, Object config) {
                this.x = x;
                this.y = y;
                this.rotation = rotation;
                this.block = block;
                this.config = config;
            }

            @Override
            public String toString() {
                return "BlockPlan{" + x + ", " + y + ", " + rotation + ", " + block.name + "}";
            }
        }
}