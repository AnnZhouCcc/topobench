package lpmaker;

import lpmaker.graphs.Link;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class FlowPair {
    int flowSrc;
    int flowDst;

    FlowPair (int _flowSrc, int _flowDst) {
        flowSrc = _flowSrc;
        flowDst = _flowDst;
    }
}

public class NetPath {
    String netpathFileName;
    String pathweightFileName;
    Vector<Link>[] adjacencyList;
    int numSwitches;
    int augmentMethod;

    public ArrayList<Path>[][] pathPool;
    HashMap<Integer, Double>[][] pathWeights;
    NPLink[][] linkPool;
    ArrayList<Rack> rackPool;
    ArrayList<LinkUsageTuple>[][] linksUsage;

    boolean shouldAvoidHotRacks;
    HashSet<Integer> hotRacks;

    public NetPath(String filename, Vector<Link>[] _adjacencyList, int _numSwitches, HashSet<Integer> _hotRacks, int _augmentMethod, String _pathweightFileName) {
        netpathFileName = filename;
        pathweightFileName = _pathweightFileName;
        adjacencyList = _adjacencyList;
        numSwitches = _numSwitches;
        augmentMethod = _augmentMethod;

        pathPool = new ArrayList[numSwitches][numSwitches];
        pathWeights = new HashMap[numSwitches][numSwitches];
        linkPool = new NPLink[numSwitches][numSwitches];
        rackPool = new ArrayList<>();
        linksUsage = new ArrayList[numSwitches][numSwitches];

        if (_hotRacks == null) {
            shouldAvoidHotRacks = false;
        } else {
            shouldAvoidHotRacks = true;
        }
        hotRacks = _hotRacks;

        populateLinkPool();
        initializePathPool();
        populatePathPool();
//        if (augmentMethod >= 0) augmentPathPool(augmentMethod);
//        initializeRackPool();
//        populateRackPool();
//        initializeLinksUsage();
//        populateLinksUsage();
//        initializePathWeights();
//        if (pathweightFileName != null) populatePathWeights();
    }

    public void populateLinkPool() {
        for (int i = 0; i < adjacencyList.length; i++) {
            for (int j = 0; j < adjacencyList[i].size(); j++) {
                int src = i;
                int dst = adjacencyList[i].get(j).linkTo;
                NPLink link = new NPLink(src, dst);
                linkPool[src][dst] = link;
            }
        }
    }

    public void initializePathPool() {
        for (int i=0; i<numSwitches; i++) {
            for (int j=0; j<numSwitches; j++) {
                pathPool[i][j] = new ArrayList<>();
            }
        }
    }

    public void populatePathPool() {
        readPathsFromFile(netpathFileName);
    }

