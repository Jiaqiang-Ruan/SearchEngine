import org.jblas.FloatMatrix;


public abstract class Criterion {
    FloatMatrix logits;
    FloatMatrix labels;
    FloatMatrix loss;

    public Criterion() {
        this.logits = null;
        this.labels = null;
        this.loss = null;
    }

    public abstract FloatMatrix forward(FloatMatrix x, FloatMatrix y);

    public abstract FloatMatrix derivative();

}