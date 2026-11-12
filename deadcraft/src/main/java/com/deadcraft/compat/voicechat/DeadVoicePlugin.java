package com.deadcraft.compat.voicechat;

import com.deadcraft.DeadCraft;
import com.deadcraft.config.DeadConfig;
import com.deadcraft.world.Hearing;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple Voice Chat integration: creatures hear you talk.
 * <p>
 * Simple Voice Chat already provides the proximity voice chat itself. This plugin listens to
 * every microphone packet, measures how loud it is, and turns it into a {@link Hearing} noise:
 * whispering carries a few blocks, shouting carries across a ruin. Only loaded when Simple Voice
 * Chat is installed (it discovers classes annotated with {@link ForgeVoicechatPlugin}).
 */
@ForgeVoicechatPlugin
public class DeadVoicePlugin implements VoicechatPlugin {
    private static final long MIN_INTERVAL_MS = 250;

    private VoicechatApi api;
    private final Map<UUID, OpusDecoder> decoders = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastNoise = new ConcurrentHashMap<>();

    @Override
    public String getPluginId() {
        return DeadCraft.MODID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        this.api = api;
        DeadCraft.LOG.info("DeadCraft: Simple Voice Chat found, creatures can now hear players speak");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        if (!DeadConfig.VOICE_HEARING.get()) return;
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null || !(sender.getPlayer().getPlayer() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        if (now - lastNoise.getOrDefault(id, 0L) < MIN_INTERVAL_MS) return;

        byte[] opus = event.getPacket().getOpusEncodedData();
        if (opus == null || opus.length == 0) {
            OpusDecoder d = decoders.remove(id);
            if (d != null) d.close();
            return;
        }
        OpusDecoder decoder = decoders.computeIfAbsent(id, k -> api.createDecoder());
        short[] pcm;
        try {
            pcm = decoder.decode(opus);
        } catch (Exception e) {
            return;
        }
        double loudness = rmsDb(pcm); // roughly -90 (silence) to 0 (clipping)
        if (loudness < -50) return;
        lastNoise.put(id, now);

        // Map loudness to range: -50 dB → 20% of the base range, -10 dB and louder → 160%.
        double t = Math.min(1.0, (loudness + 50) / 40.0);
        double radius = DeadConfig.VOICE_RANGE.get() * (0.2 + 1.4 * t);
        if (event.getPacket().isWhispering()) radius *= 0.35;
        double r = radius;
        player.server.execute(() -> {
            if (player.isAlive() && !player.isSpectator()) Hearing.noise(player.serverLevel(), player.position(), r, player);
        });
    }

    private static double rmsDb(short[] pcm) {
        if (pcm == null || pcm.length == 0) return -100;
        double sum = 0;
        for (short s : pcm) {
            double v = s / 32768.0;
            sum += v * v;
        }
        double rms = Math.sqrt(sum / pcm.length);
        return rms <= 1.0e-6 ? -100 : 20 * Math.log10(rms);
    }
}
