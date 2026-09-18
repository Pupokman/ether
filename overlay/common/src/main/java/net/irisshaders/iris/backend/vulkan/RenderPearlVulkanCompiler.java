package net.irisshaders.iris.backend.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.frontend.shaders.PipelineBuilder;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.mixin.GpuDeviceAccessor;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Compiles Iris-generated GLSL through Minecraft 26.3's RenderPearl frontend.
 * RenderPearl performs GLSL -> SPIR-V for Vulkan and creates the backend
 * pipeline; Iris never creates a GlProgram on this path.
 */
public final class RenderPearlVulkanCompiler {
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    private RenderPearlVulkanCompiler() {
    }

    public static IrisVulkanPipeline compileGraphics(
            String debugName,
            String vertexSource,
            String fragmentSource,
            VertexFormat vertexFormat,
            PrimitiveTopology topology) {
        RenderSystem.assertOnRenderThread();

        Objects.requireNonNull(debugName, "debugName");
        Objects.requireNonNull(vertexSource, "vertexSource");
        Objects.requireNonNull(fragmentSource, "fragmentSource");
        Objects.requireNonNull(vertexFormat, "vertexFormat");
        Objects.requireNonNull(topology, "topology");

        Identifier id = Identifier.fromNamespaceAndPath(
            "iris",
            "vulkan/runtime/" + sanitize(debugName));

        RenderPipeline description = RenderPipeline.builder()
            .withLocation(id)
            .withVertexShader(id)
            .withFragmentShader(id)
            .withVertexBinding(0, vertexFormat)
            .withPrimitiveTopology(topology)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .build();

        var device = RenderSystem.getDevice();
        var backend = ((GpuDeviceAccessor) device).getBackend();

        try (PipelineBuilder compiler = new PipelineBuilder(backend);
             IrisVulkanShaderSource source =
                 new IrisVulkanShaderSource(id, vertexSource, fragmentSource)) {
            var pending = compiler
                .compilePipeline(description, source, DIRECT_EXECUTOR)
                .join();

            var compiled = pending.finishCompile();
            Iris.logger.info(
                "Compiled Vulkan RenderPearl pipeline [{}] using backend [{}]",
                debugName,
                RenderSystem.getBackendDescription());

            return new IrisVulkanPipeline(description, compiled);
        }
    }

    private static String sanitize(String name) {
        StringBuilder result = new StringBuilder(name.length());

        for (int i = 0; i < name.length(); i++) {
            char c = Character.toLowerCase(name.charAt(i));
            if ((c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '/'
                || c == '_'
                || c == '-'
                || c == '.') {
                result.append(c);
            } else {
                result.append('_');
            }
        }

        return result.toString();
    }
}
