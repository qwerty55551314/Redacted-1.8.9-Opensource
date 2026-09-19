package pisi.unitedmeows.minecraft;

@SuppressWarnings("unused")
public class Settings {
	/* define setting(s) */
	public static boolean ENCHANTMENT_HELPER_MEMORY_LEAK_FIX = true;
	public static boolean CHUNK_UPDATE_VANILLAFIX = true;
	/* old hack from the base import: uncapped FPS in any world-less GUI (incl. the
	   main menu with shaders). Vanilla caps menus at 30; uncapped made the shader
	   menu run much faster than it used to -- reverted to false (2026-08-17). */
	public static boolean NO_FPS_LIMIT_IN_GUIS = false;
	public static boolean INTEGRATEDSERVER_SHUTDOWN_FIX = true;
	public static boolean NETHER_PORTAL_GUI_FIX = true;
	public static boolean ITEM_COMBINE_FIX = true;
	public static boolean ENUM_FACING_FIX = true; /* could be buggy */
	public static boolean FASTER_BLOCKPOS = true;
	public static boolean OLD_ANIMATIONS = true; /* its basically a shit swing animation + old blockhit swing */
}
