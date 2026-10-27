package io.anuke.mindustry.graphics.bloom;

import com.badlogic.gdx.graphics.Color;
import io.anuke.mindustry.entities.bullet.Bullet;
import io.anuke.mindustry.entities.bullet.BulletType;
import io.anuke.mindustry.entities.effect.Lightning;
import io.anuke.mindustry.graphics.BloomLayer;
import io.anuke.mindustry.graphics.BloomLayers;
import io.anuke.ucore.entities.trait.PosTrait;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Lines;

import static io.anuke.mindustry.Vars.*;

/** Bloom from bullets flagged with {@link BulletType#bloom}, plus lightning arcs. */
public class BulletBloomLayer implements BloomLayer{
    @Override
    public String name(){
        return BloomLayers.BULLETS;
    }

    @Override
    public int priority(){
        return 20;
    }

    @Override
    public void draw(){
        //bulletGroup also contains Lightning, so iterate the erased array
        for(Object entity : bulletGroup.allRaw()){
            if(entity instanceof Bullet bullet){
                BulletType type = bullet.getBulletType();
                if(type.bloom){
                    type.drawBloom(bullet);
                }
            }else if(entity instanceof Lightning l){
                float fade = l.fout();
                if(fade > 0.01f){
                    Draw.color(l.color, Color.WHITE, fade);
                    Lines.stroke(fade * 6f);
                    Draw.alpha(0.4f);
                    float lx = l.x, ly = l.y;
                    for(int i = 0; i < l.lines.size; i++){
                        PosTrait v = l.lines.get(i);
                        Lines.line(lx, ly, v.getX(), v.getY());
                        lx = v.getX();
                        ly = v.getY();
                    }
                    Draw.reset();
                }
            }
        }
    }
}
