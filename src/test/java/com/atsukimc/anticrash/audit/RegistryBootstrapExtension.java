//? if >=1.21.2 {
package com.atsukimc.anticrash.audit;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Only initializes Vanilla registries needed by ChunkPos's new static generation constants. Does
 * not construct a MinecraftServer, open a world, or start a server/network thread.
 */
public class RegistryBootstrapExtension implements BeforeAllCallback {
    @Override
    public void beforeAll(ExtensionContext context) {
        SharedConstants.tryDetectVersion();

        Bootstrap.bootStrap();
    }
}
//?}
