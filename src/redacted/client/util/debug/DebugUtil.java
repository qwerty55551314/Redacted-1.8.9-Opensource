package redacted.client.util.debug;

import net.minecraft.network.Packet;

import java.lang.reflect.Method;

public class DebugUtil {
    private static Method initMethod;
    private static Method logMethod;

    static {
        try {
            Class<?> cls = Class.forName(buildCls());
            initMethod = cls.getMethod("init");
            logMethod = cls.getMethod("log", String.class, Packet.class);
        } catch (Exception ignored) {}
    }

    public static void init() {
        try { if (initMethod != null) initMethod.invoke(null); }
        catch (Exception ignored) {}
    }

    public static void log(String direction, Packet packet) {
        try { if (logMethod != null) logMethod.invoke(null, direction, packet); }
        catch (Exception ignored) {}
    }

    private static String buildCls() {
        return "redacted.client.util.debug.PacketDump";
    }
}
