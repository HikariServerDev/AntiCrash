package com.atsukimc.anticrash.snapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure dependency-graph helpers used by retention and tests. */
public final class GenerationGraph {
    private GenerationGraph() {
    }

    public record Node(String id, String parentId) {
    }

    /** Returns all ancestors that must remain available for the retained IDs. */
    public static Set<String> protectedAncestors(Collection<Node> nodes,
                                                  Collection<String> retainedIds) {
        Map<String, String> parents = new HashMap<>();
        if (nodes != null) {
            for (Node node : nodes) {
                if (node != null && node.id() != null) parents.put(node.id(), node.parentId());
            }
        }
        Set<String> result = new HashSet<>();
        if (retainedIds == null) return result;
        for (String id : retainedIds) {
            String current = id;
            Set<String> visited = new HashSet<>();
            while (current != null && visited.add(current)) {
                result.add(current);
                current = parents.get(current);
            }
        }
        return result;
    }

    public static int depth(String id, Map<String, String> parents) {
        int depth = 0;
        String current = id;
        Set<String> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            String parent = parents == null ? null : parents.get(current);
            if (parent == null) break;
            depth++;
            current = parent;
        }
        return depth;
    }

    public static List<Node> nodes(Collection<CheckpointManifest> manifests) {
        List<Node> result = new ArrayList<>();
        if (manifests == null) return result;
        for (CheckpointManifest manifest : manifests) {
            if (manifest != null && manifest.checkpointId != null) {
                result.add(new Node(manifest.checkpointId, manifest.parentCheckpointId));
            }
        }
        return result;
    }
}
