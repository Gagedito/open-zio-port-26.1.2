/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.player;


import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.Setting;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;

public class NoInteractModule extends Module {
   public final Setting<TargetsChoice> targetsSetting;
   public final Setting<ModeChoice> modeSetting;
   public final Setting<Boolean> onlyFoodSetting;
   public boolean shouldStopSneaking;

   public static InteractionHand selectUsableHand() {
      ItemStack mainHandStack = ClientAccess.zioMinecraft.player.getMainHandItem();
      ItemStack offhandStack = ClientAccess.zioMinecraft.player.getOffhandItem();

      if (!isFoodOrProjectile(mainHandStack) && isFoodOrProjectile(offhandStack)) {
         return InteractionHand.OFF_HAND;
      }

      return InteractionHand.MAIN_HAND;
   }

   private boolean isSpawnPointTarget(BlockState state) {
      if (ClientContext.worldStateManager.getDimension().isOverworld()) {
         return state.getBlock() instanceof BedBlock;
      }
      if (ClientContext.worldStateManager.getDimension().isNether()) {
         return state.getBlock() instanceof RespawnAnchorBlock;
      }
      return false;
   }

   public boolean shouldBlockInteraction(ItemStack stack, BlockPos position) {
      if (!this.isToggled()) {
         return false;
      }
      if (this.onlyFoodSetting.getValue()) {
         DataComponentType<?> foodComponent = DataComponents.FOOD;
         if (!stack.has(foodComponent)) {
            return false;
         }
      }
      ClientLevel level = ClientAccess.zioMinecraft.level;
      BlockState state = level.getBlockState(position);
      if (this.targetsSetting.getValue() == NoInteractModule.TargetsChoice.SPAWN_POINT) {
         return this.isSpawnPointTarget(state);
      }
      if (this.targetsSetting.getValue() == NoInteractModule.TargetsChoice.BLOCKS) {
         return this.isBlockInteractionTarget(state);
      }
      return this.isBlockInteractionTarget(state) || this.isSpawnPointTarget(state);
   }

   public static boolean isFoodOrProjectile(ItemStack stack) {
      DataComponentType<?> foodComponent = DataComponents.FOOD;
      if (stack.has(foodComponent)) {
         return true;
      }
      return stack.getItem() instanceof EnderpearlItem
         || stack.getItem() instanceof ProjectileItem
         || stack.getItem() instanceof ProjectileWeaponItem;
   }

   private boolean isBlockInteractionTarget(BlockState state) {
      if (state.getBlock() instanceof AnvilBlock) {
         return true;
      }

      return state.hasBlockEntity() && !(state.getBlock() instanceof BedBlock);
   }

   public NoInteractModule() {
      super("NoInteract", "Prevents you from interacting with tile entities.", ModuleCategory.PLAYER, new String[0]);
      String targetsName = "Targets";
      NoInteractModule.TargetsChoice defaultTargets = NoInteractModule.TargetsChoice.BLOCKS;
      EnumSetting<TargetsChoice> targetsOption = new EnumSetting<>(targetsName, defaultTargets);
      this.targetsSetting = this.add(targetsOption);
      String modeName = "Mode";
      NoInteractModule.ModeChoice defaultMode = NoInteractModule.ModeChoice.CANCEL;
      EnumSetting<ModeChoice> modeOption = new EnumSetting<>(modeName, defaultMode);
      this.modeSetting = this.add(modeOption);
      String onlyFoodName = "OnlyFood";
      Boolean defaultOnlyFood = false;
      BooleanSetting onlyFoodOption = new BooleanSetting(onlyFoodName, defaultOnlyFood);
      this.onlyFoodSetting = this.add(onlyFoodOption);
   }

   public enum ModeChoice implements NamedEntry {
      CANCEL("Cancel"),
      SHIFT("Shift");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }

   }

   public enum TargetsChoice implements NamedEntry {
      BLOCKS("Blocks"),
      SPAWN_POINT("SpawnPoint"),
      BOTH("Both");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      TargetsChoice(String displayName) {
         this.displayName = displayName;
      }

   }
}
