import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
/** Current implementation is a naive one which seems to work well enough for the tests cases */
public class CompliantNode implements Node {

    private final Set<Transaction> seenTxs = new HashSet<>();

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
        //Not used
    }

    public void setFollowees(boolean[] followees) {
        //Not used
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        seenTxs.addAll(pendingTransactions);
    }

    public Set<Transaction> sendToFollowers() {
        return Collections.unmodifiableSet(seenTxs);
    }

    public void receiveFromFollowees(Set<Candidate> candidates) {
        seenTxs.addAll(candidates.stream().map(c -> c.tx).collect(Collectors.toSet()));
    }
}
