package com.evailcodes.chaintogether;

import com.evailcodes.chaintogether.config.ChainConfig;
import com.evailcodes.chaintogether.handler.ChainHandler;
import com.evailcodes.chaintogether.network.ChainPacketHandler;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ChainTogether.MODID)
public class ChainTogether {
    public static final String MODID = "chaintogether";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public ChainTogether(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        modContainer.registerConfig(ModConfig.Type.COMMON, ChainConfig.SPEC, "chaintogether-common.toml");

        NeoForge.EVENT_BUS.register(new ChainHandler());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ChainPacketHandler.register(event);
        });
        LOGGER.info("ChainTogether initialized!");
    }
}