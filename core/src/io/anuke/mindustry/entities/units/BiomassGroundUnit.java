package io.anuke.mindustry.entities.units;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.mindustry.entities.Units;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Mathf;

public class BiomassGroundUnit extends GroundUnit{

    public final UnitState
    patrol = new UnitState(){
        public void update(){
            if(retarget()){
                targetClosest();

                if(target != null && !Units.invalidateTarget(target, team, x, y)){
                    state.set(pursue);
                    return;
                }
            }

            BiomassGroundUnit.super.patrol.update();
        }
    },
    pursue = new UnitState(){
        public void update(){
            if(Units.invalidateTarget(target, team, x, y) || distanceTo(target) > getType().pursueRange){
                target = null;
                onCommand(getCommand());
            }else{
                if(distanceTo(target) > getWeapon().getAmmo().getRange() * 0.8f){
                    moveTo(target.getX(), target.getY());
                }
            }
        }
    };

    @Override
    public void onCommand(UnitCommand command){
        state.set(command == UnitCommand.retreat ? retreat :
                command == UnitCommand.attack ? attack :
                command == UnitCommand.patrol ? patrol :
                null);
    }

    @Override
    public void drawStats(){
        float hf = healthf();
        float frequency = 1f + (1f - hf) * 3f;
        float amplitude = 0.1f + (1f - hf) * 0.15f;

        float scale = 1f + Mathf.sin(Timers.time() * frequency, 2f, amplitude);

        Draw.color(Color.BLACK, team.color, hf + Mathf.absin(Timers.time(), hf * 5f, 1f - hf));
        Draw.alpha(hitTime);
        Draw.rect(getPowerCellRegion(), x, y,
                getPowerCellRegion().getRegionWidth() * scale,
                getPowerCellRegion().getRegionHeight() * scale,
                rotation - 90);
        Draw.color();
    }

    @Override
    public TextureRegion getPowerCellRegion(){
        if(type.hitsize > 10f)return Draw.region("biomass-heart");
        else  return Draw.region("small-biomass-heart");
    }
}
