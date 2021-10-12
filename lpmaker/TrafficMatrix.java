package lpmaker;

import lpmaker.graphs.Graph;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.StringTokenizer;

class TrafficPair {
    public int from;
    public int to;

    TrafficPair(int a, int b) {
        from = a;
        to = b;
    }
}

public class TrafficMatrix {
    double[][] switchLevelMatrix;
    double trafficPerFlow = 1;
    double packetSize = 1500.0;
    int numFlowCap = 50;
    int numSwitches;
    int numServers;
    int trafficmode;
    Graph topology;
    HashSet<Integer> hotRacks;

    public TrafficMatrix(int noNodes, int trafficMode, String trafficfile, Graph mynet, int a, int b, int[] numServersPerSwitches) {
        numSwitches = noNodes;
        numServers = mynet.totalWeight;
        switchLevelMatrix = new double[numSwitches][numSwitches];
        trafficmode = trafficMode;
        topology = mynet;
        hotRacks = new HashSet<>();

        generateTraffic(trafficfile, a, b, numServersPerSwitches);
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
        }

        System.out.println("Number of flows = " + numFlows);
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
                double traffic = (Integer.parseInt(strTok.nextToken()))/packetSize;

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

            double coefficient = maxTraffic/numFlowCap;
            for (int src = 0; src < numSwitches; src++) {
                for (int dst = 0; dst < numSwitches; dst++) {
                    if (src == dst) continue;
                    int mult = (int)(accumulativeTrafficMatrix[src][dst] / coefficient);
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
        if (a == 10) {
            SNR = 53.6;
            SNA = 1;
        } else if (a == 5) {
            SNR = 55.5;
            SNA = 0.2;
        } else if (a == 2) {
            SNR = 30;
            SNA = 0.01;
        } else {
            System.out.println("The parameters here are hard-coded. a=" + a + " does not have SNR and SNA hard-coded.");
        }
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

    public void generateTraffic(String trafficfile, int a, int b, int[] numServersPerSwitches) {
        // traffic-mode: 0 = randPerm; 1 = all-all; 2 = all-to-one; Any higher n means stride(n)
        System.out.println("trafficmode = " + trafficmode);
        ArrayList<TrafficPair> rndmap;
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
            TrafficGenFromFile(trafficfile);
        }
        else if (trafficmode == 8) {
            TrafficGenARackToBRack(a, b, numServersPerSwitches);
        }
        else if (trafficmode == 9) {
            TrafficGenMixA(a, numServersPerSwitches);
        }
        else {
            System.out.println("Trafficmode is not recognized.");
        }
    }

    public HashSet<Integer> getHotRacks() {
        return hotRacks;
    }
}
