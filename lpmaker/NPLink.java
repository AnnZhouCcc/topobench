package lpmaker;

public class NPLink {
    public int from;
    public int to;
    public int linkCapacity;

    NPLink (int _from, int _to) {
        from = _from;
        to = _to;
        linkCapacity = 1;
    }

    void setLinkCapacity(int _linkCapacity) {
        linkCapacity = _linkCapacity;
    }
}