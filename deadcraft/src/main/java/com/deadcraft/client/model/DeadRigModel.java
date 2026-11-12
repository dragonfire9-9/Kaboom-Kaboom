package com.deadcraft.client.model;

import com.deadcraft.DeadCraft;
import com.deadcraft.entity.Animated;
import com.deadcraft.entity.DeadMonster;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.entity.animation.json.AnimationHolder;
import net.neoforged.neoforge.client.entity.animation.json.AnimationLoader;
import org.jetbrains.annotations.Nullable;

/**
 * One model class for every DeadCraft creature. Geometry comes from {@link ModelGeometry}
 * (generated from tools/rigs.py, mirrored in /blockbench), and all motion comes from keyframe
 * animations in assets/deadcraft/neoforge/animations/entity/&lt;creature&gt;/: walk, idle, attack,
 * cast and fly. The same animations are in each .bbmodel for editing in Blockbench.
 */
public class DeadRigModel<T extends LivingEntity> extends HierarchicalModel<T> {
    private final ModelPart root;
    @Nullable private final ModelPart head;
    private final AnimationHolder walk, idle, attack, cast, fly;
    private final float walkSpeed;

    public DeadRigModel(ModelPart root, String id, float walkSpeed) {
        this.root = root;
        this.head = getAnyDescendantWithName("head").orElse(null);
        this.walk = holder(id, "walk");
        this.idle = holder(id, "idle");
        this.attack = holder(id, "attack");
        this.cast = holder(id, "cast");
        this.fly = holder(id, "fly");
        this.walkSpeed = walkSpeed;
    }

    private static AnimationHolder holder(String id, String anim) {
        return AnimationLoader.INSTANCE.getAnimationHolder(DeadCraft.id(id + "/" + anim));
    }

    @Nullable
    private ModelPart find(ModelPart root, String name) {
        return getAnyDescendantWithName(name).orElse(null);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float amount, float age, float yaw, float pitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        boolean frozen = entity instanceof DeadMonster m && m.isGazeFrozen();
        if (entity instanceof Animated a) {
            if (!frozen) {
                boolean airborne = fly.isBound() && !entity.onGround();
                if (airborne) {
                    animate(a.flyAnimation(), fly, age);
                } else if (walk.isBound()) {
                    animateWalk(walk, limbSwing, amount, walkSpeed, 2.5f);
                }
                if (idle.isBound()) animate(a.idleAnimation(), idle, age);
            }
            if (attack.isBound()) animate(a.attackAnimation(), attack, age);
            if (cast.isBound()) animate(a.castAnimation(), cast, age);
        }
        ModelPart h = head != null ? head : find(root, "head");
        if (h != null) {
            h.yRot += Mth.clamp(yaw, -60f, 60f) * Mth.DEG_TO_RAD;
            h.xRot += pitch * Mth.DEG_TO_RAD * 0.8f;
            if (frozen) h.zRot += 0.35f; // The Hollow's sideways stare while watched
        }
    }
}
