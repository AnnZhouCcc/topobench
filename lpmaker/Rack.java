package lpmaker;

import java.util.ArrayList;
import java.util.HashSet;

public class Rack {
    int id;
    int numSwitches;

    public HashSet<Integer>[][] incomingLinkSrc;
    public HashSet<Integer>[][] outgoingLinkDst;

    public ArrayList<HopWithDuplicate>[][] incomingHopsWithDuplicate;
    public ArrayList<HopWithDuplicate>[][] outgoingHopsWithDuplicate;

    public HashSet<HopWithNoDuplicate>[][] incomingHopsWithNoDuplicate;
    public HashSet<HopWithNoDuplicate>[][] outgoingHopsWithNoDuplicate;

    Rack(int _id, int _numSwitches) {
        id = _id;
        numSwitches = _numSwitches;

        incomingLinkSrc = new HashSet[numSwitches][numSwitches];
        outgoingLinkDst = new HashSet[numSwitches][numSwitches];

        incomingHopsWithDuplicate = new ArrayList[numSwitches][numSwitches];
        outgoingHopsWithDuplicate = new ArrayList[numSwitches][numSwitches];

        incomingHopsWithNoDuplicate = new HashSet[numSwitches][numSwitches];
        outgoingHopsWithNoDuplicate = new HashSet[numSwitches][numSwitches];

        for (int i=0; i<numSwitches; i++) {
            for (int j=0; j<numSwitches; j++) {
                incomingLinkSrc[i][j] = new HashSet<>();
                outgoingLinkDst[i][j] = new HashSet<>();

                incomingHopsWithDuplicate[i][j] = new ArrayList<>();
                outgoingHopsWithDuplicate[i][j] = new ArrayList<>();

                incomingHopsWithNoDuplicate[i][j] = new HashSet<>();
                outgoingHopsWithNoDuplicate[i][j] = new HashSet<>();
            }
        }
    }

    void addIncomingLink(int flowSrc, int flowDst, int linkSrc, int linkDst, int pid) {
        assert(linkDst == id);
        incomingLinkSrc[flowSrc][flowDst].add(linkSrc);
        incomingHopsWithDuplicate[flowSrc][flowDst].add(new HopWithDuplicate(pid, linkSrc, linkDst));
        incomingHopsWithNoDuplicate[flowSrc][flowDst].add(new HopWithNoDuplicate(pid, linkSrc, linkDst));
    }

    void addOutgoingLink(int flowSrc, int flowDst, int linkSrc, int linkDst, int pid) {
        assert(linkSrc == id);
        outgoingLinkDst[flowSrc][flowDst].add(linkDst);
        outgoingHopsWithDuplicate[flowSrc][flowDst].add(new HopWithDuplicate(pid, linkSrc, linkDst));
        outgoingHopsWithNoDuplicate[flowSrc][flowDst].add(new HopWithNoDuplicate(pid, linkSrc, linkDst));
    }
}
