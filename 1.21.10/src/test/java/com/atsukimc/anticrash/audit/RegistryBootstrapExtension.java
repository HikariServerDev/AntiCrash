package com.atsukimc.anticrash.audit;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
/** Only initializes Vanilla registries needed by ChunkPos's new static generation constants.
 * Does not construct a MinecraftServer, open a world, or start a server/network thread. */
public class RegistryBootstrapExtension implements BeforeAllCallback {
    @Override public void beforeAll(ExtensionContext context) {
        net.minecraft.SharedConstants.createGameVersion();
        net.minecraft.Bootstrap.initialize();
    }
}
