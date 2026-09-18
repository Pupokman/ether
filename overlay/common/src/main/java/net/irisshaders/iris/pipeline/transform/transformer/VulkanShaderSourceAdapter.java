package net.irisshaders.iris.pipeline.transform.transformer;

import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.irisshaders.iris.backend.IrisBackendRuntime;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Final source adaptation step between Iris' shaderpack transformer and the
 * RenderPearl Vulkan compiler.
 */
public final class VulkanShaderSourceAdapter {
    private VulkanShaderSourceAdapter() {
    }

    public static Map<PatchShaderType, String> adapt(
            Map<PatchShaderType, String> transformed,
            VertexFormat vertexFormat,
            boolean fallback) {
        if (!IrisBackendRuntime.isVulkan() || transformed == null) {
            return transformed;
        }

        EnumMap<PatchShaderType, String> result = new EnumMap<>(PatchShaderType.class);
        result.putAll(transformed);

        String vertex = result.get(PatchShaderType.VERTEX);
        if (vertex != null) {
            result.put(
                PatchShaderType.VERTEX,
                VulkanVertexInputLayoutTransformer.transform(vertex, vertexFormat, fallback));
        }

        return result;
    }
}
