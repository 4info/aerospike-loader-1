package com.aerospike.load.hs;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Host;
import com.aerospike.client.Key;
import com.aerospike.client.async.*;
import com.aerospike.client.policy.ClientPolicy;
import com.aerospike.client.policy.WritePolicy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class UploadHouseHoldData {

    private final static int         NUM_OF_CORES;
    private final static String      ZIP                     = "zip";
    private final static String      STATE                   = "st";
    private final static String      DMA                     = "dma";
    private final static int         COMMANDS_PER_EVENT_LOOP = 100;
    private final static int         DELAY_QUEUE_SIZE        = 100;
    private final static WritePolicy policy;
    private final static Throttles   throttles;
    private final static Logger      log                     = LogManager.getLogger(
            UploadHouseHoldData.class);

    static {
        NUM_OF_CORES = Runtime.getRuntime().availableProcessors();
        throttles = new Throttles(NUM_OF_CORES, COMMANDS_PER_EVENT_LOOP);
        policy = new WritePolicy();
        policy.expiration = -1;
    }

    final String       setName;
    final String       nameSpace;
    final Host[]       hosts;
    final int          PORT;
    final List<String> fileNames;

    public UploadHouseHoldData(String nameSpace, Host[] hosts, String setName, int port,
            List<String> fileNames) {
        this.nameSpace = nameSpace;
        this.hosts = hosts;
        this.PORT = port;
        this.fileNames = fileNames;
        this.setName = setName;
    }

    EventLoops initializeEventLoops() {
        final EventPolicy eventPolicy = new EventPolicy();
        eventPolicy.maxCommandsInProcess = COMMANDS_PER_EVENT_LOOP;
        eventPolicy.maxCommandsInQueue = DELAY_QUEUE_SIZE;
        return new NioEventLoops(eventPolicy, NUM_OF_CORES);
    }

    AerospikeClient initializeAerospikeClient(EventLoops eventLoops) {
        final ClientPolicy clientPolicy = new ClientPolicy();
        clientPolicy.eventLoops = eventLoops;
        clientPolicy.timeout = 100000;
        int concurrentMax = COMMANDS_PER_EVENT_LOOP * NUM_OF_CORES;
        if (clientPolicy.maxConnsPerNode < concurrentMax) {
            clientPolicy.maxConnsPerNode = concurrentMax;
        }
        return new AerospikeClient(clientPolicy, this.hosts);
    }

    long insertRecordsFromCSVFile(String fileName, AerospikeClient client, int numRecords,
            EventLoops eventLoops,
            int progressFreq) {

        final long startTime = System.nanoTime();
        final Monitor monitor = new Monitor();
        final AtomicInteger asyncWriteCount = new AtomicInteger(0);
        try {
            writeToAerospikeAsync(fileName, client, numRecords, eventLoops, progressFreq, monitor,
                    asyncWriteCount);
            monitor.waitTillComplete();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        long endTime = System.nanoTime();
        return (endTime - startTime);
    }

    private void writeToAerospikeAsync(String fileName, AerospikeClient client, int numRecords,
            EventLoops eventLoops,
            int progressFreq,
            Monitor monitor, AtomicInteger asyncWriteCount)
            throws IOException {
        final BufferedReader br = new BufferedReader(new FileReader(fileName));
        String line;
        br.readLine();// Skip The first header line
        while ((line = br.readLine()) != null) {
            // use comma as separator
            String[] hsData = line.split(",");

            final Key houseHoldId = new Key(nameSpace, setName, Long.parseLong(hsData[0].trim()));
            final Bin zip = new Bin(ZIP, hsData[1].trim());
            final Bin state = new Bin(STATE, hsData[2].trim());
            final Bin dma = new Bin(DMA, hsData[3].trim());
            final EventLoop eventLoop = eventLoops.next();
            int eventLoopIndex = eventLoop.getIndex();

            if (throttles.waitForSlot(eventLoopIndex, 1)) {
                try {
                    client.put(eventLoop,
                            new HouseHoldWriteListener(houseHoldId, eventLoopIndex, numRecords, monitor,
                                    progressFreq,
                                    throttles, asyncWriteCount),
                            policy, houseHoldId, zip, state, dma);
                } catch (Exception e) {
                    e.printStackTrace();
                    throttles.addSlot(eventLoopIndex, 1);
                }
            }

        }
        br.close();
    }

    public void startUpload() throws IOException {

        log.info("Number of Cores : {}", NUM_OF_CORES);

        final EventLoops eventLoops = initializeEventLoops();
        final AerospikeClient client = initializeAerospikeClient(eventLoops);

        for (String fileName : this.fileNames) {

            final int numRecords = getNumberOfRecordsInFile(fileName);
            final int frequency = getFrequencyOfLog(numRecords);

            log.info("Starting upload - Number of Records in {} : {}", fileName, numRecords);

            final long elapsedTime = insertRecordsFromCSVFile(fileName, client, numRecords, eventLoops, frequency);

            log.info("Upload complete - Inserted {} records for file {} in {} milliseconds", numRecords,
                    fileName, elapsedTime / 1000000);

        }
        client.close();
        eventLoops.close();
    }

    private int getFrequencyOfLog(int numRecords) {
        // Calculate how frequently logging of inserted records will be printed
        if (numRecords < 1000000) {
            return numRecords / 4;
        }
        return 1000000;
    }

    private int getNumberOfRecordsInFile(String fileName) throws IOException {
        // Find number of lines/records in the file
        final File file = new File(fileName);
        final LineNumberReader lineNumberReader = new LineNumberReader(new FileReader(file));
        while ((lineNumberReader.readLine()) != null)
            ;
        int lines = lineNumberReader.getLineNumber();
        lineNumberReader.close();
        return lines - 1; // minus 1 to skip the header line
    }
}
