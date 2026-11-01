package com.atsukimc.anticrash.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Runtime-state coverage is treated as a correctness specification: every instance field of every
 * block entity class, and of the entity classes machines are built from, has a decision. This test
 * reads the classes from the Minecraft jar the mod is compiled against and fails when a field has
 * no decision, so a Minecraft update cannot add state unnoticed.
 */
class RuntimeStateAuditCoverageTest {
    private static final Set<String> CLASSIFICATIONS =
            Set.of(
                    "PERSISTED_BY_VANILLA",
                    "DERIVED_ON_LOAD",
                    "CLIENT_ONLY",
                    "VISUAL_ONLY",
                    "TICK_LOCAL",
                    "RESTORABLE_TRANSIENT",
                    "OBSERVE_ONLY",
                    "UNSUPPORTED");
    private static final Set<String> RISKS = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "NONE");

    /**
     * Entity classes that must be in the matrix; mobs are covered as a category in the document.
     */
    private static final List<String> REQUIRED_ENTITIES =
            List.of(
                    "net.minecraft.entity.Entity",
                    "net.minecraft.entity.ItemEntity",
                    "net.minecraft.entity.TntEntity",
                    "net.minecraft.entity.FallingBlockEntity",
                    "net.minecraft.entity.ExperienceOrbEntity",
                    "net.minecraft.entity.vehicle.AbstractMinecartEntity",
                    "net.minecraft.entity.vehicle.HopperMinecartEntity",
                    "net.minecraft.entity.vehicle.FurnaceMinecartEntity",
                    "net.minecraft.entity.vehicle.TntMinecartEntity",
                    "net.minecraft.entity.vehicle.StorageMinecartEntity",
                    "net.minecraft.entity.vehicle.BoatEntity",
                    "net.minecraft.entity.vehicle.AbstractBoatEntity",
                    "net.minecraft.entity.projectile.ProjectileEntity",
                    "net.minecraft.entity.projectile.PersistentProjectileEntity");

    private static Map<String, Set<String>> matrix(String resource) throws Exception {
        Map<String, Set<String>> fields = new LinkedHashMap<>();
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                RuntimeStateAuditCoverageTest.class.getResourceAsStream(resource),
                                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] columns = line.split("\t", -1);
                assertEquals(5, columns.length, "columns in: " + line);
                assertTrue(
                        CLASSIFICATIONS.contains(columns[2]), "unknown classification in: " + line);
                assertTrue(RISKS.contains(columns[3]), "unknown risk in: " + line);
                assertTrue(!columns[4].isBlank(), "a decision needs its reason: " + line);
                Set<String> classFields =
                        fields.computeIfAbsent(columns[0], ignored -> new TreeSet<>());
                if (!columns[1].equals("-"))
                    assertTrue(classFields.add(columns[1]), "duplicate row: " + line);
            }
        }
        return fields;
    }

    @Test
    void everyBlockEntityFieldHasADecision() throws Exception {
        Map<String, Set<String>> audited = matrix("/runtime-audit/block-entities.tsv");
        Map<String, List<String>> actual =
                RuntimeFieldDump.declaredFields(BlockEntity.class, "net/minecraft/block/entity");
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, List<String>> type : actual.entrySet()) {
            String simple = type.getKey().substring(type.getKey().lastIndexOf('.') + 1);
            Set<String> decided = audited.remove(simple);
            if (decided == null) {
                problems.add("unaudited block entity class " + simple);
                continue;
            }
            Set<String> fields = new TreeSet<>(type.getValue());
            if (!fields.equals(decided))
                problems.add(simple + " audited " + decided + " but has " + fields);
        }
        for (String leftover : audited.keySet())
            problems.add("audit row for a class that does not exist: " + leftover);
        assertTrue(actual.size() >= 38, "block entity classes found: " + actual.size());
        assertEquals(List.of(), problems);
    }

    @Test
    void everyFieldOfTheMachineRelevantEntitiesHasADecision() throws Exception {
        Map<String, Set<String>> audited = matrix("/runtime-audit/entities.tsv");
        Map<String, List<String>> actual =
                RuntimeFieldDump.declaredFields(Entity.class, "net/minecraft/entity");
        List<String> problems = new ArrayList<>();
        for (String required : REQUIRED_ENTITIES) {
            if (!audited.containsKey(required)) problems.add("missing from the audit: " + required);
        }
        for (Map.Entry<String, Set<String>> type : audited.entrySet()) {
            List<String> fields = actual.get(type.getKey());
            if (fields == null) {
                problems.add("audit row for a class that does not exist: " + type.getKey());
            } else if (!new TreeSet<>(fields).equals(type.getValue())) {
                problems.add(
                        type.getKey()
                                + " audited "
                                + type.getValue()
                                + " but has "
                                + new TreeSet<>(fields));
            }
        }
        assertEquals(List.of(), problems);
    }
}
