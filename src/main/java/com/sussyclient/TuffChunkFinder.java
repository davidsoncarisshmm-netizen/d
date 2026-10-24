package com.sussyclient;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.*;

/**
 * Tuff Chunk Finder: flags chunks with base indicators.
 * Powered repeaters, beehives, and (below the natural Y limit) cobbled deepslate, vines, seagrass and fully grown amethyst.
 */
public class TuffChunkFinder {
    private static final ChunkQueue queue = new ChunkQueue();
    private static final Map<Long, Integer> scores = new HashMap<>();
    private static final Set<Long> notified = new HashSet<>();

    public static void tick(Minecraft mc) {
        Config c = Config.I;
        if (!c.tcEnabled || mc.level == null || mc.player == null) {
            if (!scores.isEmpty()) { scores.clear(); notified.clear(); queue.clear(); }
            return;
        }
        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
        if (queue.refresh(pcx, pcz, c.tcRadius)) {
            scores.keySet().removeIf(queue::outOfRange);
            notified.retainAll(scores.keySet());
        }
        for (int n = 0; n < c.tcScanSpeed; n++) {
            int[] cp = queue.poll();
            if (cp == null) break;
            long k = ChunkQueue.key(cp[0], cp[1]);
            LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cp[0], cp[1]);
            if (chunk == null) { scores.remove(k); notified.remove(k); continue; }
            int score = scan(chunk, mc.level, c);
            scores.put(k, score);
            if (score >= c.tcSensitivity) {
                if (notified.add(k) && c.tcChat)
                    mc.player.displayClientMessage(Component.literal("§b[TuffChunk] §fBase indicators at "
                            + (cp[0] * 16) + ", " + (cp[1] * 16) + " §7(score " + score + ")"), false);
            } else notified.remove(k);
        }
    }

    private static boolean isStructure(BlockState s, Config c) {
        if (c.tcPoweredRepeaters || c.tcAnyRepeaters) {
            if (s.is(Blocks.REPEATER)) return true;
        }
        return c.tcHives && s.is(Blocks.BEEHIVE);
    }

    private static boolean isNatural(BlockState s, Config c) {
        return (c.tcCobbledDeepslate && s.is(Blocks.COBBLED_DEEPSLATE))
                || (c.tcVines && s.is(Blocks.VINE))
                || (c.tcSeagrass && (s.is(Blocks.SEAGRASS) || s.is(Blocks.TALL_SEAGRASS)))
                || (c.tcAmethyst && s.is(Blocks.AMETHYST_CLUSTER));
    }

    private static int scan(LevelChunk chunk, ClientLevel level, Config c) {
        int score = 0;
        LevelChunkSection[] sections = chunk.getSections();
        int bottomSection = level.getMinY() >> 4;
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection s = sections[i];
            if (s == null || s.hasOnlyAir() || !s.maybeHas(st -> isStructure(st, c) || isNatural(st, c))) continue;
            int baseY = (bottomSection + i) * 16;
            for (int x = 0; x < 16; x++)
                for (int y = 0; y < 16; y++) {
                    int wy = baseY + y;
                    for (int z = 0; z < 16; z++) {
                        BlockState st = s.getBlockState(x, y, z);
                        if (wy < c.tcMaxY && isStructure(st, c)) {
                            if (st.is(Blocks.REPEATER)) {
                                boolean powered = st.hasProperty(BlockStateProperties.POWERED) && st.getValue(BlockStateProperties.POWERED);
                                if (c.tcAnyRepeaters || (c.tcPoweredRepeaters && powered)) score++;
                            } else score++; // beehive
                        } else if (wy < c.tcNaturalMaxY && isNatural(st, c)) {
                            score++;
                        }
                    }
                }
        }
        return score;
    }

    public static void render(WorldRenderContext ctx) {
        Config c = Config.I;
        if (!c.tcEnabled || scores.isEmpty()) return;
        List<ChunkMarkers.Box> boxes = new ArrayList<>();
        double margin = (16 - 16 * (c.tcSlabSize / 100.0)) / 2.0;
        for (Map.Entry<Long, Integer> e : scores.entrySet()) {
            if (e.getValue() < c.tcSensitivity) continue;
            ChunkMarkers.Box b = new ChunkMarkers.Box();
            b.x1 = ChunkQueue.keyX(e.getKey()) * 16 + margin; b.x2 = ChunkQueue.keyX(e.getKey()) * 16 + 16 - margin;
            b.z1 = ChunkQueue.keyZ(e.getKey()) * 16 + margin; b.z2 = ChunkQueue.keyZ(e.getKey()) * 16 + 16 - margin;
            b.y1 = c.tcSlabY; b.y2 = c.tcSlabY + c.tcSlabThickness;
            b.r = c.tcRed / 255f; b.g = c.tcGreen / 255f; b.b = c.tcBlue / 255f; b.a = c.tcFillOpacity / 100f;
            b.fill = c.tcFill; b.outline = c.tcOutline; b.thickness = c.tcOutlineThickness;
            boxes.add(b);
        }
        ChunkMarkers.render(ctx, boxes, c.tcThroughWalls);
    }
}
