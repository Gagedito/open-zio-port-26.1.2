/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.CommandExecuteEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.MinecraftTickEvent;
import dev.zio.client.events.bus.EventBus;
import dev.zio.client.managers.TaskScheduler;
import dev.zio.client.managers.CommandRegistry;
import java.util.ArrayList;
import java.util.List;

public final class WaitCommand extends Command {
   private final List<String> pendingCommands = new ArrayList<>();
   private boolean hook;
   private int delayTicks;

   public WaitCommand() {
      super("wait");
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      IntegerArgumentType tickType = IntegerArgumentType.integer(0);
      RequiredArgumentBuilder ticksArgument = Command.argument("ticks", tickType);
      com.mojang.brigadier.Command<Object> startWaiting = context -> {
         Class<Integer> integerClass = Integer.class;
         Integer ticks = context.getArgument("ticks", integerClass);
         this.delayTicks = ticks;
         this.hook = true;
         return 1;
      };
      ArgumentBuilder waitingArgument = ticksArgument.executes(startWaiting);
      builder.then(waitingArgument);
   }

   @EventHandler
   private void onMinecraftTick(MinecraftTickEvent event) {
      if (this.hook) {
         this.hook = false;
         ArrayList<String> commands = new ArrayList<>(this.pendingCommands);
         this.pendingCommands.clear();
         TaskScheduler scheduler = ClientContext.taskScheduler;
         Runnable executeCommands = () -> {
            for (String command : commands) {
               CommandRegistry.executeCommand(command);
            }
         };
         int ticks = this.delayTicks;
         scheduler.scheduleRotationTask(executeCommands, ticks);
      }
   }

   @EventHandler
   private void onCommandExecute(CommandExecuteEvent event) {
      if (this.hook) {
         List<String> commands = this.pendingCommands;
         String command = event.getCommand();
         commands.add(command);
         event.cancel();
      }
   }
}
