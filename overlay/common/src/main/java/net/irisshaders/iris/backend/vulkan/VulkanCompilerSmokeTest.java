package net.irisshaders.iris.backend.vulkan;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.irisshaders.iris.Iris;

public final class VulkanCompilerSmokeTest {
    private static final String VERTEX = """
        #version 450
        layout(location = 0) in vec3 Position;

        void main() {
            gl_Position = vec4(Position, 1.0);
        }
        """;

    private static final String FRAGMENT = """
        #version 450
        layout(location = 0) out vec4 fragColor;

        void main() {
            fragColor = vec4(1.0, 0.0, 1.0, 1.0);
        }
        """;

    private VulkanCompilerSmokeTest() {
    }

    public static void run() {
        try (IrisVulkanPipeline ignored = RenderPearlVulkanCompiler.compileGraphics(
                "compiler_smoke_test",
                VERTEX,
                FRAGMENT,
                DefaultVertexFormat.POSITION,
                PrimitiveTopology.TRIANGLES)) {
            Iris.logger.info("Iris Vulkan compiler smoke test passed.");
        } catch (Throwable t) {
            Iris.logger.error("Iris Vulkan compiler smoke test failed.", t);
        }
    }
}
