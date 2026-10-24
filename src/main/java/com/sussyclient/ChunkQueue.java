package com.sussyclient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Round-robin queue of chunk coordinates around the player, nearest first. */
public class ChunkQueue {
    private final List<int[]> q = new ArrayList<>();
    private int idx = 0, cx = Integer.MIN_VALUE, cz = Integer.MIN_VALUE, r = -1;

    public static long key(int x, int z) { return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32); }
    public static int keyX(long k) { return (int) (k & 0xFFFFFFFFL); }
    public static int keyZ(long k) { return (int) (k >>> 32); }

    public int centerX() { return cx; }
    public int centerZ() { return cz; }
    public int radius() { return r; }

    /** Rebuilds when finished a lap, the player moved chunks, or the radius changed. Returns true if rebuilt. */
    public boolean refresh(int pcx, int pcz, int radius) {
        if (q.isEmpty() || idx >= q.size() || pcx != cx || pcz != cz || radius != r) {
            q.clear();
            idx = 0; cx = pcx; cz = pcz; r = radius;
            for (int x = -radius; x <= radius; x++)
                for (int z = -radius; z <= radius; z++)
                    if (x * x + z * z <= radius * radius) q.add(new int[]{pcx + x, pcz + z});
            q.sort(Comparator.comparingInt(p -> (p[0] - pcx) * (p[0] - pcx) + (p[1] - pcz) * (p[1] - pcz)));
            return true;
        }
        return false;
    }

    public int[] poll() { return idx < q.size() ? q.get(idx++) : null; }

    public boolean outOfRange(long k) {
        int dx = keyX(k) - cx, dz = keyZ(k) - cz;
        return dx * dx + dz * dz > r * r;
    }

    public void clear() { q.clear(); idx = 0; cx = cz = Integer.MIN_VALUE; r = -1; }
}
