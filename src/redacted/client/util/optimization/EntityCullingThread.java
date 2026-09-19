package redacted.client.util.optimization;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import redacted.client.module.impl.render.EntityCulling;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/*
 * EntityCulling port: CullingThread (meldexun.entityculling.util).
 * Async daemon that ray-traces every entity + tile entity AABB against the
 * opaque-block grid and flags hidden ones (Entity.culled / TileEntity.culled,
 * plus shadowCulled for the OptiFine shadow pass). PvP-safe exceptions come
 * from EntityCulling.Config (players/bosses/named/view entity always render).
 * RenderLib caches replaced by direct vanilla calls:
 *   - hasRenderer()        -> dropped (RenderManager handles null renders)
 *   - isChunkLoaded()      -> dropped (loadedEntityList implies loaded chunk)
 *   - cached bounding box  -> entity.getEntityBoundingBox() /
 *                             tileEntity.getRenderBoundingBox()
 */
public class EntityCullingThread extends Thread {

    private static final int CACHE_SIZE_CHUNKS = 12;

    private final EntityCulling.Config config;
    private final CachedBlockAccess cachedBlockAccess = new CachedBlockAccess();
    private final RaytracingEngine engine;

    private boolean spectator;
    private double camX;
    private double camY;
    private double camZ;
    private double x;
    private double y;
    private double z;
    private boolean printedCrash;

    public EntityCullingThread(EntityCulling.Config config) {
        super("Entity Culling");
        this.setDaemon(true);
        this.config = config;
        this.engine = new RaytracingEngine(CACHE_SIZE_CHUNKS,
            (x, y, z) -> this.cachedBlockAccess.getBlockState(x, y, z).getBlock().isOpaqueCube(),
            () -> Minecraft.getMinecraft().gameSettings.renderDistanceChunks,
            () -> this.config.distanceLimitAdder);
    }

