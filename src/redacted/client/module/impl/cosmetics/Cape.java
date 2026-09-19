package redacted.client.module.impl.cosmetics;

import net.minecraft.util.ResourceLocation;
import org.lwjglx.input.Keyboard;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.value.impl.ModeValue;

import java.util.HashMap;
import java.util.Map;

public class Cape extends Module {
    private static Cape INSTANCE;

    // mode name -> bundled cape texture (resources/assets/minecraft/redacted/capes/*.png)
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<String, ResourceLocation>();
    static {
        TEXTURES.put("Migration", new ResourceLocation("minecraft", "redacted/capes/migration.png"));
        TEXTURES.put("Minecon 2011", new ResourceLocation("minecraft", "redacted/capes/minecon_2011.png"));
        TEXTURES.put("Minecon 2012", new ResourceLocation("minecraft", "redacted/capes/minecon_2012.png"));
        TEXTURES.put("Minecon 2015", new ResourceLocation("minecraft", "redacted/capes/minecon_2015.png"));
        TEXTURES.put("Minecon 2016", new ResourceLocation("minecraft", "redacted/capes/minecon_2016.png"));
    }

    private final ModeValue cape = new ModeValue("Cape", this, "Migration",
            "Migration", "Minecon 2011", "Minecon 2012", "Minecon 2015", "Minecon 2016").describe("Which cape texture to wear");

    public Cape() {
        super("Cape", "Render a client-side cape on yourself", Keyboard.KEY_NONE, Category.COSMETICS);
        INSTANCE = this;
    }

    // LayerCape reads this on the local player only; null = vanilla cape behavior
    public static ResourceLocation getActiveCape() {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return null;
        return TEXTURES.get(INSTANCE.cape.getValue());
    }
}
