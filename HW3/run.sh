ROOT=/Users/jiaqiangruan/tmp/SearchEngine
HW=HW3
LIB=$ROOT/lucene-8.1.1
CLASSPATH=$LIB/*:$ROOT/$HW/bin

java -cp $CLASSPATH QryEval $ROOT/$HW/QryEval/HW3-Exp-2a.param
