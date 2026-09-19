package redacted.client.module.impl.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.gui.team.GuiTeamManager;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.team.TeamStore;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.ColorValue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class Team extends Module {
    // was 3 separate 0-255 sliders (default 0,170,255 -> sky blue); packed here so the
    // ClickGUI's generic ColorValue picker renders instead of the hand-built one
    private final ColorValue color = new ColorValue("Color", this, 0xFF00AAFF).describe("Friend highlight color");

    // servers like Mineplex/minemem drop every player into one scoreboard team for nametag
    // coloring -- with this on, EntityUtil then treats the whole server as teammates. toggle
    // applies whether or not this module itself is enabled (settings persist independently)
    private final BooleanValue scoreboardTeams = new BooleanValue("Scoreboard Teams", this, true)
            .describe("Never target players on your vanilla scoreboard team");

    private final Set<String> members = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    private boolean loaded;

    // cached because getColor() runs per entity per frame in the ESP/nametag paths;
    // Color is immutable so sharing one instance per RGB is safe
    private int cachedColorRgb = Integer.MIN_VALUE;
    private Color cachedColor;
    private static final Color FALLBACK_COLOR = new Color(0, 170, 255);

    public Team() {
        super("Team", "Friends list: never targeted in combat, rendered in a custom color", Keyboard.KEY_NONE, Category.COMBAT);
    }

    @Override
    public void onEnable() {
        final GuiTeamManager gui = new GuiTeamManager();
        gui.setParentScreen(mc.currentScreen);
        mc.displayGuiScreen(gui);
        setEnabled(false);
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (mc != null && mc.mcDataDir != null) {
            members.addAll(TeamStore.load(mc.mcDataDir));
        }
    }

    public boolean contains(final Entity entity) {
        if (!(entity instanceof EntityPlayer)) {
            return false;
        }
        return contains(((EntityPlayer) entity).getGameProfile().getName());
    }

    public boolean contains(final String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        // fast path: game profile names never carry surrounding whitespace, so skip the
        // trim() allocations on the per-entity-per-frame lookup; trim only when needed
        String probe = name;
        if (probe.charAt(0) <= ' ' || probe.charAt(probe.length() - 1) <= ' ') {
            probe = probe.trim();
            if (probe.isEmpty()) {
                return false;
            }
        }
        ensureLoaded();
        return members.contains(probe);
    }

    public void add(final String name) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        ensureLoaded();
        if (members.add(name.trim()) && mc.mcDataDir != null) {
            TeamStore.save(mc.mcDataDir, members);
        }
    }

    public void remove(final String name) {
        if (name == null) {
            return;
        }
        ensureLoaded();
        if (members.remove(name.trim()) && mc.mcDataDir != null) {
            TeamStore.save(mc.mcDataDir, members);
        }
    }

    public List<String> getMembers() {
        ensureLoaded();
        return new ArrayList<String>(members);
    }

    public ColorValue getColorValue() {
        return color;
    }

    public Color getColor() {
        final int rgb = color.getRGB();
        if (cachedColor == null || cachedColorRgb != rgb) {
            cachedColorRgb = rgb;
            cachedColor = new Color(rgb);
        }
        return cachedColor;
    }

    public static Team get() {
        return Client.getInstance().getModuleManager().getModule(Team.class);
    }

    public static boolean isTeam(final Entity entity) {
        final Team module = get();
        return module != null && module.contains(entity);
    }

    // gate for the vanilla scoreboard same-team check in EntityUtil.isValidTarget -- false lets
    // combat modules target players even when the server groups everyone into one team
    public static boolean respectScoreboard() {
        final Team module = get();
        return module == null || module.scoreboardTeams.getValue();
    }

    public static Color getTeamColor() {
        final Team module = get();
        return module != null ? module.getColor() : FALLBACK_COLOR;
    }

    public static int getTeamColorARGB() {
        return getTeamColor().getRGB();
    }
}
