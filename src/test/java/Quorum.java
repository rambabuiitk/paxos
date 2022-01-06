import java.util.List;
import java.util.Set;

public class Quorum {

    private Set<Acceptor>  acceptors;
    private List<Proposer> proposers;

    static Quorum create(Set<Acceptor> acceptors, List<Proposer> proposers) {
        return new Quorum(acceptors, proposers);
    }

    public Quorum(Set<Acceptor> acceptors, List<Proposer> proposers){
        this.acceptors = acceptors;
        this.proposers = proposers;
    }

    public void stop() {
        for (Proposer proposer : getProposers()) {
            proposer.stop();
        }
    }

    public Set<Acceptor> getAcceptors() {
        return acceptors;
    }

    public List<Proposer> getProposers() {
        return proposers;
    }

}