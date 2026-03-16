package online.sharedtype.it.java8;

import online.sharedtype.SharedType;

@SharedType
public class TypeBoundsIssue7 {
    @SharedType
    public interface Shape {
        double area();
    }

    @SharedType
    public static class Circle implements Shape {
        public double radius;

        @Override
        public double area() {
            return 3.14 * radius * radius;
        }
    }

    @SharedType
    public static class ContainerBounds<T extends Shape> {
        public T shape;
    }

    @SharedType
    public static class MultiBoundContainer<T extends Shape & java.io.Serializable> {
        public T shape;
    }
}
