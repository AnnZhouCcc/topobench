package lpmaker;

import java.util.ArrayList;

public class Path {
    public ArrayList<NPLink> path;
    public int pid;
    int source;
    int destination;

    Path(int _pid) {
        path = new ArrayList<>();
        pid = _pid;
        source = -1;
        destination = -1;
    }

    void addLink(NPLink link) {
        if (path.size() == 0) source = link.from;
        path.add(link);
        destination = link.to;
    }
}