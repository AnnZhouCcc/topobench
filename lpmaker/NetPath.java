package lpmaker;

import lpmaker.graphs.Link;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetPath {
    String netpathFileName;
    Vector<Link>[] adjacencyList;
    int numSwitches;
    ArrayList<Path>[][] pathPool;
    NPLink[][] linkPool;
    ArrayList<Rack> rackPool;
    ArrayList<LinkUsageTupleWithDuplicate>[][] linksUsageWithDuplicate;
    HashSet<LinkUsageTupleWithNoDuplicate>[][] linksUsageWithNoDuplicate;

    public NetPath(String filename, Vector<Link>[] _adjacencyList, int _numSwitches) {
        netpathFileName = filename;
        adjacencyList = _adjacencyList;
        numSwitches = _numSwitches;
        pathPool = new ArrayList[numSwitches][numSwitches];
        linkPool = new NPLink[numSwitches][numSwitches];
        rackPool = new ArrayList<>();
        linksUsageWithDuplicate = new ArrayList[numSwitches][numSwitches];
        linksUsageWithNoDuplicate = new HashSet[numSwitches][numSwitches];

        populateLinkPool();
        initializePathPool();
        populatePathPool();
        initializeRackPool();
        populateRackPool();
        initializeLinksUsage();
        populateLinksUsage();
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

    public void readPathsFromFile(String filename) {
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
                    for (int i=0; i<numPath; i++) {
                        int prevEndingSw = -1;
                        int linkPosition = 0;
                        Path path = new Path(i);
                        strLine = br.readLine();
                        strTok = new StringTokenizer(strLine);
                        while (strTok.hasMoreTokens()) {
                            String token = strTok.nextToken();
                            Pattern pattern = Pattern.compile(regex);
                            Matcher matcher = pattern.matcher(token);
                            if (matcher.find() && matcher.groupCount() == 2) {
                                int linkSrcSw = Integer.parseInt(matcher.group(1));
                                int linkDstSw = Integer.parseInt(matcher.group(2));

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
                                System.out.println("The input format needs to be: [source switch]->[destination switch]");
                            }

                            linkPosition++;
                        }

                        if (dstSw != prevEndingSw) {
                            System.out.println("dstSw != prevEndingSw for srcSw = " + srcSw + " and dstSw = " + dstSw);
                        }

                        pathPool[srcSw][dstSw].add(path);
                    }
                }

                if (pathPool[srcSw][dstSw].size() != numPath) {
                    System.out.println("pathPool[srcSw][dstSw].size() != numPath");
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

                        rackPool.get(linkSrc).addOutgoingLink(i,j,linkSrc, linkDst, pid);
                        rackPool.get(linkDst).addIncomingLink(i,j,linkSrc, linkDst, pid);
                    }
                }
            }
        }
    }

    public void initializeLinksUsage() {
        for (int i=0; i<numSwitches; i++) {
            for (int j=0; j<numSwitches; j++) {
                linksUsageWithDuplicate[i][j] = new ArrayList<>();
                linksUsageWithNoDuplicate[i][j] = new HashSet<>();
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
                        linksUsageWithNoDuplicate[link.from][link.to].add(new LinkUsageTupleWithNoDuplicate(i,j,pid));
                        linksUsageWithDuplicate[link.from][link.to].add(new LinkUsageTupleWithDuplicate(i,j,pid));
                    }
                }

            }
        }
    }
}
