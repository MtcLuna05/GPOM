package com.luna.gpom.optimization.alfheim;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;

import java.util.NoSuchElementException;

/** Alfheim-only FIFO variant that retains its peak capacity between lighting passes. */
public final class CapacityRetainingLongArrayFIFOQueue extends LongArrayFIFOQueue {
    public CapacityRetainingLongArrayFIFOQueue(int capacity) {
        super(capacity);
    }

    @Override
    public long dequeueLong() {
        if (start == end) {
            throw new NoSuchElementException();
        }
        long value = array[start++];
        if (start == length) {
            start = 0;
        }
        return value;
    }
}
