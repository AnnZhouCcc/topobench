package lpmaker;

import java.util.Objects;

public class LinkUsageTupleWithDuplicate {
    public int flowSrc;
    public int flowDst;
    public int pid;

    public LinkUsageTupleWithDuplicate(int _flowSrc, int _flowDst, int _pid) {
        flowSrc = _flowSrc;
        flowDst = _flowDst;
        pid = _pid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LinkUsageTupleWithDuplicate that = (LinkUsageTupleWithDuplicate) o;
        return flowSrc == that.flowSrc && flowDst == that.flowDst && pid == that.pid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(flowSrc, flowDst, pid);
    }
}
