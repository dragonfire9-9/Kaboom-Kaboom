package com.kaboom.jjk.network;

import com.kaboom.jjk.client.ClientPayloadHandler;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    public static final String VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);

        registrar.playToClient(SyncJJKDataPayload.TYPE, SyncJJKDataPayload.STREAM_CODEC, ClientPayloadHandler::handleSync);
        registrar.playToClient(MarkingsPayload.TYPE, MarkingsPayload.STREAM_CODEC, ClientPayloadHandler::handleMarkings);
        registrar.playToClient(ClientFxPayload.TYPE, ClientFxPayload.STREAM_CODEC, ClientPayloadHandler::handleFx);
        registrar.playToClient(DomainStatePayload.TYPE, DomainStatePayload.STREAM_CODEC, ClientPayloadHandler::handleDomains);
        registrar.playToClient(RollResultPayload.TYPE, RollResultPayload.STREAM_CODEC, ClientPayloadHandler::handleRollResult);

        registrar.playToClient(OpenNpcPayload.TYPE, OpenNpcPayload.STREAM_CODEC, ClientPayloadHandler::handleOpenNpc);
        registrar.playToServer(NpcActionPayload.TYPE, NpcActionPayload.STREAM_CODEC, ServerPayloadHandler::handleNpcAction);
        registrar.playToServer(RollActionPayload.TYPE, RollActionPayload.STREAM_CODEC, ServerPayloadHandler::handleRollAction);
        registrar.playToServer(KeyActionPayload.TYPE, KeyActionPayload.STREAM_CODEC, ServerPayloadHandler::handleKeyAction);
    }

    private ModNetwork() {}
}
