package io.anuke.mindustry.entities.units.types;

import io.anuke.mindustry.entities.traits.TypeTrait;
import io.anuke.mindustry.entities.units.FlyingUnit;
import io.anuke.mindustry.io.SaveFileVersion;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Legacy save shim for the missile units.*/
public class MissileFlyer extends FlyingUnit{
    /** First save version that uses the shared {@link FlyingUnit} layout. */
    public static final int weaponByteVersion = 23;

    @Override
    public void writeSave(DataOutput stream) throws IOException{
        if(SaveFileVersion.currentVersion < weaponByteVersion){
            stream.writeByte(type.weapon.id);
        }
        super.writeSave(stream);
    }

    @Override
    public void readSave(DataInput stream) throws IOException{
        if(SaveFileVersion.currentVersion < weaponByteVersion){
            stream.readByte();
        }
        super.readSave(stream);
    }

    @Override
    public int getTypeID(){
        return TypeTrait.typeToID.get(FlyingUnit.class, -1);
    }
}
