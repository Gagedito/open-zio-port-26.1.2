/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.render;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.utils.math.MathConstants;
import dev.zio.client.utils.inventory.EnchantmentUtils;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.modules.combat.AutoBowReleaseModule;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
public class ProjectileTrajectorySimulator implements ClientAccess {
   private static TrajectoriesModule module = ClientContext.getModule(TrajectoriesModule.class);
   private static AutoBowReleaseModule autoBowModule = ClientContext.getModule(AutoBowReleaseModule.class);
   private static final HitResult SIMULATION_LIMIT = new HitResult(
      Vec3.ZERO
   ) {
      @Override
      public Type getType() {
         return Type.MISS;
      }
   };
   private final MutableBlockPos fluidPosition = new MutableBlockPos();
   private Vec3 segmentEnd = Vec3.ZERO;
   private Vec3 segmentStart = Vec3.ZERO;
   private Entity sourceEntity;
   private Entity ignoredCollisionOwner;
   public final Vector3d position = new Vector3d();
   private final Vector3d velocity = new Vector3d();
   private double gravity;
   private double airDrag;
   private double waterDrag;
   public boolean isEnabledProjectileItem(Item item) {
      if (item instanceof BowItem) return module.bowSetting.getValue();
      if (item instanceof CrossbowItem) return module.crossbowSetting.getValue();
      if (item instanceof TridentItem) return module.tridentSetting.getValue();
      if (item instanceof EnderpearlItem) return module.pearlsSetting.getValue();
      if (item instanceof ExperienceBottleItem) return module.expSetting.getValue();
      return (item instanceof FishingRodItem || item instanceof ThrowablePotionItem || item instanceof SnowballItem || item instanceof EggItem)
         && module.othersSetting.getValue();
   }


   public boolean initializeFromProjectile(Entity entity, double partialTick) {
      if (!(entity instanceof Projectile projectile)) return false;
      Vec3 movement = entity.getDeltaMovement();
      if (movement.lengthSqr() < 1.0e-10D || !finite(movement)) return false;
      ClientLevel level = ClientAccess.zioMinecraft.level;
      if (level == null || !level.noCollision(entity, entity.getBoundingBox().deflate(1.0e-4D))) return false;
      Vec3 start = entity.getPosition((float)partialTick);
      this.position.set(start.x, start.y, start.z);
      this.velocity.set(movement.x, movement.y, movement.z);
      this.sourceEntity = entity;
      this.ignoredCollisionOwner = projectile.getOwner();
      this.airDrag = 0.99D;
      if (entity instanceof ThrownTrident) {
         this.gravity = 0.05D;
         this.waterDrag = 0.99D;
      } else if (entity instanceof AbstractArrow) {
         this.gravity = 0.05D;
         this.waterDrag = 0.6D;
      } else if (entity instanceof ThrownExperienceBottle) {
         this.gravity = 0.07D;
         this.waterDrag = 0.8D;
      } else if (entity instanceof ThrownEnderpearl || entity instanceof Snowball || entity instanceof ThrownEgg) {
         this.gravity = 0.03D;
         this.waterDrag = 0.8D;
      } else if (entity instanceof FireworkRocketEntity) {
         this.gravity = 0.0D;
         this.airDrag = 1.0D;
         this.waterDrag = 1.0D;
      } else if (entity instanceof FishingHook) {
         this.gravity = 0.03D;
         this.airDrag = 0.92D;
         this.waterDrag = 0.0D;
      } else if (entity instanceof ThrowableProjectile) {
         this.gravity = 0.05D;
         this.waterDrag = 0.8D;
      } else if (entity instanceof AbstractHurtingProjectile) {
         this.gravity = 0.0D;
         this.airDrag = 0.95D;
         this.waterDrag = 0.8D;
      } else return false;
      if (entity.isNoGravity()) this.gravity = 0.0D;
      return true;
   }

   private static boolean finite(Vec3 vector) {
      return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
   }

