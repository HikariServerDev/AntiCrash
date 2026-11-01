package com.atsukimc.anticrash.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Says what differs between two sets of canonical lines, for a recovery that did not match. */
public final class RuntimeDiff {
    private RuntimeDiff() {}

    /**
     * @return for example {@code "checkpoint only 1: [a]; now only 2: [b, c]"}, or {@code "same
     *     lines in a different order"}, or an empty string when either side is unknown (a
     *     checkpoint that recorded only a digest)
     */
    public static String describe(List<String> checkpoint, List<String> now, int examples) {
        if (checkpoint == null || now == null) return "";
        Map<String, Integer> balance = new HashMap<>();
        for (String line : checkpoint) balance.merge(line, 1, Integer::sum);
        for (String line : now) balance.merge(line, -1, Integer::sum);
        List<String> missing = new ArrayList<>();
        List<String> extra = new ArrayList<>();
        int missingCount = 0, extraCount = 0;
        for (Map.Entry<String, Integer> entry : balance.entrySet()) {
            int count = entry.getValue();
            if (count > 0) {
                missingCount += count;
                if (missing.size() < examples) missing.add(entry.getKey());
            } else if (count < 0) {
                extraCount -= count;
                if (extra.size() < examples) extra.add(entry.getKey());
            }
        }
        if (missingCount == 0 && extraCount == 0) {
            return checkpoint.equals(now) ? "" : "same lines in a different order";
        }
        missing.sort(String::compareTo);
        extra.sort(String::compareTo);
        StringBuilder text = new StringBuilder();
        if (missingCount > 0)
            text.append("checkpoint only ").append(missingCount).append(": ").append(missing);
        if (extraCount > 0) {
            if (text.length() > 0) text.append("; ");
            text.append("now only ").append(extraCount).append(": ").append(extra);
        }
        return text.toString();
    }
}
