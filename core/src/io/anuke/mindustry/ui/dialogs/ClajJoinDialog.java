package io.anuke.mindustry.ui.dialogs;

import io.anuke.mindustry.claj.Claj;
import io.anuke.mindustry.claj.ClajLink;
import io.anuke.mindustry.claj.ClajProtocol;
import io.anuke.mindustry.core.Platform;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.scene.ui.TextField;
import io.anuke.ucore.util.Bundles;

import static io.anuke.mindustry.Vars.netClient;
import static io.anuke.mindustry.Vars.ui;

public class ClajJoinDialog extends FloatingDialog{
    private final TextField linkField;
    private final TextField passwordField;

    public ClajJoinDialog(){
        super("$text.claj.join");
        addCloseButton();

        content().defaults().pad(4f);
        content().add("$text.claj.link").colspan(2).left().row();
        linkField = content().addField(Settings.getString("claj-link", "claj://"), text -> {
            Settings.putString("claj-link", text);
            Settings.save();
        }).colspan(2).width(330f).get();
        linkField.setMaxLength(160);
        Platform.instance.addDialog(linkField, 160);
        content().row();

        content().add("$text.claj.password").left();
        passwordField = content().addField("", new TextField.TextFieldFilter.DigitsOnlyFilter(), text -> {
        }).width(120f).get();
        passwordField.setMaxLength(4);
        passwordField.setMessageText("$text.claj.password.optional");
        Platform.instance.addDialog(passwordField, 4);
        content().row();

        content().add("$text.claj.join.note").colspan(2).width(330f).wrap().left().row();

        buttons().defaults().size(170f, 60f).pad(4f);
        buttons().addButton("$text.cancel", this::hide);
        buttons().addButton("$text.ok", this::join);
    }

    private void join(){
        ClajLink link;
        try{
            link = ClajLink.fromString(linkField.getText());
        }catch(Exception e){
            ui.showError(Bundles.get("text.claj.invalid") + "\n" + e.getMessage());
            return;
        }

        short password = password();
        hide();
        ui.loadfrag.show("$text.claj.joining");
        ui.loadfrag.setButton(() -> {
            ui.loadfrag.hide();
            netClient.disconnectQuietly();
        });

        netClient.beginConnecting();
        Claj.join(link, password, () -> ui.join.hide(), reason -> {
            ui.loadfrag.hide();
            ui.showError(Bundles.get("text.claj.reject." + reason.name(), reason.name()));
        }, error -> {
            ui.loadfrag.hide();
            ui.showError(Bundles.format("text.claj.error", error.getMessage() == null ? error.toString() : error.getMessage()));
        });
    }

    private short password(){
        String text = passwordField.getText().trim();
        if(text.isEmpty()) return ClajProtocol.NO_PASSWORD;

        try{
            int value = Integer.parseInt(text);
            if(value < 0 || value > 9999) return ClajProtocol.NO_PASSWORD;
            return (short)value;
        }catch(NumberFormatException e){
            return ClajProtocol.NO_PASSWORD;
        }
    }
}
