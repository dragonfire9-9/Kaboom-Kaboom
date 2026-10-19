package com.kaboom.jjk.client.screen;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.data.CENature;
import com.kaboom.jjk.data.CETier;
import com.kaboom.jjk.data.Clan;
import com.kaboom.jjk.data.HeavenlyRestriction;
import com.kaboom.jjk.data.Rarity;
import com.kaboom.jjk.data.Technique;
import com.kaboom.jjk.network.RollActionPayload;
import com.kaboom.jjk.roll.RollResult;
import com.kaboom.jjk.roll.RollType;
import com.kaboom.jjk.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Gacha-style reel. The outcome is decided by the server before this screen opens;
 * the reel is built so that the winning cell lands exactly under the marker.
 */
public class RollScreen extends Screen {
    private static final ResourceLocation PARCHMENT = JJKMod.id("textures/gui/roll_parchment.png");
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 210;

    private static final int CELL_W = 52;
    private static final int CELL_H = 42;
    private static final int REEL_LENGTH = 44;
    private static final int TARGET_INDEX = 38;
    private static final int SPIN_TICKS = 74;
    private static final int UNROLL_TICKS = 8;

    private record Cell(Component label, Rarity rarity, int color) {}

    private record Sparkle(float x, float y, float vx, float vy, int life, int color) {}

    private final RandomSource random = RandomSource.create();
    private final List<Cell> reel = new ArrayList<>();
    private final List<Sparkle> sparkles = new ArrayList<>();

    private RollResult result;
    private int scrollsRemaining;
    private int age;
    private int spinTick;
    private int revealTick = -1;
    private int lastCell = -1;
    private boolean awaitingServer;
    private boolean decided;

    private Button acceptButton;
    private Button rerollButton;
    private Button discardButton;

    public RollScreen(RollResult result, int scrollsRemaining) {
        super(Component.translatable("jjk.roll.title." + result.type().id()));
        this.result = result;
        this.scrollsRemaining = scrollsRemaining;
        buildReel();
    }

    public RollType type() {
        return result.type();
    }

    /** Called when a reroll result arrives from the server. */
    public void respin(RollResult newResult, int scrollsRemaining) {
        this.result = newResult;
        this.scrollsRemaining = scrollsRemaining;
        this.spinTick = 0;
        this.revealTick = -1;
        this.lastCell = -1;
        this.awaitingServer = false;
        this.sparkles.clear();
        buildReel();
        updateButtons();
    }

    // ------------------------------------------------------------------ setup

    private void buildReel() {
        reel.clear();
        List<Cell> pool = pool(result.type());
        for (int i = 0; i < REEL_LENGTH; i++) {
            reel.add(i == TARGET_INDEX ? cellFor(result) : weightedFiller(pool));
        }
    }

    private Cell weightedFiller(List<Cell> pool) {
        // Visual only: commons appear often, special grades flash past now and then to build tension.
        double total = 0;
        for (Cell c : pool) total += fillerWeight(c.rarity());
        double r = random.nextDouble() * total;
        for (Cell c : pool) {
            r -= fillerWeight(c.rarity());
            if (r < 0) return c;
        }
        return pool.get(0);
    }

    private static double fillerWeight(Rarity r) {
        return switch (r) {
            case COMMON -> 10;
            case RARE -> 7;
            case EPIC -> 5;
            case LEGENDARY -> 3;
            case SPECIAL_GRADE -> 2;
        };
    }

    private static List<Cell> pool(RollType type) {
        List<Cell> out = new ArrayList<>();
        switch (type) {
            case CURSED_ENERGY -> {
                for (Technique t : Technique.values()) if (t != Technique.NONE) out.add(new Cell(t.displayName(), t.rarity, t.rarity.color));
            }
            case CLAN -> {
                for (Clan c : Clan.values()) out.add(new Cell(c.displayName(), c.rarity, c.rarity.color));
            }
            case HEAVENLY_RESTRICTION -> {
                for (HeavenlyRestriction h : HeavenlyRestriction.values()) out.add(new Cell(h.displayName(), h.rarity, h.rarity.color));
            }
        }
        return out;
    }

    private static Cell cellFor(RollResult r) {
        return new Cell(r.title(), r.rarity(), r.color());
    }

