import com.google.common.base.MoreObjects;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class ConsensusTest {
    @Test
    public void case1() {
        TestResult avg = getWorstResult(() -> Simulation.simulateConsensus(100, 0.1, 0.3, 0.01, 10));
        System.out.println(avg);
        //Uncomment for perfect consensus check
        //Assert.assertEquals(avg.consensusNodes, avg.compliantNodes);
    }

    @Test
    public void case2() {
        TestResult avg = getWorstResult(() -> Simulation.simulateConsensus(100, 0.1, 0.45, 0.01, 10));
        System.out.println(avg);
        //Uncomment for perfect consensus check
        //Assert.assertEquals(avg.consensusNodes, avg.compliantNodes);
    }

    @Test
    public void case3() {
        TestResult avg = getWorstResult(() -> Simulation.simulateConsensus(100, 0.1, 0.45, 0.05, 10));
        System.out.println(avg);
        //Uncomment for perfect consensus check
        //Assert.assertEquals(avg.consensusNodes, avg.compliantNodes);
    }

    @Test
    public void case4() {
        TestResult avg = getWorstResult(() -> Simulation.simulateConsensus(100, 0.2, 0.3, 0.01, 10));
        System.out.println(avg);
        //Uncomment for perfect consensus check
        //Assert.assertEquals(avg.consensusNodes, avg.compliantNodes);
    }

    @Test
    public void case5() {
        TestResult avg = getWorstResult(() -> Simulation.simulateConsensus(100, 0.2, 0.3, 0.05, 10));
        System.out.println(avg);
        //Uncomment for perfect consensus check
        //Assert.assertEquals(avg.consensusNodes, avg.compliantNodes);
    }

    @Test
    public void case6() {
        TestResult avg = getWorstResult(() -> Simulation.simulateConsensus(100, 0.2, 0.45, 0.01, 10));
        System.out.println(avg);
        //Uncomment for perfect consensus check
        //Assert.assertEquals(avg.consensusNodes, avg.compliantNodes);
    }

    @Test
    public void case7() {
        TestResult avg = getWorstResult(() -> Simulation.simulateConsensus(100, 0.2, 0.45, 0.05, 10));
        System.out.println(avg);
        //Uncomment for perfect consensus check
        //Assert.assertEquals(avg.consensusNodes, avg.compliantNodes);
    }

    private TestResult getWorstResult(Supplier<Node[]> simulate) {
        final int rounds = 10;
        TestResult tr = null;
        int worstDiff = 0;
        for (int i = 0; i < rounds; ++i) {
            TestResult r = getTestResult(simulate.get());
            int diff = r.compliantNodes - r.consensusNodes;
            if (diff > worstDiff || tr == null) {
                tr = r;
            }
        }
        return tr;
    }


    // ------------------------------------------------------------------
    static class TestResult {
        final int compliantNodes;
        final int consensusNodes;

        TestResult(int compliantNodes, int consensusNodes) {
            this.compliantNodes = compliantNodes;
            this.consensusNodes = consensusNodes;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("compliantNodes", compliantNodes)
                .add("consensusNodes", consensusNodes)
                .toString();
        }
    }

    private TestResult getTestResult(Node[] nodes) {
        Map<Set<Transaction>, Integer> txSetToCount = new HashMap<>();
        int compliantNodes = 0;
        for (Node node : nodes) {
            if (node instanceof CompliantNode) {
                ++compliantNodes;
                txSetToCount.compute(node.sendToFollowers(), (k, count) -> count != null? count + 1 : 1);
            }
        }
        return new TestResult(
            compliantNodes,
            txSetToCount.values().stream().max(Integer::compareTo).orElse(0)
        );
    }
    
}
