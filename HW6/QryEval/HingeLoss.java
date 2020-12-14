import org.jblas.FloatMatrix;


public class HingeLoss extends Criterion {
    float margin;

    public HingeLoss(float margin) {
        super();
        this.margin = margin;
    }

    public FloatMatrix forward(FloatMatrix relScore, FloatMatrix nonRelScore) {
        this.loss = relScore.rsub(1.0F).add(nonRelScore).max(0F);
        return this.loss;
    }


    public FloatMatrix derivative() {
        if (this.loss.get(0) > 0) {
            return new FloatMatrix(2, 1, -1, 1);
        } else {
            return new FloatMatrix(2, 1, 0, 0);
        }
    }
}