package net.irisshaders.iris.backend.vulkan;

import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import java.util.Objects;

public final class IrisVulkanPipeline implements AutoCloseable {
    private final RenderPipeline description;
    private final CompiledRenderPipeline compiled;

    public IrisVulkanPipeline(
            RenderPipeline description,
            CompiledRenderPipeline compiled) {
        this.description = Objects.requireNonNull(description, "description");
        this.compiled = Objects.requireNonNull(compiled, "compiled");
    }

    public RenderPipeline description() {
        return description;
    }

    public CompiledRenderPipeline compiled() {
        return compiled;
    }

    @Override
    public void close() {
        if (!compiled.isClosed()) {
            compiled.close();
        }
    }
}
