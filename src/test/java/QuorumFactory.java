import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class QuorumFactory {

    private static final int DEFAULT_THREAD = 5;

    public static Quorum create(int acceptorNum, int proposerNum) {
        Set<Acceptor> acceptors = new HashSet<>(acceptorNum);
        for (int i = 0; i < acceptorNum; i++) {
            acceptors.add(new DefaultAcceptor());
        }

        List<Proposer> proposers = new ArrayList<>(proposerNum);
        for (int i = 0; i < proposerNum; i++) {
            proposers.add(new DefaultProposer(acceptors, i, proposerNum, DEFAULT_THREAD));
        }

        return Quorum.create(acceptors, proposers);
    }

}