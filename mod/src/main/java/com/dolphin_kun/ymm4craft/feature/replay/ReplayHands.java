package com.dolphin_kun.ymm4craft.feature.replay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState.HandRenderSelection;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
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
        if (replacesInstantly(mainHandItem, nextMain)) mainHandItem = nextMain;
        if (replacesInstantly(offHandItem, nextOff)) offHandItem = nextOff;
        float swap = player.getItemSwapScale(1f);
        float mainTarget = mainHandItem != nextMain ? 0f : swap * swap * swap;
        float offTarget = offHandItem != nextOff ? 0f : 1f;
        mainHandHeight += Mth.clamp(mainTarget - mainHandHeight, -0.4f, 0.4f);
        offHandHeight += Mth.clamp(offTarget - offHandHeight, -0.4f, 0.4f);
        if (mainHandHeight < 0.1f) mainHandItem = nextMain;
        if (offHandHeight < 0.1f) offHandItem = nextOff;
    }

    public void extract(Player player, float partialTicks, PlayerRenderState playerState) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.getEntityRenderDispatcher().extractEntity(player, partialTicks) instanceof AvatarRenderState avatar)) return;
        playerState.hasPlayer = true;
        playerState.avatarRenderState = avatar;
        FirstPersonHandsAndItemsRenderState state = playerState.firstPersonHandsAndItems;
        LivingEntity.SwingDescription swing = player.getCurrentSwing();
        state.attackHand = swing == null ? InteractionHand.MAIN_HAND : swing.hand();
        state.viewXRot = player.getViewXRot(partialTicks);
        state.viewYRot = Mth.rotLerp(partialTicks, player.yHeadRotO, player.yHeadRot);
        state.xBob = Mth.lerp(partialTicks, xBobO, xBob);
        state.yBob = Mth.lerp(partialTicks, yBobO, yBob);
        state.isScoping = player.isScoping();
        state.useItemRemainingTicks = player.getUseItemRemainingTicks();
        state.handRenderSelection = selection(player);
        state.mainHandItem = mainHandItem;
        state.offHandItem = offHandItem;
        state.mainHandHeight = mainHandHeight;
        state.oldMainHandHeight = oMainHandHeight;
        state.offHandHeight = offHandHeight;
        state.oldOffHandHeight = oOffHandHeight;
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        ItemDisplayContext mainContext = right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        ItemDisplayContext offContext = right ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        state.mainHandRenderState.clear();
        state.offHandRenderState.clear();
        var resolver = mc.getItemModelResolver();
        resolver.updateForTopItem(state.mainHandRenderState, mainHandItem, mainContext, player.level(), player, player.getId() + mainContext.ordinal());
        resolver.updateForTopItem(state.offHandRenderState, offHandItem, offContext, player.level(), player, player.getId() + offContext.ordinal());
        state.mainHandUseDuration = mainHandItem.getUseDuration(player);
        state.offHandUseDuration = offHandItem.getUseDuration(player);
        state.mainHandChargeDuration = CrossbowItem.getChargeDuration(mainHandItem, player);
        state.offHandChargeDuration = CrossbowItem.getChargeDuration(offHandItem, player);
        state.mainHandSwapScale = resolver.swapAnimationScale(mainHandItem);
        state.offHandSwapScale = resolver.swapAnimationScale(offHandItem);
        state.hasMainHandMapData = false;
        state.hasOffHandMapData = false;
    }

    private static boolean replacesInstantly(ItemStack visible, ItemStack expected) {
        return ItemStack.matchesIgnoringComponents(visible, expected, DataComponentType::ignoreSwapAnimation)
                || !Minecraft.getInstance().getItemModelResolver().shouldPlaySwapAnimation(expected);
    }

    private static HandRenderSelection selection(Player player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        boolean bow = main.is(Items.BOW) || off.is(Items.BOW);
        boolean crossbow = main.is(Items.CROSSBOW) || off.is(Items.CROSSBOW);
        if (!bow && !crossbow) return HandRenderSelection.RENDER_BOTH_HANDS;
        if (!player.isUsingItem()) {
            return charged(main) ? HandRenderSelection.RENDER_MAIN_HAND_ONLY : HandRenderSelection.RENDER_BOTH_HANDS;
        }
        ItemStack used = player.getUseItem();
        InteractionHand hand = player.getUsedItemHand();
        if (!used.is(Items.BOW) && !used.is(Items.CROSSBOW)) {
            return hand == InteractionHand.MAIN_HAND && charged(off) ? HandRenderSelection.RENDER_MAIN_HAND_ONLY : HandRenderSelection.RENDER_BOTH_HANDS;
        }
        return HandRenderSelection.onlyForHand(hand);
    }

    private static boolean charged(ItemStack item) {
        return item.is(Items.CROSSBOW) && CrossbowItem.isCharged(item);
    }
}
