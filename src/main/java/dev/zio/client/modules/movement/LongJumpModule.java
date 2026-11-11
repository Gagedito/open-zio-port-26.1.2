/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.movement;


import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.KeyboardInputTickEvent;
import dev.zio.client.events.LocalPlayerMoveEvent;
import dev.zio.client.events.LocalPlayerSendPositionEvent;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.utils.movement.MovementPhysics;
import dev.zio.client.utils.math.Vec3Utils;
import dev.zio.client.utils.inventory.ItemUtils;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.utils.inventory.EnchantmentUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.events.EventStage;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;

public class LongJumpModule extends Module {
   public final Setting<ModeChoice> modeSetting;
   public final Setting<Float> jumpSpeedSetting;
   public final Setting<Boolean> inLiquidSetting;
   public final Setting<Integer> amountSetting;
   public final Setting<Boolean> autoJumpSetting;
   public final Setting<Boolean> useTimerSetting;
   public final Setting<Boolean> autoDisableSetting;
   protected double moveSpeed;
   protected double previousHorizontalDistance;
   protected int movementStage;
   private boolean alternateBoost;
   private boolean jumpRequested;
   private int spearUses;

   private InventoryUtils.SwapSlot findLungeWeapon() {
      Predicate<ItemStack> weaponPredicate = stack -> {
         if (ItemUtils.isSpear(stack)) {
            if (EnchantmentUtils.hasEnchantment(Enchantments.LUNGE, stack)) {
               return true;
            }
         }

         return false;
      };
      return InventoryUtils.findSwapSlot(weaponPredicate, true);
   }

   private void handleSpearLanding() {
      boolean completedJump = this.autoDisableSetting.getValue()
         ? this.jumpRequested
         : this.spearUses > 0;
      if (this.autoDisableSetting.getValue() && completedJump) {
         this.disable();
      } else {
         if (this.autoJumpSetting.getValue()) {
            ClientContext.inputOverrides.requestJump(true);
            this.jumpRequested = true;
         } else {
            this.jumpRequested = false;
         }

         this.spearUses = 0;
      }
   }

   @Override
   public void onEnable() {
      this.jumpRequested = false;
      this.spearUses = 0;
   }

   @EventHandler
   private void onSpearTick(LocalPlayerPreTickEvent event) {
      if (this.modeSetting.getValue() != LongJumpModule.ModeChoice.SPEAR) {
         return;
      }

      int selectedSlot = InventoryUtils.getSelectedSlot();

      InventoryUtils.SwapSlot weapon = this.findLungeWeapon();
      if (!weapon.isFound()) {
         return;
      }
      if (ClientAccess.zioMinecraft.player.onGround()) {
         this.handleSpearLanding();
         return;
      }
      if (!this.jumpRequested) {
         return;
      }
      if (ClientAccess.zioMinecraft.player.isFallFlying()) {
         return;
      }

      if (PlayerStateUtils.isHungerLimited()) {
         return;
      }
      int usesBeforeLunge = this.spearUses;
      Integer useLimit = this.amountSetting.getValue();
      if (usesBeforeLunge >= useLimit) {
         return;
      }
      if (PlayerStateUtils.isInLiquid(ClientAccess.zioMinecraft.player)) {
         return;
      }

      float attackStrength = ClientAccess.zioMinecraft.player.getAttackStrengthScale(1.0F);

      if (attackStrength < 1.0F) {
         return;
      }
      this.performSpearLunge(weapon, selectedSlot);
      this.spearUses++;
      if (this.autoDisableSetting.getValue()) {
         int usesAfterLunge = this.spearUses;
         Integer completedUseLimit = this.amountSetting.getValue();
         if (usesAfterLunge >= completedUseLimit) {
            this.disable();
         }
      }
   }

