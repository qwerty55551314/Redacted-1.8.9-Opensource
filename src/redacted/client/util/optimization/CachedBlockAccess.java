package redacted.client.util.optimization;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/*
 * EntityCulling port: CachedBlockAccess (meldexun.entityculling.util).
 * Async-thread block lookup with a one-section cache. ChunkProviderClient.provideChunk
 * returns the all-air blankChunk for unloaded columns, so rays pass through
 * unloaded terrain and nothing gets falsely culled. All lookups are defensive.
 */
public class CachedBlockAccess {

    private World world;
    private ExtendedBlockStorage cachedSection;
    private int cachedSectionX = Integer.MAX_VALUE;
    private int cachedSectionY = Integer.MAX_VALUE;
    private int cachedSectionZ = Integer.MAX_VALUE;

    public void init(World world) {
        this.world = world;
        this.cachedSection = null;
        this.cachedSectionX = Integer.MAX_VALUE;
        this.cachedSectionY = Integer.MAX_VALUE;
        this.cachedSectionZ = Integer.MAX_VALUE;
    }

    public void clear() {
        this.world = null;
        this.cachedSection = null;
        this.cachedSectionX = Integer.MAX_VALUE;
        this.cachedSectionY = Integer.MAX_VALUE;
        this.cachedSectionZ = Integer.MAX_VALUE;
    }

    public IBlockState getBlockState(int x, int y, int z) {
        try {
            ExtendedBlockStorage section = this.getChunkSection(x >> 4, y >> 4, z >> 4);
            if (section == null) {
                return Blocks.air.getDefaultState();
            }
            return section.get(x & 15, y & 15, z & 15);
        } catch (ArrayIndexOutOfBoundsException e) {
            return Blocks.air.getDefaultState();
        }
    }

    private ExtendedBlockStorage getChunkSection(int sectionX, int sectionY, int sectionZ) {
        if (this.cachedSectionX != sectionX || this.cachedSectionY != sectionY || this.cachedSectionZ != sectionZ) {
            this.cachedSection = this.getSection(this.world, sectionX, sectionY, sectionZ);
            this.cachedSectionX = sectionX;
            this.cachedSectionY = sectionY;
            this.cachedSectionZ = sectionZ;
        }
        return this.cachedSection;
    }

    private ExtendedBlockStorage getSection(World world, int sectionX, int sectionY, int sectionZ) {
        if (world == null) {
            return null;
        }
        if (sectionY < 0 || sectionY >= 16) {
            return null;
        }
        Chunk chunk = world.getChunkProvider().provideChunk(sectionX, sectionZ);
        if (chunk == null || chunk.isEmpty()) {
            return null;
        }
        return chunk.getBlockStorageArray()[sectionY];
    }

}
