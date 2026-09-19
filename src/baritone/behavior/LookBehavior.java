/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.behavior;

import baritone.Baritone;
import baritone.api.behavior.ILookBehavior;
import baritone.api.event.events.PlayerUpdateEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.WorldEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import baritone.behavior.look.AimProcessor;
import redacted.client.util.debug.DebugSystem;
import redacted.util.RotationUtil;

/**
 * Drives all Baritone look movement through the {@link AimProcessor} humanized
 * smoothing engine.
 *
 * <p>Two ownership modes, resolved per tick from the target's force flag and
 * the {@code freeLook} setting:
 *
 * <ul>
 *   <li><b>Visible</b> (forced interactions like bridging/placing, or
 *       {@code freeLook} off -- the default): the real camera is stepped
 *       smoothly toward the target every tick. Camera, movement physics and
 *       the outgoing packet all read the same rotation, so backwards-facing
 *       segments (bridge backup, ascend overshoot taps) produce a human flick
 *       instead of a one-tick 180-degree whip.</li>
 *   <li><b>Silent</b> ({@code freeLook} on, non-forced walking look): the
 *       camera stays untouched and the smoothly-stepped aim is handed to
 *       {@link RotationUtil}'s wire bridge, which stamps it onto the outgoing
 *       movement packet and runs physics at the same yaw. The aim state is
 *       persistent across ticks, so the wire rotation glides; when pathing
 *       stops, the aim eases back to the camera before releasing the wire.</li>
 * </ul>
 *
 * <p>Arbitration: while a client module holds a silent rotation spoof
 * (SilentAura, Scaffold, ...), silent pathing defers -- combat aim owns the
 * wire, and the movement fix keeps baritone's keys consistent with it.
 */
public final class LookBehavior extends Behavior implements ILookBehavior {

    /**
     * Target's values are as follows:
     */
    private Rotation target;

    /**
     * Whether the current target was forced (block interaction / manual look)
     */
    private boolean targetForce;

    /**
     * One-shot smooth camera look requested externally (MCP look_at). Adopted
     * on ticks where the pathing stack has no target, cleared once settled.
     */
    private Rotation manualTarget;
    private boolean manualForce = true;

    /**
     * True while the silent aim owns the outgoing rotation via the
     * RotationUtil wire bridge.
     */
    private boolean wireHeld;

    private final AimProcessor processor;

    public LookBehavior(Baritone baritone) {
        super(baritone);
        this.processor = new AimProcessor(ctx);
    }

    @Override
    public void updateTarget(Rotation target, boolean force) {
        this.target = target;
        this.targetForce = force;
        DebugSystem.log(DebugSystem.BARITONE, "target " + target + " force=" + force);
    }

    // manual look API (used by the MCP look_at tool)

    @Override
    public void requestManualLook(float yaw, float pitch, boolean force) {
        this.manualTarget = new Rotation(yaw, pitch);
        this.manualForce = force;
        DebugSystem.log(DebugSystem.BARITONE,
                "manual look requested yaw=" + String.format("%.2f", yaw)
                        + " pitch=" + String.format("%.2f", pitch) + " force=" + force);
    }

    @Override
    public boolean isManualLookActive() {
        return this.manualTarget != null;
    }

