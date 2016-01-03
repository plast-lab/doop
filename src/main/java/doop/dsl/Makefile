grammar := Datalog
javaGen := BaseListener.java Lexer.java Listener.java Parser.java
javaGen := $(javaGen:%=$(grammar)%)
tokGen  := .tokens Lexer.tokens
tokGen  := $(tokGen:%=$(grammar)%)

.PHONY: all run clean

all: $(javaGen) $(tokGen)

$(javaGen) $(tokGen): $(grammar).g4 
	java org.antlr.v4.Tool $<
	javac $(javaGen)

run:
	java org.antlr.v4.gui.TestRig LogiQL program -tree

clean:
	rm -rf $(javaGen) *.class *.tokens
