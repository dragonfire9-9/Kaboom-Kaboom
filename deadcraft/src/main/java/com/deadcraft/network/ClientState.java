package com.deadcraft.network;

/**
 * Values the server pushes to the client. Kept free of client-only classes so the payload
 * handlers can be registered on both physical sides.
 */
public final class ClientState {
    public static volatile float mana;
    public static volatile float maxMana = 100f;
    public static volatile boolean awakened;
    /** Accumulated shake trauma; the camera decays it every frame. */
    public static volatile float shake;

    private ClientState() {}
}
