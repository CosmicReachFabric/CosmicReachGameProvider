package net.cosmicreachfabric.cosmicreach.provider.mixin;

import finalforeach.cosmicreach.gamestates.MainMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(MainMenu.class)
public class MainMenuMixin {

    private static final String MODDED = "/Fabric (Modded)";

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lfinalforeach/cosmicreach/ui/FontRenderer;getTextDimensions(Lcom/badlogic/gdx/utils/viewport/Viewport;Ljava/lang/String;Lcom/badlogic/gdx/math/Vector2;)Lcom/badlogic/gdx/math/Vector2;", ordinal = 2), index = 1)
    public String setFabricVersionOnDimensions(String version) {
        return version + MODDED;
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lfinalforeach/cosmicreach/ui/FontRenderer;drawText(Lcom/badlogic/gdx/graphics/g2d/SpriteBatch;Lcom/badlogic/gdx/utils/viewport/Viewport;Ljava/lang/String;FFLfinalforeach/cosmicreach/ui/HorizontalAnchor;Lfinalforeach/cosmicreach/ui/VerticalAnchor;)V", ordinal = 5), index = 2)
    public String setFabricVersionOnShadow(String version) {
        return version + MODDED;
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lfinalforeach/cosmicreach/ui/FontRenderer;drawText(Lcom/badlogic/gdx/graphics/g2d/SpriteBatch;Lcom/badlogic/gdx/utils/viewport/Viewport;Ljava/lang/String;FFLfinalforeach/cosmicreach/ui/HorizontalAnchor;Lfinalforeach/cosmicreach/ui/VerticalAnchor;)V", ordinal = 6), index = 2)
    public String setFabricVersionOnText(String version) {
        return version + MODDED;
    }
}
