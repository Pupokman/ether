package net.irisshaders.iris.backend.vulkan;

import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * In-memory shader source used to feed Iris-transformed GLSL into RenderPearl.
 * Iris has already resolved shaderpack includes before this stage, therefore
 * getInclude is only a defensive fallback.
 */
public final class IrisVulkanShaderSource implements ShaderSource {
    private final Identifier shaderId;
    private final String vertexSource;
    private final String fragmentSource;

    public IrisVulkanShaderSource(
            Identifier shaderId,
            String vertexSource,
            String fragmentSource) {
        this.shaderId = Objects.requireNonNull(shaderId, "shaderId");
        this.vertexSource = Objects.requireNonNull(vertexSource, "vertexSource");
        this.fragmentSource = Objects.requireNonNull(fragmentSource, "fragmentSource");
    }

    @Override
    public String getShader(Identifier id, ShaderType type) {
        if (!shaderId.equals(id)) {
            return null;
        }

        return switch (type) {
            case VERTEX -> vertexSource;
            case FRAGMENT -> fragmentSource;
        };
    }

    @Override
    public CachedIncludeSource getInclude(Identifier id) {
        // ProgramSource / Jcpp preprocessing in Iris resolves shaderpack
        // includes before the transformed source reaches this backend.
        return null;
    }

    @Override
    public void close() {
    }
}
