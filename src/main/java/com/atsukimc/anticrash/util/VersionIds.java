package com.atsukimc.anticrash.util;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;

//? if >=1.19.3 {
import net.minecraft.core.registries.BuiltInRegistries;
//?}
//? if <=1.21.10 {
/*import net.minecraft.resources.ResourceLocation;

*///?} else {
import net.minecraft.resources.Identifier;

//?}

/**
 * Minecraft identifiers across versions. ResourceLocation was renamed Identifier in 1.21.11 (and
 * ResourceKey#location became #identifier), the built-in registries moved from Registry to
 * BuiltInRegistries in 1.19.3, and Registry#get became #getValue in 1.21.2.
 *
 * <p>Ids are passed around as "namespace:path" strings; only {@link #parse}, {@link #tryParse} and
 * {@link #of} return the version's own id type, for vanilla calls that need one. Registries are
 * returned by methods rather than fields so loading this class never bootstraps them.
 */
public final class VersionIds {
    private VersionIds() {}

    /** "namespace:path" of a resource key. */
    public static String key(ResourceKey<?> key) {
        //? if <=1.21.10 {
        /*return key.location().toString();
        *///?} else {
        return key.identifier().toString();
        //?}
    }

    /** Dimension id of a level, such as "minecraft:overworld". */
    public static String dimension(Level level) {
        return key(level.dimension());
    }

    /** Dimension id of the level an entity is in. */
    public static String dimension(Entity entity) {
        return dimension(VersionLevels.level(entity));
    }

    // Built-in registries

    public static Registry<Block> blocks() {
        //? if <=1.19.2 {
        /*return Registry.BLOCK;
        *///?}else {
        return BuiltInRegistries.BLOCK;
        //?}
    }

    public static Registry<Fluid> fluids() {
        //? if <=1.19.2 {
        /*return Registry.FLUID;
        *///?} else {
        return BuiltInRegistries.FLUID;
        //?}
    }

    public static Registry<Item> items() {
        //? if <=1.19.2 {
        /*return Registry.ITEM;
        *///?} else {
        return BuiltInRegistries.ITEM;
        //?}
    }

    public static Registry<EntityType<?>> entityTypes() {
        //? if <=1.19.2 {
        /*return Registry.ENTITY_TYPE;
        *///?} else {
        return BuiltInRegistries.ENTITY_TYPE;
        //?}
    }

    public static Registry<BlockEntityType<?>> blockEntityTypes() {
        //? if <=1.19.2 {
        /*return Registry.BLOCK_ENTITY_TYPE;
        *///?} else {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE;
        //?}
    }

    public static Registry<GameEvent> gameEvents() {
        //? if <=1.19.2 {
        /*return Registry.GAME_EVENT;
        *///?} else {
        return BuiltInRegistries.GAME_EVENT;
        //?}
    }

    /** Id of a registered value, or null when the registry does not know it. */
    public static <T> String id(Registry<T> registry, T value) {
        var id = registry.getKey(value);
        return id == null ? null : id.toString();
    }

    /** Whether the text is a valid id of a value in the registry. */
    public static <T> boolean contains(Registry<T> registry, String id) {
        var parsed = tryParse(id);
        return parsed != null && registry.containsKey(parsed);
    }

    /** Value the text names, or null when the text is not a valid id or names nothing. */
    public static <T> T get(Registry<T> registry, String id) {
        var parsed = tryParse(id);
        return parsed == null || !registry.containsKey(parsed) ? null : value(registry, parsed);
    }

    // The version's own id type, for vanilla calls that take one

    //? if <=1.21.10 {
    /*/^* Parsed id, or null when the text is not a valid id. ^/
    public static ResourceLocation tryParse(String id) {
        return ResourceLocation.tryParse(id);
    }

    /^* Parsed id; throws when the text is not a valid id. ^/
    public static ResourceLocation parse(String id) {
        //? if <=1.20.6 {
        /^return new ResourceLocation(id);
        ^///?} else {
        /^return ResourceLocation.parse(id);
        ^///?}
    }

    /^* Value registered under an id: Registry#get before 1.21.2, Registry#getValue after. ^/
    public static <T> T value(Registry<T> registry, ResourceLocation id) {
        //? if <=1.21.1 {
        /^return registry.get(id);
        ^///?} else {
        /^return registry.getValue(id);
        ^///?}
    }

    public static ResourceLocation of(String namespace, String path) {
        //? if <=1.20.6 {
        /^return new ResourceLocation(namespace, path);
        ^///?} else {
        /^return ResourceLocation.fromNamespaceAndPath(namespace, path);
        ^///?}
    }
    *///?} else {
    /** Parsed id, or null when the text is not a valid id. */
    public static Identifier tryParse(String id) {
        return Identifier.tryParse(id);
    }

    /** Parsed id; throws when the text is not a valid id. */
    public static Identifier parse(String id) {
        return Identifier.parse(id);
    }

    /** Value registered under an id. */
    public static <T> T value(Registry<T> registry, Identifier id) {
        return registry.getValue(id);
    }

    public static Identifier of(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
    //?}
}
