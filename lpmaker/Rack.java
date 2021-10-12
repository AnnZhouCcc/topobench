package lpmaker;

import java.util.ArrayList;

public class Rack {
    int id;
    int numSwitches;

    public ArrayList<Hop>[][] incomingHops;
    public ArrayList<Hop>[][] outgoingHops;

    Rack(int _id, int _numSwitches) {
        id = _id;
        numSwitches = _numSwitches;

        incomingHops = new ArrayList[numSwitches][numSwitches];
        outgoingHops = new ArrayList[numSwitches][numSwitches];

        for (int i=0; i<numSwitches; i++) {
            for (int j=0; j<numSwitches; j++) {
                incomingHops[i][j] = new ArrayList<>();
                outgoingHops[i][j] = new ArrayList<>();
            }
        }
    }

    void addIncomingLink(int flowSrc, int flowDst, int linkSrc, int linkDst, int pid) {
        assert(linkDst == id);
        incomingHops[flowSrc][flowDst].add(new Hop(pid, linkSrc, linkDst));
    }

    void addOutgoingLink(int flowSrc, int flowDst, int linkSrc, int linkDst, int pid) {
        assert(linkSrc == id);
        outgoingHops[flowSrc][flowDst].add(new Hop(pid, linkSrc, linkDst));
    }
}
