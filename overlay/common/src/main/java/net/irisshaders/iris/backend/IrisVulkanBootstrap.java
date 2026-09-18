package net.irisshaders.iris.backend;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializerRegistry;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.irisshaders.iris.vertices.sodium.EntityToTerrainVertexSerializer;
import net.irisshaders.iris.vertices.sodium.GlyphExtVertexSerializer;
import net.irisshaders.iris.vertices.sodium.IrisEntityToTerrainVertexSerializer;
import net.irisshaders.iris.vertices.sodium.ModelToEntityVertexSerializer;

/**
 * Vulkan-only lifecycle which intentionally stops before the legacy OpenGL
 * rendering pipeline. Shaderpack parsing/options are kept alive so the Vulkan
 * renderer can consume the exact same Iris frontend.
 */
public final class IrisVulkanBootstrap {
    private static boolean initialized;

    private IrisVulkanBootstrap() {
    }

    public static synchronized void onMinecraftInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;

        if (!IrisBackendRuntime.isVulkan()) {
            return;
        }

        VertexSerializerRegistry.instance().registerSerializer(
            DefaultVertexFormat.ENTITY,
            IrisVertexFormats.TERRAIN,
            new EntityToTerrainVertexSerializer());
        VertexSerializerRegistry.instance().registerSerializer(
            IrisVertexFormats.ENTITY,
            IrisVertexFormats.TERRAIN,
            new IrisEntityToTerrainVertexSerializer());
        VertexSerializerRegistry.instance().registerSerializer(
            DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR,
            IrisVertexFormats.GLYPH,
            new GlyphExtVertexSerializer());
        VertexSerializerRegistry.instance().registerSerializer(
            DefaultVertexFormat.ENTITY,
            IrisVertexFormats.ENTITY,
            new ModelToEntityVertexSerializer());

        // Parses selected shaderpack, options, includes and directives.
        // No GlProgram/GlFramebuffer must be created on this path.
        Iris.loadShaderpack();

        Iris.logger.info(
            "Iris Vulkan frontend initialized. Shaderpack parsing is active; "
                + "RenderPearl pipeline backend will be attached next.");
    }
}
