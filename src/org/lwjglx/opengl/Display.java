package org.lwjglx.opengl;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjglx.LWJGLException;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Display {

    private static final DisplayMode initial_mode;
    private static final long window;
    private static DisplayMode current_mode;
    private static String title = "Minecraft 1.8.9";
    private static boolean created;
    private static boolean resized;
    private static boolean focused;
    private static boolean vsync;

    private static boolean fullscreenActive;
    private static int windowedX = -1, windowedY = -1;            // -1 => center on restore
    private static int windowedWidth = 854, windowedHeight = 480; // vanilla default window


    static {
        glfwSetErrorCallback(GLFWErrorCallback.createPrint(System.err));

        if (!glfwInit())
            throw new RuntimeException("Failed to initialize GLFW");

        GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        int bpp = vidMode.redBits() + vidMode.greenBits() + vidMode.blueBits();
        current_mode = initial_mode = new DisplayMode(vidMode.width(), vidMode.height(), bpp, vidMode.refreshRate());

        //INITIAL SETUP
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        //glfwWindowHint(GLFW_DOUBLEBUFFER, GLFW_FALSE);
        glfwWindowHint(GLFW_DECORATED, GLFW_TRUE);


        window = glfwCreateWindow(current_mode.getWidth(), current_mode.getHeight(), title, NULL, NULL);
        if (window == NULL)
            throw new RuntimeException("Failed to create the GLFW window");

        glfwMakeContextCurrent(window);

        GL.createCapabilities();
    }

    public static DisplayMode getDisplayMode() {
        return current_mode;
    }

    public static void setDisplayMode(DisplayMode displayMode) {
        current_mode = displayMode;
        glfwSetWindowSize(window, displayMode.getWidth(), displayMode.getHeight());
        if (supportsWindowPosition()) {
            glfwSetWindowPos(
                    window,
                    (initial_mode.getWidth() - displayMode.getWidth()) / 2,
                    (initial_mode.getHeight() - displayMode.getHeight()) / 2
            );
        }
    }

    /** Wayland does not let the client position its own window (GLFW_FEATURE_UNAVAILABLE); skip centering there. */
    private static boolean supportsWindowPosition() {
        try {
            return glfwGetPlatform() != GLFW_PLATFORM_WAYLAND;
        } catch (Throwable t) {
            return true; // glfwGetPlatform is 3.4+; assume legacy platforms still allow positioning
        }
    }

    public static void setFullscreen(boolean fullscreen) {
        if (fullscreen == fullscreenActive) return;
        if (fullscreen) {
            // stash the windowed rect so we can come back to it (only if we're actually windowed)
            if (glfwGetWindowMonitor(window) == NULL) {
                int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
                glfwGetWindowPos(window, x, y);
                glfwGetWindowSize(window, w, h);
                windowedX = x[0]; windowedY = y[0]; windowedWidth = w[0]; windowedHeight = h[0];
            }
            long monitor = glfwGetPrimaryMonitor();
            GLFWVidMode v = glfwGetVideoMode(monitor);
            glfwSetWindowMonitor(window, monitor, 0, 0, v.width(), v.height(), v.refreshRate());
            current_mode = new DisplayMode(v.width(), v.height(),
                    v.redBits() + v.greenBits() + v.blueBits(), v.refreshRate());
        } else {
            int x = windowedX, y = windowedY;
            if (x < 0 || y < 0) { // center the default window
                x = (initial_mode.getWidth() - windowedWidth) / 2;
                y = (initial_mode.getHeight() - windowedHeight) / 2;
            }
            glfwSetWindowMonitor(window, NULL, x, y, windowedWidth, windowedHeight, GLFW_DONT_CARE);
            current_mode = new DisplayMode(windowedWidth, windowedHeight);
        }
        fullscreenActive = fullscreen;
    }

    public static void setResizable(boolean resizable) {
        glfwWindowHint(GLFW_RESIZABLE, resizable ? 1 : 0);
    }

    public static void setTitle(String titleIn) {
        title = titleIn;
        glfwSetWindowTitle(window, titleIn);
    }

    public static void setIcon(ByteBuffer[] byteBuffers) {
        if (window == 0 || byteBuffers == null || byteBuffers.length == 0) return;
        final GLFWImage.Buffer icons = GLFWImage.malloc(byteBuffers.length);
        for (int i = 0; i < byteBuffers.length; i++) {
            final ByteBuffer buf = byteBuffers[i];
            final int size = (int) Math.sqrt(buf.capacity() / 4);
            icons.get(i).set(size, size, buf);
        }
        glfwSetWindowIcon(window, icons);
        icons.free();
    }


    public static void setVSyncEnabled(boolean vsyncIn) {
        if (glfwGetCurrentContext() != 0) {
            glfwSwapInterval(vsyncIn ? GLFW_TRUE : GLFW_FALSE);
        }
        vsync = vsyncIn;
    }

    public static void destroy() {
        glfwDestroyWindow(window);
        created = false;
    }

    public static DisplayMode[] getAvailableDisplayModes() {
        GLFWVidMode.Buffer m = glfwGetVideoModes(glfwGetPrimaryMonitor());
        ArrayList<DisplayMode> modes = new ArrayList<>();
        while (m.hasRemaining()) {
            GLFWVidMode vidMode = m.get();
            int bpp = vidMode.redBits() + vidMode.greenBits() + vidMode.blueBits();
            modes.add(new DisplayMode(vidMode.width(), vidMode.height(), bpp, vidMode.refreshRate()));
        }
        return modes.toArray(new DisplayMode[modes.size()]);
    }

    public static DisplayMode getDesktopDisplayMode() {
        GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        int bpp = vidMode.redBits() + vidMode.greenBits() + vidMode.blueBits();
        return new DisplayMode(vidMode.width(), vidMode.height(), bpp, vidMode.refreshRate());
    }

    public static boolean isCreated() {
        return created;
    }

    public static boolean isCloseRequested() {
        return glfwWindowShouldClose(window);
    }

    public static void sync(int fps) {
        Sync.sync(fps);
    }

    public static void update() {
        if (glfwGetWindowAttrib(window, GLFW_VISIBLE) == 1) {
            resized = false;
            glfwSwapBuffers(window);
            Mouse.poll_scrollY = 0;
            glfwPollEvents();
            Keyboard.flushPending();
            Mouse.createEvent();
        }
    }

    public static boolean wasResized() {
        return resized;
    }

    public static int getWidth() {
        return current_mode.getWidth();
    }

    public static int getHeight() {
        return current_mode.getHeight();
    }

    public static boolean isActive() {
        if (focused) return true;
        /* 
         * Single-window game client: as long as the window exists and is visible, treat it as active.
         * On Xwayland the GLFW focus callback can miss/linger, which made pause-on-lost-focus fire every frame (menu spam) and setIngameFocus fail (menus closing on click).
         */
        try {
            if (window != 0L && glfwGetWindowAttrib(window, GLFW_VISIBLE) == 1) {
                return true;
            }
        } catch (Throwable t) {
            // ignore
        }
        return focused;
    }

    // real focus signal (used by the Unfocused FPS cap); NOT the same as isActive(), which
    // deliberately treats any visible window as active to avoid Xwayland focus-callback spam
    public static boolean isFocused() {
        return focused;
    }

    public static long getWindow() {
        return window;
    }

    public static void releaseContext() {
        glfwSetWindowShouldClose(window, true);
    }

    public static void create() throws LWJGLException {
        create(new PixelFormat(), new ContextAttribs(3, 3).withProfileCore(true));
    }

    public static void create(PixelFormat pf) throws LWJGLException {
        create(pf, new ContextAttribs(3, 3).withProfileCore(true));
    }

    public static void create(PixelFormat pf, ContextAttribs attributes) throws LWJGLException {
        created = true;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1); // int*
            IntBuffer pHeight = stack.mallocInt(1); // int*

            // Get the window size passed to glfwCreateWindow
            glfwGetWindowSize(window, pWidth, pHeight);

            // Center the window
            if (supportsWindowPosition()) {
                glfwSetWindowPos(
                        window,
                        (initial_mode.getWidth() - pWidth.get(0)) / 2,
                        (initial_mode.getHeight() - pHeight.get(0)) / 2
                );
            }
        }
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, attributes.getVersion_major());
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, attributes.getVersion_minor());
        if (attributes.isProfileCore()) glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        glfwSetWindowSizeCallback(window, new GLFWWindowSizeCallback() {
            @Override
            public void invoke(long window, int width, int height) {
                current_mode = new DisplayMode(width, height);
                resized = true;
            }
        });
        glfwSetWindowFocusCallback(window, new GLFWWindowFocusCallback() {
            @Override
            public void invoke(long l, boolean focused) {
                Display.focused = focused;
                Mouse.poll_xPos = Mouse.last_x;
                Mouse.poll_yPos = Mouse.last_y;
            }
        });

        Keyboard.create();
        Keyboard.pollGLFW();
        Mouse.create();
        Mouse.pollGLFW();

        //Use raw input, better for 3D camera
        if (glfwRawMouseMotionSupported())
            glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);

        glfwSwapInterval(vsync ? GLFW_TRUE : GLFW_FALSE);
        glfwShowWindow(window);
    }
}
