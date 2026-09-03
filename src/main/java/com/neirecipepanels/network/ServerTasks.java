package com.neirecipepanels.network;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Runs work submitted from the netty thread on the server thread at end of tick. */
public final class ServerTasks {

    public static final ServerTasks INSTANCE = new ServerTasks();

    private static final Queue<Runnable> QUEUE = new ConcurrentLinkedQueue<>();

    private ServerTasks() {}

    public static void submit(Runnable task) {
        QUEUE.add(task);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Runnable task;
        while ((task = QUEUE.poll()) != null) {
            task.run();
        }
    }
}
