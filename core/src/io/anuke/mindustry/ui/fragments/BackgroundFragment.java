package io.anuke.mindustry.ui.fragments;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.mindustry.core.GameState.State;
import io.anuke.mindustry.editor.SpaceCameraDialog;
import io.anuke.mindustry.graphics.MenuBackground;
import io.anuke.ucore.core.Core;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.scene.Group;
import io.anuke.ucore.scene.ui.layout.Unit;

import static io.anuke.mindustry.Vars.state;

public class BackgroundFragment extends Fragment{
    public final MenuBackground background = new MenuBackground();

    @Override
    public void build(Group parent){

        Core.scene.table().addRect((a, b, w, h) -> {
            background.draw(0, 0, w, h);

            boolean portrait = Gdx.graphics.getWidth() < Gdx.graphics.getHeight();
            float logoscl = (int) Unit.dp.scl(7) * (portrait ? 5f / 7f : 1f);
            TextureRegion logo = Core.skin.getRegion("logotext");
            float logow = logo.getRegionWidth() * logoscl;
            float logoh = logo.getRegionHeight() * logoscl;

            Draw.color();
            Core.batch.draw(logo, (int) (w / 2 - logow / 2), (int) (h - logoh + 15 - Unit.dp.scl(portrait ? 30f : 0)), logow, logoh);
        }).visible(() -> state.is(State.menu) && !SpaceCameraDialog.isOpen).grow();
    }
}
