package com.sussyclient;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** Shared world-space box renderer used by the newer modules. */
public class ChunkMarkers {
    public static class Box {
        public double x1, y1, z1, x2, y2, z2, thickness;
        public float r, g, b, a;
        public boolean fill, outline;
    }

    private static final RenderPipeline THROUGH = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(SussyClient.ID, "pipeline/markers_through_walls"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .build());

    private static final RenderPipeline DEPTH = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(SussyClient.ID, "pipeline/markers_depth"))
                    .withCull(false)
                    .build());

    private static final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static BufferBuilder buffer;
    private static MappableRingBuffer vertexBuffer;
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    public static void render(WorldRenderContext context, List<Box> boxes, boolean throughWalls) {
        if (boxes.isEmpty()) return;
        RenderPipeline pipeline = throughWalls ? THROUGH : DEPTH;
        Minecraft mc = Minecraft.getInstance();

        PoseStack matrices = context.matrices();
        Vec3 cam = context.worldState().cameraRenderState.pos;
        matrices.pushPose();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4fc m = matrices.last().pose();

        buffer = new BufferBuilder(allocator, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
        for (Box b : boxes) {
            if (b.fill && b.a > 0) box(m, b.x1, b.y1, b.z1, b.x2, b.y2, b.z2, b.r, b.g, b.b, b.a);
            if (b.outline) {
                double t = Math.min(b.thickness, Math.max(0.01, (b.y2 - b.y1) / 2));
                // vertical pillars
                box(m, b.x1, b.y1, b.z1, b.x1 + t, b.y2, b.z1 + t, b.r, b.g, b.b, 1f);
                box(m, b.x2 - t, b.y1, b.z1, b.x2, b.y2, b.z1 + t, b.r, b.g, b.b, 1f);
                box(m, b.x1, b.y1, b.z2 - t, b.x1 + t, b.y2, b.z2, b.r, b.g, b.b, 1f);
                box(m, b.x2 - t, b.y1, b.z2 - t, b.x2, b.y2, b.z2, b.r, b.g, b.b, 1f);
                // top and bottom frames
                for (double yy : new double[]{b.y1, b.y2 - t}) {
                    box(m, b.x1, yy, b.z1, b.x2, yy + t, b.z1 + t, b.r, b.g, b.b, 1f);
                    box(m, b.x1, yy, b.z2 - t, b.x2, yy + t, b.z2, b.r, b.g, b.b, 1f);
                    box(m, b.x1, yy, b.z1, b.x1 + t, yy + t, b.z2, b.r, b.g, b.b, 1f);
                    box(m, b.x2 - t, yy, b.z1, b.x2, yy + t, b.z2, b.r, b.g, b.b, 1f);
                }
            }
        }
        matrices.popPose();

        MeshData built = buffer.buildOrThrow();
        MeshData.DrawState params = built.drawState();
        VertexFormat format = params.format();

        int size = params.vertexCount() * format.getVertexSize();
        if (vertexBuffer == null || vertexBuffer.size() < size) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new MappableRingBuffer(() -> SussyClient.ID + " marker vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(
                vertexBuffer.currentBuffer().slice(0, built.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(built.vertexBuffer(), view.data());
        }
        GpuBuffer vertices = vertexBuffer.currentBuffer();

        built.sortQuads(allocator, RenderSystem.getProjectionType().vertexSorting());
        GpuBuffer indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(built.indexBuffer());
        VertexFormat.IndexType indexType = built.drawState().indexType();

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> SussyClient.ID + " marker render",
                mc.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
                mc.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, indexType);
            pass.drawIndexed(0, 0, params.indexCount(), 1);
        }
        built.close();
        vertexBuffer.rotate();
        buffer = null;
    }

    private static void v(Matrix4fc m, double x, double y, double z, float r, float g, float b, float a) {
        buffer.addVertex(m, (float) x, (float) y, (float) z).setColor(r, g, b, a);
    }

    private static void box(Matrix4fc m, double x1, double y1, double z1, double x2, double y2, double z2,
                            float r, float g, float b, float a) {
        v(m, x1, y1, z2, r, g, b, a); v(m, x2, y1, z2, r, g, b, a); v(m, x2, y2, z2, r, g, b, a); v(m, x1, y2, z2, r, g, b, a);
        v(m, x2, y1, z1, r, g, b, a); v(m, x1, y1, z1, r, g, b, a); v(m, x1, y2, z1, r, g, b, a); v(m, x2, y2, z1, r, g, b, a);
        v(m, x1, y1, z1, r, g, b, a); v(m, x1, y1, z2, r, g, b, a); v(m, x1, y2, z2, r, g, b, a); v(m, x1, y2, z1, r, g, b, a);
        v(m, x2, y1, z2, r, g, b, a); v(m, x2, y1, z1, r, g, b, a); v(m, x2, y2, z1, r, g, b, a); v(m, x2, y2, z2, r, g, b, a);
        v(m, x1, y2, z2, r, g, b, a); v(m, x2, y2, z2, r, g, b, a); v(m, x2, y2, z1, r, g, b, a); v(m, x1, y2, z1, r, g, b, a);
        v(m, x1, y1, z1, r, g, b, a); v(m, x2, y1, z1, r, g, b, a); v(m, x2, y1, z2, r, g, b, a); v(m, x1, y1, z2, r, g, b, a);
    }

    public static void close() {
        allocator.close();
        if (vertexBuffer != null) { vertexBuffer.close(); vertexBuffer = null; }
    }
}
