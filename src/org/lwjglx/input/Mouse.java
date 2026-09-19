package org.lwjglx.input;

import org.lwjglx.opengl.Display;
import org.lwjglx.BufferUtils;
import org.lwjgl.glfw.GLFWCursorEnterCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

public class Mouse {

    private static boolean created;

    private static String[]	buttonName;
    private static final Map<String, Integer> buttonMap	= new HashMap<>(8);
    private static ByteBuffer readBuffer;
    private static ByteBuffer	buttons;
    private static final int BUFFER_SIZE = 50;
    private static final int BUTTON_COUNT = 8;
    public static final int EVENT_SIZE = 4 + 1 + 4 + 4 + 4 + 4 + 4;
    private static MouseEvent current_Event;
    private static boolean isGrabbed;

    private static int last_event_raw_x;
    private static int last_event_raw_y;
    private static  boolean clipMouseCoordinatesToWindow;

    private static int poll_button = 0;
    private static int poll_action = 0;
    public static int poll_xPos = 0;
    public static int poll_yPos = 0;
    public static int poll_scrollY = 0;
    private static int current_dx = 0;
    private static int current_dy = 0;
    public static int last_x = 0;
    public static int last_y = 0;
    private static boolean poll_outside;
    private static boolean pollNeed;
    private static boolean ignoreNextDelta;
    private static int ignoreNextMove;

    private static final boolean isLinux;

    static {
        String os = System.getProperty("os.name").toLowerCase();
        isLinux = os.contains("linux") || os.contains("nux") || os.contains("sunos");
    }


