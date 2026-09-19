package redacted.client.module.impl.utility;

import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.value.impl.ModeValue;

/**
 * Always-on client brand control. The brand is applied at the send site
 * (NetHandlerPlayClient.handleJoinGame) instead of through packet events, so no toggle state
 * can ever leave us sending a non-spoofed brand -- this module only picks WHICH brand goes out.
 */
public class ClientSpoof extends Module {

    // Genuine Lunar brand format per lunarclient.dev ("Client Brand"): "lunarclient:<build>".
    // Value below is the official docs example; refresh it from a real install's F3 screen if
    // Lunar ever changes format (they document format changes there).
    public static final String LUNAR_BRAND = "lunarclient:v2.12.0-2349";
    // Plugin channel genuine Lunar advertises at join; some plugins treat its presence as
    // ground-truth proof of Lunar, so we register it alongside the brand.
    public static final String LUNAR_PM_CHANNEL = "lunarclient:pm";

    private final ModeValue mode = new ModeValue("Spoof", this, "Lunar", "Lunar", "Vanilla", "Forge", "Cancel").describe("Brand sent at every join (always active); Cancel sends none");

    public ClientSpoof() {
        super("Client Spoof", "Always-on client brand sent to servers", Keyboard.KEY_NONE, Category.UTILITY);
    }

    private static ClientSpoof instance() {
        return Client.getInstance().getModuleManager().getModule(ClientSpoof.class);
    }

    private static String selectedMode() {
        ClientSpoof self = instance();
        return self != null ? self.mode.getValue() : "Lunar";
    }

    /** true = suppress the MC|Brand packet entirely */
    public static boolean isBrandCancelled() {
        return "Cancel".equals(selectedMode());
    }

    /** true = brand is set to the Lunar identity (also gates the lunarclient:pm registration) */
    public static boolean isLunarBrand() {
        return "Lunar".equals(selectedMode());
    }

    public static String getBrand() {
        switch (selectedMode()) {
            case "Vanilla":
                return "vanilla";
            case "Forge":
                return "FML";
            default:
                return LUNAR_BRAND;
        }
    }
}
