package com.kaboom.jjk.client.screen;

import com.kaboom.jjk.network.NpcActionPayload;
import com.kaboom.jjk.npc.Mission;
import com.kaboom.jjk.npc.NpcType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Visual-novel style dialogue box: portrait on the left, typewriter text, and choices.
 * Pages: greeting, mission and training. Every action is validated by the server.
 */
public class NpcDialogueScreen extends Screen {
    private enum Page { MAIN, MISSION, TRAIN }

    private static final int BOX_H = 112;

    private final int entityId;
    private CompoundTag data;
    private NpcType npc;
    private Page page = Page.MAIN;
    private Component text = Component.empty();
    private int age;
    private int textStart;

    public NpcDialogueScreen(int entityId, CompoundTag data) {
        super(Component.empty());
        this.entityId = entityId;
        refresh(data);
    }

    public int entityId() {
        return entityId;
    }

    public void refresh(CompoundTag newData) {
        this.data = newData;
        this.npc = NpcType.byId(newData.getString("npc"));
        if (minecraft != null) rebuild();
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int boxTop = height - BOX_H - 8;
        int x = 140;
        int y = boxTop + BOX_H - 28;
        int w = Math.min(110, (width - x - 20) / 4 - 4);
        Mission active = data.contains("active") ? Mission.load(data.getCompound("active")) : null;
        Mission offer = Mission.load(data.getCompound("offer"));

        switch (page) {
            case MAIN -> {
                say(Component.translatable("jjk.npc." + npc.id + ".greeting"));
                addRenderableWidget(Button.builder(Component.translatable("jjk.npc.button.missions"), b -> setPage(Page.MISSION))
                        .bounds(x, y, w, 20).build());
                Button trade = addRenderableWidget(Button.builder(Component.translatable("jjk.npc.button.trade"), b -> send(NpcActionPayload.Action.TRADE))
                        .bounds(x + (w + 4), y, w, 20).build());
                trade.active = data.getBoolean("hasId");
                if (!trade.active) trade.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("jjk.npc.need_id")));
                if (data.getBoolean("teacher")) {
                    addRenderableWidget(Button.builder(Component.translatable("jjk.npc.button.train"), b -> setPage(Page.TRAIN))
                            .bounds(x + 2 * (w + 4), y, w, 20).build());
                }
                addRenderableWidget(Button.builder(Component.translatable("jjk.npc.button.bye"), b -> onClose())
                        .bounds(x + 3 * (w + 4), y, w, 20).build());
            }
            case MISSION -> {
                if (active != null) {
                    say(Component.translatable("jjk.npc.mission_active", active.describe()));
                    addRenderableWidget(Button.builder(Component.translatable("jjk.npc.button.turn_in"), b -> send(NpcActionPayload.Action.TURN_IN))
                            .bounds(x, y, w + 30, 20).build());
                } else if (offer != null) {
                    say(Component.translatable("jjk.npc.mission_offer", offer.describe(), offer.xpReward()));
                    addRenderableWidget(Button.builder(Component.translatable("jjk.npc.button.accept"), b -> send(NpcActionPayload.Action.ACCEPT_MISSION))
                            .bounds(x, y, w + 30, 20).build());
                }
                addRenderableWidget(Button.builder(Component.translatable("jjk.npc.button.back"), b -> setPage(Page.MAIN))
                        .bounds(x + w + 38, y, w, 20).build());
            }
            case TRAIN -> {
                say(Component.translatable("jjk.npc." + npc.id + ".train"));
                Button rct = addRenderableWidget(Button.builder(Component.translatable(data.getBoolean("rct") ? "jjk.npc.button.rct_done" : "jjk.npc.button.rct"),
                        b -> send(NpcActionPayload.Action.TRAIN_RCT)).bounds(x, y, w + 40, 20).build());
                rct.active = !data.getBoolean("rct");
                rct.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("jjk.train.rct_requirements")));
                Button bf = addRenderableWidget(Button.builder(Component.translatable(data.getBoolean("blackFlash") ? "jjk.npc.button.black_flash_done" : "jjk.npc.button.black_flash"),
                        b -> send(NpcActionPayload.Action.TRAIN_BLACK_FLASH)).bounds(x + w + 44, y, w + 40, 20).build());
                bf.active = !data.getBoolean("blackFlash");
                bf.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("jjk.train.black_flash_requirements")));
                addRenderableWidget(Button.builder(Component.translatable("jjk.npc.button.back"), b -> setPage(Page.MAIN))
                        .bounds(x + 2 * w + 88, y, Math.max(40, w - 30), 20).build());
            }
        }
    }

    private void setPage(Page p) {
        page = p;
        rebuild();
    }

    private void say(Component c) {
        if (!c.getString().equals(text.getString())) {
            text = c;
            textStart = age;
        }
    }

    private void send(NpcActionPayload.Action action) {
        PacketDistributor.sendToServer(new NpcActionPayload(entityId, action));
        if (action == NpcActionPayload.Action.TRADE) onClose();
    }

    @Override
    public void tick() {
        age++;
        if (minecraft != null && minecraft.level != null && !(minecraft.level.getEntity(entityId) instanceof LivingEntity)) onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // light vignette only: the world stays visible behind the dialogue
        g.fillGradient(0, height / 2, width, height, 0x00000000, 0xA0000000);
        int top = height - BOX_H - 8;
        int color = npc.color;

        // portrait panel
        g.fill(8, top - 60, 132, top + BOX_H, 0xE0100A18);
        g.fill(8, top - 60, 132, top - 58, 0xFFB01818);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(entityId) instanceof LivingEntity e) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, 10, top - 56, 130, top + BOX_H - 4, 52, 0.0625f, mouseX, mouseY, e);
        }

        // dialogue box with talisman trim
        g.fill(134, top, width - 8, top + BOX_H, 0xE8120C1C);
        g.fill(134, top, width - 8, top + 2, 0xFFB01818);
        g.fill(134, top + BOX_H - 2, width - 8, top + BOX_H, 0xFFB01818);
        g.fill(134, top, 136, top + BOX_H, color | 0xFF000000);

        // name plate
        Component name = Component.translatable("entity.jjk." + npc.id);
        int nw = font.width(name) + 16;
        g.fill(146, top - 12, 146 + nw, top + 4, 0xFF1E1426);
        g.fill(146, top - 12, 146 + nw, top - 11, color | 0xFF000000);
        g.drawString(font, name, 154, top - 8, color | 0xFF000000, false);

        // typewriter text
        String full = text.getString();
        int shown = Mth.clamp((int) ((age - textStart + partial) * 1.6f), 0, full.length());
        List<FormattedCharSequence> lines = font.split(Component.literal(full.substring(0, shown)), width - 170);
        for (int i = 0; i < Math.min(lines.size(), 6); i++) g.drawString(font, lines.get(i), 146, top + 12 + i * 11, 0xFFEDE6D8, false);
    }
}
