package com.atsukimc.anticrash.util;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public final class ReflectionUtil {
    private ReflectionUtil() {
    }

    public static void makeAccessible(AccessibleObject object) {
        try {
            object.setAccessible(true);
        } catch (RuntimeException ignored) {
            // Java 17 modules may reject an access; callers treat it as unavailable.
        }
    }

    public static List<Field> instanceFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    makeAccessible(field);
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    public static List<Method> allMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                makeAccessible(method);
                methods.add(method);
            }
        }
        return methods;
    }
}
