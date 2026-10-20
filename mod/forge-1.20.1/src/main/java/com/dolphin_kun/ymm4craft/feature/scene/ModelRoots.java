package com.dolphin_kun.ymm4craft.feature.scene;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelRoots {
    private static final Map<Class<?>, List<Field>> partFields = new ConcurrentHashMap<>();
    private static final Map<Model, ModelPart> roots = new WeakHashMap<>();

    private ModelRoots() {
    }

    public static ModelPart of(Model model) {
        if (model instanceof HierarchicalModel<?> hierarchical) return hierarchical.root();
        ModelPart cached = roots.get(model);
        if (cached != null) return cached;
        ModelPart found = find(model);
        if (found != null) roots.put(model, found);
        return found;
    }

    private static ModelPart find(Model model) {
        for (Field field : partFields.computeIfAbsent(model.getClass(), ModelRoots::fieldsOf)) {
            try {
                if (field.get(model) instanceof ModelPart part) return top(part);
            } catch (IllegalAccessException ignored) {
            }
        }
        return null;
    }

    private static ModelPart top(ModelPart part) {
        ModelPart current = part;
        for (int depth = 0; depth < 64; depth++) {
            ModelPart parent = ((ModelPartParent) (Object) current).ymm4craft$parent();
            if (parent == null) return current;
            current = parent;
        }
        return current;
    }

    private static List<Field> fieldsOf(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != ModelPart.class) continue;
                try {
                    field.setAccessible(true);
                    fields.add(field);
                } catch (RuntimeException ignored) {
                }
            }
        }
        return fields;
    }
}
