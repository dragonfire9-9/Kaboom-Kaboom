package com.kaboom.jjk.client;

import com.kaboom.jjk.network.MarkingsPayload;
import com.kaboom.jjk.network.RollResultPayload;
import com.kaboom.jjk.network.SyncJJKDataPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Referenced from common network registration, so it must not touch client classes in its own signatures.
 * The real work is delegated to {@link ClientHooks}, which is only loaded on the physical client.
 */
public final class ClientPayloadHandler {

    public static void handleSync(SyncJJKDataPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientHooks.applySync(payload));
    }

    public static void handleRollResult(RollResultPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientHooks.showRoll(payload));
    }

    public static void handleMarkings(MarkingsPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientHooks.setMarked(payload.entityId(), payload.marked()));
    }

    private ClientPayloadHandler() {}
}
