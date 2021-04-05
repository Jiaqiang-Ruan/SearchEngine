import org.jblas.FloatMatrix;

import org.jblas.util.Random;

public class Linear {
    public FloatMatrix W;
    public FloatMatrix b;
    public FloatMatrix x;

    public FloatMatrix dW;
    public FloatMatrix db;

    // For adagrad
    public FloatMatrix sqW;
    public FloatMatrix sqb;

    public Linear(int in_features, int out_features, long seed) {
        Random.seed(seed);
        this.W = FloatMatrix.randn(in_features, out_features);
        this.b = FloatMatrix.zeros(1, out_features);
        this.x = null;
        this.dW = FloatMatrix.zeros(in_features, out_features);
        this.db = FloatMatrix.zeros(1, out_features);
        this.sqW = FloatMatrix.zeros(this.W.rows, this.W.columns);
        this.sqb = FloatMatrix.zeros(this.b.rows, this.b.columns);
    }

    public FloatMatrix forward(FloatMatrix x) {
        this.x = x;
        return x.mmul(this.W).addRowVector(this.b);

    }

    public FloatMatrix backward(FloatMatrix delta) {
        float batch_size = delta.rows;
        this.dW = (this.x.transpose().mmul(delta)).div(batch_size);
        this.db = delta.columnSums().div(batch_size);
        FloatMatrix backwardPass = delta.mmul(this.W.transpose());
        return backwardPass;
    }

}
