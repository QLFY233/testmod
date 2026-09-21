package io.github.qlfy233.testmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * RGB 取色界面。
 *
 * <p>三个滑块分别控制红/绿/蓝，右侧实时预览色块，底部给出十六进制值和 ARGB 整数。
 * 预览色块本身可以点击/拖拽取色（在色域内按下就把三个滑块设成对应比例）。</p>
 *
 * <p>绘制顺序遵循 {@link DemoGuiScreen} 里踩过的那条经验：
 * <b>背景只画一次，然后用遍历 renderables 代替 super.render()</b>，
 * 否则 renderBackground 会被调用两次，第二次的延迟模糊会糊掉自绘文字。</p>
 */
public class RgbPickerScreen extends Screen {

    private static final int PANEL_BG     = 0xC0000000;
    private static final int PANEL_BORDER = 0xFF4C8DFF;
    private static final int TEXT_MAIN    = 0xFFEDEDED;
    private static final int TEXT_DIM     = 0xFF9A9A9A;
    private static final int HEX_TEXT     = 0xFF7FE3A1;

    // 预览色块（同时也是可点击的取色区）
    private static final int SWATCH_W = 64;
    private static final int SWATCH_H = 64;

    private ChannelSlider redSlider;
    private ChannelSlider greenSlider;
    private ChannelSlider blueSlider;

    public RgbPickerScreen() {
        super(Component.translatable("testmod.rgb.title"));
    }

    // ── 布局：所有坐标都由面板尺寸推导，不写死 ─────────────────────────
    private int panelW() {
        return Math.min(300, this.width - 20);
    }

    private int panelH() {
        return Math.min(190, this.height - 20);
    }

    private int panelLeft() {
        return this.width / 2 - panelW() / 2;
    }

    private int panelTop() {
        return this.height / 2 - panelH() / 2;
    }

    /** 滑块区域的左边界。 */
    private int sliderLeft() {
        return panelLeft() + 12;
    }

    /** 滑块的宽度：留出右侧预览色块的位置。 */
    private int sliderWidth() {
        return panelW() - 24 - SWATCH_W - 12;
    }

    /** 预览色块左上角。 */
    private int swatchLeft() {
        return panelLeft() + panelW() - 12 - SWATCH_W;
    }

    private int swatchTop() {
        return panelTop() + 40;
    }

