package dev.leo.sableplayerragdoll.neoforge.mixin;

import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import dev.leo.sableplayerragdoll.neoforge.client.RagdollPartBlockEntityRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerMixin {

    @SuppressWarnings("rawtypes")
    @Inject(method = "setPartVisibility", at = @At("RETURN"))
    private void onSetPartVisibility(HumanoidModel model, EquipmentSlot slot, CallbackInfo ci) {
        BodyPart bodyPart = RagdollPartBlockEntityRenderer.activeBodyPart();
        if (bodyPart == null) return;
        switch (bodyPart) {
            case TORSO -> {
                model.leftArm.visible  = false;
                model.rightArm.visible = false;
                model.leftLeg.visible  = false;
                model.rightLeg.visible = false;
            }
            case LEFT_ARM -> {
                model.body.visible     = false;
                model.rightArm.visible = false;
            }
            case RIGHT_ARM -> {
                model.body.visible    = false;
                model.leftArm.visible = false;
            }
            case LEFT_LEG -> {
                model.body.visible    = false;
                model.rightLeg.visible = false;
            }
            case RIGHT_LEG -> {
                model.body.visible    = false;
                model.leftLeg.visible = false;
            }
            default -> {}
        }
    }
}