   private void performSpearLunge(InventoryUtils.SwapSlot weapon, int selectedSlot) {
      ItemStack componentStack = weapon.getStack();
      PiercingWeapon piercingWeapon = ((DataComponentHolder)componentStack).get(DataComponents.PIERCING_WEAPON);
      if (weapon.hand() == InteractionHand.OFF_HAND) {
         int containerSlot = InventoryUtils.toMenuSlot(selectedSlot);

         InventoryUtils.clickSlot(containerSlot, 40, ContainerInput.SWAP);
      } else {
         InventoryUtils.swapTo(weapon);
      }

      ClientAccess.zioMinecraft.gameMode.piercingAttack(piercingWeapon);
      ClientAccess.zioMinecraft.player.swing(InteractionHand.MAIN_HAND);
      ClientAccess.zioMinecraft.player.resetOnlyAttackStrengthTicker();
      if (weapon.hand() == InteractionHand.OFF_HAND) {
         int convertedRestoreSlot = InventoryUtils.toMenuSlot(selectedSlot);

         InventoryUtils.clickSlot(convertedRestoreSlot, 40, ContainerInput.SWAP);
      } else {
         InventoryUtils.swapBack(selectedSlot, weapon);
      }
   }

   @EventHandler(priority = 100)
   public void onJumpInput(KeyboardInputTickEvent event) {
      if (event.getInput().keyPresses.jump()) {
         this.jumpRequested = true;
      }
   }

   @EventHandler(priority = 100)
   private void measureHorizontalDistance(LocalPlayerSendPositionEvent event) {
      if (event.getStage() != EventStage.Post) {
         if (this.modeSetting.getValue() == LongJumpModule.ModeChoice.PLAIN) {
            if (!MovementPhysics.isLocalPlayerMoving()) {
               this.movementStage = 4;
            }

            double deltaX = ClientAccess.zioMinecraft.player.getX()
               - ClientAccess.zioMinecraft.player.xo;
            double deltaZ = ClientAccess.zioMinecraft.player.getZ()
               - ClientAccess.zioMinecraft.player.zo;
            double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
            this.previousHorizontalDistance = Math.sqrt(distanceSquared);
         }
      }
   }

   @EventHandler
   private void applyPlainMovement(LocalPlayerMoveEvent event) {
      if (this.modeSetting.getValue() != ModeChoice.PLAIN || this.collidesWithSuffocatingColumn()
         || this.isLiquidBlocked() || ClientAccess.zioMinecraft.player.isFallFlying()
         || ClientAccess.zioMinecraft.player.isSpectator()) {
         return;
      }
      double motionY = event.getY();
      if (this.useTimerSetting.getValue()) {
         if (ClientContext.serverStateManager.getSetbackTimer().hasElapsed(250L)) {
            ClientContext.tickRateController.requestMultiplier(this, 1.0888F);
         } else {
            ClientContext.tickRateController.releaseMultiplier(this);
         }
      }
      if (!ClientContext.serverStateManager.getSetbackTimer().hasElapsed(250L) && this.autoDisableSetting.getValue()) {
         this.toggle();
      }
      double jumpSpeedScale = 2.0F * this.jumpSpeedSetting.getValue();
      double baseSpeed = 0.2873D * jumpSpeedScale;
      if (this.movementStage == 1 && MovementPhysics.isLocalPlayerMoving()) {
         this.moveSpeed = 1.35D * MovementPhysics.applySpeedEffects(false, baseSpeed) - 0.01D;
      } else if (this.movementStage == 2 && MovementPhysics.isLocalPlayerMoving()) {
         motionY = 0.42D + MovementPhysics.getJumpBoost();
         this.moveSpeed *= this.alternateBoost ? 1.6835D : 1.395D;
      } else if (this.movementStage == 3) {
         this.moveSpeed = this.previousHorizontalDistance
            - 0.6600000262260437D * (this.previousHorizontalDistance - MovementPhysics.applySpeedEffects(true, baseSpeed));
         this.alternateBoost = !this.alternateBoost;
      } else {
         LocalPlayer player = ClientAccess.zioMinecraft.player;
         if (player.verticalCollision
            || !ClientAccess.zioMinecraft.level.noCollision(player.getBoundingBox().move(0.0, player.getDeltaMovement().y, 0.0))) {
            if (this.movementStage > 0) {
               this.movementStage = MovementPhysics.isLocalPlayerMoving() ? 1 : 0;
            }
         }
         this.moveSpeed = this.previousHorizontalDistance - this.previousHorizontalDistance / 159.0D;
      }
      this.moveSpeed = Math.max(this.moveSpeed, MovementPhysics.applySpeedEffects(false, baseSpeed));
      double motionX = 0.0;
      double motionZ = 0.0;
      if (MovementPhysics.isLocalPlayerMoving()) {
         LocalPlayer player = ClientAccess.zioMinecraft.player;
         float yaw = player.getViewYRot(RenderUtils.getPartialTick());
         double[] horizontal = MovementPhysics.getHorizontalMotion(yaw, player.input, this.moveSpeed);
         motionX = horizontal[0];
         motionZ = horizontal[1];
      }
      Vec3Utils.setComponents(event.getVelocity(), motionX, motionY, motionZ);
      if (MovementPhysics.isLocalPlayerMoving()) {
         this.movementStage++;
      }
   }

