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

    /**
     * Bone lookup cache. Vanilla's keyframe player resolves every animated bone by name each frame
     * with a full tree search (a stream over every part); on 30-60 bone rigs, with two or three
     * animations playing on every creature, that dominated the render cost. We index once.
     */
    private final java.util.Map<String, java.util.Optional<ModelPart>> byName = new java.util.HashMap<>();

    private static java.lang.reflect.Field childrenField;

    @SuppressWarnings("unchecked")
    private static void index(ModelPart part, java.util.Map<String, java.util.Optional<ModelPart>> out) {
        try {
            if (childrenField == null) {
                for (var f : ModelPart.class.getDeclaredFields()) {
                    if (java.util.Map.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        childrenField = f;
                        break;
                    }
                }
            }
            var children = (java.util.Map<String, ModelPart>) childrenField.get(part);
            for (var e : children.entrySet()) {
                out.putIfAbsent(e.getKey(), java.util.Optional.of(e.getValue()));
                index(e.getValue(), out);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // fall back to the vanilla search below
        }
    }

    @Override
    public java.util.Optional<ModelPart> getAnyDescendantWithName(String name) {
        java.util.Optional<ModelPart> hit = byName.get(name);
        if (hit != null) return hit;
        hit = super.getAnyDescendantWithName(name);
        byName.put(name, hit);
        return hit;
    }

    // ---- glow pass: only bones that carry emissive pixels are drawn (generated glow_parts.json) ----
    private static java.util.Map<String, java.util.Set<String>> glowParts;
    private final String id;
    private java.util.List<ModelPart> dark;

    private static java.util.Set<String> glowBones(String id) {
        if (glowParts == null) {
            glowParts = new java.util.HashMap<>();
            try (var in = net.minecraft.client.Minecraft.getInstance().getResourceManager()
                    .open(DeadCraft.id("glow_parts.json")); var r = new java.io.InputStreamReader(in)) {
                var json = com.google.gson.JsonParser.parseReader(r).getAsJsonObject();
                for (var e : json.entrySet()) {
                    java.util.Set<String> names = new java.util.HashSet<>();
                    e.getValue().getAsJsonArray().forEach(v -> names.add(v.getAsString()));
                    glowParts.put(e.getKey(), names);
                }
            } catch (Exception ignored) {
                // without the table every bone glows, as before
            }
        }
        return glowParts.get(id);
    }

    /** Draws only the glowing bones (eyes, flames, crystals...) for the emissive layer. */
    public void renderGlow(com.mojang.blaze3d.vertex.PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer vc, int light, int overlay, int color) {
        if (dark == null) {
            java.util.Set<String> lit = glowBones(id);
            dark = new java.util.ArrayList<>();
            if (lit != null) {
                for (var e : byName.entrySet()) {
                    if (!lit.contains(e.getKey()) && e.getValue().isPresent()) dark.add(e.getValue().get());
                }
            }
        }
        for (ModelPart p : dark) p.skipDraw = true;
        renderToBuffer(pose, vc, light, overlay, color);
        for (ModelPart p : dark) p.skipDraw = false;
    }

    public DeadRigModel(ModelPart root, String id, float walkSpeed) {
        this.id = id;
        this.root = root;
        index(root, byName);
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

    private final net.minecraft.world.entity.AnimationState menuIdle = new net.minecraft.world.entity.AnimationState();
    private final net.minecraft.world.entity.AnimationState menuWalk = new net.minecraft.world.entity.AnimationState();

    /** Poses the model outside the world (the title screen): its idle loop, plus a slow walk cycle if asked. */
    public void poseForMenu(float ageTicks, boolean walking) {
        root.getAllParts().forEach(ModelPart::resetPose);
        menuIdle.startIfStopped(0);
        if (walking && walk.isBound()) animateWalk(walk, ageTicks * 0.35f, 0.6f, walkSpeed, 2.5f);
        if (idle.isBound()) animate(menuIdle, idle, ageTicks);
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
