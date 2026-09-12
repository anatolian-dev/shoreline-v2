package net.shoreline.client;


public class ShutdownHook extends Thread
{
    public ShutdownHook()
    {
        setName("Shoreline-ShutdownHook");
    }


    @Override
    public void run()
    {
        Shoreline.CONFIG.saveModConfiguration();
    }
}
