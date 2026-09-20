package io.github.qlfy233.testmod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * testmod 主类。
 *
 * <p>本 mod 的目的是打通「写代码 → GitHub Actions 编译 → 下载 jar → 进游戏看效果」这条链路，
 * 内容是一个按 K 键打开的自定义 GUI。</p>
 *
 * <p>注意这里没有任何注册表（item/block/tab）相关的代码：纯 GUI mod 不需要。
 * 唯一的内容注册发生在客户端的 {@link TestModClient} 里（按键绑定）。</p>
 */
@Mod(TestMod.MODID)
public class TestMod {

    /** 必须与 gradle.properties 的 mod_id、neoforge.mods.toml 的 ${mod_id} 完全一致。 */
    public static final String MODID = "testmod";

    public static final Logger LOGGER = LogUtils.getLogger();

    // FML 能识别 IEventBus / ModContainer 这类参数并自动注入
    public TestMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP (testmod)");
    }
}
