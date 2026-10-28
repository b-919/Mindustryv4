package io.anuke.mindustry.io.versions;

import io.anuke.mindustry.game.MappableContent;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


public class Save20 extends Save19{
    private MappableContent[][] contentMapper;

    public Save20(){
        super(20);
    }

    public Save20(int version){
        super(version);
    }

    @Override
    public MappableContent[][] readContentHeader(DataInputStream stream) throws IOException{
        contentMapper = super.readContentHeader(stream);
        return contentMapper;
    }

    @Override
    protected void writeVersionData(DataOutputStream stream) throws IOException{
        writePlans(stream);
    }

    @Override
    protected void readVersionData(DataInputStream stream) throws IOException{
        readPlans(stream, contentMapper);
    }
}
