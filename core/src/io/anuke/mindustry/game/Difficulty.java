package io.anuke.mindustry.game;

import io.anuke.ucore.util.Bundles;

public enum Difficulty{
    training(3f, 3f, 2.0f, 0.5f),
    easy(1.4f, 1.5f,1.75f, 0.8f),
    normal(1f, 1f, 1f, 1f),
    hard(0.5f, 0.75f, 0.5f, 1.2f),
    insane(0.25f, 0.5f, 0.25f, 1.4f),
    eradication(0.10f, 0.05f, 0.05f, 1.8f),;

    /**Multiplier of the time between waves.*/
    public final float timeScaling;
    /**Multiplier of spawner grace period.*/
    public final float spawnerScaling;
    /**Multiplier of unit spawning period.*/
    public final float UnitAmountScaling;
    /**Speed multiplier for hive spawner unit production.*/
    public final float massScaling;

    private String value;

    Difficulty(float timeScaling, float spawnerScaling, float UnitAmountScaling, float unitSpawnScaling){
        this.timeScaling = timeScaling;
        this.spawnerScaling = spawnerScaling;
        this.UnitAmountScaling = UnitAmountScaling;
        this.massScaling = unitSpawnScaling;
    }

    @Override
    public String toString(){
        if(value == null){
            value = Bundles.get("setting.difficulty." + name());
        }
        return value;
    }
}
