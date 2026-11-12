package com.deadcraft.client.model;

import com.deadcraft.entity.VehicleEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Ruin Runner (wheels spin, chassis rolls on its suspension) and Sky Skiff (thrusters tilt). */
public class VehicleModel<T extends VehicleEntity> extends HierarchicalModel<T> {
    private final ModelPart root;
    private final boolean buggy;
    private ModelPart chassis, hull, thrusterL, thrusterR;
    private ModelPart[] wheels;
    private float partial;

    public VehicleModel(ModelPart root, boolean buggy) {
        this.root = root;
        this.buggy = buggy;
        if (buggy) {
            chassis = root.getChild("chassis");
            wheels = new ModelPart[]{root.getChild("wheel_fl"), root.getChild("wheel_fr"), root.getChild("wheel_bl"), root.getChild("wheel_br")};
        } else {
            hull = root.getChild("hull");
            thrusterL = root.getChild("thruster_l");
            thrusterR = root.getChild("thruster_r");
        }
    }

    @Override
    public ModelPart root() { return root; }

    @Override
    public void prepareMobModel(T entity, float limbSwing, float amount, float partialTick) {
        this.partial = partialTick;
    }

    @Override
    public void setupAnim(T e, float limbSwing, float amount, float age, float yaw, float pitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float spin = Mth.lerp(partial, e.spinO, e.spin);
        float throttle = e.getThrottle();
        if (buggy) {
            for (ModelPart w : wheels) w.xRot = spin;
            wheels[0].yRot = wheels[1].yRot = 0f;
            chassis.xRot = -throttle * 0.04f;
            chassis.y += Mth.sin(age * 1.7f) * 0.15f * (e.isVehicle() ? 1 : 0);
        } else {
            float bob = Mth.sin(age * 0.12f) * 0.6f;
            hull.y += bob;
            thrusterL.y += bob;
            thrusterR.y += bob;
            thrusterL.xRot = -throttle * 0.35f;
            thrusterR.xRot = -throttle * 0.35f;
            hull.xRot = -throttle * 0.06f;
        }
    }
}
