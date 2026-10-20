package com.dolphin_kun.ymm4craft.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;
import java.util.regex.Pattern;

public final class CommandParams {
    private final JsonObject json;

    public CommandParams(JsonObject json) {
        this.json = json == null ? new JsonObject() : json;
    }

    public boolean has(String name) {
        return json.has(name) && !json.get(name).isJsonNull();
    }

    public double getDouble(String name, double min, double max) throws CommandException {
        JsonElement e = require(name);
        double v;
        try {
            v = e.getAsDouble();
        } catch (RuntimeException ex) {
            throw new CommandException(name + " は数値で指定してください");
        }
        if (!Double.isFinite(v) || v < min || v > max) {
            throw new CommandException(name + " は " + min + " ～ " + max + " の範囲で指定してください");
        }
        return v;
    }

    public int getInt(String name, int min, int max) throws CommandException {
        double v = getDouble(name, min, max);
        if (v != Math.rint(v)) throw new CommandException(name + " は整数で指定してください");
        return (int) v;
    }

    public long getLong(String name, long min, long max) throws CommandException {
        JsonElement e = require(name);
        long v;
        try {
            v = e.getAsJsonPrimitive().getAsBigDecimal().longValueExact();
        } catch (RuntimeException ex) {
            throw new CommandException(name + " は整数で指定してください");
        }
        if (v < min || v > max) throw new CommandException(name + " は " + min + " ～ " + max + " の範囲で指定してください");
        return v;
    }

    public boolean getBoolean(String name) throws CommandException {
        JsonElement e = require(name);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) {
            throw new CommandException(name + " は true / false で指定してください");
        }
        return e.getAsBoolean();
    }

    public String getChoice(String name, Set<String> choices) throws CommandException {
        JsonElement e = require(name);
        String v = e.isJsonPrimitive() ? e.getAsString() : null;
        if (v == null || !choices.contains(v)) {
            throw new CommandException(name + " は " + choices + " のどれかで指定してください");
        }
        return v;
    }

    public String getString(String name, Pattern allowed) throws CommandException {
        JsonElement e = require(name);
        String v = e.isJsonPrimitive() ? e.getAsString() : null;
        if (v == null || !allowed.matcher(v).matches()) {
            throw new CommandException(name + " の値が不正です");
        }
        return v;
    }

    public CommandParams getObject(String name) throws CommandException {
        JsonElement e = require(name);
        if (!e.isJsonObject()) throw new CommandException(name + " はオブジェクトで指定してください");
        return new CommandParams(e.getAsJsonObject());
    }

    public java.util.List<CommandParams> getObjectArray(String name, int maxCount) throws CommandException {
        JsonElement e = require(name);
        if (!e.isJsonArray()) throw new CommandException(name + " は配列で指定してください");
        var array = e.getAsJsonArray();
        if (array.size() > maxCount) throw new CommandException(name + " は " + maxCount + " 個までです");
        java.util.List<CommandParams> list = new java.util.ArrayList<>(array.size());
        for (JsonElement item : array) {
            if (!item.isJsonObject()) throw new CommandException(name + " の要素はオブジェクトで指定してください");
            list.add(new CommandParams(item.getAsJsonObject()));
        }
        return list;
    }

    private JsonElement require(String name) throws CommandException {
        if (!has(name)) throw new CommandException(name + " がありません");
        return json.get(name);
    }
}
