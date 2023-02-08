package lpmaker;

import lpmaker.graphs.Graph;
import lpmaker.graphs.GraphFromFileSrcDstPair;
import lpmaker.graphs.LeafSpine;

import java.io.*;
import java.util.*;

class TrafficPair {
    public int from;
    public int to;

    TrafficPair(int a, int b) {
        from = a;
        to = b;
    }
}

public class TrafficMatrix {
    public double[][] switchLevelMatrix;
    public double[][] serverLevelMatrix;
    public double[][] serverTrafficMatrix;
    double trafficPerFlow = 1;
    int packetSize = 1500;
    double trafficCap = 50;
    int numSwitches;
    int numServers;
    int trafficmode;
    Graph topology;
    HashSet<Integer> hotRacks;
    int timeframeStart;
    int timeframeEnd;

    public TrafficMatrix(int _numSwitches, int trafficMode, String trafficfile, Graph mynet, int a, int b, int[] numServersPerSwitches, int _timeframeStart, int _timeframeEnd, GraphFromFileSrcDstPair rrgnet, LeafSpine lsnet) {
        numSwitches = _numSwitches;
        numServers = mynet.totalWeight;
        int numNodes = numServers+numSwitches;
        switchLevelMatrix = new double[numSwitches][numSwitches];
        serverLevelMatrix = new double[numServers][numServers];
        serverTrafficMatrix = new double[numNodes][numNodes];
        trafficmode = trafficMode;
        topology = mynet;
        hotRacks = new HashSet<>();
        timeframeStart = _timeframeStart;
        timeframeEnd = _timeframeEnd;

        generateTraffic(trafficfile, a, b, numServersPerSwitches, rrgnet, lsnet);
    }

    // Send from all servers to some random server
    public void TrafficGenAllToOne()
    {
        System.out.println("All-to-one flows");

        int target = topology.rand.nextInt(numServers);
        int dst_sw = topology.svrToSwitch(target);
        int numFlows = 0;

        for (int svr = 0; svr < numServers; svr++) {
            int src_sw = topology.svrToSwitch(svr);
            if (src_sw == dst_sw) continue;
            switchLevelMatrix[src_sw][dst_sw] += trafficPerFlow;
            numFlows++;
        }

        System.out.println("Number of flows = " + numFlows);
        System.out.println("Target = " + target);
    }

    public void TrafficGenAllAll()
    {
        System.out.println("All-to-all flows");
        int numFlows = 0;

        for (int svr = 0; svr < numServers; svr++) {
            for (int svrto = 0; svrto < numServers; svrto++) {
                int src_sw = topology.svrToSwitch(svr);
                int dst_sw = topology.svrToSwitch(svrto);
                if (src_sw == dst_sw) continue;
                switchLevelMatrix[src_sw][dst_sw] += trafficPerFlow;
                numFlows++;
            }

            System.out.println("Number of flows = " + numFlows);
        }
    }

    public void TrafficGenStride(int n) {
        System.out.println("Stride flows");
        int numFlows = 0;

        for (int svr = 0; svr < numServers; svr++) {
            int src_sw = topology.svrToSwitch(svr);
            int dst_sw = topology.svrToSwitch((svr+n)%numServers);
            if (src_sw == dst_sw) continue;
            switchLevelMatrix[src_sw][dst_sw] += trafficPerFlow;
            numFlows++;
        }

        System.out.println("Number of flows = " + numFlows);
    }

    public void TrafficGenRackLevelAllToAll() {
        System.out.println("Rack-level All-to-all flows");
        int numFlows = 0;

        for (int src = 0; src < numSwitches; src++) {
            for (int dst = 0; dst < numSwitches; dst++) {
                if (src == dst) continue;
                switchLevelMatrix[src][dst] += trafficPerFlow;
                numFlows++;
            }
        }

        System.out.println("Number of flows = " + numFlows);
    }

    public void TrafficGenFromFileHelper(int inaccuracymode, String filename) {
        if (inaccuracymode == 0) {
            TrafficGenFromFile(filename);
        } else {
            TrafficGenFromFileWithInaccuracy(inaccuracymode, filename);
        }
    }

