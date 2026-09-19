import java.util.Arrays;
import java.io.File;
import java.io.IOException;

import net.minecraft.client.main.Main;

public class Start {
    public static void main(String[] args) {
        String[] launchArgs = concat(new String[]{"--version", "mcp", "--accessToken", "0", "--assetsDir", "assets", "--assetIndex", "1.8", "--userProperties", "{}"}, args);
        File assetsDir = new File(getOptionValue(launchArgs, "--assetsDir", "assets"));
        String assetIndex = getOptionValue(launchArgs, "--assetIndex", "1.8");

        try {
            MinecraftSoundAssetDownloader.ensureSoundAssets(assetsDir, "1.8.9", assetIndex);
        } catch (IOException exception) {
            System.err.println("[Assets] Unable to prepare Minecraft sound assets: " + exception.getMessage());
            exception.printStackTrace(System.err);
        }

        Main.main(launchArgs);
    }

    public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static String getOptionValue(String[] args, String option, String fallback) {
        String value = fallback;
        for (int i = 0; i < args.length; ++i) {
            String arg = args[i];
            if (arg.equals(option) && i + 1 < args.length) {
                value = args[++i];
            } else if (arg.startsWith(option + "=")) {
                value = arg.substring(option.length() + 1);
            }
        }
        return value;
    }
}
