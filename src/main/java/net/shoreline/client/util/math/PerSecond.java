package net.shoreline.client.util.math;

public class PerSecond
{
    private static final int BUCKET_COUNT = 20; // 20 buckets * 50ms = 1000ms window
    private static final long BUCKET_SIZE_MS = 50L;

    private final int[] counts = new int[BUCKET_COUNT];
    private final long[] bucketTimes = new long[BUCKET_COUNT];

    public synchronized void count()
    {
        long now = System.currentTimeMillis();
        long bucket = now / BUCKET_SIZE_MS;
        int idx = (int) (bucket % BUCKET_COUNT);
        if (bucketTimes[idx] != bucket)
        {
            bucketTimes[idx] = bucket;
            counts[idx] = 0;
        }
        counts[idx]++;
    }

    public synchronized int getPerSecond()
    {
        long now = System.currentTimeMillis();
        long currentBucket = now / BUCKET_SIZE_MS;
        int total = 0;
        for (int i = 0; i < BUCKET_COUNT; i++)
        {
            if (currentBucket - bucketTimes[i] < BUCKET_COUNT)
            {
                total += counts[i];
            }
        }
        return total;
    }
}