    void readPathsFromFile(String filename) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            String strLine = "";
            String regex = "(\\d*)->(\\d*)";
            while ((strLine = br.readLine()) != null){
                StringTokenizer strTok = new StringTokenizer(strLine);
                int srcSw = Integer.parseInt(strTok.nextToken());
                int dstSw = Integer.parseInt(strTok.nextToken());
                int numPath = Integer.parseInt(strTok.nextToken());

                if (numPath > 0) {
                    ArrayList<Path> pathsThroughHotRacks = new ArrayList<>();

                    for (int i=0; i<numPath; i++) {
                        int prevEndingSw = -1;
                        int linkPosition = 0;
                        Path path = new Path(i);
                        boolean shouldAddThisPath = true;

                        strLine = br.readLine();
                        strTok = new StringTokenizer(strLine);
                        while (strTok.hasMoreTokens()) {
                            String token = strTok.nextToken();
                            Pattern pattern = Pattern.compile(regex);
                            Matcher matcher = pattern.matcher(token);
                            if (matcher.find() && matcher.groupCount() == 2) {
                                int linkSrcSw = Integer.parseInt(matcher.group(1));
                                int linkDstSw = Integer.parseInt(matcher.group(2));

                                if (shouldAvoidHotRacks) {
                                    if (hotRacks.contains(linkSrcSw) || hotRacks.contains(linkDstSw)) shouldAddThisPath = false;
                                }

                                if (linkPosition == 0 && srcSw != linkSrcSw) {
                                    System.out.println("srcSw != linkSrcSw for srcSw = " + srcSw + " and dstSw = " + dstSw);
                                }
                                if (linkPosition > 0 && linkSrcSw != prevEndingSw) {
                                    System.out.println("linkSrcSw != prevEndingSw");
                                    System.out.println("srcSw = " + srcSw + ", dstSw = " + dstSw);
                                    System.out.println("linkPosition = " + linkPosition + ", prevEndingSw = " + prevEndingSw);
                                    System.out.println("linkSrcSw = " + linkSrcSw + ", linkDstSw = " + linkDstSw);
                                }

                                prevEndingSw = linkDstSw;
                                NPLink link = linkPool[linkSrcSw][linkDstSw];
                                if (link == null || link.from != linkSrcSw || link.to != linkDstSw) {
                                    System.out.println("The link from the linkPool is not quite right");
                                    System.out.println("srcSw = " + srcSw + ", dstSw = " + dstSw);
                                    System.out.println("linkSrcSw = " + linkSrcSw + ", linkDstSw = " + linkDstSw);
                                }
                                path.addLink(link);
                            } else {
                                System.out.println("Pattern is not matched.");
                                System.out.println(strLine);
                                System.out.println(strTok);
                                System.out.println(token);
                                System.out.println("srcsw="+srcSw+", dstsw="+dstSw);
                                System.out.println("The input format needs to be: [source switch]->[destination switch]");
                            }

                            linkPosition++;
                        }

                        if (dstSw != prevEndingSw) {
                            System.out.println("dstSw != prevEndingSw for srcSw = " + srcSw + " and dstSw = " + dstSw);
                        }

                        if (shouldAddThisPath) {
                            pathPool[srcSw][dstSw].add(path);
                        } else {
                            pathsThroughHotRacks.add(path);
                        }
                    }

                    if (pathPool[srcSw][dstSw].size() == 0) {
                        for (int i=0; i<pathsThroughHotRacks.size(); i++) {
                            pathPool[srcSw][dstSw].add(pathsThroughHotRacks.get(i));
                        }
                    }
                }

                if (pathPool[srcSw][dstSw].size() > numPath) {
                    System.out.println("pathPool[srcSw][dstSw].size() > numPath");
                }
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void initializeRackPool() {
        for (int i=0; i<numSwitches; i++) {
            Rack rack = new Rack(i, numSwitches);
            rackPool.add(rack);
        }
    }

    public void populateRackPool() {
        for (int i=0; i<numSwitches; i++) {
            for (int j=0; j<numSwitches; j++) {
                if (i == j) continue;
                ArrayList<Path> feasiblePaths = pathPool[i][j];
                if (feasiblePaths.size() == 0) {
                    System.out.println("There is no feasible path from " + i + " to " + j);
                }

                for (int k=0; k<feasiblePaths.size(); k++) {
                    Path thisPath = feasiblePaths.get(k);
                    ArrayList<NPLink> theLinks = thisPath.path;
                    int pid = thisPath.pid;
                    for (int l=0; l<theLinks.size(); l++) {
                        NPLink thisLink = theLinks.get(l);
                        int linkSrc = thisLink.from;
                        int linkDst = thisLink.to;

                        rackPool.get(linkSrc).addOutgoingLink(i,j,linkSrc, linkDst, pid, l);
                        rackPool.get(linkDst).addIncomingLink(i,j,linkSrc, linkDst, pid);
                    }
                }
            }
        }
    }

    public void initializeLinksUsage() {
        for (int i=0; i<numSwitches; i++) {
            for (int j=0; j<numSwitches; j++) {
                linksUsage[i][j] = new ArrayList<>();
            }
        }
    }

    public void populateLinksUsage() {
        for (int i=0; i<numSwitches; i++) {
            for (int j=0; j<numSwitches; j++) {
                if (i == j) continue;
                ArrayList<Path> paths = pathPool[i][j];
                for (int p=0; p<paths.size(); p++) {
                    Path thisPath = paths.get(p);
                    int pid = thisPath.pid;
                    for (int l=0; l<thisPath.path.size(); l++) {
                        NPLink link = thisPath.path.get(l);
                        linksUsage[link.from][link.to].add(new LinkUsageTuple(i,j,pid));
                    }
                }

            }
        }
    }

    public void augmentPathPool(int method) {
        if (method < 0) {
            System.out.println("Method = " + method + ". Should not augment path pool.");
        } else {
            switch (method) {
                case 0:
                    favorShorterPaths();
                    break;
                case 1:
                    break;
                default:
                    System.out.println("Method " + method + " not implemented.");
            }
        }
    }

    Path copyPath(Path refPath, int pid) {
        Path newPath = new Path(pid);

        for (int i=0; i< refPath.path.size(); i++) {
            newPath.addLink(refPath.path.get(i));
        }
        return newPath;
    }

    void favorShorterPaths() {
        System.out.println("Favor shorter paths");

        int SUM = 3;
        for (int s=0; s<numSwitches; s++) {
            for (int d=0; d<numSwitches; d++) {
                ArrayList<Path> paths = pathPool[s][d];
                int totalPaths = paths.size();
                int pid = totalPaths;

                for (int p=0; p<totalPaths; p++) {
                    Path thisPath = paths.get(p);
                    int pathLength = thisPath.path.size();
                    int numDuplicate = SUM - pathLength;

                    for (int i=0; i<numDuplicate; i++) {
                        pathPool[s][d].add(copyPath(thisPath, pid));
                        pid++;
                    }
                }
            }
        }
    }

    public void initializePathWeights() {
        for (int i=0; i<numSwitches; i++) {
            for (int j=0; j<numSwitches; j++) {
                pathWeights[i][j] = new HashMap();
            }
        }
    }

    public void populatePathWeights() {
//        readPathWeightsFromFile(pathweightFileName);
        readGlobalPathWeightsFromFile(pathweightFileName);
    }

    void readPathWeightsFromFile(String filename) {
        String flowIDmapfile = "flowIDmapfiles/flowIDmap_fbs";
        try {
            BufferedReader br = new BufferedReader(new FileReader(flowIDmapfile));
            String strLine = "";
            HashMap<Integer, FlowPair> flowIDmap = new HashMap<>();
            while ((strLine = br.readLine()) != null) {
                StringTokenizer strTok = new StringTokenizer(strLine);
                int flowID = Integer.parseInt(strTok.nextToken());
                int flowSrc = Integer.parseInt(strTok.nextToken());
                int flowDst = Integer.parseInt(strTok.nextToken());

                flowIDmap.put(flowID, new FlowPair(flowSrc, flowDst));
            }
            br.close();

            br = new BufferedReader(new FileReader(filename));
            strLine = "";
            String regex = "f_(\\d*)_(\\d*)_(\\d*)_(\\d*)";
            while ((strLine = br.readLine()) != null){
                StringTokenizer strTok = new StringTokenizer(strLine);
                String name = strTok.nextToken();
                if (name.equals("K")) break;
//                if (name.equals("f_223_12_0_1")) break;
                double weight = Double.parseDouble(strTok.nextToken()); // Is actually absolute amount; but should be ok

                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(name);
                if (matcher.find() && matcher.groupCount() == 4) {
                    int fid = Integer.parseInt(matcher.group(1));
                    int pid = Integer.parseInt(matcher.group(2));
                    int linkSrc = Integer.parseInt(matcher.group(3));
                    int linkDst = Integer.parseInt(matcher.group(4));
                    int flowSrc = flowIDmap.get(fid).flowSrc;
                    int flowDst = flowIDmap.get(fid).flowDst;

                    if (!pathWeights[flowSrc][flowDst].containsKey(pid)) {
                        pathWeights[flowSrc][flowDst].put(pid, weight);
                    }
                }
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void readGlobalPathWeightsFromFile(String filename) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            String strLine = "";
            while ((strLine = br.readLine()) != null){
                StringTokenizer strTok = new StringTokenizer(strLine);
                int flowSrc = Integer.parseInt(strTok.nextToken());
                int flowDst = Integer.parseInt(strTok.nextToken());
                int fid = Integer.parseInt(strTok.nextToken());
                int firstHopSrc = Integer.parseInt(strTok.nextToken());
                int firstHopDst = Integer.parseInt(strTok.nextToken());
                int pid = Integer.parseInt(strTok.nextToken());
                double weight = Double.parseDouble(strTok.nextToken());

                pathWeights[flowSrc][flowDst].put(pid, weight);
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
