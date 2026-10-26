package io.anuke.mindustry.world.special;

import io.anuke.ucore.io.*;
import io.anuke.mindustry.entities.*;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.gen.Call;

@SuppressWarnings("unchecked")
public abstract class BasicEntity implements Entityc{
    protected boolean added;
    protected int id = EntityGroup.nextId();

    @Override
    public void update(){

    }

    @Override
    public <T extends Entityc> T self(){
        return (T)this;
    }

    @Override
    public <T> T as(){
        return (T)this;
    }

    @Override
    public boolean isAdded(){
        return added;
    }

    @Override
    public boolean isLocal(){
        return false;
    }

    @Override
    public boolean isRemote(){
        return false;
    }

    public boolean isNull(){
        return false;
    }

    @Override
    public boolean serialize(){
        return false;
    }
    @Override
    public int classId(){
        return 0;
    }

    @Override
    public void beforeWrite(){

    }

    @Override
    public void read(Reads reads){

    }
    @Override
    public void write(Writes writes){

    }
    @Override
    public void afterRead(){

    }
    @Override
    public void afterReadAll(){

    }

    @Override
    public int id(){
        return id;
    }
    @Override
    public void id(int i){
        id = i;
    }

    @Override
    public void add(){
        if(added) return;

        addGroup();
        added = true;
    }
    protected void addGroup(){
        Groups.all.add(this);
    }

    @Override
    public void remove(){
        if(!added) return;

        removeGroup();
        added = false;
    }
    protected void removeGroup(){
        Groups.all.remove(this);
    }
}
