package lpmaker;

import java.util.Objects;

public class Hop {
    public int pid;
    public int linkSrc;
    public int linkDst;

    Hop(int _pid, int _linkSrc, int _linkDst) {
        pid = _pid;
        linkSrc = _linkSrc;
        linkDst = _linkDst;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Hop that = (Hop) o;
        return pid == that.pid && linkSrc == that.linkSrc && linkDst == that.linkDst;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pid, linkSrc, linkDst);
    }
}
