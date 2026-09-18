package net.irisshaders.iris.backend;

public enum GraphicsBackend {
    OPENGL,
    VULKAN;

    public boolean isVulkan() {
        return this == VULKAN;
    }
}
