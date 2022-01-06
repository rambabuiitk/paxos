public interface Acceptor {

    Promise prepare(Long epoch, Object var);

    Accepted accept(Long epoch, Object var, Object value);
}