    @Override
    protected void init() {
        super.init();

        int sx = this.sliderLeft();
        int sw = this.sliderWidth();
        int top = this.swatchTop();
        int gap = 24;

        // 三个通道滑块，默认值给一个好看的青蓝色
        this.redSlider   = new ChannelSlider(sx, top,              sw, 20, "testmod.rgb.red",   0.20D);
        this.greenSlider = new ChannelSlider(sx, top + gap,        sw, 20, "testmod.rgb.green", 0.70D);
        this.blueSlider  = new ChannelSlider(sx, top + gap * 2,    sw, 20, "testmod.rgb.blue",  0.90D);

        this.addRenderableWidget(this.redSlider);
        this.addRenderableWidget(this.greenSlider);
        this.addRenderableWidget(this.blueSlider);

        // 底部按钮：随机 / 关闭
        int btnW = (this.sliderWidth() - 8) / 2;
        int btnY = this.panelTop() + this.panelH() - 32;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("testmod.rgb.random"),
                        b -> this.randomize())
                .bounds(sx, btnY, btnW, 20)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("testmod.button.close"),
                        b -> this.onClose())
                .bounds(sx + btnW + 8, btnY, btnW, 20)
                .build());
    }

    private void randomize() {
        this.redSlider.setChannel(Math.random());
        this.greenSlider.setChannel(Math.random());
        this.blueSlider.setChannel(Math.random());
    }

    // ── 当前颜色 ──────────────────────────────────────────────────────
    private int red() {
        return this.redSlider.channel255();
    }

    private int green() {
        return this.greenSlider.channel255();
    }

    private int blue() {
        return this.blueSlider.channel255();
    }

    /** 不透明的 ARGB。 */
    private int argb() {
        return 0xFF000000 | (this.red() << 16) | (this.green() << 8) | this.blue();
    }

    private String hex() {
        return String.format("#%02X%02X%02X", this.red(), this.green(), this.blue());
    }

    /** 判断鼠标是否落在预览色块内。 */
    private boolean overSwatch(double mouseX, double mouseY) {
        int x = this.swatchLeft();
        int y = this.swatchTop();
        return mouseX >= x && mouseX < x + SWATCH_W && mouseY >= y && mouseY < y + SWATCH_H;
    }

    /**
     * 在色块内拖动时，把鼠标的二维位置映射成颜色。
     *
     * <p>做法：横向控制 R，纵向控制 G，B 保持不变。这是一种简单直观的"取色盘"，
     * 虽然不是 HSV 那种标准色轮，但胜在实现简单、可预期。</p>
     */
    private void pickFromSwatch(double mouseX, double mouseY) {
        int x = this.swatchLeft();
        int y = this.swatchTop();
        double fx = Mth.clamp((mouseX - x) / (double) SWATCH_W, 0.0D, 1.0D);
        double fy = Mth.clamp((mouseY - y) / (double) SWATCH_H, 0.0D, 1.0D);
        this.redSlider.setChannel(fx);
        this.greenSlider.setChannel(1.0D - fy);   // 上方更亮，符合直觉
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 先让控件处理（滑块需要拿到点击）
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && this.overSwatch(mouseX, mouseY)) {
            this.pickFromSwatch(mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (button == 0 && this.overSwatch(mouseX, mouseY)) {
            this.pickFromSwatch(mouseX, mouseY);
            return true;
        }
        return false;
    }

    // ── 渲染 ──────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 见 DemoGuiScreen 的注释：背景只画这一次，绝不用 super.render()
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        int left = this.panelLeft();
        int top = this.panelTop();
        int w = this.panelW();
        int h = this.panelH();

        // ① 面板
        graphics.fill(left, top, left + w, top + h, PANEL_BG);
        graphics.renderOutline(left, top, w, h, PANEL_BORDER);

        // ② 标题 + 分隔线
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 12, TEXT_MAIN);
        graphics.hLine(left + 8, left + w - 8, top + 28, PANEL_BORDER);

        // ③ 预览色块。在它上面按鼠标拖动可以取色
        int sx = this.swatchLeft();
        int sy = this.swatchTop();
        graphics.fill(sx, sy, sx + SWATCH_W, sy + SWATCH_H, this.argb());
        boolean hover = this.overSwatch(mouseX, mouseY);
        graphics.renderOutline(sx, sy, SWATCH_W, SWATCH_H, hover ? 0xFFFFFFFF : PANEL_BORDER);

        // ④ 色值文本：十六进制 + 各通道数值
        int textX = left + 12;
        int textY = sy + SWATCH_H + 8;
        graphics.drawString(this.font, Component.literal(this.hex()), textX, textY, HEX_TEXT, false);
        graphics.drawString(this.font,
                Component.translatable("testmod.rgb.channels", this.red(), this.green(), this.blue()),
                textX, textY + 12, TEXT_DIM, false);

        // ⑤ 提示：这个色块可以拖
        if (hover) {
            graphics.drawString(this.font,
                    Component.translatable("testmod.rgb.hint"),
                    textX, textY + 26, TEXT_DIM, false);
        }

        // ⑥ 最后画控件（手动遍历，避免 super.render() 里多余的 renderBackground）
        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 单通道滑块：把 0.0~1.0 显示成 0~255。 */
    private static class ChannelSlider extends AbstractSliderButton {

        private final String labelKey;

        ChannelSlider(int x, int y, int width, int height, String labelKey, double value) {
            super(x, y, width, height, Component.empty(), value);
            this.labelKey = labelKey;
            this.updateMessage();
        }

        /** 0~255 的整数值。 */
        int channel255() {
            return (int) Math.round(Mth.clamp(this.value, 0.0D, 1.0D) * 255.0D);
        }

        void setChannel(double newValue) {
            this.value = Mth.clamp(newValue, 0.0D, 1.0D);
            this.updateMessage();
            this.applyValue();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable(this.labelKey, this.channel255()));
        }

        @Override
        protected void applyValue() {
            // 不需要额外副作用：取值时现算
        }
    }
}
