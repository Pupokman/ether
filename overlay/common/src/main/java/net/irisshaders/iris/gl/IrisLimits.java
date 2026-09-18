package net.irisshaders.iris.gl;

import net.irisshaders.iris.backend.IrisBackendRuntime;

public class IrisLimits {
    /**
     * The maximum number of color textures that a shader pack can write to and
     * read from in gbuffer and composite programs.
     */
    public static final int MAX_COLOR_BUFFERS = 32;

    /**
     * Vulkan/ShaderC requires explicit stage IO locations. OpenGL does not.
     */
    public static boolean requiresVkConformance() {
        return IrisBackendRuntime.isVulkan();
    }
}
