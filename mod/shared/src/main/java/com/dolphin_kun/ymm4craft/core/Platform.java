package com.dolphin_kun.ymm4craft.core;

import java.nio.file.Path;

public interface Platform {
    Path configDir();

    String modVersion();
}
