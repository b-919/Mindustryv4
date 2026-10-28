package io.anuke.mindustry.io;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.core.Platform;
import io.anuke.mindustry.io.versions.Save16;
import io.anuke.mindustry.io.versions.Save17;
import io.anuke.mindustry.io.versions.Save18;
import io.anuke.mindustry.io.versions.Save19;
import io.anuke.mindustry.io.versions.Save20;
import io.anuke.mindustry.maps.campaign.Campaign;

import java.io.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import static io.anuke.mindustry.Vars.*;

public class SaveIO{
    public static final IntArray breakingVersions = IntArray.with(47, 48, 49, 50, 51, 52, 53, 54, 55, 56);
    /**How many rotated backups are kept for each save slot, on top of the save file itself.*/
    public static final int backupCount = 3;
    public static final IntMap<SaveFileVersion> versions = new IntMap<>();
    public static final String CAMPAIGNS_SAVE_FILE = "campaigns.dat";
    private static final int campaignsSaveVersion = 2;

    public static final Array<SaveFileVersion> versionArray = Array.with(
        new Save16(),
        new Save17(),
        new Save18(),
        new Save19(),
        new Save20()
    );

    static{
        for(SaveFileVersion version : versionArray){
            versions.put(version.version, version);
        }
    }
    public void saveCampaigns(Array<Campaign> campaigns){
        FileHandle fileHandle = Platform.instance.getAppDirectory().child(CAMPAIGNS_SAVE_FILE);

        try(DataOutputStream stream = new DataOutputStream(fileHandle.write(false))){
            stream.writeInt(campaignsSaveVersion);
            stream.writeInt(campaigns.size);

            for(Campaign campaign : campaigns){
                stream.writeUTF(campaign.name == null ? "" : campaign.name);
                stream.writeInt(campaign.getCompletedSectors());
            }
        }catch(IOException e){
            throw new RuntimeException("Failed to save campaigns.", e);
        }
    }

    public Array<Campaign> loadCampaigns(){
        FileHandle fileHandle = Platform.instance.getAppDirectory().child(CAMPAIGNS_SAVE_FILE);
        if(!fileHandle.exists()){
            return new Array<>();
        }

        try(DataInputStream stream = new DataInputStream(fileHandle.read())){
            int version = stream.readInt();
            if(version != 1 && version != campaignsSaveVersion){
                return new Array<>();
            }

            int campaignCount = stream.readInt();
            Array<Campaign> campaigns = new Array<>(campaignCount);

            for(int i = 0; i < campaignCount; i++){
                Campaign campaign = new Campaign(stream.readUTF());
                campaign.setCompletedSectors(stream.readInt());
                if(version == 1){
                    int sectorCount = stream.readInt();
                    for(int j = 0; j < sectorCount; j++){
                        stream.readShort();
                        stream.readShort();
                    }
                }
                campaigns.add(campaign);
            }

            return campaigns;
        }catch(IOException e){
            throw new RuntimeException("Failed to load campaigns.", e);
        }
    }

    public static SaveFileVersion getSaveWriter(){
        return versionArray.peek();
    }

    public static void saveToSlot(int slot){
        FileHandle file = fileFor(slot);

        for(int i = backupCount - 1; i > 0; i--){
            FileHandle from = backupFile(file, i - 1);
            if(from.exists()){
                FileHandle to = backupFile(file, i);
                if(to.exists()) to.delete();
                from.moveTo(to);
            }
        }

        FileHandle backup = backupFile(file, 0);
        if(file.exists()){
            if(backup.exists()) backup.delete();
            file.moveTo(backup);
        }

        try{
            write(file);
        }catch(Exception e){
            restoreBestBackup(file);
            throw new RuntimeException(e);
        }
    }

    public static FileHandle backupFile(FileHandle file, int index){
        if(index == 0) return file.sibling(file.name() + "-backup." + file.extension());
        return file.sibling(file.name() + "-backup" + (index + 1) + "." + file.extension());
    }

    /**Restores the newest existing backup to the main save file, clearing any corrupt copies.*/
    public static void restoreBestBackup(FileHandle file){
        if(file.exists()) file.delete();
        for(int i = 0; i < backupCount; i++){
            FileHandle backup = backupFile(file, i);
            if(backup.exists()){
                backup.moveTo(file);
                return;
            }
        }
    }

