package io.anuke.mindustry.content.fx;

import com.badlogic.gdx.graphics.Color;
import io.anuke.mindustry.entities.effect.GroundEffectEntity.GroundEffect;
import io.anuke.mindustry.graphics.DrawPseudo3D;
import io.anuke.mindustry.graphics.Drawf;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.game.ContentList;
import io.anuke.ucore.core.Effects.Effect;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Fill;
import io.anuke.ucore.graphics.Hue;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Tmp;

import static io.anuke.mindustry.Vars.tilesize;

public class BlockFx extends FxList implements ContentList{
    public static Effect reactorsmoke, nuclearsmoke, nuclearcloud, redgeneratespark, generatespark, fuelburn, plasticburn,
    pulverize, pulverizeRed, pulverizeRedder, pulverizeSmall, pulverizeMedium, producesmoke, smeltsmoke, formsmoke, blastsmoke, smokes,
    lava, dooropen, doorclose, dooropenlarge, doorcloselarge, purify, purifyoil, purifystone, generate, mine, mineBig, mineHuge,
    smelt, teleportActivate, teleport, teleportOut, ripple, bubble, commandSend, healBlock, healBlockFull, healWaveMend, overdriveWave,
    overdriveBlockFull, shieldBreak,
    fissionCloud,
    biomassSpore, biomassSmoke;

