/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.example.bricklayer.Bricklayer;
import dev.example.bricklayer.compat.CompatGate;
import dev.example.bricklayer.config.BricklayerConfig;
import dev.example.bricklayer.core.grid.SectionGrid;
import dev.example.bricklayer.core.grid.SectionSnapshot;
import dev.example.bricklayer.core.meta.CellFlags;
import dev.example.bricklayer.meta.VanillaStates;
import dev.example.bricklayer.snapshot.Snapshotter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** {@code /bricklayer stats|toggle|parity|dump}. */
public final class BricklayerCommand {
  private BricklayerCommand() {}

  public static void register(RegisterClientCommandsEvent event) {
    CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
    dispatcher.register(
        Commands.literal("bricklayer")
            .then(Commands.literal("stats").executes(BricklayerCommand::stats))
            .then(Commands.literal("toggle").executes(BricklayerCommand::toggle))
            .then(Commands.literal("parity").executes(BricklayerCommand::parity))
            .then(Commands.literal("reload").executes(BricklayerCommand::reload))
            .then(
                Commands.literal("dump")
                    .executes(context -> dump(context, null))
                    .then(
                        Commands.argument("pos", BlockPosArgument.blockPos())
                            .executes(
                                context ->
                                    dump(
                                        context,
                                        BlockPosArgument.getLoadedBlockPos(context, "pos"))))));
  }

  private static int stats(CommandContext<CommandSourceStack> context) {
    var scheduler = Bricklayer.get().scheduler();
    var governor = Bricklayer.get().uploadGovernor();
    feedback(
        context, Component.translatable("bricklayer.command.stats.header"), ChatFormatting.GOLD);
    feedback(
        context,
        Component.literal(
            "gate="
                + CompatGate.mode()
                + " workers="
                + scheduler.workerCount()
                + " queued="
                + scheduler.queueDepth()
                + " inFlight="
                + scheduler.inFlight()
                + " uploadsQueued="
                + governor.queued()),
        ChatFormatting.GRAY);
    feedback(
        context,
        Component.literal(
            String.format(
                "meanCompile=%.3fms deferred=%.1f%% uploads=%d tables=%d states/%d models",
                scheduler.meanCompileMs(),
                scheduler.deferredRatio() * 100.0,
                governor.uploadsTotal(),
                Bricklayer.metaTable().size(),
                Bricklayer.quadStore().modelCount())),
        ChatFormatting.GRAY);
    feedback(
        context,
        Component.literal(
            "visibilityCache="
                + Bricklayer.faceVisibility().size()
                + " decodeCache="
                + Bricklayer.get().decodeCache().size()
                + " buffers={"
                + Bricklayer.get().bufferPool().describe()
                + "}"),
        ChatFormatting.DARK_GRAY);
    return 1;
  }

  private static int toggle(CommandContext<CommandSourceStack> context) {
    boolean enabled = !BricklayerConfig.get().enabled();
    Bricklayer.LOGGER.info(
        "Bricklayer toggled to {} (change enabled= in the config to persist)", enabled);
    feedback(
        context,
        Component.translatable("bricklayer.command.toggle", enabled),
        enabled ? ChatFormatting.GREEN : ChatFormatting.RED);
    CompatGate.evaluate();
    return 1;
  }

  private static int parity(CommandContext<CommandSourceStack> context) {
    Minecraft minecraft = Minecraft.getInstance();
    ClientLevel level = minecraft.level;
    if (level == null || minecraft.player == null) {
      return 0;
    }
    SectionPos pos = SectionPos.of(minecraft.player.blockPosition());
    for (Component line : ParityTool.run(level, pos)) {
      feedback(context, line, ChatFormatting.GRAY);
    }
    return 1;
  }

  private static int reload(CommandContext<CommandSourceStack> context) {
    CompatGate.resetBreaker();
    Bricklayer.get().scheduler().reset();
    Bricklayer.get().bufferPool().clear();
    feedback(context, Component.literal("Bricklayer state reset"), ChatFormatting.GREEN);
    return 1;
  }

  private static int dump(CommandContext<CommandSourceStack> context, BlockPos target) {
    Minecraft minecraft = Minecraft.getInstance();
    ClientLevel level = minecraft.level;
    if (level == null) {
      return 0;
    }
    BlockPos pos = target != null ? target : minecraft.player.blockPosition();
    SectionPos sectionPos = SectionPos.of(pos);

    SectionSnapshot snapshot = new SectionSnapshot();
    new Snapshotter(Bricklayer.get().decodeCache())
        .capture(level, sectionPos, sectionPos.asLong(), 0, snapshot);

    StringBuilder out = new StringBuilder();
    out.append("bricklayer dump ").append(sectionPos).append('\n');
    out.append("generation=").append(Bricklayer.resourceGeneration()).append('\n');
    out.append("empty=")
        .append(snapshot.empty)
        .append(" blockEntities=")
        .append(snapshot.beCount)
        .append('\n');
    int localX =
        SectionGrid.cellX(SectionGrid.cellIndex(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15));
    int cell = SectionGrid.cellIndex(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    int grid = SectionGrid.gridOf(cell);
    int state = snapshot.state[grid];
    out.append("cell ").append(localX).append('/').append(cell).append(" state=").append(state);
    var blockState = VanillaStates.byId(state);
    out.append(" block=").append(blockState == null ? "?" : blockState.getBlock()).append('\n');
    out.append("flags=0x")
        .append(Integer.toHexString(Bricklayer.metaTable().flags(state)))
        .append('\n');
    for (int dir = 0; dir < 6; dir++) {
      int neighbourState =
          snapshot.state[grid + dev.example.bricklayer.core.grid.Dir.VALUES[dir].offset()];
      out.append("  face ")
          .append(dir)
          .append(" neighbour=")
          .append(neighbourState)
          .append(" visible=")
          .append(
              Bricklayer.faceVisibility()
                  .visible(state, neighbourState, dir, Bricklayer.get().visibilityOracle()))
          .append('\n');
    }
    out.append("model=").append(Bricklayer.metaTable().model(state)).append('\n');
    out.append("deferred=")
        .append(
            (Bricklayer.metaTable().flags(state) & (CellFlags.FORCE_VANILLA | CellFlags.FLUID))
                != 0)
        .append('\n');

    Path file =
        minecraft.gameDirectory.toPath().resolve("bricklayer-dump-" + sectionPos.asLong() + ".txt");
    try {
      Files.writeString(file, out.toString());
      feedback(
          context,
          Component.translatable(
              "bricklayer.command.dump.saved", sectionPos.toString(), file.toString()),
          ChatFormatting.GREEN);
    } catch (IOException failure) {
      feedback(
          context,
          Component.literal("Failed to write dump: " + failure.getMessage()),
          ChatFormatting.RED);
    }
    return 1;
  }

  private static void feedback(
      CommandContext<CommandSourceStack> context, Component message, ChatFormatting color) {
    context.getSource().sendSuccess(() -> message.copy().withStyle(color), false);
  }
}