    @Override
    protected void init() {
        int x = (width - PANEL_W) / 2;
        int y = (height - PANEL_H) / 2;
        int by = y + PANEL_H - 32;
        acceptButton = addRenderableWidget(Button.builder(Component.translatable("jjk.roll.accept"), b -> choose(RollActionPayload.Action.ACCEPT))
                .bounds(x + 22, by, 82, 20).build());
        rerollButton = addRenderableWidget(Button.builder(rerollLabel(), b -> reroll())
                .bounds(x + 109, by, 96, 20).build());
        discardButton = addRenderableWidget(Button.builder(Component.translatable("jjk.roll.discard"), b -> choose(RollActionPayload.Action.DISCARD))
                .bounds(x + 210, by, 68, 20).build());
        updateButtons();
    }

    private Component rerollLabel() {
        return Component.translatable("jjk.roll.reroll", scrollsRemaining >= 99 ? "∞" : String.valueOf(scrollsRemaining));
    }

    private void updateButtons() {
        if (acceptButton == null) return;
        boolean ready = isRevealed() && !awaitingServer;
        acceptButton.active = ready;
        discardButton.active = ready;
        rerollButton.active = ready && scrollsRemaining > 0;
        rerollButton.setMessage(rerollLabel());
    }

    private void choose(RollActionPayload.Action action) {
        decided = true;
        PacketDistributor.sendToServer(new RollActionPayload(action));
        onClose();
    }

    private void reroll() {
        awaitingServer = true;
        updateButtons();
        PacketDistributor.sendToServer(new RollActionPayload(RollActionPayload.Action.REROLL));
    }

