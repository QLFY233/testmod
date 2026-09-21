package io.github.qlfy233.testmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 演示界面：把常用控件摆一遍，用来验证渲染链路是否正常。
 *
 * <p>设计要点：界面里所有坐标都由 {@link #width} / {@link #height} 相对计算，
 * 不写死像素——因为 GUI 缩放和窗口尺寸都会变。</p>
 */
public class DemoGuiScreen extends Screen {

    private static final int PANEL_BG     = 0xC0000000;   // ARGB：半透明黑
    private static final int PANEL_BORDER = 0xFF4C8DFF;
    private static final int TEXT_MAIN    = 0xFFEDEDED;
    private static final int TEXT_DIM     = 0xFF9A9A9A;
    private static final int TEXT_VALUE   = 0xFF7FE3A1;

    private static final String TRANSLATION_KEY = "testmod.screen.title";

    private EditBox nameField;
    private ValueSlider valueSlider;

    public DemoGuiScreen() {
        super(Component.translatable(TRANSLATION_KEY));
    }

    @Override
    protected void init() {
        super.init();

        int cx = this.width / 2;

        // 面板尺寸：夹在合理的范围内，窗口很小时也不会画到屏幕外
        int panelW = Math.min(320, this.width - 20);
        int panelH = Math.min(200, this.height - 20);
        int left   = cx - panelW / 2;
        int top    = this.height / 2 - panelH / 2;

        // ── 文本框 ──────────────────────────────────────────────
        this.nameField = new EditBox(this.font, left + 12, top + 40, panelW - 24, 20,
                Component.translatable("testmod.field.name"));
        this.nameField.setMaxLength(32);
        this.nameField.setHint(Component.translatable("testmod.field.name.hint"));
        this.addRenderableWidget(this.nameField);

        // ── 复选框 ──────────────────────────────────────────────
        // 只是摆着看看：Checkbox 没有读取当前状态的静态方式之外的东西，
        // 这里不持有引用，因为它不需要被外部修改。
        Checkbox fancyCheckbox = Checkbox.builder(Component.translatable("testmod.checkbox.fancy"), this.font)
                .pos(left + 12, top + 70)
                .selected(true)
                .build();
        this.addRenderableWidget(fancyCheckbox);

        // ── 滑块 ────────────────────────────────────────────────
        // 初始 message 传 empty 即可：构造器内部会立刻调 updateMessage() 填上真实文本，
        // 传一个没参数的 translatable 反而会短暂显示成 "Amount: %s" 字面量。
        this.valueSlider = new ValueSlider(left + 12, top + 96, panelW - 24, 20,
                Component.empty(), 0.5D);
        this.addRenderableWidget(this.valueSlider);

        // ── 按钮 ────────────────────────────────────────────────
        int btnW = (panelW - 36) / 2;
        int btnY = top + panelH - 32;

        this.addRenderableWidget(Button.builder(
                        Component.translatable("testmod.button.reset"),
                        b -> this.resetInputs())
                .bounds(left + 12, btnY, btnW, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("testmod.button.close"),
                        b -> this.onClose())
                .bounds(left + 24 + btnW, btnY, btnW, 20)
                .build());

        // 打开界面时光标直接落在文本框里
        this.setInitialFocus(this.nameField);
    }

    private void resetInputs() {
        this.nameField.setValue("");
        this.valueSlider.setValue(0.5D);
        // 注：Checkbox 只有 selected() 这个 getter，没有 setter。
        // 要动态改勾选状态，得用 Checkbox.Builder 重新构建一个控件（也就是重新走 init()）。
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // ★ 这里既不调 super.render()，也只把背景画一次。
        //
        // 原因：super.render() 内部第一件事就是调 renderBackground（javap 反汇编确认），
        // 而 renderBackground → renderBlurredBackground 在 1.21.1 是「延迟到下一帧生效」的模糊效果。
        // 自己再调一次，模糊就会叠到本帧已经画好的文字上。
        // 症状很典型：背景和控件都清晰，只有文字发虚。
        //
        // 所以我在这里显式调一次 renderBackground，然后自己遍历 renderables 画控件，
        // 绕开 super.render() 里那次多余的背景调用。
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        int panelW = Math.min(320, this.width - 20);
        int panelH = Math.min(200, this.height - 20);
        int left = cx - panelW / 2;
        int top = this.height / 2 - panelH / 2;

        // ① 面板本体 + 边框
        graphics.fill(left, top, left + panelW, top + panelH, PANEL_BG);
        graphics.renderOutline(left, top, panelW, panelH, PANEL_BORDER);

        // ② 面板上的自绘文字（压在面板之上）
        graphics.drawCenteredString(this.font, this.title, cx, top + 12, TEXT_MAIN);
        graphics.hLine(left + 8, left + panelW - 8, top + 28, PANEL_BORDER);
        this.paintStatusText(graphics, left, top, panelH);

        // ③ 最后画各控件，保证它们在最上层。
        // renderables 是 Screen 的 public 字段（javap 确认），手动遍历即可。
        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    /** 面板下方的两行实时状态文字。 */
    private void paintStatusText(GuiGraphics graphics, int left, int top, int panelH) {
        String state = this.nameField.getValue().isEmpty() ? "-" : this.nameField.getValue();
        graphics.drawString(this.font,
                Component.translatable("testmod.label.input", state),
                left + 12, top + panelH - 52, TEXT_DIM, false);

        graphics.drawString(this.font,
                Component.translatable("testmod.label.value", this.valueSlider.displayValue()),
                left + 12, top + panelH - 64, TEXT_VALUE, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;   // 单机里开着界面时不暂停世界，方便观察
    }

    /** 一个把 0.0~1.0 映射成 0~100 的滑块。 */
    private static class ValueSlider extends AbstractSliderButton {

        ValueSlider(int x, int y, int width, int height, Component message, double value) {
            super(x, y, width, height, message, value);
            this.updateMessage();
        }

        /** 值变化时更新显示文本。必须实现，否则拖动时文字不变。 */
        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable("testmod.slider.amount", this.displayValue()));
        }

        /** 值变化时应用副作用。这里没有实际业务，仅记录日志。 */
        @Override
        protected void applyValue() {
            // 想在这里干点什么的话，可以读 this.value
        }

        String displayValue() {
            return Integer.toString((int) Math.round(Mth.clamp(this.value, 0.0D, 1.0D) * 100.0D));
        }

        void setValue(double newValue) {
            this.value = Mth.clamp(newValue, 0.0D, 1.0D);
            this.updateMessage();
            this.applyValue();
        }
    }
}
