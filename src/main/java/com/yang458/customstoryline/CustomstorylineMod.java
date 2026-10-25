package com.yang458.customstoryline;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Custom Storyline Mod
 * License: CC0-1.0
 */
@Mod(CustomstorylineMod.MODID)
public class CustomstorylineMod {
    public static final String MODID = "customstoryline";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CustomstorylineMod() {
        LOGGER.info("[Customstoryline] Mod initialized successfully.");
    }
}