package io.github.qlfy233.testmod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端专用入口。
 *
 * <p>这个类在专用服务器上不会被加载，所以在这里引用 {@code net.minecraft.client.*} 是安全的。
 * 这就是 mod 开发里最重要的一条边界：<b>所有客户端 API 只能出现在 {@code dist = Dist.CLIENT}
 * 的类里</b>。</p>
 *
 * <p>{@code @EventBusSubscriber} 上的 {@code value = Dist.CLIENT} 同样是必需的，
 * 否则在专用服务器上加载这个类时会因为找不到客户端事件类型而崩溃。</p>
 */
@Mod(value = TestMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = TestMod.MODID, value = Dist.CLIENT)
public class TestModClient {

    public TestModClient(ModContainer container) {
        // 留空：本 mod 没有配置文件，也就没有配置界面需要注册
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        TestMod.LOGGER.info("HELLO FROM CLIENT SETUP (testmod)");
    }
}
