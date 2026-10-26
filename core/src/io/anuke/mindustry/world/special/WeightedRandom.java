package io.anuke.mindustry.world.special;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;

/** Picks a random item, weighted by the value given on insertion. */
public class WeightedRandom<T>{
    float lastValue = 0f;
    Array<T> items = new Array<>();
    FloatArray weights = new FloatArray();

    public void add(T t, float weight){
        if(weight <= 0f) return;
        items.add(t);
        weights.add(lastValue + weight);
        lastValue += weight;
    }

    public T get(){
        double rnd = Math.random() * lastValue;
        int size = items.size;
        for(int i = 0; i < size; i++){
            float lw = i <= 0 ? -1f : weights.get(i - 1);
            float w = weights.get(i);
            if(rnd > lw && rnd <= w){
                return items.get(i);
            }
        }
        return items.random();
    }

    public void clear(){
        items.clear();
        weights.clear();
        lastValue = 0f;
    }
}
