package redacted.client.util.optimization;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import redacted.client.module.impl.render.ParticleCulling;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/*
 * ParticleCulling port: CullThread (bl4ckscor3.mod.particleculling).
 * Async daemon iterating the 8 fx layers (4 render layers x alpha/blend), culling
 * particles outside the frustum and (optionally) particles hidden behind opaque
 * blocks. The block raytrace is World#rayTraceBlocks adapted to trace through
 * blocks; Vec3 fields are final in 1.8.9 so the scan uses local coordinates.
 * Errors always un-cull the particle (never hide something by accident).
 */
public class ParticleCullingThread extends Thread {

    private final ParticleCulling.Config config;
    private final List<EntityFX> temporaryParticleStorage = new ArrayList<>();

    public ParticleCullingThread(ParticleCulling.Config config) {
        super("Particle Culling");
        this.setDaemon(true);
        this.config = config;
    }

    @Override
    public void run() {
        Minecraft mc = Minecraft.getMinecraft();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                long start = System.nanoTime();

                if (mc.theWorld != null && mc.effectRenderer != null) {
                    List<EntityFX>[][] fxLayers = mc.effectRenderer.getFxLayers();
                    if (fxLayers != null) {
                        for (int i = 0; i < 4; i++) {
                            List<EntityFX>[] layer = fxLayers[i];
                            if (layer == null) {
                                continue;
                            }
                            for (int j = 0; j < 2; j++) {
                                this.iterateParticles(mc, layer[j]);
                            }
                        }
                    }
                }

                double d = (System.nanoTime() - start) / 1_000_000.0D;
                long sleepTime = 10 - (long) d;
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable e) {
                /* never kill the client over async culling; skip this cycle */
            }
        }
    }

    /* minimizes CMEs/NSEEs when iterating over an fx layer (Meldexun's trick) */
    private void iterateParticles(Minecraft mc, List<EntityFX> deque) {
        if (deque == null) {
            return;
        }
        try {
            Iterator<EntityFX> iterator = deque.iterator();
            while (iterator.hasNext()) {
                EntityFX particle;
                try {
                    particle = iterator.next();
                } catch (ConcurrentModificationException | NoSuchElementException e) {
                    break;
                }
                if (particle != null) {
                    this.temporaryParticleStorage.add(particle);
                }
            }

            for (EntityFX particle : this.temporaryParticleStorage) {
                particle.particleCulled = this.shouldCullParticle(particle, mc);
            }
        } finally {
            this.temporaryParticleStorage.clear();
        }
    }

    private boolean shouldCullParticle(EntityFX particle, Minecraft mc) {
        try {
            if (!this.config.cullInSpectator && mc.thePlayer != null && mc.thePlayer.isSpectator()) {
                return false;
            }

            ICamera camera = ParticleCulling.camera;
            if (camera == null || ParticleCulling.isIgnored(particle.getClass())) {
                return false;
            }

            if (camera.isBoundingBoxInFrustum(particle.getEntityBoundingBox())) {
                if (this.config.cullBehindBlocks) {
                    Entity entity = mc.getRenderViewEntity();
                    if (entity != null) {
                        return this.shouldCull(entity.worldObj,
                            new Vec3(entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ),
                            new Vec3(particle.posX, particle.posY, particle.posZ));
                    }
                }
                return false;
            }

            return true;
        } catch (Exception e) {
            /* particles that error are never culled */
            return false;
        }
    }

    /* adapted from World#rayTraceBlocks to be able to trace through blocks */
    private boolean shouldCull(World world, Vec3 from, Vec3 to) {
        if (Double.isNaN(from.xCoord) || Double.isNaN(from.yCoord) || Double.isNaN(from.zCoord)
                || Double.isNaN(to.xCoord) || Double.isNaN(to.yCoord) || Double.isNaN(to.zCoord)) {
            return false;
        }
        double fromX = from.xCoord;
        double fromY = from.yCoord;
        double fromZ = from.zCoord;
        boolean opacityCheck = false;
        int blocks = 0;
        int toX = MathHelper.floor_double(to.xCoord);
        int toY = MathHelper.floor_double(to.yCoord);
        int toZ = MathHelper.floor_double(to.zCoord);
        int checkX = MathHelper.floor_double(fromX);
        int checkY = MathHelper.floor_double(fromY);
        int checkZ = MathHelper.floor_double(fromZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(checkX, checkY, checkZ);
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        if (block.getCollisionBoundingBox(world, pos, state) != null && block.canCollideCheck(state, false)
                && block.collisionRayTrace(world, pos, from, to) != null) {
            blocks++;
            opacityCheck = opacityCheck || this.config.cullBehindGlass || (block.isFullCube() && block.isOpaqueCube());
        }

        int maxIterations = 50;

        while (maxIterations-- >= 0) {
            if (checkX == toX && checkY == toY && checkZ == toZ) {
                return opacityCheck && (++blocks > this.config.blockBuffer);
            }

            boolean wasXChanged = true;
            boolean wasYChanged = true;
            boolean wasZChanged = true;
            double d0 = 999.0D;
            double d1 = 999.0D;
            double d2 = 999.0D;

            if (toX > checkX) {
                d0 = checkX + 1.0D;
            } else if (toX < checkX) {
                d0 = checkX + 0.0D;
            } else {
                wasXChanged = false;
            }

            if (toY > checkY) {
                d1 = checkY + 1.0D;
            } else if (toY < checkY) {
                d1 = checkY + 0.0D;
            } else {
                wasYChanged = false;
            }

            if (toZ > checkZ) {
                d2 = checkZ + 1.0D;
            } else if (toZ < checkZ) {
                d2 = checkZ + 0.0D;
            } else {
                wasZChanged = false;
            }

            double d3 = 999.0D;
            double d4 = 999.0D;
            double d5 = 999.0D;
            double d6 = to.xCoord - fromX;
            double d7 = to.yCoord - fromY;
            double d8 = to.zCoord - fromZ;

            if (wasXChanged) {
                d3 = (d0 - fromX) / d6;
            }
            if (wasYChanged) {
                d4 = (d1 - fromY) / d7;
            }
            if (wasZChanged) {
                d5 = (d2 - fromZ) / d8;
            }

            if (d3 == -0.0D) {
                d3 = -1.0E-4D;
            }
            if (d4 == -0.0D) {
                d4 = -1.0E-4D;
            }
            if (d5 == -0.0D) {
                d5 = -1.0E-4D;
            }

            EnumFacing facing;

            if (d3 < d4 && d3 < d5) {
                facing = toX > checkX ? EnumFacing.WEST : EnumFacing.EAST;
                fromX = d0;
                fromY += d7 * d3;
                fromZ += d8 * d3;
            } else if (d4 < d5) {
                facing = toY > checkY ? EnumFacing.DOWN : EnumFacing.UP;
                fromX += d6 * d4;
                fromY = d1;
                fromZ += d8 * d4;
            } else {
                facing = toZ > checkZ ? EnumFacing.NORTH : EnumFacing.SOUTH;
                fromX += d6 * d5;
                fromY += d7 * d5;
                fromZ = d2;
            }

            checkX = MathHelper.floor_double(fromX) - (facing == EnumFacing.EAST ? 1 : 0);
            checkY = MathHelper.floor_double(fromY) - (facing == EnumFacing.UP ? 1 : 0);
            checkZ = MathHelper.floor_double(fromZ) - (facing == EnumFacing.SOUTH ? 1 : 0);
            pos.set(checkX, checkY, checkZ);
            state = world.getBlockState(pos);
            block = state.getBlock();

            if (block.getMaterial() == Material.portal || block.getCollisionBoundingBox(world, pos, state) != null
                    && block.canCollideCheck(state, false)
                    && block.collisionRayTrace(world, pos, from, to) != null) {
                opacityCheck = opacityCheck || this.config.cullBehindGlass || (block.isFullCube() && block.isOpaqueCube());

                if (++blocks > this.config.blockBuffer) {
                    return opacityCheck;
                }
            }
        }

        return opacityCheck && blocks > this.config.blockBuffer;
    }

}
