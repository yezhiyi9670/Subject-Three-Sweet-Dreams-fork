package com.hexagram2021.subject3.common.entities;

import com.hexagram2021.subject3.common.STSavedData;
import com.hexagram2021.subject3.register.STEntities;
import com.hexagram2021.subject3.register.STItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nonnull;

public class BedBoatEntity extends Boat implements IBedVehicle {
	private static final EntityDataAccessor<Integer> DATA_ID_DYE_COLOR = SynchedEntityData.defineId(BedBoatEntity.class, EntityDataSerializers.INT);

	public BedBoatEntity(EntityType<? extends Boat> entityType, Level level) {
		super(entityType, level);
		this.blocksBuilding = true;
	}

	public BedBoatEntity(Level level, double x, double y, double z) {
		this(STEntities.BED_BOAT.get(), level);
		this.setPos(x, y, z);
		this.xo = x;
		this.yo = y;
		this.zo = z;
	}

	@Override
	public void setColor(DyeColor color) {
		this.entityData.set(DATA_ID_DYE_COLOR, color.ordinal());
	}

	@Override @Nonnull
	public DyeColor getBedColor() {
		return DyeColor.byId(this.entityData.get(DATA_ID_DYE_COLOR));
	}

	@Override
	public int passengersCount() {
		return this.getPassengers().size();
	}

	@Override
	public float getBedVehicleRotY() {
		return this.getYRot();
	}
	@Override
	public double getBedVehicleOffsetY() {
		return 0.875D;
	}

	@Override
	public void setPos(double x, double y, double z) {
		super.setPos(x, y, z);

		if(this.level instanceof ServerLevel serverlevel) {
			if (serverlevel.dimension().equals(ServerLevel.OVERWORLD)) {
				ChunkPos newPos = new ChunkPos(this.blockPosition());
				ChunkPos oldPos = STSavedData.addBedVehicle(this.uuid, newPos);
				if (!newPos.equals(oldPos)) {
					STSavedData.updateForceChunk(newPos, serverlevel, true);
					if (oldPos != null) {
						STSavedData.updateForceChunk(oldPos, serverlevel, false);
					}
				}
			}
		}
	}

	@Override
	protected void defineSynchedData() {
		super.defineSynchedData();
		this.entityData.define(DATA_ID_DYE_COLOR, DyeColor.WHITE.ordinal());
	}

	@Override
	protected int getMaxPassengers() {
		return 1;
	}

	@Override
	protected void addAdditionalSaveData(@Nonnull CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putString("DyeColor", this.getBedColor().getName());
	}

	@Override
	protected void readAdditionalSaveData(@Nonnull CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		if (nbt.contains("DyeColor", Tag.TAG_STRING)) {
			this.setColor(DyeColor.byName(nbt.getString("DyeColor"), DyeColor.WHITE));
		}
	}

	@Override @Nonnull
	public InteractionResult interact(@Nonnull Player player, @Nonnull InteractionHand hand) {
		if (!BedBlock.canSetSpawn(this.level)) {
			if (this.level.isClientSide) {
				return InteractionResult.SUCCESS;
			}
			this.level.explode(
					this, DamageSource.badRespawnPointExplosion(), null,
					this.getX() + 0.5D, this.getY() + 0.125D, this.getZ() + 0.5D,
					5.0F, true, Explosion.BlockInteraction.DESTROY
			);
			this.kill();
			return InteractionResult.CONSUME;
		}
		InteractionResult ret = super.interact(player, hand);
		if(ret == InteractionResult.CONSUME && player instanceof IHasVehicleRespawnPosition) {
			((IHasVehicleRespawnPosition)player).setBedVehicleUUID(this.uuid);
		}
		return ret;
	}

	@Override
	public void remove(@Nonnull Entity.RemovalReason reason) {
		if (!this.level.isClientSide && reason.shouldDestroy()) {
			ChunkPos chunkPos = STSavedData.removeBedVehicle(this.uuid);
			if(chunkPos != null && this.level instanceof ServerLevel) {
				STSavedData.updateForceChunk(chunkPos, (ServerLevel)this.level, false);
			}
		}

		super.remove(reason);
	}

	@Override @Nonnull
	public Item getDropItem() {
		return STItems.BedBoats.byTypeAndColor(this.getBoatType(), this.getBedColor());
	}

	@Override
	public boolean shouldRiderSit() {
		return false;
	}

	@Override @Nonnull
	public Packet<?> getAddEntityPacket() {
		return NetworkHooks.getEntitySpawningPacket(this);
	}
}
