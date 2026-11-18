package io.anuke.mindustry.ui.dialogs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Align;
import com.xpdustry.claj.api.Claj;
import com.xpdustry.claj.api.ClajLink;
import com.xpdustry.claj.api.ClajPinger;
import com.xpdustry.claj.api.ClajProxy;
import io.anuke.mindustry.claj.ClajPublicRelays;
import io.anuke.mindustry.claj.ClajSupport;
import io.anuke.mindustry.core.Platform;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.net.Net;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.scene.event.ClickListener;
import io.anuke.ucore.scene.event.InputEvent;
import io.anuke.ucore.scene.event.Touchable;
import io.anuke.ucore.scene.ui.Dialog;
import io.anuke.ucore.scene.ui.Label;
import io.anuke.ucore.scene.ui.ScrollPane;
import io.anuke.ucore.scene.ui.TextField;
import io.anuke.ucore.scene.ui.layout.Table;
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
        String savedAddress = Settings.getString("claj-server", "");
        if(savedAddress.isEmpty() || !savedAddress.contains(":") || savedAddress.startsWith("localhost")
            || savedAddress.startsWith("127.0.0.1")){
            savedAddress = ClajPublicRelays.defaultAddress();
        }
        addressField = content().addField(savedAddress, text -> {
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

        content().add("$text.claj.public-relays").colspan(2).left().row();
        content().addButton("$text.claj.browse-relays", this::showRelays).colspan(2).width(330f).left().row();

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
        buttons().addButton("$text.claj.create", this::create).disabled(b -> creating || creatingRoom() || !Net.server() || hasRoom());
        buttons().addButton("$text.claj.close-room", this::closeRoom).disabled(b -> !hasRoom());
        buttons().row();
        buttons().addButton("$text.copylink", this::copyLink).disabled(b -> link() == null).colspan(2).width(346f);

        shown(this::refresh);
    }

    private void refresh(){
        if(!creatingRoom() && !hasRoom()){
            status.setText(Bundles.get("text.claj.none"));
            linkLabel.setText("");
            return;
        }

        if(creatingRoom()){
            status.setText(Bundles.get("text.claj.creating"));
            linkLabel.setText("");
        }else{
            status.setText(Bundles.get("text.claj.room-open"));
            ClajLink link = link();
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

        ClajSupport.Address relay;
        try{
            relay = ClajSupport.parseAddress(address);
        }catch(Exception e){
            ui.showError(Bundles.format("text.claj.error", message(e)));
            return;
        }

        short password = password();
        boolean protectedRoom = password != ClajPinger.NO_PASSWORD;

        ClajSupport.init();
        ClajProxy proxy = Claj.get().proxies.get(0);
        proxy.setDefaultConfiguration(false, protectedRoom,
            protectedRoom ? password : ClajPinger.NO_PASSWORD, false);

        creating = true;
        refresh();
        ui.loadfrag.show("$text.claj.creating");

        Claj.get().createRoom(relay.host, relay.port, link -> {
            creating = false;
            ui.loadfrag.hide();
            refresh();
            showCreated(link);
        }, reason -> {
            creating = false;
            ui.loadfrag.hide();
            refresh();
            ui.showError(Bundles.format("text.claj.room-closed", reason == null ? "closed" : reason.name()));
        }, error -> {
            creating = false;
            ui.loadfrag.hide();
            refresh();
            ui.showError(Bundles.format("text.claj.error", message(error)));
        });
    }

    private void showRelays(){
        Dialog dialog = new Dialog("$text.claj.public-relays", "dialog");
        dialog.content().margin(10f);

        Table list = new Table();
        ScrollPane pane = new ScrollPane(list);
        pane.setFadeScrollBars(false);

        for(ClajPublicRelays.Relay relay : ClajPublicRelays.SERVERS){
            list.addButton(relay.name + "\n[lightgray]" + relay.address, "clear", () -> {
                addressField.setText(relay.address);
                dialog.hide();
            }).width(340f).pad(2f).row();
        }

        dialog.content().add(pane).width(360f).height(260f);
        dialog.buttons().defaults().size(160f, 54f).pad(4f);
        dialog.buttons().addButton("$text.cancel", dialog::hide);
        dialog.show();
    }

    private void closeRoom(){
        if(Claj.initialized()) Claj.get().closeRooms();
        refresh();
    }

    private void copyLink(){
        ClajLink link = link();
        if(link != null) copy(link);
    }

    private ClajProxy proxy(){
        return Claj.initialized() ? Claj.get().proxies.getOrNull(0) : null;
    }

    private boolean hasRoom(){
        return Claj.initialized() && Claj.get().hasOpenRoom();
    }

    private boolean creatingRoom(){
        ClajProxy proxy = proxy();
        return proxy != null && proxy.isConnecting();
    }

    private ClajLink link(){
        ClajProxy proxy = proxy();
        return proxy != null && proxy.roomCreated() ? proxy.link() : null;
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
        if(text.isEmpty()) return ClajPinger.NO_PASSWORD;

        try{
            int value = Integer.parseInt(text);
            if(value < 0 || value > 9999) return ClajPinger.NO_PASSWORD;
            return (short)value;
        }catch(NumberFormatException e){
            return ClajPinger.NO_PASSWORD;
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
