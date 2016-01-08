grammar := Datalog
javaGen := BaseListener.java Lexer.java Listener.java Parser.java
javaGen := $(javaGen:%=$(grammar)%)
tokGen  := .tokens Lexer.tokens
tokGen  := $(tokGen:%=$(grammar)%)
javaSrc := Main.java DSLListener.java
classes := $(javaSrc:%.java=%.class)
tests   := t1 t2 t3 t4 t5 t6 sample
tests   := $(tests:%=examples/%.logic)

.PHONY: all run clean

all: build
	
build: $(javaGen) $(tokGen) $(classes)

$(javaGen) $(tokGen): $(grammar).g4 
	java org.antlr.v4.Tool $<
	javac $(javaGen)

$(classes): $(javaSrc)
#	javac $?

run: build
	@runTest $(tests)

clean:
	rm -rf $(javaGen) *.class *.tokens
