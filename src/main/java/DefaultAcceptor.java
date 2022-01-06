import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import util.MDCs;

public class DefaultAcceptor implements Acceptor {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAcceptor.class);

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String NAME_PREFIX = "acceptor_";

    private final String name;
    private Map<Object, Object> values = new ConcurrentHashMap<>();
    private Map<Object, Long> lastestEpoches = new ConcurrentHashMap<>();

    public DefaultAcceptor() {
        this(NAME_PREFIX + SEQUENCE.getAndIncrement());
    }

    public DefaultAcceptor(String name) {
        this.name = name;
    }

    @Override
    public synchronized Promise prepare(Long epoch, Object var) {
        MDC.put(MDCs.MDC_NAME, this.name);
        LOG.info("receive prepare, epoch [{}], var [{}]", epoch, var);
        Long preEpoch = lastestEpoches.get(var);

        if (preEpoch == null) {
            LOG.info("ACK prepare for var [{}],epoch [{}],  no preEpoch", var, epoch);
            lastestEpoches.put(var, epoch);
            return Promise.create(null, var, null);
        }

        Object oldValue = values.get(var);
        if (preEpoch > epoch) {
            LOG.info("NAK prepare fro var [{}], preEpoch [{}] is greater than epoch [{}]", var, preEpoch, epoch);
            return Promise.create(preEpoch, var, oldValue, true);
        }

        LOG.info("ACK accept for var [{}], epoch [{}], preEpoch [{}]", var, epoch, preEpoch);
        lastestEpoches.put(var, epoch);
        return Promise.create(preEpoch, var, oldValue);
    }

    @Override
    public synchronized Accepted accept(Long epoch, Object var, Object value) {
        MDC.put(MDCs.MDC_NAME, this.name);
        LOG.info("receive accept, epoch [{}], var [{}], value [{}]", epoch, var, value);
        Long preEpoch = lastestEpoches.get(var);
        if (preEpoch == null) {
            String msg = "I(the Acceptor) need an epoch before I can accept";
            LOG.error(msg);
            throw new IllegalStateException(msg);
        }

        Object oldValue = values.get(var);
        if (preEpoch > epoch) {
            LOG.info(
                    "NAK accept for var [{}], preEpoch is greater! current : (epoch [{}],value [{}]), pre (repEpoch [{}], preValue [{}] )",
                    var, epoch, value, preEpoch, oldValue);
            return Accepted.create(preEpoch, var, oldValue, true);
        }

        LOG.info("ACK accept for var [{}], current : (epoch [{}],value [{}]), pre (repEpoch [{}], preValue [{}] )", var,
                epoch, value, preEpoch, oldValue);
        values.put(var, value);
        return Accepted.create(epoch, var, value);

    }
}