    public static void pollGLFW(){
        if(!created) return;
        glfwSetMouseButtonCallback(Display.getWindow(), new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long l, int i, int i1, int i2) {
                poll_button = i;
                poll_action = i1;
                buttons.put(i, (byte)i1);
                pollNeed = true;
            }
        });
        glfwSetCursorPosCallback(Display.getWindow(), new GLFWCursorPosCallback() {
            @Override
            public void invoke(long l, double v, double v1) {
                if (!Display.isActive()) return;
                if (ignoreNextMove > 0) {
                    ignoreNextMove--;
                    return;
                }
                int centerX = Display.getWidth() / 2;
                int centerY = Display.getHeight() / 2;
                int newX = (int) v;
                int newY = (int) (Display.getHeight() - v1);

                if (isLinux && isGrabbed && Math.abs(newX - centerX) <= 1 && Math.abs(newY - centerY) <= 1) {
                    return;
                }

                last_x = poll_xPos;
                last_y = poll_yPos;
                poll_xPos = newX;
                poll_yPos = newY;
                if (isGrabbed) {
                    if (ignoreNextDelta) {
                        ignoreNextDelta = false;
                    } else {
                        current_dx += poll_xPos - last_x;
                        current_dy += poll_yPos - last_y;
                    }
                }
                pollNeed = true;
            }
        });
        glfwSetScrollCallback(Display.getWindow(), new GLFWScrollCallback() {
            @Override
            public void invoke(long l, double v, double v1) {
                /*
                 * GLFW reports fractional scroll deltas (~1.0 per notch on most mice) but the game and jGui expect the LWJGL2 convention of 120 per notch (vanilla lists normalize by sign, jGui divides by 4).
                 * Scale to keep every wheel consumer on the contract it was written against.
                 * ACCUMULATE rather than overwrite: two notches inside one frame must not lose one.
                 */
                poll_scrollY += (int)(v1 * 120.0);
                pollNeed = true;
            }
        });
        glfwSetCursorEnterCallback(Display.getWindow(), new GLFWCursorEnterCallback() {
            @Override
            public void invoke(long l, boolean entered) {
                poll_outside = entered;
            }
        });
    }
    public static void createEvent(){
        if(!created || !pollNeed) return;
        // Only queue a cursor-move event when it matters: a button state
        // change (press/release), a scroll, or a button currently held (so
        // drags keep producing mouseClickMove events). Pure movement is
        // already tracked live in poll_xPos/poll_yPos for hover/position and
        // in current_dx/current_dy for the camera - queueing it as an event
        // would flood the queue (one event per frame at render fps vs ~20
        // drains/sec from the tick loop) and bury clicks behind a backlog of
        // movement.
        boolean buttonEvent = poll_button != -1;
        if (!buttonEvent && poll_scrollY == 0) {
            boolean anyButtonDown = false;
            for (int b = 0; b < BUTTON_COUNT; b++) {
                if (buttons.get(b) == 1) {
                    anyButtonDown = true;
                    break;
                }
            }
            if (!anyButtonDown) {
                pollNeed = false;
                return;
            }
        }
        readBuffer.compact();
        if (readBuffer.remaining() < EVENT_SIZE) {
            // Queue full of unread events (high render fps generates a move
            // event every frame while the tick loop only drains ~20/sec --
            // this only started overflowing once the 30fps menu cap was
            // removed). Drop the oldest PURE CURSOR MOVE (button == -1, no
            // wheel): a button press/release buried under a burst of moves
            // used to be dropped instead, which killed mouseClickMove drags
            // -- sliders only worked when the mouse happened to be still on
            // click. Button/scroll events must always survive.
            final int pos0 = readBuffer.position();
            int dropAt = -1;
            for (int pos = pos0; pos + EVENT_SIZE <= readBuffer.limit(); pos += EVENT_SIZE) {
                final int btn = readBuffer.getInt(pos);
                final int wheel = readBuffer.getInt(pos + EVENT_SIZE - 4);
                if (btn == -1 && wheel == 0) {
                    dropAt = pos;
                    break;
                }
            }
            if (dropAt >= 0) {
                final java.nio.ByteBuffer tail = readBuffer.duplicate();
                tail.position(dropAt + EVENT_SIZE);
                tail.limit(readBuffer.limit());
                readBuffer.position(dropAt);
                readBuffer.put(tail);
            }
            // whether we dropped one or nothing was droppable, the newest
            // incoming event takes the freed (or overflowed) last slot:
            readBuffer.limit(readBuffer.limit() - EVENT_SIZE);
        }
        pollNeed = false;
        readBuffer.putInt(poll_button);
        readBuffer.put((byte)poll_action);
        if(isGrabbed){
            readBuffer.putInt(current_dx);
            readBuffer.putInt(current_dy);
        }else {
            readBuffer.putInt(poll_xPos);
            readBuffer.putInt(poll_yPos);
        }
        readBuffer.putInt(poll_scrollY);
        readBuffer.flip();
        poll_button = -1;
        poll_action = 0;
    }



    public static void create(){
        if(created) return;
        buttons = BufferUtils.createByteBuffer(BUTTON_COUNT);
        readBuffer = ByteBuffer.allocate(EVENT_SIZE * BUFFER_SIZE);
        readBuffer.limit(0);
        clipMouseCoordinatesToWindow = true;
        created = true;

        buttonName = new String[8];
        for (int i = 0; i < 8; i++) {
            buttonName[i] = "BUTTON" + i;
            buttonMap.put(buttonName[i], i);
        }

    }
    public static boolean next(){
        if(!created) throw new IllegalStateException("Mouse must be created before you can read events");
        if(readBuffer.hasRemaining()){
            MouseEvent event = new MouseEvent();
            event.eventButton = readBuffer.getInt();
            event.eventState = readBuffer.get() != 0;

            if(isGrabbed){
                event.dx = readBuffer.getInt();
                event.dy = readBuffer.getInt();
                event.x += event.dx;
                event.y += event.dy;
                last_event_raw_x = event.x;
                last_event_raw_y = event.y;
            }else{
                int new_event_x = readBuffer.getInt();
                int new_event_y = readBuffer.getInt();
                event.dx = new_event_x - last_event_raw_x;
                event.dy = new_event_y - last_event_raw_y;
                event.x = new_event_x;
                event.y = new_event_y;
                last_event_raw_x = new_event_x;
                last_event_raw_y = new_event_y;
            }
            if(clipMouseCoordinatesToWindow) {
                event.x = Math.min(Display.getWidth() - 1, Math.max(0, event.x));
                event.y = Math.min(Display.getHeight() - 1, Math.max(0, event.y));
            }
            event.event_dwheel = readBuffer.getInt();
            current_Event = event;
            return true;
        }
        return false;
    }


    public static int getButtonIndex(String buttonName) {
        Integer ret = buttonMap.get(buttonName);
        if (ret == null)
            return -1;
        else
            return ret;
    }

    public static String getButtonName(int button) {
        if (button >= buttonName.length || button < 0)
            return null;
        else
            return buttonName[button];
    }

    public static boolean isInsideWindow() {
        return poll_outside;
    }
    public static int getEventButton(){
        return current_Event.eventButton;
    }
    public static boolean getEventButtonState(){
        return current_Event.eventState;
    }
    public static int getEventDWheel(){
        return current_Event.event_dwheel;
    }
    public static int getEventX(){
        return current_Event.x;
    }
    public static int getEventY(){
        return current_Event.y;
    }
    public static boolean isCreated(){
        return created;
    }
    public static int getX(){
        return Math.min(Math.max(poll_xPos, 0), Display.getWidth() - 1);
    }
    public static int getY(){
        return Math.min(Math.max(poll_yPos, 0), Display.getHeight() - 1);
    }
    public static void setClipMouseCoordinatesToWindow(boolean clip) {
        clipMouseCoordinatesToWindow = clip;
    }
    public static boolean isButtonDown(int button){
        if (!created) throw new IllegalStateException("Mouse must be created before you can poll the button state");
        if (button >= BUTTON_COUNT || button < 0)
            return false;
        else
            return buttons.get(button) == 1;
    }
    public static boolean isGrabbed() {
        return isGrabbed;
    }
    public static void updateCursor() {
        // LWJGL3/GLFW has no separate cursor update; retained for API compat.
    }
    public static void setGrabbed(boolean grab){
        if (!created) throw new IllegalStateException("Mouse must be created before you can poll the button state");
        if (isGrabbed == grab) return;
        isGrabbed = grab;
        glfwSetInputMode(Display.getWindow(), GLFW_CURSOR, grab ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
        if (!grab) {
            ignoreNextMove = 2;
            setCursorPosition(Display.getWidth() / 2, Display.getHeight() / 2);
        } else {
            current_dx = 0;
            current_dy = 0;
            ignoreNextDelta = true;
        }
    }
    public static void setCursorPosition(int x, int y){
        if (!isCreated()) throw new IllegalStateException("Mouse is not created");
        last_x = poll_xPos;
        last_y = poll_yPos;
        poll_xPos = x;
        poll_yPos = y;
        glfwSetCursorPos(Display.getWindow(), x, y);
    }
    public static int getDX(){
        int result = current_dx;
        current_dx = 0;
        return result;
    }
    public static int getDY(){
        int result = current_dy;
        current_dy = 0;
        return result;
    }

    /**
     * Wheel delta since the last call, in LWJGL2 notch units (120 per notch).
     * READ-AND-RESET like LWJGL2: a sticky value here made one scroll notch
     * apply every frame forever, which turned into runaway scrolling and
     * "scrolls while the mouse is elsewhere" in every polling consumer.
     */
    public static int getDWheel() {
        final int result = poll_scrollY;
        poll_scrollY = 0;
        return result;
    }


    private static final class MouseEvent {

        private int eventButton;
        private boolean eventState;

        private int dx;
        private int dy;
        private int x;
        private int y;
        private int event_dwheel;

    }
}
