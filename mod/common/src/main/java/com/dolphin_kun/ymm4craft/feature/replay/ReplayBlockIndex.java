package com.dolphin_kun.ymm4craft.feature.replay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

final class ReplayBlockIndex {
    static final String KEY = "blocks";

    private final List<ReplayBlock> blocks;

    private ReplayBlockIndex(List<ReplayBlock> blocks) {
        this.blocks = blocks;
    }

    static ReplayBlockIndex of(JsonObject info) {
        List<ReplayBlock> blocks = new ArrayList<>();
        blocks.add(ReplayBlock.START);
        if (info != null && info.get(KEY) instanceof JsonArray array) {
            for (JsonElement e : array) {
                if (!(e instanceof JsonArray pair) || pair.size() != 2) continue;
                long entry = pair.get(0).getAsLong();
                long offset = pair.get(1).getAsLong();
                ReplayBlock last = blocks.getLast();
                if (entry > last.entry() && offset > last.offset()) blocks.add(new ReplayBlock(entry, offset));
            }
        }
        return new ReplayBlockIndex(blocks);
    }

    static JsonArray toJson(List<ReplayBlock> blocks) {
        JsonArray array = new JsonArray();
        for (ReplayBlock b : blocks) {
            JsonArray pair = new JsonArray();
            pair.add(b.entry());
            pair.add(b.offset());
            array.add(pair);
        }
        return array;
    }

    ReplayBlock floor(long entry) {
        ReplayBlock found = ReplayBlock.START;
        for (ReplayBlock b : blocks) {
            if (b.entry() > entry) break;
            found = b;
        }
        return found;
    }
}
