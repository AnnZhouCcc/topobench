package lpmaker;

import java.util.Objects;

public class HopWithDuplicate {
    public int pid;
    public int linkSrc;
    public int linkDst;

    HopWithDuplicate(int _pid, int _linkSrc, int _linkDst) {
        pid = _pid;
        linkSrc = _linkSrc;
        linkDst = _linkDst;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HopWithDuplicate that = (HopWithDuplicate) o;
        return pid == that.pid && linkSrc == that.linkSrc && linkDst == that.linkDst;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pid, linkSrc, linkDst);
    }
}
