package io.anuke.mindustry.desktop;

import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRPC;
import club.minnced.discord.rpc.DiscordRichPresence;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Base64Coder;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.core.GameState.State;
import io.anuke.mindustry.core.Platform;
import io.anuke.mindustry.game.Difficulty;
import io.anuke.mindustry.game.GameMode;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.maps.missions.WaveExtraMission;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.ui.dialogs.FileChooser;
import io.anuke.ucore.function.Consumer;
import io.anuke.ucore.util.OS;
import io.anuke.ucore.util.Strings;

import java.io.File;
import java.net.NetworkInterface;
import java.util.Enumeration;

import static io.anuke.mindustry.Vars.*;

public class DesktopPlatform extends Platform{
    final static boolean useDiscord = OS.is64Bit;
    final static String applicationId = "398246104468291591";
    String[] args;
    private static File mainDirectory;

    /**Returns the folder the game's main files are located in, based on the jar location rather than the working directory.*/
    public static File getMainDirectory(){
        if(mainDirectory == null){
            try{
                File file = new File(DesktopPlatform.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                if(file.isFile()){
                    file = file.getParentFile();
                }
                mainDirectory = file;
            }catch(Exception e){
                mainDirectory = new File(".");
            }
        }
        return mainDirectory;
    }

    public DesktopPlatform(String[] args){
        this.args = args;

        Vars.testMobile = Array.with(args).contains("-testMobile", false);

        if(useDiscord){
            DiscordEventHandlers handlers = new DiscordEventHandlers();
            DiscordRPC.INSTANCE.Discord_Initialize(applicationId, handlers, true, "");
        }
    }

    @Override
    public void showFileChooser(String text, String content, Consumer<FileHandle> cons, boolean open, String filter){
        String[] extensions = filter.split(",");
        new FileChooser(text, file -> {
            for(String extension : extensions){
                if(file.extension().equalsIgnoreCase(extension)){
                    return true;
                }
            }
            return false;
        }, open, cons).show();
    }

    @Override
    public void updateRPC(){

        if(!useDiscord) return;

        DiscordRichPresence presence = new DiscordRichPresence();

        if(!state.is(State.menu)){
            presence.state = Strings.capitalize(state.mode.name());
            if(world.getMap() == null){
                presence.details = "In Missigno Map";
            }else if(state.mode.disableWaves){
                presence.details = Strings.capitalize(world.getMap().name);
            }else{
                presence.details = Strings.capitalize(world.getMap().name) + " | Wave " + state.wave + " | Difficulty: " + Strings.capitalize(state.difficulty.name());

                presence.largeImageText = "Wave " + state.wave + " | Difficulty: " + Strings.capitalize(state.difficulty.name());
            }

            if(state.mode != GameMode.noWaves){
                presence.state = Strings.capitalize(state.mode.name());
            }else{
                presence.state = unitGroups[players[0].getTeam().ordinal()].size() == 1 ? "1 Unit Active" :
                (unitGroups[players[0].getTeam().ordinal()].size() + " Units Active");
            }
            if(state.mode == GameMode.customAttackMode){
                int enemyCores = 0;

                for(Team enemy : state.teams.enemiesOf(players[0].getTeam())){
                    if(state.teams.isActive(enemy)){
                        enemyCores += state.teams.get(enemy).cores.size;
                    }
                }
                presence.state = " Attack Mode | Remaining Enemy Cores = " + enemyCores + "\n | Difficulty: " + Strings.capitalize(state.difficulty.name());
            }

            if(state.mode == GameMode.SiegeMode){
                presence.details = Strings.capitalize(world.getMap().name);
                presence.state =  " | Siege Survival | Wave " + state.wave + " | Difficulty: " + Strings.capitalize(state.difficulty.name() + " | Enemy Funds = " + WaveExtraMission.displayedFunds);

                presence.largeImageText = "Wave " + state.wave + " | Difficulty: " + Strings.capitalize(state.difficulty.name());

            }

            if(Net.active()){
                presence.partyMax = 16;
                presence.partySize = playerGroup.size();
            }
        }else{
            if(ui.editor != null && ui.editor.isShown()){
                presence.state = "In Editor";
            }else{
                presence.state = "In Menu";
            }
        }

        presence.largeImageKey = "logo";

        DiscordRPC.INSTANCE.Discord_UpdatePresence(presence);
    }

    @Override
    public void onGameExit(){
        if(useDiscord) DiscordRPC.INSTANCE.Discord_Shutdown();
    }

    @Override
    public String getUUID(){
        try{
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
            NetworkInterface out;
            for(out = e.nextElement(); (out.getHardwareAddress() == null || !validAddress(out.getHardwareAddress())) && e.hasMoreElements(); out = e.nextElement()) ;

            byte[] bytes = out.getHardwareAddress();
            byte[] result = new byte[8];
            System.arraycopy(bytes, 0, result, 0, bytes.length);

            String str = new String(Base64Coder.encode(result));

            if(str.equals("AAAAAAAAAOA=")) throw new RuntimeException("Bad UUID.");

            return str;
        }catch(Exception e){
            return super.getUUID();
        }
    }

    @Override
    public FileHandle getAppDirectory(){
        return Gdx.files.absolute(getMainDirectory().getAbsolutePath());
    }

    private boolean validAddress(byte[] bytes){
        if(bytes == null) return false;
        byte[] result = new byte[8];
        System.arraycopy(bytes, 0, result, 0, bytes.length);
        return !new String(Base64Coder.encode(result)).equals("AAAAAAAAAOA=");
    }
}
