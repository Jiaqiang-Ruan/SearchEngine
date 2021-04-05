ROOT=/Users/jiaqiangruan/tmp/SearchEngine
HW=HW3
LIB=$ROOT/lucene-8.1.1
CLASSPATH=$LIB/*:$ROOT/$HW
PARAM_DIR=$ROOT/$HW/PARAM_DIR

java -cp $CLASSPATH QryEval $PARAM_DIR/HW3-Exp-1a.param
java -cp $CLASSPATH QryEval $PARAM_DIR/HW3-Exp-1b.param
java -cp $CLASSPATH QryEval $PARAM_DIR/HW3-Exp-1c.param
