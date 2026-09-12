package net.shoreline.client.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public final class ShorelineLog
{
    private static final Logger LOGGER = LogManager.getLogger("Shoreline");

    private ShorelineLog()
    {
    }

    public static void info(String message)
    {
        LOGGER.info("[Shoreline] {}", message);
    }

    public static void info(String message, Object... params)
    {
        LOGGER.info("[Shoreline] " + message, params);
    }

    public static void error(String message)
    {
        LOGGER.error("[Shoreline] {}", message);
    }

    public static void error(String message, Object... params)
    {
        LOGGER.error("[Shoreline] " + message, params);
    }

    public static void error(String message, Throwable t)
    {
        LOGGER.error("[Shoreline] {}", message, t);
    }
}
