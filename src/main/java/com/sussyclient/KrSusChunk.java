package com.sussyclient;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.*;

/**
 * Kr Sus Chunk: storage scanner. Chests and barrels count 1, shulker boxes count 2.
 * Chunks with a trial spawner or vault are treated as trial chambers and can be ignored.
 * Low color = reached the threshold, high color = twice the threshold or more.
 */
public class KrSusChunk {
    private static final ChunkQueue queue = new ChunkQueue();
    private static final Map<Long, Integer> scores = new HashMap<>();
    private static final Set<Long> notified = new HashSet<>();

    public static void tick(Minecraft mc) {
        Config c = Config.I;
        if (!c.krEnabled || mc.level == null || mc.player == null) {
            if (!scores.isEmpty()) { scores.clear(); notified.clear(); queue.clear(); }
            return;
        }
        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
        if (queue.refresh(pcx, pcz, c.krRadius)) {
            scores.keySet().removeIf(queue::outOfRange);
            notified.retainAll(scores.keySet());
        }
        for (int n = 0; n < c.krScanSpeed; n++) {
            int[] cp = queue.poll();
            if (cp == null) break;
            long k = ChunkQueue.key(cp[0], cp[1]);
            LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cp[0], cp[1]);
            if (chunk == null) { scores.remove(k); notified.remove(k); continue; }
            int score = scan(chunk, c);
            scores.put(k, score);
            if (score >= c.krThreshold) {
                if (notified.add(k) && c.krChat)
                    mc.player.displayClientMessage(Component.literal("§c[KrSusChunk] §fStorage chunk at "
                            + (cp[0] * 16) + ", " + (cp[1] * 16) + " §7(score " + score + ")"), false);
            } else notified.remove(k);
        }
    }

    private static int scan(LevelChunk chunk, Config c) {
        int score = 0;
        boolean trial = false;
        for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
            BlockState st = e.getValue().getBlockState();
            if (st.is(Blocks.TRIAL_SPAWNER) || st.is(Blocks.VAULT)) trial = true;
            if (c.krOnlyBelow && e.getKey().getY() >= c.krMaxY) continue;
            if (st.is(Blocks.CHEST) || st.is(Blocks.TRAPPED_CHEST) || st.is(Blocks.BARREL)) score += 1;
            else if (st.getBlock() instanceof ShulkerBoxBlock) score += 2;
        }
        return (trial && c.krIgnoreTrial) ? 0 : score;
    }

    public static void render(WorldRenderContext ctx) {
        Config c = Config.I;
        Minecraft mc = Minecraft.getInstance();
        if (!c.krEnabled || mc.level == null || scores.isEmpty()) return;
        List<ChunkMarkers.Box> boxes = new ArrayList<>();
        double margin = (16 - 16 * (c.krBoxSize / 100.0)) / 2.0;
        double y1 = mc.level.getMinY();
        for (Map.Entry<Long, Integer> e : scores.entrySet()) {
            int score = e.getValue();
            if (score < c.krThreshold) continue;
            boolean high = score >= c.krThreshold * 2;
            ChunkMarkers.Box b = new ChunkMarkers.Box();
            b.x1 = ChunkQueue.keyX(e.getKey()) * 16 + margin; b.x2 = ChunkQueue.keyX(e.getKey()) * 16 + 16 - margin;
            b.z1 = ChunkQueue.keyZ(e.getKey()) * 16 + margin; b.z2 = ChunkQueue.keyZ(e.getKey()) * 16 + 16 - margin;
            b.y1 = y1; b.y2 = y1 + c.krBoxHeight;
            b.r = (high ? c.krHighRed : c.krLowRed) / 255f;
            b.g = (high ? c.krHighGreen : c.krLowGreen) / 255f;
            b.b = (high ? c.krHighBlue : c.krLowBlue) / 255f;
            b.a = c.krFillOpacity / 100f;
            b.fill = c.krFill; b.outline = c.krOutline; b.thickness = c.krThickness;
            boxes.add(b);
        }
        ChunkMarkers.render(ctx, boxes, c.krThroughWalls);
    }
}
