all: dir build

dir:
	mkdir -p bin
	ln -sfn ~/tmp/SearchEngine INPUT_DIR
	ln -sfn QryEval TEST_DIR

build:
	javac -Xlint -cp QryEval:../lucene-8.1.1/* -d bin QryEval/*.java

clean:
	rm -r bin