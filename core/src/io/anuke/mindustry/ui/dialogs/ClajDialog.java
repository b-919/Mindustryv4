package io.anuke.mindustry.ui.dialogs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Align;
import io.anuke.mindustry.claj.Claj;
import io.anuke.mindustry.claj.ClajHost;
import io.anuke.mindustry.claj.ClajProtocol;
import io.anuke.mindustry.claj.ClajLink;
import io.anuke.mindustry.core.Platform;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.net.Net;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.scene.event.ClickListener;
import io.anuke.ucore.scene.event.InputEvent;
import io.anuke.ucore.scene.event.Touchable;
import io.anuke.ucore.scene.ui.Dialog;
import io.anuke.ucore.scene.ui.Label;
import io.anuke.ucore.scene.ui.TextField;
import io.anuke.ucore.util.Bundles;

import static io.anuke.mindustry.Vars.ui;

public class ClajDialog extends FloatingDialog{
    private final TextField addressField;
    private final TextField passwordField;
    private final Label status;
    private final Label linkLabel;

    private boolean creating;

    public ClajDialog(){
        super("$text.claj.manage");
        addCloseButton();

        content().defaults().pad(4f);

        content().add("$text.claj.server").colspan(2).left().row();
        addressField = content().addField(Settings.getString("claj-server", "localhost:7000"), text -> {
            Settings.putString("claj-server", text);
            Settings.save();
        }).colspan(2).width(330f).get();
        addressField.setMaxLength(64);
        Platform.instance.addDialog(addressField, 64);
        content().row();

        content().add("$text.claj.server.note").colspan(2).width(330f).wrap().left().padBottom(6f).row();

        content().add("$text.claj.password").left();
        passwordField = content().addField("", new TextField.TextFieldFilter.DigitsOnlyFilter(), text -> {
        }).width(120f).get();
        passwordField.setMaxLength(4);
        passwordField.setMessageText("$text.claj.password.optional");
        Platform.instance.addDialog(passwordField, 4);
        content().row();

        status = new Label("");
        content().add(status).colspan(2).left().row();

        linkLabel = new Label("");
        linkLabel.setWrap(true);
        linkLabel.setTouchable(Touchable.enabled);
        content().add(linkLabel).colspan(2).width(330f).left().color(Palette.accent).row();
        linkLabel.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                copyLink();
            }
        });

        buttons().defaults().size(170f, 54f).pad(3f);
        buttons().addButton("$text.claj.create", this::create).disabled(b -> creating || Claj.isCreatingRoom() || !Net.server() || Claj.hasOpenRoom());
        buttons().addButton("$text.claj.close-room", this::closeRoom).disabled(b -> !Claj.hasOpenRoom());
        buttons().row();
        buttons().addButton("$text.copylink", this::copyLink).disabled(b -> Claj.link() == null).colspan(2).width(346f);

        shown(this::refresh);
    }

    private void refresh(){
        ClajHost host = Claj.host();

        if(host == null || (!host.isCreating() && !host.isRoomCreated())){
            status.setText(Bundles.get("text.claj.none"));
            linkLabel.setText("");
            return;
        }

        if(host.isCreating()){
            status.setText(Bundles.get("text.claj.creating"));
            linkLabel.setText("");
        }else{
            status.setText(Bundles.get("text.claj.room-open"));
            ClajLink link = host.link();
            linkLabel.setText(link == null ? "" : link.toString());
        }
    }

    private void create(){
        if(!Net.server()){
            ui.showError(Bundles.get("text.claj.not-hosting"));
            return;
        }

        String address = addressField.getText().trim();
        if(address.isEmpty()){
            ui.showError(Bundles.get("text.claj.missing-host"));
            return;
        }

        short password = password();
        creating = true;
        refresh();
        ui.loadfrag.show("$text.claj.creating");

        Claj.createRoom(address, password != ClajProtocol.NO_PASSWORD, password, link -> {
            creating = false;
            ui.loadfrag.hide();
            refresh();
            showCreated(link);
        }, reason -> {
            creating = false;
            ui.loadfrag.hide();
            refresh();
            ui.showError(Bundles.format("text.claj.room-closed", reason.name()));
        }, error -> {
            creating = false;
            ui.loadfrag.hide();
            refresh();
            ui.showError(Bundles.format("text.claj.error", message(error)));
        });
    }

    private void closeRoom(){
        Claj.closeRoom();
        refresh();
    }

    private void copyLink(){
        ClajLink link = Claj.link();
        if(link != null) copy(link);
    }

    private void copy(ClajLink link){
        Gdx.app.getClipboard().setContents(link.toString());
        status.setText("$text.claj.copied");
        ui.showInfoFade(Bundles.get("text.claj.copied"));
    }

    private void showCreated(ClajLink link){
        Dialog dialog = new Dialog("$text.claj.room-created.title", "dialog");
        dialog.content().margin(18f);
        dialog.content().setTouchable(Touchable.enabled);

        dialog.content().add("$text.claj.room-created.body").width(330f).wrap().get()
            .setAlignment(Align.center, Align.center);
        dialog.content().row();

        Label linkLabel = dialog.content().add(link.toString()).width(330f).wrap().color(Palette.accent).get();
        linkLabel.setAlignment(Align.center, Align.center);
        dialog.content().row();

        Label hintLabel = dialog.content().add("$text.claj.copy-hint").width(330f).wrap().get();
        hintLabel.setAlignment(Align.center, Align.center);

        if(isLoopback(link.host)){
            dialog.content().row();
            Label warning = dialog.content().add("$text.claj.loopback-warning").width(330f).wrap().get();
            warning.setAlignment(Align.center, Align.center);
            warning.setColor(com.badlogic.gdx.graphics.Color.SCARLET);
        }

        Runnable copyAction = () -> {
            Gdx.app.getClipboard().setContents(link.toString());
            hintLabel.setText("$text.claj.copied");
        };

        // Clicking anywhere in the dialog copies the link.
        dialog.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                copyAction.run();
            }
        });

        dialog.buttons().defaults().size(160f, 54f).pad(4f);
        dialog.buttons().addButton("$text.copylink", copyAction);
        dialog.buttons().addButton("$text.ok", dialog::hide);
        dialog.show();
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

    private boolean isLoopback(String host){
        return host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")
            || host.equals("::1") || host.equals("0.0.0.0");
    }

    private String message(Throwable error){
        if(error == null) return "unknown error";
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }
}
