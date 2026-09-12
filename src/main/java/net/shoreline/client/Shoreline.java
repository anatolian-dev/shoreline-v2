package net.shoreline.client;

import net.shoreline.client.api.font.FontManager;
import net.shoreline.client.impl.Managers;
import net.shoreline.client.impl.file.ModConfiguration;
import net.shoreline.client.util.ShorelineLog;


public class Shoreline
{
    public static final long UPTIME = System.currentTimeMillis();

    public static ModConfiguration CONFIG;


    public static ShutdownHook SHUTDOWN;


    public static void init()
    {
        info("Starting Shoreline...");

        Managers.init();

        CONFIG = new ModConfiguration();
        CONFIG.loadModConfiguration();

        SHUTDOWN = new ShutdownHook();
        Runtime.getRuntime().addShutdownHook(SHUTDOWN);
    }

    public static void postInit()
    {
        FontManager.init();
    }

    public static void info(String message)
    {
        ShorelineLog.info(message);
    }

    public static void info(String message, Object... params)
    {
        ShorelineLog.info(message, params);
    }
}
