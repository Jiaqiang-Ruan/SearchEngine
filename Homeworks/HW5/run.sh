ROOT=/Users/jiaqiangruan/Projects/SearchEngine/Homeworks
HW=HW5
LIB=$ROOT/lucene-8.1.1
CLASSPATH=$LIB/*:$ROOT/$HW
PARAM_DIR=$ROOT/$HW/PARAM_DIR

java -cp $CLASSPATH QryEval $PARAM_DIR/HW5-Exp-2.1a.param
