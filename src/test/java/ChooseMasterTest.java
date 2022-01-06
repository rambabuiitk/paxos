import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;
import util.Asserts;

public class ChooseMasterTest {

    private static final int   DEFAULT_THREAD = 10;
    private Quorum             quorum         = null;
    private ThreadPoolExecutor executor;

    @Test
    public void test_seq_1Proposer_1Acceptor() {
        quorum = QuorumFactory.create(1, 1);
        Proposer proposer = quorum.getProposers().get(0);

        Object master1 = proposer.propose("master", "node_1");
        Object master2 = proposer.propose("master", "node_2");
        assertEquals(master1, master2);
    }

    @Test
    public void test_seq_1Proposer_3Acceptor() {
        quorum = QuorumFactory.create(3, 1);
        Proposer proposer = quorum.getProposers().get(0);

        Object master1 = proposer.propose("master", "node_2");
        Object master2 = proposer.propose("master", "node_1");

        assertEquals(master1, master2);
    }

    @Test
    public void test_seq_2Proposer_3Acceptor() {
        quorum = QuorumFactory.create(3, 2);
        Proposer proposer1 = quorum.getProposers().get(0);
        Proposer proposer2 = quorum.getProposers().get(1);

        Object master1 = proposer1.propose("master", "node_1");
        Object master2 = proposer1.propose("master", "node_2");
        Object master3 = proposer2.propose("master", "node_3");
        Object master4 = proposer2.propose("master", "node_4");

        Asserts.equals(master1, master2, master3, master4);
    }

    @Test
    public void test_concurrent() throws Exception {
        // 1 acceptor, 1 proposer, 3 client , loop 50 instances
        testConcurrentWithLoopNum(1, 1, 3, 50);

        // testConcurrentWithLoopNum(3, 1, 3, 50);

        // testConcurrentWithLoopNum(3, 3, 3, 50);

        //testConcurrentWithLoopNum(5, 5, 5, 50);
    }

    public void testConcurrentWithLoopNum(int acceptorNum, int proposerNum, int clientNum, int loopNum)
            throws Exception {
        for (int i = 0; i < loopNum; i++) {
            testConcurrent(acceptorNum, proposerNum, clientNum);
        }

    }

    public void testConcurrent(int acceptorNum, int proposerNum, int clientNum) throws Exception {
        if (quorum != null) {
            quorum.stop();
        }
        quorum = QuorumFactory.create(acceptorNum, proposerNum);
        CompletionService<Object> executor = createCompletionService();

        Random random = new Random();
        List<Proposer> proposers = quorum.getProposers();
        for (int i = 0; i < clientNum; i++) {
            executor.submit(new ProposorCall(proposers.get(random.nextInt(proposerNum)), "master", "node_" + i));
        }

        List<Object> results = new ArrayList<>(clientNum);
        for (int i = 0; i < clientNum; i++) {
            Future<Object> future = executor.take();
            Object result = future.get();
            results.add(result);
        }

        for (Object o : results) {
            System.out.println(o);
        }
        Asserts.equals(results.toArray());
    }

    public CompletionService<Object> createCompletionService() {
        this.executor = new ThreadPoolExecutor(DEFAULT_THREAD,
                DEFAULT_THREAD,
                0,
                TimeUnit.MICROSECONDS,
                new ArrayBlockingQueue<Runnable>(DEFAULT_THREAD),
                new CallerRunsPolicy());

        return new ExecutorCompletionService<Object>(this.executor);
    }

    // ======== after =======
    @After
    public void after() {
        if (quorum != null) {
            quorum.stop();
        }

        if (this.executor != null) {
            this.executor.shutdown();
            try {
                this.executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class ProposorCall implements Callable<Object> {

        private Proposer proposer;
        private Object   var;
        private Object   value;

        public ProposorCall(Proposer proposer, Object var, Object value){
            this.proposer = proposer;
            this.var = var;
            this.value = value;
        }

        @Override
        public Object call() throws Exception {
            Object actual = this.proposer.propose(var, value);
            return actual;
        }

    }
}