   public boolean collidesWithSuffocatingColumn() {
      AABB playerBounds = ClientAccess.zioMinecraft.player.getBoundingBox();
      AABB columnBox = new AABB(
         ClientAccess.zioMinecraft.player.blockPosition().getX(),
         playerBounds.minY,
         ClientAccess.zioMinecraft.player.blockPosition().getZ(),
         ClientAccess.zioMinecraft.player.blockPosition().getX() + 1.0D,
         playerBounds.maxY,
         ClientAccess.zioMinecraft.player.blockPosition().getZ() + 1.0D
      );

      double inset = 1e-07D;
      AABB collisionBox = columnBox.deflate(inset);
      return ClientAccess.zioMinecraft.level.collidesWithSuffocatingBlock(ClientAccess.zioMinecraft.player, collisionBox);
   }


   public boolean isLiquidBlocked() {
      if (PlayerStateUtils.isInLiquid(ClientAccess.zioMinecraft.player)) {
         if (!this.inLiquidSetting.getValue()) {
            return true;
         }
      }

      return false;
   }

   public LongJumpModule() {
      super("LongJump", "Makes you jump fast 'n far.", ModuleCategory.MOVEMENT, new String[0]);
      this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.PLAIN));
      Predicate<Object> jumpSpeedVisible = ignored -> this.modeSetting.getValue() == ModeChoice.PLAIN;
      this.jumpSpeedSetting = this.add(new NumberSetting<>("JumpSpeed", 1.0F, 0.0F, 5.0F, jumpSpeedVisible));
      Predicate<Object> inLiquidVisible = ignored -> this.modeSetting.getValue() == ModeChoice.PLAIN;
      this.inLiquidSetting = this.add(new BooleanSetting("InLiquid", false, inLiquidVisible));
      Predicate<Object> amountVisible = ignored -> this.modeSetting.getValue() == ModeChoice.SPEAR;
      this.amountSetting = this.add(new NumberSetting<>("Amount", 2, 1, 4, amountVisible));
      Predicate<Object> autoJumpVisible = ignored -> this.modeSetting.getValue() == ModeChoice.SPEAR;
      this.autoJumpSetting = this.add(new BooleanSetting("AutoJump", true, autoJumpVisible));
      this.useTimerSetting = this.add(new BooleanSetting("UseTimer", true));
      this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", true));

      this.movementStage = 4;
   }

   public enum ModeChoice implements NamedEntry {
      PLAIN("Plain"),
      SPEAR("Spear");

      private final String displayName;
      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
