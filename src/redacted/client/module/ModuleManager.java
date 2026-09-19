package redacted.client.module;

import redacted.client.Client;
import redacted.client.module.impl.combat.*;
import redacted.client.module.impl.movement.BridgeAssist;

import redacted.client.module.impl.movement.InventoryMove;
import redacted.client.module.impl.movement.KeepSprint;
import redacted.client.module.impl.movement.NoFall;
import redacted.client.module.impl.movement.NoSlow;

import redacted.client.module.impl.movement.Sprint;

import redacted.client.module.impl.movement.NoJumpDelay;
import redacted.client.module.impl.render.ClickGUI;
import redacted.client.module.impl.render.Crosshair;
import redacted.client.module.impl.render.EntityESP;
import redacted.client.module.impl.render.BlockESP;
import redacted.client.module.impl.render.FullBright;
import redacted.client.module.impl.render.HUD;
import redacted.client.module.impl.render.Trajectories;
import redacted.client.module.impl.render.Notifications;
import redacted.client.module.impl.render.SuperheroFX;
import redacted.client.module.impl.render.TNTTimer;
import redacted.client.module.impl.render.Tweaks;
import redacted.client.module.impl.render.Freecam;
import redacted.client.module.impl.render.FreeLook;
import redacted.client.module.impl.render.EntityCulling;
import redacted.client.module.impl.render.ParticleCulling;
import redacted.client.module.impl.render.Mirror;
import redacted.client.module.impl.utility.AutoHeal;
import redacted.client.module.impl.utility.InvManager;
import redacted.client.module.impl.utility.AutoTool;
import redacted.client.module.impl.utility.ClientSpoof;
import redacted.client.module.impl.utility.MiddleClickFriend;
import redacted.client.module.impl.utility.FlagDetector;
import redacted.client.module.impl.utility.AnticheatDetector;
import redacted.client.module.impl.world.AutoPearl;
import redacted.client.module.impl.world.BaritoneModule;
import redacted.client.module.impl.world.BedProtection;
import redacted.client.module.impl.world.ChestStealer;
import redacted.client.module.impl.world.Clutch;
import redacted.client.module.impl.world.FastPlace;
import redacted.client.module.impl.world.Scaffold;
import redacted.client.module.impl.developer.PacketLogger;
import redacted.client.module.impl.cosmetics.Cape;
import redacted.client.module.impl.cosmetics.Wings;
import redacted.client.module.impl.cosmetics.Hat;
import redacted.client.module.impl.cosmetics.Particles;
import redacted.client.module.impl.cosmetics.HitSounds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<Module>();
    // class -> first registered module whose hierarchy claims that class (mirror of list-order scan)
    private final Map<Class<?>, Module> classIndex = new IdentityHashMap<Class<?>, Module>();

    public void init() {
        this.modules.clear();
        this.classIndex.clear();

        // Movement
        this.register(new Sprint());
        this.register(new NoSlow());
        this.register(new BridgeAssist());
        this.register(new NoFall());
        this.register(new InventoryMove());
        this.register(new NoJumpDelay());
        this.register(new KeepSprint());

        // Player
    

        // Combat
        this.register(new AimAssist());
        this.register(new BowAimAssist());
        this.register(new AntiBot());
        this.register(new Team());
        this.register(new TargetFilter());
        this.register(new AntiMiss());
        // PRE order is owned by listener priorities: AutoBlock -> AntiFireBall -> SilentAura.
        this.register(new AutoBlock());
        this.register(new AntiFireBall());
        this.register(new AutoClicker());
        this.register(new Reach());
        this.register(new WTap());
        this.register(new BackTrack());
        this.register(new FakeLag());
        this.register(new Velocity());
        this.register(new Triggerbot());
        this.register(new AutoThrow());
        this.register(new SilentAura());
        this.register(new SilentAim());
        this.register(new BedAura());
        this.register(new Criticals());
        this.register(new NoHitDelay());

        // Render
        this.register(new ClickGUI());
        this.register(new HUD());
        this.register(new EntityESP());
        this.register(new BlockESP());
        this.register(new FullBright());
        this.register(new Crosshair());
        this.register(new Trajectories());
        this.register(new Notifications());
        this.register(new SuperheroFX());
        this.register(new Tweaks());
        this.register(new TNTTimer());
        this.register(new Freecam());
        this.register(new FreeLook());
        this.register(new EntityCulling());
        this.register(new ParticleCulling());
        this.register(new Mirror());

        // World
        this.register(new FastPlace());
    
        this.register(new ChestStealer());
        this.register(new BedProtection());
        this.register(new Scaffold());
        this.register(new PacketLogger());
        this.register(new BaritoneModule());
        this.register(new Clutch());
        this.register(new AutoPearl());

        // Utility
        this.register(new AutoHeal());
        this.register(new InvManager());
        this.register(new AutoTool());
        this.register(new ClientSpoof());
        this.register(new MiddleClickFriend());
        this.register(new FlagDetector());
        this.register(new AnticheatDetector());

        // Cosmetics
        this.register(new Cape());
        this.register(new Wings());
        this.register(new Hat());
        this.register(new Particles());
        this.register(new HitSounds());
    }

    public void shutdown() {
        for (final Module module : new ArrayList<Module>(this.modules)) {
            module.setEnabled(false);
        }
        this.modules.clear();
    }

    public void register(final Module... modules) {
        if (modules == null) return;

        for (final Module module : modules) {
            if (module == null) continue;
            // Developer-category modules are never registered on release builds.
            if (!Client.DEV_BUILD && module.getCategory() == Category.DEVELOPER) continue;
            if (!this.modules.contains(module)) {
                this.modules.add(module);
                this.indexClassHierarchy(module);
            }
        }
    }

    private void indexClassHierarchy(final Module module) {
        for (Class<?> c = module.getClass(); c != null && c != Module.class; c = c.getSuperclass()) {
            final Module existing = this.classIndex.putIfAbsent(c, module);
            if (existing != null) break;
        }
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(this.modules);
    }

    public <T extends Module> T getModule(final Class<T> moduleClass) {
        if (moduleClass == null) return null;
        return moduleClass.cast(this.classIndex.get(moduleClass));
    }

    public Module getModule(final String name) {
        if (name == null) return null;

        for (final Module module : this.modules) {
            if (module.getName().equalsIgnoreCase(name)) return module;
        }

        return null;
    }

    public List<Module> getModulesByCategory(final Category category) {
        final List<Module> categoryModules = new ArrayList<Module>();
        if (category == null) return categoryModules;

        for (final Module module : this.modules) {
            if (module.getCategory() == category) categoryModules.add(module);
        }

        return Collections.unmodifiableList(categoryModules);
    }

    public void onKey(final int key) {
        if (key == 0) return;

        for (final Module module : this.modules) {
            if (module.getKey() == key) module.toggle();
        }
    }
}
