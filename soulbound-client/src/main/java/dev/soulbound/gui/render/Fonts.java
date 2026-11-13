package dev.soulbound.gui.render;

import dev.soulbound.SoulBound;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Custom TTF fonts (assets/soulbound/font/*.json). */
public enum Fonts {
    BODY("body", 7.5f),
    BODY_BOLD("body_bold", 7.5f),
    LABEL("label", 6f),
    HEADING("heading", 9f),
    BRAND("brand", 10f),
    TITLE("title", 13f),
    MENU("menu", 10.5f),
    DISPLAY("display", 24f);

    private final Style style;
    public final float size;

    Fonts(String id, float size) {
        this.style = Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of(SoulBound.MOD_ID, id)));
        this.size = size;
    }

    public MutableText text(String s) { return Text.literal(s).setStyle(style); }

    private static TextRenderer tr() { return MinecraftClient.getInstance().textRenderer; }

    public float width(String s) { return tr().getWidth(text(s)); }

    public float width(String s, float tracking) {
        if (tracking == 0) return width(s);
        float w = 0;
        for (int i = 0; i < s.length(); i++) w += width(String.valueOf(s.charAt(i))) + tracking;
        return w - tracking;
    }

    /** Draw with the text's visual centre at {@code cy}. */
    public void drawCentered(DrawContext ctx, String s, float x, float cy, int color) {
        draw(ctx, s, x, cy - size / 2f, color);
    }

    public void draw(DrawContext ctx, String s, float x, float y, int color) {
        if ((color >>> 24) < 4) return;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        ctx.drawText(tr(), text(s), 0, 0, color, false);
        m.popMatrix();
    }

    /** Letter-spaced text (used for small caps labels and the brand). */
    public void drawTracked(DrawContext ctx, String s, float x, float y, float tracking, int color) {
        if (tracking == 0) { draw(ctx, s, x, y, color); return; }
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            String c = String.valueOf(s.charAt(i));
            draw(ctx, c, cx, y, color);
            cx += width(c) + tracking;
        }
    }

    /** Trim with an ellipsis so the string fits in {@code maxWidth}. */
    public String fit(String s, float maxWidth) {
        if (width(s) <= maxWidth) return s;
        String e = "…";
        int n = s.length();
        while (n > 0 && width(s.substring(0, n) + e) > maxWidth) n--;
        return s.substring(0, n) + e;
    }
}
