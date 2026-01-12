all:
	mkdir -p bin
	javac -d bin src/*.java

clean:
	rm -rf bin
	rm -f *.log

test: all
	./verify.sh
