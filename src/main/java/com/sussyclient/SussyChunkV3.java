package com.sussyclient;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.*;

/**
 * Sussy Chunk v3: flags chunks with fully grown amethyst clusters below a Y level.
 *  - Smart Adjustment: skips clusters that sit inside a natural geode (calcite / smooth basalt shell nearby).
 *  - Encloser: only counts clusters whose air pocket is small and sealed.
 *  - Min Flags: how many remaining clusters a chunk needs before it is shown.
 */
public class SussyChunkV3 {
    private static final ChunkQueue queue = new ChunkQueue();
    private static final Map<Long, Integer> flags = new HashMap<>();
    private static final Set<Long> notified = new HashSet<>();

    public static void tick(Minecraft mc) {
        Config c = Config.I;
        if (!c.v3Enabled || mc.level == null || mc.player == null) {
            if (!flags.isEmpty()) { flags.clear(); notified.clear(); queue.clear(); }
            return;
        }
        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
        if (queue.refresh(pcx, pcz, c.v3SimDistance)) {
            flags.keySet().removeIf(queue::outOfRange);
            notified.retainAll(flags.keySet());
        }
        for (int n = 0; n < c.v3ScanSpeed; n++) {
            int[] cp = queue.poll();
            if (cp == null) break;
            long k = ChunkQueue.key(cp[0], cp[1]);
            LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cp[0], cp[1]);
            if (chunk == null) { flags.remove(k); notified.remove(k); continue; }
            int count = scan(chunk, mc.level, cp[0], cp[1], c);
            flags.put(k, count);
            if (count >= c.v3MinFlags) {
                if (notified.add(k) && c.v3Chat)
                    mc.player.displayClientMessage(Component.literal("§d[SussyChunk v3] §fSus chunk at "
                            + (cp[0] * 16) + ", " + (cp[1] * 16) + " §7(" + count + " flags)"), false);
            } else notified.remove(k);
        }
    }

    private static int scan(LevelChunk chunk, ClientLevel level, int cx, int cz, Config c) {
        int count = 0;
        LevelChunkSection[] sections = chunk.getSections();
        int bottomSection = level.getMinY() >> 4;
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection s = sections[i];
            int baseY = (bottomSection + i) * 16;
            if (baseY >= c.v3MaxY) break;
            if (s == null || s.hasOnlyAir() || !s.maybeHas(st -> st.is(Blocks.AMETHYST_CLUSTER))) continue;
            for (int x = 0; x < 16; x++)
                for (int y = 0; y < 16; y++) {
                    int wy = baseY + y;
                    if (wy >= c.v3MaxY) continue;
                    for (int z = 0; z < 16; z++) {
                        if (!s.getBlockState(x, y, z).is(Blocks.AMETHYST_CLUSTER)) continue;
                        mut.set(cx * 16 + x, wy, cz * 16 + z);
                        if (c.v3Smart && nearGeodeShell(level, mut, c.v3ShellDistance)) continue;
                        if (c.v3Encloser && !enclosed(level, mut, c.v3MaxPocket)) continue;
                        count++;
                    }
                }
        }
        return count;
    }

    private static boolean nearGeodeShell(ClientLevel level, BlockPos origin, int d) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -d; dx <= d; dx++)
            for (int dy = -d; dy <= d; dy++)
                for (int dz = -d; dz <= d; dz++) {
                    m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState st = level.getBlockState(m);
                    if (st.is(Blocks.CALCITE) || st.is(Blocks.SMOOTH_BASALT) || st.is(Blocks.AMETHYST_BLOCK)
                            || st.is(Blocks.BUDDING_AMETHYST)) return true;
                }
        return false;
    }

    /** True if the air next to this block forms a pocket of at most maxPocket air blocks. */
    private static boolean enclosed(ClientLevel level, BlockPos origin, int maxPocket) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        ArrayDeque<Long> todo = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        for (Direction d : Direction.values()) {
            m.set(origin.getX() + d.getStepX(), origin.getY() + d.getStepY(), origin.getZ() + d.getStepZ());
            if (level.getBlockState(m).isAir()) {
                long l = m.asLong();
                if (seen.add(l)) todo.add(l);
            }
        }
        if (todo.isEmpty()) return false;
        while (!todo.isEmpty()) {
            long cur = todo.poll();
            int x = BlockPos.getX(cur), y = BlockPos.getY(cur), z = BlockPos.getZ(cur);
            for (Direction d : Direction.values()) {
                m.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
                if (!level.getBlockState(m).isAir()) continue;
                long l = m.asLong();
                if (seen.add(l)) {
                    if (seen.size() > maxPocket) return false; // leaks out of a small pocket
                    todo.add(l);
                }
            }
        }
        return true;
    }

    public static void render(WorldRenderContext ctx) {
        Config c = Config.I;
        Minecraft mc = Minecraft.getInstance();
        if (!c.v3Enabled || mc.level == null || flags.isEmpty()) return;
        List<ChunkMarkers.Box> boxes = new ArrayList<>();
        double margin = (16 - 16 * (c.v3BoxSize / 100.0)) / 2.0;
        double y1 = mc.level.getMinY();
        for (Map.Entry<Long, Integer> e : flags.entrySet()) {
            if (e.getValue() < c.v3MinFlags) continue;
            ChunkMarkers.Box b = new ChunkMarkers.Box();
            b.x1 = ChunkQueue.keyX(e.getKey()) * 16 + margin; b.x2 = ChunkQueue.keyX(e.getKey()) * 16 + 16 - margin;
            b.z1 = ChunkQueue.keyZ(e.getKey()) * 16 + margin; b.z2 = ChunkQueue.keyZ(e.getKey()) * 16 + 16 - margin;
            b.y1 = y1; b.y2 = y1 + c.v3BoxHeight;
            b.r = c.v3Red / 255f; b.g = c.v3Green / 255f; b.b = c.v3Blue / 255f; b.a = c.v3Alpha / 100f;
            b.fill = c.v3Fill; b.outline = c.v3Outline; b.thickness = c.v3Thickness;
            boxes.add(b);
        }
        ChunkMarkers.render(ctx, boxes, c.v3ThroughWalls);
    }
}
