package lpmaker;

import java.util.Objects;

public class LinkUsageTupleWithNoDuplicate {
    public int flowSrc;
    public int flowDst;
    public int pid;

    public LinkUsageTupleWithNoDuplicate(int _flowSrc, int _flowDst, int _pid) {
        flowSrc = _flowSrc;
        flowDst = _flowDst;
        pid = _pid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LinkUsageTupleWithNoDuplicate that = (LinkUsageTupleWithNoDuplicate) o;
        return flowSrc == that.flowSrc && flowDst == that.flowDst;
    }

    @Override
    public int hashCode() {
        return Objects.hash(flowSrc, flowDst);
    }
}
