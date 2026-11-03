package io.anuke.mindustry;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Base64Coder;
import io.anuke.kryonet.KryoClient;
import io.anuke.kryonet.KryoServer;
import io.anuke.mindustry.core.Platform;
import io.anuke.mindustry.game.Saves.SaveSlot;
import io.anuke.mindustry.io.SaveIO;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.ui.dialogs.FileChooser;
import io.anuke.ucore.function.Consumer;
import io.anuke.ucore.scene.ui.layout.Unit;
import io.anuke.ucore.util.Bundles;
import io.anuke.ucore.util.Strings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import static io.anuke.mindustry.Vars.*;

public class AndroidLauncher extends PatchedAndroidApplication {
    public static final int PERMISSION_REQUEST_CODE = 1;
    boolean doubleScaleTablets = true;
    FileChooser chooser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        migrateData();

        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        Platform.instance = new Platform() {


            @Override
            public String getUUID() {
                try {
                    String s = Secure.getString(getContext().getContentResolver(), Secure.ANDROID_ID);
                    int len = s.length();
                    byte[] data = new byte[len / 2];
                    for (int i = 0; i < len; i += 2) {
                        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                + Character.digit(s.charAt(i + 1), 16));
                    }
                    String result = new String(Base64Coder.encode(data));
                    if (result.equals("AAAAAAAAAOA=")) throw new RuntimeException("Bad UUID.");
                    return result;
                } catch (Exception e) {
                    return super.getUUID();
                }
            }

            @Override
            public void shareFile(FileHandle file) {
            }

            @Override
            public void showFileChooser(String text, String content, Consumer<FileHandle> cons, boolean open, String filetype) {
                String[] extensions = filetype.split(",");
                chooser = new FileChooser(text, file -> {
                    for(String extension : extensions){
                        if(file.extension().equalsIgnoreCase(extension)){
                            return true;
                        }
                    }
                    return false;
                }, open, cons);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                        checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
                    chooser.show();
                    chooser = null;
                } else {
                    ArrayList<String> perms = new ArrayList<>();
                    if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    }
                    if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                    }
                    requestPermissions(perms.toArray(new String[perms.size()]), PERMISSION_REQUEST_CODE);
                }
            }

            @Override
            public void beginForceLandscape() {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }

            @Override
            public void endForceLandscape() {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            }

            @Override
            public boolean canDonate() {
                return true;
            }
        };

        if (doubleScaleTablets && isTablet(this.getContext())) {
            Unit.dp.addition = 0.5f;
        }
        // config.hideStatusBar = true;
        Net.setClientProvider(new KryoClient());
        Net.setServerProvider(new KryoServer());
        initialize(new Mindustry(), config);
        checkFiles(getIntent());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i : grantResults) {
                if (i != PackageManager.PERMISSION_GRANTED) return;
            }
            if (chooser != null) {
                chooser.show();
            }
        }
    }

    private void checkFiles(Intent intent) {
        try {
            Uri uri = intent.getData();
            if (uri != null) {
                File myFile = null;
                String scheme = uri.getScheme();
                if (scheme == null) return;
                if (scheme.equals("file")) {
                    String fileName = uri.getEncodedPath();
                    myFile = new File(fileName);
                } else if (!scheme.equals("content")) {
                    return;
                }
                String path = uri.getPath();
                if (path == null) return;
                boolean save = path.endsWith(saveExtension);
                boolean map = path.endsWith(mapExtension);
                InputStream inStream;
                if (myFile != null) inStream = new FileInputStream(myFile);
                else inStream = getContentResolver().openInputStream(uri);
                if (inStream == null) return;
                Gdx.app.postRunnable(() -> {
                    if (save) {
                        System.out.println("Opening save.");
                        FileHandle file = Gdx.files.local("temp-save." + saveExtension);
                        file.write(inStream, false);
                        if (SaveIO.isSaveValid(file)) {
                            try {
                                SaveSlot slot = control.saves.importSave(file);
                                ui.load.runLoadSave(slot);
                            } catch (IOException e) {
                                ui.showError(Bundles.format("text.save.import.fail", Strings.parseException(e, false)));
                            }
                        } else {
                            ui.showError("$text.save.import.invalid");
                        }
                    } else if (map) {
                        Gdx.app.postRunnable(() -> {
                            System.out.println("Opening map.");
                            if (!ui.editor.isShown()) {
                                ui.editor.show();
                            }
                            ui.editor.beginEditMap(inStream);
                        });
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isPackageInstalled(String packagename) {
        try {
            getPackageManager().getPackageInfo(packagename, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTablet(Context context) {
        TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return manager.getPhoneType() == TelephonyManager.PHONE_TYPE_NONE;
    }

    private void migrateData() {
        try {
            File oldDir = new File(Environment.getExternalStorageDirectory(), "MindustryV4");
            File newDir = getExternalFilesDir(null);

            if (newDir != null && oldDir.exists() && oldDir.isDirectory() && (!newDir.exists() || newDir.list() == null || newDir.list().length == 0)) {
                moveRecursive(oldDir, newDir);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void moveRecursive(File source, File target) {
        if (source.isDirectory()) {
            if (!target.exists()) {
                target.mkdirs();
            }

            String[] children = source.list();
            if (children != null) {
                for (String child : children) {
                    moveRecursive(new File(source, child), new File(target, child));
                }
            }
            source.delete();
        } else {
            File targetDir = target.getParentFile();
            if (targetDir != null && !targetDir.exists()) {
                targetDir.mkdirs();
            }

            if (source.renameTo(target)) {
                // Success
            } else {
                // Fallback to copy if rename fails
                try {
                    copyFile(source, target);
                    source.delete();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void copyFile(File source, File target) throws IOException {
        try (InputStream in = new FileInputStream(source);
             java.io.OutputStream out = new java.io.FileOutputStream(target)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }
}