    public static void loadFromSlot(int slot){
        load(fileFor(slot));
    }

    public static DataInputStream getSlotStream(int slot){
        return new DataInputStream(new InflaterInputStream(fileFor(slot).read()));
    }

    public static boolean isSaveValid(int slot){
        FileHandle file = fileFor(slot);

        for(int i = 0; i <= backupCount; i++){
            FileHandle candidate = i == 0 ? file : backupFile(file, i - 1);
            if(candidate.exists()){
                try{
                    if(isSaveValid(candidate)) return true;
                }catch(Exception e){
                }
            }
        }
        return false;
    }

    public static boolean isSaveValid(FileHandle file){
        return isSaveValid(new DataInputStream(new InflaterInputStream(file.read())));
    }

    public static boolean isSaveValid(DataInputStream stream){

        try{
            int version = stream.readInt();
            SaveFileVersion ver = versions.get(version);
            if(ver == null) return false;
            ver.getData(stream);
            while(true){
                try{
                    stream.readByte();
                }catch(EOFException e){
                    break;
                }
            }
            return true;
        }catch(Exception e){
            return false;
        }
    }

    public static SaveMeta getData(int slot){
        FileHandle file = fileFor(slot);
        RuntimeException last = null;

        for(int i = 0; i <= backupCount; i++){
            FileHandle candidate = i == 0 ? file : backupFile(file, i - 1);
            if(!candidate.exists()) continue;

            try{
                return getData(new DataInputStream(new InflaterInputStream(candidate.read())));
            }catch(Exception e){
                last = new RuntimeException(e);
            }
        }

        throw last != null ? last : new RuntimeException("No save data found for slot " + slot);
    }

    public static SaveMeta getData(DataInputStream stream){

        try{
            int version = stream.readInt();
            SaveMeta meta = versions.get(version).getData(stream);
            stream.close();
            return meta;
        }catch(IOException e){
            throw new RuntimeException(e);
        }
    }

    public static FileHandle fileFor(int slot){
        return saveDirectory.child(slot + "." + Vars.saveExtension);
    }

    public static void write(FileHandle file){
        FileHandle tmpFile = file.sibling(file.name() + ".tmp");
        write(new DeflaterOutputStream(tmpFile.write(false)){
            byte[] tmp = {0};

            public void write(int var1) throws IOException {
                tmp[0] = (byte)(var1 & 255);
                this.write(tmp, 0, 1);
            }
        });
        if(isSaveValid(tmpFile)){
            if(file.exists()) file.delete();
            tmpFile.moveTo(file);
        }else{
            tmpFile.delete();
            throw new RuntimeException("Failed to write a valid save file: " + file.name());
        }
    }

    public static void write(OutputStream os){
        DataOutputStream stream;

        try{
            stream = new DataOutputStream(os);
            SaveFileVersion.currentVersion = getVersion().version;
            getVersion().write(stream);
            stream.close();
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    public static void load(FileHandle file){
        RuntimeException last = null;

        for(int i = 0; i <= backupCount; i++){
            FileHandle candidate = i == 0 ? file : backupFile(file, i - 1);
            if(!candidate.exists()) continue;
            try{
                load(new InflaterInputStream(candidate.read()));
                if(candidate != file && file.exists()){
                    candidate.copyTo(file);
                }
                return;
            }catch(RuntimeException e){
                last = e;
                e.printStackTrace();
            }
        }

        throw new RuntimeException("Failed to load save, no valid file found.", last);
    }

    public static void load(InputStream is){
        logic.reset();

        DataInputStream stream;

        try{
            stream = new DataInputStream(is);
            int version = stream.readInt();
            SaveFileVersion ver = versions.get(version);

            SaveFileVersion.currentVersion = ver.version;
            ver.read(stream);

            stream.close();
        }catch(Exception e){
            content.setTemporaryMapper(null);
            throw new RuntimeException(e);
        }
    }

    public static SaveFileVersion getVersion(){
        return versionArray.peek();
    }
}
