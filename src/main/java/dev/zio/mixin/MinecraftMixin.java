/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import dev.zio.client.modules.misc.AntiQuitModule;
import dev.zio.client.modules.combat.ArrowsModule;
import dev.zio.client.modules.combat.AutoClickerModule;
import dev.zio.client.modules.combat.AutoCrystalModule;
import dev.zio.client.modules.player.AutoEatModule;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.gui.screens.ConfirmGameCloseScreen;
import dev.zio.client.events.ScreenChangeEvent;
import dev.zio.client.events.MinecraftTickEvent;
import dev.zio.client.modules.client.DiscordModule;
import dev.zio.client.events.MinecraftDisconnectEvent;
import dev.zio.client.events.MinecraftRunTickEvent;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.exploit.MultiTaskModule;
import dev.zio.client.modules.combat.NoHitDelayModule;
import dev.zio.client.modules.render.NoRenderModule;
import dev.zio.client.modules.client.UIModule;
import dev.zio.client.utils.LockUtils;
import dev.zio.client.utils.math.Vec3Utils;
import dev.zio.client.modules.player.FreecamModule;
import dev.zio.client.modules.render.FreeLookModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
   private static UIModule uiModule = ClientContext.getModule(UIModule.class);
   private static MultiTaskModule multiTaskModule = ClientContext.getModule(MultiTaskModule.class);
   private static AutoEatModule autoEatModule = ClientContext.getModule(AutoEatModule.class);
   private static AntiQuitModule antiQuitModule = ClientContext.getModule(AntiQuitModule.class);
   private static NoHitDelayModule noHitDelayModule = ClientContext.getModule(NoHitDelayModule.class);
   private static AutoClickerModule autoClickerModule = ClientContext.getModule(AutoClickerModule.class);
   private static ArrowsModule arrowsModule = ClientContext.getModule(ArrowsModule.class);
   private static AutoCrystalModule autoCrystalModule = ClientContext.getModule(AutoCrystalModule.class);
   private static NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   private static DiscordModule discordModule = ClientContext.getModule(DiscordModule.class);
   private static FreecamModule freecam = ClientContext.getModule(FreecamModule.class);
   private static FreeLookModule freelook = ClientContext.getModule(FreeLookModule.class);
   @Unique
   private Screen previousScreen;

   @Unique
   private boolean pickingFromFreecam = false;

   /**
    * MC 26.1.2: GameRenderer.pick(F) se movio a Minecraft.pick(F) (privado).
    * Migrado desde GameRendererMixin para conservar el comportamiento de
    * Freecam (raycast desde la posicion de la camara fantasma) y FreeLook.
    */
   @Shadow
   private void pick(float partialTick) {
      throw new UnsupportedOperationException("Shadow stub (target private method)");
   }

   @Shadow
   public Entity getCameraEntity() {
      throw new UnsupportedOperationException("Shadow stub (target method)");
   }

   @Shadow
   public int missTime;
   @Shadow
   @Nullable
   public Screen screen;
   private static boolean shutdownSaveStarted = false;

   @Inject(method = "startAttack", at = @At("HEAD"))
   public void resetAttackMissTime(CallbackInfoReturnable<Boolean> callback) {
      if (noHitDelayModule.isToggled()
         || autoClickerModule.isToggled()) {
         this.missTime = 0;
      }
   }

   @Inject(method = "destroy", at = @At("HEAD"))
   public void saveConfigurationOnDestroy(CallbackInfo callback) {
      if (!shutdownSaveStarted
         && ClientContext.configurationManager != null) {
         shutdownSaveStarted = true;
         uiModule.disable();
         ClientContext.configurationManager
            .save();
      }
   }

   @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;close()V"))
   public void postDisconnectEvent(
      Screen screen, boolean ignoredDisconnectFlag1, boolean ignoredDisconnectFlag2, CallbackInfo callback
   ) {
      MinecraftDisconnectEvent event = new MinecraftDisconnectEvent();
      ClientAccess.eventBus
         .post(event);
   }

   @ModifyExpressionValue(method = "continueAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
   public boolean bypassUsingItemForContinueAttack(boolean originalUsingItem) {
      return !multiTaskModule.isToggled() && originalUsingItem;
   }

   @ModifyExpressionValue(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z"), require = 0)
   public boolean bypassDestroyingForStartUseItem(boolean originalDestroying) {
      return multiTaskModule.isToggled() ? false : originalDestroying;
   }

   @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
   private void postBeforeScreenChange(Screen newScreen, CallbackInfo callback) {
      this.previousScreen = this.screen;
      ScreenChangeEvent.Before event = new ScreenChangeEvent.Before(
         this.previousScreen, newScreen
      );
      ClientAccess.eventBus
         .post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @Inject(method = "setScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;init(II)V", shift = Shift.AFTER), cancellable = true)
   private void postAfterScreenInit(Screen newScreen, CallbackInfo callback) {
      ScreenChangeEvent.AfterInit event = new ScreenChangeEvent.AfterInit(
         this.previousScreen, newScreen
      );
      ClientAccess.eventBus
         .post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @Inject(method = "tick", at = @At("HEAD"))
   private void postMinecraftTick(CallbackInfo callback) {
      ClientAccess.eventBus
         .post(
            MinecraftTickEvent.INSTANCE
         );
   }

   @ModifyExpressionValue(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
   private boolean bypassUsingItemForAutoEat(boolean originalUsingItem) {
      return autoEatModule.isEating()
            && autoEatModule.isToggled()
         ? false
         : originalUsingItem;
   }

   @WrapWithCondition(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;stop()V"))
   private boolean confirmGameClose(Minecraft client) {
      if (antiQuitModule.isToggled()
         && antiQuitModule.gameCloseSetting.getValue()) {
         if (!(client.screen instanceof ConfirmGameCloseScreen)) {
            client.setScreen(new ConfirmGameCloseScreen());
         }

         return false;
      } else {
         return true;
      }
   }

   @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runAllTasks()V", shift = Shift.AFTER))
   private void postMinecraftRunTick(
      boolean renderLevel, CallbackInfo callback, @Local int ticksToRun
   ) {
      ClientAccess.eventBus
         .post(new MinecraftRunTickEvent(ticksToRun >= 1));
   }

   @ModifyExpressionValue(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
   private boolean bypassUsingItemForArrows(boolean originalUsingItem) {
      return arrowsModule.shouldBypassUsingItemCheck()
         ? false
         : originalUsingItem;
   }

   @Inject(method = "startUseItem", at = @At("HEAD"))
   private void completeCrystalCalculationBeforeUse(CallbackInfo callback) {
      autoCrystalModule.completeCalculation();
   }

   @ModifyExpressionValue(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isHandsBusy()Z"))
   private boolean bypassHandsBusyForStartUseItem(boolean originalHandsBusy) {
      return multiTaskModule.isToggled() ? false : originalHandsBusy;
   }

   @ModifyExpressionValue(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isHandsBusy()Z"))
   private boolean bypassHandsBusyForStartAttack(boolean originalHandsBusy) {
      return multiTaskModule.isToggled() ? false : originalHandsBusy;
   }

   @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
   private void suppressEntityGlow(
      Entity entity, CallbackInfoReturnable<Boolean> callback
   ) {
      if (noRenderModule.isToggled()
         && noRenderModule.glowSetting.getValue()) {
         callback.setReturnValue(false);
      }
   }

   @Inject(method = "close", at = @At("HEAD"))
   public void closeDiscordClient(CallbackInfo callback) {
      discordModule.closeClient();
   }

   @Unique
   private static void zio$bindMinecraftModules(ModuleRegistry moduleRegistry) {
      uiModule = (UIModule)moduleRegistry.getModule(
         UIModule.class
      );
      multiTaskModule = (MultiTaskModule)moduleRegistry.getModule(
         MultiTaskModule.class
      );
      autoEatModule = (AutoEatModule)moduleRegistry.getModule(
         AutoEatModule.class
      );
      antiQuitModule = (AntiQuitModule)moduleRegistry.getModule(
         AntiQuitModule.class
      );
      noHitDelayModule = (NoHitDelayModule)moduleRegistry.getModule(
         NoHitDelayModule.class
      );
      autoClickerModule = (AutoClickerModule)moduleRegistry.getModule(
         AutoClickerModule.class
      );
      arrowsModule = (ArrowsModule)moduleRegistry.getModule(
         ArrowsModule.class
      );
      autoCrystalModule = (AutoCrystalModule)moduleRegistry.getModule(
         AutoCrystalModule.class
      );
      noRenderModule = (NoRenderModule)moduleRegistry.getModule(
         NoRenderModule.class
      );
      discordModule = (DiscordModule)moduleRegistry.getModule(
         DiscordModule.class
      );
      freecam = (FreecamModule)moduleRegistry.getModule(
         FreecamModule.class
      );
      freelook = (FreeLookModule)moduleRegistry.getModule(
         FreeLookModule.class
      );
   }

   /**
    * MC 26.1.2: migrado desde GameRendererMixin. El picking de crosshair vive
    * ahora en Minecraft.pick(F); Freecam reubica temporalmente la camara del
    * jugador para raycastear desde la posicion fantasma, y FreeLook cancela el
    * picking propio del jugador.
    */
   @Inject(method = "pick(F)V", at = @At("HEAD"), cancellable = true)
   private void applyCameraPicking(float partialTick, CallbackInfo callback) {
      if (freecam.isToggled()
         && this.getCameraEntity() != null
         && !this.pickingFromFreecam) {
         callback.cancel();
         Entity cameraEntity = this.getCameraEntity();
         double savedX = cameraEntity.getX();
         double savedY = cameraEntity.getY();
         double savedZ = cameraEntity.getZ();
         double savedPreviousX = cameraEntity.xo;
         double savedPreviousY = cameraEntity.yo;
         double savedPreviousZ = cameraEntity.zo;
         float savedYaw = cameraEntity.getYRot();
         float savedPitch = cameraEntity.getXRot();
         float savedPreviousYaw = cameraEntity.yRotO;
         float savedPreviousPitch = cameraEntity.xRotO;
         cameraEntity.xo = freecam.previousCameraPosition.x;
         cameraEntity.yo = freecam.previousCameraPosition.y
            - cameraEntity.getEyeHeight(cameraEntity.getPose());
         cameraEntity.zo = freecam.previousCameraPosition.z;
         cameraEntity.setYRot(freecam.cameraYaw);
         cameraEntity.setYHeadRot(freecam.cameraYaw);
         cameraEntity.setXRot(freecam.cameraPitch);
         cameraEntity.yRotO = freecam.previousCameraYaw;
         cameraEntity.xRotO = freecam.previousCameraPitch;
         this.pickingFromFreecam = true;
         LockUtils.runWithSharedLock(
            () -> {
               Vec3Utils.setComponents(
                  cameraEntity.position(),
                  freecam.cameraPosition.x,
                  freecam.cameraPosition.y
                     - cameraEntity.getEyeHeight(cameraEntity.getPose()),
                  freecam.cameraPosition.z
               );
               this.pick(partialTick);
               Vec3Utils.setComponents(
                  cameraEntity.position(), savedX, savedY, savedZ
               );
            });
         this.pickingFromFreecam = false;
         cameraEntity.xo = savedPreviousX;
         cameraEntity.yo = savedPreviousY;
         cameraEntity.zo = savedPreviousZ;
         cameraEntity.setYRot(savedYaw);
         cameraEntity.setXRot(savedPitch);
         cameraEntity.yRotO = savedPreviousYaw;
         cameraEntity.xRotO = savedPreviousPitch;
      }

      if (freelook.isToggled() && !freecam.isToggled()) {
         callback.cancel();
      }
   }
}