   public void initializeLaunch(
      Entity entity, double pitchOffset, double speed, double spreadAngle, double gravityPerTick, double dragInWater, boolean addEntityVelocity, double partialTick
   ) {
      this.sourceEntity = entity;
      this.ignoredCollisionOwner = entity;
      this.position.set(Mth.lerp(partialTick, entity.xo, entity.getX()),
         Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getEyeHeight(entity.getPose()),
         Mth.lerp(partialTick, entity.zo, entity.getZ()));
      double yaw = entity.getViewYRot((float)partialTick);
      double pitch = entity.getViewXRot((float)partialTick);
      double directionX;
      double directionY;
      double directionZ;
      if (spreadAngle == 0.0) {
         double yawRadians = yaw * MathConstants.RADIANS_PER_DEGREE;
         double pitchRadians = pitch * MathConstants.RADIANS_PER_DEGREE;
         directionX = -Math.sin(yawRadians) * Math.cos(pitchRadians);
         directionY = -Math.sin((pitch + pitchOffset) * MathConstants.RADIANS_PER_DEGREE);
         directionZ = Math.cos(yawRadians) * Math.cos(pitchRadians);
      } else {
         Vec3 upVector = entity.getUpVector(1.0F);
         Quaterniond spreadRotation = new Quaterniond().setAngleAxis(spreadAngle, upVector.x, upVector.y, upVector.z);
         Vec3 viewVector = entity.getViewVector(1.0F);
         Vector3d rotatedDirection = new Vector3d(viewVector.x, viewVector.y, viewVector.z);
         rotatedDirection.rotate(spreadRotation);
         directionX = rotatedDirection.x;
         directionY = rotatedDirection.y;
         directionZ = rotatedDirection.z;
      }
      this.velocity.set(directionX, directionY, directionZ).normalize().mul(speed);
      if (addEntityVelocity) {
         Vec3 movement = entity.getDeltaMovement();
         if (entity == ClientAccess.zioMinecraft.player) {
            Vec3 previous = module.getPreviousPlayerVelocity();
            movement = new Vec3(Mth.lerp(partialTick, previous.x, movement.x),
               Mth.lerp(partialTick, previous.y, movement.y), Mth.lerp(partialTick, previous.z, movement.z));
         }
         this.velocity.add(movement.x, entity.onGround() ? 0.0D : movement.y, movement.z);
      }
      this.gravity = gravityPerTick;
      this.airDrag = 0.99D;
      this.waterDrag = dragInWater;
   }
   public boolean initializeFromItem(
      Entity entity, ItemStack stack, double spreadAngle, boolean addEntityVelocity, double partialTick
   ) {
      Item item = stack.getItem();
      if (item instanceof TridentItem) {
         ResourceKey riptideKey = Enchantments.RIPTIDE;
         if (EnchantmentUtils.hasEnchantment(riptideKey, stack)) {
            return false;
         }
      }
      if (item instanceof BowItem) {
         Boolean configuredBow = module.bowSetting.getValue();
         if (configuredBow) {
            if (!(entity instanceof LivingEntity shooter)) return false;
            double currentPower = BowItem.getPowerForTime(shooter.getTicksUsingItem());
            double previousPower = entity == ClientAccess.zioMinecraft.player ? module.getPreviousBowPower() : currentPower;
            double bowPower = Mth.lerp(partialTick, previousPower, currentPower);
            if (bowPower <= 0.0D) {
               bowPower = autoBowModule.isToggled() ? BowItem.getPowerForTime(autoBowModule.getReleaseTicks()) : 1.0D;
            }
            double bowSpeed = bowPower * 3.0D;
            this.initializeLaunch(
               entity, 0.0, bowSpeed, spreadAngle, 0.05000000074505806D, 0.6D, addEntityVelocity, partialTick
            );
            return true;
         }
      }
      if (item instanceof CrossbowItem) {
         Boolean configuredCrossbow = module.crossbowSetting.getValue();
         if (configuredCrossbow) {
            if (!CrossbowItem.isCharged(stack)) {
               return false;
            }
            ChargedProjectiles chargedProjectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
            if (chargedProjectiles == null) {
               return false;
            }
            Item fireworkItem = Items.FIREWORK_ROCKET;
            if (chargedProjectiles.contains(fireworkItem)) {
               double fireworkSpeed = CrossbowItem.getShootingPower(chargedProjectiles);
               this.initializeLaunch(
                  entity, 0.0, fireworkSpeed, spreadAngle, 0.0, 0.6D, addEntityVelocity, partialTick
               );
            } else {
               double crossbowSpeed = CrossbowItem.getShootingPower(chargedProjectiles);
               this.initializeLaunch(
                  entity, 0.0, crossbowSpeed, spreadAngle, 0.05000000074505806D, 0.6D, addEntityVelocity, partialTick
               );
            }
            return true;
         }
      }
      if (item instanceof FishingRodItem) {
         Boolean configuredOthers = module.othersSetting.getValue();
         if (configuredOthers) {
            this.initializeFishingRod(entity, partialTick);
            return true;
         }
      }
      if (item instanceof TridentItem) {
         Boolean configuredTrident = module.tridentSetting.getValue();
         if (configuredTrident) {
            this.initializeLaunch(
               entity, 0.0, 2.5D, spreadAngle, 0.05000000074505806D, 0.99D, addEntityVelocity, partialTick
            );
            return true;
         }
      }
      if (((item instanceof SnowballItem || item instanceof EggItem) && module.othersSetting.getValue())
         || (item instanceof EnderpearlItem && module.pearlsSetting.getValue())) {
         this.initializeLaunch(
            entity, 0.0, 1.5D, spreadAngle, 0.03D, 0.8D, addEntityVelocity, partialTick
         );
         return true;
      }
      if (item instanceof ExperienceBottleItem) {
         Boolean configuredExp = module.expSetting.getValue();
         if (configuredExp) {
            this.initializeLaunch(
               entity, -20.0D, 0.7D, spreadAngle, 0.07D, 0.8D, addEntityVelocity, partialTick
            );
            return true;
         }
      }
      if (!(item instanceof ThrowablePotionItem)) {
         return false;
      }
      Boolean configuredOthers3 = module.othersSetting.getValue();
      if (!configuredOthers3) {
         return false;
      }
      double potionSpeed = MathConstants.HALF;
      this.initializeLaunch(
         entity, -20.0D, potionSpeed, spreadAngle, 0.05D, 0.8D, addEntityVelocity, partialTick
      );
      return true;
   }

