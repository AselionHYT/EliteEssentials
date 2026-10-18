package com.eliteessentials.util;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import org.joml.Vector3i;

import java.util.concurrent.CompletableFuture;

/**
 * Block and chunk-column access for Hytale 0.7.
 *
 * <p>0.7.0-pre.5 removed the block getters from {@code World} and {@code WorldChunk}
 * ({@code getChunk}, {@code getChunkIfLoaded}, {@code getChunkAsync}, {@code getBlockType},
 * {@code getFluidId}). Blocks are now read per section through a {@link SectionReader} on the
 * world's chunk store, and a column is loaded through the chunk store's reference getters.
 *
 * <p>Reading through a {@link SectionReader} loads nothing: a position in a section that is not
 * in memory reads as empty. Readers must be used on the world's thread.
 */
public final class WorldBlocks {

    private WorldBlocks() {
    }

    /** Whether the chunk column is in memory, so that its blocks can be read right now. */
    public static boolean isColumnInMemory(World world, long chunkIndex) {
        var reference = world.getChunkStore().getChunkReference(chunkIndex);
        return reference != null && reference.isValid();
    }

    /**
     * Loads or generates the chunk column.
     *
     * @return a future that completes with {@code true} once the column is in memory, and with
     *         {@code false} when it could not be loaded
     */
    public static CompletableFuture<Boolean> loadColumn(World world, long chunkIndex) {
        return world.getChunkStore().getChunkReferenceAsync(chunkIndex)
                .thenApply(reference -> reference != null && reference.isValid());
    }

    /** A reader for the world's blocks and fluids; takes world coordinates. */
    public static SectionReader reader(World world) {
        return new SectionReader(world.getChunkStore());
    }

    /** The block type at a position, or {@code null} for an unknown block id. */
    public static BlockType blockType(SectionReader reader, int x, int y, int z) {
        return BlockType.getAssetMap().getAsset(reader.getBlock(x, y, z));
    }

    /** One-off lookup; use {@link #reader(World)} when reading more than one block. */
    public static BlockType blockType(World world, Vector3i position) {
        return blockType(reader(world), position.x, position.y, position.z);
    }
}
