import com.google.common.collect.ImmutableList;

import java.util.*;
import java.util.function.Supplier;

public class MaliciousNode implements Node {
    private static final Random rnd = new Random(123);
    private static final List<Supplier<Strategy>> strategies = ImmutableList.of(
        DontPropagate::new,
        SendOwn::new,
        SendOwnAndNone::new
    );
    private final Strategy strategy;

    public MaliciousNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
        //strategy = strategies.get(rnd.nextInt(strategies.size())).get();
        //TODO this is hardcoded for the worst strategy for the current consensus logic
        strategy = strategies.get(2).get();
    }

    public void setFollowees(boolean[] followees) {
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        strategy.setPendingTransaction(pendingTransactions);
    }

    public Set<Transaction> sendToFollowers() {
        return strategy.sendToFollowers();
    }

    public void receiveFromFollowees(Set<Candidate> candidates) {
    }

    // ------------------------------------------------------------------
    private interface Strategy {
        Set<Transaction> sendToFollowers();

        default void setPendingTransaction(Set<Transaction> pendingTransactions) {}
    }

    private static class DontPropagate implements Strategy {
        @Override
        public Set<Transaction> sendToFollowers() {
            return Collections.emptySet();
        }
    }

    private static class SendOwn implements Strategy {
        private Set<Transaction> pendingTransactions;

        @Override
        public void setPendingTransaction(Set<Transaction> pendingTransactions) {
            this.pendingTransactions = pendingTransactions;
        }

        @Override
        public Set<Transaction> sendToFollowers() {
            return pendingTransactions;
        }
    }

    private static class SendOwnAndNone implements Strategy {
        private Set<Transaction> pendingTransactions;

        @Override
        public void setPendingTransaction(Set<Transaction> pendingTransactions) {
            this.pendingTransactions = pendingTransactions;
        }

        @Override
        public Set<Transaction> sendToFollowers() {
            return rnd.nextDouble() < 0.01? pendingTransactions : Collections.emptySet();
        }
    }
}
