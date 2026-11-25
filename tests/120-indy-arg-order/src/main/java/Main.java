// An instance lambda that captures `this` and a value: the invokedynamic
// arguments are (this, payload). With the arguments reversed, the lambda body
// runs on the payload instead of `this`, and box.value never gets the payload.
public class Main {
    static class Box { Object value; }
    static class Payload { }

    Box box = new Box();

    void put(Object payload) {
        Runnable r = () -> this.box.value = payload;
        r.run();
    }

    public static void main(String[] args) {
        new Main().put(new Payload());
    }
}
