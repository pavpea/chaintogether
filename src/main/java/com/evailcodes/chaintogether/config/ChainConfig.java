package com.evailcodes.chaintogether.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;

public class ChainConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ConfigValue<Double> CHAIN_LENGTH;
    public static final ConfigValue<Boolean> UNBREAKABLE_CHAIN;
    public static final ConfigValue<Boolean> PREVENT_TELEPORT;
    public static final ConfigValue<Boolean> SHARED_RESPAWN;
    public static final ConfigValue<Boolean> AUTO_RESPAWN;
    public static final ConfigValue<Integer> CHAIN_TRANSPARENCY;

    static {
        // 开始构建ChainTogether配置，创建一个新的配置分类
        BUILDER.push("ChainTogether Config");

        // 定义链条长度倍率配置项
        // 描述：链条长度相对于原版拴绳长度的倍率
        // 默认值：0.7倍
        // 取值范围：0.5到3.0之间
        CHAIN_LENGTH = BUILDER.comment("Chain length multiplier (default: 0.7x vanilla lead length)")
                .defineInRange("chainLength", 0.7, 0.5, 3.0);

        // 定义链条是否不可破坏配置项
        // 描述：控制链条是否可以被破坏
        // 默认值：true（不可破坏）
        UNBREAKABLE_CHAIN = BUILDER.comment("Whether chains are unbreakable")
                .define("unbreakableChain", true);

        // 定义是否阻止传送配置项
        // 描述：控制被链条连接的玩家是否可以使用传送
        // 默认值：true（阻止传送）
        PREVENT_TELEPORT = BUILDER.comment("Prevent teleportation while chained")
                .define("preventTeleport", true);

        // 定义共享重生点配置项
        // 描述：控制被链条连接的玩家是否共享重生点
        // 默认值：true（共享重生点）
        SHARED_RESPAWN = BUILDER.comment("Enable shared respawn point for chained players")
                .define("sharedRespawn", true);

        // 定义自动重生配置项
        // 描述：控制被链条连接的玩家是否自动重生
        // 默认值：false（禁用自动重生，玩家需要手动选择复活）
        AUTO_RESPAWN = BUILDER.comment("Enable automatic respawn")
                .define("autoRespawn", false);

        // 定义链条透明度配置项
        // 描述：控制链条粒子线的透明度（10-100，10最弱，100不透明）
        // 默认值：100（不透明）
        CHAIN_TRANSPARENCY = BUILDER.comment("Chain particle line transparency (10-100, 10 weakest, 100 opaque)")
                .defineInRange("chainTransparency", 100, 10, 100);

        // 结束当前配置分类
        BUILDER.pop();
        
        // 构建最终的配置规范
        SPEC = BUILDER.build();
    }

    // 获取透明度值（转换为0.0-1.0的浮点数）
    public static float getTransparencyAsFloat() {
        return CHAIN_TRANSPARENCY.get() / 100.0f;
    }

    // 获取默认距离倍率
    public static double getDefaultDistanceMultiplier() {
        return CHAIN_LENGTH.get();
    }
}