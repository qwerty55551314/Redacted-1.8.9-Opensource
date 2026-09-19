package org.lwjglx;

import org.lwjgl.Version;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;

@SuppressWarnings("UnusedReturnValue")
public class Sys {

    public static long getTime() {
        return (long) GLFW.glfwGetTime();
    }

    public static long getTimerResolution() {
        return GLFW.glfwGetTimerFrequency();
    }

    public static boolean openURL(String url) {
        String osName = System.getProperty("os.name", "").toLowerCase();
        try {
            if (osName.contains("linux")) {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
                return true;
            }
            if (osName.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"/usr/bin/open", url});
                return true;
            }
            if (osName.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"cmd.exe", "/C", "start", url});
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            return true;
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            return false;
        }
    }

    public static String getVersion() {
        return "LWJGL3 Port by Juli15 Version 2.0 stable, based on LWJGL" + Version.getVersion();
    }
}
