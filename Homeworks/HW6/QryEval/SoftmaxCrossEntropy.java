import org.jblas.FloatMatrix;
import org.jblas.MatrixFunctions;


public class SoftmaxCrossEntropy extends Criterion {

    FloatMatrix softmax_sum;


    public SoftmaxCrossEntropy() {
        super();
        this.softmax_sum = null;
    }

    public FloatMatrix forward(FloatMatrix x, FloatMatrix y) {
        this.logits = x;
        this.labels = y;

        FloatMatrix a = x.rowMaxs();


        this.softmax_sum = x.subColumnVector(a.add(MatrixFunctions.log(MatrixFunctions.exp(x.subColumnVector(a)).rowSums())));
        this.loss = this.softmax_sum.mul(y).rowSums().neg();

        return this.loss;

    }

    public FloatMatrix derivative() {
        return MatrixFunctions.exp(this.softmax_sum).sub(this.labels);

    }
}