    @Override
    public void run() {
        Minecraft mc = Minecraft.getMinecraft();

        while (!Thread.currentThread().isInterrupted()) {
            long t = System.nanoTime();

            try {
                World world = mc.theWorld;
                EntityPlayer player = mc.thePlayer;
                Entity renderViewEntity = mc.getRenderViewEntity();

                if (world != null && player != null && renderViewEntity != null) {
                    this.cachedBlockAccess.init(world);
                    this.spectator = player.isSpectator();
                    float partialTicks = mc.timer.renderPartialTicks;
                    this.x = renderViewEntity.lastTickPosX + (renderViewEntity.posX - renderViewEntity.lastTickPosX) * partialTicks;
                    this.y = renderViewEntity.lastTickPosY + (renderViewEntity.posY - renderViewEntity.lastTickPosY) * partialTicks;
                    this.z = renderViewEntity.lastTickPosZ + (renderViewEntity.posZ - renderViewEntity.lastTickPosZ) * partialTicks;
                    Vec3 cameraPosition = ActiveRenderInfo.getPosition();
                    this.camX = this.x + cameraPosition.xCoord;
                    this.camY = this.y + cameraPosition.yCoord;
                    this.camZ = this.z + cameraPosition.zCoord;
                    this.engine.setup(this.camX, this.camY, this.camZ);

                    Iterator<Entity> entityIterator = world.loadedEntityList.iterator();
                    while (entityIterator.hasNext()) {
                        Entity entity;
                        try {
                            entity = entityIterator.next();
                        } catch (ConcurrentModificationException | NoSuchElementException e) {
                            break;
                        }
                        if (entity == null) {
                            continue;
                        }
                        this.updateEntityCullingState(entity);
                    }

                    Iterator<TileEntity> tileEntityIterator = world.loadedTileEntityList.iterator();
                    while (tileEntityIterator.hasNext()) {
                        TileEntity tileEntity;
                        try {
                            tileEntity = tileEntityIterator.next();
                        } catch (ConcurrentModificationException | NoSuchElementException e) {
                            break;
                        }
                        if (tileEntity == null) {
                            continue;
                        }
                        this.updateTileEntityCullingState(tileEntity);
                    }
                }
            } catch (Throwable e) {
                /* never kill the client over an async culling failure: log once, keep going */
                if (!this.printedCrash) {
                    this.printedCrash = true;
                    e.printStackTrace();
                }
            } finally {
                this.cachedBlockAccess.clear();
                this.engine.clearCache();
            }

            double d = (System.nanoTime() - t) / 1_000_000.0D;
            long sleepTime = 10 - (long) d;
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void updateEntityCullingState(Entity entity) {
        entity.culled = !this.checkEntityVisibility(entity);
        if (this.config.cullShadows) {
            entity.shadowCulled = !this.checkEntityShadowVisibility(entity);
        } else {
            entity.shadowCulled = false;
        }
    }

    private void updateTileEntityCullingState(TileEntity tileEntity) {
        tileEntity.culled = !this.checkTileEntityVisibility(tileEntity);
        if (this.config.cullShadows) {
            tileEntity.shadowCulled = !this.checkTileEntityShadowVisibility(tileEntity);
        } else {
            tileEntity.shadowCulled = false;
        }
    }

    private boolean checkEntityVisibility(Entity entity) {
        if (this.config.disabledInSpectator && this.spectator) {
            return true;
        }
        if (this.config.alwaysRenderBosses && (entity instanceof EntityDragon || entity instanceof EntityWither)) {
            return true;
        }
        if (this.config.alwaysRenderNamed && entity.getAlwaysRenderNameTagForRender()) {
            return true;
        }
        if (this.config.alwaysRenderPlayers && entity instanceof EntityPlayer) {
            return true;
        }
        if (entity == Minecraft.getMinecraft().getRenderViewEntity()) {
            return true;
        }
        if (entity instanceof EntityEnderCrystal) {
            return true;
        }

        // Occlusion culling is only ever meant for things BEHIND the camera. If any part of the
        // entity's bounding box is in front of the camera plane, it is visible (or will be by the
        // time it renders) - never cull it. Testing the whole box rather than just the center means
        // mobs at an angle, or close to the camera edge, still render instead of popping out.
        if (this.isInFrontOfCamera(entity)) {
            return true;
        }

        net.minecraft.util.AxisAlignedBB aabb = entity.getEntityBoundingBox();
        if (aabb == null || aabb.maxX - aabb.minX > this.config.sizeLimit
                || aabb.maxY - aabb.minY > this.config.sizeLimit
                || aabb.maxZ - aabb.minZ > this.config.sizeLimit) {
            return true;
        }

        return this.checkBox(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
    }

    /**
     * True if any of the entity's bounding-box corners lies in front of the camera plane. Uses the
     * look vector; a positive dot against any corner means the entity is (at least partly) in front,
     * so it must render. This deliberately favors not culling over culling near the camera plane.
     */
    private boolean isInFrontOfCamera(Entity entity) {
        Entity renderViewEntity = Minecraft.getMinecraft().getRenderViewEntity();
        if (renderViewEntity == null) {
            return true;
        }
        net.minecraft.util.Vec3 look = renderViewEntity.getLook(
            Minecraft.getMinecraft().timer.renderPartialTicks);
        net.minecraft.util.AxisAlignedBB box = entity.getEntityBoundingBox();
        if (box == null) {
            return true;
        }

        double lx = look.xCoord, ly = look.yCoord, lz = look.zCoord;
        // If any corner is in front of the camera plane (dot > 0), render the entity.
        return this.cornerInFront(box.minX, box.minY, box.minZ, lx, ly, lz)
            || this.cornerInFront(box.maxX, box.minY, box.minZ, lx, ly, lz)
            || this.cornerInFront(box.minX, box.maxY, box.minZ, lx, ly, lz)
            || this.cornerInFront(box.minX, box.minY, box.maxZ, lx, ly, lz)
            || this.cornerInFront(box.maxX, box.maxY, box.minZ, lx, ly, lz)
            || this.cornerInFront(box.maxX, box.minY, box.maxZ, lx, ly, lz)
            || this.cornerInFront(box.minX, box.maxY, box.maxZ, lx, ly, lz)
            || this.cornerInFront(box.maxX, box.maxY, box.maxZ, lx, ly, lz);
    }

    private boolean cornerInFront(double x, double y, double z, double lx, double ly, double lz) {
        double ex = x - this.camX;
        double ey = y - this.camY;
        double ez = z - this.camZ;
        return ex * lx + ey * ly + ez * lz > 0.0D;
    }

    /**
     * Render-thread override: true if any corner of the entity's bounding box is in front of the
     * live camera. Used to salvage entities that the async thread flagged culled but that are now
     * in view (the async camera/look can lag a frame). Safe to call from the render thread.
     */
    public static boolean isInFrontOfCameraLive(Entity entity) {
        Entity renderViewEntity = Minecraft.getMinecraft().getRenderViewEntity();
        if (renderViewEntity == null || entity == null) {
            return true;
        }
        net.minecraft.util.Vec3 look = renderViewEntity.getLook(
            Minecraft.getMinecraft().timer.renderPartialTicks);
        net.minecraft.util.AxisAlignedBB box = entity.getEntityBoundingBox();
        if (box == null) {
            return true;
        }
        double lx = look.xCoord, ly = look.yCoord, lz = look.zCoord;
        double camX = renderViewEntity.posX;
        double camY = renderViewEntity.posY + renderViewEntity.getEyeHeight();
        double camZ = renderViewEntity.posZ;
        return cornerInFrontStatic(box.minX, box.minY, box.minZ, camX, camY, camZ, lx, ly, lz)
            || cornerInFrontStatic(box.maxX, box.minY, box.minZ, camX, camY, camZ, lx, ly, lz)
            || cornerInFrontStatic(box.minX, box.maxY, box.minZ, camX, camY, camZ, lx, ly, lz)
            || cornerInFrontStatic(box.minX, box.minY, box.maxZ, camX, camY, camZ, lx, ly, lz)
            || cornerInFrontStatic(box.maxX, box.maxY, box.minZ, camX, camY, camZ, lx, ly, lz)
            || cornerInFrontStatic(box.maxX, box.minY, box.maxZ, camX, camY, camZ, lx, ly, lz)
            || cornerInFrontStatic(box.minX, box.maxY, box.maxZ, camX, camY, camZ, lx, ly, lz)
            || cornerInFrontStatic(box.maxX, box.maxY, box.maxZ, camX, camY, camZ, lx, ly, lz);
    }

    private static boolean cornerInFrontStatic(double x, double y, double z,
                                               double camX, double camY, double camZ,
                                               double lx, double ly, double lz) {
        double ex = x - camX;
        double ey = y - camY;
        double ez = z - camZ;
        return ex * lx + ey * ly + ez * lz > 0.0D;
    }

    private boolean checkTileEntityVisibility(TileEntity tileEntity) {
        if (!this.config.cullTileEntities) {
            return true;
        }
        if (this.config.disabledInSpectator && this.spectator) {
            return true;
        }

        net.minecraft.util.BlockPos pos = tileEntity.getPos();
        net.minecraft.util.AxisAlignedBB aabb = new net.minecraft.util.AxisAlignedBB(
                pos.getX() - 0.5D, pos.getY() - 0.5D, pos.getZ() - 0.5D,
                pos.getX() + 1.5D, pos.getY() + 1.5D, pos.getZ() + 1.5D);
        if (aabb.maxX - aabb.minX > this.config.sizeLimit
                || aabb.maxY - aabb.minY > this.config.sizeLimit
                || aabb.maxZ - aabb.minZ > this.config.sizeLimit) {
            return true;
        }

        // Same forward-frustum rule as entities: never cull tile entities in front of the camera.
        double ex = pos.getX() + 0.5D - this.camX;
        double ey = pos.getY() + 0.5D - this.camY;
        double ez = pos.getZ() + 0.5D - this.camZ;
        Entity renderViewEntity = Minecraft.getMinecraft().getRenderViewEntity();
        if (renderViewEntity != null) {
            net.minecraft.util.Vec3 look = renderViewEntity.getLook(
                Minecraft.getMinecraft().timer.renderPartialTicks);
            if (ex * look.xCoord + ey * look.yCoord + ez * look.zCoord > 0.0D) {
                return true;
            }
        }

        return this.checkBox(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
    }

    private boolean checkEntityShadowVisibility(Entity entity) {
        if (this.config.disabledInSpectator && this.spectator) {
            return true;
        }
        if (!entity.culled) {
            return true;
        }
        return this.engine.raytraceUncachedThreshold(entity.posX, entity.posY + entity.height * 0.5D, entity.posZ,
                this.config.shadowDiff);
    }

    private boolean checkTileEntityShadowVisibility(TileEntity tileEntity) {
        if (this.config.disabledInSpectator && this.spectator) {
            return true;
        }
        if (!tileEntity.culled) {
            return true;
        }
        net.minecraft.util.BlockPos pos = tileEntity.getPos();
        return this.engine.raytraceUncachedThreshold(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                this.config.shadowDiff);
    }

    private boolean checkBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (this.config.enableRaytraceCache) {
            return this.checkBoxCached(minX, minY, minZ, maxX, maxY, maxZ);
        }
        return this.checkBoxUncached(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private boolean checkBoxCached(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        int startX = MathUtil.floor(minX);
        int startY = MathUtil.floor(minY);
        int startZ = MathUtil.floor(minZ);
        int endX = MathUtil.ceil(maxX);
        int endY = MathUtil.ceil(maxY);
        int endZ = MathUtil.ceil(maxZ);

        if (this.camX >= startX && this.camX <= endX
                && this.camY >= startY && this.camY <= endY
                && this.camZ >= startZ && this.camZ <= endZ) {
            return true;
        }
        if (this.camX < startX) {
            if (this.checkRectangleCached(startY, endY, startZ, endZ, startX, this::raytraceCachedYZX)) {
                return true;
            }
        } else if (this.camX > endX) {
            if (this.checkRectangleCached(startY, endY, startZ, endZ, endX, this::raytraceCachedYZX)) {
                return true;
            }
        }
        if (this.camY < startY) {
            int x0 = this.camX < startX ? startX + 1 : startX;
            int x1 = this.camX > endX ? endX - 1 : endX;
            if (this.checkRectangleCached(x0, x1, startZ, endZ, startY, this::raytraceCachedXZY)) {
                return true;
            }
        } else if (this.camY > endY) {
            int x0 = this.camX < startX ? startX + 1 : startX;
            int x1 = this.camX > endX ? endX - 1 : endX;
            if (this.checkRectangleCached(x0, x1, startZ, endZ, endY, this::raytraceCachedXZY)) {
                return true;
            }
        }
        if (this.camZ < startZ) {
            int x0 = this.camX < startX ? startX + 1 : startX;
            int x1 = this.camX > endX ? endX - 1 : endX;
            int y0 = this.camY < startY ? startY + 1 : startY;
            int y1 = this.camY > endY ? endY - 1 : endY;
            if (this.checkRectangleCached(x0, x1, y0, y1, startZ, this::raytraceCachedXYZ)) {
                return true;
            }
        } else if (this.camZ > endZ) {
            int x0 = this.camX < startX ? startX + 1 : startX;
            int x1 = this.camX > endX ? endX - 1 : endX;
            int y0 = this.camY < startY ? startY + 1 : startY;
            int y1 = this.camY > endY ? endY - 1 : endY;
            if (this.checkRectangleCached(x0, x1, y0, y1, endZ, this::raytraceCachedXYZ)) {
                return true;
            }
        }

        return false;
    }

    private boolean raytraceCachedYZX(int y, int z, int x) {
        return this.engine.raytraceCachedThreshold(x, y, z, this.config.raytraceThreshold);
    }

    private boolean raytraceCachedXZY(int x, int z, int y) {
        return this.engine.raytraceCachedThreshold(x, y, z, this.config.raytraceThreshold);
    }

    private boolean raytraceCachedXYZ(int x, int y, int z) {
        return this.engine.raytraceCachedThreshold(x, y, z, this.config.raytraceThreshold);
    }

    private static boolean checkRectangleCached(int minX, int maxX, int minY, int maxY, int z, RaytracingEngine.IntIntInt2BooleanFunction f) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (f.apply(x, y, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkBoxUncached(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (this.camX >= minX && this.camX <= maxX
                && this.camY >= minY && this.camY <= maxY
                && this.camZ >= minZ && this.camZ <= maxZ) {
            return true;
        }

        double deltaX = maxX - minX;
        double deltaY = maxY - minY;
        double deltaZ = maxZ - minZ;
        int stepsX = MathUtil.ceil(deltaX);
        int stepsY = MathUtil.ceil(deltaY);
        int stepsZ = MathUtil.ceil(deltaZ);
        double dx = deltaX / stepsX;
        double dy = deltaY / stepsY;
        double dz = deltaZ / stepsZ;

        if (this.camX < minX) {
            if (this.checkRectangleUncached(0, stepsY, minY, dy, 0, stepsZ, minZ, dz, minX, this::raytraceUncachedYZX)) {
                return true;
            }
        } else if (this.camX > maxX) {
            if (this.checkRectangleUncached(0, stepsY, minY, dy, 0, stepsZ, minZ, dz, maxX, this::raytraceUncachedYZX)) {
                return true;
            }
        }
        if (this.camY < minY) {
            int x0 = this.camX < minX ? 1 : 0;
            int x1 = this.camX > maxX ? stepsX - 1 : stepsX;
            if (this.checkRectangleUncached(x0, x1, minX, dx, 0, stepsZ, minZ, dz, minY, this::raytraceUncachedXZY)) {
                return true;
            }
        } else if (this.camY > maxY) {
            int x0 = this.camX < minX ? 1 : 0;
            int x1 = this.camX > maxX ? stepsX - 1 : stepsX;
            if (this.checkRectangleUncached(x0, x1, minX, dx, 0, stepsZ, minZ, dz, maxY, this::raytraceUncachedXZY)) {
                return true;
            }
        }
        if (this.camZ < minZ) {
            int x0 = this.camX < minX ? 1 : 0;
            int x1 = this.camX > maxX ? stepsX - 1 : stepsX;
            int y0 = this.camY < minY ? 1 : 0;
            int y1 = this.camY > maxY ? stepsY - 1 : stepsY;
            if (this.checkRectangleUncached(x0, x1, minX, dx, y0, y1, minY, dy, minZ, this::raytraceUncachedXYZ)) {
                return true;
            }
        } else if (this.camZ > maxZ) {
            int x0 = this.camX < minX ? 1 : 0;
            int x1 = this.camX > maxX ? stepsX - 1 : stepsX;
            int y0 = this.camY < minY ? 1 : 0;
            int y1 = this.camY > maxY ? stepsY - 1 : stepsY;
            if (this.checkRectangleUncached(x0, x1, minX, dx, y0, y1, minY, dy, maxZ, this::raytraceUncachedXYZ)) {
                return true;
            }
        }

        return false;
    }

    private boolean raytraceUncachedYZX(double y, double z, double x) {
        return this.engine.raytraceUncachedThreshold(x, y, z, this.config.raytraceThreshold);
    }

    private boolean raytraceUncachedXZY(double x, double z, double y) {
        return this.engine.raytraceUncachedThreshold(x, y, z, this.config.raytraceThreshold);
    }

    private boolean raytraceUncachedXYZ(double x, double y, double z) {
        return this.engine.raytraceUncachedThreshold(x, y, z, this.config.raytraceThreshold);
    }

    private static boolean checkRectangleUncached(int x0, int x1, double minX, double dx, int y0, int y1, double minY, double dy, double z, DoubleDoubleDouble2BooleanFunction f) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                if (f.apply(minX + x * dx, minY + y * dy, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    @FunctionalInterface
    private interface DoubleDoubleDouble2BooleanFunction {
        boolean apply(double x, double y, double z);
    }

}