    @Override
    public void load(){

        reactorsmoke = new Effect(17, e -> {
            Angles.randLenVectors(e.id, 4, e.fin() * 8f, (x, y) -> {
                float size = 1f + e.fout() * 5f;
                Draw.color(Color.LIGHT_GRAY, Color.GRAY, e.fin());
                Draw.rect("circle", e.x + x, e.y + y, size, size);
                Draw.reset();
            });
        });
        nuclearsmoke = new Effect(40, e -> {
            Angles.randLenVectors(e.id, 4, e.fin() * 13f, (x, y) -> {
                float size = e.fslope() * 4f;
                Draw.color(Color.LIGHT_GRAY, Color.GRAY, e.fin());
                Draw.rect("circle", e.x + x, e.y + y, size, size);
                Draw.reset();
            });
        });
        nuclearcloud = new Effect(90, 200f, e -> {
            Angles.randLenVectors(e.id, 10, e.finpow() * 90f, (x, y) -> {
                float size = e.fout() * 14f;
                Draw.color(Color.valueOf("bf92f9"), Color.GRAY, e.fin());
                Draw.rect("circle", e.x + x, e.y + y, size, size);
                Drawf.light(e.x + x, e.y + y, size * 2.5f, 0.5f * e.fout());
                Draw.reset();
            });

        });
        nuclearcloud.emitLight = true;
        nuclearcloud.particleLight = true;
        nuclearcloud.lightRadius = 100f;
        nuclearcloud.lightOpacity = 0.8f;
        nuclearcloud.lightColor = Color.valueOf("bf92f9");

        fissionCloud = new Effect(180, 400f, e -> {
            Angles.randLenVectors(e.id, 35, e.finpow() * 180f, (x, y) -> {
                float size = e.fout() * 28f;
                Draw.color(Color.valueOf("ffd969"), Color.GRAY, e.fin());
                Draw.rect("circle", e.x + x, e.y + y, size, size);
                Drawf.light(e.x + x, e.y + y, size * 2.5f, 0.4f * e.fout());
                Draw.reset();
            });
        });
        fissionCloud.emitLight = true;
        fissionCloud.particleLight = true;
        fissionCloud.lightRadius = 150f;
        fissionCloud.lightOpacity = 0.6f;
        fissionCloud.lightColor = Color.valueOf("ffd969");

        redgeneratespark = new Effect(18, e -> {
            Angles.randLenVectors(e.id, 5, e.fin() * 8f, (x, y) -> {
                float len = e.fout() * 4f;
                Draw.color(Palette.redSpark, Color.GRAY, e.fin());
                //Draw.alpha(e.fout());
                Draw.rect("circle", e.x + x, e.y + y, len, len);
                Draw.reset();
            });
        });
        generatespark = new Effect(18, e -> {
            Angles.randLenVectors(e.id, 5, e.fin() * 8f, (x, y) -> {
                float len = e.fout() * 4f;
                Draw.color(Palette.orangeSpark, Color.GRAY, e.fin());
                Draw.rect("circle", e.x + x, e.y + y, len, len);
                Draw.reset();
            });
        });
        fuelburn = new Effect(23, e -> {
            Angles.randLenVectors(e.id, 5, e.fin() * 9f, (x, y) -> {
                float len = e.fout() * 4f;
                Draw.color(Color.LIGHT_GRAY, Color.GRAY, e.fin());
                Draw.rect("circle", e.x + x, e.y + y, len, len);
                Draw.reset();
            });
        });
        plasticburn = new Effect(40, e -> {
            Angles.randLenVectors(e.id, 5, 3f + e.fin() * 5f, (x, y) -> {
                Draw.color(Color.valueOf("e9ead3"), Color.GRAY, e.fin());
                Fill.circle(e.x + x, e.y + y, e.fout() * 1f);
                Draw.reset();
            });
        });
        pulverize = new Effect(40, e -> {
            Angles.randLenVectors(e.id, 5, 3f + e.fin() * 8f, (x, y) -> {
                Draw.color(Palette.stoneGray);
                Fill.square(e.x + x, e.y + y, e.fout() * 2f + 0.5f, 45);
                Draw.reset();
            });
        });
        pulverizeRed = new Effect(40, e -> {
            Angles.randLenVectors(e.id, 5, 3f + e.fin() * 8f, (x, y) -> {
                Draw.color(Palette.redDust, Palette.stoneGray, e.fin());
                Fill.square(e.x + x, e.y + y, e.fout() * 2f + 0.5f, 45);
                Draw.reset();
            });
        });
        pulverizeRedder = new Effect(40, e -> {
            Angles.randLenVectors(e.id, 5, 3f + e.fin() * 9f, (x, y) -> {
                Draw.color(Palette.redderDust, Palette.stoneGray, e.fin());
                Fill.square(e.x + x, e.y + y, e.fout() * 2.5f + 0.5f, 45);
                Draw.reset();
            });
        });
        pulverizeSmall = new Effect(30, e -> {
            Angles.randLenVectors(e.id, 3, e.fin() * 5f, (x, y) -> {
                Draw.color(Palette.stoneGray);
                Fill.square(e.x + x, e.y + y, e.fout() * 1f + 0.5f, 45);
                Draw.reset();
            });
        });
        pulverizeMedium = new Effect(30, e -> {
            Angles.randLenVectors(e.id, 5, 3f + e.fin() * 8f, (x, y) -> {
                Draw.color(Palette.stoneGray);
                Fill.square(e.x + x, e.y + y, e.fout() * 1f + 0.5f, 45);
                Draw.reset();
            });
        });
        producesmoke = new Effect(12, e -> {
            Angles.randLenVectors(e.id, 8, 4f + e.fin() * 18f, (x, y) -> {
                Draw.color(Color.WHITE, Palette.accent, e.fin());
                Fill.square(e.x + x, e.y + y, 1f + e.fout() * 3f, 45);
                Draw.reset();
            });
        });
        biomassSmoke = new Effect(24, e -> {
            Angles.randLenVectors(e.id, 8, 4f + e.fin() * 18f, (x, y) -> {
                Draw.color(Color.valueOf("331616"), Color.valueOf("510f0f"), e.fin());
                Fill.square(e.x + x, e.y + y, 1f + e.fout() * 3f, 45);
                Draw.reset();
            });
        });
        smeltsmoke = new Effect(15, e -> {
            Angles.randLenVectors(e.id, 6, 4f + e.fin() * 5f, (x, y) -> {
                Draw.color(Color.WHITE, e.color, e.fin());
                Fill.square(e.x + x, e.y + y, 0.5f + e.fout() * 2f, 45);
                Draw.reset();
            });
        });
        formsmoke = new Effect(40, e -> {
            Angles.randLenVectors(e.id, 6, 5f + e.fin() * 8f, (x, y) -> {
                Draw.color(Palette.plasticSmoke, Color.LIGHT_GRAY, e.fin());
                Fill.square(e.x + x, e.y + y, 0.2f + e.fout() * 2f, 45);
                Draw.reset();
            });
        });
        blastsmoke = new Effect(26, e -> {
            Angles.randLenVectors(e.id, 12, 1f + e.fin() * 23f, (x, y) -> {
                float size = 2f + e.fout() * 6f;
                Draw.color(Color.LIGHT_GRAY, Color.DARK_GRAY, e.fin());
                Draw.rect("circle", e.x + x, e.y + y, size, size);
                Draw.reset();
            });
        });
        smokes = new Effect(240, 96f, e -> {
            SmokeData data = e.data instanceof SmokeData ? (SmokeData) e.data : null;
            Color color = data != null ? data.color : e.color;
            float length = data != null ? data.length : 40f;
            float direction = data != null ? data.direction : 180f;
            float size = data != null ? data.size : 3f;
            float sway = data != null ? data.sway : 1.5f;
            float arc = data != null ? data.arc : 1f;
            float speed = data != null ? data.speed : 0.7f;
            float fade = data != null ? data.fade : 35f;
            float step = data != null ? data.step : 6f;
            float path = length * 1.5707963f;
            float randomness = data != null ? data.randomness : 1f;
            float speedJitter = Math.min(0.5f, 0.12f * randomness);
            float arcJitter = 5f * randomness;
            float riseJitter = 0.07f * length * randomness;
            float sizeJitter = 0.35f * randomness;
            float alphaJitter = 0.3f * randomness;
            float windJitter = 6f * randomness;
            float windAmpJitter = 0.7f * randomness;
            float slowestLife = path / (speed * (1f - speedJitter)) + fade;
            float sweep = 90f * arc;
            boolean curve = arc > 0.02f;
            float radius = curve ? length / arc : 0f;
            float t0 = direction + 90f;
            int minI = Math.max((int)((e.time - slowestLife) * speed / step) - 1, 0);
            int maxI = (int)(e.time * speed / step);
            for(int i = minI; i <= maxI; i++){
                float tb = i * step / speed;
                float age = e.time - tb;
                if(age < 0f) continue;
                long seed = e.id * 1000003L + i;
                float pspeed = speed * (1f + Mathf.randomSeedRange(seed, speedJitter));
                float life = path / pspeed + fade;
                if(age > life) continue;
                float fout = 1f - age / life;
                float dist = Math.min(pspeed * age, path);
                float extra = Math.max(pspeed * age - path, 0f);
                float p = dist / path;
                float fadeIn = fade > 0f ? Mathf.clamp(extra / (fade * pspeed)) : 0f;
                float t0p = t0 + Mathf.randomSeedRange(seed + 7, arcJitter);
                float tp = t0p + sweep * p;
                float rise = Mathf.randomSeedRange(seed + 11, riseJitter);
                float ox, oy, side;
                if(curve){
                    ox = radius * (Angles.trnsy(t0p, 1f) - Angles.trnsy(tp, 1f));
                    oy = radius * (Angles.trnsx(tp, 1f) - Angles.trnsx(t0p, 1f)) + rise;
                    side = tp + 90f;
                }else{
                    ox = Angles.trnsx(direction, dist);
                    oy = Angles.trnsy(direction, dist) + rise;
                    side = direction + 90f;
                }
                if(extra > 0f){
                    ox += Angles.trnsx(direction, extra);
                    oy += Angles.trnsy(direction, extra);
                }
                float windPhase = Mathf.randomSeedRange(seed + 19, windJitter);
                float windAmp = sway * (1f + Mathf.randomSeedRange(seed + 23, windAmpJitter));
                float wind = Mathf.sin(p * 360f + e.id * 1.7f + windPhase, 1f, 1f) * windAmp * p * (1f - p)
                        + Mathf.sin(fadeIn * 540f + e.id * 1.7f + windPhase, 1f, 1f) * windAmp * fadeIn * (1f - fadeIn) * 0.5f;
                float gx = e.x + ox + Angles.trnsx(side, wind);
                float gy = e.y + Angles.trnsy(side, wind);
                float ps = size * (0.6f + p * 0.6f) * (1f + Mathf.randomSeedRange(seed + 13, sizeJitter)) * (1f + fadeIn * 0.6f);
                float alpha = Mathf.clamp(0.85f + Mathf.randomSeedRange(seed + 17, alphaJitter));
                float h = DrawPseudo3D.worldHeight(oy);
                DrawPseudo3D.shadow(gx, gy, oy, length, ps, data != null ? data.shadowAlpha : 0.12f);
                float scl = DrawPseudo3D.hScale(h);
                float x = DrawPseudo3D.xHeight(gx, h);
                float y = DrawPseudo3D.yHeight(gy, h) + oy;
                Draw.color(color.r, color.g, color.b, fout * 0.6f * alpha);
                Draw.rect("circle", x, y, ps * scl, ps * scl);
            }
            Draw.reset();
        });
        lava = new Effect(18, e -> {
            Angles.randLenVectors(e.id, 3, 1f + e.fin() * 10f, (x, y) -> {
                float size = e.fslope() * 4f;
                Draw.color(Color.ORANGE, Color.GRAY, e.fin());
                Draw.rect("circle", e.x + x, e.y + y, size, size);
                Draw.reset();
            });
        });
        biomassSpore = new Effect(32, e -> {
            Angles.randLenVectors(e.id, 5, 1f + e.fin() * 10f, (x, y) -> {
                float size = e.fslope() * 4f;
                Draw.color(Color.valueOf("b01616"), Color.valueOf("3a0000"), e.fin());
                Draw.rect("circle", e.x + x, e.y + y, size, size);
                Draw.reset();
            });
        });
        dooropen = new Effect(10, e -> {
            Lines.stroke(e.fout() * 1.6f);
            Lines.square(e.x, e.y, tilesize / 2f + e.fin() * 2f);
            Draw.reset();
        });
        doorclose = new Effect(10, e -> {
            Lines.stroke(e.fout() * 1.6f);
            Lines.square(e.x, e.y, tilesize / 2f + e.fout() * 2f);
            Draw.reset();
        });
        dooropenlarge = new Effect(10, e -> {
            Lines.stroke(e.fout() * 1.6f);
            Lines.square(e.x, e.y, tilesize + e.fin() * 2f);
            Draw.reset();
        });
        doorcloselarge = new Effect(10, e -> {
            Lines.stroke(e.fout() * 1.6f);
            Lines.square(e.x, e.y, tilesize + e.fout() * 2f);
            Draw.reset();
        });
        purify = new Effect(10, e -> {
            Draw.color(Color.ROYAL, Color.GRAY, e.fin());
            Lines.stroke(2f);
            Lines.spikes(e.x, e.y, e.fin() * 4f, 2, 6);
            Draw.reset();
        });
        purifyoil = new Effect(10, e -> {
            Draw.color(Color.BLACK, Color.GRAY, e.fin());
            Lines.stroke(2f);
            Lines.spikes(e.x, e.y, e.fin() * 4f, 2, 6);
            Draw.reset();
        });
        purifystone = new Effect(10, e -> {
            Draw.color(Color.ORANGE, Color.GRAY, e.fin());
            Lines.stroke(2f);
            Lines.spikes(e.x, e.y, e.fin() * 4f, 2, 6);
            Draw.reset();
        });
        generate = new Effect(11, e -> {
            Draw.color(Color.ORANGE, Color.YELLOW, e.fin());
            Lines.stroke(1f);
            Lines.spikes(e.x, e.y, e.fin() * 5f, 2, 8);
            Draw.reset();
        });
        mine = new Effect(20, e -> {
            Angles.randLenVectors(e.id, 6, 3f + e.fin() * 6f, (x, y) -> {
                Draw.color(e.color, Color.LIGHT_GRAY, e.fin());
                Fill.square(e.x + x, e.y + y, e.fout() * 2f, 45);
                Draw.reset();
            });
        });
        mineBig = new Effect(30, e -> {
            Angles.randLenVectors(e.id, 6, 4f + e.fin() * 8f, (x, y) -> {
                Draw.color(e.color, Color.LIGHT_GRAY, e.fin());
                Fill.square(e.x + x, e.y + y, e.fout() * 2f + 0.2f, 45);
                Draw.reset();
            });
        });
        mineHuge = new Effect(40, e -> {
            Angles.randLenVectors(e.id, 8, 5f + e.fin() * 10f, (x, y) -> {
                Draw.color(e.color, Color.LIGHT_GRAY, e.fin());
                Fill.square(e.x + x, e.y + y, e.fout() * 2f + 0.5f, 45);
                Draw.reset();
            });
        });
        smelt = new Effect(20, e -> {
            Angles.randLenVectors(e.id, 6, 2f + e.fin() * 5f, (x, y) -> {
                Draw.color(Color.WHITE, e.color, e.fin());
                Fill.square(e.x + x, e.y + y, 0.5f + e.fout() * 2f, 45);
                Draw.reset();
            });
        });
        teleportActivate = new Effect(50, e -> {
            Draw.color(e.color);

            e.scaled(8f, e2 -> {
                Lines.stroke(e2.fout() * 4f);
                Lines.circle(e2.x, e2.y, 4f + e2.fin() * 27f);
            });

            Lines.stroke(e.fout() * 2f);

            Angles.randLenVectors(e.id, 30, 4f + 40f * e.fin(), (x, y) -> {
                Lines.lineAngle(e.x + x, e.y + y, Mathf.atan2(x, y), e.fin() * 4f + 1f);
            });

            Draw.reset();
        });
        teleport = new Effect(60, e -> {
            Draw.color(e.color);
            Lines.stroke(e.fin() * 2f);
            Lines.circle(e.x, e.y, 7f + e.fout() * 8f);

            Angles.randLenVectors(e.id, 20, 6f + 20f * e.fout(), (x, y) -> {
                Lines.lineAngle(e.x + x, e.y + y, Mathf.atan2(x, y), e.fin() * 4f + 1f);
            });

            Draw.reset();
        });
        teleportOut = new Effect(20, e -> {
            Draw.color(e.color);
            Lines.stroke(e.fout() * 2f);
            Lines.circle(e.x, e.y, 7f + e.fin() * 8f);

            Angles.randLenVectors(e.id, 20, 4f + 20f * e.fin(), (x, y) -> {
                Lines.lineAngle(e.x + x, e.y + y, Mathf.atan2(x, y), e.fslope() * 4f + 1f);
            });

            Draw.reset();
        });
        ripple = new GroundEffect(false, 30, e -> {
            Draw.color(Hue.shift(Tmp.c1.set(e.color), 2, 0.1f));
            Lines.stroke(e.fout() + 0.4f);
            Lines.circle(e.x, e.y, 2f + e.fin() * 4f);
            Draw.reset();
        });

        bubble = new Effect(20, e -> {
            Draw.color(Hue.shift(Tmp.c1.set(e.color), 2, 0.1f));
            Lines.stroke(e.fout() + 0.2f);
            Angles.randLenVectors(e.id, 2, 8f, (x, y) -> {
                Lines.circle(e.x + x, e.y + y, 1f + e.fin() * 3f);
            });
            Draw.reset();
        });

        commandSend = new Effect(28, e -> {
            Draw.color(Palette.command);
            Lines.stroke(e.fout() * 2f);
            Lines.poly(e.x, e.y, 40, 4f + e.finpow() * 120f);
            Draw.color();
        });

        healWaveMend = new Effect(40, e -> {
            Draw.color(e.color);
            Lines.stroke(e.fout() * 2f);
            Lines.poly(e.x, e.y, 30, e.finpow() * e.rotation);
            Draw.color();
        });

        overdriveWave = new Effect(50, e -> {
            Draw.color(e.color);
            Lines.stroke(e.fout() * 1f);
            Lines.poly(e.x, e.y, 30, e.finpow() * e.rotation);
            Draw.color();
        });

        healBlock = new Effect(20, e -> {
            Draw.color(Palette.heal);
            Lines.stroke(2f * e.fout() + 0.5f);
            Lines.square(e.x, e.y, 1f + (e.fin() * e.rotation * tilesize/2f-1f));
            Draw.color();
        });

        healBlockFull = new Effect(20, e -> {
            Draw.color(e.color);
            Draw.alpha(e.fout());
            Fill.square(e.x, e.y, e.rotation * tilesize);
            Draw.color();
        });

        overdriveBlockFull = new Effect(60, e -> {
            Draw.color(e.color);
            Draw.alpha(e.fslope() * 0.4f);
            Fill.square(e.x, e.y, e.rotation * tilesize);
            Draw.color();
        });

        shieldBreak = new Effect(40, e -> {
            Draw.color(Palette.accent);
            Lines.stroke(3f * e.fout());
            Lines.poly(e.x, e.y, 6, e.rotation + e.fin(), 90);
            Draw.reset();
        });
    }
        //bleh
    public static class SmokeData{
        public Color color = Color.WHITE;
        public float length = 40f;
        public float direction = 180f;
        public float size = 3f;
        public float sway = 1.5f;
        public float arc = 1f;
        public float speed = 0.7f;
        public float fade = 35f;
        public float step = 6f;
        public float randomness = 1f;
        public float shadowAlpha = 0.12f;

        public SmokeData(){
        }

        public SmokeData(Color color, float length, float direction, float size){
            this.color = color;
            this.length = length;
            this.direction = direction;
            this.size = size;
        }

        public SmokeData(Color color, float length, float direction, float size, float randomness){
            this(color, length, direction, size);
            this.randomness = randomness;
        }

        public SmokeData(Color color, float length, float direction, float size, float randomness, float shadowAlpha){
            this(color, length, direction, size, randomness);
            this.shadowAlpha = shadowAlpha;
        }

        public float pathLength(){
            return length * 1.5707963f;
        }
    }
}
