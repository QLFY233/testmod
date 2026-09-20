package io.github.qlfy233.testmod.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

import org.lwjgl.glfw.GLFW;

import io.github.qlfy233.testmod.TestMod;

/**
 * 按键绑定：默认 K 键打开 {@link DemoGuiScreen}。
 *
 * <p><b>关于事件总线</b>：在 1.21.1，{@code @EventBusSubscriber} 的 {@code bus} 参数已被废弃且<b>被忽略</b>，
 * 总线由事件类型自动判定——实现了 {@code IModBusEvent} 的走 mod 总线，其余走 game 总线。
 * 所以 {@link RegisterKeyMappingsEvent}（mod 总线）和 {@link ClientTickEvent.Post}（game 总线）
 * 可以写在同一个类里，这是 NeoForge 1.21.1 的官方行为。</p>
 */
@EventBusSubscriber(modid = TestMod.MODID, value = Dist.CLIENT)
public final class KeyBindings {

    /**
     * 按键绑定。
     *
     * <p>用 NeoForge 的构造器而不是原版的，是为了指定 {@link KeyConflictContext}：
     * 原版构造器创建的绑定是 UNIVERSAL（任何场景都生效），会出现在主菜单里按键也触发；
     * IN_GAME 则限定"没打开任何界面时"才生效，这才是我们想要的行为。</p>
     */
    public static final KeyMapping OPEN_GUI = new KeyMapping(
            "key.testmod.open_gui",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.testmod");

    private KeyBindings() {}

    /** mod 总线事件：把按键注册进游戏的「选项 → 控制」列表。不注册就永远不会触发。 */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUI);
        TestMod.LOGGER.info("Registered key mapping: key.testmod.open_gui");
    }

    /**
     * 每客户端 tick 检查一次按键。
     *
     * <p>必须用 {@code while} + {@code consumeClick()}：{@code consumeClick} 只返回"尚未被处理过的点击"，
     * 循环可以把一 tick 内累积的多次按键全部消费掉，既不会丢事件也不会死循环。</p>
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN_GUI.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            // 已经开着别的界面时不要强行顶掉（例如聊天栏、背包）
            if (minecraft.screen == null) {
                minecraft.setScreen(new DemoGuiScreen());
            }
        }
    }
}
