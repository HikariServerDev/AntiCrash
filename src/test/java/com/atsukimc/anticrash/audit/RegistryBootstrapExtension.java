//? if (>=1.21.2 && <=26.3) {
package com.atsukimc.anticrash.audit;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Only initializes Vanilla registries needed by ChunkPos's new static generation constants. Does
 * not construct a MinecraftServer, open a world, or start a server/network thread.
 */
public class RegistryBootstrapExtension implements BeforeAllCallback {
    @Override
    public void beforeAll(ExtensionContext context) {

        //? if (>=1.21.2 && <=1.21.11) {
        /*net.minecraft.SharedConstants.tryDetectVersion();
*///?} else if (>=26.1 && <=26.3) {
        net.minecraft.SharedConstants.tryDetectVersion();
        //?}

        //? if (>=1.21.2 && <=1.21.11) {
        /*net.minecraft.server.Bootstrap.bootStrap();
*///?} else if (>=26.1 && <=26.3) {
        net.minecraft.server.Bootstrap.bootStrap();
        //?}

    }
}
//?}
