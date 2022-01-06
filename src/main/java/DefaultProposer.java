import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import util.Asserts;
import util.Counter;
import util.MDCs;

public class DefaultProposer implements Proposer {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultProposer.class);
    private static final String NAME_PREFIX = "proposer_";

    private long sleepTimeWhenNeedNextRound = TimeUnit.NANOSECONDS.convert(10, TimeUnit.MILLISECONDS);
    private AtomicLong lastEpoch = new AtomicLong(-1L);
    private String name;
    private Set<Acceptor> acceptors = new HashSet<>();
    private int majorityAcceptorNum;
    private Long proposerId;
    private Integer proposerNum;
    private ThreadPoolExecutor executor;

    public DefaultProposer(Set<Acceptor> acceptors, long proposerId, int proposerNum, int threads) {
        this.name = NAME_PREFIX + proposerId;
        this.acceptors = acceptors;
        this.majorityAcceptorNum = acceptors.size() / 2 + 1;
        this.proposerId = proposerId;
        this.proposerNum = proposerNum;
        this.executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(threads), new CallerRunsPolicy());
    }

    @Override
    public Object propose(final Object var, final Object value) {
        final Long epoch = generateEpoch(0L, var);
        return proposeWithEpoch(epoch, var, value);
    }

    public Object proposeWithEpoch(final Long epoch, final Object var, final Object value) {
        MDC.put(MDCs.MDC_NAME, this.name);
        LOG.info("start to send prepare (epoch[{}], var [{}], value [{}])", epoch, var, value);
        //phase 1
        List<Promise> promises = concurrentPrepare(var, epoch);
        if (promises.size() < this.majorityAcceptorNum) {
            LOG.info("receive promises num [{}] , less then majorityAcceptorNum [{}], try to propose again",
                    promises.size(), this.majorityAcceptorNum);
            proposeWithEpoch(epoch, var, value);
        }

        int nakPromisesNum = 0;
        Long maxEpoch = epoch;
        int firstPromises = 0;
        int maybeHasValuePromises = 0;

        Counter<Object> values = new Counter<>();
        for (Promise promise : promises) {
            if (promise.isNAK()) {
                nakPromisesNum++;
                maxEpoch = maxEpoch > promise.getPreEpoch() ? maxEpoch : promise.getPreEpoch();
                continue;
            }
            if (promise.getPreEpoch() == null) {
                firstPromises++;
                continue;
            }
            if (promise.getPreEpoch() <= epoch) {
                maybeHasValuePromises++;
                values.add(promise.getValue());
                continue;
            }

            Asserts.unreachable();
        }

        LOG.info(
                "prepare (epoch [{}], var [{}], value [{}]) status  : nakPromisesNum [{}], maxEpoch [{}], firstPromises [{}], maybeHasValuePromises [{}], this.majorityAcceptorNum [{}]",
                epoch, var, value, nakPromisesNum, maxEpoch, firstPromises, maybeHasValuePromises,
                this.majorityAcceptorNum);
        if (nakPromisesNum >= this.majorityAcceptorNum) {
            LOG.info(
                    "prepare fail for var [{}], value [{}], nakPromisesNum [{}] is greater then majorityAcceptorNum [{}], the max preEpoch is [{}]",
                    var, value, nakPromisesNum, majorityAcceptorNum, maxEpoch);
            return nextRound(maxEpoch, var, value);
        }

        // prepare success, and we can continue phase 2
        if (firstPromises >= this.majorityAcceptorNum) {
            LOG.info(
                    "prepare success for var [{}], value [{}], firstPromises [{}] is greater then majorityAcceptorNum [{}], try accept with epoch [{}]",
                    var, value, firstPromises, this.majorityAcceptorNum, epoch);
            return tryAccept(epoch, var, value);
        }

        if (maybeHasValuePromises >= this.majorityAcceptorNum
                || (firstPromises + maybeHasValuePromises) >= this.majorityAcceptorNum) {
            Object newValue = values.getMostItem();
            if (newValue == null) {
                newValue = value;
            }

            LOG.info("prepare success for var [{}], epoch [{}], we can try accept with new value [{}]", var, epoch,
                    newValue);
            return tryAccept(epoch, var, newValue);
        }

        return nextRound(maxEpoch, var, value);

    }

    public Object tryAccept(final Long epoch, final Object var, final Object value) {
        List<Accepted> accepteds = concurrentCommit(epoch, var, value);
        if (accepteds.size() < this.majorityAcceptorNum) {
            return proposeWithEpoch(epoch, var, value);
        }

        int nakAcceptedNum = 0;
        int successAcceptedNum = 0;
        Long maxEpochWhenAccepted = epoch;
        Counter<Object> oldAcceptedValues = new Counter<>();
        for (Accepted accepted : accepteds) {
            if (accepted.isNAK()) {
                nakAcceptedNum++;
                maxEpochWhenAccepted = maxEpochWhenAccepted > accepted.getEpoch() ? maxEpochWhenAccepted
                        : accepted.getEpoch();
                oldAcceptedValues.add(accepted.getValue());
                continue;
            }

            if (!accepted.isNAK()) {
                successAcceptedNum++;
                oldAcceptedValues.add(accepted.getValue());
                continue;
            }
            Asserts.unreachable();
        }

        LOG.info(
                "accept (epoch [{}], var [{}], value [{}]) status : nakAcceptedNum [{}], successAcceptedNum [{}], maxEpochWhenAccepted [{}], majorityAcceptorNum [{}]",
                epoch, var, value, nakAcceptedNum, successAcceptedNum, maxEpochWhenAccepted, this.majorityAcceptorNum);

        if (nakAcceptedNum >= this.majorityAcceptorNum) {
            LOG.info("NAK accept, epoch [{}], var [{}], value[{}], the maxEpochWhenAccepted is [{}]", epoch, var, value,
                    maxEpochWhenAccepted);
            return nextRound(maxEpochWhenAccepted, var, value);
        }

        if (successAcceptedNum >= this.majorityAcceptorNum) {
            LOG.info("ACK accept, epoch [{}], var [{}], value[{}]", epoch, var, value);
            return oldAcceptedValues.getMostItem();
        }

        return nextRound(maxEpochWhenAccepted, var, value);
    }

    protected List<Promise> concurrentPrepare(final Object var, final Long epoch) {
        CompletionService<Promise> completionService = new ExecutorCompletionService<>(this.executor);
        for (final Acceptor acceptor : acceptors) {
            completionService.submit(() -> acceptor.prepare(epoch, var));
        }

        List<Promise> promises = new ArrayList<>(acceptors.size());
        for (int i = 0; i < acceptors.size(); i++) {
            Future<Promise> future = null;
            try {
                future = completionService.take();
                Promise promise = future.get();
                promises.add(promise);
            } catch (InterruptedException e) {
                throw new RuntimeException("propose (phase 1) was interrupted", e);
            } catch (ExecutionException e) {
                processExecutionExeception(e);
            }
        }
        return promises;
    }

    protected List<Accepted> concurrentCommit(final Long epoch, final Object var, final Object value) {
        CompletionService<Accepted> completionService = new ExecutorCompletionService<>(this.executor);
        for (final Acceptor acceptor : acceptors) {
            completionService.submit(() -> acceptor.accept(epoch, var, value));
        }

        List<Accepted> accepteds = new ArrayList<>(acceptors.size());
        for (int i = 0; i < acceptors.size(); i++) {
            Future<Accepted> future = null;
            try {
                future = completionService.take();
                Accepted accepted = future.get();
                accepteds.add(accepted);
            } catch (InterruptedException e) {
                throw new RuntimeException("propose (phase 2) was interrupted", e);
            } catch (ExecutionException e) {
                processExecutionExeception(e);
            }
        }
        return accepteds;
    }

    protected Object nextRound(Long maxEpoch, Object var, Object value) {
        LockSupport.parkNanos(this.sleepTimeWhenNeedNextRound);
        Long newEpoch = generateEpoch(maxEpoch, var);
        return proposeWithEpoch(newEpoch, var, value);
    }

    protected void processExecutionExeception(ExecutionException e) {
        e.printStackTrace();
    }

    synchronized protected Long generateEpoch(Long preEpoch, Object var) {
        long lastEpoch = this.lastEpoch.get();
        if (preEpoch > lastEpoch) {
            long epoch = (preEpoch / this.proposerNum + 1) * this.proposerNum + this.proposerId;
            this.lastEpoch.set(epoch);
            return epoch;
        } else {
            long epoch = ((lastEpoch / this.proposerNum + 1) * this.proposerNum) + this.proposerId;
            this.lastEpoch.set(epoch);
            return epoch;
        }
    }

    @Override
    public void stop() {
        this.executor.shutdown();
        try {
            this.executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
