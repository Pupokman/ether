package net.irisshaders.iris.backend;

import java.util.Objects;

/**
 * Backend selection bridge. This class deliberately has no OpenGL/Vulkan
 * implementation-class references so it is safe to load before the backend.
 */
public final class IrisBackendRuntime {
    private static volatile GraphicsBackend backend = GraphicsBackend.OPENGL;
    private static volatile boolean selected;

    private IrisBackendRuntime() {
    }

    public static void select(GraphicsBackend value) {
        Objects.requireNonNull(value, "value");

        synchronized (IrisBackendRuntime.class) {
            if (selected && backend != value) {
                throw new IllegalStateException(
                    "Iris graphics backend was already selected as " + backend
                        + ", cannot switch to " + value);
            }

            backend = value;
            selected = true;
        }
    }

    public static GraphicsBackend get() {
        return backend;
    }

    public static boolean isVulkan() {
        return backend.isVulkan();
    }

    public static boolean isSelected() {
        return selected;
    }
}
