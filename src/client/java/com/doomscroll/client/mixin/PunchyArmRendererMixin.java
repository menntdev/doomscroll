package com.doomscroll.client.mixin;

import com.doomscroll.Doomscroll;
import com.doomscroll.client.TabletHandRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Held tablet when Punchy is installed.
 *
 * <p>Punchy replaces first person wholesale: it cancels the game's own submitHandsWithItems and draws the arm and
 * the item from its own renderer, which never calls the per-hand method {@link ItemInHandRendererMixin} hooks. The
 * tablet then came out as an ordinary held item, drawn wherever Punchy puts items, with only the live page on it
 * from the item renderer.
 *
 * <p>So the tablet is drawn here instead: Punchy's own item draw is cancelled for it, and the grip is applied at
 * the head of Punchy's renderer, where the pose is still the one the game handed over. The two view-bob rotations
 * the game applies before the per-hand render are repeated here, so the tablet sits exactly where it does without
 * Punchy. The arm stays Punchy's, animations and all.
 *
 * <p>{@link Pseudo} and the optional config make this a no-op when Punchy is absent. It is tied to Punchy's
 * internals, so it needs a look whenever Punchy updates; if the method names ever change, the injection simply
 * does not apply and the tablet falls back to Punchy's item rendering.
 */
@Pseudo
@Mixin(targets = "punchy.client.render.PunchyArmRenderer", remap = false)
public abstract class PunchyArmRendererMixin {
	@Inject(method = "renderFirstPerson", at = @At("HEAD"), remap = false)
	private static void doomscroll$tablet(FirstPersonHandsAndItemsRenderer renderer, LocalPlayer player,
										  float partialTick, PoseStack pose, SubmitNodeCollector collector, int light,
										  FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		if (player == null || state == null || state.isScoping || state.handRenderSelection == null) {
			return;
		}
		boolean mainHand = isTablet(state.mainHandItem) && state.handRenderSelection.renderMainHand;
		boolean offHand = isTablet(state.offHandItem) && state.handRenderSelection.renderOffHand;
		if (!mainHand && !offHand) {
			return;
		}

		pose.pushPose();
		// The two rotations the game applies in submitHandsWithItems before handing the pose to the per-hand render.
		pose.rotateDegrees(Axis.XP, (state.viewXRot - state.xBob) * 0.1f);
		pose.rotateDegrees(Axis.YP, (state.viewYRot - state.yBob) * 0.1f);
		if (mainHand) {
			draw(player, partialTick, pose, collector, light, state, InteractionHand.MAIN_HAND);
		}
		if (offHand) {
			draw(player, partialTick, pose, collector, light, state, InteractionHand.OFF_HAND);
		}
		pose.popPose();
	}

	/** One hand, with the swing and equip progress worked out exactly as the game does for the per-hand render. */
	private static void draw(LocalPlayer player, float partialTick, PoseStack pose, SubmitNodeCollector collector,
							 int light, FirstPersonHandsAndItemsRenderState state, InteractionHand hand) {
		boolean main = hand == InteractionHand.MAIN_HAND;
		HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
		float swingProgress = state.attackHand == hand ? player.getSwingAnimation(partialTick) : 0.0f;
		float equipProgress = main
				? state.mainHandSwapScale * (1.0f - Mth.lerp(partialTick, state.oldMainHandHeight, state.mainHandHeight))
				: state.offHandSwapScale * (1.0f - Mth.lerp(partialTick, state.oldOffHandHeight, state.offHandHeight));
		TabletHandRenderer.draw(pose, collector, light, player, arm, swingProgress, equipProgress, partialTick, null);
	}

	/** Punchy's own item draw, skipped for the hand holding the tablet: it is drawn above, with its own grip. */
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true, remap = false)
	private static void doomscroll$skipTablet(FirstPersonHandsAndItemsRenderer renderer, PlayerModel model,
											  AvatarRenderState avatarState, LocalPlayer player, HumanoidArm arm,
											  PoseStack pose, SubmitNodeCollector collector, int light,
											  Matrix4f armMatrix, ModelPart part1, ModelPart part2, ModelPart part3,
											  float partialTick, FirstPersonHandsAndItemsRenderState state,
											  CallbackInfo ci) {
		if (player == null) {
			return;
		}
		ItemStack stack = arm == player.getMainArm() ? player.getMainHandItem() : player.getOffhandItem();
		if (isTablet(stack)) {
			ci.cancel();
		}
	}

	private static boolean isTablet(ItemStack stack) {
		return stack != null && stack.getItem() == Doomscroll.TABLET_ITEM;
	}
}