    @Override
    public void removed() {
        // Closed by anything other than a button (disconnect, another screen): keep the old data.
        if (!decided) PacketDistributor.sendToServer(new RollActionPayload(RollActionPayload.Action.DISCARD));
        super.removed();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ animation

    private boolean isRevealed() {
        return revealTick >= 0;
    }

    private float spinProgress(float partial) {
        return Mth.clamp((spinTick + partial) / SPIN_TICKS, 0, 1);
    }

    /** Reel position in cells; ease-out quart so it whips past early and crawls into place. */
    private float reelOffset(float partial) {
        float t = spinProgress(partial);
        float eased = 1 - (float) Math.pow(1 - t, 4);
        return eased * TARGET_INDEX;
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (age < UNROLL_TICKS) return;

        if (!isRevealed()) {
            spinTick++;
            int cell = Math.round(reelOffset(0));
            if (cell != lastCell) {
                lastCell = cell;
                float p = spinProgress(0);
                play(ModSounds.ROLL_TICK.get(), 0.9f + 0.7f * p, 0.6f);
            }
            if (spinTick >= SPIN_TICKS) startReveal();
        } else {
            revealTick++;
            if (result.rarity().ordinal() >= Rarity.EPIC.ordinal() && revealTick % 3 == 0) spawnSparkles(3);
        }

        sparkles.replaceAll(s -> new Sparkle(s.x() + s.vx(), s.y() + s.vy(), s.vx() * 0.94f, s.vy() * 0.94f + 0.05f, s.life() - 1, s.color()));
        sparkles.removeIf(s -> s.life() <= 0);
    }

    private void startReveal() {
        revealTick = 0;
        Rarity rarity = result.rarity();
        boolean big = rarity.ordinal() >= Rarity.LEGENDARY.ordinal();
        play(big ? ModSounds.ROLL_REVEAL_RARE.get() : ModSounds.ROLL_REVEAL.get(), 1.0f, 1.0f);
        spawnSparkles(big ? 60 : rarity.ordinal() >= Rarity.RARE.ordinal() ? 30 : 12);
        updateButtons();
    }

    private void spawnSparkles(int count) {
        int cx = width / 2;
        int cy = (height - PANEL_H) / 2 + 40 + CELL_H / 2;
        for (int i = 0; i < count; i++) {
            float a = random.nextFloat() * Mth.TWO_PI;
            float speed = 1.5f + random.nextFloat() * 3.5f;
            sparkles.add(new Sparkle(cx, cy, Mth.cos(a) * speed, Mth.sin(a) * speed - 1.5f, 18 + random.nextInt(18),
                    random.nextBoolean() ? result.color() : 0xFFFFFFFF));
        }
    }

    private void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ------------------------------------------------------------------ rendering

    /** Screen#render draws the background first, so the parchment and reel live here, under the buttons. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderTransparentBackground(g);
        int x = (width - PANEL_W) / 2;
        int y = (height - PANEL_H) / 2;

        // Unroll: the parchment opens from the middle outward.
        float unroll = Mth.clamp((age + partial) / UNROLL_TICKS, 0, 1);
        int visibleH = Math.max(12, Math.round(PANEL_H * (1 - (float) Math.pow(1 - unroll, 3))));
        int top = y + (PANEL_H - visibleH) / 2;
        g.enableScissor(x, top, x + PANEL_W, top + visibleH);

        g.blit(PARCHMENT, x, y, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
        drawCenteredNoShadow(g, title, width / 2 + 1, y + 19, 0x40000000);
        drawCenteredNoShadow(g, title, width / 2, y + 18, 0xFF7A1010);

        renderReel(g, x + 20, y + 38, partial);
        if (isRevealed()) renderResult(g, x, y + 88, partial);
        else drawCenteredNoShadow(g, Component.translatable("jjk.roll.spinning"), width / 2, y + 120, 0xFF5A3A20);

        g.disableScissor();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        boolean opened = age >= UNROLL_TICKS;
        acceptButton.visible = opened;
        rerollButton.visible = opened;
        discardButton.visible = opened;
        super.render(g, mouseX, mouseY, partial); // background (panel) + buttons
        renderSparkles(g, partial);
        renderFlash(g, partial);
    }

    private void renderReel(GuiGraphics g, int rx, int ry, float partial) {
        int rw = PANEL_W - 40;
        int cx = rx + rw / 2;

        // ink-black window with a red lacquer frame
        g.fill(rx - 3, ry - 3, rx + rw + 3, ry + CELL_H + 7, 0xFF5A0E0E);
        g.fill(rx - 1, ry - 1, rx + rw + 1, ry + CELL_H + 5, 0xFF000000);
        g.fill(rx, ry, rx + rw, ry + CELL_H + 4, 0xFF120A18);

        g.enableScissor(rx, ry, rx + rw, ry + CELL_H + 4);
        float offset = reelOffset(partial);
        int first = Math.max(0, (int) Math.floor(offset) - 4);
        int last = Math.min(reel.size() - 1, (int) Math.ceil(offset) + 4);
        for (int i = first; i <= last; i++) {
            float cellX = cx + (i - offset) * CELL_W - CELL_W / 2f;
            renderCell(g, reel.get(i), Math.round(cellX), ry + 2, i == TARGET_INDEX && isRevealed(), partial);
        }
        // motion blur-ish fade at both edges
        for (int i = 0; i < 24; i++) {
            int a = (int) (200 * (1 - i / 24f));
            g.fill(rx + i, ry, rx + i + 1, ry + CELL_H + 4, a << 24 | 0x120A18);
            g.fill(rx + rw - i - 1, ry, rx + rw - i, ry + CELL_H + 4, a << 24 | 0x120A18);
        }
        g.disableScissor();

        // centre marker: two red talisman tabs
        g.fill(cx - 1, ry - 6, cx + 1, ry + 1, 0xFFE02020);
        g.fill(cx - 4, ry - 6, cx + 4, ry - 3, 0xFFE02020);
        g.fill(cx - 1, ry + CELL_H + 3, cx + 1, ry + CELL_H + 10, 0xFFE02020);
        g.fill(cx - 4, ry + CELL_H + 7, cx + 4, ry + CELL_H + 10, 0xFFE02020);
    }

    private void renderCell(GuiGraphics g, Cell cell, int x, int y, boolean winner, float partial) {
        int w = CELL_W - 4;
        int color = cell.color();
        if (winner) {
            float pulse = 0.5f + 0.5f * Mth.sin((revealTick + partial) * 0.35f);
            for (int i = 6; i >= 1; i--) {
                int a = (int) ((30 + 50 * pulse) / i);
                g.fill(x + 2 - i, y - i, x + 2 + w + i, y + CELL_H + i, a << 24 | (color & 0xFFFFFF));
            }
        }
        g.fill(x + 2, y, x + 2 + w, y + CELL_H, winner ? 0xFF2A1A30 : 0xFF1E1426);
        g.fill(x + 2, y, x + 2 + w, y + 3, color);              // rarity stripe
        g.fill(x + 2, y + CELL_H - 1, x + 2 + w, y + CELL_H, darken(color));

        float scale = 0.62f;
        List<FormattedCharSequence> lines = font.split(cell.label(), (int) ((w - 4) / scale));
        int shown = Math.min(lines.size(), 3);
        float lineH = font.lineHeight * scale;
        float startY = y + 4 + (CELL_H - 4 - shown * lineH) / 2f;
        g.pose().pushPose();
        g.pose().translate(x + 2 + w / 2f, startY, 0);
        g.pose().scale(scale, scale, 1);
        for (int i = 0; i < shown; i++) {
            FormattedCharSequence line = lines.get(i);
            g.drawString(font, line, -font.width(line) / 2, (int) (i * font.lineHeight), color, false);
        }
        g.pose().popPose();
    }

    private void renderResult(GuiGraphics g, int x, int y, float partial) {
        Rarity rarity = result.rarity();
        float appear = Mth.clamp((revealTick + partial) / 10f, 0, 1);
        int alpha = (int) (appear * 255) << 24;

        // dark ink band so rarity colours stay readable on parchment
        g.fill(x + 16, y, x + PANEL_W - 16, y + 80, (int) (appear * 0xB0) << 24 | 0x1A1020);
        float pulse = 0.5f + 0.5f * Mth.sin((revealTick + partial) * 0.25f);
        g.fill(x + 16, y, x + PANEL_W - 16, y + 1, (int) (appear * (120 + 120 * pulse)) << 24 | (rarity.color & 0xFFFFFF));
        g.fill(x + 16, y + 79, x + PANEL_W - 16, y + 80, (int) (appear * (120 + 120 * pulse)) << 24 | (rarity.color & 0xFFFFFF));

        // title, scaled up with a soft glow
        Component title = result.title();
        float s = 1.45f;
        g.pose().pushPose();
        g.pose().translate(width / 2f, y + 6, 0);
        g.pose().scale(s, s, 1);
        int tw = font.width(title);
        int glow = (int) (appear * (60 + 60 * pulse)) << 24 | (rarity.color & 0xFFFFFF);
        g.drawString(font, title.getString(), -tw / 2 - 1, 0, glow, false);
        g.drawString(font, title.getString(), -tw / 2 + 1, 0, glow, false);
        g.drawString(font, title.getString(), -tw / 2, 1, glow, false);
        g.drawString(font, title.getString(), -tw / 2, 0, alpha | (rarity.color & 0xFFFFFF), false);
        g.pose().popPose();

        Component rarityLine = rarity.displayName();
        g.drawCenteredString(font, rarityLine, width / 2, y + 22, alpha | 0xFFFFFF);

        int lineY = y + 34;
        if (result.type() == RollType.CURSED_ENERGY) {
            Component stats = Component.translatable("jjk.roll.ce_line",
                    CETier.byId(result.secondary()).displayName(), CENature.byId(result.tertiary()).displayName());
            g.drawCenteredString(font, stats, width / 2, lineY, alpha | 0xD8CCFF);
            lineY += 11;
        }
        List<FormattedCharSequence> desc = font.split(result.description(), PANEL_W - 48);
        for (int i = 0; i < Math.min(desc.size(), result.type() == RollType.CURSED_ENERGY ? 3 : 4); i++) {
            FormattedCharSequence line = desc.get(i);
            g.drawString(font, line, width / 2 - font.width(line) / 2, lineY + i * 10, alpha | 0xC8C0B8, false);
        }
    }

    private void renderSparkles(GuiGraphics g, float partial) {
        for (Sparkle s : sparkles) {
            float px = s.x() + s.vx() * partial;
            float py = s.y() + s.vy() * partial;
            int a = Mth.clamp(s.life() * 12, 0, 255);
            int size = s.life() > 12 ? 2 : 1;
            g.fill((int) px, (int) py, (int) px + size, (int) py + size, a << 24 | (s.color() & 0xFFFFFF));
        }
    }

    /** Screen flash on Legendary / Special Grade reveals. */
    private void renderFlash(GuiGraphics g, float partial) {
        if (!isRevealed() || result.rarity().ordinal() < Rarity.LEGENDARY.ordinal()) return;
        float t = (revealTick + partial) / 8f;
        if (t >= 1) return;
        int a = (int) ((1 - t) * 170);
        g.fill(0, 0, width, height, a << 24 | (result.rarity() == Rarity.SPECIAL_GRADE ? 0xFFD0D0 : 0xFFF0C0));
    }

    private void drawCenteredNoShadow(GuiGraphics g, Component text, int cx, int y, int color) {
        g.drawString(font, text, cx - font.width(text) / 2, y, color, false);
    }

    private static int darken(int c) {
        return 0xFF000000 | ((int) (((c >> 16) & 0xFF) * 0.5f) << 16) | ((int) (((c >> 8) & 0xFF) * 0.5f) << 8) | (int) ((c & 0xFF) * 0.5f);
    }
}