   public void initializeFishingRod(Entity entity, double partialTick) {
      this.sourceEntity = entity;
      this.ignoredCollisionOwner = entity;
      double yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
      double pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
      double yawRadians = -yaw * MathConstants.RADIANS_PER_DEGREE - (float)MathConstants.PI;
      double yawCosine = Math.cos(yawRadians);
      double yawSine = Math.sin(yawRadians);
      double pitchRadians = -pitch * MathConstants.RADIANS_PER_DEGREE;
      double pitchCosine = -Math.cos(pitchRadians);
      double pitchSine = Math.sin(pitchRadians);
      this.position.set(Mth.lerp(partialTick, entity.xo, entity.getX()),
         Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getEyeHeight(entity.getPose()),
         Mth.lerp(partialTick, entity.zo, entity.getZ()));
      this.position.sub(yawSine * 0.3D, 0.0D, yawCosine * 0.3D);
      this.velocity.set(-yawSine, Mth.clamp(-(pitchSine / pitchCosine), -5.0D, 5.0D), -yawCosine);
      double velocityScale = 0.6D / this.velocity.length() + MathConstants.ONE_HALF;
      this.velocity.mul(velocityScale);
      this.gravity = 0.03D;
      this.airDrag = 0.92D;
      this.waterDrag = 0.0D;
   }


   public HitResult step() {
      this.segmentStart = new Vec3(this.position.x, this.position.y, this.position.z);
      this.position.add(this.velocity);
      if (!Double.isFinite(this.position.x) || !Double.isFinite(this.position.y) || !Double.isFinite(this.position.z)) {
         return SIMULATION_LIMIT;
      }
      ClientLevel level = ClientAccess.zioMinecraft.level;
      if (level == null || this.position.y < level.getMinY()) return SIMULATION_LIMIT;
      int chunkX = Mth.floor(this.position.x) >> 4;
      int chunkZ = Mth.floor(this.position.z) >> 4;
      if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) return SIMULATION_LIMIT;
      this.segmentEnd = new Vec3(this.position.x, this.position.y, this.position.z);
      HitResult hit = this.findCollision();
      this.velocity.mul(this.isInWater() ? this.waterDrag : this.airDrag);
      this.velocity.y -= this.gravity;
      return hit.getType() == Type.MISS ? null : hit;
   }

   private HitResult findCollision() {
      ClientLevel level = ClientAccess.zioMinecraft.level;
      BlockHitResult blockHit = level.clip(new ClipContext(this.segmentStart, this.segmentEnd, Block.COLLIDER,
         this.waterDrag == 0.0D ? Fluid.ANY : Fluid.NONE, this.sourceEntity));
      Vec3 end = blockHit.getType() == Type.MISS ? this.segmentEnd : blockHit.getLocation();
      AABB searchBounds = new AABB(this.segmentStart, end).inflate(1.0D);
      Predicate<Entity> targets = entity -> entity != this.sourceEntity && entity != this.ignoredCollisionOwner
         && !entity.isSpectator() && entity.isAlive() && entity.isPickable();
      EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, this.sourceEntity,
         this.segmentStart, end, searchBounds, targets, 0.3F);
      return entityHit == null ? blockHit : entityHit;
   }

   private boolean isInWater() {
      this.fluidPosition.set(this.position.x, this.position.y, this.position.z);
      ClientLevel level = ClientAccess.zioMinecraft.level;
      FluidState fluid = level.getFluidState(this.fluidPosition);
      if (!fluid.is(FluidTags.WATER)) return false;
      double fractionalY = this.position.y - Math.floor(this.position.y);
      return fractionalY <= fluid.getHeight(level, this.fluidPosition);
   }

   public static void bindModules(ModuleRegistry registry) {
      module = (TrajectoriesModule)registry.getModule(
         TrajectoriesModule.class
      );
      autoBowModule = (AutoBowReleaseModule)registry.getModule(
         AutoBowReleaseModule.class
      );
   }
}