    public void TrafficGenFromFile(String filename) {
        System.out.println("Flows from file " + filename);
        int numFlows = 0;
        double[][] accumulativeTrafficMatrix = new double[numSwitches][numSwitches];

        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            String strLine = "";
            while ((strLine = br.readLine()) != null){
                StringTokenizer strTok = new StringTokenizer(strLine);
                int src = Integer.parseInt(strTok.nextToken());
                int dst = Integer.parseInt(strTok.nextToken());
                int traffic = Integer.parseInt(strTok.nextToken());

                if (src>=numServers || dst >=numServers) continue;

                int src_sw = topology.svrToSwitch(src);
                int dst_sw = topology.svrToSwitch(dst);
                accumulativeTrafficMatrix[src_sw][dst_sw] += traffic;
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        double maxTraffic = 0;
        for (int ssw=0; ssw<numSwitches; ssw++) {
            for (int dsw=0; dsw<numSwitches; dsw++) {
                double traffic = accumulativeTrafficMatrix[ssw][dsw];
                maxTraffic = Math.max(maxTraffic, traffic);
            }
        }

//        try {
//            String writeFilename = "trafficfiles/tm_raw_fb_uniform.txt";
//            FileWriter fstream = new FileWriter(writeFilename);
//            BufferedWriter out = new BufferedWriter(fstream);

            double coefficient = maxTraffic/ trafficCap;
            for (int src = 0; src < numSwitches; src++) {
                for (int dst = 0; dst < numSwitches; dst++) {
                    if (src == dst) continue;
                    double mult = accumulativeTrafficMatrix[src][dst] / coefficient;
                    switchLevelMatrix[src][dst] += trafficPerFlow * mult;
//                    out.write(src + " " + dst + " " + trafficPerFlow * mult + "\n");
                    numFlows += mult;
                }
            }
//            out.close();
//        }
//        catch (Exception e)
//        {
//            System.err.println("Write TM File FromFile Error: " + e.getMessage());
//            e.printStackTrace();
//        }

        System.out.println("Number of flows = " + numFlows);

        if (filename.equals("trafficfiles/fb_skewed.data")) {
            System.out.print("Hot racks: ");
            if (numServers == 3072) {
                for (int i=65; i<80; i++) {
                    hotRacks.add(i);
                    System.out.print(i + " ");
                }
            } else {
                assert(numServers == 2988);
                for (int i=67; i<80; i++) {
                    hotRacks.add(i);
                    System.out.print(i + " ");
                }
            }
            System.out.println();
        } else {
            System.out.println("No hotRacks for file " + filename);
        }

//        System.out.println("************Temporary fix: for SU3 with fbs, remove flow 5229 from rack 67 to rack 61***************");
//        switchLevelMatrix[67][61] = 0;
    }

    public void TrafficGenFromFileWithInaccuracy(int inaccuracymode, String filename) {
        System.out.println("Flows from file " + filename);
        int numFlows = 0;
        double[][] accumulativeTrafficMatrix = new double[numSwitches][numSwitches];

        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            String strLine = "";
            while ((strLine = br.readLine()) != null){
                StringTokenizer strTok = new StringTokenizer(strLine);
                int src = Integer.parseInt(strTok.nextToken());
                int dst = Integer.parseInt(strTok.nextToken());
                int traffic = Integer.parseInt(strTok.nextToken());

                if (src>=numServers || dst >=numServers) continue;

                int src_sw = topology.svrToSwitch(src);
                int dst_sw = topology.svrToSwitch(dst);
                accumulativeTrafficMatrix[src_sw][dst_sw] += traffic;
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        double maxTraffic = 0;
        for (int ssw=0; ssw<numSwitches; ssw++) {
            for (int dsw=0; dsw<numSwitches; dsw++) {
                double traffic = accumulativeTrafficMatrix[ssw][dsw];
                maxTraffic = Math.max(maxTraffic, traffic);
            }
        }

//        try {
//            String writeFilename = "trafficfiles/tm_raw_fb_uniform.txt";
//            FileWriter fstream = new FileWriter(writeFilename);
//            BufferedWriter out = new BufferedWriter(fstream);

        double coefficient = maxTraffic/ trafficCap;
        for (int src = 0; src < numSwitches; src++) {
            for (int dst = 0; dst < numSwitches; dst++) {
                if (src == dst) continue;
                double mult = accumulativeTrafficMatrix[src][dst] / coefficient;
                switchLevelMatrix[src][dst] += trafficPerFlow * mult;
//                    out.write(src + " " + dst + " " + trafficPerFlow * mult + "\n");
                numFlows += mult;
            }
        }
//            out.close();
//        }
//        catch (Exception e)
//        {
//            System.err.println("Write TM File FromFile Error: " + e.getMessage());
//            e.printStackTrace();
//        }

        System.out.println("Number of flows = " + numFlows);

        int case1variation = 100;
        int case2variation = 100;
        int case2probability = 20;
        Random rand = new Random();
        for (int src=0; src<numSwitches; src++) {
            for (int dst=0; dst<numSwitches; dst++) {
                double oldValue = switchLevelMatrix[src][dst];
                switch (inaccuracymode) {
                    case 1:
                        double randomDouble = rand.nextDouble() * case1variation*2 - case1variation;
                        switchLevelMatrix[src][dst] = oldValue*(1+randomDouble/100.0);
                        break;
                    case 2:
                        int randomVary = rand.nextInt(case2probability);
                        if (randomVary == 0) {
                            int randomSign = rand.nextInt(2)*2-1;
                            double multiplier = randomSign*case2variation/100.0;
                            switchLevelMatrix[src][dst] = oldValue*(1+multiplier);
                        }
                        break;
                    default:
                        System.out.println("inaccuracymode = " + inaccuracymode + " is not recognized.");
                }
            }
        }
    }

    public void TrafficGenFromFileClusterX(String filename) {
        System.out.println("Flows from file " + filename);
        int starttime = timeframeStart;
        int endtime = timeframeEnd;

        int numFlows = 0;
        double[][] accumulativeTrafficMatrix = new double[numSwitches][numSwitches];

        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            String strLine = "";
            while ((strLine = br.readLine()) != null){
                StringTokenizer strTok = new StringTokenizer(strLine);
                int timestamp = Integer.parseInt(strTok.nextToken());
                int traffic = Integer.parseInt(strTok.nextToken());
                int src_sw = Integer.parseInt(strTok.nextToken());
                int dst_sw = Integer.parseInt(strTok.nextToken());

                if (timestamp >= endtime) break;
                if (timestamp >= starttime && timestamp < endtime) {
                    accumulativeTrafficMatrix[src_sw][dst_sw] += traffic;
                }
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        double maxTraffic = 0;
        for (int ssw=0; ssw<numSwitches; ssw++) {
            for (int dsw=0; dsw<numSwitches; dsw++) {
                double traffic = accumulativeTrafficMatrix[ssw][dsw];
                maxTraffic = Math.max(maxTraffic, traffic);
            }
        }

//        try {
//            String writeFilename = "trafficfiles/tm_raw_fb_uniform.txt";
//            FileWriter fstream = new FileWriter(writeFilename);
//            BufferedWriter out = new BufferedWriter(fstream);

        double coefficient = maxTraffic/ trafficCap;
        for (int src = 0; src < numSwitches; src++) {
            for (int dst = 0; dst < numSwitches; dst++) {
                if (src == dst) continue;
                double mult = accumulativeTrafficMatrix[src][dst] / coefficient;
                switchLevelMatrix[src][dst] += trafficPerFlow * mult;
//                    out.write(src + " " + dst + " " + trafficPerFlow * mult + "\n");
                numFlows += mult;
            }
        }
//            out.close();
//        }
//        catch (Exception e)
//        {
//            System.err.println("Write TM File FromFile Error: " + e.getMessage());
//            e.printStackTrace();
//        }

        System.out.println("Number of flows = " + numFlows);

        if (filename.equals("trafficfiles/fb_skewed.data")) {
            System.out.print("Hot racks: ");
            if (numServers == 3072) {
                for (int i=65; i<80; i++) {
                    hotRacks.add(i);
                    System.out.print(i + " ");
                }
            } else {
                assert(numServers == 2988);
                for (int i=67; i<80; i++) {
                    hotRacks.add(i);
                    System.out.print(i + " ");
                }
            }
            System.out.println();
        } else {
            System.out.println("No hotRacks for file " + filename);
        }

//        System.out.println("************Temporary fix: for SU3 with fbs, remove flow 5229 from rack 67 to rack 61***************");
//        switchLevelMatrix[67][61] = 0;
    }

    public void TrafficGenFromFileClusterXPacketAdjusted(String filename) {
        System.out.println("Flows from file " + filename);
        int starttime = timeframeStart;
        int endtime = timeframeEnd;

        int numFlows = 0;
        double[][] accumulativeTrafficMatrix = new double[numSwitches][numSwitches];

        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            String strLine = "";
            while ((strLine = br.readLine()) != null){
                StringTokenizer strTok = new StringTokenizer(strLine);
                int timestamp = Integer.parseInt(strTok.nextToken());
                int traffic = Integer.parseInt(strTok.nextToken());
                int src_sw = Integer.parseInt(strTok.nextToken());
                int dst_sw = Integer.parseInt(strTok.nextToken());

                if (timestamp >= endtime) break;
                if (timestamp >= starttime && timestamp < endtime) {
                    traffic = packetSize * ((traffic+packetSize-1) / packetSize);
                    accumulativeTrafficMatrix[src_sw][dst_sw] += traffic;
                }
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        double maxTraffic = 0;
        for (int ssw=0; ssw<numSwitches; ssw++) {
            for (int dsw=0; dsw<numSwitches; dsw++) {
                double traffic = accumulativeTrafficMatrix[ssw][dsw];
                maxTraffic = Math.max(maxTraffic, traffic);
            }
        }

//        try {
//            String writeFilename = "trafficfiles/tm_raw_fb_uniform.txt";
//            FileWriter fstream = new FileWriter(writeFilename);
//            BufferedWriter out = new BufferedWriter(fstream);

        double coefficient = maxTraffic/ trafficCap;
        for (int src = 0; src < numSwitches; src++) {
            for (int dst = 0; dst < numSwitches; dst++) {
                if (src == dst) continue;
                double mult = accumulativeTrafficMatrix[src][dst] / coefficient;
                switchLevelMatrix[src][dst] += trafficPerFlow * mult;
//                    out.write(src + " " + dst + " " + trafficPerFlow * mult + "\n");
                numFlows += mult;
            }
        }
//            out.close();
//        }
//        catch (Exception e)
//        {
//            System.err.println("Write TM File FromFile Error: " + e.getMessage());
//            e.printStackTrace();
//        }

        System.out.println("Number of flows = " + numFlows);

        if (filename.equals("trafficfiles/fb_skewed.data")) {
            System.out.print("Hot racks: ");
            if (numServers == 3072) {
                for (int i=65; i<80; i++) {
                    hotRacks.add(i);
                    System.out.print(i + " ");
                }
            } else {
                assert(numServers == 2988);
                for (int i=67; i<80; i++) {
                    hotRacks.add(i);
                    System.out.print(i + " ");
                }
            }
            System.out.println();
        } else {
            System.out.println("No hotRacks for file " + filename);
        }

//        System.out.println("************Temporary fix: for SU3 with fbs, remove flow 5229 from rack 67 to rack 61***************");
//        switchLevelMatrix[67][61] = 0;
    }

    public void TrafficGenClusterX(String cluster) {
        System.out.println("Flows from cluster " + cluster);
        int solve_starttime = timeframeStart;
        int solve_endtime = timeframeEnd;

        String file_prefix = "../DataParsing/outputs/cluster_" + cluster + "/traffic/traffic_64racks";
        String[] file_suffix = {};
        if (cluster.equals("a")) {
            file_suffix = new String[]{"_0_273"};
        } else if (cluster.equals("b")) {
            file_suffix = new String[]{"_0_500","_500_1000","_1000_1500","_1500_2000","_2000_2500","_2500_2900"};
        } else if (cluster.equals("c")) {
            file_suffix = new String[]{"_0_273"};
        }

        double numFlows = 0;
        double[][] accumulativeTrafficMatrix = new double[numSwitches][numSwitches];

        try {
            BufferedReader br;
            String strLine = "";

            for (int i=0; i<file_suffix.length; i++) {
                String trafficfile = file_prefix + file_suffix[i];
                br = new BufferedReader(new FileReader(trafficfile));
                while ((strLine = br.readLine()) != null){
                    StringTokenizer strTok = new StringTokenizer(strLine);
                    int timestamp = Integer.parseInt(strTok.nextToken());
                    int traffic = Integer.parseInt(strTok.nextToken());
                    int src_svr = Integer.parseInt(strTok.nextToken());
                    int dst_svr = Integer.parseInt(strTok.nextToken());
                    int src_sw = topology.svrToSwitch(src_svr);
                    int dst_sw = topology.svrToSwitch(dst_svr);
                    if (src_sw == dst_sw) continue;

                    if (timestamp >= solve_endtime) break;
                    if (timestamp >= solve_starttime && timestamp < solve_endtime) {
                        accumulativeTrafficMatrix[src_sw][dst_sw] += traffic;
                    }
                }
                br.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        double maxTraffic = 0;
        for (int ssw=0; ssw<numSwitches; ssw++) {
            for (int dsw=0; dsw<numSwitches; dsw++) {
                double traffic = accumulativeTrafficMatrix[ssw][dsw];
                maxTraffic = Math.max(maxTraffic, traffic);
            }
        }

        double coefficient = maxTraffic/ trafficCap;
        for (int src = 0; src < numSwitches; src++) {
            for (int dst = 0; dst < numSwitches; dst++) {
                if (src == dst) continue;
                double mult = accumulativeTrafficMatrix[src][dst] / coefficient;
                switchLevelMatrix[src][dst] += trafficPerFlow * mult;
                numFlows += mult;
            }
        }

        System.out.println("Number of flows = " + numFlows);
    }

    public void TrafficGenARackToBRack(int a, int b, int[] numServersPerSwitch)
    {
        System.out.println(a + " racks to " + b + " racks flows");

        HashSet<Integer> srcRacks = new HashSet<>();
        HashSet<Integer> dstRacks = new HashSet<>();
        System.out.print("Hot racks: ");
        while (srcRacks.size() < a) {
            int rack = topology.rand.nextInt(numSwitches);
            srcRacks.add(rack);
            hotRacks.add(rack);
            System.out.print(rack + " ");
        }
        while (dstRacks.size() < b) {
            int rack = topology.rand.nextInt(numSwitches);
            if (!srcRacks.contains(rack)) {
                dstRacks.add(rack);
                hotRacks.add(rack);
                System.out.print(rack + " ");
            }
        }
        System.out.println();

        int numFlows = 0;
        for (int srcSw : srcRacks) {
            for (int dstSw : dstRacks) {
                assert(srcSw != dstSw);
                switchLevelMatrix[srcSw][dstSw] += trafficPerFlow * numServersPerSwitch[srcSw];
                numFlows += numServersPerSwitch[srcSw];
            }
        }

        System.out.println("Number of flows = " + numFlows);
        System.out.print("Source racks: ");
        for (int r : srcRacks) {
            System.out.print(r + " ");
        }
        System.out.println();
        System.out.print("Destination racks: ");
        for (int r : dstRacks) {
            System.out.print(r + " ");
        }
        System.out.println();
    }

    public void TrafficGenMixA(int a, int[] numServersPerSwitch)
    {
        System.out.println("Half of the flows are in between " + a + " racks");

        HashSet<Integer> r2rRacks = new HashSet<>();
        System.out.print("Hot racks: ");
        while (r2rRacks.size() < a) {
            int rack = topology.rand.nextInt(numSwitches);
            r2rRacks.add(rack);
            hotRacks.add(rack);
            System.out.print(rack + " ");
        }
        System.out.println();

        HashSet<Integer> a2aRacks = new HashSet<>();
        for (int i=0; i<numSwitches; i++) {
            if (!r2rRacks.contains(i)) a2aRacks.add(i);
        }

        // Hard-coding for now
        double SNR = 0, SNA = 0;
//        if (a == 10) {
//            SNR = 53.6;
//            SNA = 1;
//        } else if (a == 5) {
//            SNR = 55.5;
//            SNA = 0.2;
//        } else if (a == 2) {
//            SNR = 30;
//            SNA = 0.01;
//        } else {
//            System.out.println("The parameters here are hard-coded. a=" + a + " does not have SNR and SNA hard-coded.");
//        }
        SNR = trafficCap;
        SNA = (trafficCap*a*(a-1))/((numSwitches-a)*(numSwitches-a-1));
        System.out.println("SNR = " + SNR + ", SNA = " + SNA);

//        try {
//            String writeFilename = "trafficfiles/tm_raw_mix5.txt";
//            FileWriter fstream = new FileWriter(writeFilename);
//            BufferedWriter out = new BufferedWriter(fstream);

            double numa2aFlows = 0;
            for (int srca2a : a2aRacks) {
                for (int dsta2a : a2aRacks) {
                    if (srca2a == dsta2a) continue;
                    switchLevelMatrix[srca2a][dsta2a] += trafficPerFlow * SNA;
//                    out.write(srca2a + " " + dsta2a + " " + trafficPerFlow * SNA + "\n");
                    numa2aFlows += SNA;
                }
            }
            double numr2rFlows = 0;
            for (int srcr2r : r2rRacks) {
                for (int dstr2r : r2rRacks) {
                    if (srcr2r == dstr2r) continue;
                    switchLevelMatrix[srcr2r][dstr2r] += trafficPerFlow * SNR;
//                    out.write(srcr2r + " " + dstr2r + " " + trafficPerFlow * SNR + "\n");
                    numr2rFlows += SNR;
                }
            }
//            out.close();

            System.out.println("Number of r2r flows = " + numr2rFlows + ", number of a2a flows = " + numa2aFlows);
            System.out.print("r2r racks: ");
            for (int r : r2rRacks) {
                System.out.print(r + " ");
            }
            System.out.println();
//        }
//        catch (Exception e)
//        {
//            System.err.println("TestProgram Error: " + e.getMessage());
//            e.printStackTrace();
//        }
    }

    public void RandomPermutationPairs(int size)
    {
        System.out.println("Random permutation flows");

        int screw;
        ArrayList<TrafficPair> ls;
        do
        {
            screw=0;
            ls=new ArrayList<TrafficPair>();
            for(int i=0; i<size; i++)
            {
                ls.add(new TrafficPair(i, i));
            }
            for(int i=0; i<size; i++)
            {
                int ok=0;
                int k=0;
                int cnt=0;
                do
                {
                    //choose a shift in [0,size-1-i]
                    k = topology.rand.nextInt(size-i);
                    //check if we should swap i and i+k
                    int r;
                    if(topology.svrToSwitch(i) != topology.svrToSwitch(ls.get(i+k).to))
                    {
                        ok = 1;
                    }
                    //System.out.println(i + " " + ls.get(i+k) + " " + i/serverport + " " + ls.get(i+k)/serverport);
                    cnt++;
                    if(cnt>500)
                    {
                        screw=1;
                        ok=1;
                    }
                }
                while(ok==0);
                //swap i's value and i+k's value
                int buf=ls.get(i).to;
                ls.set(i, new TrafficPair(i, ls.get(i+k).to));
                ls.set(i+k, new TrafficPair(i+k, buf));
            }
        }
        while(screw==1);

        for (int i = 0; i < ls.size(); i++) {
            int from = ls.get(i).from;
            int to=ls.get(i).to;

            int fromsw=topology.svrToSwitch(from);
            int tosw=topology.svrToSwitch(to);

            // I'm counting only number of connections
            if (fromsw == tosw) continue;
            switchLevelMatrix[fromsw][tosw] += trafficPerFlow;
        }
    }

    public void TrafficGenPermutationRackLevel()
    {
        System.out.println("Rack-level permutation TM");

        boolean[] receivers = new boolean[numSwitches];
        for (int i=0; i<numSwitches; i++) {
            int numServersAttached = topology.adjacencyList[i].size();
            while (true) {
                int dst = topology.rand.nextInt(numSwitches);
                if (dst == i) continue;
                if (!receivers[dst]) {
                    receivers[dst] = true;
                    switchLevelMatrix[i][dst] = trafficPerFlow*numServersAttached;
                    break;
                }
            }
        }

//        try {
//            FileWriter fstream = new FileWriter("resultfiles/tm.txt");
//            BufferedWriter out = new BufferedWriter(fstream);
//            for (int ii = numSwitches - 1; ii >= 0; ii--) {
//                out.write(ii + "\t");
//                for (int jj = 0; jj < numSwitches; jj++) {
//                    if (ii == jj) {
//                        out.write(0 + "\t");
//                    } else {
//                        out.write(switchLevelMatrix[ii][jj] + "\t");
//                    }
//                }
//                out.write("\n");
//            }
//
//            out.write("\t");
//            for (int jj = 0; jj < numSwitches; jj++) {
//                out.write(jj + "\t");
//            }
//            out.write("\n");
//            out.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    public void TrafficGenPermutationServerLevel()
    {
        System.out.println("Server-level permutation TM");
        int countThreshold = 1000000;
        int numFlows = 0;

        boolean[] receivers = new boolean[numServers];
        for (int i=0; i<numServers; i++) {
            int senderRack = topology.svrToSwitch(i);
            int count = 0;
            while (true) {
                count++;
                if (count > countThreshold) { // Approximate server-level permutation; can have really unfortunate situations where i=3071 but the only false server is 3068
                    System.out.println("count=" + count);
                    break;
                }
                int dst = topology.rand.nextInt(numServers);
                int receiverRack = topology.svrToSwitch(dst);
                if (senderRack == receiverRack) continue;
                if (!receivers[dst]) {
                    receivers[dst] = true;
                    switchLevelMatrix[senderRack][receiverRack] += trafficPerFlow;
                    numFlows++;
                    break;
                }
            }
        }

        System.out.println("Number of flows = " + numFlows);

//        try {
//            FileWriter fstream = new FileWriter("resultfiles/tm.txt");
//            BufferedWriter out = new BufferedWriter(fstream);
//            for (int ii = numSwitches - 1; ii >= 0; ii--) {
//                out.write(ii + "\t");
//                for (int jj = 0; jj < numSwitches; jj++) {
//                    if (ii == jj) {
//                        out.write(0 + "\t");
//                    } else {
//                        out.write(switchLevelMatrix[ii][jj] + "\t");
//                    }
//                }
//                out.write("\n");
//            }
//
//            out.write("\t");
//            for (int jj = 0; jj < numSwitches; jj++) {
//                out.write(jj + "\t");
//            }
//            out.write("\n");
//            out.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    public void MaxWeightPairs(String filename){
        System.out.println ("Max weight pairs from file " + filename);

        try
        {
            FileInputStream fstream = new FileInputStream(filename);
            BufferedReader br = new BufferedReader(new InputStreamReader(fstream));

            String strLine;

            while ((strLine = br.readLine()) != null)   {
                String[] matchedNodes = strLine.split(" ");
                int svr1 = Integer.parseInt(matchedNodes[0]);
                int svr2 = Integer.parseInt(matchedNodes[1]);

                int src_sw = topology.svrToSwitch(svr1);
                int dst_sw = topology.svrToSwitch(svr2);
                if (src_sw == dst_sw) continue;
                switchLevelMatrix[src_sw][dst_sw] += trafficPerFlow;

                System.out.println (svr1 + " " + svr2);
            }
            br.close();
        }
        catch (Exception e)
        {
            System.err.println("Max Weight Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void TrafficGenRacktoRackHardCoding(int[] numServersPerSwitch) {
        System.out.println("Rack to rack hard coding.");

        HashSet<Integer> sourceServers = new HashSet<>();
        for (int i=1344; i<=1391; i++) {
            sourceServers.add(i);
        }
        HashSet<Integer> destinationServers = new HashSet<>();
        for (int i=2640; i<=2687; i++) {
            destinationServers.add(i);
        }

        for (int srcServer : sourceServers) {
            for (int dstServer : destinationServers) {
                int srcSw = topology.svrToSwitch(srcServer);
                int dstSw = topology.svrToSwitch(dstServer);
//                System.out.println("source: server " + srcServer + " in rack " + srcSw);
//                System.out.println("destination: server " + dstServer + " in rack " + dstSw);
                switchLevelMatrix[srcSw][dstSw] += trafficPerFlow;
            }
        }
    }

    public void TrafficGenCsSkewedHardCoding() {
        System.out.println("CsSkewed Hard Coding");

//        int[] sender_starts = new int[] {1344,2640,2304,2880,96,1728,528,1920,2448,2112,2256,720,1824,432,1440,2592};
//        int[] sender_ends = new int[] {1391,2687,2351,2927,143,1775,575,1967,2495,2159,2303,767,1871,479,1487,2639};
//        int[] receiver_starts = new int[] {2496,2016,384,240};
//        int[] receiver_ends = new int[] {2543,2063,431,287};
        int[] receiver_starts = new int[] {1344,2640,2304,2880,96,1728,528,1920,2448,2112,2256,720,1824,432,1440,2592};
        int[] receiver_ends = new int[] {1391,2687,2351,2927,143,1775,575,1967,2495,2159,2303,767,1871,479,1487,2639};
        int[] sender_starts = new int[] {2496,2016,384,240};
        int[] sender_ends = new int[] {2543,2063,431,287};
        ArrayList<Integer> senders = new ArrayList<>();
        ArrayList<Integer> receivers = new ArrayList<>();
        for (int i=0; i<sender_starts.length; i++) {
            for (int j=sender_starts[i]; j<=sender_ends[i]; j++) {
                senders.add(j);
            }
        }
        for (int i=0; i<receiver_starts.length; i++) {
            for (int j=receiver_starts[i]; j<=receiver_ends[i]; j++) {
                receivers.add(j);
            }
        }

        for (int srcServer : senders) {
            for (int dstServer : receivers) {
                int srcSw = topology.svrToSwitch(srcServer);
                int dstSw = topology.svrToSwitch(dstServer);
                if (srcSw == dstSw) continue;
                switchLevelMatrix[srcSw][dstSw] += trafficPerFlow*0.5;
            }
        }

        double maxTraffic = 0;
        for (int i=0; i<numSwitches; i++) {
            for (int j=0; j<numSwitches; j++) {
                maxTraffic = Math.max(maxTraffic, switchLevelMatrix[i][j]);
            }
        }

        double downscale = maxTraffic / trafficCap;
        for (int i=0; i<numSwitches; i++) {
            for (int j=0; j<numSwitches; j++) {
                double original = switchLevelMatrix[i][j];
                switchLevelMatrix[i][j] = original / downscale;
            }
        }
    }

    public void TrafficGenCss16to4HardCoding() {
        System.out.println("16-to-4 Hard Coding");
        int[] receiver_starts = new int[] {0};
        int[] receiver_ends = new int[] {191};
        int[] sender_starts = new int[] {2304};
        int[] sender_ends = new int[] {3071};
        ArrayList<Integer> senders = new ArrayList<>();
        ArrayList<Integer> receivers = new ArrayList<>();
        for (int i=0; i<sender_starts.length; i++) {
            for (int j=sender_starts[i]; j<=sender_ends[i]; j++) {
                senders.add(j);
            }
        }
        for (int i=0; i<receiver_starts.length; i++) {
            for (int j=receiver_starts[i]; j<=receiver_ends[i]; j++) {
                receivers.add(j);
            }
        }

        for (int srcServer : senders) {
            for (int dstServer : receivers) {
                int srcSw = topology.svrToSwitch(srcServer);
                int dstSw = topology.svrToSwitch(dstServer);
                if (srcSw == dstSw) continue;
                switchLevelMatrix[srcSw][dstSw] += trafficPerFlow;
            }
        }

        double maxTraffic = 0;
        for (int i=0; i<numSwitches; i++) {
            for (int j=0; j<numSwitches; j++) {
                maxTraffic = Math.max(maxTraffic, switchLevelMatrix[i][j]);
            }
        }

        double downscale = maxTraffic / trafficCap;
        for (int i=0; i<numSwitches; i++) {
            for (int j=0; j<numSwitches; j++) {
                double original = switchLevelMatrix[i][j];
                switchLevelMatrix[i][j] = original / downscale;
            }
        }
    }

    public void TrafficGenAlltoAllHardCoding() {
        System.out.println("All-to-all flows hard-coding");
        double numFlows = 0;

        for (int srcsvr=0; srcsvr<3072; srcsvr++) {
            for (int dstsvr=0; dstsvr<3072; dstsvr++) {
                int srcSw = topology.svrToSwitch(srcsvr);
                int dstSw = topology.svrToSwitch(dstsvr);
                if (srcSw == dstSw) continue;
                switchLevelMatrix[srcSw][dstSw] += trafficPerFlow;
            }
        }

        double maxTraffic = 0;
        for (int i=0; i<numSwitches; i++) {
            for (int j=0; j<numSwitches; j++) {
                maxTraffic = Math.max(maxTraffic, switchLevelMatrix[i][j]);
            }
        }

        double downscale = maxTraffic / trafficCap;
        System.out.println("downscale = " + downscale);
        for (int i=0; i<numSwitches; i++) {
            for (int j=0; j<numSwitches; j++) {
                double original = switchLevelMatrix[i][j];
                switchLevelMatrix[i][j] = original / downscale;
                numFlows += original/downscale;
            }
        }

        System.out.println("Number of flows = " + numFlows);
    }

    /*
    public void TrafficGenAlltoAllHardCoding2() {
        System.out.println("All-to-all flows hard-coding -- half servers");
        int numFlows = 0;

        for (int srcsvr=0; srcsvr<3072; srcsvr++) {
            if (srcsvr%48<24) continue;
            for (int dstsvr=0; dstsvr<3072; dstsvr++) {
                if (dstsvr%48>=24) continue;

                int srcSw = topology.svrToSwitch(srcsvr);
                int dstSw = topology.svrToSwitch(dstsvr);

                if (srcSw == dstSw) continue;
                switchLevelMatrix[srcSw][dstSw] += trafficPerFlow;
            }
        }

        double maxTraffic = 0;
        for (int i=0; i<numSwitches; i++) {
            for (int j=0; j<numSwitches; j++) {
                maxTraffic = Math.max(maxTraffic, switchLevelMatrix[i][j]);
            }
        }

        double downscale = maxTraffic / trafficCap;
        for (int i=0; i<numSwitches; i++) {
            for (int j=0; j<numSwitches; j++) {
                double original = switchLevelMatrix[i][j];
                switchLevelMatrix[i][j] = original / downscale;
            }
        }

        System.out.println("Number of flows = " + numFlows);
    }
     */

    public void TrafficGenPermutationHardCoding() {
        System.out.println("Permutation hard-coding");
        int numFlows = 0;

        try {
            BufferedReader br = new BufferedReader(new FileReader("trafficfiles/permutation_rack_pairs.txt"));
            ArrayList<Integer> srcracks = new ArrayList<>();
            ArrayList<Integer> dstracks = new ArrayList<>();
            String strLine = "";
            for (int l=0; l<2; l++) {
                strLine = br.readLine();
                StringTokenizer strTok = new StringTokenizer(strLine);
                for (int i = 0; i < 64; i++) {
                    if (l==0) {
                        srcracks.add(Integer.parseInt(strTok.nextToken()));
                    } else if (l==1) {
                        dstracks.add(Integer.parseInt(strTok.nextToken()));
                    }
                }
            }
            br.close();

            for (int r=0; r<64; r++) {
                int srcrack = srcracks.get(r);
                int dstrack = dstracks.get(r);
                for (int m=0; m<48; m++) {
                    for (int n=0; n<48; n++) {
                        int srcsvr = srcrack*48+m;
                        int dstsvr = dstrack*48+n;

                        int srcSw = topology.svrToSwitch(srcsvr);
                        int dstSw = topology.svrToSwitch(dstsvr);
                        if (srcSw == dstSw) continue;
                        switchLevelMatrix[srcSw][dstSw] += trafficPerFlow;
                    }
                }
            }

            double maxTraffic = 0;
            for (int i = 0; i < numSwitches; i++) {
                for (int j = 0; j < numSwitches; j++) {
                    maxTraffic = Math.max(maxTraffic, switchLevelMatrix[i][j]);
                }
            }

            double downscale = maxTraffic / trafficCap;
            for (int i = 0; i < numSwitches; i++) {
                for (int j = 0; j < numSwitches; j++) {
                    double original = switchLevelMatrix[i][j];
                    switchLevelMatrix[i][j] = original / downscale;
                }
            }
        } catch(Exception e){
            e.printStackTrace();
        }

        System.out.println("Number of flows = " + numFlows);
    }

    /*
    public void TrafficGenPermutationHardCoding2() {
        System.out.println("Permutation hard-coding -- half servers");
        int numFlows = 0;

        try {
            BufferedReader br = new BufferedReader(new FileReader("trafficfiles/permutation_rack_pairs.txt"));
            ArrayList<Integer> srcracks = new ArrayList<>();
            ArrayList<Integer> dstracks = new ArrayList<>();
            String strLine = "";
            for (int l=0; l<2; l++) {
                strLine = br.readLine();
                StringTokenizer strTok = new StringTokenizer(strLine);
                for (int i = 0; i < 64; i++) {
                    if (l==0) {
                        srcracks.add(Integer.parseInt(strTok.nextToken()));
                    } else if (l==1) {
                        dstracks.add(Integer.parseInt(strTok.nextToken()));
                    }
                }
            }
            br.close();

            for (int r=0; r<64; r++) {
                int srcrack = srcracks.get(r);
                int dstrack = dstracks.get(r);
                for (int m=0; m<24; m++) {
                    for (int n=0; n<24; n++) {
                        int srcsvr = srcrack*48+m;
                        int dstsvr = dstrack*48+n;

                        int srcSw = topology.svrToSwitch(srcsvr);
                        int dstSw = topology.svrToSwitch(dstsvr);

                        if (srcSw == dstSw) continue;
                        switchLevelMatrix[srcSw][dstSw] += trafficPerFlow;
                    }
                }
            }

            double maxTraffic = 0;
            for (int i = 0; i < numSwitches; i++) {
                for (int j = 0; j < numSwitches; j++) {
                    maxTraffic = Math.max(maxTraffic, switchLevelMatrix[i][j]);
                }
            }

            double downscale = maxTraffic / trafficCap;
            for (int i = 0; i < numSwitches; i++) {
                for (int j = 0; j < numSwitches; j++) {
                    double original = switchLevelMatrix[i][j];
                    switchLevelMatrix[i][j] = original / downscale;
                }
            }
        } catch(Exception e){
            e.printStackTrace();
        }

        System.out.println("Number of flows = " + numFlows);
    }
     */

    private void printSwitchLevelMatrix(String filename) {
        try {
            FileWriter fstream = new FileWriter(filename);
            BufferedWriter out = new BufferedWriter(fstream);
            for (int ii = numSwitches - 1; ii >= 0; ii--) {
                out.write(ii + "\t");
                for (int jj = 0; jj < numSwitches; jj++) {
                    out.write(switchLevelMatrix[ii][jj] + "\t");
                }
                out.write("\n");
            }

            out.write("\t");
            for (int jj = 0; jj < numSwitches; jj++) {
                out.write(jj + "\t");
            }
            out.write("\n");
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void printServerTrafficMatrix(String filename, int from, int to) {
        try {
            FileWriter fstream = new FileWriter(filename);
            BufferedWriter out = new BufferedWriter(fstream);
            for (int ii = to - 1; ii >= from; ii--) {
                out.write(ii + "\t");
                for (int jj = from; jj < to; jj++) {
                    out.write(serverTrafficMatrix[ii][jj] + "\t");
                }
                out.write("\n");
            }

            out.write("\t");
            for (int jj = from; jj < to; jj++) {
                out.write(jj + "\t");
            }
            out.write("\n");
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void generateTrafficAllServerToAllServer() {
        System.out.println("Generate traffic all server to all server.");
        double unitTraffic = 1;
        double totalTraffic = 0;

        for (int srcsvr=0; srcsvr<numServers; srcsvr++) {
            for (int dstsvr=0; dstsvr<numServers; dstsvr++) {
                int srcSw = topology.svrToSwitch(srcsvr);
                int dstSw = topology.svrToSwitch(dstsvr);
                if (srcSw == dstSw) continue;
                switchLevelMatrix[srcSw][dstSw] += unitTraffic;
                totalTraffic += unitTraffic;
            }
        }

//        printSwitchLevelMatrix("resultfiles/a2a_tm.txt");

        System.out.println("Total traffic = " + totalTraffic);
    }

    public void generateTrafficRRGUniform(Graph rrgnet) {
        System.out.println("Generate traffic RRG uniform.");
        double unitTraffic = 1;
        double totalTraffic = 0;

        for (int srcsw=0; srcsw<rrgnet.noNodes; srcsw++) {
            for (int dstsw=0; dstsw<rrgnet.noNodes; dstsw++) {
                int[] srcsvrs = rrgnet.getServersForSwitch(srcsw);
                int srcnumlinks = rrgnet.numPorts - srcsvrs.length;
                HashSet<Integer> mysrcsvrs = new HashSet<>();
                while (mysrcsvrs.size() < srcnumlinks) {
                    mysrcsvrs.add(rrgnet.rand.nextInt(srcsvrs.length));
                }

                int[] dstsvrs = rrgnet.getServersForSwitch(dstsw);
                int dstnumlinks = rrgnet.numPorts - dstsvrs.length;
                HashSet<Integer> mydstsvrs = new HashSet<>();
                while (mydstsvrs.size() < dstnumlinks) {
                    mydstsvrs.add(rrgnet.rand.nextInt(dstsvrs.length));
                }

                for (int srcsvrindex : mysrcsvrs) {
                    for (int dstsvrindex : mydstsvrs) {
                        int srcsvr = srcsvrs[srcsvrindex];
                        int dstsvr = dstsvrs[dstsvrindex];

                        int mysrcsw = topology.svrToSwitch(srcsvr);
                        int mydstsw = topology.svrToSwitch(dstsvr);
                        if (mysrcsw != mydstsw) {
                            switchLevelMatrix[mysrcsw][mydstsw] += unitTraffic;
                            totalTraffic += unitTraffic;
                        }
                    }
                }
            }
        }

//        printSwitchLevelMatrix("resultfiles/rrg_uniform_tm.txt");
//        System.exit(0);

        System.out.println("Total traffic = " + totalTraffic);
    }

    private void printPermutationSequence(int[] dstsws, int offset, String filename) {
        try {
            FileWriter fstream = new FileWriter(filename);
            BufferedWriter out = new BufferedWriter(fstream);
            for (int i=0; i< dstsws.length; i++) {
                out.write((i+offset) + "\t" + (dstsws[i]+offset) + "\n");
            }
            out.write("\n");
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void generateTrafficRackPermutation(Graph lsnet) {
        System.out.println("Generate traffic rack permutation.");
        double unitTraffic = 1;
        double totalTraffic = 0;

        int maxLeafspineSwitchWithServers = -1, minLeafspineSwitchWithServers = Integer.MAX_VALUE;
        for (int s=0; s<lsnet.noNodes; s++) {
            if (lsnet.weightEachNode[s] > 0) {
                maxLeafspineSwitchWithServers = Math.max(maxLeafspineSwitchWithServers,s);
                minLeafspineSwitchWithServers = Math.min(minLeafspineSwitchWithServers, s);
            }
        }
        int numLeafspineSwitchesWithServers = maxLeafspineSwitchWithServers - minLeafspineSwitchWithServers + 1;

        int[] dstsws = new int[numLeafspineSwitchesWithServers];
        boolean[] dsthassender = new boolean[numLeafspineSwitchesWithServers];
        for (int srcsw=0; srcsw<numLeafspineSwitchesWithServers; srcsw++) {
            while (true) {
                int dstsw = lsnet.rand.nextInt(numLeafspineSwitchesWithServers);
                if (dstsw == srcsw) continue;
                if (dsthassender[dstsw]) continue;
                dsthassender[dstsw] = true;
                dstsws[srcsw] = dstsw;
                break;
            }
        }

//        printPermutationSequence(dstsws, minLeafspineSwitchWithServers, "resultfiles/rperm_sequence.txt");

        for (int i=0; i<numLeafspineSwitchesWithServers; i++) {
            int srcsw = i+minLeafspineSwitchWithServers;
            int dstsw = dstsws[i] + minLeafspineSwitchWithServers;

            int[] srcsvrs = lsnet.getServersForSwitch(srcsw);
            int[] dstsvrs = lsnet.getServersForSwitch(dstsw);
            for (int srcsvr : srcsvrs) {
                if (srcsvr>=topology.totalWeight) continue;
                for (int dstsvr : dstsvrs) {
                    if (dstsvr>=topology.totalWeight) continue;
                    int mysrcsw = topology.svrToSwitch(srcsvr);
                    int mydstsw = topology.svrToSwitch(dstsvr);
                    if (mysrcsw != mydstsw) {
                        switchLevelMatrix[mysrcsw][mydstsw] += unitTraffic;
                        totalTraffic += unitTraffic;
                    }
                }
            }
        }

//        printSwitchLevelMatrix("resultfiles/rperm_tm.txt");
//        System.exit(0);

        System.out.println("Total traffic = " + totalTraffic);
    }

    public void generateTrafficRRGRackPermutation(Graph rrgnet) {
        System.out.println("Generate traffic rrg rack permutation.");
        double unitTraffic = 1;
        double totalTraffic = 0;

        int[] dstsws = new int[rrgnet.noNodes];
        boolean[] dsthassender = new boolean[rrgnet.noNodes];
        for (int srcsw=0; srcsw<rrgnet.noNodes; srcsw++) {
            while (true) {
                int dstsw = rrgnet.rand.nextInt(rrgnet.noNodes);
                if (dstsw == srcsw) continue;
                if (dsthassender[dstsw]) continue;
                dsthassender[dstsw] = true;
                dstsws[srcsw] = dstsw;
                break;
            }
        }

        for (int srcsw=0; srcsw<rrgnet.noNodes; srcsw++) {
            int dstsw = dstsws[srcsw];

            int[] srcsvrs = rrgnet.getServersForSwitch(srcsw);
            int[] dstsvrs = rrgnet.getServersForSwitch(dstsw);
            for (int srcsvr : srcsvrs) {
                for (int dstsvr : dstsvrs) {
                    int mysrcsw = topology.svrToSwitch(srcsvr);
                    int mydstsw = topology.svrToSwitch(dstsvr);
                    if (mysrcsw != mydstsw) {
                        switchLevelMatrix[mysrcsw][mydstsw] += unitTraffic;
                        totalTraffic += unitTraffic;
                    }
                }
            }
        }

//        printSwitchLevelMatrix("resultfiles/rrg_rperm_tm.txt");
//        System.exit(0);

        System.out.println("Total traffic = " + totalTraffic);
    }

    public void generateTrafficServerPermutation() {
        System.out.println("Generate traffic server permutation.");
        double unitTraffic = 1;
        double totalTraffic = 0;

        int numServers = topology.totalWeight;
        int[] dstsvrs = new int[numServers];
        boolean[] dsthassender = new boolean[numServers];
        for (int srcsvr=0; srcsvr<numServers; srcsvr++) {
            while (true) {
                int dstsvr = topology.rand.nextInt(numServers);
                if (dstsvr == srcsvr) continue;
                if (dsthassender[dstsvr]) continue;
                dsthassender[dstsvr] = true;
                dstsvrs[srcsvr] = dstsvr;
                break;
            }
        }

        for (int i=0; i<numServers; i++) {
            int srcsvr = i;
            int dstsvr = dstsvrs[i];
            int mysrcsw = topology.svrToSwitch(srcsvr);
            int mydstsw = topology.svrToSwitch(dstsvr);
            if (mysrcsw != mydstsw) {
                switchLevelMatrix[mysrcsw][mydstsw] += unitTraffic;
                totalTraffic += unitTraffic;
            }
        }

        System.out.println("Total traffic = " + totalTraffic);
    }

    public void generateTrafficARackToBRack(int a, int b, Graph lsnet) {
        System.out.println("Generate traffic a rack to b rack: a=" + a + ", b=" + b + ".");
        double unitTraffic = 1;
        double totalTraffic = 0;

        ArrayList<Integer> srcswslist = new ArrayList<>();
        ArrayList<Integer> dstswslist = new ArrayList<>();
        while (srcswslist.size() < a) {
            int srcsw = lsnet.rand.nextInt(lsnet.noNodes);
            if (srcswslist.contains(srcsw)) continue;
            srcswslist.add(srcsw);
        }
        // It is possible to have intra-rack traffic.
        while (dstswslist.size() < b) {
            int dstsw = lsnet.rand.nextInt(lsnet.noNodes);
            if (dstswslist.contains(dstsw)) continue;
            dstswslist.add(dstsw);
        }
        System.out.println(srcswslist);
        System.out.println(dstswslist);

        for (int srcsw : srcswslist) {
            for (int dstsw : dstswslist) {
                int[] srcsvrs = lsnet.getServersForSwitch(srcsw);
                int[] dstsvrs = lsnet.getServersForSwitch(dstsw);
                for (int srcsvr : srcsvrs) {
                    if (srcsvr >= topology.totalWeight) continue;
                    for (int dstsvr : dstsvrs) {
                        if (dstsvr >= topology.totalWeight) continue;
                        int mysrcsw = topology.svrToSwitch(srcsvr);
                        int mydstsw = topology.svrToSwitch(dstsvr);
                        if (mysrcsw != mydstsw) {
                            switchLevelMatrix[mysrcsw][mydstsw] += unitTraffic;
                            totalTraffic += unitTraffic;
                        }
                    }
                }
            }
        }

//        printSwitchLevelMatrix("resultfiles/serverlevelmatrix.txt");

        System.out.println("Total traffic = " + totalTraffic);

//        setUpServerTrafficMatrix();
    }

    public void generateTrafficClusterX(String cluster) {
        System.out.println("Generate traffic cluster " + cluster + ".");
        double totalTraffic = 0;
        double scaledown = 1000;
        int solve_starttime = timeframeStart;
        int solve_endtime = timeframeEnd;

        String file_prefix = "../DRing/src/emp/datacentre/trafficfiles/cluster_" + cluster + "/traffic/traffic_64racks";
        String[] file_suffix = {};
        if (cluster.equals("a")) {
            file_suffix = new String[]{"_0_273"};
        } else if (cluster.equals("b")) {
            file_suffix = new String[]{"_0_500","_500_1000","_1000_1500","_1500_2000","_2000_2500","_2500_2900"};
        } else if (cluster.equals("c")) {
            file_suffix = new String[]{"_0_273"};
        }

        try {
            BufferedReader br;
            String strLine = "";

            for (int i=0; i<file_suffix.length; i++) {
                String trafficfile = file_prefix + file_suffix[i];
                br = new BufferedReader(new FileReader(trafficfile));
                while ((strLine = br.readLine()) != null){
                    StringTokenizer strTok = new StringTokenizer(strLine);
                    int timestamp = Integer.parseInt(strTok.nextToken());
                    double traffic = Double.parseDouble(strTok.nextToken());
                    traffic /= scaledown;
                    int src_svr = Integer.parseInt(strTok.nextToken());
                    int dst_svr = Integer.parseInt(strTok.nextToken());
                    if (src_svr>=topology.totalWeight | dst_svr>=topology.totalWeight) continue;
                    int src_sw = topology.svrToSwitch(src_svr);
                    int dst_sw = topology.svrToSwitch(dst_svr);
                    if (src_sw == dst_sw) continue;

                    if (timestamp >= solve_endtime) break;
                    if (timestamp >= solve_starttime && timestamp < solve_endtime) {
                        switchLevelMatrix[src_sw][dst_sw] += traffic;
                        totalTraffic += traffic;
                    }
                }
                br.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Total traffic = " + totalTraffic);
    }

    public void generateTrafficMix(int n, int t, Graph lsnet) {
        System.out.println("Generate traffic mix.");
        double unitTraffic = 1;
        double totalTraffic = 0;

        int maxLeafspineRack = -1, minLeafspineRack = Integer.MAX_VALUE;
        for (int s=0; s<lsnet.noNodes; s++) {
            if (lsnet.weightEachNode[s] > 0) {
                maxLeafspineRack = Math.max(maxLeafspineRack,s);
                minLeafspineRack = Math.min(minLeafspineRack, s);
            }
        }
        int numLeafspineRacks = maxLeafspineRack - minLeafspineRack + 1;

        HashSet<Integer> heavyRacks = new HashSet<>();
        while (heavyRacks.size() < n) {
            heavyRacks.add(lsnet.rand.nextInt(numLeafspineRacks)+minLeafspineRack);
        }
        System.out.println("Number of heavy racks = " + heavyRacks.size());
        HashSet<Integer> lightRacks = new HashSet<>();
        for (int s=minLeafspineRack; s<=maxLeafspineRack; s++) {
            if (!heavyRacks.contains(s)) {
                lightRacks.add(s);
            }
        }
        System.out.println("Number of light racks = " + lightRacks.size());

        HashSet<Integer> heavyServers = new HashSet<>();
        for (int hr : heavyRacks) {
            int[] servers = lsnet.getServersForSwitch(hr);
            for (int svr : servers) {
                heavyServers.add(svr);
            }
        }
        System.out.println("Number of heavy servers = " + heavyServers.size());
        HashSet<Integer> lightServers = new HashSet<>();
        for (int lr : lightRacks) {
            int[] servers = lsnet.getServersForSwitch(lr);
            for (int svr : servers) {
                lightServers.add(svr);
            }
        }
        System.out.println("Number of light servers = " + lightServers.size());

        for (int srcsvr=0; srcsvr<heavyServers.size(); srcsvr++) {
            for (int dstsvr=0; dstsvr<heavyServers.size(); dstsvr++) {
                if (srcsvr>=topology.totalWeight | dstsvr>=topology.totalWeight) continue;
                int srcsw = topology.svrToSwitch(srcsvr);
                int dstsw = topology.svrToSwitch(dstsvr);
                if (srcsw == dstsw) continue;
                switchLevelMatrix[srcsw][dstsw] += unitTraffic*t;
                totalTraffic += unitTraffic*t;
            }
        }

        for (int srcsvr=0; srcsvr<lightServers.size(); srcsvr++) {
            for (int dstsvr=0; dstsvr<lightServers.size(); dstsvr++) {
                if (srcsvr>=topology.totalWeight | dstsvr>=topology.totalWeight) continue;
                int srcsw = topology.svrToSwitch(srcsvr);
                int dstsw = topology.svrToSwitch(dstsvr);
                if (srcsw == dstsw) continue;
                switchLevelMatrix[srcsw][dstsw] += unitTraffic;
                totalTraffic += unitTraffic;
            }
        }

        /*
        for (int srcsw : heavyRacks) {
            for (int dstsw : heavyRacks) {
                int[] srcsvrs = lsnet.getServersForSwitch(srcsw);
                int[] dstsvrs = lsnet.getServersForSwitch(dstsw);
                for (int srcsvr : srcsvrs) {
                    if (srcsvr>=topology.totalWeight) continue;
                    for (int dstsvr : dstsvrs) {
                        if (dstsvr>=topology.totalWeight) continue;
                        int mysrcsw = topology.svrToSwitch(srcsvr);
                        int mydstsw = topology.svrToSwitch(dstsvr);
                        if (mysrcsw != mydstsw) {
                            switchLevelMatrix[mysrcsw][mydstsw] += unitTraffic * t;
                            totalTraffic += unitTraffic * t;
                        }
                    }
                }
            }
        }

        for (int srcsw : lightRacks) {
            for (int dstsw : lightRacks) {
                int[] srcsvrs = lsnet.getServersForSwitch(srcsw);
                int[] dstsvrs = lsnet.getServersForSwitch(dstsw);
                for (int srcsvr : srcsvrs) {
                    if (srcsvr>=topology.totalWeight) continue;
                    for (int dstsvr : dstsvrs) {
                        if (dstsvr>=topology.totalWeight) continue;
                        int mysrcsw = topology.svrToSwitch(srcsvr);
                        int mydstsw = topology.svrToSwitch(dstsvr);
                        if (mysrcsw != mydstsw) {
                            switchLevelMatrix[mysrcsw][mydstsw] += unitTraffic;
                            totalTraffic += unitTraffic;
                        }
                    }
                }
            }
        }

         */

        System.out.println("Total traffic = " + totalTraffic);
    }

    private void printArray(int[] arr) {
        for (int i=0; i<arr.length; i++) {
            System.out.print(arr[i] + " ");
        }
        System.out.println();
    }

    public void generateSwitchServerTrafficAllServerToAllServer() {
        System.out.println("Generate switch & server traffic all server to all server.");
        double unitTraffic = 1;
        double totalTraffic = 0;

        for (int srcsvr=0; srcsvr<numServers; srcsvr++) {
            for (int dstsvr=0; dstsvr<numServers; dstsvr++) {
                int mysrcsw = topology.svrToSwitch(srcsvr);
                int mydstsw = topology.svrToSwitch(dstsvr);
                serverLevelMatrix[srcsvr][dstsvr] += unitTraffic;
                switchLevelMatrix[mysrcsw][mydstsw] += unitTraffic;
                totalTraffic += unitTraffic;
            }
        }
        System.out.println("Total traffic = " + totalTraffic);

//        printSwitchLevelMatrix("resultfiles/switchleveltraffic_a2a.txt");
        writeServerLevelMatrix();
        //writeSwitchLevelMatrix();
    }

    public void generateSwitchServerTrafficARackToBRackFromFile(int a, int b, String trafficfile, LeafSpine lsnet) {
        int configfilenumber = Integer.parseInt(trafficfile);
        String s2strafficfile = "/home/annzhou/WeightTuning/trafficfiles/s2s_"+a+"_"+b+"_0_"+configfilenumber;
        System.out.println("Generate switch & server traffic a rack to b rack from file: a=" + a + ", b=" + b + ", file="+s2strafficfile);
        double unitTraffic = 1;
        double totalTraffic = 0;

        ArrayList<Integer> srcswslist = new ArrayList<>();
        ArrayList<Integer> dstswslist = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(s2strafficfile));
            String strLine = "";
            while ((strLine = br.readLine()) != null) {
                StringTokenizer strTok = new StringTokenizer(strLine);
                String token = strTok.nextToken();
                int racknumber = Integer.parseInt(token);
                if (srcswslist.size() < a) {
                    srcswslist.add(racknumber);
                } else {
                    dstswslist.add(racknumber);
                }
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (srcswslist.size() != a) System.err.println("File " + s2strafficfile + " has " + srcswslist.size() + " sending racks; a=" + a);
        if (dstswslist.size() != b) System.err.println("File " + s2strafficfile + " has " + dstswslist.size() + " receiving racks; b=" + b);

        System.out.println(srcswslist);
        System.out.println(dstswslist);

        for (int srcsw : srcswslist) {
            int[] srcsvrs = lsnet.getServersForSwitch(srcsw);
            for (int dstsw : dstswslist) {
                int[] dstsvrs = lsnet.getServersForSwitch(dstsw);

                for (int srcsvr : srcsvrs) {
                    if (srcsvr >= topology.totalWeight) continue;
                    for (int dstsvr : dstsvrs) {
                        if (dstsvr >= topology.totalWeight) continue;

                        int mysrcsw = topology.svrToSwitch(srcsvr);
                        int mydstsw = topology.svrToSwitch(dstsvr);

                        serverLevelMatrix[srcsvr][dstsvr] += unitTraffic;
                        switchLevelMatrix[mysrcsw][mydstsw] += unitTraffic;
                        totalTraffic += unitTraffic;
                    }
                }
            }
        }
        System.out.println("Total traffic = " + totalTraffic);

//        writeServerLevelMatrix();
//        writeSwitchLevelMatrix();
    }

    public void generateSwitchServerTrafficARackToBRackFromFileFlat(int a, int b, String trafficfile) {
        int basetrafficpattern = Integer.parseInt(trafficfile.split(" ")[0]);
        int configfilenumber = Integer.parseInt(trafficfile.split(" ")[1]);
        String s2strafficfile = "/home/annzhou/WeightTuning/trafficfiles/s2s_"+a+"_"+b+"_0_"+configfilenumber;
        System.out.println("Generate switch & server traffic a rack to b rack from file: a=" + a + ", b=" + b + ", file="+s2strafficfile+", basetrafficpattern="+basetrafficpattern);
        double unitTraffic = 1;
        double totalTraffic = 0;

        ArrayList<Integer> srcswslist = new ArrayList<>();
        ArrayList<Integer> dstswslist = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(s2strafficfile));
            String strLine = "";
            while ((strLine = br.readLine()) != null) {
                StringTokenizer strTok = new StringTokenizer(strLine);
                String token = strTok.nextToken();
                int racknumber = Integer.parseInt(token);
                if (srcswslist.size() < a) {
                    srcswslist.add(racknumber);
                } else {
                    dstswslist.add(racknumber);
                }
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (srcswslist.size() != a) System.err.println("File " + s2strafficfile + " has " + srcswslist.size() + " sending racks; a=" + a);
        if (dstswslist.size() != b) System.err.println("File " + s2strafficfile + " has " + dstswslist.size() + " receiving racks; b=" + b);

        System.out.println(srcswslist);
        System.out.println(dstswslist);

        if (basetrafficpattern == 0) { //leafspine
            int lsservers = 48;
            for (int srcsw : srcswslist) {
                for (int dstsw : dstswslist) {
                    for (int srcsvr=srcsw*lsservers; srcsvr<(srcsw+1)*lsservers; srcsvr++) {
                        for (int dstsvr=dstsw*lsservers; dstsvr<(dstsw+1)*lsservers; dstsvr++) {
                            if (srcsvr>=topology.totalWeight || dstsvr>=topology.totalWeight) continue;

                            int mysrcsw = topology.svrToSwitch(srcsvr);
                            int mydstsw = topology.svrToSwitch(dstsvr);

                            serverLevelMatrix[srcsvr][dstsvr] += unitTraffic;
                            switchLevelMatrix[mysrcsw][mydstsw] += unitTraffic;
                            totalTraffic += unitTraffic;
                        }
                    }
                }
            }
        } else if (basetrafficpattern == 1 || basetrafficpattern == 2) {
            String mappingfilename="";
            if (basetrafficpattern == 1) { //rrg
                mappingfilename = "/home/annzhou/DRing/src/emp/datacentre/topoflowsfiles/sw-svr-mapping_rrg.txt";
            } else if (basetrafficpattern == 2) { //dring
                mappingfilename = "/home/annzhou/DRing/src/emp/datacentre/topoflowsfiles/sw-svr-mapping_dring.txt";
            } else {
                System.err.println("basetrafficpattern " + basetrafficpattern + " not recognized.");
            }

            HashMap<Integer, Integer> serverToSwitchMapping = new HashMap<>();
            try {
                BufferedReader br = new BufferedReader(new FileReader(mappingfilename));
                String strLine = "";
                while ((strLine = br.readLine()) != null) {
                    StringTokenizer strTok = new StringTokenizer(strLine);
                    int thisswitch = Integer.parseInt(strTok.nextToken());
                    int thisserver = Integer.parseInt(strTok.nextToken());
                    serverToSwitchMapping.put(thisserver, thisswitch);
                }
                br.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            for (int srcsw : srcswslist) {
                for (int dstsw : dstswslist) {
                    ArrayList<Integer> srcservers = new ArrayList<>();
                    ArrayList<Integer> dstservers = new ArrayList<>();
                    for (int s=0; s<topology.totalWeight; s++) {
                        int r = serverToSwitchMapping.get(s);
                        if (r == srcsw) srcservers.add(s);
                        if (r == dstsw) dstservers.add(s);
                    }

                    for (int srcsvr : srcservers) {
                        for (int dstsvr : dstservers) {
                            int mysrcsw = topology.svrToSwitch(srcsvr);
                            int mydstsw = topology.svrToSwitch(dstsvr);

                            serverLevelMatrix[srcsvr][dstsvr] += unitTraffic;
                            switchLevelMatrix[mysrcsw][mydstsw] += unitTraffic;
                            totalTraffic += unitTraffic;
                        }
                    }
                }
            }
        }

        System.out.println("Total traffic = " + totalTraffic);

//        writeServerLevelMatrix();
//        writeSwitchLevelMatrix();
    }

    public void generateSwitchServerTrafficARackToBRackFromFileServer(int a, int b, String trafficfile) {
        int configfilenumber = Integer.parseInt(trafficfile);
        String s2strafficfile = "/home/annzhou/WeightTuning/trafficfiles/v2v_"+a+"_"+b+"_0_"+configfilenumber;
        System.out.println("Generate switch & server traffic a rack to b rack from file: a=" + a + ", b=" + b + ", file="+s2strafficfile);
        double unitTraffic = 1;
        double totalTraffic = 0;

        ArrayList<Integer> srcswslist = new ArrayList<>();
        ArrayList<Integer> dstswslist = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(s2strafficfile));
            String strLine = "";
            while ((strLine = br.readLine()) != null) {
                StringTokenizer strTok = new StringTokenizer(strLine);
                String token = strTok.nextToken();
                int racknumber = Integer.parseInt(token);
                if (srcswslist.size() < a) {
                    srcswslist.add(racknumber);
                } else {
                    dstswslist.add(racknumber);
                }
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (srcswslist.size() != a) System.err.println("File " + s2strafficfile + " has " + srcswslist.size() + " sending racks; a=" + a);
        if (dstswslist.size() != b) System.err.println("File " + s2strafficfile + " has " + dstswslist.size() + " receiving racks; b=" + b);

        System.out.println(srcswslist);
        System.out.println(dstswslist);

        for (int srcsvr : srcswslist) {
            for (int dstsvr : dstswslist) {
                if (srcsvr>=topology.totalWeight || dstsvr>=topology.totalWeight) continue;

                int mysrcsw = topology.svrToSwitch(srcsvr);
                int mydstsw = topology.svrToSwitch(dstsvr);

                serverLevelMatrix[srcsvr][dstsvr] += unitTraffic;
                switchLevelMatrix[mysrcsw][mydstsw] += unitTraffic;
                totalTraffic += unitTraffic;
            }
        }

        System.out.println("Total traffic = " + totalTraffic);

//        writeServerLevelMatrix();
//        writeSwitchLevelMatrix();
    }

    public void generateSwitchServerTrafficARackToBRackFromFileRandom(int a, int b, String trafficfile) {
        int configfilenumber = Integer.parseInt(trafficfile);
        String s2strafficfile = "/home/annzhou/WeightTuning/trafficfiles/s2s_"+a+"_"+b+"_0_"+configfilenumber;
        System.out.println("Generate switch & server traffic a rack to b rack from file: a=" + a + ", b=" + b + ", file="+s2strafficfile);
        double unitTraffic = 1;
        double totalTraffic = 0;
        int active_servers_per_rack = 36;

        ArrayList<Integer> srcswslist = new ArrayList<>();
        ArrayList<Integer> dstswslist = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(s2strafficfile));
            String strLine = "";
            while ((strLine = br.readLine()) != null) {
                StringTokenizer strTok = new StringTokenizer(strLine);
                String token = strTok.nextToken();
                int racknumber = Integer.parseInt(token);
                if (srcswslist.size() < a) {
                    srcswslist.add(racknumber);
                } else {
                    dstswslist.add(racknumber);
                }
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (srcswslist.size() != a) System.err.println("File " + s2strafficfile + " has " + srcswslist.size() + " sending racks; a=" + a);
        if (dstswslist.size() != b) System.err.println("File " + s2strafficfile + " has " + dstswslist.size() + " receiving racks; b=" + b);

        System.out.println(srcswslist);
        System.out.println(dstswslist);

        String mappingfilename="";
        if (topology.totalWeight == 2988) {
            mappingfilename="/home/annzhou/DRing/src/emp/datacentre/topoflowsfiles/sw-svr-mapping_dring.txt";
        } else if (topology.totalWeight == 3072) {
            mappingfilename="/home/annzhou/DRing/src/emp/datacentre/topoflowsfiles/sw-svr-mapping_rrg.txt";
        } else {
            System.out.println("***Error this topology is not recognized.");
        }
        HashMap<Integer, Integer> serverToSwitchMapping = new HashMap<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(mappingfilename));
            String strLine = "";
            while ((strLine = br.readLine()) != null) {
                StringTokenizer strTok = new StringTokenizer(strLine);
                int thisswitch = Integer.parseInt(strTok.nextToken());
                int thisserver = Integer.parseInt(strTok.nextToken());
                serverToSwitchMapping.put(thisserver, thisswitch);
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (int srcsw : srcswslist) {
            for (int dstsw : dstswslist) {
                ArrayList<Integer> srcservers = new ArrayList<>();
                ArrayList<Integer> dstservers = new ArrayList<>();
                for (int s=0; s<topology.totalWeight; s++) {
                    int r = serverToSwitchMapping.get(s);
                    if (r == srcsw && srcservers.size()<active_servers_per_rack) srcservers.add(s);
                    if (r == dstsw && dstservers.size()<active_servers_per_rack) dstservers.add(s);
                }

                for (int srcsvr : srcservers) {
                    for (int dstsvr : dstservers) {
                        int mysrcsw = topology.svrToSwitch(srcsvr);
                        int mydstsw = topology.svrToSwitch(dstsvr);

                        serverLevelMatrix[srcsvr][dstsvr] += unitTraffic;
                        switchLevelMatrix[mysrcsw][mydstsw] += unitTraffic;
                        totalTraffic += unitTraffic;
                    }
                }
            }
        }

        System.out.println("Total traffic = " + totalTraffic);

        writeServerLevelMatrix();
        //writeSwitchLevelMatrix();
    }

    public void generateSwitchServerTrafficRackPermutation(LeafSpine lsnet) {
        System.out.println("Generate switch & server traffic rack permutation.");
        double unitTraffic = 1;
        double totalTraffic = 0;

        int[] dstsws = new int[lsnet.noNodes]; // the first numSpineSwitches should be unused
        boolean[] dsthassender = new boolean[lsnet.noNodes];
        for (int srcsw=lsnet.getNumSpineSwitches(); srcsw<lsnet.noNodes; srcsw++) {
            while (true) {
                int dstsw = lsnet.generateRandomSwitch();
                if (dstsw == srcsw) continue;
                if (dsthassender[dstsw]) continue;
                dsthassender[dstsw] = true;
                dstsws[srcsw] = dstsw;
                break;
            }
        }

        for (int i=lsnet.getNumSpineSwitches(); i<lsnet.noNodes; i++) {
            int srcsw = i;
            int dstsw = dstsws[i];
            int[] srcsvrs = lsnet.getServersForSwitch(srcsw);
            int[] dstsvrs = lsnet.getServersForSwitch(dstsw);
            for (int srcsvr : srcsvrs) {
                if (srcsvr>=topology.totalWeight) continue;
                for (int dstsvr : dstsvrs) {
                    if (dstsvr>=topology.totalWeight) continue;
                    int mysrcsw = topology.svrToSwitch(srcsvr);
                    int mydstsw = topology.svrToSwitch(dstsvr);
                    serverLevelMatrix[srcsvr][dstsvr] += unitTraffic;
                    switchLevelMatrix[mysrcsw][mydstsw] += unitTraffic;
                    totalTraffic += unitTraffic;
                }
            }
        }
        System.out.println("Total traffic = " + totalTraffic);

        writeServerLevelMatrix();
    }

    public void generateSwitchServerTrafficServerPermutation() {
        System.out.println("Generate switch & server traffic server permutation.");
        double unitTraffic = 1;
        double totalTraffic = 0;

        int numServers = topology.totalWeight;
        int[] dstsvrs = new int[numServers];
        boolean[] dsthassender = new boolean[numServers];
        for (int srcsvr=0; srcsvr<numServers; srcsvr++) {
            while (true) {
                int dstsvr = topology.rand.nextInt(numServers);
                if (dstsvr == srcsvr) continue;
                if (dsthassender[dstsvr]) continue;
                dsthassender[dstsvr] = true;
                dstsvrs[srcsvr] = dstsvr;
                break;
            }
        }

        for (int i=0; i<numServers; i++) {
            int srcsvr = i;
            int dstsvr = dstsvrs[i];
            int mysrcsw = topology.svrToSwitch(srcsvr);
            int mydstsw = topology.svrToSwitch(dstsvr);
            serverLevelMatrix[srcsvr][dstsvr] += unitTraffic;
            switchLevelMatrix[mysrcsw][mydstsw] += unitTraffic;
            totalTraffic += unitTraffic;
        }
        System.out.println("Total traffic = " + totalTraffic);

        writeServerLevelMatrix();
    }

    public void generateSwitchServerTrafficARackToBRack(int a, int b, LeafSpine lsnet) {
        System.out.println("Generate switch & server traffic a rack to b rack: a=" + a + ", b=" + b + ".");
        double unitTraffic = 1;
        double totalTraffic = 0;

        ArrayList<Integer> srcswslist = new ArrayList<>();
        ArrayList<Integer> dstswslist = new ArrayList<>();
        while (srcswslist.size() < a) {
            int srcsw = lsnet.generateRandomSwitch();
            if (srcswslist.contains(srcsw)) continue;
            srcswslist.add(srcsw);
        }
        // It is possible to have intra-rack traffic.
        while (dstswslist.size() < b) {
            int dstsw = lsnet.generateRandomSwitch();
            if (dstswslist.contains(dstsw)) continue;
//            if (srcswslist.contains(dstsw)) continue;
            dstswslist.add(dstsw);
        }
        System.out.println(srcswslist);
        System.out.println(dstswslist);

        for (int srcsw : srcswslist) {
            int[] srcsvrs = lsnet.getServersForSwitch(srcsw);
            for (int dstsw : dstswslist) {
                int[] dstsvrs = lsnet.getServersForSwitch(dstsw);

                for (int srcsvr : srcsvrs) {
                    if (srcsvr >= topology.totalWeight) continue;
                    for (int dstsvr : dstsvrs) {
                        if (dstsvr >= topology.totalWeight) continue;

                        int mysrcsw = topology.svrToSwitch(srcsvr);
                        int mydstsw = topology.svrToSwitch(dstsvr);

                        serverLevelMatrix[srcsvr][dstsvr] += unitTraffic;
                        switchLevelMatrix[mysrcsw][mydstsw] += unitTraffic;
                        totalTraffic += unitTraffic;
                    }
                }
            }
        }
        System.out.println("Total traffic = " + totalTraffic);

        writeServerLevelMatrix();
    }

    public void generateSwitchServerTrafficClusterX(String cluster) {
        System.out.println("Generate switch & server traffic cluster " + cluster + ".");
        double totalTraffic = 0;
        int solve_starttime = timeframeStart;
        int solve_endtime = timeframeEnd;

        String file_prefix = "/home/annzhou/DRing/src/emp/datacentre/trafficfiles/cluster_" + cluster + "/traffic_64racks";
//        String file_prefix = "../../DataParsing/outputs/cluster_" + cluster + "/traffic/traffic_64racks";
        String[] file_suffix = {};
        if (cluster.equals("a")) {
            file_suffix = new String[]{"_0_273"};
        } else if (cluster.equals("b")) {
            file_suffix = new String[]{"_0_500","_500_1000","_1000_1500","_1500_2000","_2000_2500","_2500_2900"};
        } else if (cluster.equals("c")) {
            file_suffix = new String[]{"_0_273"};
        } else {
            System.out.println("***Error: unknown cluster, cluster " + cluster);
        }

        try {
            BufferedReader br;
            String strLine = "";
            for (int i=0; i<file_suffix.length; i++) {
                String trafficfile = file_prefix + file_suffix[i];
                br = new BufferedReader(new FileReader(trafficfile));
                while ((strLine = br.readLine()) != null){
                    StringTokenizer strTok = new StringTokenizer(strLine);
                    int timestamp = Integer.parseInt(strTok.nextToken());
                    double traffic = Double.parseDouble(strTok.nextToken());
                    int srcsvr = Integer.parseInt(strTok.nextToken());
                    int dstsvr = Integer.parseInt(strTok.nextToken());
                    if (srcsvr>=topology.totalWeight) continue;
                    if (dstsvr>=topology.totalWeight) continue;
                    int srcsw = topology.svrToSwitch(srcsvr);
                    int dstsw = topology.svrToSwitch(dstsvr);

                    if (timestamp >= solve_endtime) break;
                    if (timestamp >= solve_starttime && timestamp < solve_endtime) {
                        serverLevelMatrix[srcsvr][dstsvr] += traffic;
                        switchLevelMatrix[srcsw][dstsw] += traffic;
                        totalTraffic += traffic;
                    }
                }
                br.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Total traffic = " + totalTraffic);

        writeServerLevelMatrix();
        //writeSwitchLevelMatrix();
        //printSwitchLevelMatrix("resultfiles/clustera_tm.txt");
    }

    public void generateSwitchServerTrafficMix(int n, int t, LeafSpine lsnet) {
        System.out.println("Generate switch & server traffic mix, n="+n+", t="+t);
        double unitTraffic = 1;
        double totalTraffic = 0;

        HashSet<Integer> heavyRacks = new HashSet<>();
        while (heavyRacks.size() < n) {
            heavyRacks.add(lsnet.generateRandomSwitch());
        }
        System.out.println(heavyRacks);
        HashSet<Integer> lightRacks = new HashSet<>();
        for (int s=lsnet.getNumSpineSwitches(); s<lsnet.noNodes; s++) {
            if (!heavyRacks.contains(s)) lightRacks.add(s);
        }
        System.out.println(lightRacks);

        HashSet<Integer> heavyServers = new HashSet<>();
        for (int hr : heavyRacks) {
            int[] servers = lsnet.getServersForSwitch(hr);
            for (int svr : servers) heavyServers.add(svr);
        }
        System.out.println("Number of heavy servers = " + heavyServers.size());
        HashSet<Integer> lightServers = new HashSet<>();
        for (int lr : lightRacks) {
            int[] servers = lsnet.getServersForSwitch(lr);
            for (int svr : servers) lightServers.add(svr);
        }
        System.out.println("Number of light servers = " + lightServers.size());

        for (int srcsvr=0; srcsvr<heavyServers.size(); srcsvr++) {
            for (int dstsvr=0; dstsvr<heavyServers.size(); dstsvr++) {
                if (srcsvr>=topology.totalWeight) continue;
                if (dstsvr>=topology.totalWeight) continue;
                int srcsw = topology.svrToSwitch(srcsvr);
                int dstsw = topology.svrToSwitch(dstsvr);
                serverLevelMatrix[srcsvr][dstsvr] += unitTraffic*t;
                switchLevelMatrix[srcsw][dstsw] += unitTraffic*t;
                totalTraffic += unitTraffic*t;
            }
        }

        for (int srcsvr=0; srcsvr<lightServers.size(); srcsvr++) {
            for (int dstsvr=0; dstsvr<lightServers.size(); dstsvr++) {
                if (srcsvr>=topology.totalWeight) continue;
                if (dstsvr>=topology.totalWeight) continue;
                int srcsw = topology.svrToSwitch(srcsvr);
                int dstsw = topology.svrToSwitch(dstsvr);
                serverLevelMatrix[srcsvr][dstsvr] += unitTraffic;
                switchLevelMatrix[srcsw][dstsw] += unitTraffic;
                totalTraffic += unitTraffic;
            }
        }
        System.out.println("Total traffic = " + totalTraffic);

        writeServerLevelMatrix();
    }

    public void setUpServerTrafficMatrix() {
        // A
        for (int i=0; i<numServers; i++) {
            for (int j=0; j<numServers; j++) {
                serverTrafficMatrix[i][j] = serverLevelMatrix[i][j];
            }
        }

        // B
        for (int i=0; i<numServers; i++) {
            int j = topology.adjacencyList[i].get(0).linkTo;
            for (int k=0; k<numServers; k++) {
                serverTrafficMatrix[i][j] += serverTrafficMatrix[i][k];
            }
        }

        // D
        for (int j=0; j<numServers; j++) {
            int i = topology.adjacencyList[j].get(0).linkTo;
            for (int k=0; k<numServers; k++) {
                serverTrafficMatrix[i][j] += serverTrafficMatrix[k][j];
            }
        }

        // C
//        for (int i=numServers; i<topology.noNodes; i++) {
//            for (int j=numServers; j<topology.noNodes; j++) {
//                for (int a=topology.weightBeforeEachNode[i]; a<topology.weightBeforeEachNode[i+1]; a++) {
//                    for (int b=topology.weightBeforeEachNode[j]; b<topology.weightBeforeEachNode[j+1]; b++) {
//                        serverTrafficMatrix[i][j] += serverTrafficMatrix[a][b];
//                    }
//                }
//            }
//        }
//        printServerTrafficMatrix("lpserverfiles/servertrafficmatrix1.txt",numServers,topology.noNodes);

        for (int i=0; i<numServers; i++) {
            int srcsw = topology.svrToSwitch(i);
//            srcsw += numServers;
            for (int j=0; j<numServers; j++) {
                int dstsw = topology.svrToSwitch(j);
//                dstsw += numServers;
                serverTrafficMatrix[srcsw][dstsw] += serverTrafficMatrix[i][j];
            }
        }
//        printServerTrafficMatrix("lpserverfiles/servertrafficmatrix2.txt",numServers,topology.noNodes);
    }

    public void generateTraffic(String trafficfile, int a, int b, int[] numServersPerSwitches, GraphFromFileSrcDstPair rrgnet, LeafSpine lsnet) {
        // traffic-mode: 0 = randPerm; 1 = all-all; 2 = all-to-one; Any higher n means stride(n)
        int inaccuracymode = 0;
        System.out.println("trafficmode = " + trafficmode);
        System.out.println("inaccuracymode = " + inaccuracymode);

        if (trafficmode == 0) {
            RandomPermutationPairs(numServers);
        }
        else if (trafficmode == 1) {
            TrafficGenAllAll();
        }
        else if (trafficmode == 2) {
            TrafficGenAllToOne();
        }
        else if (trafficmode == 3) {
            TrafficGenStride(trafficmode);
        }
        else if (trafficmode == 4) {
            MaxWeightPairs("maxWeightMatch.txt");System.out.println("*******MAX-WEIGHT-MATCHING************");
        }
        else if (trafficmode == 5 || trafficmode == 6) {
            TrafficGenRackLevelAllToAll();
        }
        else if (trafficmode == 7) {
            TrafficGenFromFileHelper(inaccuracymode, trafficfile);
        }
        else if (trafficmode == 8) {
            TrafficGenARackToBRack(a, b, numServersPerSwitches);
        }
        else if (trafficmode == 9) {
            TrafficGenMixA(a, numServersPerSwitches);
        }
        else if (trafficmode == 10) {
            TrafficGenRacktoRackHardCoding(numServersPerSwitches);
        }
        else if (trafficmode == 11) {
            TrafficGenCsSkewedHardCoding();
        }
        else if (trafficmode == 12) {
            TrafficGenFromFileClusterX(trafficfile);
        }
        else if (trafficmode == 13) {
            TrafficGenFromFileClusterXPacketAdjusted(trafficfile);
        }
        else if (trafficmode == 14) {
            TrafficGenClusterX(trafficfile);
        }
        else if (trafficmode == 15) {
            TrafficGenPermutationRackLevel();
        }
        else if (trafficmode == 16) {
            TrafficGenPermutationServerLevel();
        }
        else if (trafficmode == 17) {
            TrafficGenCss16to4HardCoding();
        }
        else if (trafficmode == 18) {
            TrafficGenAlltoAllHardCoding();
        }
        else if (trafficmode == 19) {
            TrafficGenPermutationHardCoding();
        }
        /*
        else if (trafficmode == 20) {
            TrafficGenAlltoAllHardCoding2();
        }
        else if (trafficmode == 21) {
            TrafficGenPermutationHardCoding2();
        }
         */
        else if (trafficmode == 100) {
            generateTrafficAllServerToAllServer();
        }
        else if (trafficmode == 101) {
            generateTrafficRRGUniform(rrgnet);
        }
        else if (trafficmode == 102) {
            generateTrafficRackPermutation(lsnet);
        }
        else if (trafficmode == 103) {
            generateTrafficRRGRackPermutation(rrgnet);
        }
        else if (trafficmode == 104) {
            generateTrafficServerPermutation();
        }
        else if (trafficmode == 105) {
            generateTrafficARackToBRack(a,b,lsnet);
        }
        else if (trafficmode == 106) {
            generateTrafficClusterX(trafficfile);
        }
        else if (trafficmode == 107) {
            generateTrafficMix(a,b,lsnet);
        }
        else if (trafficmode == 200) {
            generateSwitchServerTrafficAllServerToAllServer();
        }
        else if (trafficmode == 201) {
            generateSwitchServerTrafficARackToBRackFromFile(a,b,trafficfile,lsnet);
        }
        else if (trafficmode == 202) {
            generateSwitchServerTrafficRackPermutation(lsnet);
        }
        else if (trafficmode == 204) {
            generateSwitchServerTrafficServerPermutation();
        }
        else if (trafficmode == 205) {
            generateSwitchServerTrafficARackToBRack(a,b,lsnet);
        }
        else if (trafficmode == 206) {
            generateSwitchServerTrafficClusterX(trafficfile);
        }
        else if (trafficmode == 207) {
            generateSwitchServerTrafficMix(a,b,lsnet);
        }
        else if (trafficmode == 208) {
            generateSwitchServerTrafficARackToBRackFromFileFlat(a,b,trafficfile);
        }
        else if (trafficmode == 209) {
            generateSwitchServerTrafficARackToBRackFromFileServer(a,b,trafficfile);
        }
        else if (trafficmode == 210) {
            generateSwitchServerTrafficARackToBRackFromFileRandom(a,b,trafficfile);
        }
        else {
            System.out.println("Trafficmode is not recognized.");
        }
    }

    public HashSet<Integer> getHotRacks() {
        return hotRacks;
    }

    public void writeServerTrafficMatrix() {
        String writefile = "intermediatefiles/servertrafficmatrix.txt";
        int numNodes = numServers + numSwitches;
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(writefile));
            for (int i=0; i<numNodes; i++) {
                for (int j=0; j<numNodes; j++) {
                    out.write(serverTrafficMatrix[i][j] + "\t");
                }
                out.write("\n");
            }
            out.close();
        } catch (Exception e) {
            System.err.println("TrafficMatrix writeServerTrafficMatrix Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void writeServerLevelMatrix() {
        String writefile = "intermediatefiles/serverlevelmatrix.txt";
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(writefile));
            for (int i=0; i<numServers; i++) {
                for (int j=0; j<numServers; j++) {
                    out.write(serverLevelMatrix[i][j] + "\t");
                }
                out.write("\n");
            }
            out.close();
        } catch (Exception e) {
            System.err.println("TrafficMatrix writeServerLevelMatrix Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void writeSwitchLevelMatrix() {
        String writefile = "../debugresultfiles/switchlevelmatrix.txt";
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(writefile));
            for (int i=numSwitches-1; i>=0; i--) {
                out.write(i+"\t");
                for (int j=0; j<numSwitches; j++) {
                    out.write(switchLevelMatrix[i][j] + "\t");
                }
                out.write("\n");
            }
            out.write("\t");
            for (int j=0; j<numSwitches; j++) {
                out.write(j+"\t");
            }
            out.close();
        } catch (Exception e) {
            System.err.println("TrafficMatrix writeSwitchLevelMatrix Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
