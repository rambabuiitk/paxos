package util;

public abstract class Asserts {

    public static Object unreachable() {
        throw new IllegalStateException("unreachable code!");
    }

    public static void equals(Object... objects) {
        if (objects == null) {
            return;
        }
        Object first = objects[0];
        for (Object o : objects) {
            if (!o.equals(first)) {
                throw new IllegalArgumentException(String.format("%s is not equals %s", o, first));
            }
        }
    }
}
