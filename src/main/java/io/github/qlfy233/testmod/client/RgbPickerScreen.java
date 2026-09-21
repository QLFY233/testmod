package io.github.qlfy233.testmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * RGB 取色器：色相条 + 饱和度/明度二维平面 + 透明度条。
 *
 * <h2>关于 API 的三个坑（都是从字节码里确认的，不是推测）</h2>
 *
 * <h3>1. 不要用 {@code Mth#hsvToRgb}，它会返回全透明色</h3>
 * 字节码显示它只是转调 {@code hsvToArgb(h, s, v, 0)}，alpha 直接写死 0：
 * <pre>
 * public static int hsvToRgb(float, float, float);
 *    0: fload_0
 *    1: fload_1
 *    2: fload_2
 *    3: iconst_0                  // ← alpha = 0
 *    4: invokestatic  hsvToArgb:(FFFI)I
 * </pre>
 * 所以永远用 {@link Mth#hsvToArgb(float, float, float, int)} 并显式传 255。
 *
 * <h3>2. 色相是 0.0~1.0，不是 0~360</h3>
 * 字节码里是 {@code (int)(h * 6) % 6} 分档，说明 h 的周期是 1.0。
 * 饱和度 S 和明度 V 同样是 0.0~1.0。
 *
 * <h3>3. {@code fillGradient} 只能画竖向渐变</h3>
 * 它内部是「每行一个颜色」，无法直接画二维渐变。
 * 所以 SV 平面用「逐列画一条竖向渐变」的方式拼出来。
 */
public class RgbPickerScreen extends Screen {

    // ── 配色 ────────────────────────────────────────────────────────
    private static final int PANEL_BG     = 0xF0101010;
    private static final int PANEL_BORDER = 0xFF4C8DFF;
    private static final int TEXT_MAIN    = 0xFFEDEDED;
    private static final int TEXT_DIM     = 0xFF9A9A9A;
    private static final int TEXT_VALUE   = 0xFF7FE3A1;
    private static final int MARKER_DARK  = 0xFF000000;
    private static final int MARKER_LIGHT = 0xFFFFFFFF;

    // ── 布局 ────────────────────────────────────────────────────────
    private static final int MARGIN = 12;
    private static final int GAP    = 8;
    private static final int PLANE_H = 100;
    private static final int BAR_H   = 12;

    // ── 当前颜色状态（HSV 为准，RGB 由它算出来）─────────────────────
    /** 色相 0.0~1.0 */
    private float hue = 0.55F;
    /** 饱和度 0.0~1.0 */
    private float saturation = 0.85F;
    /** 明度 0.0~1.0 */
    private float value = 1.0F;
    /** 透明度 0.0~1.0 */
    private float alpha = 1.0F;

    /** 正在拖拽哪个区域；null 表示没在拖。 */
    private DragTarget dragging = null;

    private enum DragTarget { PLANE, HUE, ALPHA }

    public RgbPickerScreen() {
        super(Component.translatable("testmod.rgb.title"));
    }

    // ── 布局计算：全部由面板尺寸推导，不写死像素 ──────────────────────
    private int panelW() {
        return Math.min(196, this.width - 20);
    }

    private int panelH() {
        return Math.min(232, this.height - 20);
    }

    private int panelLeft() {
        return this.width / 2 - panelW() / 2;
    }

    private int panelTop() {
        return this.height / 2 - panelH() / 2;
    }

    private int innerLeft() {
        return panelLeft() + MARGIN;
    }

    private int innerW() {
        return panelW() - MARGIN * 2;
    }

    private int planeTop() {
        return panelTop() + 34;
    }

    private int hueTop() {
        return planeTop() + PLANE_H + GAP;
    }

    private int alphaTop() {
        return hueTop() + BAR_H + GAP;
    }

    private int textTop() {
        return alphaTop() + BAR_H + GAP + 2;
    }

    // ── 颜色计算 ────────────────────────────────────────────────────
    /** 当前颜色（不透明）。注意 alpha 必须显式给，不能调 hsvToRgb。 */
    private int opaqueColor() {
        return Mth.hsvToArgb(this.hue, this.saturation, this.value, 255);
    }

    /** 当前颜色（带用户选的透明度）。 */
    private int argbColor() {
        return Mth.hsvToArgb(this.hue, this.saturation, this.value,
                Mth.clamp((int) Math.round(this.alpha * 255.0F), 0, 255));
    }

    private int red() {
        return (this.opaqueColor() >> 16) & 0xFF;
    }

    private int green() {
        return (this.opaqueColor() >> 8) & 0xFF;
    }

    private int blue() {
        return this.opaqueColor() & 0xFF;
    }

    private String hex() {
        return String.format("#%02X%02X%02X", this.red(), this.green(), this.blue());
    }

    // ── 命中测试 ────────────────────────────────────────────────────
    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private DragTarget hitTest(double mx, double my) {
        if (inside(mx, my, innerLeft(), planeTop(), innerW(), PLANE_H)) {
            return DragTarget.PLANE;
        }
        if (inside(mx, my, innerLeft(), hueTop(), innerW(), BAR_H)) {
            return DragTarget.HUE;
        }
        if (inside(mx, my, innerLeft(), alphaTop(), innerW(), BAR_H)) {
            return DragTarget.ALPHA;
        }
        return null;
    }

    /** 把鼠标位置换算成对应区域的值。 */
    private void applyDrag(DragTarget target, double mx, double my) {
        int w = Math.max(1, innerW() - 1);

        switch (target) {
            case PLANE -> {
                // 横向 = 饱和度，纵向 = 明度（上方更亮，符合直觉）
                this.saturation = (float) Mth.clamp((mx - innerLeft()) / w, 0.0D, 1.0D);
                this.value = (float) (1.0D - Mth.clamp((my - planeTop()) / (double) (PLANE_H - 1), 0.0D, 1.0D));
            }
            case HUE -> this.hue = (float) Mth.clamp((mx - innerLeft()) / w, 0.0D, 1.0D);
            case ALPHA -> this.alpha = (float) Mth.clamp((mx - innerLeft()) / w, 0.0D, 1.0D);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        DragTarget target = this.hitTest(mouseX, mouseY);
        if (target != null) {
            this.dragging = target;
            this.applyDrag(target, mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (this.dragging != null) {
            // 拖出区域边界也继续跟随，这样手感更顺（值会被 clamp 住）
            this.applyDrag(this.dragging, mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragging != null) {
            this.dragging = null;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void randomize() {
        this.hue = (float) Math.random();
        this.saturation = 0.4F + (float) Math.random() * 0.6F;
        this.value = 0.6F + (float) Math.random() * 0.4F;
        this.alpha = 1.0F;
    }

    @Override
    protected void init() {
        super.init();

        int btnW = (this.innerW() - 8) / 2;
        int btnY = this.panelTop() + this.panelH() - 32;

        this.addRenderableWidget(Button.builder(
                        Component.translatable("testmod.rgb.random"),
                        b -> this.randomize())
                .bounds(this.innerLeft(), btnY, btnW, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("testmod.button.close"),
                        b -> this.onClose())
                .bounds(this.innerLeft() + btnW + 8, btnY, btnW, 20)
                .build());
    }

    // ── 渲染 ────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 背景只画一次。详见 DemoGuiScreen 的注释：
        // 多调一次会让 renderBackground 里那个延迟模糊糊掉自绘文字。
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

        // ③ 主体：SV 平面 + 色相条 + 透明度条
        this.renderSvPlane(graphics);
        this.renderHueBar(graphics);
        this.renderAlphaBar(graphics);

        // ④ 数值回显
        int textX = this.innerLeft();
        int ty = this.textTop();
        graphics.drawString(this.font, Component.literal(this.hex()), textX, ty, TEXT_VALUE, false);
        graphics.drawString(this.font,
                Component.translatable("testmod.rgb.rgb", this.red(), this.green(), this.blue()),
                textX, ty + 11, TEXT_DIM, false);
        graphics.drawString(this.font,
                Component.translatable("testmod.rgb.alpha", Math.round(this.alpha * 100.0F)),
                textX, ty + 22, TEXT_DIM, false);

        // ⑤ 最后画控件（手动遍历，避开 super.render() 里那次多余的 renderBackground）
        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    /**
     * 画饱和度/明度二维平面。
     *
     * <p>{@code fillGradient} 只能画竖向渐变，所以这里按列绘制：
     * 每一列从顶部的「该饱和度下的最亮色」渐变到底部的黑色。</p>
     */
    private void renderSvPlane(GuiGraphics graphics) {
        int x = this.innerLeft();
        int y = this.planeTop();
        int w = this.innerW();
        int h = PLANE_H;

        for (int col = 0; col < w; col++) {
            float s = w <= 1 ? 0.0F : col / (float) (w - 1);
            int topColor = Mth.hsvToArgb(this.hue, s, 1.0F, 255);
            int bottomColor = Mth.hsvToArgb(this.hue, s, 0.0F, 255);
            graphics.fillGradient(x + col, y, x + col + 1, y + h, topColor, bottomColor);
        }

        graphics.renderOutline(x, y, w, h, PANEL_BORDER);

        // 当前位置标记：十字准星（黑白双色，保证在任何底色上都看得见）
        int mx = x + Math.round(this.saturation * (w - 1));
        int my = y + Math.round((1.0F - this.value) * (h - 1));
        graphics.renderOutline(mx - 3, my - 3, 7, 7, MARKER_DARK);
        graphics.renderOutline(mx - 2, my - 2, 5, 5, MARKER_LIGHT);
    }

    /** 画色相条：逐列填充纯色相，0.0 在左、1.0 在右。 */
    private void renderHueBar(GuiGraphics graphics) {
        int x = this.innerLeft();
        int y = this.hueTop();
        int w = this.innerW();

        for (int col = 0; col < w; col++) {
            float h = w <= 1 ? 0.0F : col / (float) (w - 1);
            graphics.fill(x + col, y, x + col + 1, y + BAR_H, Mth.hsvToArgb(h, 1.0F, 1.0F, 255));
        }

        graphics.renderOutline(x, y, w, BAR_H, PANEL_BORDER);

        int mx = x + Math.round(this.hue * (w - 1));
        graphics.renderOutline(mx - 2, y - 2, 5, BAR_H + 4, MARKER_DARK);
        graphics.renderOutline(mx - 1, y - 1, 3, BAR_H + 2, MARKER_LIGHT);
    }

    /**
     * 画透明度条：先铺棋盘格（表示透明），再叠一层从全透明到当前颜色的横向渐变。
     *
     * <p>棋盘格是这类控件表现"透明"的通行做法：没有它的话，
     * 低透明度区域看起来和黑色没区别。</p>
     */
    private void renderAlphaBar(GuiGraphics graphics) {
        int x = this.innerLeft();
        int y = this.alphaTop();
        int w = this.innerW();

        // 棋盘格底（8px 一格）
        final int cell = 8;
        for (int row = 0; row * cell < BAR_H; row++) {
            for (int col = 0; col * cell < w; col++) {
                boolean light = ((row + col) & 1) == 0;
                int cx1 = x + col * cell;
                int cy1 = y + row * cell;
                int cx2 = Math.min(cx1 + cell, x + w);
                int cy2 = Math.min(cy1 + cell, y + BAR_H);
                graphics.fill(cx1, cy1, cx2, cy2, light ? 0xFFB0B0B0 : 0xFF707070);
            }
        }

        // 从全透明渐变到当前颜色（逐列，因为需要横向的 alpha 变化）
        for (int col = 0; col < w; col++) {
            float a = w <= 1 ? 1.0F : col / (float) (w - 1);
            int color = Mth.hsvToArgb(this.hue, this.saturation, this.value,
                    Mth.clamp((int) Math.round(a * 255.0F), 0, 255));
            graphics.fill(x + col, y, x + col + 1, y + BAR_H, color);
        }

        graphics.renderOutline(x, y, w, BAR_H, PANEL_BORDER);

        int mx = x + Math.round(this.alpha * (w - 1));
        graphics.renderOutline(mx - 2, y - 2, 5, BAR_H + 4, MARKER_DARK);
        graphics.renderOutline(mx - 1, y - 1, 3, BAR_H + 2, MARKER_LIGHT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
