import org.jblas.FloatMatrix;

public class ReLU extends Activation {

    public ReLU() {
        super();
    }

    public FloatMatrix forward(FloatMatrix x) {
        this.state = x.max(0.0F);
        return this.state;
    }

    public FloatMatrix derivative() {
        return this.state.gt(0.0F);

    }

//    public static void main(String args[]) {
//        FloatMatrix b = FloatMatrix.randn(3, 3);
//        ReLU relu = new ReLU();
//        relu.forward(b);
//        System.out.println(relu.derivative());
//
//    }
}

