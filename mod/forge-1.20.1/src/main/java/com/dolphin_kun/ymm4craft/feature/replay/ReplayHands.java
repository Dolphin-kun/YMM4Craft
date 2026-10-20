package com.dolphin_kun.ymm4craft.feature.replay;

import com.dolphin_kun.ymm4craft.mixin.ItemInHandRendererAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ReplayHands {
    private ItemStack mainHandItem = ItemStack.EMPTY;
    private ItemStack offHandItem = ItemStack.EMPTY;
    private float mainHandHeight;
    private float oMainHandHeight;
    private float offHandHeight;
    private float oOffHandHeight;
    private float xBob;
    private float xBobO;
    private float yBob;
    private float yBobO;
    private Player bobbing;

    void reset() {
        mainHandItem = ItemStack.EMPTY;
        offHandItem = ItemStack.EMPTY;
        mainHandHeight = oMainHandHeight = offHandHeight = oOffHandHeight = 0;
        bobbing = null;
    }

    void tick(Player player) {
        if (bobbing != player) {
            bobbing = player;
            xBob = xBobO = player.getXRot();
            yBob = yBobO = player.getYHeadRot();
        }
        xBobO = xBob;
        yBobO = yBob;
        xBob += (player.getXRot() - xBob) * 0.5f;
        yBob += (player.getYHeadRot() - yBob) * 0.5f;

        oMainHandHeight = mainHandHeight;
        oOffHandHeight = offHandHeight;
        ItemStack nextMain = player.getMainHandItem();
        ItemStack nextOff = player.getOffhandItem();
        if (ItemStack.matches(mainHandItem, nextMain)) mainHandItem = nextMain;
        if (ItemStack.matches(offHandItem, nextOff)) offHandItem = nextOff;
        float strength = player.getAttackStrengthScale(1f);
        float mainTarget = mainHandItem != nextMain ? 0f : strength * strength * strength;
        float offTarget = offHandItem != nextOff ? 0f : 1f;
        mainHandHeight += Mth.clamp(mainTarget - mainHandHeight, -0.4f, 0.4f);
        offHandHeight += Mth.clamp(offTarget - offHandHeight, -0.4f, 0.4f);
        if (mainHandHeight < 0.1f) mainHandItem = nextMain;
        if (offHandHeight < 0.1f) offHandItem = nextOff;
    }

    public void render(ItemInHandRenderer renderer, float partialTicks, PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                       AbstractClientPlayer player, int light) {
        float attack = player.getAttackAnim(partialTicks);
        InteractionHand swinging = player.swingingArm == null ? InteractionHand.MAIN_HAND : player.swingingArm;
        float pitch = Mth.lerp(partialTicks, player.xRotO, player.getXRot());
        float xb = Mth.lerp(partialTicks, xBobO, xBob);
        float yb = Mth.lerp(partialTicks, yBobO, yBob);
        poseStack.mulPose(Axis.XP.rotationDegrees((player.getViewXRot(partialTicks) - xb) * 0.1f));
        poseStack.mulPose(Axis.YP.rotationDegrees((Mth.rotLerp(partialTicks, player.yHeadRotO, player.yHeadRot) - yb) * 0.1f));
        int selection = selection(player);
        ItemInHandRendererAccessor accessor = (ItemInHandRendererAccessor) renderer;
        if (selection != OFF_ONLY) {
            float swing = swinging == InteractionHand.MAIN_HAND ? attack : 0f;
            float equip = 1f - Mth.lerp(partialTicks, oMainHandHeight, mainHandHeight);
            accessor.ymm4craft$renderArmWithItem(player, partialTicks, pitch, InteractionHand.MAIN_HAND, swing, mainHandItem, equip, poseStack, buffers, light);
        }
        if (selection != MAIN_ONLY) {
            float swing = swinging == InteractionHand.OFF_HAND ? attack : 0f;
            float equip = 1f - Mth.lerp(partialTicks, oOffHandHeight, offHandHeight);
            accessor.ymm4craft$renderArmWithItem(player, partialTicks, pitch, InteractionHand.OFF_HAND, swing, offHandItem, equip, poseStack, buffers, light);
        }
        buffers.endBatch();
    }

    private static final int BOTH = 0;
    private static final int MAIN_ONLY = 1;
    private static final int OFF_ONLY = 2;

    private static int selection(Player player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        boolean bow = main.is(Items.BOW) || off.is(Items.BOW);
        boolean crossbow = main.is(Items.CROSSBOW) || off.is(Items.CROSSBOW);
        if (!bow && !crossbow) return BOTH;
        if (!player.isUsingItem()) return charged(main) ? MAIN_ONLY : BOTH;
        ItemStack used = player.getUseItem();
        InteractionHand hand = player.getUsedItemHand();
        if (!used.is(Items.BOW) && !used.is(Items.CROSSBOW)) {
            return hand == InteractionHand.MAIN_HAND && charged(off) ? MAIN_ONLY : BOTH;
        }
        return hand == InteractionHand.MAIN_HAND ? MAIN_ONLY : OFF_ONLY;
    }

    private static boolean charged(ItemStack item) {
        return item.is(Items.CROSSBOW) && CrossbowItem.isCharged(item);
    }
}
