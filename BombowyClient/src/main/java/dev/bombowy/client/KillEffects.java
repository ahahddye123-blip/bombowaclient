package dev.bombowy.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class KillEffects {
    private KillEffects() {}

    public static final String[] EFFECT_NAMES = {
            "Dolary ($)",
            "Rekin z nieba",
            "Piorun",
            "Eksplozja iskier",
            "Złote monety"
    };

    private static final class ActiveEffect {
        final int type;
        final double x, y, z;
        final long startMs;
        final long durationMs;
        final List<BillParticle> bills = new ArrayList<>();
        final List<SparkParticle> sparks = new ArrayList<>();

        ActiveEffect(int type, double x, double y, double z, long now, Random rng) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.startMs = now;
            this.durationMs = (type == 1) ? 1450L : (type == 2) ? 950L : 1550L;

            if (type == 0 || type == 4) {
                int count = 14;
                for (int i = 0; i < count; i++) {
                    double ang = (Math.PI * 2.0 * i) / count + rng.nextDouble() * 0.25;
                    double spd = 0.045 + rng.nextDouble() * 0.055;
                    bills.add(new BillParticle(
                            Math.cos(ang) * spd,
                            0.055 + rng.nextDouble() * 0.055,
                            Math.sin(ang) * spd,
                            rng.nextFloat() * 360f,
                            (rng.nextFloat() - 0.5f) * 8f
                    ));
                }
            }
            for (int i = 0; i < 18; i++) {
                double ang = rng.nextDouble() * Math.PI * 2.0;
                double spd = 0.03 + rng.nextDouble() * 0.08;
                sparks.add(new SparkParticle(
                        Math.cos(ang) * spd,
                        0.02 + rng.nextDouble() * 0.08,
                        Math.sin(ang) * spd
                ));
            }
        }
    }

    private static final class BillParticle {
        double vx, vy, vz, px, py, pz;
        float yaw, vYaw;
        BillParticle(double vx, double vy, double vz, float yaw, float vYaw) {
            this.vx = vx; this.vy = vy; this.vz = vz; this.yaw = yaw; this.vYaw = vYaw;
        }
        void step() {
            px += vx; py += vy; pz += vz;
            vy -= 0.0028;
            yaw += vYaw;
        }
    }

    private static final class SparkParticle {
        double vx, vy, vz, px, py, pz;
        SparkParticle(double vx, double vy, double vz) {
            this.vx = vx; this.vy = vy; this.vz = vz;
        }
        void step() {
            px += vx; py += vy; pz += vz;
            vy -= 0.0022;
        }
    }

    private static final List<ActiveEffect> EFFECTS = new ArrayList<>();
    private static final Random RNG = new Random();

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(KillEffects::renderAll);
    }

    public static void spawnAt(double x, double y, double z) {
        BombowyConfig cfg = BombowyConfig.get();
        int type = MathHelper.clamp(cfg.killEffectType, 0, EFFECT_NAMES.length - 1);
        long now = System.currentTimeMillis();
        if (EFFECTS.size() >= 12) EFFECTS.remove(0);
        EFFECTS.add(new ActiveEffect(type, x, y, z, now, RNG));

        MinecraftClient client = MinecraftClient.getInstance();
        if (cfg.killEffectSound && client.world != null && client.player != null) {
            switch (type) {
                case 0, 4 -> client.world.playSound(client.player, x, y, z,
                        SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.35f);
                case 1 -> client.world.playSound(client.player, x, y, z,
                        SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 1.2f, 0.85f);
                case 2 -> client.world.playSound(client.player, x, y, z,
                        SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 1.1f, 1.05f);
                case 3 -> client.world.playSound(client.player, x, y, z,
                        SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.7f, 1.25f);
            }
        }
    }

    private static void renderAll(WorldRenderContext ctx) {
        if (EFFECTS.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            EFFECTS.clear();
            return;
        }

        long now = System.currentTimeMillis();
        MatrixStack matrices = ctx.matrixStack();
        if (matrices == null) return;
        Camera cam = ctx.camera();
        Vec3d cPos = cam.getPos();
        float scale = MathHelper.clamp(BombowyConfig.get().killEffectScale, 0.6f, 1.8f);

        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        TextRenderer tr = client.textRenderer;

        Iterator<ActiveEffect> it = EFFECTS.iterator();
        while (it.hasNext()) {
            ActiveEffect ef = it.next();
            long age = now - ef.startMs;
            if (age > ef.durationMs) {
                it.remove();
                continue;
            }
            float t = age / (float) ef.durationMs;
            float alpha = 1.0f - MathHelper.clamp((t - 0.65f) / 0.35f, 0.0f, 1.0f);
            int aByte = MathHelper.clamp((int) (alpha * 255), 0, 255);

            for (BillParticle b : ef.bills) b.step();
            for (SparkParticle s : ef.sparks) s.step();

            if (ef.type == 0 || ef.type == 4) {
                boolean dollar = (ef.type == 0);
                int billColor = dollar ? ((aByte << 24) | 0x22C55E) : ((aByte << 24) | 0xFACC15);
                int borderColor = dollar ? ((aByte << 24) | 0x14532D) : ((aByte << 24) | 0xA16207);

                for (int i = 0; i < ef.bills.size(); i++) {
                    BillParticle b = ef.bills.get(i);
                    matrices.push();
                    matrices.translate(ef.x + b.px - cPos.x, ef.y + b.py - cPos.y, ef.z + b.pz - cPos.z);
                    matrices.multiply(cam.getRotation());
                    matrices.multiply(com.mojang.math.Axis.ZP.rotationDegrees(b.yaw * 0.25f));
                    matrices.scale(0.022f * scale, -0.022f * scale, 0.022f * scale);
                    Matrix4f m = matrices.peek().getPositionMatrix();

                    int bw = dollar ? 18 : 10, bh = 10;
                    net.minecraft.client.gui.DrawContext dc = new net.minecraft.client.gui.DrawContext(client, immediate);
                    dc.fill(-bw / 2 - 1, -bh / 2 - 1, bw / 2 + 1, bh / 2 + 1, borderColor);
                    dc.fill(-bw / 2, -bh / 2, bw / 2, bh / 2, billColor);
                    String sym = dollar ? "$" : "●";
                    tr.draw(Text.literal(sym), -tr.getWidth(sym) / 2.0f, -4.0f,
                            (aByte << 24) | 0xFFFFFF, false, m, immediate, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
                    matrices.pop();
                }
            } else if (ef.type == 1) {
                float dropY = 7.5f * (1.0f - MathHelper.clamp(t * 1.45f, 0.0f, 1.0f));
                matrices.push();
                matrices.translate(ef.x - cPos.x, ef.y + dropY - cPos.y, ef.z - cPos.z);
                matrices.multiply(cam.getRotation());
                matrices.scale(0.045f * scale, -0.045f * scale, 0.045f * scale);
                Matrix4f m = matrices.peek().getPositionMatrix();
                net.minecraft.client.gui.DrawContext dc = new net.minecraft.client.gui.DrawContext(client, immediate);
                dc.fill(-20, -10, 20, 10, (aByte << 24) | 0x334155);
                dc.fill(-14, -6, 14, 6, (aByte << 24) | 0x64748B);
                dc.fill(-4, -16, 4, -8, (aByte << 24) | 0x475569);
                tr.draw(Text.literal("SHARK!"), -tr.getWidth("SHARK!") / 2.0f, -4.0f,
                        (aByte << 24) | 0xF8FAFC, false, m, immediate, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
                matrices.pop();
            } else if (ef.type == 2) {
                VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());
                matrices.push();
                matrices.translate(ef.x - cPos.x, ef.y - cPos.y, ef.z - cPos.z);
                var entry = matrices.peek();
                float[][] bolts = {
                        {0f, 12f, 0f, 0.35f * scale, 7.5f, 0.25f * scale},
                        {0.35f * scale, 7.5f, 0.25f * scale, -0.30f * scale, 3.8f, -0.20f * scale},
                        {-0.30f * scale, 3.8f, -0.20f * scale, 0f, 0f, 0f}
                };
                for (float[] seg : bolts) {
                    lines.vertex(entry, seg[0], seg[1], seg[2]).color(0.75f, 0.92f, 1.0f, alpha).normal(entry, 0f, 1f, 0f);
                    lines.vertex(entry, seg[3], seg[4], seg[5]).color(1.0f, 0.98f, 0.65f, alpha).normal(entry, 0f, 1f, 0f);
                }
                matrices.pop();
                immediate.draw(RenderLayer.getLines());
            } else if (ef.type == 3) {
                for (SparkParticle s : ef.sparks) {
                    matrices.push();
                    matrices.translate(ef.x + s.px - cPos.x, ef.y + s.py - cPos.y, ef.z + s.pz - cPos.z);
                    matrices.multiply(cam.getRotation());
                    matrices.scale(0.025f * scale, -0.025f * scale, 0.025f * scale);
                    net.minecraft.client.gui.DrawContext dc = new net.minecraft.client.gui.DrawContext(client, immediate);
                    dc.fill(-3, -3, 3, 3, (aByte << 24) | 0xFF8C3B);
                    matrices.pop();
                }
            }
        }
        immediate.draw();
    }
}
