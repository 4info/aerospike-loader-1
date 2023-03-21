package com.aerospike.load.hs;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Key;
import com.aerospike.client.async.Monitor;
import com.aerospike.client.async.Throttles;
import com.aerospike.client.listener.WriteListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

public class HouseHoldWriteListener implements WriteListener {

    private final static Logger        log = LogManager.getLogger(
            HouseHoldWriteListener.class);
    private final        AtomicInteger asyncWriteCount;
    private final        Key           key;
    private final        int           eventLoopIndex;
    private final        int           finalCount;
    private final        int           progressFreq;
    private final        Throttles     throttles;
    private final        Monitor       monitor;

    public HouseHoldWriteListener(Key key, int eventLoopIndex, int finalCount, Monitor monitor,
            int progressFreq,
            Throttles throttles, AtomicInteger atomicInteger) {
        this.key = key;
        this.eventLoopIndex = eventLoopIndex;
        this.finalCount = finalCount;
        this.monitor = monitor;
        this.progressFreq = progressFreq;
        this.throttles = throttles;
        this.asyncWriteCount = atomicInteger;
    }

    @Override
    public void onSuccess(Key key) {

        throttles.addSlot(eventLoopIndex, 1);
        int currentCount = asyncWriteCount.incrementAndGet();
        if (progressFreq > 0 && currentCount % progressFreq == 0) {
            log.info("Inserted {} records in Aerospike",currentCount);
        }
        if (currentCount == finalCount) {
            monitor.notifyComplete();
        }

    }

    @Override
    public void onFailure(AerospikeException e) {
        log.info("Put failed: namespace={} set={} key={} exception={}",key.namespace,key.setName,key.userKey,e.getMessage());
        monitor.notifyComplete();
    }
}
