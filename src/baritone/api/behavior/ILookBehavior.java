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

package baritone.api.behavior;

import baritone.api.utils.Rotation;

/**
 * @author Brady
 * @since 9/23/2018
 */
public interface ILookBehavior extends IBehavior {

    /**
     * Updates the current {@link ILookBehavior} target to target
     * the specified rotations on the next tick. If force is {@code true},
     * then freeLook will be overriden and angles will be set regardless.
     * If any sort of block interaction is required, force should be {@code true},
     * otherwise, it should be {@code false};
     *
     * @param rotation The target rotations
     * @param force    Whether or not to "force" the rotations
     */
    void updateTarget(Rotation rotation, boolean force);

    /**
     * Requests a one-shot smooth camera turn toward the given rotation. The
     * turn is executed by the humanized aim processor over the next few ticks
     * (visible even when freeLook is enabled, when {@code force} is true), and
     * completes automatically once settled. Pathing targets always take
     * priority while a path is active.
     *
     * @param yaw   Target yaw in degrees
     * @param pitch Target pitch in degrees
     * @param force Whether the turn should apply to the real camera regardless
     *              of freeLook
     */
    void requestManualLook(float yaw, float pitch, boolean force);

    /**
     * @return Whether a {@link #requestManualLook} turn is still in progress
     */
    boolean isManualLookActive();
}
