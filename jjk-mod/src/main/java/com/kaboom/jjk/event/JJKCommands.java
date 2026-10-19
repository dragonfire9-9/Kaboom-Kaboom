package com.kaboom.jjk.event;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.Grade;
import com.kaboom.jjk.data.HeavenlyRestriction;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.data.Technique;
import com.kaboom.jjk.data.CETier;
import com.kaboom.jjk.data.Clan;
import com.kaboom.jjk.network.SyncJJKDataPayload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.function.BiConsumer;

/** {@code /jjk} — inspect and (for operators) edit jujutsu data. Handy for testing every phase. */
@EventBusSubscriber(modid = JJKMod.MODID)
public final class JJKCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("jjk")
                .then(Commands.literal("info").executes(c -> info(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> info(c, EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("grade").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("grade", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Grade.values()).map(Grade::id), b))
                                .executes(c -> edit(c, (p, data) -> data.setGrade(Grade.byId(StringArgumentType.getString(c, "grade")))))))
                .then(Commands.literal("xp").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(c -> edit(c, (p, data) -> data.addExperience(IntegerArgumentType.getInteger(c, "amount"))))))
                .then(Commands.literal("technique").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("technique", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Technique.values()).map(Technique::id), b))
                                .executes(c -> edit(c, (p, data) -> data.setTechnique(Technique.byId(StringArgumentType.getString(c, "technique")),
                                        data.getCeTier(), data.getCeNature())))))
                .then(Commands.literal("tier").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("tier", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(CETier.values()).map(CETier::id), b))
                                .executes(c -> edit(c, (p, data) -> data.setTechnique(data.getTechnique(),
                                        CETier.byId(StringArgumentType.getString(c, "tier")), data.getCeNature())))))
                .then(Commands.literal("clan").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("clan", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Clan.values()).map(Clan::id), b))
                                .executes(c -> edit(c, (p, data) -> data.setClan(Clan.byId(StringArgumentType.getString(c, "clan")))))))
                .then(Commands.literal("restriction").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("restriction", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(HeavenlyRestriction.values()).map(HeavenlyRestriction::id), b))
                                .executes(c -> edit(c, (p, data) -> data.setRestriction(HeavenlyRestriction.byId(StringArgumentType.getString(c, "restriction")))))))
                .then(Commands.literal("fill").requires(s -> s.hasPermission(2))
                        .executes(c -> edit(c, (p, data) -> {
                            data.setCurrentCE(data.getMaxCE());
                            for (String id : data.getUnlockedAbilities()) data.setCooldown(id, 0);
                        })))
                .then(Commands.literal("unlock").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("rct").executes(c -> edit(c, (p, data) -> data.setRctUnlocked(true))))
                        .then(Commands.literal("black_flash").executes(c -> edit(c, (p, data) -> data.setBlackFlashUnlocked(true)))))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(2))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            CursedEnergyData fresh = new CursedEnergyData();
                            fresh.setStarterKitGiven(true);
                            p.setData(ModAttachments.CURSED_ENERGY, fresh);
                            PlayerStats.applyAttributes(p, fresh);
                            SyncJJKDataPayload.sync(p);
                            c.getSource().sendSuccess(() -> Component.translatable("jjk.command.reset"), true);
                            return 1;
                        })));
    }

    private static int edit(CommandContext<CommandSourceStack> c, BiConsumer<ServerPlayer, CursedEnergyData> action) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        CursedEnergyData data = ModAttachments.get(p);
        action.accept(p, data);
        PlayerStats.applyAttributes(p, data);
        SyncJJKDataPayload.sync(p);
        return info(c, p);
    }

    private static int info(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        CursedEnergyData d = ModAttachments.get(p);
        c.getSource().sendSuccess(() -> Component.translatable("jjk.command.info",
                p.getDisplayName(), d.getGrade().displayName(), d.getTechnique().displayName(), d.getClan().displayName(),
                d.getCeTier().displayName(), d.getCeNature().displayName(), d.getRestriction().displayName(),
                String.format("%.0f/%.0f", d.getCurrentCE(), d.getMaxCE()), String.format("%.2f", d.getRegenPerSecond()),
                d.getExperience(), d.getCurseKills(), String.join(", ", d.getUnlockedAbilities())), false);
        return 1;
    }

    private JJKCommands() {}
}
