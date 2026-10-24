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
 * Sus Patterns: flags chunks by natural-block pattern analysis.
 * Score = stripped budding amethyst (budding amethyst with no buds/clusters touching it)
 *       + any enabled plants (vines, kelp, berries, cane, cocoa) found below the plant Y limit.
 */
public class SusPatterns {
    private static final ChunkQueue queue = new ChunkQueue();
    private static final Map<Long, Integer> scores = new HashMap<>();
    private static final Set<Long> notified = new HashSet<>();

    public static void tick(Minecraft mc) {
        Config c = Config.I;
        if (!c.spEnabled || mc.level == null || mc.player == null) {
            if (!scores.isEmpty()) { scores.clear(); notified.clear(); queue.clear(); }
            return;
        }
        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
        if (queue.refresh(pcx, pcz, c.spRadius)) {
            scores.keySet().removeIf(queue::outOfRange);
            notified.retainAll(scores.keySet());
        }
        for (int n = 0; n < c.spScanSpeed; n++) {
            int[] cp = queue.poll();
            if (cp == null) break;
            long k = ChunkQueue.key(cp[0], cp[1]);
            LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cp[0], cp[1]);
            if (chunk == null) { scores.remove(k); notified.remove(k); continue; }
            int score = scan(chunk, mc.level, cp[0], cp[1], c);
            scores.put(k, score);
            if (score >= c.spSensitivity) {
                if (notified.add(k) && c.spChat)
                    mc.player.displayClientMessage(Component.literal("§d[SusPatterns] §fSus chunk at "
                            + (cp[0] * 16) + ", " + (cp[1] * 16) + " §7(score " + score + ")"), false);
            } else notified.remove(k);
        }
    }

    private static boolean plantEnabled(BlockState s, Config c) {
        return (c.spJungleVines && s.is(Blocks.VINE))
                || (c.spCaveVines && (s.is(Blocks.CAVE_VINES) || s.is(Blocks.CAVE_VINES_PLANT)))
                || (c.spKelp && (s.is(Blocks.KELP) || s.is(Blocks.KELP_PLANT)))
                || (c.spBerries && s.is(Blocks.SWEET_BERRY_BUSH))
                || (c.spSugarCane && s.is(Blocks.SUGAR_CANE))
                || (c.spCocoa && s.is(Blocks.COCOA));
    }

    private static boolean interesting(BlockState s, Config c) {
        return (c.spAmethyst && s.is(Blocks.BUDDING_AMETHYST)) || plantEnabled(s, c);
    }

    private static boolean isBud(BlockState s) {
        return s.is(Blocks.SMALL_AMETHYST_BUD) || s.is(Blocks.MEDIUM_AMETHYST_BUD)
                || s.is(Blocks.LARGE_AMETHYST_BUD) || s.is(Blocks.AMETHYST_CLUSTER);
    }

    private static int scan(LevelChunk chunk, ClientLevel level, int cx, int cz, Config c) {
        int score = 0;
        LevelChunkSection[] sections = chunk.getSections();
        int bottomSection = level.getMinY() >> 4;
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection s = sections[i];
            if (s == null || s.hasOnlyAir() || !s.maybeHas(st -> interesting(st, c))) continue;
            int baseY = (bottomSection + i) * 16;
            for (int x = 0; x < 16; x++)
                for (int y = 0; y < 16; y++)
                    for (int z = 0; z < 16; z++) {
                        BlockState st = s.getBlockState(x, y, z);
                        if (!interesting(st, c)) continue;
                        int wy = baseY + y;
                        if (c.spAmethyst && st.is(Blocks.BUDDING_AMETHYST)) {
                            boolean hasBud = false;
                            for (Direction d : Direction.values()) {
                                mut.set(cx * 16 + x + d.getStepX(), wy + d.getStepY(), cz * 16 + z + d.getStepZ());
                                if (isBud(level.getBlockState(mut))) { hasBud = true; break; }
                            }
                            if (!hasBud) score++;
                        } else if (wy < c.spPlantMaxY) {
                            score++;
                        }
                    }
        }
        return score;
    }

    public static void render(WorldRenderContext ctx) {
        Config c = Config.I;
        Minecraft mc = Minecraft.getInstance();
        if (!c.spEnabled || mc.level == null || scores.isEmpty()) return;
        List<ChunkMarkers.Box> boxes = new ArrayList<>();
        double margin = (16 - 16 * (c.spBoxSize / 100.0)) / 2.0;
        double y1 = mc.level.getMinY();
        for (Map.Entry<Long, Integer> e : scores.entrySet()) {
            if (e.getValue() < c.spSensitivity) continue;
            ChunkMarkers.Box b = new ChunkMarkers.Box();
            b.x1 = ChunkQueue.keyX(e.getKey()) * 16 + margin; b.x2 = ChunkQueue.keyX(e.getKey()) * 16 + 16 - margin;
            b.z1 = ChunkQueue.keyZ(e.getKey()) * 16 + margin; b.z2 = ChunkQueue.keyZ(e.getKey()) * 16 + 16 - margin;
            b.y1 = y1; b.y2 = y1 + c.spBoxHeight;
            b.r = c.spRed / 255f; b.g = c.spGreen / 255f; b.b = c.spBlue / 255f; b.a = c.spFillOpacity / 100f;
            b.fill = c.spFill; b.outline = c.spOutline; b.thickness = c.spThickness;
            boxes.add(b);
        }
        ChunkMarkers.render(ctx, boxes, c.spThroughWalls);
    }
}