    // event handlers

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN) {
            return;
        }
        // reroll the per-tick aim noise exactly once per game tick
        this.processor.tick();
        // the pathing stack's target always wins; a pending manual look is
        // adopted only while pathing has nothing to say this tick
        if (this.target == null && this.manualTarget != null) {
            this.target = this.manualTarget;
            this.targetForce = this.manualForce;
        }
    }

    @Override
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (event.getState() != EventState.PRE) {
            return;
        }
        if (this.target == null) {
            handleIdle();
        } else {
            handleTarget();
        }
    }

    @Override
    public void onWorldEvent(WorldEvent event) {
        this.target = null;
        this.manualTarget = null;
        this.wireHeld = false;
        this.processor.disengage();
        RotationUtil.clearBaritoneWire();
        DebugSystem.log(DebugSystem.BARITONE, "world change: aim state reset");
    }

    // aim state machine

    private void handleTarget() {
        final boolean visible = this.targetForce || !Baritone.settings().freeLook.value;

        if (!visible && RotationUtil.isActive()) {
            // a client module holds a silent spoof on the wire -- combat aim wins.
            // stand down for the tick; baritone's keys flow through the module's
            // movement fix so movement stays prediction-consistent.
            if (this.wireHeld) {
                this.wireHeld = false;
                RotationUtil.clearBaritoneWire();
            }
            this.processor.disengage();
            DebugSystem.log(DebugSystem.BARITONE, "defer: client rotation spoof owns the wire this tick");
            this.target = null;
            return;
        }

        if (this.wireHeld && visible) {
            // switching from a silent hold to a visible turn: the camera is about
            // to own the wire itself, drop the bridge
            this.wireHeld = false;
            RotationUtil.clearBaritoneWire();
        }

        if (!this.processor.isEngaged()) {
            if (visible) {
                this.processor.syncTo(ctx.player().rotationYaw, ctx.player().rotationPitch);
            } else {
                this.processor.syncTo(ctx.player().getLastReportedYaw(), ctx.player().getLastReportedPitch());
            }
            DebugSystem.log(DebugSystem.BARITONE, "aim engaged mode=" + (visible ? "VISIBLE" : "SILENT")
                    + " seedYaw=" + String.format("%.2f", this.processor.getAimYaw())
                    + " seedPitch=" + String.format("%.2f", this.processor.getAimPitch()));
        }

        final boolean walking = baritone.getInputOverrideHandler().isInputForcedDown(Input.MOVE_FORWARD);
        this.processor.advance(this.target, walking);

        if (visible) {
            ctx.player().rotationYaw = this.processor.getAimYaw();
            ctx.player().rotationPitch = this.processor.getAimPitch();
        } else {
            this.wireHeld = true;
            RotationUtil.setBaritoneWire(this.processor.getAimYaw(), this.processor.getAimPitch());
        }

        DebugSystem.log(DebugSystem.BARITONE, "step mode=" + (visible ? "VISIBLE" : "SILENT")
                + " yaw=" + String.format("%.2f", this.processor.getAimYaw())
                + " pitch=" + String.format("%.2f", this.processor.getAimPitch())
                + " dYaw=" + String.format("%.3f", this.processor.getLastStepYaw())
                + " dPitch=" + String.format("%.3f", this.processor.getLastStepPitch()));

        if (this.manualTarget != null && this.manualTarget == this.target
                && isSettled(this.processor.getAimYaw(), this.processor.getAimPitch(),
                        this.manualTarget.getYaw(), this.manualTarget.getPitch())) {
            this.manualTarget = null;
            DebugSystem.log(DebugSystem.BARITONE, "manual look complete");
        }

        this.target = null;
    }

    private void handleIdle() {
        if (!this.wireHeld) {
            if (this.processor.isEngaged()) {
                this.processor.disengage();
                DebugSystem.log(DebugSystem.BARITONE, "aim idle: disengaged");
            }
            return;
        }
        // nothing wants the aim: ease the silent hold back to the camera, then
        // hand the wire to vanilla so the body doesn't freeze mid-turn
        this.processor.advance(new Rotation(ctx.player().rotationYaw, ctx.player().rotationPitch), false);
        RotationUtil.setBaritoneWire(this.processor.getAimYaw(), this.processor.getAimPitch());
        if (isSettled(this.processor.getAimYaw(), this.processor.getAimPitch(),
                ctx.player().rotationYaw, ctx.player().rotationPitch)) {
            this.wireHeld = false;
            RotationUtil.clearBaritoneWire();
            this.processor.disengage();
            DebugSystem.log(DebugSystem.BARITONE, "wire released back to camera");
        } else {
            DebugSystem.log(DebugSystem.BARITONE, "release step yaw=" + String.format("%.2f", this.processor.getAimYaw())
                    + " pitch=" + String.format("%.2f", this.processor.getAimPitch()));
        }
    }

    private boolean isSettled(final float yaw, final float pitch, final float targetYaw, final float targetPitch) {
        final float yawDiff = Math.abs(Rotation.normalizeYaw(yaw - targetYaw));
        final float settle = (float) Math.max(0.05D, Baritone.settings().humanizedAimSettle.value) + 0.05F;
        return Math.min(yawDiff, 360.0F - yawDiff) <= settle
                && Math.abs(pitch - targetPitch) <= settle;
    }
}
