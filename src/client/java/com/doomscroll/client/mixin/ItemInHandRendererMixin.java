package com.doomscroll.client.mixin;

import com.doomscroll.Doomscroll;
import com.doomscroll.client.TabletHandRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Held tablet, vanilla path: the game's own per-hand render is replaced with {@link TabletHandRenderer}, which
 * keeps the one-handed WebDisplays grip. A mod that takes first person over entirely is handled in
 * {@link PunchyArmRendererMixin}.
 */
@Mixin(net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Shadow
	private void renderPlayerArm(PoseStack pose, SubmitNodeCollector collector, int light,
								 float equipProgress, float swingProgress, HumanoidArm arm, PlayerRenderState state) {
		throw new AssertionError("shadow");
	}

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void doomscroll$tablet(PlayerRenderState state, FirstPersonHandsAndItemsRenderState handsState,
								   float partialTick, float pitch, InteractionHand hand,
								   float swingProgress, ItemStack stack, float equipProgress,
								   PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (stack.getItem() != Doomscroll.TABLET_ITEM) {
			return;
		}
		// First person only ever draws the local player, and the render state carries no arm or invisibility.
		AbstractClientPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		ci.cancel();
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		TabletHandRenderer.draw(pose, collector, light, player, arm, swingProgress, equipProgress, partialTick,
				p -> renderPlayerArm(p, collector, light, equipProgress, swingProgress, arm, state));
	}
}
